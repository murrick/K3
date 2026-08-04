/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.account.RegistrationPolicy;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IReactor;

/**
 * Holds the per-user session lock around the complete legacy application
 * request. The delegate therefore observes one uninterrupted mutable workflow
 * from token lookup through response construction.
 *
 * <p>The account-registration policy is resolved once when this boundary is
 * constructed. The policy reactor remains inside the session boundary and
 * before all mail and legacy account side effects.</p>
 */
final class SessionSerializingReactor implements IReactor<JSONObject> {

    private final IReactor<JSONObject> delegate;

    SessionSerializingReactor(IReactor<JSONObject> delegate) {
        this(RegistrationPolicy.fromEmailMode(
                Settings.getProperty("server.email.mode", "disabled")), delegate);
    }

    SessionSerializingReactor(RegistrationPolicy policy,
                              IReactor<JSONObject> delegate) {
        if (policy == null || delegate == null) {
            throw new IllegalArgumentException("policy and delegate must not be null");
        }
        this.delegate = new AccountPolicyReactor(policy, delegate);
    }

    @Override
    public Object run(final JSONObject packet) throws Exception {
        final JSONObject parameters = parameters(packet);
        final String token = string(parameters, "token");

        if (token != null && !token.isEmpty()) {
            return UserFactory.executeWithSessionIfPresent(
                    token,
                    new SessionRegistry.Work<Object>() {
                        @Override
                        public Object run() throws Exception {
                            return invoke(packet, parameters);
                        }
                    });
        }

        String login = string(parameters, "currentlogin");
        String password = string(parameters, "currentpassword");
        if (login == null || password == null) {
            login = string(parameters, "login");
            password = string(parameters, "password");
        }

        if (login != null && password != null) {
            final String authenticatedLogin = login;
            final String authenticatedPassword = password;
            try {
                return UserFactory.executeWithAuthenticatedUserIfPresent(
                        authenticatedLogin,
                        authenticatedPassword,
                        new SessionRegistry.Work<Object>() {
                            @Override
                            public Object run() throws Exception {
                                return invoke(packet, parameters);
                            }
                        });
            } catch (AuthenticationErrorException rejected) {
                return authenticationRejected(rejected);
            }
        }

        return invoke(packet, parameters);
    }

    private Object invoke(JSONObject packet, JSONObject parameters) throws Exception {
        JSONObject violation = ApiInputPolicy.violation(parameters);
        if (violation != null) {
            return violation;
        }
        return delegate.run(packet);
    }

    static JSONObject authenticationRejected(AuthenticationErrorException rejected) {
        return new JSONObject()
                .put("result", "error")
                .put("description", rejected.toString());
    }

    static JSONObject parameters(JSONObject packet) {
        if (packet == null) {
            return new JSONObject();
        }

        JSONObject body = packet.optJSONObject("body");
        if (body != null) {
            JSONObject parameters = body.optJSONObject("parameters");
            if (parameters != null) {
                return parameters;
            }
        }

        JSONObject query = packet.optJSONObject("query");
        if (query != null) {
            JSONObject parameters = query.optJSONObject("parameters");
            if (parameters != null) {
                return parameters;
            }
        }
        return new JSONObject();
    }

    private static String string(JSONObject parameters, String name) {
        if (parameters == null || !parameters.has(name) || parameters.isNull(name)) {
            return null;
        }
        try {
            return parameters.getString(name).trim();
        } catch (JSONException ex) {
            return null;
        }
    }
}
