/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kanger.security.CredentialMaterial;
import org.kanger.security.CredentialStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PendingDeletionTombstoneTest {

    private Path directory;
    private Path accountRoot;
    private CredentialStore credentials;
    private AccountDeletionStore deletions;
    private PendingRegistrationStore pendingStore;
    private PendingRegistrationService pendingService;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-pending-deletion-");
        accountRoot = directory.resolve("KANGER");
        credentials = new CredentialStore(directory.resolve("users.conf"));
        deletions = new AccountDeletionStore(
                accountRoot.resolve("account-deletions.conf"));
        AccountLifecycleService accounts = new AccountLifecycleService(
                credentialAuthority(credentials),
                new FileAccountWorkspace(accountRoot, directory.toString()),
                new NoopRuntime(),
                deletions,
                accountRoot.resolve(".quarantine"));
        pendingStore = new PendingRegistrationStore(
                accountRoot.resolve("pending-registrations.conf"),
                7L * 24L * 60L * 60L * 1000L,
                24L * 60L * 60L * 1000L,
                15L * 60L * 1000L,
                0L,
                100);
        pendingService = new PendingRegistrationService(pendingStore, accounts);

        deletions.prepare(
                7L,
                "rick",
                "rick@example.org",
                accountRoot.resolve("7"),
                accountRoot.resolve(".quarantine"));
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
    void preparedDeletionBlocksNewPendingRegistration() {
        PendingRegistrationException failure = assertThrows(
                PendingRegistrationException.class,
                () -> pendingService.register(
                        "rick",
                        "new password",
                        "rick@example.org",
                        "Rick",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE));

        assertEquals(AccountErrorCode.LOGIN_ALREADY_USED, failure.getCode());
    }

    @Test
    void preparedDeletionBlocksConfirmationAndPreservesPendingIntent()
            throws Exception {
        CredentialMaterial material = credentials.preparePassword(
                "pending password");
        PendingRegistrationStore.Created created = pendingStore.create(
                new PendingRegistrationStore.Draft(
                        "rick",
                        "rick@example.org",
                        material,
                        "Rick",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE));

        PendingRegistrationException failure = assertThrows(
                PendingRegistrationException.class,
                () -> pendingService.confirm(created.getConfirmationToken()));

        assertEquals(AccountErrorCode.LOGIN_ALREADY_USED, failure.getCode());
        assertEquals(1, pendingStore.size());
        assertEquals(created.getRegistration().getId(),
                pendingStore.resolveConfirmation(
                        created.getConfirmationToken()).getId());
    }

    private static AccountLifecycleService.CredentialAuthority credentialAuthority(
            final CredentialStore store) {
        return new AccountLifecycleService.CredentialAuthority() {
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
            public boolean deletePrepared(
                    long userId,
                    final AccountLifecycleService.DeletionPreparation preparation)
                    throws Exception {
                return store.deletePrepared(userId, preparation::prepare);
            }

            @Override
            public boolean delete(long userId) throws Exception {
                return store.delete(userId);
            }

            @Override
            public Long findUserId(String login) throws Exception {
                return store.findUserId(login);
            }

            @Override
            public String findLogin(long userId) throws Exception {
                return store.findLogin(userId);
            }

            @Override
            public boolean containsUserId(long userId) throws Exception {
                return store.containsUserId(userId);
            }
        };
    }

    private static final class NoopRuntime
            implements AccountLifecycleService.RuntimeAuthority {
        @Override
        public void closeUser(long userId) {
        }

        @Override
        public void revokeLegacyConfirmation(long userId) {
        }

        @Override
        public void revokePending(String login) {
        }
    }
}
