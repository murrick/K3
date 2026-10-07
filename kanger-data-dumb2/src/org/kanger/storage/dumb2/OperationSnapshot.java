/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Version;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable M3 operation boundary: working Context plus exact pinned targets.
 */
final class OperationSnapshot implements AutoCloseable {

    private final Path sourceLocation;
    private final ContextSnapshot source;
    private final RevisionRef sourceRef;
    private final ConnectionVector connections;
    private final Map<UUID, ContextSnapshot> targets;
    private boolean closed;

    private OperationSnapshot(Path sourceLocation,
                              ContextSnapshot source,
                              ConnectionVector connections,
                              Map<UUID, ContextSnapshot> targets) {
        this.sourceLocation =
                sourceLocation.toAbsolutePath().normalize();
        this.source = source;
        this.sourceRef = new RevisionRef(
                source.getContextId(), source.getRevision());
        this.connections = connections;
        this.targets = targets;
    }

    static OperationSnapshot open(Path sourceLocation)
            throws Exception {
        ContextSnapshot source =
                ContextSnapshot.open(sourceLocation);
        try {
            RevisionRef sourceRef = new RevisionRef(
                    source.getContextId(), source.getRevision());
            return openSelected(
                    sourceLocation,
                    source,
                    ConnectionStore.read(
                            sourceLocation, sourceRef));
        } catch (Exception | Error failure) {
            source.close();
            throw failure;
        }
    }

    /**
     * Freezes one session-local working topology for the duration of an
     * operation. The supplied vector is never persisted here.
     */
    static OperationSnapshot open(
            Path sourceLocation,
            ConnectionVector connections) throws Exception {
        if (connections == null) {
            throw new NullPointerException("connections");
        }
        ContextSnapshot source =
                ContextSnapshot.open(sourceLocation);
        try {
            return openSelected(
                    sourceLocation,
                    source,
                    connections);
        } catch (Exception | Error failure) {
            source.close();
            throw failure;
        }
    }

    static OperationSnapshot open(Path sourceLocation, long revision, ConnectionVector connections) throws Exception {
        ContextSnapshot source=ContextSnapshot.open(sourceLocation,revision);
        try { return openSelected(sourceLocation,source,connections); }
        catch (Exception | Error failure) { source.close(); throw failure; }
    }

    private static OperationSnapshot openSelected(
            Path sourceLocation,
            ContextSnapshot source,
            ConnectionVector vector) throws Exception {
        Map<UUID, ContextSnapshot> targets =
                new LinkedHashMap<UUID, ContextSnapshot>();
        try {
            RevisionRef sourceRef = new RevisionRef(
                    source.getContextId(), source.getRevision());
            for (ContextConnection connection
                    : vector.getConnections()) {
                RevisionRef targetRef = connection.getTarget();
                if (!connection.getCertificate().matches(
                        sourceRef,
                        targetRef,
                        Version.CORE_VERSION_S)) {
                    throw new StorageLifecycleException(
                            StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                            "Compatibility certificate is stale for "
                                    + sourceRef + " / " + targetRef);
                }
                ContextSnapshot target = ContextSnapshot.open(
                        connection.getTargetLocation(),
                        targetRef.getRevision());
                if (!targetRef.getContextId()
                        .equals(target.getContextId())) {
                    target.close();
                    throw new StorageLifecycleException(
                            StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                            "Pinned Context identity mismatch for "
                                    + connection.getTargetLocation()
                                    + ": expected "
                                    + targetRef.getContextId()
                                    + ", found "
                                    + target.getContextId());
                }
                targets.put(targetRef.getContextId(), target);
            }
            return new OperationSnapshot(
                    sourceLocation, source, vector, targets);
        } catch (Exception failure) {
            for (ContextSnapshot target : targets.values()) {
                target.close();
            }
            throw failure;
        } catch (Error failure) {
            for (ContextSnapshot target : targets.values()) {
                target.close();
            }
            throw failure;
        }
    }

    RevisionRef getSourceRef() {
        requireOpen();
        return sourceRef;
    }

    Path getSourceLocation() {
        requireOpen();
        return sourceLocation;
    }

    ContextSnapshot getSource() {
        requireOpen();
        return source;
    }

    ConnectionVector getConnections() {
        requireOpen();
        return connections;
    }

    ContextSnapshot getTarget(UUID contextId) {
        requireOpen();
        return targets.get(contextId);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        for (ContextSnapshot target : targets.values()) {
            target.close();
        }
        source.close();
        closed = true;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "DUMB2 operation snapshot is closed");
        }
    }
}
