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
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.ILogEntry;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.stores.HypothesisStore;

import java.net.URLDecoder;

/**
 * Completes the lifecycle of request-local Mind transactions that are hidden
 * inside the historical query protocol.
 *
 * <p>The legacy processor creates a child Mind for an execution request but
 * publishes only its parent through {@link IUser#getCurrentMind()}. An
 * exception before commit/release therefore used to leave an unreachable
 * reservation on the parent. This boundary owns that child explicitly and
 * releases it on every pre-finalization failure while preserving the legacy
 * response shape for successful requests.</p>
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
        if (request == null
                || !"query".equalsIgnoreCase(request.context)
                || request.parameters == null
                || !request.parameters.has("request")
                || request.parameters.isNull("request")
                || !request.parameters.has("token")
                || request.parameters.isNull("token")) {
            return delegate.run(packet);
        }

        IUser user;
        try {
            user = UserFactory.getUser(request.parameters.getString("token"));
            if (user.getCurrentMind() == null) {
                user.setCurrentMind(new Mind(user));
            }
            return decorate(execute(request.parameters, user), user);
        } catch (Exception failure) {
            JSONObject result = error("query_execution_failed", failure.toString());
            try {
                String token = request.parameters.optString("token", "");
                if (!token.isEmpty()) {
                    user = UserFactory.getUser(token);
                    return decorate(result, user);
                }
            } catch (Exception ignored) {
                // Authentication/session failure is already represented by the
                // original error and must not hide it.
            }
            return result;
        }
    }

    private JSONObject execute(JSONObject parameters, IUser user) throws Exception {
        IMind parent = user.getCurrentMind();
        parent.clearLog();
        ((HypothesisStore) parent.getHypothesis()).clear();

        String source = URLDecoder.decode(
                parameters.getString("request"), "UTF-8");
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
        } catch (Exception failure) {
            if (!finalizationStarted) {
                try {
                    parent.release(child);
                } catch (Exception cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw failure;
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

    private JSONObject error(String code, String description) {
        return new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description == null ? "" : description);
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
