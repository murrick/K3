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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only IData adapter for one DUMB2 Context path.
 *
 * <p>Each {@link #use(String)} is an operation/runtime attachment boundary:
 * it selects the then-current published revision and keeps that immutable
 * Context snapshot until {@link #close()}. Reopening later may therefore see a
 * newer revision, while an already attached Mind cannot change its semantic
 * basis underneath a running operation.</p>
 *
 * <p>This adapter deliberately reuses the existing User/Mind factory
 * publication path. Persistent records and local IDs remain those of the
 * source Context; only transient ID allocation used by runtime query
 * compilation is local to the attachment.</p>
 */
final class ContextSnapshotData implements IData {

    private final Path location;
    private final String logicalName;
    private final Long exactRevision;
    private final ContextCandidate candidate;
    private final Map<String, SnapshotRuntimeBase> bases =
            new LinkedHashMap<String, SnapshotRuntimeBase>();

    private IUser user;
    private ContextSnapshot snapshot;

    ContextSnapshotData(Path location, String logicalName) {
        this(location, logicalName, null, null);
    }

    ContextSnapshotData(Path location,
                        String logicalName,
                        Long exactRevision) {
        this(location, logicalName, exactRevision, null);
    }

    ContextSnapshotData(ContextCandidate candidate,
                        String logicalName) {
        this(candidate.getLocation(),
                logicalName,
                null,
                candidate);
    }

    private ContextSnapshotData(Path location,
                                String logicalName,
                                Long exactRevision,
                                ContextCandidate candidate) {
        if (location == null) {
            throw new NullPointerException("location");
        }
        if (logicalName == null || logicalName.trim().isEmpty()) {
            throw new IllegalArgumentException("logicalName must not be blank");
        }
        if (exactRevision != null
                && exactRevision.longValue()
                < RevisionStore.INITIAL_REVISION) {
            throw new IllegalArgumentException(
                    "exactRevision must be non-negative");
        }
        if (exactRevision != null
                && candidate != null) {
            throw new IllegalArgumentException(
                    "exact published revision and candidate are mutually exclusive");
        }
        this.location = location;
        this.logicalName = logicalName;
        this.exactRevision = exactRevision;
        this.candidate = candidate;
    }

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
        if (!logicalName.equals(name)) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                    "DUMB2 Context snapshot was not found: " + name);
        }
        if (!isClosed()) {
            close();
        }

        ContextSnapshot acquired;
        if (candidate != null) {
            acquired = candidate.openSnapshot();
        } else {
            acquired = exactRevision == null
                    ? ContextSnapshot.open(location)
                    : ContextSnapshot.open(
                            location,
                            exactRevision.longValue());
        }
        snapshot = acquired;
        bases.clear();
    }

    @Override
    public synchronized void close() {
        for (SnapshotRuntimeBase base : bases.values()) {
            base.close();
        }
        bases.clear();
        if (snapshot != null) {
            snapshot.close();
            snapshot = null;
        }
    }

    @Override
    public synchronized void flush() {
        requireOpen();
        /*
         * Root Mind settlement always reaches IData.flush(). The snapshot
         * runtime has no durable delta, so a successful read-only operation has
         * nothing to publish. Physical mutation is rejected by its bases.
         */
    }

    @Override
    public void remove(String name) {
        throw readOnly("remove Context");
    }

    @Override
    public synchronized boolean exists(String name) {
        if (!logicalName.equals(name)) {
            return false;
        }
        if (candidate != null) {
            return Files.isDirectory(
                    candidate.getGeneration());
        }
        return Files.isRegularFile(ContextStore.contextPath(location))
                && Files.isRegularFile(ContextStore.revisionPath(location));
    }

    @Override
    public void reindex(IReactor<String> reactor, IMind mind) {
        throw readOnly("reindex Context");
    }

    @Override
    public synchronized boolean isClosed() {
        return snapshot == null || snapshot.isClosed();
    }

    @Override
    public synchronized String getStorageName() {
        return isClosed() ? "" : logicalName;
    }

    @Override
    public synchronized IBase getBase(String schema) throws Exception {
        requireOpen();
        SnapshotRuntimeBase base = bases.get(schema);
        if (base == null) {
            base = new SnapshotRuntimeBase(snapshot.getBase(schema));
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
        return "DUMB 2.0 immutable Context snapshot";
    }

    @Override
    public StorageTelemetry telemetry() {
        return StorageTelemetry.unavailable();
    }

    @Override
    public synchronized Collection<String> list() {
        ArrayList<String> result = new ArrayList<String>();
        if (Files.isRegularFile(ContextStore.contextPath(location))
                && Files.isRegularFile(ContextStore.revisionPath(location))) {
            result.add(logicalName);
        }
        return result;
    }

    synchronized UUID getContextId() {
        requireOpen();
        return snapshot.getContextId();
    }

    synchronized long getRevision() {
        requireOpen();
        return snapshot.getRevision();
    }

    private void requireInitialized() {
        if (user == null) {
            throw new IllegalStateException(
                    "DUMB2 Context snapshot runtime is not initialized");
        }
    }

    private void requireOpen() {
        if (isClosed()) {
            throw new IllegalStateException(
                    "DUMB2 Context snapshot runtime is closed: " + logicalName);
        }
    }

    private UnsupportedOperationException readOnly(String operation) {
        return new UnsupportedOperationException(
                "Cannot " + operation + " through immutable DUMB2 Context snapshot "
                        + logicalName);
    }
}
