/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.RegistrationPolicy;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

/**
 * Enforces account-registration topology before the historical request
 * processor can create credentials, user directories, sessions or mail side
 * effects.
 */
final class AccountPolicyReactor implements IReactor<JSONObject> {

    interface ExistingAccountActivator {
        void activate(JSONObject parameters) throws Exception;
    }

    private final RegistrationPolicy policy;
    private final IReactor<JSONObject> delegate;
    private final ExistingAccountActivator existingAccountActivator;

    AccountPolicyReactor(RegistrationPolicy policy,
                         IReactor<JSONObject> delegate) {
        this(policy, delegate, new LegacyExistingAccountActivator());
    }

    AccountPolicyReactor(RegistrationPolicy policy,
                         IReactor<JSONObject> delegate,
                         ExistingAccountActivator existingAccountActivator) {
        if (policy == null || delegate == null || existingAccountActivator == null) {
            throw new IllegalArgumentException(
                    "policy, delegate and existing account activator must not be null");
        }
        this.policy = policy;
        this.delegate = delegate;
        this.existingAccountActivator = existingAccountActivator;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        JSONObject parameters = SessionSerializingReactor.parameters(packet);
        if (isNewPublicRegistration(packet, parameters)
                && !policy.allowsPublicSelfRegistration()) {
            return registrationDisabled();
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

    static JSONObject registrationDisabled() {
        return new JSONObject()
                .put("result", "error")
                .put("code", AccountErrorCode.REGISTRATION_DISABLED.code())
                .put("description",
                        "Public registration is disabled by the server registration policy");
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

    private static final class LegacyExistingAccountActivator
            implements ExistingAccountActivator {
        @Override
        public void activate(JSONObject parameters) throws Exception {
            IUser user = UserFactory.getUser(
                    string(parameters, "login"),
                    string(parameters, "password"));
            try {
                ((User) user).getData();
            } catch (RuntimeErrorException absent) {
                new DB().init(user);
            }
            try {
                ((User) user).getUdf();
            } catch (RuntimeErrorException absent) {
                new UDF().init(user);
            }
        }
    }
}
