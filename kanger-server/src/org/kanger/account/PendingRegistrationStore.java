/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.json.JSONObject;
import org.kanger.security.CredentialMaterial;
import org.kanger.security.SecureTokens;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Versioned persistent transient registration store.
 *
 * <p>Raw passwords and raw security tokens are never written. Every operation
 * performs lazy pending-TTL eviction under one JVM-wide file authority lock.
 * Confirmation-token expiry does not remove an otherwise live pending record.</p>
 */
public final class PendingRegistrationStore {

    interface TimeSource {
        long now();
    }

    static final class Config {
        final long pendingTtlMillis;
        final long confirmationTtlMillis;
        final long actionTtlMillis;
        final long resendCooldownMillis;
        final int maxRecords;

        Config(long pendingTtlMillis,
               long confirmationTtlMillis,
               long actionTtlMillis,
               long resendCooldownMillis,
               int maxRecords) {
            if (pendingTtlMillis <= 0L
                    || confirmationTtlMillis <= 0L
                    || actionTtlMillis <= 0L
                    || resendCooldownMillis < 0L
                    || maxRecords <= 0) {
                throw new IllegalArgumentException(
                        "pending registration limits must be positive");
            }
            this.pendingTtlMillis = pendingTtlMillis;
            this.confirmationTtlMillis = confirmationTtlMillis;
            this.actionTtlMillis = actionTtlMillis;
            this.resendCooldownMillis = resendCooldownMillis;
            this.maxRecords = maxRecords;
        }
    }

    static final class Draft {
        final String login;
        final String email;
        final CredentialMaterial credentialMaterial;
        final String name;
        final String country;
        final String city;
        final Boolean privacyConsent;

        Draft(String login,
              String email,
              CredentialMaterial credentialMaterial,
              String name,
              String country,
              String city,
              Boolean privacyConsent) {
            this.login = normalizeLogin(login);
            this.email = normalizeEmail(email);
            if (this.login.isEmpty() || this.email.isEmpty()
                    || credentialMaterial == null) {
                throw new IllegalArgumentException(
                        "login, e-mail and credential material are required");
            }
            this.credentialMaterial = credentialMaterial;
            this.name = optional(name);
            this.country = optional(country);
            this.city = optional(city);
            this.privacyConsent = privacyConsent;
        }
    }

    public static final class Created {
        private final PendingRegistration registration;
        private final String confirmationToken;

        private Created(PendingRegistration registration, String confirmationToken) {
            this.registration = registration;
            this.confirmationToken = confirmationToken;
        }

        public PendingRegistration getRegistration() {
            return registration;
        }

        public String getConfirmationToken() {
            return confirmationToken;
        }
    }

    public static final class Authenticated {
        private final PendingRegistration registration;
        private final String actionToken;

        private Authenticated(PendingRegistration registration, String actionToken) {
            this.registration = registration;
            this.actionToken = actionToken;
        }

        public PendingRegistration getRegistration() {
            return registration;
        }

        public String getActionToken() {
            return actionToken;
        }
    }

    public static final class Rotation {
        private final PendingRegistration registration;
        private final String confirmationToken;
        private final String actionToken;

        private Rotation(PendingRegistration registration,
                         String confirmationToken,
                         String actionToken) {
            this.registration = registration;
            this.confirmationToken = confirmationToken;
            this.actionToken = actionToken;
        }

        public PendingRegistration getRegistration() {
            return registration;
        }

        public String getConfirmationToken() {
            return confirmationToken;
        }

        public String getActionToken() {
            return actionToken;
        }
    }

    private static final String VERSION = "v1";
    private static final Object STORE_AUTHORITY_LOCK = new Object();

    private final Path file;
    private final Config config;
    private final TimeSource clock;

    public PendingRegistrationStore(Path file,
                                    long pendingTtlMillis,
                                    long confirmationTtlMillis,
                                    long actionTtlMillis,
                                    long resendCooldownMillis,
                                    int maxRecords) throws IOException {
        this(file,
                new Config(pendingTtlMillis,
                        confirmationTtlMillis,
                        actionTtlMillis,
                        resendCooldownMillis,
                        maxRecords),
                new TimeSource() {
                    @Override
                    public long now() {
                        return System.currentTimeMillis();
                    }
                });
    }

    PendingRegistrationStore(Path file, Config config, TimeSource clock)
            throws IOException {
        if (file == null || config == null || clock == null) {
            throw new IllegalArgumentException(
                    "pending file, config and clock must not be null");
        }
        this.file = file.toAbsolutePath().normalize();
        this.config = config;
        this.clock = clock;
        synchronized (STORE_AUTHORITY_LOCK) {
            Load load = read(clock.now());
            if (load.changed) {
                write(load.records);
            }
        }
    }

    Created create(Draft draft) throws Exception {
        if (draft == null) {
            throw new IllegalArgumentException("pending draft must not be null");
        }
        synchronized (STORE_AUTHORITY_LOCK) {
            long now = clock.now();
            Load load = read(now);
            ensureUnique(load.records, draft.login, draft.email, null);
            if (load.records.size() >= config.maxRecords) {
                throw new IllegalStateException(
                        "Pending registration capacity exceeded");
            }

            String confirmationToken = SecureTokens.random256();
            MutableRecord record = new MutableRecord();
            record.id = SecureTokens.random256();
            record.login = draft.login;
            record.email = draft.email;
            record.credentialMaterial = draft.credentialMaterial;
            record.name = draft.name;
            record.country = draft.country;
            record.city = draft.city;
            record.privacyConsent = draft.privacyConsent;
            record.createdAt = now;
            record.expiresAt = now + config.pendingTtlMillis;
            record.confirmationHash = fingerprint(confirmationToken);
            record.confirmationExpiresAt = now + config.confirmationTtlMillis;
            record.actionHash = "";
            record.actionExpiresAt = 0L;
            record.resendCount = 0;
            record.lastResendAt = now;
            load.records.add(record);
            write(load.records);
            return new Created(snapshot(record), confirmationToken);
        }
    }

    public PendingRegistration resolveConfirmation(String token) throws Exception {
        String hash = requiredFingerprint(token);
        synchronized (STORE_AUTHORITY_LOCK) {
            long now = clock.now();
            Load load = read(now);
            MutableRecord record = findByConfirmationHash(load.records, hash);
            if (load.changed) {
                write(load.records);
            }
            if (record == null) {
                throw failure(AccountErrorCode.CONFIRMATION_TOKEN_INVALID,
                        "Confirmation token is invalid");
            }
            if (record.confirmationExpiresAt <= now) {
                throw failure(AccountErrorCode.CONFIRMATION_TOKEN_EXPIRED,
                        "Confirmation token is expired");
            }
            return snapshot(record);
        }
    }

    public boolean complete(String pendingId, String confirmationToken)
            throws Exception {
        String hash = requiredFingerprint(confirmationToken);
        synchronized (STORE_AUTHORITY_LOCK) {
            Load load = read(clock.now());
            for (int index = 0; index < load.records.size(); index++) {
                MutableRecord record = load.records.get(index);
                if (record.id.equals(pendingId)
                        && secureEquals(record.confirmationHash, hash)) {
                    load.records.remove(index);
                    write(load.records);
                    return true;
                }
            }
            if (load.changed) {
                write(load.records);
            }
            return false;
        }
    }

    public Authenticated authenticate(String login, String password)
            throws Exception {
        String normalized = normalizeLogin(login);
        synchronized (STORE_AUTHORITY_LOCK) {
            long now = clock.now();
            Load load = read(now);
            MutableRecord record = findByLogin(load.records, normalized);
            if (record == null || !record.credentialMaterial.matches(password)) {
                if (load.changed) {
                    write(load.records);
                }
                throw failure(AccountErrorCode.AUTHENTICATION_FAILED,
                        "Pending registration authentication failed");
            }
            String actionToken = SecureTokens.random256();
            record.actionHash = fingerprint(actionToken);
            record.actionExpiresAt = now + config.actionTtlMillis;
            write(load.records);
            return new Authenticated(snapshot(record), actionToken);
        }
    }

    public Rotation resend(String actionToken) throws Exception {
        String hash = requiredFingerprint(actionToken);
        synchronized (STORE_AUTHORITY_LOCK) {
            long now = clock.now();
            Load load = read(now);
            MutableRecord record = requireAction(load.records, hash, now);
            if (record.lastResendAt + config.resendCooldownMillis > now) {
                if (load.changed) {
                    write(load.records);
                }
                throw failure(AccountErrorCode.RESEND_RATE_LIMITED,
                        "Confirmation resend cooldown is active");
            }
            String confirmationToken = SecureTokens.random256();
            record.confirmationHash = fingerprint(confirmationToken);
            record.confirmationExpiresAt = now + config.confirmationTtlMillis;
            record.resendCount++;
            record.lastResendAt = now;
            write(load.records);
            return new Rotation(snapshot(record), confirmationToken, actionToken);
        }
    }

    public Rotation changeEmail(String actionToken, String email) throws Exception {
        String hash = requiredFingerprint(actionToken);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("e-mail must not be empty");
        }
        synchronized (STORE_AUTHORITY_LOCK) {
            long now = clock.now();
            Load load = read(now);
            MutableRecord record = requireAction(load.records, hash, now);
            ensureUnique(load.records, record.login, normalizedEmail, record.id);

            String confirmationToken = SecureTokens.random256();
            String nextActionToken = SecureTokens.random256();
            record.email = normalizedEmail;
            record.confirmationHash = fingerprint(confirmationToken);
            record.confirmationExpiresAt = now + config.confirmationTtlMillis;
            record.actionHash = fingerprint(nextActionToken);
            record.actionExpiresAt = now + config.actionTtlMillis;
            record.resendCount++;
            record.lastResendAt = now;
            write(load.records);
            return new Rotation(
                    snapshot(record), confirmationToken, nextActionToken);
        }
    }

    public PendingRegistration cancel(String actionToken) throws Exception {
        String hash = requiredFingerprint(actionToken);
        synchronized (STORE_AUTHORITY_LOCK) {
            long now = clock.now();
            Load load = read(now);
            MutableRecord record = requireAction(load.records, hash, now);
            load.records.remove(record);
            write(load.records);
            return snapshot(record);
        }
    }

    public boolean containsLogin(String login) throws IOException {
        synchronized (STORE_AUTHORITY_LOCK) {
            Load load = read(clock.now());
            if (load.changed) {
                write(load.records);
            }
            return findByLogin(load.records, normalizeLogin(login)) != null;
        }
    }

    public boolean containsEmail(String email) throws IOException {
        String normalized = normalizeEmail(email);
        synchronized (STORE_AUTHORITY_LOCK) {
            Load load = read(clock.now());
            if (load.changed) {
                write(load.records);
            }
            for (MutableRecord record : load.records) {
                if (record.email.equals(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }

    public PendingRegistration findById(String id) throws IOException {
        synchronized (STORE_AUTHORITY_LOCK) {
            Load load = read(clock.now());
            if (load.changed) {
                write(load.records);
            }
            for (MutableRecord record : load.records) {
                if (record.id.equals(id)) {
                    return snapshot(record);
                }
            }
            return null;
        }
    }

    public int size() throws IOException {
        synchronized (STORE_AUTHORITY_LOCK) {
            Load load = read(clock.now());
            if (load.changed) {
                write(load.records);
            }
            return load.records.size();
        }
    }

    private Load read(long now) throws IOException {
        List<MutableRecord> records = new ArrayList<MutableRecord>();
        boolean changed = false;
        if (!Files.exists(file)) {
            return new Load(records, false);
        }
        for (String original : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = original.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            String[] values = line.split("\\t", 2);
            if (values.length != 2 || !VERSION.equals(values[0])) {
                throw new IOException("Invalid pending registration record in " + file);
            }
            try {
                String jsonText = new String(
                        Base64.getUrlDecoder().decode(values[1]),
                        StandardCharsets.UTF_8);
                MutableRecord record = parse(new JSONObject(jsonText));
                if (record.expiresAt > now) {
                    records.add(record);
                } else {
                    changed = true;
                }
            } catch (Exception error) {
                throw new IOException(
                        "Invalid pending registration payload in " + file, error);
            }
        }
        return new Load(records, changed);
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
                int created = Long.compare(left.createdAt, right.createdAt);
                return created != 0 ? created : left.id.compareTo(right.id);
            }
        });
        List<String> lines = new ArrayList<String>();
        lines.add("# KANGER transient pending registrations; owner access only");
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
                .put("login", record.login)
                .put("email", record.email)
                .put("credential", record.credentialMaterial.encode())
                .put("name", record.name)
                .put("country", record.country)
                .put("city", record.city)
                .put("privacy", record.privacyConsent == null
                        ? JSONObject.NULL : record.privacyConsent)
                .put("createdAt", record.createdAt)
                .put("expiresAt", record.expiresAt)
                .put("confirmationHash", record.confirmationHash)
                .put("confirmationExpiresAt", record.confirmationExpiresAt)
                .put("actionHash", record.actionHash)
                .put("actionExpiresAt", record.actionExpiresAt)
                .put("resendCount", record.resendCount)
                .put("lastResendAt", record.lastResendAt);
    }

    private static MutableRecord parse(JSONObject json) throws Exception {
        MutableRecord record = new MutableRecord();
        record.id = json.getString("id");
        record.login = normalizeLogin(json.getString("login"));
        record.email = normalizeEmail(json.getString("email"));
        record.credentialMaterial = CredentialMaterial.decode(
                json.getString("credential"));
        record.name = json.optString("name", "");
        record.country = json.optString("country", "");
        record.city = json.optString("city", "");
        record.privacyConsent = json.isNull("privacy")
                ? null : Boolean.valueOf(json.getBoolean("privacy"));
        record.createdAt = json.getLong("createdAt");
        record.expiresAt = json.getLong("expiresAt");
        record.confirmationHash = json.getString("confirmationHash");
        record.confirmationExpiresAt = json.getLong("confirmationExpiresAt");
        record.actionHash = json.optString("actionHash", "");
        record.actionExpiresAt = json.optLong("actionExpiresAt", 0L);
        record.resendCount = json.optInt("resendCount", 0);
        record.lastResendAt = json.optLong("lastResendAt", record.createdAt);
        if (record.id.isEmpty() || record.login.isEmpty() || record.email.isEmpty()
                || record.createdAt <= 0L || record.expiresAt <= record.createdAt
                || record.confirmationHash.isEmpty()) {
            throw new IOException("Invalid pending registration values");
        }
        return record;
    }

    private static PendingRegistration snapshot(MutableRecord record) {
        return new PendingRegistration(
                record.id,
                record.login,
                record.email,
                record.credentialMaterial,
                record.name,
                record.country,
                record.city,
                record.privacyConsent,
                record.createdAt,
                record.expiresAt,
                record.confirmationExpiresAt,
                record.resendCount,
                record.lastResendAt);
    }

    private static MutableRecord findByLogin(List<MutableRecord> records,
                                             String login) {
        for (MutableRecord record : records) {
            if (record.login.equals(login)) {
                return record;
            }
        }
        return null;
    }

    private static MutableRecord findByConfirmationHash(
            List<MutableRecord> records,
            String hash) {
        for (MutableRecord record : records) {
            if (secureEquals(record.confirmationHash, hash)) {
                return record;
            }
        }
        return null;
    }

    private static MutableRecord requireAction(List<MutableRecord> records,
                                               String hash,
                                               long now)
            throws PendingRegistrationException {
        for (MutableRecord record : records) {
            if (secureEquals(record.actionHash, hash)
                    && record.actionExpiresAt > now) {
                return record;
            }
        }
        throw failure(AccountErrorCode.AUTHENTICATION_FAILED,
                "Pending action token is invalid or expired");
    }

    private static void ensureUnique(List<MutableRecord> records,
                                     String login,
                                     String email,
                                     String excludedId)
            throws PendingRegistrationException {
        for (MutableRecord record : records) {
            if (excludedId != null && excludedId.equals(record.id)) {
                continue;
            }
            if (record.login.equals(login)) {
                throw failure(AccountErrorCode.LOGIN_ALREADY_USED,
                        "Login already has a pending registration");
            }
            if (record.email.equals(email)) {
                throw failure(AccountErrorCode.EMAIL_ALREADY_USED,
                        "E-mail already has a pending registration");
            }
        }
    }

    private static String requiredFingerprint(String token)
            throws PendingRegistrationException {
        if (token == null || token.trim().isEmpty()) {
            throw failure(AccountErrorCode.AUTHENTICATION_FAILED,
                    "Security token must not be empty");
        }
        return fingerprint(token.trim());
    }

    private static String fingerprint(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static boolean secureEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static PendingRegistrationException failure(AccountErrorCode code,
                                                         String message) {
        return new PendingRegistrationException(code, message);
    }

    private static String normalizeLogin(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeEmail(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String optional(String value) {
        return value == null ? "" : value;
    }

    private static void ownerOnly(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException unsupported) {
            // Non-POSIX filesystem: rely on the enclosing service state ACL.
        }
    }

    private static final class Load {
        private final List<MutableRecord> records;
        private final boolean changed;

        private Load(List<MutableRecord> records, boolean changed) {
            this.records = records;
            this.changed = changed;
        }
    }

    private static final class MutableRecord {
        private String id;
        private String login;
        private String email;
        private CredentialMaterial credentialMaterial;
        private String name;
        private String country;
        private String city;
        private Boolean privacyConsent;
        private long createdAt;
        private long expiresAt;
        private String confirmationHash;
        private long confirmationExpiresAt;
        private String actionHash;
        private long actionExpiresAt;
        private int resendCount;
        private long lastResendAt;
    }
}
