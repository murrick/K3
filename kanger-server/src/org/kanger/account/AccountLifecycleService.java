/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.kanger.UserFactory;
import org.kanger.security.CredentialMaterial;
import org.kanger.security.CredentialStore;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Sole target owner of complete ACTIVE account publication.
 *
 * <p>The workspace is fully prepared and published before the credential is
 * atomically made visible. If any later step fails, the service rolls the
 * workspace back and preserves the original exception with cleanup failures as
 * suppressed diagnostics.</p>
 */
public final class AccountLifecycleService {

    interface Preparation {
        void prepare(long userId) throws Exception;
    }

    interface CredentialAuthority {
        CredentialMaterial preparePassword(String password) throws Exception;

        long createPrepared(String login,
                            CredentialMaterial material,
                            Preparation preparation) throws Exception;

        boolean delete(long userId) throws Exception;

        Long findUserId(String login) throws Exception;
    }

    interface WorkspaceAuthority {
        PreparedWorkspace prepare(long userId,
                                  ActiveAccountRequest request) throws Exception;

        Long findUserIdByLogin(String login) throws Exception;

        Long findUserIdByEmail(String email) throws Exception;

        boolean hasActivationReference(long userId, String reference)
                throws Exception;
    }

    interface PreparedWorkspace {
        Path home();

        void publish() throws Exception;

        void rollback() throws Exception;
    }

    private static volatile AccountLifecycleService runtime;

    private final CredentialAuthority credentials;
    private final WorkspaceAuthority workspaces;

    AccountLifecycleService(CredentialAuthority credentials,
                            WorkspaceAuthority workspaces) {
        if (credentials == null || workspaces == null) {
            throw new IllegalArgumentException("credential and workspace authorities must not be null");
        }
        this.credentials = credentials;
        this.workspaces = workspaces;
    }

    /**
     * Returns the server-runtime lifecycle service over the same credential
     * authority and account root used by UserFactory.
     */
    public static AccountLifecycleService runtime() {
        AccountLifecycleService current = runtime;
        if (current != null) {
            return current;
        }
        synchronized (AccountLifecycleService.class) {
            if (runtime == null) {
                Path credentialFile = resolveCredentialFile();
                CredentialStore store = new CredentialStore(credentialFile);
                Path accountRoot = Paths.get(UserFactory.getDir(UserFactory.rootDir));
                runtime = new AccountLifecycleService(
                        new StoreCredentialAuthority(store),
                        new FileAccountWorkspace(accountRoot, UserFactory.getHome()));
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
     * session. Operator requests derive material synchronously; confirmed
     * pending registrations supply the previously persisted material directly.
     */
    public ActiveAccount createActiveAccount(final ActiveAccountRequest request)
            throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("active account request must not be null");
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
            return new ActiveAccount(userId, request.getLogin(), prepared[0].home());
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
     * Credential-authority primitive used by the later safe deletion workflow.
     * Physical home quarantine and runtime revocation remain mandatory higher
     * layers and are not bypassed by public APIs.
     */
    boolean deleteCredential(long userId) throws Exception {
        return credentials.delete(userId);
    }

    Long findUserId(String login) throws Exception {
        Long versioned = credentials.findUserId(login);
        return versioned != null ? versioned : workspaces.findUserIdByLogin(login);
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
        public boolean delete(long userId) throws Exception {
            return store.delete(userId);
        }

        @Override
        public Long findUserId(String login) throws Exception {
            return store.findUserId(login);
        }
    }
}
