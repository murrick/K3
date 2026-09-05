/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.kanger.interfaces.IReactor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Bridges the Browser's structured read-only STATUS telemetry request into the
 * canonical server-owned command ingress.
 *
 * <p>The Browser deliberately sends {@code context=command,status=""} so its
 * operation protocol can classify telemetry as a read without parsing operator
 * command text. This adapter is the only place where that transport shape is
 * projected back to the canonical raw {@code status} command. Parsing and
 * STATUS semantics therefore remain owned by {@link CanonicalCommandIngressReactor}
 * and {@link CanonicalCommandRuntimeReactor}.</p>
 */
final class StructuredStatusIngressReactor implements IReactor<JSONObject> {

    static final String COMMAND_CONTEXT = "command";
    static final String STATUS_PARAMETER = "status";

    private final IReactor<JSONObject> delegate;

    StructuredStatusIngressReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        Envelope envelope = Envelope.parse(packet);
        if (envelope == null
                || !COMMAND_CONTEXT.equalsIgnoreCase(envelope.context)
                || !envelope.parameters.has(STATUS_PARAMETER)) {
            return delegate.run(packet);
        }

        JSONObject invalid = validate(envelope.parameters);
        if (invalid != null) {
            return invalid;
        }

        String token = envelope.parameters.optString("token", "");
        clearParameters(envelope.parameters);
        if (!token.isEmpty()) {
            envelope.parameters.put("token", token);
        }
        envelope.parameters.put(CanonicalCommandIngressReactor.LINE_PARAMETER, "status");
        envelope.setContext(CanonicalCommandIngressReactor.DIALOGUE_CONTEXT);
        return delegate.run(packet);
    }

    private JSONObject validate(JSONObject parameters) {
        Object status = parameters.opt(STATUS_PARAMETER);
        if (!(status instanceof String) || !((String) status).isEmpty()) {
            return error("structured_status_invalid",
                    "Structured STATUS parameter must be an empty string");
        }

        Iterator<String> keys = parameters.keys();
        while (keys.hasNext()) {
            String name = keys.next();
            if (!"token".equals(name) && !STATUS_PARAMETER.equals(name)) {
                return error("structured_status_invalid",
                        "Unexpected structured STATUS parameter " + name);
            }
        }
        return null;
    }

    private void clearParameters(JSONObject parameters) {
        List<String> names = new ArrayList<String>();
        Iterator<String> keys = parameters.keys();
        while (keys.hasNext()) {
            names.add(keys.next());
        }
        for (String name : names) {
            parameters.remove(name);
        }
    }

    private JSONObject error(String code, String description) {
        return new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description);
    }

    private static final class Envelope {
        private final JSONObject object;
        private final JSONObject parameters;
        private String context;

        private Envelope(JSONObject object, String context, JSONObject parameters) {
            this.object = object;
            this.context = context;
            this.parameters = parameters;
        }

        private void setContext(String context) {
            this.context = context;
            object.put("context", context);
        }

        private static Envelope parse(JSONObject packet) {
            if (packet == null) {
                return null;
            }
            Envelope body = parseObject(packet.optJSONObject("body"));
            if (body != null) {
                return body;
            }
            return parseObject(packet.optJSONObject("query"));
        }

        private static Envelope parseObject(JSONObject object) {
            if (object == null) {
                return null;
            }
            JSONObject parameters = object.optJSONObject("parameters");
            if (parameters == null) {
                return null;
            }
            String context = object.optString("context", "");
            return context.isEmpty() ? null : new Envelope(object, context, parameters);
        }
    }
}
