/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.kanger.User;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Builds an account below a private staging directory and publishes the
 * complete home with one same-filesystem move.
 */
final class FileAccountWorkspace implements AccountLifecycleService.WorkspaceAuthority {

    interface RuntimeInitializer {
        void initialize(long userId,
                        Path canonicalHome,
                        Path sourceDirectory,
                        Path databaseDirectory) throws Exception;
    }

    private final Path root;
    private final String serverHome;
    private final RuntimeInitializer runtimeInitializer;

    FileAccountWorkspace(Path root, String serverHome) {
        this(root, serverHome, new KangerRuntimeInitializer());
    }

    FileAccountWorkspace(Path root,
                         String serverHome,
                         RuntimeInitializer runtimeInitializer) {
        if (root == null || runtimeInitializer == null) {
            throw new IllegalArgumentException("root and runtime initializer must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
        this.serverHome = serverHome == null ? "" : serverHome;
        this.runtimeInitializer = runtimeInitializer;
    }

    @Override
    public AccountLifecycleService.PreparedWorkspace prepare(
            long userId,
            ActiveAccountRequest request) throws Exception {
        if (userId < 0L || request == null) {
            throw new IllegalArgumentException("valid user id and request are required");
        }

        Path canonical = root.resolve(Long.toString(userId)).normalize();
        ensureChild(canonical);
        if (Files.exists(canonical)) {
            throw new IOException("Account home already exists: " + canonical);
        }

        Long loginOwner = findUserIdByLogin(request.getLogin());
        if (loginOwner != null) {
            throw new IOException(
                    "Account login already belongs to user " + loginOwner);
        }
        if (!request.getEmail().isEmpty()) {
            Long emailOwner = findUserIdByEmail(request.getEmail());
            if (emailOwner != null) {
                throw new IOException(
                        "Account e-mail already belongs to user " + emailOwner);
            }
        }

        Path stagingRoot = root.resolve(".creating").normalize();
        Path staging = stagingRoot.resolve(userId + "-" + UUID.randomUUID()).normalize();
        ensureChild(staging);

        try {
            Files.createDirectories(staging.resolve("SRC"));
            Files.createDirectories(staging.resolve("DB"));
            writeProfile(staging, canonical, request);
            runtimeInitializer.initialize(
                    userId,
                    canonical,
                    canonical.resolve("SRC"),
                    canonical.resolve("DB"));
            return new Prepared(staging, canonical);
        } catch (Exception failure) {
            deleteTree(staging);
            removeIfEmpty(stagingRoot);
            throw failure;
        }
    }

    @Override
    public Long findUserIdByLogin(String login) throws Exception {
        String normalized = normalizeLogin(login);
        if (normalized.isEmpty() || !Files.isDirectory(root)) {
            return null;
        }
        try (DirectoryStream<Path> homes = Files.newDirectoryStream(root)) {
            for (Path home : homes) {
                String name = home.getFileName().toString();
                if (!Files.isDirectory(home) || !name.matches("[0-9]+")) {
                    continue;
                }
                Properties profile = readProfile(home);
                if (normalized.equals(normalizeLogin(
                        profile.getProperty("reg.login", "")))) {
                    return Long.valueOf(name);
                }
            }
        }
        return null;
    }

    @Override
    public Long findUserIdByEmail(String email) throws Exception {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty() || !Files.isDirectory(root)) {
            return null;
        }
        try (DirectoryStream<Path> homes = Files.newDirectoryStream(root)) {
            for (Path home : homes) {
                String name = home.getFileName().toString();
                if (!Files.isDirectory(home) || !name.matches("[0-9]+")) {
                    continue;
                }
                Properties profile = readProfile(home);
                if (normalized.equals(normalizeEmail(
                        profile.getProperty("reg.email", "")))) {
                    return Long.valueOf(name);
                }
            }
        }
        return null;
    }

    @Override
    public boolean hasActivationReference(long userId, String reference)
            throws Exception {
        if (reference == null || reference.isEmpty()) {
            return false;
        }
        Path home = root.resolve(Long.toString(userId)).normalize();
        ensureChild(home);
        if (!Files.isDirectory(home)) {
            return false;
        }
        return reference.equals(readProfile(home).getProperty(
                "reg.activation.reference", ""));
    }

    private void writeProfile(Path staging,
                              Path canonical,
                              ActiveAccountRequest request) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user.home", serverHome);
        properties.setProperty("user.dir", directory(canonical));
        properties.setProperty("sources.dir", directory(canonical.resolve("SRC")));
        properties.setProperty("database.dir", directory(canonical.resolve("DB")));
        properties.setProperty("reg.login", request.getLogin());
        String emailVerified = Boolean.toString(request.isEmailVerified());
        properties.setProperty("reg.agreed", emailVerified);
        properties.setProperty("reg.email.confirmed", emailVerified);
        putIfPresent(properties, "reg.email", request.getEmail());
        putIfPresent(properties, "reg.name", request.getName());
        putIfPresent(properties, "reg.country", request.getCountry());
        putIfPresent(properties, "reg.city", request.getCity());
        putIfPresent(properties, "reg.activation.reference",
                request.getActivationReference());
        if (request.getPrivacyConsent() != null) {
            properties.setProperty("reg.privacy",
                    request.getPrivacyConsent().toString());
        }

        Path configuration = staging.resolve("kanger.conf");
        try (BufferedWriter writer = Files.newBufferedWriter(
                configuration,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            properties.store(writer, "KANGER complete ACTIVE account");
        }
    }

    private static Properties readProfile(Path home) throws Exception {
        Properties properties = new Properties();
        Path file = home.resolve("kanger.conf");
        if (!Files.isRegularFile(file)) {
            return properties;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static void putIfPresent(Properties properties,
                                     String key,
                                     String value) {
        if (value != null && !value.isEmpty()) {
            properties.setProperty(key, value);
        }
    }

    private static String directory(Path value) {
        return value.toAbsolutePath().normalize().toString() + File.separator;
    }

    private static String normalizeLogin(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeEmail(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureChild(Path value) throws IOException {
        if (!value.startsWith(root) || value.equals(root)) {
            throw new IOException("Account path escapes the configured root: " + value);
        }
    }

    private final class Prepared implements AccountLifecycleService.PreparedWorkspace {
        private final Path staging;
        private final Path canonical;
        private boolean published;

        private Prepared(Path staging, Path canonical) {
            this.staging = staging;
            this.canonical = canonical;
        }

        @Override
        public Path home() {
            return canonical;
        }

        @Override
        public void publish() throws Exception {
            if (published) {
                throw new IllegalStateException("workspace already published");
            }
            if (Files.exists(canonical)) {
                throw new IOException("Account home already exists: " + canonical);
            }
            try {
                Files.move(staging, canonical, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, canonical);
            }
            published = true;
            removeIfEmpty(root.resolve(".creating"));
        }

        @Override
        public void rollback() throws Exception {
            Path target = published ? canonical : staging;
            deleteTree(target);
            removeIfEmpty(root.resolve(".creating"));
        }
    }

    private static final class KangerRuntimeInitializer implements RuntimeInitializer {
        @Override
        public void initialize(long userId,
                               Path canonicalHome,
                               Path sourceDirectory,
                               Path databaseDirectory) throws Exception {
            User user = new User();
            user.setId(userId);
            user.setUserDir(directory(canonicalHome));
            user.setSourceDir(directory(sourceDirectory));
            user.setDatabaseDir(directory(databaseDirectory));

            new DB().init(user);
            new UDF().init(user);

            user.getData();
            user.getUdf();
        }
    }

    static void deleteTree(Path target) throws IOException {
        if (target == null || !Files.exists(target)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException error) {
                            throw new DeleteFailure(error);
                        }
                    });
        } catch (DeleteFailure failure) {
            throw failure.error;
        }
    }

    private static void removeIfEmpty(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            if (!entries.findAny().isPresent()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private static final class DeleteFailure extends RuntimeException {
        private final IOException error;

        private DeleteFailure(IOException error) {
            super(error);
            this.error = error;
        }
    }
}
