/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.command.SourceNamePolicy;
import org.kanger.interfaces.IReactor;

import java.util.Collection;

/**
 * Canonical filesystem-name boundary for Server source transport operations.
 *
 * <p>Named {@code get}/{@code put}/{@code delete} requests are validated as
 * safe leaf names and then normalized through the shared command source-name
 * policy before any filesystem-owning reactor can observe them. Bare source
 * discovery remains read-only and publishes only physical names already in
 * canonical {@code *.k} form.</p>
 */
final class SourceTransportBoundaryReactor implements IReactor<JSONObject> {

    private static final String[] SOURCE_PARAMETERS =
            new String[]{"get", "put", "delete"};

    private final IReactor<JSONObject> delegate;

    SourceTransportBoundaryReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        JSONObject violation = canonicalizeNamedSource(packet);
        if (violation != null) {
            return violation;
        }

        Object response = delegate.run(packet);
        return filterSourceDiscovery(packet, response);
    }

    private JSONObject canonicalizeNamedSource(JSONObject packet) {
        if (!"command".equalsIgnoreCase(context(packet))) {
            return null;
        }
        JSONObject parameters = parameters(packet);
        if (parameters == null) {
            return null;
        }
        for (String name : SOURCE_PARAMETERS) {
            if (!parameters.has(name) || parameters.isNull(name)) {
                continue;
            }
            Object raw = parameters.opt(name);
            if (!(raw instanceof String)) {
                continue;
            }
            String value = ((String) raw).trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!ApiInputPolicy.isSafeLeafName(value)) {
                return invalid(name);
            }
            String canonical = SourceNamePolicy.canonicalize(value);
            if (!ApiInputPolicy.isSafeLeafName(canonical)) {
                return invalid(name);
            }
            parameters.put(name, canonical);
        }
        return null;
    }

    private Object filterSourceDiscovery(JSONObject packet, Object response) {
        if (!(response instanceof JSONObject)
                || !"command".equalsIgnoreCase(context(packet))) {
            return response;
        }
        JSONObject parameters = parameters(packet);
        if (parameters == null
                || !parameters.has("get")
                || parameters.isNull("get")
                || !parameters.optString("get", "").isEmpty()) {
            return response;
        }

        JSONObject result = (JSONObject) response;
        if (!"OK".equalsIgnoreCase(result.optString("result", ""))
                || !result.has("list") || result.isNull("list")) {
            return response;
        }

        JSONArray filtered = new JSONArray();
        Object raw = result.opt("list");
        if (raw instanceof JSONArray) {
            JSONArray list = (JSONArray) raw;
            for (int index = 0; index < list.length(); index++) {
                Object item = list.opt(index);
                if (item != null
                        && SourceNamePolicy.isCanonicalSourceFileName(
                                String.valueOf(item))) {
                    filtered.put(item);
                }
            }
        } else if (raw instanceof Collection<?>) {
            for (Object item : (Collection<?>) raw) {
                if (item != null
                        && SourceNamePolicy.isCanonicalSourceFileName(
                                String.valueOf(item))) {
                    filtered.put(item);
                }
            }
        } else {
            return response;
        }

        result.put("list", filtered);
        result.put("size", filtered.length());
        return result;
    }

    private JSONObject invalid(String parameter) {
        return new JSONObject()
                .put("result", "error")
                .put("code", "source_name_invalid")
                .put("description",
                        "Invalid filesystem identifier in parameter " + parameter);
    }

    private String context(JSONObject packet) {
        JSONObject envelope = envelope(packet);
        return envelope == null ? "" : envelope.optString("context", "");
    }

    private JSONObject parameters(JSONObject packet) {
        JSONObject envelope = envelope(packet);
        if (envelope == null) {
            return null;
        }
        try {
            return envelope.getJSONObject("parameters");
        } catch (JSONException malformed) {
            return null;
        }
    }

    private JSONObject envelope(JSONObject packet) {
        if (packet == null) {
            return null;
        }
        JSONObject body = packet.optJSONObject("body");
        if (body != null && body.has("context")) {
            return body;
        }
        return packet.optJSONObject("query");
    }
}
