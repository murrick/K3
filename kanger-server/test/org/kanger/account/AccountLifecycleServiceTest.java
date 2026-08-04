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
import org.kanger.security.CredentialMaterial;
import org.kanger.security.CredentialStore;

import java.io.File;
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
    void operatorPublishesActiveAccountWithoutClaimingEmailVerification()
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
        assertEquals("false", profile.getProperty("reg.agreed"));
        assertEquals("false", profile.getProperty("reg.email.confirmed"));
        assertEquals("true", profile.getProperty("reg.privacy"));
        assertEquals(directory(account.getHome()), profile.getProperty("user.dir"));
        assertEquals(directory(account.getHome().resolve("SRC")),
                profile.getProperty("sources.dir"));
        assertEquals(directory(account.getHome().resolve("DB")),
                profile.getProperty("database.dir"));
        for (Object value : profile.values()) {
            assertFalse(value.toString().contains(".creating"));
        }
        assertFalse(profile.containsValue("correct horse battery staple"));
    }

    @Test
    void persistedCredentialMaterialActivatesVerifiedEmailWithoutPassword()
            throws Exception {
        CredentialStore store = new CredentialStore(credentialFile);
        AccountLifecycleService service = service(
                store,
                new FileAccountWorkspace(accountRoot, directory.toString()));
        CredentialMaterial prepared = service.prepareCredential("pending password");
        CredentialMaterial restored = CredentialMaterial.decode(prepared.encode());

        ActiveAccount account = service.createActiveAccount(
                new ActiveAccountRequest(
                        "pending-user",
                        restored,
                        AccountActivationSource.EMAIL_CONFIRMATION,
                        "pending@example.org",
                        "Pending User",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE));

        assertEquals(1L, account.getUserId());
        assertEquals(1L,
                store.authenticate("pending-user", "pending password"));
        Properties profile = load(account.getHome().resolve("kanger.conf"));
        assertEquals("true", profile.getProperty("reg.agreed"));
        assertEquals("true", profile.getProperty("reg.email.confirmed"));
        assertFalse(new String(
                Files.readAllBytes(account.getHome().resolve("kanger.conf")),
                StandardCharsets.UTF_8).contains("pending password"));
    }

    @Test
    void emailConfirmationAuthorityRequiresAnEmailAddress() throws Exception {
        CredentialStore store = new CredentialStore(credentialFile);
        CredentialMaterial material = store.preparePassword("pending password");

        assertThrows(IllegalArgumentException.class,
                () -> new ActiveAccountRequest(
                        "pending-user",
                        material,
                        AccountActivationSource.EMAIL_CONFIRMATION,
                        "",
                        "Pending User",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE));
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
        final CredentialStore materialStore = new CredentialStore(
                directory.resolve("material.conf"));
        AccountLifecycleService.CredentialAuthority failingCredentials =
                new AccountLifecycleService.CredentialAuthority() {
                    @Override
                    public CredentialMaterial preparePassword(String password)
                            throws Exception {
                        return materialStore.preparePassword(password);
                    }

                    @Override
                    public long createPrepared(
                            String login,
                            CredentialMaterial material,
                            AccountLifecycleService.Preparation preparation)
                            throws Exception {
                        preparation.prepare(17L);
                        throw new IOException("synthetic credential publication failure");
                    }

                    @Override
                    public long publishPrepared(long userId,
                                                String login,
                                                CredentialMaterial material) {
                        throw new AssertionError("recovery publication must not be called");
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
    void existingCanonicalHomeIsNeverOverwrittenOrPublishedAsCredential()
            throws Exception {
        CredentialStore store = new CredentialStore(credentialFile);
        Path existingHome = accountRoot.resolve("1");
        Files.createDirectories(existingHome);
        Path sentinel = existingHome.resolve("preserve.me");
        Files.write(sentinel,
                java.util.Collections.singletonList("operator recovery evidence"),
                StandardCharsets.UTF_8);
        AccountLifecycleService service = service(
                store,
                new FileAccountWorkspace(accountRoot, directory.toString()));

        IOException failure = assertThrows(IOException.class,
                () -> service.createActiveAccount(
                        new ActiveAccountRequest("rick", "secret")));

        assertTrue(failure.getMessage().contains("already exists"));
        assertTrue(Files.isRegularFile(sentinel));
        assertFalse(Files.exists(accountRoot.resolve(".creating")));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "secret"));
    }

    @Test
    void exactRecoveryPublishesCredentialForMatchingExistingWorkspace()
            throws Exception {
        CredentialStore store = new CredentialStore(credentialFile);
        FileAccountWorkspace workspace = new FileAccountWorkspace(
                accountRoot, directory.toString());
        AccountLifecycleService service = service(store, workspace);
        CredentialMaterial material = store.preparePassword("pending password");
        ActiveAccountRequest request = new ActiveAccountRequest(
                "rick",
                material,
                AccountActivationSource.EMAIL_CONFIRMATION,
                "rick@example.org",
                "Rick",
                "Austria",
                "Vienna",
                Boolean.TRUE,
                "pending-reference");
        AccountLifecycleService.PreparedWorkspace prepared =
                workspace.prepare(7L, request);
        prepared.publish();

        long userId = service.publishCredentialForExistingWorkspace(
                7L, "rick", material, "pending-reference");

        assertEquals(7L, userId);
        assertEquals(7L, store.authenticate("rick", "pending password"));
        assertTrue(Files.isDirectory(accountRoot.resolve("7")));
    }

    @Test
    void exactRecoveryRejectsDifferentActivationReference() throws Exception {
        CredentialStore store = new CredentialStore(credentialFile);
        FileAccountWorkspace workspace = new FileAccountWorkspace(
                accountRoot, directory.toString());
        AccountLifecycleService service = service(store, workspace);
        CredentialMaterial material = store.preparePassword("pending password");
        AccountLifecycleService.PreparedWorkspace prepared = workspace.prepare(
                7L,
                new ActiveAccountRequest(
                        "rick",
                        material,
                        AccountActivationSource.EMAIL_CONFIRMATION,
                        "rick@example.org",
                        "Rick",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE,
                        "original-reference"));
        prepared.publish();

        assertThrows(IllegalStateException.class,
                () -> service.publishCredentialForExistingWorkspace(
                        7L, "rick", material, "different-reference"));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "pending password"));
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
                    public CredentialMaterial preparePassword(String password)
                            throws Exception {
                        return store.preparePassword(password);
                    }

                    @Override
                    public long createPrepared(
                            String login,
                            CredentialMaterial material,
                            final AccountLifecycleService.Preparation preparation)
                            throws Exception {
                        return store.createPrepared(
                                login,
                                material,
                                preparation::prepare);
                    }

                    @Override
                    public long publishPrepared(long userId,
                                                String login,
                                                CredentialMaterial material)
                            throws Exception {
                        return store.publishPrepared(userId, login, material);
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

    private static String directory(Path path) {
        return path.toAbsolutePath().normalize().toString() + File.separator;
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
