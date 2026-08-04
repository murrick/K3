/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.kanger.UserFactory;
import org.kanger.security.ConfirmationTokenStore;
import org.kanger.security.CredentialMaterial;
import org.kanger.security.CredentialStore;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Sole owner of complete ACTIVE account publication and safe account deletion.
 *
 * <p>Creation publishes the workspace before the credential. Deletion is a
 * persistent forward-only state machine: after credential removal the service
 * never restores public authentication as an automatic rollback.</p>
 */
public final class AccountLifecycleService {

    interface Preparation {
        void prepare(long userId) throws Exception;
    }

    interface DeletionPreparation {
        void prepare(long userId) throws Exception;
    }

    interface CredentialAuthority {
        CredentialMaterial preparePassword(String password) throws Exception;

        long createPrepared(String login,
                            CredentialMaterial material,
                            Preparation preparation) throws Exception;

        long publishPrepared(long userId,
                             String login,
                             CredentialMaterial material) throws Exception;

        default boolean deletePrepared(long userId,
                                       DeletionPreparation preparation)
                throws Exception {
            preparation.prepare(userId);
            return delete(userId);
        }

        boolean delete(long userId) throws Exception;

        Long findUserId(String login) throws Exception;

        default String findLogin(long userId) throws Exception {
            return null;
        }

        default boolean containsUserId(long userId) throws Exception {
            return false;
        }
    }

    interface WorkspaceAuthority {
        PreparedWorkspace prepare(long userId,
                                  ActiveAccountRequest request) throws Exception;

        ActiveAccountIdentity inspect(long userId) throws Exception;

        Path quarantine(AccountDeletion deletion) throws Exception;

        Long findUserIdByLogin(String login) throws Exception;

        Long findUserIdByEmail(String email) throws Exception;

        boolean hasActivationReference(long userId, String reference)
                throws Exception;
    }

    interface RuntimeAuthority {
        void closeUser(long userId) throws Exception;

        void revokeLegacyConfirmation(long userId) throws Exception;

        void revokePending(String login) throws Exception;
    }

    interface PreparedWorkspace {
        Path home();

        void publish() throws Exception;

        void rollback() throws Exception;
    }

    private static volatile AccountLifecycleService runtime;

    private final CredentialAuthority credentials;
    private final WorkspaceAuthority workspaces;
    private final RuntimeAuthority runtimeAuthority;
    private final AccountDeletionStore deletions;
    private final Path quarantineRoot;

    AccountLifecycleService(CredentialAuthority credentials,
                            WorkspaceAuthority workspaces) {
        this(credentials,
                workspaces,
                new NoopRuntimeAuthority(),
                null,
                null);
    }

    AccountLifecycleService(CredentialAuthority credentials,
                            WorkspaceAuthority workspaces,
                            RuntimeAuthority runtimeAuthority,
                            AccountDeletionStore deletions,
                            Path quarantineRoot) {
        if (credentials == null || workspaces == null || runtimeAuthority == null) {
            throw new IllegalArgumentException(
                    "credential, workspace and runtime authorities must not be null");
        }
        if ((deletions == null) != (quarantineRoot == null)) {
            throw new IllegalArgumentException(
                    "deletion journal and quarantine root must be configured together");
        }
        this.credentials = credentials;
        this.workspaces = workspaces;
        this.runtimeAuthority = runtimeAuthority;
        this.deletions = deletions;
        this.quarantineRoot = quarantineRoot == null
                ? null : quarantineRoot.toAbsolutePath().normalize();
    }

    /**
     * Returns the server-runtime lifecycle service over the same credential,
     * pending, confirmation and account roots used by the public server.
     */
    public static AccountLifecycleService runtime() {
        AccountLifecycleService current = runtime;
        if (current != null) {
            return current;
        }
        synchronized (AccountLifecycleService.class) {
            if (runtime == null) {
                try {
                    Path credentialFile = resolveCredentialFile();
                    Path accountRoot = Paths.get(
                            UserFactory.getDir(UserFactory.rootDir));
                    CredentialStore store = new CredentialStore(credentialFile);
                    runtime = new AccountLifecycleService(
                            new StoreCredentialAuthority(store),
                            new FileAccountWorkspace(
                                    accountRoot, UserFactory.getHome()),
                            new ServerRuntimeAuthority(
                                    new ConfirmationTokenStore(
                                            resolveConfirmationFile())),
                            new AccountDeletionStore(
                                    accountRoot.resolve("account-deletions.conf")),
                            accountRoot.resolve(".quarantine"));
                } catch (Exception failure) {
                    throw new IllegalStateException(
                            "Account lifecycle runtime could not be initialized",
                            failure);
                }
            }
            return runtime;
        }
    }

    static void resetRuntimeForTests() {
        synchronized (AccountLifecycleService.class) {
            runtime = null;
        }
    }

    /**
     * Derives persistable verifier material for a PendingRegistration without
     * creating an account, allocating an id or publishing a credential.
     */
    public CredentialMaterial prepareCredential(String password) throws Exception {
        return credentials.preparePassword(password);
    }

    /**
     * Creates one complete ACTIVE account without opening an authenticated
     * session.
     */
    public ActiveAccount createActiveAccount(final ActiveAccountRequest request)
            throws Exception {
        return AccountRegistrationAuthority.execute(
                new AccountRegistrationAuthority.Work<ActiveAccount>() {
                    @Override
                    public ActiveAccount run() throws Exception {
                        return createActiveAccountLocked(request);
                    }
                });
    }

    private ActiveAccount createActiveAccountLocked(
            final ActiveAccountRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException(
                    "active account request must not be null");
        }
        AccountDeletion deleting = findDeletionByLogin(request.getLogin());
        if (deleting != null && !deleting.isComplete()) {
            throw new IllegalStateException(
                    "Account login is reserved by deletion " + deleting.getId());
        }

        final CredentialMaterial material = request.hasPreparedCredential()
                ? request.getCredentialMaterial()
                : credentials.preparePassword(request.getPassword());
        final PreparedWorkspace[] prepared = new PreparedWorkspace[1];
        try {
            long userId = credentials.createPrepared(
                    request.getLogin(),
                    material,
                    new Preparation() {
                        @Override
                        public void prepare(long userId) throws Exception {
                            prepared[0] = workspaces.prepare(userId, request);
                            prepared[0].publish();
                        }
                    });
            return new ActiveAccount(
                    userId, request.getLogin(), prepared[0].home());
        } catch (Exception failure) {
            if (prepared[0] != null) {
                try {
                    prepared[0].rollback();
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * Completes the crash window where a canonical account home with the exact
     * pending activation reference exists but its credential snapshot was not
     * yet published.
     */
    long publishCredentialForExistingWorkspace(long userId,
                                               String login,
                                               CredentialMaterial material,
                                               String activationReference)
            throws Exception {
        Long workspaceUserId = workspaces.findUserIdByLogin(login);
        if (workspaceUserId == null || workspaceUserId.longValue() != userId) {
            throw new IllegalStateException(
                    "Recovery workspace does not match the requested login and user id");
        }
        if (!workspaces.hasActivationReference(userId, activationReference)) {
            throw new IllegalStateException(
                    "Recovery workspace does not match the pending activation reference");
        }
        return credentials.publishPrepared(userId, login, material);
    }

    public AccountDeletion deleteActiveAccountByLogin(final String login)
            throws Exception {
        if (login == null || login.trim().isEmpty()) {
            throw new IllegalArgumentException("login must not be empty");
        }
        return AccountRegistrationAuthority.execute(
                new AccountRegistrationAuthority.Work<AccountDeletion>() {
                    @Override
                    public AccountDeletion run() throws Exception {
                        AccountDeletion journal = findDeletionByLogin(login);
                        if (journal != null && !journal.isComplete()) {
                            return continueDeletion(journal);
                        }

                        Long credentialUserId = credentials.findUserId(login.trim());
                        Long workspaceUserId = workspaces.findUserIdByLogin(login.trim());
                        if (credentialUserId != null && workspaceUserId != null
                                && credentialUserId.longValue()
                                != workspaceUserId.longValue()) {
                            throw new IllegalStateException(
                                    "Credential and workspace resolve login to different users");
                        }
                        Long resolved = credentialUserId != null
                                ? credentialUserId : workspaceUserId;
                        if (resolved == null) {
                            if (journal != null) {
                                return journal;
                            }
                            throw new IllegalStateException(
                                    "Account login does not exist: " + login);
                        }
                        return deleteActiveAccountLocked(resolved.longValue());
                    }
                });
    }

    public AccountDeletion deleteActiveAccountByUserId(final long userId)
            throws Exception {
        if (userId <= 0L) {
            throw new IllegalArgumentException("user id must be positive");
        }
        return AccountRegistrationAuthority.execute(
                new AccountRegistrationAuthority.Work<AccountDeletion>() {
                    @Override
                    public AccountDeletion run() throws Exception {
                        return deleteActiveAccountLocked(userId);
                    }
                });
    }

    public AccountDeletion resumeDeletion(final String deletionId)
            throws Exception {
        requireDeletionSupport();
        if (deletionId == null || deletionId.isEmpty()) {
            throw new IllegalArgumentException("deletion id must not be empty");
        }
        return AccountRegistrationAuthority.execute(
                new AccountRegistrationAuthority.Work<AccountDeletion>() {
                    @Override
                    public AccountDeletion run() throws Exception {
                        AccountDeletion deletion = deletions.findById(deletionId);
                        if (deletion == null) {
                            throw new IllegalStateException(
                                    "Deletion does not exist: " + deletionId);
                        }
                        return continueDeletion(deletion);
                    }
                });
    }

    private AccountDeletion deleteActiveAccountLocked(long userId)
            throws Exception {
        requireDeletionSupport();
        AccountDeletion existing = deletions.findByUserId(userId);
        if (existing != null) {
            return continueDeletion(existing);
        }

        ActiveAccountIdentity identity = workspaces.inspect(userId);
        if (identity == null) {
            throw new IllegalStateException(
                    "Canonical account home does not exist for user " + userId);
        }
        if (!credentials.containsUserId(userId)) {
            throw new IllegalStateException(
                    "Credential does not exist for canonical account user " + userId);
        }
        String versionedLogin = credentials.findLogin(userId);
        if (versionedLogin != null
                && !versionedLogin.equals(identity.getLogin())) {
            throw new IllegalStateException(
                    "Credential login and canonical profile login disagree");
        }

        AccountDeletion prepared = deletions.prepare(
                userId,
                identity.getLogin(),
                identity.getEmail(),
                identity.getHome(),
                quarantineRoot);
        return continueDeletion(prepared);
    }

    private AccountDeletion continueDeletion(AccountDeletion initial)
            throws Exception {
        AccountDeletion deletion = initial;
        try {
            if (deletion.getState() == AccountDeletionState.PREPARED) {
                runtimeAuthority.revokePending(deletion.getLogin());
                if (credentials.containsUserId(deletion.getUserId())) {
                    final long userId = deletion.getUserId();
                    boolean removed = credentials.deletePrepared(
                            userId,
                            new DeletionPreparation() {
                                @Override
                                public void prepare(long ignored) throws Exception {
                                    runtimeAuthority.closeUser(userId);
                                    runtimeAuthority.revokeLegacyConfirmation(userId);
                                }
                            });
                    if (!removed) {
                        throw new IllegalStateException(
                                "Credential disappeared during prepared deletion");
                    }
                } else {
                    // Recovery from a process stop after credential persistence
                    // but before journal advancement.
                    runtimeAuthority.closeUser(deletion.getUserId());
                    runtimeAuthority.revokeLegacyConfirmation(
                            deletion.getUserId());
                }
                deletion = deletions.advance(
                        deletion.getId(),
                        AccountDeletionState.CREDENTIAL_REMOVED,
                        "Credential and runtime authorities revoked");
            }

            if (deletion.getState()
                    == AccountDeletionState.CREDENTIAL_REMOVED) {
                // Idempotent recovery verifies all revocation authorities before
                // the canonical workspace leaves its published location.
                runtimeAuthority.revokePending(deletion.getLogin());
                runtimeAuthority.closeUser(deletion.getUserId());
                runtimeAuthority.revokeLegacyConfirmation(
                        deletion.getUserId());
                workspaces.quarantine(deletion);
                deletion = deletions.advance(
                        deletion.getId(),
                        AccountDeletionState.HOME_QUARANTINED,
                        "Canonical account home moved to quarantine");
            }

            if (deletion.getState()
                    == AccountDeletionState.HOME_QUARANTINED) {
                workspaces.quarantine(deletion);
                deletion = deletions.advance(
                        deletion.getId(),
                        AccountDeletionState.COMPLETE,
                        "Safe account deletion complete");
            }
            return deletion;
        } catch (Exception failure) {
            AccountDeletion current = deletion;
            try {
                AccountDeletion persisted = deletions.findById(deletion.getId());
                if (persisted != null) {
                    current = deletions.diagnose(
                            persisted.getId(),
                            failure.getClass().getSimpleName() + ": "
                                    + safeMessage(failure));
                }
            } catch (Exception journalFailure) {
                failure.addSuppressed(journalFailure);
            }
            throw new AccountDeletionIncompleteException(current, failure);
        }
    }

    private void requireDeletionSupport() {
        if (deletions == null || quarantineRoot == null) {
            throw new IllegalStateException(
                    "This lifecycle service has no deletion journal authority");
        }
    }

    AccountDeletion findDeletionByLogin(String login) throws Exception {
        if (deletions == null || login == null || login.trim().isEmpty()) {
            return null;
        }
        AccountDeletion latestComplete = null;
        List<AccountDeletion> records = deletions.all();
        for (AccountDeletion record : records) {
            if (!record.getLogin().equals(login.trim())) {
                continue;
            }
            if (!record.isComplete()) {
                return record;
            }
            if (latestComplete == null
                    || record.getUpdatedAt() > latestComplete.getUpdatedAt()) {
                latestComplete = record;
            }
        }
        return latestComplete;
    }

    boolean isDeletionInProgress(String login) throws Exception {
        AccountDeletion deletion = findDeletionByLogin(login);
        return deletion != null && !deletion.isComplete();
    }

    boolean deleteCredential(long userId) throws Exception {
        return credentials.delete(userId);
    }

    Long findCredentialUserId(String login) throws Exception {
        return credentials.findUserId(login);
    }

    Long findWorkspaceUserId(String login) throws Exception {
        return workspaces.findUserIdByLogin(login);
    }

    Long findUserId(String login) throws Exception {
        Long versioned = findCredentialUserId(login);
        return versioned != null ? versioned : findWorkspaceUserId(login);
    }

    Long findUserIdByEmail(String email) throws Exception {
        return workspaces.findUserIdByEmail(email);
    }

    boolean hasActivationReference(long userId, String reference)
            throws Exception {
        return workspaces.hasActivationReference(userId, reference);
    }

    private static Path resolveCredentialFile() {
        Path direct = Paths.get(UserFactory.getDir("users.conf"));
        if (Files.exists(direct)) {
            return direct;
        }
        return Paths.get(
                UserFactory.getDir(UserFactory.rootDir)
                        + File.separator + "users.conf");
    }

    private static Path resolveConfirmationFile() {
        Path direct = Paths.get(UserFactory.getDir("confirmation-tokens.conf"));
        if (Files.exists(direct)) {
            return direct;
        }
        return Paths.get(
                UserFactory.getDir(UserFactory.rootDir)
                        + File.separator + "confirmation-tokens.conf");
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getName() : message;
    }

    private static final class StoreCredentialAuthority
            implements CredentialAuthority {
        private final CredentialStore store;

        private StoreCredentialAuthority(CredentialStore store) {
            this.store = store;
        }

        @Override
        public CredentialMaterial preparePassword(String password) throws Exception {
            return store.preparePassword(password);
        }

        @Override
        public long createPrepared(String login,
                                   CredentialMaterial material,
                                   final Preparation preparation)
                throws Exception {
            return store.createPrepared(
                    login,
                    material,
                    new CredentialStore.AccountPreparation() {
                        @Override
                        public void prepare(long userId) throws Exception {
                            preparation.prepare(userId);
                        }
                    });
        }

        @Override
        public long publishPrepared(long userId,
                                    String login,
                                    CredentialMaterial material)
                throws Exception {
            return store.publishPrepared(userId, login, material);
        }

        @Override
        public boolean deletePrepared(long userId,
                                      final DeletionPreparation preparation)
                throws Exception {
            return store.deletePrepared(
                    userId,
                    new CredentialStore.AccountDeletionPreparation() {
                        @Override
                        public void prepare(long preparedUserId) throws Exception {
                            preparation.prepare(preparedUserId);
                        }
                    });
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
    }

    private static final class ServerRuntimeAuthority
            implements RuntimeAuthority {
        private final ConfirmationTokenStore confirmations;

        private ServerRuntimeAuthority(ConfirmationTokenStore confirmations) {
            this.confirmations = confirmations;
        }

        @Override
        public void closeUser(long userId) throws Exception {
            UserFactory.dropUser(Long.valueOf(userId));
        }

        @Override
        public void revokeLegacyConfirmation(long userId) throws Exception {
            confirmations.revoke(userId);
        }

        @Override
        public void revokePending(String login) throws Exception {
            PendingRegistrationService.runtime().revokeForAccount(login);
        }
    }

    private static final class NoopRuntimeAuthority
            implements RuntimeAuthority {
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
