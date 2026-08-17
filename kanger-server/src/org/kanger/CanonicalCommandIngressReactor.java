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
import org.kanger.interfaces.IUser;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Converts one raw operator-dialogue line into the qualified legacy Server
 * operation surface without moving canonical parsing into Browser JavaScript.
 *
 * <p>The reactor is intentionally a transport adapter. It parses through the
 * shared {@code kanger-command} module, projects compatibility intents onto the
 * existing Server protocol, and leaves converged canonical intents marked for
 * {@link CanonicalCommandRuntimeReactor}. It never executes Core/storage
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
    static final String CONFIRMED_PARAMETER = "confirmed";
    static final String CANONICAL_CONTEXT = "canonical";
    static final String INVOCATION_MARKER = "_kanger_command_invocation";
    static final String CANONICAL_INTENT_FIELD = "canonical_intent";
    static final String SESSION_STATE_FIELD = "session";
    static final String SESSION_CLOSED_STATE = "closed";
    static final String DIALOGUE_CHOICES_FIELD = "dialogue_choices";
    static final String CONFIRMATION_FIELD = "confirmation";

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
        boolean confirmed = envelope.parameters.optBoolean(CONFIRMED_PARAMETER, false);
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

        if (!confirmed && requiresConfirmation(invocation, token)) {
            return confirmation(invocation);
        }

        Object response = delegate.run(packet);
        response = normalizeCanonicalResponse(invocation, response);
        response = decorateDialogueChoices(invocation, response);
        response = decorateSessionState(invocation, response);
        return decorateCanonicalIntent(invocation, response);
    }

    private Object normalizeCanonicalResponse(CommandInvocation invocation,
                                              Object response) {
        if (invocation == null || !(response instanceof JSONObject)) {
            return response;
        }
        JSONObject result = (JSONObject) response;
        if (invocation.getIntent() == CommandIntent.TX_STATUS
                && !result.has("result")) {
            result.put("result", "OK");
        }
        return response;
    }

    private Object decorateCanonicalIntent(CommandInvocation invocation,
                                           Object response) {
        if (invocation == null || invocation.isCoreLanguage()
                || !(response instanceof JSONObject)) {
            return response;
        }
        ((JSONObject) response).put(
                CANONICAL_INTENT_FIELD, invocation.getIntent().name());
        return response;
    }

    private boolean requiresConfirmation(CommandInvocation invocation,
                                         String token) {
        if (invocation == null || invocation.isCoreLanguage()) {
            return false;
        }
        switch (invocation.getIntent()) {
            case ERASE:
            case STORAGE_DROP:
                return true;
            case SOURCE_DELETE:
                return !string(invocation, "source").isEmpty();
            case SOURCE_PUT:
                return sourceExists(token, string(invocation, "source"));
            default:
                // Transaction rollback and reindex are intentionally immediate.
                return false;
        }
    }

    private boolean sourceExists(String token, String name) {
        if (token == null || token.isEmpty() || name == null || name.isEmpty()) {
            return false;
        }
        try {
            IUser user = UserFactory.getUser(token);
            String canonical = name.trim();
            if (!canonical.toLowerCase(Locale.ROOT).endsWith(".k")) {
                canonical += ".k";
            }
            return new File(user.getSourceDir(), canonical).isFile();
        } catch (Exception unavailable) {
            // Authentication/filesystem errors remain owned by the normal
            // qualified execution path; this UX guard must not replace them.
            return false;
        }
    }

    private JSONObject confirmation(CommandInvocation invocation) {
        String prompt;
        switch (invocation.getIntent()) {
            case ERASE:
                prompt = "Erase current workspace?";
                break;
            case SOURCE_DELETE:
                prompt = "Delete source " + string(invocation, "source") + "?";
                break;
            case SOURCE_PUT:
                prompt = "Overwrite source " + string(invocation, "source") + "?";
                break;
            case STORAGE_DROP:
                prompt = "Drop storage " + string(invocation, "name") + "?";
                break;
            default:
                prompt = "Confirm operation?";
                break;
        }
        return new JSONObject()
                .put("result", "confirmation_required")
                .put("code", "confirmation_required")
                .put("description", prompt)
                .put(CANONICAL_INTENT_FIELD, invocation.getIntent().name())
                .put(CONFIRMATION_FIELD, new JSONObject()
                        .put("schema", 1)
                        .put("prompt", prompt));
    }

    private Object decorateDialogueChoices(CommandInvocation invocation,
                                            Object response) {
        if (invocation == null || !(response instanceof JSONObject)) {
            return response;
        }
        JSONObject result = (JSONObject) response;
        if (!"OK".equalsIgnoreCase(result.optString("result", ""))) {
            return response;
        }

        JSONObject choices = null;
        if (invocation.getIntent() == CommandIntent.SOURCE_GET
                && string(invocation, "source").isEmpty()) {
            choices = new JSONObject()
                    .put("schema", 1)
                    .put("label", "Available sources: ")
                    .put("empty", "No source files available")
                    .put("compose", "get");
        } else if (invocation.getIntent() == CommandIntent.SOURCE_DELETE
                && string(invocation, "source").isEmpty()) {
            choices = new JSONObject()
                    .put("schema", 1)
                    .put("label", "Select source to delete: ")
                    .put("empty", "No source files available")
                    .put("compose", "delete");
        } else if (invocation.getIntent() == CommandIntent.STORAGE_STATUS) {
            choices = new JSONObject()
                    .put("schema", 1)
                    .put("label", "Available DBs: ")
                    .put("empty", "No databases was created")
                    .put("compose", "storage use");
        }
        if (choices != null) {
            result.put(DIALOGUE_CHOICES_FIELD, choices);
        }
        return response;
    }

    private Object decorateSessionState(CommandInvocation invocation, Object response) {
        if (invocation == null
                || invocation.getIntent() != CommandIntent.QUIT
                || !(response instanceof JSONObject)) {
            return response;
        }
        JSONObject result = (JSONObject) response;
        if (!"OK".equalsIgnoreCase(result.optString("result", ""))) {
            return response;
        }
        result.put(SESSION_STATE_FIELD, new JSONObject()
                .put("schema", 1)
                .put("state", SESSION_CLOSED_STATE));
        return response;
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
                if (invocation.getArgument("level") == null) {
                    return false;
                }
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

            case SOURCE_GET:
                command(envelope, "get", string(invocation, "source"));
                return true;
            case SOURCE_PUT:
                command(envelope, "put", string(invocation, "source"));
                return true;
            case SOURCE_DELETE:
                if (string(invocation, "source").isEmpty()) {
                    // Bare delete is a read-only discovery/list operation.
                    command(envelope, "get", "");
                } else {
                    command(envelope, "delete", string(invocation, "source"));
                }
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

            // Converged canonical families and canonical-only projections do
            // not fall back into the legacy query/command protocol.
            case TX_STATUS:
            case TX_START:
            case TX_COMMIT:
            case TX_ROLLBACK:
            case STORAGE_STATUS:
            case STORAGE_USE:
            case STORAGE_CLOSE:
            case STORAGE_DROP:
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
        Object value = invocation.getArgument(name);
        return value == null ? "" : String.valueOf(value);
    }

    private JSONObject validateRawEnvelope(JSONObject parameters) {
        Iterator<String> names = parameters.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!"token".equals(name)
                    && !LINE_PARAMETER.equals(name)
                    && !CONFIRMED_PARAMETER.equals(name)) {
                return error("dialogue_envelope_invalid", null,
                        "Unexpected dialogue parameter " + name);
            }
        }
        if (parameters.has(CONFIRMED_PARAMETER)
                && !parameters.isNull(CONFIRMED_PARAMETER)
                && !(parameters.opt(CONFIRMED_PARAMETER) instanceof Boolean)) {
            return error("dialogue_envelope_invalid", null,
                    "Dialogue confirmed parameter must be boolean");
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
