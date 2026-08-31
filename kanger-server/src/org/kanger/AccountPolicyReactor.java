/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.PendingRegistrationException;
import org.kanger.account.RegistrationPolicy;
import org.kanger.bootstrap.RuntimeBootstrap;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.util.Locale;

/**
 * Enforces account-registration topology before the historical request
 * processor can create credentials, user directories, sessions or mail side
 * effects.
 */
final class AccountPolicyReactor implements IReactor<JSONObject> {

    interface ExistingAccountActivator {
        void activate(JSONObject parameters) throws Exception;
    }

    interface ProfileUpdateGuard {
        PendingRegistrationException violation(JSONObject parameters) throws Exception;
    }

    private final RegistrationPolicy policy;
    private final IReactor<JSONObject> delegate;
    private final ExistingAccountActivator existingAccountActivator;
    private final ProfileUpdateGuard profileUpdateGuard;

    AccountPolicyReactor(RegistrationPolicy policy,
                         IReactor<JSONObject> delegate) {
        this(policy,
                delegate,
                new LegacyExistingAccountActivator(),
                new LegacyProfileUpdateGuard());
    }

    AccountPolicyReactor(RegistrationPolicy policy,
                         IReactor<JSONObject> delegate,
                         ExistingAccountActivator existingAccountActivator) {
        this(policy,
                delegate,
                existingAccountActivator,
                new LegacyProfileUpdateGuard());
    }

    AccountPolicyReactor(RegistrationPolicy policy,
                         IReactor<JSONObject> delegate,
                         ExistingAccountActivator existingAccountActivator,
                         ProfileUpdateGuard profileUpdateGuard) {
        if (policy == null || delegate == null
                || existingAccountActivator == null || profileUpdateGuard == null) {
            throw new IllegalArgumentException(
                    "policy, delegate, activator and profile guard must not be null");
        }
        this.policy = policy;
        this.delegate = delegate;
        this.existingAccountActivator = existingAccountActivator;
        this.profileUpdateGuard = profileUpdateGuard;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        JSONObject parameters = SessionSerializingReactor.parameters(packet);
        if (isNewPublicRegistration(packet, parameters)
                && !policy.allowsPublicSelfRegistration()) {
            throw registrationDisabled();
        }
        if (isAuthenticatedProfileUpdate(packet, parameters)) {
            PendingRegistrationException violation =
                    profileUpdateGuard.violation(parameters);
            if (violation != null) {
                throw violation;
            }
        }
        if (SessionSerializingReactor.hasAuthenticatedCredential(packet)
                && isExistingCredentialLogin(packet, parameters)) {
            // Every credential that predates the pending-registration cutover
            // is an existing ACTIVE account in both registration policies.
            existingAccountActivator.activate(parameters);
        }
        return delegate.run(packet);
    }

    static boolean isNewPublicRegistration(JSONObject packet,
                                           JSONObject parameters) {
        return "login".equalsIgnoreCase(context(packet))
                && has(parameters, "register")
                && has(parameters, "password")
                && string(parameters, "token").isEmpty();
    }

    static boolean isExistingCredentialLogin(JSONObject packet,
                                             JSONObject parameters) {
        return "login".equalsIgnoreCase(context(packet))
                && !has(parameters, "register")
                && has(parameters, "login")
                && has(parameters, "password")
                && string(parameters, "token").isEmpty();
    }

    static boolean isAuthenticatedProfileUpdate(JSONObject packet,
                                                JSONObject parameters) {
        return "login".equalsIgnoreCase(context(packet))
                && has(parameters, "register")
                && has(parameters, "password")
                && !string(parameters, "token").isEmpty();
    }

    static PendingRegistrationException accountLoginChangeViolation(
            String currentLogin,
            String requestedLogin) {
        return normalizeLogin(currentLogin).equals(normalizeLogin(requestedLogin))
                ? null : accountLoginImmutable();
    }

    static PendingRegistrationException verifiedEmailChangeViolation(
            boolean confirmed,
            String currentEmail,
            String requestedEmail) {
        if (!confirmed) {
            return null;
        }
        return normalizeEmail(currentEmail).equals(normalizeEmail(requestedEmail))
                ? null : verifiedEmailImmutable();
    }

    static PendingRegistrationException registrationDisabled() {
        return new PendingRegistrationException(
                AccountErrorCode.REGISTRATION_DISABLED,
                "Public registration is disabled by the server registration policy");
    }

    static PendingRegistrationException verifiedEmailImmutable() {
        return new PendingRegistrationException(
                AccountErrorCode.VERIFIED_EMAIL_IMMUTABLE,
                "A verified e-mail address cannot be changed");
    }

    static PendingRegistrationException accountLoginImmutable() {
        return new PendingRegistrationException(
                AccountErrorCode.ACCOUNT_LOGIN_IMMUTABLE,
                "The account login cannot be changed");
    }

    private static String context(JSONObject packet) {
        if (packet == null) {
            return "";
        }
        JSONObject body = packet.optJSONObject("body");
        if (body != null) {
            String value = body.optString("context", "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        JSONObject query = packet.optJSONObject("query");
        return query == null ? "" : query.optString("context", "");
    }

    private static boolean has(JSONObject parameters, String key) {
        return parameters != null
                && parameters.has(key)
                && !parameters.isNull(key);
    }

    private static String string(JSONObject parameters, String key) {
        if (!has(parameters, key)) {
            return "";
        }
        try {
            return parameters.getString(key).trim();
        } catch (JSONException error) {
            return "";
        }
    }

    private static String normalizeLogin(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeEmail(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class LegacyExistingAccountActivator
            implements ExistingAccountActivator {
        @Override
        public void activate(JSONObject parameters) throws Exception {
            IUser user = UserFactory.getUser(
                    string(parameters, "login"),
                    string(parameters, "password"));
            RuntimeBootstrap.ensure(user);
        }
    }

    private static final class LegacyProfileUpdateGuard
            implements ProfileUpdateGuard {
        @Override
        public PendingRegistrationException violation(JSONObject parameters)
                throws Exception {
            IUser user = UserFactory.getUser(string(parameters, "token"));
            PendingRegistrationException loginViolation = accountLoginChangeViolation(
                    user.getProperty("reg.login", ""),
                    string(parameters, "register"));
            if (loginViolation != null) {
                return loginViolation;
            }
            if (!has(parameters, "email")) {
                return null;
            }
            boolean confirmed = Boolean.parseBoolean(user.getProperty(
                    "reg.email.confirmed",
                    user.getProperty("reg.agreed", "false")));
            return verifiedEmailChangeViolation(
                    confirmed,
                    user.getProperty("reg.email", ""),
                    string(parameters, "email"));
        }
    }
}
