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
        IMind mind = user.getCurrentMind();
        IMind parent = mind;
        boolean outerChild = false;
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
        String previousName = mind.getSourceFileName();

        try {
            if (mind.isStorageUsed()) {
                mind = new Mind(mind);
                outerChild = true;
            }

            mind.setSourceFileName(file.getName());
            Boolean accepted = mind.compile(
                    SourceDocumentState.compilerInput(exactSource));
            String description = mind.getCurrentLogRecord(LogMode.ANALYZER)
                    .getRecord();

            if (Boolean.TRUE.equals(accepted)) {
                SourceDocumentState.publish(user, exactSource);
                description += "<br>File " + file.getName() + " loaded";
            } else {
                mind.setSourceFileName(previousName);
            }

            if (mind.isStorageUsed() && mind.isEmptyLevel()) {
                IMind next = mind.getNext();
                next.release(mind);
                mind = next;
                outerChild = false;
            }

            if (mind.getTransactionLevel() > 0) {
                description += "<br>Transaction level "
                        + mind.getTransactionLevel() + " (" + mind.getId() + ")";
            }
            ((Mind) mind).setQueryResult(accepted);
            user.setCurrentMind(mind);

            if (Boolean.TRUE.equals(accepted)) {
                return ok(description);
            }
            mind.setSourceFileName(previousName);
            return recoveryError("source_compile_rejected", description,
                    file.getName(), exactSource);
        } catch (Exception failure) {
            if (outerChild) {
                try {
                    parent.release(mind);
                } catch (Exception releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
            parent.setSourceFileName(previousName);
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
