/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.net.URLDecoder;

/**
 * Owns Editor source replacement for nested explicit user transaction levels.
 *
 * <p>U0 deliberately remains on the qualified legacy stop-loss path until root
 * replacement is closed separately. For U_n above root, source replacement is
 * delegated to {@link NestedCurrentLevelSourceReplacement}; rejected input
 * leaves the published Mind unchanged.</p>
 */
final class CompileSourceBoundaryReactor implements IReactor<JSONObject> {

    private final IReactor<JSONObject> delegate;

    CompileSourceBoundaryReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate reactor is required");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        JSONObject parameters = SessionSerializingReactor.parameters(packet);
        String context = CanonicalCommandIngressReactor.context(packet);
        if (!"query".equalsIgnoreCase(context)
                || !parameters.has("compile")
                || parameters.isNull("compile")) {
            return delegate.run(packet);
        }

        IUser user = requireUser(parameters);
        if (user == null) {
            return error("authentication_required", "User not logged in");
        }
        if (user.getCurrentMind() == null) {
            user.setCurrentMind(new Mind(user));
        }
        if (user.getCurrentMind().getTransactionLevel() <= 0) {
            return delegate.run(packet);
        }

        String exactSource = URLDecoder.decode(
                parameters.getString("compile"), "UTF-8");
        NestedCurrentLevelSourceReplacement.Outcome outcome =
                NestedCurrentLevelSourceReplacement.replace(user, exactSource);

        IMind current = user.getCurrentMind();
        JSONObject result = outcome.isAccepted()
                ? ok(outcome.getDescription())
                : error("compile_rejected", outcome.getDescription());
        result.put("transaction", current.getTransactionLevel());
        result.put("empty", current.isEmptyLevel());
        return result;
    }

    private IUser requireUser(JSONObject parameters) {
        String token = parameters.optString("token", "");
        if (token.isEmpty()) {
            return null;
        }
        try {
            return UserFactory.getUser(token);
        } catch (Exception unavailable) {
            return null;
        }
    }

    private JSONObject ok(String description) {
        return new JSONObject()
                .put("result", "OK")
                .put("description", description == null ? "" : description);
    }

    private JSONObject error(String code, String description) {
        return new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description == null ? "" : description);
    }
}
