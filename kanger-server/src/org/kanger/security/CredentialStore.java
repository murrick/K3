/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.security;

import org.kanger.exception.AuthenticationErrorException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned password credential store with transparent legacy migration.
 */
public final class CredentialStore {

    public static final int DEFAULT_ITERATIONS = 210000;

    private static final String VERSION = "v2";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private final Path file;
    private final int iterations;
    private final SecureRandom random;

    public CredentialStore(Path file) {
        this(file, DEFAULT_ITERATIONS, new SecureRandom());
    }

    CredentialStore(Path file, int iterations, SecureRandom random) {
        if (file == null) {
            throw new IllegalArgumentException("credential file must not be null");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be greater than zero");
        }
        this.file = file;
        this.iterations = iterations;
        this.random = random;
    }

    /**
     * Authenticates a user. A matching legacy record is replaced atomically by
     * a PBKDF2 record only after successful authentication.
     */
    public synchronized long authenticate(String login, String password) throws Exception {
        validateInput(login, password);
        Snapshot snapshot = readSnapshot();

        CredentialRecord record = findByLogin(snapshot.records, login);
        if (record != null) {
            if (!verify(password, record)) {
                throw new AuthenticationErrorException();
            }
            return record.userId;
        }

        String legacy = legacyToken(login, password);
        Long userId = snapshot.legacy.remove(legacy);
        if (userId == null) {
            throw new AuthenticationErrorException();
        }

        removeByUserId(snapshot.records, userId.longValue());
        snapshot.records.add(createRecord(login, password, userId.longValue()));
        writeSnapshot(snapshot);
        return userId.longValue();
    }

    /**
     * Creates a new credential and returns the allocated user id.
     */
    public synchronized long create(String login, String password) throws Exception {
        validateInput(login, password);
        Snapshot snapshot = readSnapshot();

        if (findByLogin(snapshot.records, login) != null
                || snapshot.legacy.containsKey(legacyToken(login, password))) {
            throw new AuthenticationErrorException("User already exists");
        }

        long userId = maxUserId(snapshot) + 1L;
        snapshot.records.add(createRecord(login, password, userId));
        writeSnapshot(snapshot);
        return userId;
    }

    /**
     * Replaces all credentials belonging to a user with one versioned record.
     */
    public synchronized void update(long userId, String login, String password) throws Exception {
        validateInput(login, password);
        Snapshot snapshot = readSnapshot();

        CredentialRecord duplicate = findByLogin(snapshot.records, login);
        if (duplicate != null && duplicate.userId != userId) {
            throw new Exception("Login and password used by another user");
        }
        Long legacyOwner = snapshot.legacy.get(legacyToken(login, password));
        if (legacyOwner != null && legacyOwner.longValue() != userId) {
            throw new Exception("Login and password used by another user");
        }

        removeByUserId(snapshot.records, userId);
        removeLegacyByUserId(snapshot.legacy, userId);
        snapshot.records.add(createRecord(login, password, userId));
        writeSnapshot(snapshot);
    }

    /**
     * Reproduces the historical Java-hash token solely for migration lookup.
     */
    public static String legacyToken(String login, String password) {
        return String.format("%04x%04x", login.hashCode(), password.hashCode());
    }

    private CredentialRecord createRecord(String login, String password, long userId)
            throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations);
        return new CredentialRecord(login, userId, iterations, salt, hash);
    }

    private static boolean verify(String password, CredentialRecord record) throws Exception {
        byte[] candidate = derive(password, record.salt, record.iterations);
        return MessageDigest.isEqual(record.hash, candidate);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) throws Exception {
        char[] chars = password.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(chars, salt, iterations, HASH_BYTES * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(specification).getEncoded();
        } finally {
            specification.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    private Snapshot readSnapshot() throws IOException {
        Snapshot snapshot = new Snapshot();
        if (!Files.exists(file)) {
            return snapshot;
        }

        for (String original : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = original.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            if (line.startsWith(VERSION + "\t")) {
                CredentialRecord record = parseVersioned(line);
                if (findByLogin(snapshot.records, record.login) != null) {
                    throw new IOException("Duplicate credential login in " + file);
                }
                snapshot.records.add(record);
                continue;
            }

            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IOException("Unsupported credential record in " + file);
            }
            String token = line.substring(0, separator).trim();
            long userId = Long.parseLong(line.substring(separator + 1).trim());
            snapshot.legacy.put(token, userId);
        }
        return snapshot;
    }

    private static CredentialRecord parseVersioned(String line) throws IOException {
        String[] values = line.split("\\t", -1);
        if (values.length != 6 || !VERSION.equals(values[0])) {
            throw new IOException("Invalid versioned credential record");
        }
        try {
            String login = new String(decode(values[1]), StandardCharsets.UTF_8);
            long userId = Long.parseLong(values[2]);
            int iterations = Integer.parseInt(values[3]);
            byte[] salt = decode(values[4]);
            byte[] hash = decode(values[5]);
            if (login.isEmpty() || userId < 0L || iterations <= 0
                    || salt.length < 16 || hash.length < 32) {
                throw new IOException("Invalid versioned credential values");
            }
            return new CredentialRecord(login, userId, iterations, salt, hash);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid versioned credential encoding", ex);
        }
    }

    private void writeSnapshot(Snapshot snapshot) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<CredentialRecord> records = new ArrayList<CredentialRecord>(snapshot.records);
        Collections.sort(records, new Comparator<CredentialRecord>() {
            @Override
            public int compare(CredentialRecord left, CredentialRecord right) {
                int id = Long.compare(left.userId, right.userId);
                return id != 0 ? id : left.login.compareTo(right.login);
            }
        });

        List<String> lines = new ArrayList<String>();
        lines.add("# KANGER credential store; do not edit while the server is running");
        for (CredentialRecord record : records) {
            lines.add(VERSION + "\t"
                    + encode(record.login.getBytes(StandardCharsets.UTF_8)) + "\t"
                    + record.userId + "\t"
                    + record.iterations + "\t"
                    + encode(record.salt) + "\t"
                    + encode(record.hash));
        }

        List<Map.Entry<String, Long>> legacy =
                new ArrayList<Map.Entry<String, Long>>(snapshot.legacy.entrySet());
        Collections.sort(legacy, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
                return left.getKey().compareTo(right.getKey());
            }
        });
        for (Map.Entry<String, Long> entry : legacy) {
            lines.add(entry.getKey() + "=" + entry.getValue());
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

    private static CredentialRecord findByLogin(List<CredentialRecord> records, String login) {
        for (CredentialRecord record : records) {
            if (record.login.equals(login)) {
                return record;
            }
        }
        return null;
    }

    private static void removeByUserId(List<CredentialRecord> records, long userId) {
        for (int index = records.size() - 1; index >= 0; index--) {
            if (records.get(index).userId == userId) {
                records.remove(index);
            }
        }
    }

    private static void removeLegacyByUserId(Map<String, Long> legacy, long userId) {
        List<String> remove = new ArrayList<String>();
        for (Map.Entry<String, Long> entry : legacy.entrySet()) {
            if (entry.getValue().longValue() == userId) {
                remove.add(entry.getKey());
            }
        }
        for (String key : remove) {
            legacy.remove(key);
        }
    }

    private static long maxUserId(Snapshot snapshot) {
        long max = 0L;
        for (CredentialRecord record : snapshot.records) {
            max = Math.max(max, record.userId);
        }
        for (Long userId : snapshot.legacy.values()) {
            max = Math.max(max, userId.longValue());
        }
        return max;
    }

    private static void validateInput(String login, String password) {
        if (login == null || login.isEmpty()) {
            throw new IllegalArgumentException("login must not be empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static final class Snapshot {
        private final List<CredentialRecord> records = new ArrayList<CredentialRecord>();
        private final Map<String, Long> legacy = new LinkedHashMap<String, Long>();
    }

    private static final class CredentialRecord {
        private final String login;
        private final long userId;
        private final int iterations;
        private final byte[] salt;
        private final byte[] hash;

        private CredentialRecord(String login,
                                 long userId,
                                 int iterations,
                                 byte[] salt,
                                 byte[] hash) {
            this.login = login;
            this.userId = userId;
            this.iterations = iterations;
            this.salt = salt.clone();
            this.hash = hash.clone();
        }
    }
}
