/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

/**
 * Initializes runtime modules for an already authenticated ACTIVE account.
 *
 * <p>E-mail verification is activation provenance and must not gate DB/UDF
 * availability after authentication. LOCAL_OPERATOR and EMAIL_CONFIRMATION
 * accounts therefore enter the same runtime module state once a valid session
 * token is presented.</p>
 */
final class AuthenticatedRuntimeBootstrapReactor implements IReactor<JSONObject> {

    private final IReactor<JSONObject> delegate;

    AuthenticatedRuntimeBootstrapReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        JSONObject parameters = parameters(packet);
        if (parameters != null
                && parameters.has("token")
                && !parameters.isNull("token")) {
            String token = parameters.optString("token", "");
            if (!token.isEmpty()) {
                try {
                    ensureRuntimeModules(UserFactory.getUser(token));
                } catch (AuthenticationErrorException rejected) {
                    // Preserve the established authentication error surface.
                    // The downstream request processor owns invalid-token
                    // projection and no runtime side effect occurred here.
                }
            }
        }
        return delegate.run(packet);
    }

    static void ensureRuntimeModules(IUser user) throws Exception {
        if (user == null) {
            return;
        }
        try {
            ((User) user).getData();
        } catch (RuntimeErrorException missing) {
            new DB().init(user);
        }
        try {
            ((User) user).getUdf();
        } catch (RuntimeErrorException missing) {
            new UDF().init(user);
        }
    }

    private static JSONObject parameters(JSONObject packet) {
        if (packet == null) {
            return null;
        }
        JSONObject body = packet.optJSONObject("body");
        if (body != null) {
            JSONObject parameters = body.optJSONObject("parameters");
            if (parameters != null) {
                return parameters;
            }
        }
        JSONObject query = packet.optJSONObject("query");
        if (query == null) {
            return null;
        }
        try {
            return query.getJSONObject("parameters");
        } catch (JSONException malformed) {
            return null;
        }
    }
}
