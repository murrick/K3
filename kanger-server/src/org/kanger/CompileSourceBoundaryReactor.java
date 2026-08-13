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
 * Owns Editor source replacement for every explicit user transaction level.
 *
 * <p>U0 is replaced atomically through an operation-local technical child of
 * the same root Mind. Nested U_n levels are rebuilt as temporary siblings over
 * U_{n-1}, preserving transaction-control delta that has no standalone source
 * representation. Rejected input leaves the published explicit level
 * unchanged in both cases.</p>
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

        String exactSource = URLDecoder.decode(
                parameters.getString("compile"), "UTF-8");
        boolean accepted;
        String description;
        if (user.getCurrentMind().getTransactionLevel() == 0) {
            RootCurrentLevelSourceReplacement.Outcome outcome =
                    RootCurrentLevelSourceReplacement.replace(user, exactSource);
            accepted = outcome.isAccepted();
            description = outcome.getDescription();
        } else {
            NestedCurrentLevelSourceReplacement.Outcome outcome =
                    NestedCurrentLevelSourceReplacement.replace(user, exactSource);
            accepted = outcome.isAccepted();
            description = outcome.getDescription();
        }

        IMind current = user.getCurrentMind();
        JSONObject result = accepted
                ? ok(description)
                : error("compile_rejected", description);
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
