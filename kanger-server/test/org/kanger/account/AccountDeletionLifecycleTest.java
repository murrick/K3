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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountDeletionLifecycleTest {

    private Path directory;
    private Path accountRoot;
    private Path credentialFile;
    private CredentialStore store;
    private FileAccountWorkspace workspace;
    private AccountDeletionStore deletions;
    private RecordingRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-deletion-lifecycle-");
        accountRoot = directory.resolve("KANGER");
        credentialFile = directory.resolve("users.conf");
        store = new CredentialStore(credentialFile);
        workspace = new FileAccountWorkspace(accountRoot, directory.toString());
        deletions = new AccountDeletionStore(
                accountRoot.resolve("account-deletions.conf"));
        runtime = new RecordingRuntime();
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
    void successfulDeletionRevokesCredentialAndQuarantinesHome()
            throws Exception {
        AccountLifecycleService service = service(workspace, runtime);
        ActiveAccount account = service.createActiveAccount(
                new ActiveAccountRequest(
                        "rick",
                        "correct password",
                        "rick@example.org",
                        "Rick",
                        "Austria",
                        "Vienna",
                        Boolean.TRUE));

        AccountDeletion deletion = service.deleteActiveAccountByUserId(
                account.getUserId());

        assertEquals(AccountDeletionState.COMPLETE, deletion.getState());
        assertFalse(Files.exists(deletion.getCanonicalHome()));
        assertTrue(Files.isDirectory(deletion.getQuarantineHome()));
        assertEquals(1, runtime.closed.get());
        assertTrue(runtime.confirmations.get() >= 1);
        assertTrue(runtime.pending.get() >= 1);
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "correct password"));
        assertEquals(deletion.getId(),
                deletions.findByUserId(account.getUserId()).getId());
    }

    @Test
    void failureBeforeCredentialRemovalLeavesAccountUsable()
            throws Exception {
        runtime.failClose = true;
        AccountLifecycleService service = service(workspace, runtime);
        ActiveAccount account = service.createActiveAccount(
                new ActiveAccountRequest("rick", "correct password"));

        AccountDeletionIncompleteException failure = assertThrows(
                AccountDeletionIncompleteException.class,
                () -> service.deleteActiveAccountByUserId(account.getUserId()));

        assertEquals(AccountDeletionState.PREPARED,
                failure.getDeletion().getState());
        assertEquals(account.getUserId(),
                store.authenticate("rick", "correct password"));
        assertTrue(Files.isDirectory(account.getHome()));
        assertFalse(Files.exists(failure.getDeletion().getQuarantineHome()));

        runtime.failClose = false;
        AccountDeletion completed = service.resumeDeletion(
                failure.getDeletion().getId());
        assertEquals(AccountDeletionState.COMPLETE, completed.getState());
    }

    @Test
    void quarantineFailureAfterCredentialRemovalRecoversForward()
            throws Exception {
        FailingQuarantineWorkspace failing = new FailingQuarantineWorkspace(
                workspace, false);
        AccountLifecycleService service = service(failing, runtime);
        ActiveAccount account = service.createActiveAccount(
                new ActiveAccountRequest("rick", "correct password"));

        AccountDeletionIncompleteException failure = assertThrows(
                AccountDeletionIncompleteException.class,
                () -> service.deleteActiveAccountByUserId(account.getUserId()));

        assertEquals(AccountDeletionState.CREDENTIAL_REMOVED,
                failure.getDeletion().getState());
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "correct password"));
        assertTrue(Files.isDirectory(account.getHome()));

        AccountDeletion completed = service.resumeDeletion(
                failure.getDeletion().getId());
        assertEquals(AccountDeletionState.COMPLETE, completed.getState());
        assertFalse(Files.exists(account.getHome()));
        assertTrue(Files.isDirectory(completed.getQuarantineHome()));
    }

    @Test
    void crashAfterHomeMoveDoesNotCreateSecondQuarantineTree()
            throws Exception {
        FailingQuarantineWorkspace failing = new FailingQuarantineWorkspace(
                workspace, true);
        AccountLifecycleService service = service(failing, runtime);
        ActiveAccount account = service.createActiveAccount(
                new ActiveAccountRequest("rick", "correct password"));

        AccountDeletionIncompleteException failure = assertThrows(
                AccountDeletionIncompleteException.class,
                () -> service.deleteActiveAccountByUserId(account.getUserId()));

        assertEquals(AccountDeletionState.CREDENTIAL_REMOVED,
                failure.getDeletion().getState());
        assertFalse(Files.exists(account.getHome()));
        assertTrue(Files.isDirectory(failure.getDeletion().getQuarantineHome()));

        AccountDeletion completed = service.resumeDeletion(
                failure.getDeletion().getId());
        assertEquals(AccountDeletionState.COMPLETE, completed.getState());
        assertEquals(failure.getDeletion().getQuarantineHome(),
                completed.getQuarantineHome());
        try (Stream<Path> entries = Files.list(accountRoot.resolve(".quarantine"))) {
            assertEquals(1L, entries.count());
        }
    }

    @Test
    void completedDeletionPermitsIdentityReuseButNeverUserIdReuse()
            throws Exception {
        AccountLifecycleService service = service(workspace, runtime);
        ActiveAccount first = service.createActiveAccount(
                new ActiveAccountRequest(
                        "rick", "first password", "rick@example.org",
                        "Rick", "Austria", "Vienna", Boolean.TRUE));
        AccountDeletion deletion = service.deleteActiveAccountByUserId(
                first.getUserId());
        assertEquals(AccountDeletionState.COMPLETE, deletion.getState());

        ActiveAccount replacement = service.createActiveAccount(
                new ActiveAccountRequest(
                        "rick", "second password", "rick@example.org",
                        "Rick", "Austria", "Vienna", Boolean.TRUE));

        assertEquals(2L, replacement.getUserId());
        assertEquals(2L, store.authenticate("rick", "second password"));
        assertTrue(Files.isDirectory(deletion.getQuarantineHome()));
    }

    @Test
    void deleteByLoginCrossChecksCredentialAndWorkspaceIdentity()
            throws Exception {
        AccountLifecycleService service = service(workspace, runtime);
        ActiveAccount account = service.createActiveAccount(
                new ActiveAccountRequest("rick", "correct password"));

        AccountDeletion deletion = service.deleteActiveAccountByLogin("rick");

        assertEquals(account.getUserId(), deletion.getUserId());
        assertEquals(AccountDeletionState.COMPLETE, deletion.getState());
    }

    private AccountLifecycleService service(
            AccountLifecycleService.WorkspaceAuthority workspaceAuthority,
            AccountLifecycleService.RuntimeAuthority runtimeAuthority)
            throws Exception {
        return new AccountLifecycleService(
                credentialAuthority(store),
                workspaceAuthority,
                runtimeAuthority,
                deletions,
                accountRoot.resolve(".quarantine"));
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
                return store.deletePrepared(
                        userId, preparation::prepare);
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

    private static final class RecordingRuntime
            implements AccountLifecycleService.RuntimeAuthority {
        private final AtomicInteger closed = new AtomicInteger();
        private final AtomicInteger confirmations = new AtomicInteger();
        private final AtomicInteger pending = new AtomicInteger();
        private boolean failClose;

        @Override
        public void closeUser(long userId) {
            closed.incrementAndGet();
            if (failClose) {
                throw new IllegalStateException("synthetic runtime close failure");
            }
        }

        @Override
        public void revokeLegacyConfirmation(long userId) {
            confirmations.incrementAndGet();
        }

        @Override
        public void revokePending(String login) {
            pending.incrementAndGet();
        }
    }

    private static final class FailingQuarantineWorkspace
            implements AccountLifecycleService.WorkspaceAuthority {
        private final FileAccountWorkspace delegate;
        private final boolean moveBeforeFailure;
        private boolean failed;

        private FailingQuarantineWorkspace(FileAccountWorkspace delegate,
                                           boolean moveBeforeFailure) {
            this.delegate = delegate;
            this.moveBeforeFailure = moveBeforeFailure;
        }

        @Override
        public AccountLifecycleService.PreparedWorkspace prepare(
                long userId,
                ActiveAccountRequest request) throws Exception {
            return delegate.prepare(userId, request);
        }

        @Override
        public ActiveAccountIdentity inspect(long userId) throws Exception {
            return delegate.inspect(userId);
        }

        @Override
        public Path quarantine(AccountDeletion deletion) throws Exception {
            if (!failed) {
                failed = true;
                if (moveBeforeFailure) {
                    delegate.quarantine(deletion);
                }
                throw new IOException("synthetic quarantine failure");
            }
            return delegate.quarantine(deletion);
        }

        @Override
        public Long findUserIdByLogin(String login) throws Exception {
            return delegate.findUserIdByLogin(login);
        }

        @Override
        public Long findUserIdByEmail(String email) throws Exception {
            return delegate.findUserIdByEmail(email);
        }

        @Override
        public boolean hasActivationReference(long userId, String reference)
                throws Exception {
            return delegate.hasActivationReference(userId, reference);
        }
    }
}
