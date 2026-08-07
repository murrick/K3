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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Projects one canonical source/storage/workspace state into every authenticated
 * response after the underlying operation has completed.
 *
 * <p>The legacy browser historically inferred state from command names and
 * optimistic response text. This boundary makes the server-side runtime state
 * explicit, including failed operations that preserve the previous workspace.</p>
 */
public final class WorkspaceStateReactor implements IReactor<JSONObject> {

    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    private final IReactor<JSONObject> delegate;

    public WorkspaceStateReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate reactor is required");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        Request request = Request.parse(packet);
        IUser user = resolveUser(request);
        normalizeRequest(request);

        Object response = delegate.run(packet);
        if (!(response instanceof JSONObject) || user == null
                || user.getCurrentMind() == null) {
            return response;
        }

        JSONObject result = (JSONObject) response;
        normalizeTypedError(request, result);
        normalizeLists(request, result, user);
        result.put("workspace", project(user));
        return result;
    }

    private IUser resolveUser(Request request) {
        if (request == null || request.parameters == null
                || !request.parameters.has("token")
                || request.parameters.isNull("token")) {
            return null;
        }
        String token = request.parameters.optString("token", "");
        if (token.isEmpty()) {
            return null;
        }
        try {
            return UserFactory.getUser(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void normalizeRequest(Request request) {
        if (request == null || request.parameters == null
                || !"command".equalsIgnoreCase(request.context)) {
            return;
        }
        JSONObject parameters = request.parameters;
        normalizeSourceParameter(parameters, "get");
        normalizeSourceParameter(parameters, "put");
        normalizeSourceParameter(parameters, "delete");
        normalizeStorageParameter(parameters, "use");
        normalizeStorageParameter(parameters, "drop");
        normalizeStorageParameter(parameters, "reindex");
    }

    private void normalizeSourceParameter(JSONObject parameters, String name) {
        if (!parameters.has(name) || parameters.isNull(name)) {
            return;
        }
        String value = parameters.optString(name, "");
        if (!value.trim().isEmpty()) {
            parameters.put(name, canonicalSourceName(value));
        }
    }

    private void normalizeStorageParameter(JSONObject parameters, String name) {
        if (!parameters.has(name) || parameters.isNull(name)) {
            return;
        }
        String value = parameters.optString(name, "");
        if (!value.trim().isEmpty()) {
            parameters.put(name, canonicalStorageLogicalName(value));
        }
    }

    private void normalizeTypedError(Request request, JSONObject result) {
        if (request == null || result == null
                || !"error".equalsIgnoreCase(result.optString("result"))) {
            return;
        }

        String existing = result.optString("code", "");
        if ("command".equalsIgnoreCase(request.context)
                && request.parameters.has("used") && existing.isEmpty()) {
            result.put("code", "storage_not_used");
            return;
        }

        if (!"operation_failed".equals(existing)) {
            return;
        }
        String typed = typedOperationCode(request);
        if (typed != null) {
            result.put("code", typed);
        }
    }

    private String typedOperationCode(Request request) {
        if (request == null || request.parameters == null) {
            return null;
        }
        JSONObject parameters = request.parameters;
        if ("command".equalsIgnoreCase(request.context)) {
            if (parameters.has("get")
                    && !parameters.optString("get", "").isEmpty()) {
                return "source_load_failed";
            }
            if (parameters.has("put")) {
                return "source_save_failed";
            }
            if (parameters.has("delete")) {
                return "source_delete_failed";
            }
            if (parameters.has("use")
                    && !parameters.optString("use", "").isEmpty()) {
                return "storage_switch_failed";
            }
            if (parameters.has("close")) {
                return "storage_close_failed";
            }
            if (parameters.has("drop")) {
                return "storage_drop_failed";
            }
            if (parameters.has("reindex")) {
                return "storage_reindex_failed";
            }
        }
        if ("query".equalsIgnoreCase(request.context)
                && parameters.has("compile")) {
            return "source_compile_failed";
        }
        return null;
    }

    private void normalizeLists(Request request, JSONObject result, IUser user)
            throws Exception {
        if (request == null || result == null
                || !"OK".equalsIgnoreCase(result.optString("result"))
                || !"command".equalsIgnoreCase(request.context)) {
            return;
        }
        if (request.parameters.has("get")
                && request.parameters.optString("get", "").isEmpty()) {
            normalizeSourceList(result, user);
        }
        if (request.parameters.has("use")
                && request.parameters.optString("use", "").isEmpty()) {
            normalizeStorageList(result, user);
        }
    }

    private void normalizeSourceList(JSONObject result, IUser user) throws Exception {
        JSONArray legacy = result.optJSONArray("list");
        Set<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (legacy != null) {
            for (int index = 0; index < legacy.length(); index++) {
                String one = legacy.optString(index, "").trim();
                if (one.toLowerCase(Locale.ROOT).endsWith(".k")) {
                    names.add(canonicalSourceName(one));
                }
            }
        }

        JSONArray list = new JSONArray();
        JSONArray sources = new JSONArray();
        String activeName = nullToEmpty(user.getCurrentMind().getSourceFileName());
        JSONObject current = sourceProjection(user.getCurrentMind(), user);
        for (String name : names) {
            list.put(name);
            boolean active = name.equalsIgnoreCase(activeName);
            sources.put(new JSONObject()
                    .put("logical_name", name)
                    .put("active", active)
                    .put("repository_state", active
                            ? current.getString("repository_state") : "saved"));
        }
        result.put("list", list);
        result.put("size", list.length());
        result.put("sources", sources);
    }

    private void normalizeStorageList(JSONObject result, IUser user) throws Exception {
        JSONArray legacy = result.optJSONArray("list");
        Set<String> logicalNames = new TreeSet<String>(
                new Comparator<String>() {
                    @Override
                    public int compare(String left, String right) {
                        int insensitive = left.compareToIgnoreCase(right);
                        return insensitive != 0 ? insensitive : left.compareTo(right);
                    }
                });
        if (legacy != null) {
            for (int index = 0; index < legacy.length(); index++) {
                String raw = legacy.optString(index, "").trim();
                if (!raw.isEmpty()) {
                    logicalNames.add(logicalStorageName(raw));
                }
            }
        }

        IMind mind = user.getCurrentMind();
        String activeCanonical = mind.isStorageUsed()
                ? nullToEmpty(mind.getStorageName()) : "";
        JSONArray list = new JSONArray();
        JSONArray storages = new JSONArray();
        for (String logical : logicalNames) {
            String canonical = canonicalStorageName(logical);
            list.put(logical);
            storages.put(new JSONObject()
                    .put("logical_name", logical)
                    .put("canonical_name", canonical)
                    .put("active", canonical.equals(activeCanonical))
                    .put("generation_present",
                            generationState(user, canonical).getBoolean("present")));
        }
        result.put("list", list);
        result.put("size", list.length());
        result.put("storages", storages);
    }

    static JSONObject project(IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject workspace = new JSONObject();
        workspace.put("schema", 1);
        workspace.put("source", sourceProjection(mind, user));
        workspace.put("storage", storageProjection(mind, user));
        workspace.put("transaction", new JSONObject()
                .put("level", mind.getTransactionLevel())
                .put("empty", mind.isEmptyLevel()));
        return workspace;
    }

    private static JSONObject sourceProjection(IMind mind, IUser user)
            throws Exception {
        String logicalName = nullToEmpty(mind.getSourceFileName());
        String source = nullToEmpty(mind.getSourceCode());
        byte[] currentBytes = source.getBytes(StandardCharsets.UTF_8);
        Path path = logicalName.isEmpty() ? null : sourcePath(user, logicalName);
        boolean persisted = path != null && Files.isRegularFile(path);
        String repositoryState;
        boolean dirty;
        if (logicalName.isEmpty()) {
            repositoryState = "unbound";
            dirty = !source.isEmpty();
        } else if (!persisted) {
            repositoryState = "missing";
            dirty = true;
        } else {
            byte[] stored = Files.readAllBytes(path);
            boolean equal = Arrays.equals(stored, currentBytes);
            repositoryState = equal ? "saved" : "modified";
            dirty = !equal;
        }

        return new JSONObject()
                .put("logical_name", logicalName.isEmpty()
                        ? JSONObject.NULL : logicalName)
                .put("has_text", !source.isEmpty())
                .put("bytes_utf8", currentBytes.length)
                .put("repository_state", repositoryState)
                .put("persisted", persisted)
                .put("dirty", dirty);
    }

    private static JSONObject storageProjection(IMind mind, IUser user)
            throws Exception {
        boolean active = mind.isStorageUsed();
        String canonical = active ? nullToEmpty(mind.getStorageName()) : "";
        JSONObject generation = active
                ? generationState(user, canonical)
                : emptyGeneration();
        return new JSONObject()
                .put("active", active)
                .put("logical_name", active
                        ? logicalStorageName(canonical) : JSONObject.NULL)
                .put("canonical_name", active ? canonical : JSONObject.NULL)
                .put("physical_generation", generation);
    }

    private static JSONObject generationState(IUser user, String canonical)
            throws IOException {
        JSONArray artifacts = new JSONArray();
        Path base = storagePath(user, canonical);
        boolean present = false;
        for (String suffix : GENERATION_SUFFIXES) {
            if (Files.isRegularFile(Paths.get(base.toString() + suffix))) {
                artifacts.put(suffix.substring(1));
                present = true;
            }
        }

        int walSegments = 0;
        Path directory = base.getParent();
        if (directory != null && Files.isDirectory(directory)) {
            String prefix = base.getFileName().toString() + ".wal.";
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)
                            && entry.getFileName().toString().startsWith(prefix)) {
                        walSegments++;
                        present = true;
                    }
                }
            }
        }
        return new JSONObject()
                .put("present", present)
                .put("artifacts", artifacts)
                .put("wal_segments", walSegments);
    }

    private static JSONObject emptyGeneration() {
        return new JSONObject()
                .put("present", false)
                .put("artifacts", new JSONArray())
                .put("wal_segments", 0);
    }

    private static Path sourcePath(IUser user, String fileName) {
        Path base = Paths.get(user.getSourceDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(fileName).normalize();
        return resolved.startsWith(base) ? resolved : null;
    }

    private static Path storagePath(IUser user, String canonical) {
        return Paths.get(user.getDatabaseDir()).resolve(canonical).normalize();
    }

    private static String canonicalSourceName(String value) {
        String normalized = nullToEmpty(value).trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".k")) {
            normalized += ".k";
        }
        return normalized;
    }

    private static String canonicalStorageLogicalName(String value) {
        String normalized = nullToEmpty(value).trim()
                .replace('\\', '.')
                .replace('/', '.');
        while (normalized.contains("..")) {
            normalized = normalized.replace("..", ".");
        }
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String canonicalStorageName(String logical) {
        return canonicalStorageLogicalName(logical)
                .replace(".", Enums.FILE_SEPARATOR);
    }

    private static String logicalStorageName(String canonical) {
        return nullToEmpty(canonical)
                .replace(Enums.FILE_SEPARATOR, ".")
                .replace('\\', '.')
                .replace('/', '.');
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
