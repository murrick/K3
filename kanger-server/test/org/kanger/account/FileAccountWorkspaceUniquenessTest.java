/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAccountWorkspaceUniquenessTest {

    private Path directory;
    private Path accountRoot;
    private FileAccountWorkspace workspace;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-workspace-unique-");
        accountRoot = directory.resolve("KANGER");
        workspace = new FileAccountWorkspace(accountRoot, directory.toString());

        Path legacyHome = accountRoot.resolve("7");
        Files.createDirectories(legacyHome);
        Properties profile = new Properties();
        profile.setProperty("reg.login", "legacy");
        profile.setProperty("reg.email", "legacy@example.org");
        try (java.io.Writer writer = Files.newBufferedWriter(
                legacyHome.resolve("kanger.conf"), StandardCharsets.UTF_8)) {
            profile.store(writer, "existing account");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (directory != null) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void prepareRejectsLoginOwnedByExistingWorkspace() {
        IOException failure = assertThrows(IOException.class,
                () -> workspace.prepare(
                        1L,
                        new ActiveAccountRequest(
                                "legacy",
                                "new password",
                                "new@example.org",
                                "New User",
                                "Austria",
                                "Vienna",
                                Boolean.TRUE)));

        assertTrue(failure.getMessage().contains("login"));
        assertFalse(Files.exists(accountRoot.resolve("1")));
        assertFalse(Files.exists(accountRoot.resolve(".creating")));
    }

    @Test
    void prepareRejectsEmailOwnedByExistingWorkspaceCaseInsensitively() {
        IOException failure = assertThrows(IOException.class,
                () -> workspace.prepare(
                        1L,
                        new ActiveAccountRequest(
                                "new-login",
                                "new password",
                                "LEGACY@example.org",
                                "New User",
                                "Austria",
                                "Vienna",
                                Boolean.TRUE)));

        assertTrue(failure.getMessage().contains("e-mail"));
        assertFalse(Files.exists(accountRoot.resolve("1")));
        assertFalse(Files.exists(accountRoot.resolve(".creating")));
    }
}
