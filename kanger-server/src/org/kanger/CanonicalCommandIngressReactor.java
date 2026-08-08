/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.command.CommandIntent;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParseException;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IReactor;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Converts one raw operator-dialogue line into the qualified legacy Server
 * operation surface without moving canonical parsing into Browser JavaScript.
 *
 * <p>The reactor is intentionally a transport adapter. It parses through the
 * shared {@code kanger-command} module, projects already-supported intents onto
 * the existing Server protocol, and leaves non-legacy canonical intents marked
 * for {@link CanonicalCommandRuntimeReactor}. It never executes Core/storage
 * operations itself.</p>
 *
 * <p>Placement is inside the per-session serialization/mail boundaries and
 * immediately before {@link WorkspaceStateReactor}. This ensures that the
 * Workspace projector sees the translated historical context/parameters and
 * all established lifecycle/stop-loss reactors remain below the adapter.</p>
 */
final class CanonicalCommandIngressReactor implements IReactor<JSONObject> {

    static final String DIALOGUE_CONTEXT = "dialogue";
    static final String LINE_PARAMETER = "line";
    static final String CANONICAL_CONTEXT = "canonical";
    static final String INVOCATION_MARKER = "_kanger_command_invocation";

    private final IReactor<JSONObject> delegate;
    private final CommandParser parser;

    CanonicalCommandIngressReactor(IReactor<JSONObject> delegate) {
        this(delegate, new CommandParser());
    }

    CanonicalCommandIngressReactor(IReactor<JSONObject> delegate,
                                   CommandParser parser) {
        if (delegate == null || parser == null) {
            throw new IllegalArgumentException("delegate and parser must not be null");
        }
        this.delegate = delegate;
        this.parser = parser;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        Envelope envelope = Envelope.parse(packet);
        if (envelope == null
                || !DIALOGUE_CONTEXT.equalsIgnoreCase(envelope.context)
                || !envelope.parameters.has(LINE_PARAMETER)
                || envelope.parameters.isNull(LINE_PARAMETER)) {
            return delegate.run(packet);
        }

        JSONObject envelopeError = validateRawEnvelope(envelope.parameters);
        if (envelopeError != null) {
            return envelopeError;
        }

        final String line;
        try {
            line = envelope.parameters.getString(LINE_PARAMETER);
        } catch (JSONException malformed) {
            return error("dialogue_line_invalid", null,
                    "Dialogue line must be a string");
        }

        final CommandInvocation invocation;
        try {
            invocation = parser.parse(line);
        } catch (CommandParseException rejected) {
            return error("command_parse_error", rejected.getReason().name(),
                    rejected.getMessage());
        }

        String token = envelope.parameters.optString("token", "");
        clearParameters(envelope.parameters);
        if (!token.isEmpty()) {
            envelope.parameters.put("token", token);
        }
        packet.put(INVOCATION_MARKER, invocation);

        if (invocation.isCoreLanguage()) {
            envelope.setContext("query");
            envelope.parameters.put("request",
                    URLEncoder.encode(invocation.getRaw(), "UTF-8"));
            return delegate.run(packet);
        }

        if (!translateLegacy(invocation, envelope)) {
            envelope.setContext(CANONICAL_CONTEXT);
        }

        JSONObject policyViolation = ApiInputPolicy.violation(envelope.parameters);
        if (policyViolation != null) {
            return policyViolation;
        }
        return delegate.run(packet);
    }

    private boolean translateLegacy(CommandInvocation invocation,
                                    Envelope envelope) {
        JSONObject parameters = envelope.parameters;
        switch (invocation.getIntent()) {
            case RULE_STATUS:
                query(envelope, "rules", "");
                return true;
            case RULE_SHOW:
                query(envelope, "rules", "");
                parameters.put("id", number(invocation, "id"));
                return true;
            case RULE_ALL:
                query(envelope, "rules", "all");
                return true;
            case RULE_PRODUCED:
                query(envelope, "rules", "produced");
                return true;
            case RULE_LEVEL:
                query(envelope, "rules", "");
                parameters.put("level", number(invocation, "level"));
                return true;
            case RULE_TREE:
                query(envelope, "rules", "");
                parameters.put("id", number(invocation, "id"));
                parameters.put("tree", true);
                return true;

            case FUNCTIONS:
                query(envelope, "functions", "");
                return true;
            case FUNCTION_SHOW:
                query(envelope, "functions", "");
                parameters.put("id", number(invocation, "id"));
                return true;
            case FUNCTION_SOURCE:
                query(envelope, "functions", "");
                parameters.put("id", number(invocation, "id"));
                parameters.put("sources", true);
                return true;

            case BASE_PREDICATES:
                query(envelope, "predicates", "");
                return true;
            case BASE_PREDICATE:
                query(envelope, "statements", "");
                parameters.put("predicate", invocation.getArgument("predicate"));
                return true;

            case VALUES:
                query(envelope, "results", "");
                return true;
            case SOLUTIONS:
                query(envelope, "solutions", "");
                return true;
            case WHEN_STATUS:
                query(envelope, "hypothesis", "");
                return true;

            case TX_STATUS:
                query(envelope, "transaction", "");
                return true;
            case TX_START:
                query(envelope, "transaction", "create");
                return true;
            case TX_COMMIT:
                query(envelope, "transaction", "commit");
                return true;
            case TX_ROLLBACK:
                query(envelope, "transaction", "rollback");
                return true;

            case SOURCE_GET:
                command(envelope, "get", string(invocation, "source"));
                return true;
            case SOURCE_PUT:
                command(envelope, "put", string(invocation, "source"));
                return true;
            case SOURCE_DELETE:
                command(envelope, "delete", string(invocation, "source"));
                return true;

            case STORAGE_STATUS:
                command(envelope, "use", "");
                return true;
            case STORAGE_USE:
                command(envelope, "use", string(invocation, "name"));
                return true;
            case STORAGE_CLOSE:
                command(envelope, "close", "");
                return true;
            case STORAGE_DROP:
                command(envelope, "drop", string(invocation, "name"));
                return true;
            case STORAGE_REINDEX:
                command(envelope, "reindex", string(invocation, "name"));
                return true;

            case ERASE:
                command(envelope, "erase", "");
                return true;
            case QUIT:
                command(envelope, "quit", "");
                return true;

            // Canonical-runtime bindings deliberately do not fall through into
            // broader/overloaded legacy paths.
            case RULE_COMMENT_GET:
            case RULE_COMMENT_SET:
            case BASE_STATUS:
            case BASE_TREE:
            case VALUES_ORDER:
            case SOLUTION_SHOW:
            case SOLUTION_TREE:
            case WHEN_ACCEPT:
            case HELP:
                return false;
            default:
                return false;
        }
    }

    private void query(Envelope envelope, String name, Object value) {
        envelope.setContext("query");
        envelope.parameters.put(name, value);
    }

    private void command(Envelope envelope, String name, Object value) {
        envelope.setContext("command");
        envelope.parameters.put(name, value);
    }

    private long number(CommandInvocation invocation, String name) {
        return ((Number) invocation.getArgument(name)).longValue();
    }

    private String string(CommandInvocation invocation, String name) {
        return String.valueOf(invocation.getArgument(name));
    }

    private JSONObject validateRawEnvelope(JSONObject parameters) {
        Iterator<String> names = parameters.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!"token".equals(name) && !LINE_PARAMETER.equals(name)) {
                return error("dialogue_envelope_invalid", null,
                        "Unexpected dialogue parameter " + name);
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

    private JSONObject error(String code, String reason, String description) {
        JSONObject result = new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description == null ? "" : description);
        if (reason != null) {
            result.put("reason", reason);
        }
        return result;
    }

    static CommandInvocation invocation(JSONObject packet) {
        if (packet == null || !packet.has(INVOCATION_MARKER)) {
            return null;
        }
        Object value = packet.opt(INVOCATION_MARKER);
        return value instanceof CommandInvocation ? (CommandInvocation) value : null;
    }

    static String context(JSONObject packet) {
        Envelope envelope = Envelope.parse(packet);
        return envelope == null ? "" : envelope.context;
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
