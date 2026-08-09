/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.compiler.Token;
import org.kanger.enums.LogMode;
import org.kanger.enums.Tools;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.ILogEntry;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.stores.HypothesisStore;

import java.net.URLDecoder;

/**
 * Completes the lifecycle of server-owned Mind transactions hidden behind the
 * historical browser protocol.
 *
 * <p>The legacy processor creates request-local children without publishing
 * them through {@link IUser#getCurrentMind()}, publishes explicit transaction
 * children separately, and clears the compatibility slot before session
 * logout. Those three paths previously had different failure semantics. This
 * boundary owns them as one lifecycle: every request-local child is finalized,
 * explicit transaction publication follows commit/release completion, and
 * logout delegates chain closure to the session runtime authority.</p>
 *
 * <p>Transaction responses are stable protocol results. Analyzer output is
 * exposed separately as optional {@code details}; it never replaces the
 * transaction description.</p>
 */
final class MindLifecycleReactor implements IReactor<JSONObject> {

    interface ChildFactory {
        Mind create(IMind parent) throws Exception;
    }

    private final IReactor<JSONObject> delegate;
    private final ChildFactory childFactory;

    MindLifecycleReactor(IReactor<JSONObject> delegate) {
        this(delegate, new ChildFactory() {
            @Override
            public Mind create(IMind parent) throws Exception {
                return new Mind(parent);
            }
        });
    }

    MindLifecycleReactor(IReactor<JSONObject> delegate,
                         ChildFactory childFactory) {
        if (delegate == null || childFactory == null) {
            throw new IllegalArgumentException(
                    "delegate and childFactory must not be null");
        }
        this.delegate = delegate;
        this.childFactory = childFactory;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        Request request = Request.parse(packet);
        String operation = operation(request);
        if (operation == null) {
            return delegate.run(packet);
        }

        String token = request.parameters.optString("token", "");
        IUser user = null;
        try {
            user = UserFactory.getUser(token);
            if (!"quit".equals(operation) && user.getCurrentMind() == null) {
                user.setCurrentMind(new Mind(user));
            }

            if ("query".equals(operation)) {
                return decorate(executeQuery(request.parameters, user), user);
            }
            if ("transaction".equals(operation)) {
                return decorate(executeTransaction(request.parameters, user), user);
            }
            if ("quit".equals(operation)) {
                UserFactory.logout(token);
                Watchdog.log(user, "User left system");
                return ok("User left system");
            }
            return delegate.run(packet);
        } catch (StorageLifecycleException rejected) {
            JSONObject result = error(rejected.getCode(), rejected.toString());
            if (rejected.getRequiredAction() != null) {
                result.put("required_action", rejected.getRequiredAction());
            }
            if (user != null && user.getCurrentMind() != null) {
                return decorate(result, user);
            }
            return result;
        } catch (Exception failure) {
            JSONObject result = error(operation + "_failed", failure.toString());
            if (user != null && user.getCurrentMind() != null) {
                return decorate(result, user);
            }
            return result;
        }
    }

    private String operation(Request request) {
        if (request == null || request.parameters == null
                || !request.parameters.has("token")
                || request.parameters.isNull("token")) {
            return null;
        }
        if ("query".equalsIgnoreCase(request.context)) {
            if (request.parameters.has("request")
                    && !request.parameters.isNull("request")) {
                return "query";
            }
            if (request.parameters.has("transaction")
                    && !request.parameters.isNull("transaction")) {
                String action = request.parameters.optString("transaction", "");
                if ("create".equalsIgnoreCase(action)
                        || "commit".equalsIgnoreCase(action)
                        || "rollback".equalsIgnoreCase(action)) {
                    return "transaction";
                }
            }
        }
        if ("command".equalsIgnoreCase(request.context)
                && request.parameters.has("quit")
                && !request.parameters.isNull("quit")) {
            return "quit";
        }
        return null;
    }

    private JSONObject executeQuery(JSONObject parameters, IUser user)
            throws Exception {
        IMind parent = user.getCurrentMind();
        parent.clearLog();
        ((HypothesisStore) parent.getHypothesis()).clear();

        String source = URLDecoder.decode(
                parameters.getString("request"), "UTF-8");

        /*
         * Bare '?' is a Core program check, not an unterminated ordinary
         * informational query. Mind.query("?") owns its own internal child and
         * commits the regenerated program state when the complete link/analyze
         * pass succeeds. Running it in this reactor's ordinary request-local
         * child and then releasing that child would discard exactly the
         * generated-rule rebuild requested by the operator.
         */
        if ("?".equals(source.trim())) {
            Boolean response = parent.query("?");
            return queryResponse(parent, response);
        }

        Mind child = childFactory.create(parent);
        boolean finalizationStarted = false;

        try {
            Token token = null;
            boolean informational = false;
            Boolean response = null;

            while ((token = Tools.extractLine(source, token)) != null) {
                String expression = token.getToken(source);
                if (!expression.isEmpty() && expression.charAt(0) == '?') {
                    informational = true;
                }
                response = child.query(expression);
            }

            if (informational) {
                finalizationStarted = true;
                parent.release(child);
                if (response == null) {
                    ((HypothesisStore) parent.getHypothesis())
                            .commit(child.getHypothesis());
                }
            } else {
                finalizationStarted = true;
                parent.commit(child);
            }

            return queryResponse(parent, response);
        } catch (Throwable failure) {
            if (!finalizationStarted) {
                try {
                    parent.release(child);
                } catch (Throwable cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw asException(failure);
        }
    }

    private JSONObject executeTransaction(JSONObject parameters, IUser user)
            throws Exception {
        IMind active = user.getCurrentMind();
        long requestedId = active.getId();
        String action = parameters.optString("transaction", "");
        JSONObject result = new JSONObject();

        if ("create".equalsIgnoreCase(action)) {
            IMind child = childFactory.create(active);
            user.setCurrentMind(child);
            result.put("result", "OK");
            result.put("description", "New transaction created");
        } else if ("commit".equalsIgnoreCase(action)) {
            IMind parent = active.getNext();
            if (parent == null) {
                return noTransaction(requestedId);
            }

            boolean applied;
            try {
                applied = parent.commit(active);
            } finally {
                // Mind.commit owns the reservation on every success, rejection
                // and qualified exception path. The published slot must never
                // remain on the now-finished child.
                user.setCurrentMind(parent);
            }
            result.put("result", applied ? "OK" : "error");
            result.put("description", applied
                    ? "Transaction committed" : "Transaction rejected");
            addTransactionDetails(result, parent);
        } else if ("rollback".equalsIgnoreCase(action)) {
            IMind parent = active.getNext();
            if (parent == null) {
                return noTransaction(requestedId);
            }

            parent.release(active);
            user.setCurrentMind(parent);
            result.put("result", "OK");
            result.put("description", "Transaction rolled back");
            addTransactionDetails(result, parent);
        }

        result.put("id", requestedId);
        return result;
    }

    private JSONObject noTransaction(long requestedId) {
        return new JSONObject()
                .put("result", "error")
                .put("code", "NO_ACTIVE_TRANSACTION")
                .put("legacy_code", "no_transaction")
                .put("required_action", "CREATE_TRANSACTION")
                .put("description", "No active transaction exists")
                .put("id", requestedId);
    }

    private void addTransactionDetails(JSONObject result, IMind mind) {
        if (mind != null && !mind.getLog().isEmpty()) {
            result.put("details",
                    mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
        }
    }

    private JSONObject queryResponse(IMind mind, Boolean response) throws Exception {
        JSONObject result = new JSONObject();
        result.put("response", response == null
                ? "unknown" : (response ? "yes" : "no"));
        result.put("results", mind.getValues().size());
        result.put("solutions", mind.getSolutions().size());
        result.put("hypothesis", mind.getHypothesis().size());

        ILogEntry current = mind.getCurrentLogRecord(LogMode.ANALYZER);
        if (current == null) {
            return error("query_result_missing", "Query error");
        }

        String description = current.getRecord();
        for (ILogEntry entry : mind.getLog()) {
            if (entry.getType() == LogMode.SOLVES) {
                description += "<br>" + entry.getRecord();
            }
        }
        for (ILogEntry entry : mind.getLog()) {
            if (entry.getType() == LogMode.VALUES) {
                description += "<br>" + entry.getRecord();
            }
        }
        result.put("result", "OK");
        result.put("description", description);
        return result;
    }

    private JSONObject decorate(JSONObject result, IUser user) {
        IMind mind = user == null ? null : user.getCurrentMind();
        if (result != null && mind != null) {
            result.put("transaction", mind.getTransactionLevel());
            result.put("empty", mind.isEmptyLevel());
        }
        return result;
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

    private static Exception asException(Throwable failure) {
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure instanceof Exception) {
            return (Exception) failure;
        }
        return new RuntimeException(failure);
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
                String context = envelope.getString("context");
                JSONObject parameters = envelope.getJSONObject("parameters");
                return new Request(context, parameters);
            } catch (JSONException malformed) {
                return null;
            }
        }
    }
}
