/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.security.CredentialStore;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountLifecycleServiceTest {

    private Path directory;
    private Path accountRoot;
    private Path credentialFile;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-account-lifecycle-");
        accountRoot = directory.resolve("KANGER");
        credentialFile = directory.resolve("users.conf");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (directory != null) {
            deleteTree(directory);
        }
    }

    @Test
    void publishesCompleteActiveAccountBeforeCredentialBecomesVisible()
            throws Exception {
        CredentialStore store = new CredentialStore(credentialFile);
        AccountLifecycleService service = service(
                store,
                new FileAccountWorkspace(accountRoot, directory.toString()));
        ActiveAccountRequest request = new ActiveAccountRequest(
                "rick",
                "correct horse battery staple",
                "rick@example.org",
                "Dmitry",
                "Austria",
                "Vienna",
                Boolean.TRUE);

        ActiveAccount account = service.createActiveAccount(request);

        assertEquals(1L, account.getUserId());
        assertEquals(accountRoot.resolve("1").toAbsolutePath().normalize(),
                account.getHome());
        assertTrue(Files.isDirectory(account.getHome().resolve("SRC")));
        assertTrue(Files.isDirectory(account.getHome().resolve("DB")));
        assertTrue(Files.isRegularFile(account.getHome().resolve("kanger.conf")));
        assertFalse(Files.exists(accountRoot.resolve(".creating")));
        assertEquals(1L,
                store.authenticate("rick", "correct horse battery staple"));

        Properties profile = load(account.getHome().resolve("kanger.conf"));
        assertEquals("rick", profile.getProperty("reg.login"));
        assertEquals("rick@example.org", profile.getProperty("reg.email"));
        assertEquals("true", profile.getProperty("reg.agreed"));
        assertEquals("true", profile.getProperty("reg.email.confirmed"));
        assertEquals("true", profile.getProperty("reg.privacy"));
        assertFalse(profile.containsValue("correct horse battery staple"));
    }

    @Test
    void preparationFailureLeavesNoCredentialOrWorkspaceAndRetrySucceeds()
            throws Exception {
        CredentialStore store = new CredentialStore(credentialFile);
        FileAccountWorkspace brokenWorkspace = new FileAccountWorkspace(
                accountRoot,
                directory.toString(),
                (userId, home, source, database) -> {
                    throw new IllegalStateException("synthetic runtime failure");
                });
        AccountLifecycleService broken = service(store, brokenWorkspace);
        ActiveAccountRequest request = new ActiveAccountRequest(
                "rick", "correct horse battery staple");

        assertThrows(IllegalStateException.class,
                () -> broken.createActiveAccount(request));

        assertFalse(Files.exists(accountRoot.resolve("1")));
        assertFalse(Files.exists(accountRoot.resolve(".creating")));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "correct horse battery staple"));

        AccountLifecycleService retry = service(
                store,
                new FileAccountWorkspace(accountRoot, directory.toString()));
        ActiveAccount account = retry.createActiveAccount(request);

        assertEquals(1L, account.getUserId());
        assertTrue(Files.isDirectory(account.getHome()));
    }

    @Test
    void credentialPublicationFailureRollsBackAlreadyPublishedHome()
            throws Exception {
        AccountLifecycleService.CredentialAuthority failingCredentials =
                new AccountLifecycleService.CredentialAuthority() {
                    @Override
                    public long createPrepared(
                            String login,
                            String password,
                            AccountLifecycleService.Preparation preparation)
                            throws Exception {
                        preparation.prepare(17L);
                        throw new IOException("synthetic credential publication failure");
                    }

                    @Override
                    public boolean delete(long userId) {
                        return false;
                    }

                    @Override
                    public Long findUserId(String login) {
                        return null;
                    }
                };
        AccountLifecycleService service = new AccountLifecycleService(
                failingCredentials,
                new FileAccountWorkspace(accountRoot, directory.toString()));

        IOException failure = assertThrows(IOException.class,
                () -> service.createActiveAccount(
                        new ActiveAccountRequest("rick", "secret")));

        assertTrue(failure.getMessage().contains("credential publication"));
        assertFalse(Files.exists(accountRoot.resolve("17")));
        assertFalse(Files.exists(accountRoot.resolve(".creating")));
    }

    @Test
    void duplicateLoginDoesNotDisturbExistingCompleteAccount() throws Exception {
        CredentialStore store = new CredentialStore(credentialFile);
        AccountLifecycleService service = service(
                store,
                new FileAccountWorkspace(accountRoot, directory.toString()));
        ActiveAccount first = service.createActiveAccount(
                new ActiveAccountRequest("rick", "first password"));

        assertThrows(AuthenticationErrorException.class,
                () -> service.createActiveAccount(
                        new ActiveAccountRequest("rick", "second password")));

        assertTrue(Files.isDirectory(first.getHome()));
        assertEquals(1L, store.authenticate("rick", "first password"));
        assertFalse(Files.exists(accountRoot.resolve("2")));
    }

    private static AccountLifecycleService service(
            final CredentialStore store,
            AccountLifecycleService.WorkspaceAuthority workspaces) {
        assertNotNull(store);
        return new AccountLifecycleService(
                new AccountLifecycleService.CredentialAuthority() {
                    @Override
                    public long createPrepared(
                            String login,
                            String password,
                            final AccountLifecycleService.Preparation preparation)
                            throws Exception {
                        return store.createPrepared(
                                login,
                                password,
                                preparation::prepare);
                    }

                    @Override
                    public boolean delete(long userId) throws Exception {
                        return store.delete(userId);
                    }

                    @Override
                    public Long findUserId(String login) throws Exception {
                        return store.findUserId(login);
                    }
                },
                workspaces);
    }

    private static Properties load(Path file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
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
