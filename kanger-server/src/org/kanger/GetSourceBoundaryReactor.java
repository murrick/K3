/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Exact-document boundary for an explicit browser {@code get <source>}.
 *
 * <p>The stored document is read byte-for-byte as UTF-8 and kept unchanged for
 * editor/persistence purposes. Only the compiler view receives the virtual
 * terminal line boundary required by the historical parser. A rejected load
 * remains an error, does not publish the rejected document as accepted source,
 * and returns that exact document separately for repair in the browser editor.</p>
 *
 * <p>A load is one operation-local technical transaction over the current
 * user-visible Mind. Successful compilation commits the semantic delta into
 * that same user level; rejection or failure rolls it back. The technical
 * child is never published through {@link IUser#setCurrentMind(IMind)} and
 * therefore cannot change the explicit transaction depth.</p>
 *
 * <p>This reactor deliberately owns only non-empty {@code command/get}
 * requests. Source listing and every other command remain with the existing
 * reactor chain.</p>
 */
public final class GetSourceBoundaryReactor implements IReactor<JSONObject> {

    private final IReactor<JSONObject> delegate;

    public GetSourceBoundaryReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate reactor is required");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        Request request = Request.parse(packet);
        if (!isExplicitGet(request)) {
            return delegate.run(packet);
        }

        IUser user = UserFactory.getUser(request.parameters.getString("token"));
        if (user.getCurrentMind() == null) {
            user.setCurrentMind(new Mind(user));
        }
        JSONObject result = load(request.parameters, user);
        IMind current = user.getCurrentMind();
        result.put("transaction", current.getTransactionLevel());
        result.put("empty", current.isEmptyLevel());
        return result;
    }

    private boolean isExplicitGet(Request request) {
        return request != null
                && "command".equalsIgnoreCase(request.context)
                && request.parameters != null
                && request.parameters.has("token")
                && !request.parameters.isNull("token")
                && request.parameters.has("get")
                && !request.parameters.isNull("get")
                && !request.parameters.optString("get", "").isEmpty();
    }

    private JSONObject load(JSONObject parameters, IUser user) throws Exception {
        Mind parent = (Mind) user.getCurrentMind();
        String fileName = parameters.getString("get");
        File file = new File(user.getSourceDir() + fileName);

        if (!file.exists()) {
            return error("source_not_found", "File not found " + file);
        }
        if (file.length() == 0L) {
            return error("source_empty", "File is empty " + file);
        }

        String exactSource = new String(
                Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String previousName = parent.getSourceFileName();

        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(parent)) {
            Mind work = tx.mind();
            Boolean compiled = work.compile(
                    SourceDocumentState.compilerInput(exactSource));
            String description = work.getCurrentLogRecord(LogMode.ANALYZER)
                    .getRecord();

            if (!Boolean.TRUE.equals(compiled)) {
                tx.rollback();
                parent.setSourceFileName(previousName);
                parent.setQueryResult(false);
                user.setCurrentMind(parent);
                return recoveryError("source_compile_rejected", description,
                        file.getName(), exactSource);
            }

            boolean committed = tx.commit();
            if (!committed) {
                parent.setSourceFileName(previousName);
                parent.setQueryResult(false);
                user.setCurrentMind(parent);
                return recoveryError("source_compile_rejected", description,
                        file.getName(), exactSource);
            }

            parent.setSourceFileName(file.getName());
            SourceDocumentState.publish(user, exactSource);
            description += "<br>File " + file.getName() + " loaded";
            if (parent.getTransactionLevel() > 0) {
                description += "<br>Transaction level "
                        + parent.getTransactionLevel() + " (" + parent.getId() + ")";
            }
            parent.setQueryResult(true);
            user.setCurrentMind(parent);
            return ok(description);
        } catch (Exception failure) {
            parent.setSourceFileName(previousName);
            parent.setQueryResult(false);
            user.setCurrentMind(parent);
            return recoveryError("source_load_failed", failure.toString(),
                    file.getName(), exactSource);
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

    private JSONObject recoveryError(String code,
                                     String description,
                                     String logicalName,
                                     String text) {
        return error(code, description).put("source_recovery", new JSONObject()
                .put("schema", 1)
                .put("logical_name", logicalName)
                .put("text", text == null ? "" : text));
    }

    private static final class Request {
        private final String context;
        private final JSONObject parameters;

        private Request(String context, JSONObject parameters) {
            this.context = context;
            this.parameters = parameters;
        }

        private static Request parse(JSONObject packet) {
            try {
                JSONObject envelope = packet.getJSONObject("body");
                return new Request(envelope.getString("context"),
                        envelope.getJSONObject("parameters"));
            } catch (JSONException bodyError) {
                try {
                    JSONObject envelope = packet.getJSONObject("query");
                    return new Request(envelope.getString("context"),
                            envelope.getJSONObject("parameters"));
                } catch (JSONException queryError) {
                    return null;
                }
            }
        }
    }
}
