/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.json.JSONObject;
import org.kanger.security.SecureTokens;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Persistent forward-only deletion journal.
 *
 * <p>COMPLETE records are intentionally retained as audit identity until a
 * later explicit purge operation removes both journal entry and quarantine
 * data.</p>
 */
public final class AccountDeletionStore {

    interface TimeSource {
        long now();
    }

    private static final String VERSION = "v1";
    private static final Object STORE_AUTHORITY_LOCK = new Object();

    private final Path file;
    private final TimeSource clock;

    public AccountDeletionStore(Path file) throws IOException {
        this(file, new TimeSource() {
            @Override
            public long now() {
                return System.currentTimeMillis();
            }
        });
    }

    AccountDeletionStore(Path file, TimeSource clock) throws IOException {
        if (file == null || clock == null) {
            throw new IllegalArgumentException(
                    "deletion journal file and clock must not be null");
        }
        this.file = file.toAbsolutePath().normalize();
        this.clock = clock;
        synchronized (STORE_AUTHORITY_LOCK) {
            read();
        }
    }

    public AccountDeletion prepare(long userId,
                                   String login,
                                   String email,
                                   Path canonicalHome,
                                   Path quarantineRoot) throws Exception {
        if (userId <= 0L || login == null || login.trim().isEmpty()
                || canonicalHome == null || quarantineRoot == null) {
            throw new IllegalArgumentException(
                    "user id, login, canonical home and quarantine root are required");
        }
        synchronized (STORE_AUTHORITY_LOCK) {
            List<MutableRecord> records = read();
            MutableRecord existing = findByUserId(records, userId);
            if (existing != null) {
                if (!existing.login.equals(login.trim())
                        || !existing.canonicalHome.equals(
                        canonicalHome.toAbsolutePath().normalize().toString())) {
                    throw new IllegalStateException(
                            "Deletion identity conflicts with existing journal record");
                }
                return snapshot(existing);
            }

            long now = clock.now();
            MutableRecord record = new MutableRecord();
            record.id = SecureTokens.random256();
            record.userId = userId;
            record.login = login.trim();
            record.email = email == null ? "" : email.trim();
            record.canonicalHome = canonicalHome.toAbsolutePath().normalize().toString();
            record.quarantineHome = quarantineRoot.toAbsolutePath().normalize()
                    .resolve(userId + "-" + record.id.substring(0, 16))
                    .normalize().toString();
            record.state = AccountDeletionState.PREPARED;
            record.createdAt = now;
            record.updatedAt = now;
            record.diagnostic = "";
            records.add(record);
            write(records);
            return snapshot(record);
        }
    }

    public AccountDeletion advance(String deletionId,
                                   AccountDeletionState next,
                                   String diagnostic) throws Exception {
        if (deletionId == null || deletionId.isEmpty() || next == null) {
            throw new IllegalArgumentException(
                    "deletion id and next state must not be empty");
        }
        synchronized (STORE_AUTHORITY_LOCK) {
            List<MutableRecord> records = read();
            MutableRecord record = findById(records, deletionId);
            if (record == null) {
                throw new IllegalStateException(
                        "Deletion journal record does not exist: " + deletionId);
            }
            if (!record.state.canAdvanceTo(next)) {
                throw new IllegalStateException(
                        "Deletion state cannot move backward from "
                                + record.state + " to " + next);
            }
            record.state = next;
            record.updatedAt = clock.now();
            record.diagnostic = diagnostic == null ? "" : diagnostic;
            write(records);
            return snapshot(record);
        }
    }

    public AccountDeletion diagnose(String deletionId, String diagnostic)
            throws Exception {
        if (deletionId == null || deletionId.isEmpty()) {
            throw new IllegalArgumentException("deletion id must not be empty");
        }
        synchronized (STORE_AUTHORITY_LOCK) {
            List<MutableRecord> records = read();
            MutableRecord record = findById(records, deletionId);
            if (record == null) {
                throw new IllegalStateException(
                        "Deletion journal record does not exist: " + deletionId);
            }
            record.updatedAt = clock.now();
            record.diagnostic = diagnostic == null ? "" : diagnostic;
            write(records);
            return snapshot(record);
        }
    }

    public AccountDeletion findByUserId(long userId) throws Exception {
        synchronized (STORE_AUTHORITY_LOCK) {
            MutableRecord record = findByUserId(read(), userId);
            return record == null ? null : snapshot(record);
        }
    }

    public AccountDeletion findById(String deletionId) throws Exception {
        synchronized (STORE_AUTHORITY_LOCK) {
            MutableRecord record = findById(read(), deletionId);
            return record == null ? null : snapshot(record);
        }
    }

    public List<AccountDeletion> all() throws Exception {
        synchronized (STORE_AUTHORITY_LOCK) {
            List<AccountDeletion> result = new ArrayList<AccountDeletion>();
            for (MutableRecord record : read()) {
                result.add(snapshot(record));
            }
            return result;
        }
    }

    private List<MutableRecord> read() throws IOException {
        List<MutableRecord> records = new ArrayList<MutableRecord>();
        if (!Files.exists(file)) {
            return records;
        }
        for (String original : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = original.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            String[] values = line.split("\\t", 2);
            if (values.length != 2 || !VERSION.equals(values[0])) {
                throw new IOException("Invalid account deletion record in " + file);
            }
            try {
                String jsonText = new String(
                        Base64.getUrlDecoder().decode(values[1]),
                        StandardCharsets.UTF_8);
                records.add(parse(new JSONObject(jsonText)));
            } catch (Exception error) {
                throw new IOException(
                        "Invalid account deletion payload in " + file, error);
            }
        }
        return records;
    }

    private void write(List<MutableRecord> source) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<MutableRecord> records = new ArrayList<MutableRecord>(source);
        Collections.sort(records, new Comparator<MutableRecord>() {
            @Override
            public int compare(MutableRecord left, MutableRecord right) {
                int user = Long.compare(left.userId, right.userId);
                return user != 0 ? user : left.id.compareTo(right.id);
            }
        });

        List<String> lines = new ArrayList<String>();
        lines.add("# KANGER persistent account deletion journal; owner access only");
        for (MutableRecord record : records) {
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    serialize(record).toString().getBytes(StandardCharsets.UTF_8));
            lines.add(VERSION + "\t" + payload);
        }

        Path temporary = file.resolveSibling(file.getFileName().toString()
                + ".tmp-" + UUID.randomUUID().toString());
        Files.write(temporary, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        ownerOnly(temporary);
        try {
            Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        ownerOnly(file);
    }

    private static JSONObject serialize(MutableRecord record) {
        return new JSONObject()
                .put("id", record.id)
                .put("userId", record.userId)
                .put("login", record.login)
                .put("email", record.email)
                .put("canonicalHome", record.canonicalHome)
                .put("quarantineHome", record.quarantineHome)
                .put("state", record.state.name())
                .put("createdAt", record.createdAt)
                .put("updatedAt", record.updatedAt)
                .put("diagnostic", record.diagnostic);
    }

    private static MutableRecord parse(JSONObject json) throws Exception {
        MutableRecord record = new MutableRecord();
        record.id = json.getString("id");
        record.userId = json.getLong("userId");
        record.login = json.getString("login");
        record.email = json.optString("email", "");
        record.canonicalHome = Paths.get(json.getString("canonicalHome"))
                .toAbsolutePath().normalize().toString();
        record.quarantineHome = Paths.get(json.getString("quarantineHome"))
                .toAbsolutePath().normalize().toString();
        record.state = AccountDeletionState.valueOf(json.getString("state"));
        record.createdAt = json.getLong("createdAt");
        record.updatedAt = json.getLong("updatedAt");
        record.diagnostic = json.optString("diagnostic", "");
        if (record.id.isEmpty() || record.userId <= 0L
                || record.login.trim().isEmpty()
                || record.createdAt <= 0L || record.updatedAt < record.createdAt) {
            throw new IOException("Invalid account deletion values");
        }
        return record;
    }

    private static MutableRecord findByUserId(List<MutableRecord> records,
                                              long userId) {
        for (MutableRecord record : records) {
            if (record.userId == userId) {
                return record;
            }
        }
        return null;
    }

    private static MutableRecord findById(List<MutableRecord> records,
                                          String deletionId) {
        for (MutableRecord record : records) {
            if (record.id.equals(deletionId)) {
                return record;
            }
        }
        return null;
    }

    private static AccountDeletion snapshot(MutableRecord record) {
        return new AccountDeletion(
                record.id,
                record.userId,
                record.login,
                record.email,
                Paths.get(record.canonicalHome),
                Paths.get(record.quarantineHome),
                record.state,
                record.createdAt,
                record.updatedAt,
                record.diagnostic);
    }

    private static void ownerOnly(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException unsupported) {
            // Non-POSIX filesystem: rely on the enclosing service state ACL.
        }
    }

    private static final class MutableRecord {
        private String id;
        private long userId;
        private String login;
        private String email;
        private String canonicalHome;
        private String quarantineHome;
        private AccountDeletionState state;
        private long createdAt;
        private long updatedAt;
        private String diagnostic;
    }
}
