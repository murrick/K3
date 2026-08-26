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
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Date;

/**
 * Narrow compatibility wrapper that establishes a stop-loss boundary around
 * destructive browser operations while the legacy protocol remains in use.
 *
 * <p>The delegate remains authoritative for every non-intercepted request. This
 * class does not redesign the browser API; it only prevents known destructive
 * paths from bypassing transaction and filesystem invariants.</p>
 */
public final class DestructiveStopLossReactor implements IReactor<JSONObject> {

    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    private final IReactor<JSONObject> delegate;

    public DestructiveStopLossReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate reactor is required");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        Request request = Request.parse(packet);
        if (request == null || request.parameters == null
                || !request.parameters.has("token")
                || request.parameters.isNull("token")) {
            return delegate.run(packet);
        }

        String operation = interceptedOperation(request);
        if (operation == null) {
            return delegate.run(packet);
        }

        try {
            IUser user = UserFactory.getUser(request.parameters.getString("token"));
            if (user.getCurrentMind() == null) {
                user.setCurrentMind(new Mind(user));
            }

            JSONObject result;
            if ("compile".equals(operation)) {
                result = compile(request.parameters, user);
            } else if ("put".equals(operation)) {
                result = saveSource(request.parameters, user);
            } else if ("get".equals(operation)) {
                result = loadSource(request.parameters, user);
            } else if ("delete".equals(operation)) {
                result = deleteSource(request.parameters, user);
            } else if ("drop".equals(operation)) {
                result = dropStorage(request.parameters, user);
            } else if ("reindex".equals(operation)) {
                result = reindexStorage(request.parameters, user);
            } else if ("use".equals(operation)) {
                result = useStorage(request.parameters, user);
            } else {
                JSONObject blocked = requireRootTransaction(user, operation);
                if (blocked != null) {
                    result = blocked;
                } else {
                    return delegate.run(packet);
                }
            }
            return decorate(result, user);
        } catch (SourceDeleteException failure) {
            throw failure;
        } catch (StorageSwitchException failure) {
            return error("storage_switch_failed", failure.getMessage());
        } catch (Exception error) {
            if ("drop".equals(operation)
                    && error instanceof org.kanger.exception.StorageLifecycleException) {
                throw error;
            }
            System.err.println(new Date());
            error.printStackTrace(System.err);
            return error("operation_failed", error.toString());
        }
    }

    private String interceptedOperation(Request request) {
        if ("query".equalsIgnoreCase(request.context)
                && request.parameters.has("compile")
                && !request.parameters.isNull("compile")) {
            return "compile";
        }
        if (!"command".equalsIgnoreCase(request.context)) {
            return null;
        }
        if (request.parameters.has("put") && !request.parameters.isNull("put")) {
            return "put";
        }
        if (request.parameters.has("get") && !request.parameters.isNull("get")
                && !request.parameters.optString("get", "").isEmpty()) {
            return "get";
        }
        if (request.parameters.has("delete") && !request.parameters.isNull("delete")) {
            return "delete";
        }
        if (request.parameters.has("drop") && !request.parameters.isNull("drop")) {
            return "drop";
        }
        if (request.parameters.has("reindex") && !request.parameters.isNull("reindex")) {
            return "reindex";
        }
        if (request.parameters.has("use") && !request.parameters.isNull("use")
                && !request.parameters.optString("use", "").isEmpty()) {
            return "use";
        }
        if (request.parameters.has("erase") && !request.parameters.isNull("erase")) {
            return "erase";
        }
        if (request.parameters.has("close") && !request.parameters.isNull("close")) {
            return "close";
        }
        return null;
    }

    private JSONObject compile(JSONObject parameters, IUser user) throws Exception {
        JSONObject blocked = requireRootTransaction(user, "compile");
        if (blocked != null) {
            return blocked;
        }

        String source = URLDecoder.decode(parameters.getString("compile"), "UTF-8");
        CompileProbe probe = validateReplacement(source);
        if (!probe.accepted) {
            return error("compile_rejected", probe.description);
        }

        RootCurrentLevelSourceReplacement.Outcome replacement =
                RootCurrentLevelSourceReplacement.replace(user, source);
        if (!replacement.isAccepted()) {
            return error("compile_apply_failed", replacement.getDescription());
        }
        return ok(replacement.getDescription());
    }

    private CompileProbe validateReplacement(String source) throws Exception {
        IUser probeUser = new User();
        new UDF().init(probeUser);
        IMind probeMind = new Mind(probeUser);
        boolean accepted = probeMind.compile(source);
        return new CompileProbe(accepted,
                probeMind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
    }

    private JSONObject saveSource(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        String fileName = parameters.optString("put", "");
        if (fileName == null || fileName.isEmpty()) {
            return error("source_name_required", "You have to select name for file.");
        }

        Path target = sourcePath(user, fileName);
        Path directory = target.getParent();
        if (directory == null) {
            directory = Paths.get(".").toAbsolutePath().normalize();
            target = directory.resolve(target.getFileName());
        }

        Path temporary = null;
        boolean existed = false;
        boolean published = false;
        try {
            Files.createDirectories(directory);
            existed = Files.exists(target);
            temporary = Files.createTempFile(directory,
                    target.getFileName().toString() + ".", ".tmp");
            byte[] data = SourceContextMaterializer.materializeCurrentLevel(mind)
                    .getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            published = true;
        } catch (IOException failure) {
            return error("source_save_failed", "Source save failed");
        } finally {
            if (!published && temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    Watchdog.warn("Unable to clean temporary source file");
                }
            }
        }

        if (!existed) {
            try {
                Watchdog.log(user, "New source file created: " + fileName);
            } catch (Exception logFailure) {
                Watchdog.warn("Unable to record source creation event");
            }
        }
        return ok("Source file " + fileName + " saved.");
    }

    private JSONObject loadSource(JSONObject parameters, IUser user) throws Exception {
        String fileName = parameters.optString("get", "");
        Path source = sourcePath(user, fileName);
        if (!Files.isRegularFile(source)) {
            return error("source_not_found", "File not found " + source);
        }
        if (Files.size(source) == 0L) {
            return error("source_empty", "File is empty " + source);
        }

        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        IMind parent = user.getCurrentMind();
        IMind mind = parent;
        boolean childCreated = false;
        try {
            if (mind.isStorageUsed()) {
                mind = new Mind(mind);
                childCreated = true;
            }
            boolean accepted = mind.compile(text);
            String description = mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord();
            if (accepted) {
                description += "<br>File " + source.getFileName() + " loaded";
            }
            if (childCreated && mind.isEmptyLevel()) {
                parent.release(mind);
                mind = parent;
                childCreated = false;
            }
            if (mind.getTransactionLevel() > 0) {
                description += "<br>Transaction level "
                        + mind.getTransactionLevel() + " (" + mind.getId() + ")";
            }
            ((Mind) mind).setQueryResult(accepted);
            user.setCurrentMind(mind);
            return accepted
                    ? ok(description)
                    : error("source_compile_rejected", description);
        } catch (Exception failure) {
            if (childCreated) {
                try {
                    parent.release(mind);
                } catch (Exception releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
                user.setCurrentMind(parent);
            }
            throw failure;
        }
    }

    private JSONObject deleteSource(JSONObject parameters, IUser user) throws Exception {
        String fileName = parameters.optString("delete", "");
        if (fileName == null || fileName.isEmpty()) {
            return error("source_name_required", "You have to select name for file");
        }
        Path target = sourcePath(user, fileName);
        if (!Files.exists(target)) {
            return error("source_not_found", "Source file not found " + fileName);
        }
        try {
            Files.delete(target);
        } catch (IOException failure) {
            throw new SourceDeleteException(fileName, failure);
        }
        if (Files.exists(target)) {
            return error("source_delete_incomplete",
                    "Source file could not be deleted " + fileName);
        }
        Watchdog.log(user, "Source file " + fileName + " deleted.");
        return ok("Source file " + fileName + " deleted.");
    }

    private JSONObject useStorage(JSONObject parameters, IUser user) throws Exception {
        String displayName = parameters.optString("use", "");
        String storageName = canonicalStorageName(displayName);
        IMind mind = user.getCurrentMind();
        String previousStorage = mind.isStorageUsed() ? mind.getStorageName() : null;

        if (previousStorage != null && previousStorage.equals(storageName)) {
            return storageInfo(mind);
        }

        /*
         * An open-generation A->B switch belongs entirely to Core. This
         * stop-loss boundary may probe B before mutation, but it must not
         * replay source text or perform an independent restore afterward:
         * User.use transports every explicit U-level as semantic delta and
         * compensates back to A if target replay fails after mutation starts.
         */
        if (previousStorage != null) {
            try {
                probeStorage(user, storageName);
                IMind rebased = mind.useStorage(storageName);
                user.setCurrentMind(rebased);
                if (!rebased.isStorageUsed()) {
                    throw new IOException("Error opening database " + displayName);
                }
                return storageInfo(rebased);
            } catch (Exception switchFailure) {
                throw new StorageSwitchException(displayName, switchFailure);
            }
        }

        /*
         * Historical no-storage workspace attachment remains a separate
         * compatibility case: a transient level-0 semantic projection has to
         * become an overlay above the newly attached persistent base. Unlike
         * A->B, there is no pre-existing persistent U0 for Core to rebase.
         */
        JSONObject blocked = requireRootTransaction(user, "use");
        if (blocked != null) {
            return blocked;
        }

        String previousSource = SourceContextMaterializer.materializeCurrentLevel(mind);
        try {
            probeStorage(user, storageName);
        } catch (Exception probeFailure) {
            throw new StorageSwitchException(displayName, probeFailure);
        }
        try {
            mind = mind.useStorage(storageName);
            user.setCurrentMind(mind);
            if (!mind.isStorageUsed()) {
                throw new IOException("Error opening database " + displayName);
            }

            if (!previousSource.isEmpty()) {
                IMind overlay = null;
                try {
                    overlay = new Mind(mind);
                    if (overlay.compile(previousSource)) {
                        if (!overlay.isEmptyLevel()) {
                            mind = overlay;
                            overlay = null;
                        } else {
                            mind.release(overlay);
                            overlay = null;
                        }
                    } else {
                        mind.release(overlay);
                        overlay = null;
                        throw new IOException("Current workspace conflicts with database "
                                + displayName);
                    }
                } catch (Exception overlayFailure) {
                    if (overlay != null) {
                        try {
                            mind.release(overlay);
                        } catch (Exception releaseFailure) {
                            overlayFailure.addSuppressed(releaseFailure);
                        }
                    }
                    throw overlayFailure;
                }
            }
            user.setCurrentMind(mind);
            return storageInfo(mind);
        } catch (Exception switchFailure) {
            try {
                restoreStorage(user, null, previousSource);
            } catch (Exception restoreFailure) {
                switchFailure.addSuppressed(restoreFailure);
            }
            throw new StorageSwitchException(displayName, switchFailure);
        }
    }

    private void probeStorage(IUser sourceUser, String storageName) throws Exception {
        IUser probeUser = new User();
        probeUser.setDatabaseDir(sourceUser.getDatabaseDir());
        new UDF().init(probeUser);
        new DB().init(probeUser);
        IMind probeMind = new Mind(probeUser);
        probeMind = probeMind.useStorage(storageName);
        probeMind.closeStorage();
    }

    private void restoreStorage(IUser user, String storageName, String source)
            throws Exception {
        IMind mind = user.getCurrentMind();
        if (mind == null) {
            mind = new Mind(user);
        }
        if (mind.getTransactionLevel() > 0) {
            throw new IllegalStateException("Cannot restore storage through active transaction");
        }
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
        }
        if (storageName != null && !storageName.isEmpty()) {
            mind = mind.useStorage(storageName);
        } else if (source != null && !source.isEmpty() && !mind.compile(source)) {
            throw new IllegalStateException("Previous workspace could not be restored");
        }
        user.setCurrentMind(mind);
    }

    private JSONObject reindexStorage(JSONObject parameters, IUser user) throws Exception {
        JSONObject blocked = requireRootTransaction(user, "reindex");
        if (blocked != null) {
            return blocked;
        }

        IMind mind = user.getCurrentMind();
        String requested = parameters.optString("reindex", "");
        String storageName = requested.isEmpty()
                ? mind.getStorageName() : canonicalStorageName(requested);
        try {
            if (storageName == null || storageName.isEmpty()
                    || !storageArtifactsExist(user, storageName)) {
                return error("storage_not_found", "Database not found " + requested);
            }

            deleteStorageArtifacts(user, storageName + "-temporary");
            mind = mind.reindexStorage(storageName);
            user.setCurrentMind(mind);
            deleteStorageArtifacts(user, storageName + "-temporary");
            return ok("Database " + requested + " indexed");
        } catch (Exception reindexFailure) {
            return error("storage_reindex_failed", "Storage reindex failed");
        }
    }

    private JSONObject dropStorage(JSONObject parameters, IUser user) throws Exception {
        JSONObject blocked = requireRootTransaction(user, "drop");
        if (blocked != null) {
            return blocked;
        }

        IMind mind = user.getCurrentMind();
        String requested = parameters.optString("drop", "");
        String storageName = requested.isEmpty()
                ? mind.getStorageName() : canonicalStorageName(requested);
        if (storageName == null || storageName.isEmpty()) {
            return error("storage_not_found", "Database not found " + requested);
        }
        try {
            if (!storageArtifactsExist(user, storageName)) {
                return error("storage_not_found", "Database not found " + requested);
            }
        } catch (IOException probeFailure) {
            org.kanger.exception.StorageLifecycleException failure =
                    new org.kanger.exception.StorageLifecycleException(
                            org.kanger.enums.StorageLifecycleErrorCode.STORAGE_DELETE_INCOMPLETE,
                            "Database deletion was incomplete "
                                    + storageName.replace(Enums.FILE_SEPARATOR, "."));
            failure.addSuppressed(probeFailure);
            throw failure;
        }

        mind = mind.removeStorage(storageName);
        user.setCurrentMind(mind);
        try {
            Watchdog.log(user, "Database " + requested + " deleted");
        } catch (Exception logFailure) {
            Watchdog.warn("Unable to record storage deletion event");
        }
        return ok("Database " + requested + " dropped");
    }

    private JSONObject requireRootTransaction(IUser user, String operation) {
        IMind mind = user.getCurrentMind();
        if (mind != null && mind.getTransactionLevel() > 0) {
            return error("transaction_open",
                    "Operation " + operation
                            + " requires transaction level 0; commit or rollback first");
        }
        return null;
    }

    private JSONObject storageInfo(IMind mind) throws Exception {
        if (!mind.isStorageUsed()) {
            return error("storage_not_used", "No database used");
        }
        return new JSONObject()
                .put("result", "OK")
                .put("name", mind.getStorageName())
                .put("rules", mind.getTop().getRules().size())
                .put("predicates", mind.getTop().getPredicates().size())
                .put("dictionary", mind.getTerms().size())
                .put("udf", mind.getTop().getLibrary().size())
                .put("description", "Database used: "
                        + mind.getStorageName().replace(Enums.FILE_SEPARATOR, ".")
                        + ", Rules: " + mind.getTop().getRules().size()
                        + ", Predicates: " + mind.getTop().getPredicates().size()
                        + ", Dictionary: " + mind.getTerms().size()
                        + ", UDF: " + mind.getTop().getLibrary().size());
    }

    private JSONObject decorate(JSONObject result, IUser user) {
        IMind mind = user.getCurrentMind();
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

    private Path sourcePath(IUser user, String fileName) {
        return Paths.get(user.getSourceDir()).resolve(fileName).normalize();
    }

    private String canonicalStorageName(String displayName) {
        return displayName.replace(".", Enums.FILE_SEPARATOR);
    }

    private Path storagePath(IUser user, String storageName) {
        return Paths.get(user.getDatabaseDir()).resolve(storageName).normalize();
    }

    private boolean storageArtifactsExist(IUser user, String storageName)
            throws IOException {
        Path base = storagePath(user, storageName);
        for (String suffix : GENERATION_SUFFIXES) {
            if (Files.exists(Paths.get(base.toString() + suffix))) {
                return true;
            }
        }
        Path directory = base.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        String prefix = base.getFileName().toString() + ".wal.";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)
                        && entry.getFileName().toString().startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void deleteStorageArtifacts(IUser user, String storageName)
            throws IOException {
        Path base = storagePath(user, storageName);
        for (String suffix : GENERATION_SUFFIXES) {
            Files.deleteIfExists(Paths.get(base.toString() + suffix));
        }
        Path directory = base.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        String prefix = base.getFileName().toString() + ".wal.";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)
                        && entry.getFileName().toString().startsWith(prefix)) {
                    Files.deleteIfExists(entry);
                }
            }
        }
    }

    private static final class CompileProbe {
        private final boolean accepted;
        private final String description;

        private CompileProbe(boolean accepted, String description) {
            this.accepted = accepted;
            this.description = description;
        }
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
