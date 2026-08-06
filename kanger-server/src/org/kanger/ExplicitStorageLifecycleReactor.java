/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

/**
 * Earliest server boundary for storage lifecycle preconditions.
 *
 * <p>An already-open database makes every {@code use} request invalid,
 * including a request for the same database. The rejection is deliberately
 * performed before the destructive stop-loss layer canonicalizes, probes,
 * opens or validates the requested target.</p>
 */
final class ExplicitStorageLifecycleReactor implements IReactor<JSONObject> {

    private final IReactor<JSONObject> delegate;

    ExplicitStorageLifecycleReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate reactor is required");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        Request request = Request.parse(packet);
        if (request == null
                || !"command".equalsIgnoreCase(request.context)
                || !request.parameters.has("token")
                || request.parameters.isNull("token")
                || !request.parameters.has("use")
                || request.parameters.isNull("use")
                || request.parameters.optString("use", "").isEmpty()) {
            return delegate.run(packet);
        }

        IUser user = UserFactory.getUser(
                request.parameters.getString("token"));
        if (user.getCurrentMind() == null) {
            user.setCurrentMind(new Mind(user));
        }

        IMind active = user.getCurrentMind();
        if (!active.isStorageUsed()) {
            return delegate.run(packet);
        }

        JSONObject result = new JSONObject()
                .put("result", "error")
                .put("code", "storage_already_open")
                .put("description", "Database " + active.getStorageName()
                        + " is already open; explicit close is required before use")
                .put("transaction", active.getTransactionLevel())
                .put("empty", active.isEmptyLevel());
        return result;
    }

    private static final class Request {
        private final String context;
        private final JSONObject parameters;

        private Request(String context, JSONObject parameters) {
            this.context = context;
            this.parameters = parameters;
        }

        private static Request parse(JSONObject packet) {
            if (packet == null) {
                return null;
            }
            Request body = parseEnvelope(packet.optJSONObject("body"));
            if (body != null) {
                return body;
            }
            return parseEnvelope(packet.optJSONObject("query"));
        }

        private static Request parseEnvelope(JSONObject envelope) {
            if (envelope == null) {
                return null;
            }
            try {
                return new Request(
                        envelope.getString("context"),
                        envelope.getJSONObject("parameters"));
            } catch (JSONException malformed) {
                return null;
            }
        }
    }
}
