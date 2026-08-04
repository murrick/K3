/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.security;

import org.kanger.exception.AuthenticationErrorException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Persistent, expiring, one-time e-mail confirmation token store.
 */
public final class ConfirmationTokenStore {

    private static final String VERSION = "v1";

    private final Path file;

    public ConfirmationTokenStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("confirmation file must not be null");
        }
        this.file = file;
    }

    public synchronized String issue(long userId, long ttlMillis) throws IOException {
        String token = SecureTokens.random256();
        bind(token, userId, ttlMillis);
        return token;
    }

    /**
     * Binds a pre-generated token to a user. This exists only to preserve the
     * historical registration call order while QueryProcessor is migrated.
     */
    public synchronized void bind(String token, long userId, long ttlMillis) throws IOException {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("confirmation token must not be empty");
        }
        if (userId < 0L) {
            throw new IllegalArgumentException("user id must not be negative");
        }
        if (ttlMillis <= 0L) {
            throw new IllegalArgumentException("confirmation ttl must be positive");
        }

        long now = System.currentTimeMillis();
        List<Record> records = readActive(now);
        for (int index = records.size() - 1; index >= 0; index--) {
            Record record = records.get(index);
            if (record.userId == userId || record.token.equals(token)) {
                records.remove(index);
            }
        }
        records.add(new Record(token, userId, now + ttlMillis));
        write(records);
    }

    public synchronized long consume(String token) throws Exception {
        if (token == null || token.isEmpty()) {
            throw new AuthenticationErrorException();
        }

        long now = System.currentTimeMillis();
        List<Record> records = readActive(now);
        Long userId = null;
        for (int index = records.size() - 1; index >= 0; index--) {
            Record record = records.get(index);
            if (record.token.equals(token)) {
                userId = record.userId;
                records.remove(index);
                break;
            }
        }
        write(records);
        if (userId == null) {
            throw new AuthenticationErrorException("Confirmation token is invalid or expired");
        }
        return userId.longValue();
    }

    /**
     * Revokes every historical confirmation record for an exact user id.
     */
    public synchronized boolean revoke(long userId) throws IOException {
        long now = System.currentTimeMillis();
        List<Record> records = readActive(now);
        int before = records.size();
        for (int index = records.size() - 1; index >= 0; index--) {
            if (records.get(index).userId == userId) {
                records.remove(index);
            }
        }
        boolean changed = before != records.size();
        if (changed || Files.exists(file)) {
            write(records);
        }
        return changed;
    }

    private List<Record> readActive(long now) throws IOException {
        List<Record> records = new ArrayList<Record>();
        if (!Files.exists(file)) {
            return records;
        }

        for (String original : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = original.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            String[] values = line.split("\\t", -1);
            if (values.length != 4 || !VERSION.equals(values[0])) {
                throw new IOException("Invalid confirmation token record in " + file);
            }
            long userId = Long.parseLong(values[2]);
            long expiresAt = Long.parseLong(values[3]);
            if (expiresAt > now) {
                records.add(new Record(values[1], userId, expiresAt));
            }
        }
        return records;
    }

    private void write(List<Record> source) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<Record> records = new ArrayList<Record>(source);
        Collections.sort(records, new Comparator<Record>() {
            @Override
            public int compare(Record left, Record right) {
                int expiry = Long.compare(left.expiresAt, right.expiresAt);
                return expiry != 0 ? expiry : left.token.compareTo(right.token);
            }
        });

        List<String> lines = new ArrayList<String>();
        lines.add("# KANGER one-time confirmation tokens");
        for (Record record : records) {
            lines.add(VERSION + "\t" + record.token + "\t"
                    + record.userId + "\t" + record.expiresAt);
        }

        Path temporary = file.resolveSibling(file.getFileName().toString()
                + ".tmp-" + UUID.randomUUID().toString());
        Files.write(temporary, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static final class Record {
        private final String token;
        private final long userId;
        private final long expiresAt;

        private Record(String token, long userId, long expiresAt) {
            this.token = token;
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
