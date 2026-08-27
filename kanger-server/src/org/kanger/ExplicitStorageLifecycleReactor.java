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
 * Earliest server protocol adapter for the Core storage lifecycle contract.
 *
 * <p>The Core owns every lifecycle precondition. This adapter intercepts only
 * operations whose ordering must be protected before the historical command
 * processor touches lifecycle state: root {@code commit} as durable
 * checkpoint and explicit {@code close}. Storage {@code use} is delegated to
 * Core because Core owns semantic U-stack rebase and compensating restore.
 * Typed lifecycle failures propagate unchanged to the canonical Server error
 * boundary; this adapter owns ordering, not error presentation.</p>
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
                || !request.parameters.has("token")
                || request.parameters.isNull("token")) {
            return delegate.run(packet);
        }

        boolean use = isUse(request);
        boolean commit = isCommit(request);
        boolean close = isClose(request);
        if (!use && !commit && !close) {
            return delegate.run(packet);
        }

        IUser user = UserFactory.getUser(
                request.parameters.getString("token"));
        if (user.getCurrentMind() == null) {
            user.setCurrentMind(new Mind(user));
        }

        IMind active = user.getCurrentMind();
        if (use) {
            /*
             * Core User.use owns same-generation rejection and semantic
             * A->B rebase. Pre-rejecting an already-open generation here
             * would bypass U-stack snapshot/replay and its compensating
             * restore path, so the protocol adapter only delegates.
             */
            return delegate.run(packet);
        }

        if (close) {
            IMind closed = user.close(active);
            user.setCurrentMind(closed);
            return success("Storage closed", closed, closed.getId());
        }

        if (active.getNext() != null) {
            return delegate.run(packet);
        }

        user.checkpoint(active);
        return success("Storage checkpoint completed", active, active.getId());
    }

    private JSONObject success(String description, IMind mind, long id) {
        return new JSONObject()
                .put("result", "OK")
                .put("description", description)
                .put("id", id)
                .put("transaction", mind.getTransactionLevel())
                .put("empty", mind.isEmptyLevel());
    }

    private boolean isUse(Request request) {
        return "command".equalsIgnoreCase(request.context)
                && request.parameters.has("use")
                && !request.parameters.isNull("use")
                && !request.parameters.optString("use", "").isEmpty();
    }

    private boolean isClose(Request request) {
        return "command".equalsIgnoreCase(request.context)
                && request.parameters.has("close")
                && !request.parameters.isNull("close");
    }

    private boolean isCommit(Request request) {
        return "query".equalsIgnoreCase(request.context)
                && request.parameters.has("transaction")
                && !request.parameters.isNull("transaction")
                && "commit".equalsIgnoreCase(
                        request.parameters.optString("transaction", ""));
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
