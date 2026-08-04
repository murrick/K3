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

class PendingRegistrationServiceTest {

    private Path directory;
    private Path accountRoot;
    private Path credentialFile;
    private Path pendingFile;
    private CredentialStore credentials;
    private AccountLifecycleService accounts;
    private PendingRegistrationStore pending;
    private PendingRegistrationService service;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-pending-service-");
        accountRoot = directory.resolve("KANGER");
        credentialFile = directory.resolve("users.conf");
        pendingFile = accountRoot.resolve("pending-registrations.conf");
        credentials = new CredentialStore(credentialFile);
        accounts = accountService(
                credentials,
                new FileAccountWorkspace(accountRoot, directory.toString()));
        pending = pendingStore();
        service = new PendingRegistrationService(pending, accounts);
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
    void registrationCreatesOnlyPersistentPendingIntent() throws Exception {
        PendingRegistrationStore.Created created = register(service);

        assertNotNull(created.getConfirmationToken());
        assertEquals(1, service.pendingCount());
        assertTrue(Files.isRegularFile(pendingFile));
        assertFalse(Files.exists(accountRoot.resolve("1")));
        assertThrows(AuthenticationErrorException.class,
                () -> credentials.authenticate("rick", "pending password"));

        String raw = new String(Files.readAllBytes(pendingFile), StandardCharsets.UTF_8);
        assertFalse(raw.contains("pending password"));
        assertFalse(raw.contains(created.getConfirmationToken()));
    }

    @Test
    void successfulConfirmationPublishesVerifiedAccountAndRemovesPending()
            throws Exception {
        PendingRegistrationStore.Created created = register(service);

        PendingRegistrationService.Activation activation =
                service.confirm(created.getConfirmationToken());

        assertFalse(activation.isRecovered());
        assertEquals(1L, activation.getUserId());
        assertEquals(0, service.pendingCount());
        assertEquals(1L, credentials.authenticate("rick", "pending password"));

        Path home = accountRoot.resolve("1");
        Properties profile = load(home.resolve("kanger.conf"));
        assertEquals("true", profile.getProperty("reg.email.confirmed"));
        assertEquals("true", profile.getProperty("reg.agreed"));
        assertEquals("rick@example.org", profile.getProperty("reg.email"));
        assertEquals(created.getRegistration().getId(),
                profile.getProperty("reg.activation.reference"));
    }

    @Test
    void activationFailureLeavesPendingRetryableAndCreatesNoAccount()
            throws Exception {
        FileAccountWorkspace failingWorkspace = new FileAccountWorkspace(
                accountRoot,
                directory.toString(),
                (userId, home, source, database) -> {
                    throw new IllegalStateException("synthetic activation failure");
                });
        PendingRegistrationService failing = new PendingRegistrationService(
                pending,
                accountService(credentials, failingWorkspace));
        PendingRegistrationStore.Created created = register(failing);

        assertThrows(IllegalStateException.class,
                () -> failing.confirm(created.getConfirmationToken()));

        assertEquals(1, failing.pendingCount());
        assertEquals(created.getRegistration().getId(),
                pending.resolveConfirmation(created.getConfirmationToken()).getId());
        assertFalse(Files.exists(accountRoot.resolve("1")));
        assertThrows(AuthenticationErrorException.class,
                () -> credentials.authenticate("rick", "pending password"));
    }

    @Test
    void repeatedConfirmationReconcilesPostPublicationCrashByExactReference()
            throws Exception {
        PendingRegistrationStore.Created created = register(service);
        PendingRegistration record = pending.resolveConfirmation(
                created.getConfirmationToken());

        ActiveAccount published = accounts.createActiveAccount(
                new ActiveAccountRequest(
                        record.getLogin(),
                        record.getCredentialMaterial(),
                        AccountActivationSource.EMAIL_CONFIRMATION,
                        record.getEmail(),
                        record.getName(),
                        record.getCountry(),
                        record.getCity(),
                        record.getPrivacyConsent(),
                        record.getId()));

        assertEquals(1L, published.getUserId());
        assertEquals(1, service.pendingCount());

        PendingRegistrationService.Activation recovered =
                service.confirm(created.getConfirmationToken());

        assertTrue(recovered.isRecovered());
        assertEquals(published.getUserId(), recovered.getUserId());
        assertEquals(0, service.pendingCount());
        assertFalse(Files.exists(accountRoot.resolve("2")));
    }

    @Test
    void repeatedConfirmationPublishesMissingCredentialForExactHome()
            throws Exception {
        PendingRegistrationStore.Created created = register(service);
        PendingRegistration record = pending.resolveConfirmation(
                created.getConfirmationToken());
        FileAccountWorkspace workspace = new FileAccountWorkspace(
                accountRoot, directory.toString());
        AccountLifecycleService.PreparedWorkspace prepared = workspace.prepare(
                7L,
                new ActiveAccountRequest(
                        record.getLogin(),
                        record.getCredentialMaterial(),
                        AccountActivationSource.EMAIL_CONFIRMATION,
                        record.getEmail(),
                        record.getName(),
                        record.getCountry(),
                        record.getCity(),
                        record.getPrivacyConsent(),
                        record.getId()));
        prepared.publish();

        assertTrue(Files.isDirectory(accountRoot.resolve("7")));
        assertThrows(AuthenticationErrorException.class,
                () -> credentials.authenticate("rick", "pending password"));
        assertEquals(1, service.pendingCount());

        PendingRegistrationService.Activation recovered =
                service.confirm(created.getConfirmationToken());

        assertTrue(recovered.isRecovered());
        assertEquals(7L, recovered.getUserId());
        assertEquals(7L, credentials.authenticate("rick", "pending password"));
        assertEquals(0, service.pendingCount());
        assertFalse(Files.exists(accountRoot.resolve("1")));
    }

    @Test
    void unrelatedHomeWithSameLoginDoesNotConsumePending() throws Exception {
        PendingRegistrationStore.Created created = register(service);
        PendingRegistration record = pending.resolveConfirmation(
                created.getConfirmationToken());
        FileAccountWorkspace workspace = new FileAccountWorkspace(
                accountRoot, directory.toString());
        AccountLifecycleService.PreparedWorkspace prepared = workspace.prepare(
                7L,
                new ActiveAccountRequest(
                        record.getLogin(),
                        record.getCredentialMaterial(),
                        AccountActivationSource.EMAIL_CONFIRMATION,
                        record.getEmail(),
                        record.getName(),
                        record.getCountry(),
                        record.getCity(),
                        record.getPrivacyConsent(),
                        "different-pending-reference"));
        prepared.publish();

        PendingRegistrationException failure = assertThrows(
                PendingRegistrationException.class,
                () -> service.confirm(created.getConfirmationToken()));

        assertEquals(AccountErrorCode.LOGIN_ALREADY_USED, failure.getCode());
        assertEquals(1, service.pendingCount());
        assertThrows(AuthenticationErrorException.class,
                () -> credentials.authenticate("rick", "pending password"));
    }

    @Test
    void existingActiveLoginOrEmailRejectsNewPendingRegistration()
            throws Exception {
        accounts.createActiveAccount(new ActiveAccountRequest(
                "active",
                "active password",
                "active@example.org",
                "Active User",
                "Austria",
                "Vienna",
                Boolean.TRUE));

        PendingRegistrationException loginFailure = assertThrows(
                PendingRegistrationException.class,
                () -> service.register(
                        "active",
                        "other password",
                        "other@example.org",
                        "Other",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE));
        assertEquals(AccountErrorCode.LOGIN_ALREADY_USED, loginFailure.getCode());

        PendingRegistrationException emailFailure = assertThrows(
                PendingRegistrationException.class,
                () -> service.register(
                        "other",
                        "other password",
                        "ACTIVE@example.org",
                        "Other",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE));
        assertEquals(AccountErrorCode.EMAIL_ALREADY_USED, emailFailure.getCode());
    }

    @Test
    void legacyProfileLoginBlocksPendingEvenBeforeCredentialMigration()
            throws Exception {
        Path legacyHome = accountRoot.resolve("7");
        Files.createDirectories(legacyHome);
        Properties profile = new Properties();
        profile.setProperty("reg.login", "legacy");
        profile.setProperty("reg.email", "legacy@example.org");
        try (java.io.Writer writer = Files.newBufferedWriter(
                legacyHome.resolve("kanger.conf"), StandardCharsets.UTF_8)) {
            profile.store(writer, "legacy profile");
        }
        Files.write(credentialFile,
                java.util.Collections.singletonList(
                        CredentialStore.legacyToken("legacy", "legacy password") + "=7"),
                StandardCharsets.UTF_8);

        PendingRegistrationException failure = assertThrows(
                PendingRegistrationException.class,
                () -> service.register(
                        "legacy",
                        "new password",
                        "new@example.org",
                        "Legacy",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE));

        assertEquals(AccountErrorCode.LOGIN_ALREADY_USED, failure.getCode());
        assertEquals(0, service.pendingCount());
    }

    private PendingRegistrationStore.Created register(
            PendingRegistrationService target) throws Exception {
        return target.register(
                "rick",
                "pending password",
                "Rick@Example.org",
                "Rick",
                "Austria",
                "Vienna",
                Boolean.TRUE);
    }

    private PendingRegistrationStore pendingStore() throws Exception {
        return new PendingRegistrationStore(
                pendingFile,
                7L * 24L * 60L * 60L * 1000L,
                24L * 60L * 60L * 1000L,
                15L * 60L * 1000L,
                0L,
                100);
    }

    private static AccountLifecycleService accountService(
            final CredentialStore store,
            AccountLifecycleService.WorkspaceAuthority workspaces) {
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
                                login, material, preparation::prepare);
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
}
