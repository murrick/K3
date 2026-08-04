/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.kanger.Settings;
import org.kanger.UserFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Coordinates persistent pending intent with complete ACTIVE account
 * publication. No method creates an authenticated session.
 */
public final class PendingRegistrationService {

    public static final class Activation {
        private final long userId;
        private final boolean recovered;

        private Activation(long userId, boolean recovered) {
            this.userId = userId;
            this.recovered = recovered;
        }

        public long getUserId() {
            return userId;
        }

        public boolean isRecovered() {
            return recovered;
        }
    }

    private static volatile PendingRegistrationService runtime;

    private final PendingRegistrationStore store;
    private final AccountLifecycleService accounts;

    PendingRegistrationService(PendingRegistrationStore store,
                               AccountLifecycleService accounts) {
        if (store == null || accounts == null) {
            throw new IllegalArgumentException(
                    "pending store and account lifecycle must not be null");
        }
        this.store = store;
        this.accounts = accounts;
    }

    public static PendingRegistrationService runtime() throws Exception {
        PendingRegistrationService current = runtime;
        if (current != null) {
            return current;
        }
        synchronized (PendingRegistrationService.class) {
            if (runtime == null) {
                Path root = Paths.get(UserFactory.getDir(UserFactory.rootDir));
                Path file = root.resolve("pending-registrations.conf");
                runtime = new PendingRegistrationService(
                        new PendingRegistrationStore(
                                file,
                                hours("server.registration.pending.ttl.hours", 168L),
                                hours("server.registration.confirmation.ttl.hours", 24L),
                                minutes("server.registration.action.ttl.minutes", 15L),
                                seconds("server.registration.resend.cooldown.seconds", 60L),
                                integer("server.registration.pending.max.records", 10000)),
                        AccountLifecycleService.runtime());
            }
            return runtime;
        }
    }

    static void resetRuntimeForTests() {
        synchronized (PendingRegistrationService.class) {
            runtime = null;
        }
    }

    public PendingRegistrationStore.Created register(final String login,
                                                     final String password,
                                                     final String email,
                                                     final String name,
                                                     final String country,
                                                     final String city,
                                                     final Boolean privacyConsent)
            throws Exception {
        return AccountRegistrationAuthority.execute(
                new AccountRegistrationAuthority.Work<PendingRegistrationStore.Created>() {
                    @Override
                    public PendingRegistrationStore.Created run() throws Exception {
                        ensureActiveUnique(login, email);
                        return store.create(new PendingRegistrationStore.Draft(
                                login,
                                email,
                                accounts.prepareCredential(password),
                                name,
                                country,
                                city,
                                privacyConsent));
                    }
                });
    }

    public PendingRegistrationStore.Authenticated authenticate(
            String login,
            String password) throws Exception {
        return store.authenticate(login, password);
    }

    public PendingRegistrationStore.Rotation resend(String actionToken)
            throws Exception {
        return store.resend(actionToken);
    }

    public PendingRegistrationStore.Rotation changeEmail(
            final String actionToken,
            final String email) throws Exception {
        return AccountRegistrationAuthority.execute(
                new AccountRegistrationAuthority.Work<PendingRegistrationStore.Rotation>() {
                    @Override
                    public PendingRegistrationStore.Rotation run() throws Exception {
                        Long activeOwner = accounts.findUserIdByEmail(email);
                        if (activeOwner != null) {
                            throw failure(AccountErrorCode.EMAIL_ALREADY_USED,
                                    "E-mail already belongs to an active account");
                        }
                        return store.changeEmail(actionToken, email);
                    }
                });
    }

    public PendingRegistration cancel(String actionToken) throws Exception {
        return store.cancel(actionToken);
    }

    /**
     * Operator deletion revokes stale pending state for an active-account login.
     */
    public PendingRegistration revokeForAccount(final String login)
            throws Exception {
        return AccountRegistrationAuthority.execute(
                new AccountRegistrationAuthority.Work<PendingRegistration>() {
                    @Override
                    public PendingRegistration run() throws Exception {
                        return store.removeByLogin(login);
                    }
                });
    }

    /**
     * Activates the pending record and removes it only after complete account
     * publication succeeds.
     *
     * <p>The whole reconciliation is serialized in one JVM. Two distinct crash
     * windows are recovered explicitly:</p>
     *
     * <ol>
     *   <li>credential and canonical home both exist, but pending cleanup did
     *   not complete;</li>
     *   <li>canonical home exists with the exact activation reference, but the
     *   process stopped before final credential publication.</li>
     * </ol>
     */
    public Activation confirm(final String confirmationToken) throws Exception {
        return AccountRegistrationAuthority.execute(
                new AccountRegistrationAuthority.Work<Activation>() {
                    @Override
                    public Activation run() throws Exception {
                        PendingRegistration pending = store.resolveConfirmation(
                                confirmationToken);
                        ensureNotDeleting(pending.getLogin());
                        Long credentialUserId = accounts.findCredentialUserId(
                                pending.getLogin());
                        Long workspaceUserId = accounts.findWorkspaceUserId(
                                pending.getLogin());

                        if (credentialUserId != null) {
                            if (workspaceUserId == null
                                    || credentialUserId.longValue()
                                    != workspaceUserId.longValue()) {
                                throw new IllegalStateException(
                                        "Credential and account workspace disagree for login "
                                                + pending.getLogin());
                            }
                            requireActivationReference(
                                    workspaceUserId.longValue(), pending);
                            completePending(pending, confirmationToken);
                            return new Activation(
                                    credentialUserId.longValue(), true);
                        }

                        if (workspaceUserId != null) {
                            requireActivationReference(
                                    workspaceUserId.longValue(), pending);
                            Long emailOwner = accounts.findUserIdByEmail(
                                    pending.getEmail());
                            if (emailOwner != null
                                    && emailOwner.longValue()
                                    != workspaceUserId.longValue()) {
                                throw failure(AccountErrorCode.EMAIL_ALREADY_USED,
                                        "E-mail already belongs to another account workspace");
                            }
                            accounts.publishCredentialForExistingWorkspace(
                                    workspaceUserId.longValue(),
                                    pending.getLogin(),
                                    pending.getCredentialMaterial(),
                                    pending.getId());
                            completePending(pending, confirmationToken);
                            return new Activation(
                                    workspaceUserId.longValue(), true);
                        }

                        Long emailOwner = accounts.findUserIdByEmail(
                                pending.getEmail());
                        if (emailOwner != null) {
                            throw failure(AccountErrorCode.EMAIL_ALREADY_USED,
                                    "E-mail already belongs to an active account");
                        }

                        ActiveAccount account = accounts.createActiveAccount(
                                new ActiveAccountRequest(
                                        pending.getLogin(),
                                        pending.getCredentialMaterial(),
                                        AccountActivationSource.EMAIL_CONFIRMATION,
                                        pending.getEmail(),
                                        pending.getName(),
                                        pending.getCountry(),
                                        pending.getCity(),
                                        pending.getPrivacyConsent(),
                                        pending.getId()));

                        completePending(pending, confirmationToken);
                        return new Activation(account.getUserId(), false);
                    }
                });
    }

    public boolean containsLogin(String login) throws Exception {
        return store.containsLogin(login);
    }

    public int pendingCount() throws Exception {
        return store.size();
    }

    private void requireActivationReference(long userId,
                                            PendingRegistration pending)
            throws Exception {
        if (!accounts.hasActivationReference(userId, pending.getId())) {
            throw failure(AccountErrorCode.LOGIN_ALREADY_USED,
                    "Login already belongs to another account or workspace");
        }
    }

    private void completePending(PendingRegistration pending,
                                 String confirmationToken) throws Exception {
        if (!store.complete(pending.getId(), confirmationToken)) {
            throw new IllegalStateException(
                    "Activated pending registration could not be completed");
        }
    }

    private void ensureActiveUnique(String login, String email) throws Exception {
        ensureNotDeleting(login);
        if (accounts.findUserId(login) != null) {
            throw failure(AccountErrorCode.LOGIN_ALREADY_USED,
                    "Login already belongs to an active account");
        }
        if (accounts.findUserIdByEmail(email) != null) {
            throw failure(AccountErrorCode.EMAIL_ALREADY_USED,
                    "E-mail already belongs to an active account");
        }
    }

    private void ensureNotDeleting(String login) throws Exception {
        if (accounts.isDeletionInProgress(login)) {
            throw failure(AccountErrorCode.LOGIN_ALREADY_USED,
                    "Login is reserved by an account deletion in progress");
        }
    }

    private static PendingRegistrationException failure(AccountErrorCode code,
                                                         String message) {
        return new PendingRegistrationException(code, message);
    }

    private static long hours(String key, long fallback) {
        return positiveLong(key, fallback) * 60L * 60L * 1000L;
    }

    private static long minutes(String key, long fallback) {
        return positiveLong(key, fallback) * 60L * 1000L;
    }

    private static long seconds(String key, long fallback) {
        return positiveLong(key, fallback) * 1000L;
    }

    private static long positiveLong(String key, long fallback) {
        String value = Settings.getProperty(key, Long.toString(fallback));
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0L) {
                throw new IllegalArgumentException(key + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be an integer", error);
        }
    }

    private static int integer(String key, int fallback) {
        long value = positiveLong(key, fallback);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " exceeds integer range");
        }
        return (int) value;
    }
}
