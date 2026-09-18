/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.User;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;
import org.kanger.interfaces.internal.StorageTelemetry;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime-facing IData adapter for one autonomous DUMB 2.0 Context.
 *
 * <p>This class intentionally is not registered through RuntimeBootstrap yet.
 * M1 qualification attaches it explicitly to a {@link User}; production
 * selection therefore remains on the existing DUMB provider.</p>
 *
 * <p>The adapter owns only physical Context lifecycle. Semantic transaction
 * layering, commit/rollback and factory publication remain in User/Mind.
 * Root settlement eventually reaches {@link #flush()}, where
 * {@link ContextStore} publishes one storage-wide revision.</p>
 */
public final class DB implements IData {

    private IUser user;
    private ContextStore context;
    private String storageName = "";
    private final Map<String, IBase> bases =
            new LinkedHashMap<String, IBase>();

    @Override
    public synchronized void init(IUser user) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        this.user = user;
        ((User) user).setData(this);
    }

    @Override
    public synchronized void use(String name) throws Exception {
        requireInitialized();
        if (name == null || name.trim().isEmpty()) {
            throw new CommandErrorException("DB name expected");
        }
        if (!isClosed()) {
            close();
        }

        Path location = location(name);
        boolean hasContext = Files.exists(ContextStore.contextPath(location));
        boolean hasRevision = Files.exists(ContextStore.revisionPath(location));
        boolean hasState = Files.exists(ContextStore.stateRoot(location));

        ContextStore acquired;
        if (!hasContext && !hasRevision && !hasState) {
            acquired = ContextStore.create(location);
        } else {
            acquired = ContextStore.open(location);
        }

        context = acquired;
        storageName = name;
        bases.clear();
    }

    @Override
    public synchronized void close() throws Exception {
        if (context == null) {
            bases.clear();
            storageName = "";
            return;
        }

        /*
         * ContextStore.close() keeps the Context open if flush fails. Mirror
         * that failure atomicity here: only clear the IData generation after
         * the physical owner has actually closed.
         */
        context.close();
        bases.clear();
        context = null;
        storageName = "";
    }

    @Override
    public synchronized void flush() throws Exception {
        requireOpen();
        context.flush();
    }

    @Override
    public synchronized void remove(String name) throws Exception {
        requireInitialized();

        String target;
        if (name == null || name.isEmpty()) {
            if (isClosed()) {
                throw new CommandErrorException("DB name expected");
            }
            target = storageName;
        } else {
            target = name;
        }

        if (!isClosed() && storageName.equals(target)) {
            close();
        }

        Path location = location(target);
        boolean existed = storageArtifactsExist(location);
        if (!existed) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                    "DUMB2 Context was not found: " + target);
        }

        IOException failure = null;
        try {
            deleteRecursively(ContextStore.stateRoot(location));
        } catch (IOException error) {
            failure = error;
        }
        try {
            Files.deleteIfExists(ContextStore.revisionPath(location));
        } catch (IOException error) {
            failure = accumulate(failure, error);
        }
        try {
            Files.deleteIfExists(ContextStore.contextPath(location));
        } catch (IOException error) {
            failure = accumulate(failure, error);
        }

        if (failure != null || storageArtifactsExist(location)) {
            StorageLifecycleException incomplete = new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_DELETE_INCOMPLETE,
                    "DUMB2 Context deletion was incomplete: " + target);
            if (failure != null) {
                incomplete.addSuppressed(failure);
            }
            throw incomplete;
        }
        pruneEmptyParents(location);
    }

    @Override
    public synchronized boolean exists(String name) throws Exception {
        requireInitialized();
        if (name == null || name.isEmpty()) {
            return false;
        }
        return storageArtifactsExist(location(name));
    }

    @Override
    public void reindex(IReactor<String> reactor, IMind mind) {
        throw new UnsupportedOperationException(
                "DUMB2 reindex is outside M1 base lifecycle");
    }

    @Override
    public synchronized boolean isClosed() {
        return context == null || context.isClosed();
    }

    @Override
    public synchronized String getStorageName() {
        return isClosed() ? "" : storageName;
    }

    @Override
    public synchronized IBase getBase(String schema) throws Exception {
        requireOpen();
        IBase base = bases.get(schema);
        if (base == null) {
            base = context.getBase(schema);
            bases.put(schema, base);
        }
        return base;
    }

    @Override
    public synchronized IBase connect(String schema) {
        return isClosed() ? null : bases.get(schema);
    }

    @Override
    public String getDescription() {
        return "DUMB 2.0 data model";
    }

    @Override
    public StorageTelemetry telemetry() {
        return StorageTelemetry.unavailable();
    }

    @Override
    public synchronized Collection<String> list() {
        if (user == null) {
            return new ArrayList<String>();
        }

        Path root = databaseRoot();
        ArrayList<String> result = new ArrayList<String>();
        if (!Files.isDirectory(root)) {
            return result;
        }

        try {
            collectContexts(root, root, result);
        } catch (IOException ignored) {
            /*
             * IData.list() has no checked failure surface. A namespace that
             * cannot be enumerated truthfully is exposed as the successfully
             * observed subset; exists()/use() retain checked diagnostics.
             */
        }
        return result;
    }

    synchronized long getRevision() {
        return context == null ? -1L : context.getRevision();
    }

    synchronized java.util.UUID getContextId() {
        return context == null ? null : context.getContextId();
    }

    private void collectContexts(Path root,
                                 Path directory,
                                 Collection<String> result) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    if (!child.getFileName().toString().endsWith(ContextStore.STATE_SUFFIX)) {
                        collectContexts(root, child, result);
                    }
                    continue;
                }

                String file = child.getFileName().toString();
                if (!file.endsWith(ContextStore.CONTEXT_SUFFIX)) {
                    continue;
                }

                String stem = file.substring(
                        0, file.length() - ContextStore.CONTEXT_SUFFIX.length());
                Path logical = child.resolveSibling(stem);
                Path relative = root.relativize(logical);
                result.add(relative.toString());
            }
        }
    }

    private boolean storageArtifactsExist(Path location) {
        return Files.exists(ContextStore.contextPath(location))
                || Files.exists(ContextStore.revisionPath(location))
                || Files.exists(ContextStore.stateRoot(location));
    }

    private Path location(String name) {
        return databaseRoot().resolve(name);
    }

    private Path databaseRoot() {
        String directory = user == null ? "" : user.getDatabaseDir();
        if (directory == null || directory.isEmpty()) {
            return Paths.get(".");
        }
        return Paths.get(directory);
    }

    private void requireInitialized() {
        if (user == null) {
            throw new IllegalStateException("DUMB2 DB is not initialized");
        }
    }

    private void requireOpen() {
        requireInitialized();
        if (isClosed()) {
            throw new IllegalStateException("DUMB2 Context is not open");
        }
    }

    private void pruneEmptyParents(Path location) {
        Path root = databaseRoot().toAbsolutePath().normalize();
        Path directory = location.toAbsolutePath().normalize().getParent();
        while (directory != null && !directory.equals(root)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                if (stream.iterator().hasNext()) {
                    return;
                }
            } catch (IOException failure) {
                return;
            }
            Path parent = directory.getParent();
            try {
                Files.deleteIfExists(directory);
            } catch (IOException failure) {
                return;
            }
            directory = parent;
        }
    }

    private static IOException accumulate(IOException primary, IOException next) {
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
