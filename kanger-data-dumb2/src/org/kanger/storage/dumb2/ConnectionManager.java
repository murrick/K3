/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.nio.file.Path;

/**
 * Qualification-gated lifecycle for directed Context connections.
 */
final class ConnectionManager {

    private ConnectionManager() {
    }

    /**
     * Legacy package-level publication helper retained for existing storage
     * regression fixtures. Runtime/operator code must use
     * {@link #qualifyConnect(Path, Path)} and keep the result in its working
     * topology candidate.
     */
    static ContextConnection connect(
            Path sourceLocation,
            Path targetLocation) throws Exception {
        ContextConnection connection =
                qualifyConnect(sourceLocation, targetLocation);

        ContextSnapshot sourceSnapshot =
                ContextSnapshot.open(sourceLocation);
        try {
            RevisionRef source = new RevisionRef(
                    sourceSnapshot.getContextId(),
                    sourceSnapshot.getRevision());
            ConnectionVector vector =
                    ConnectionStore.read(sourceLocation, source);
            ConnectionStore.write(
                    sourceLocation,
                    source,
                    vector.with(connection));
        } finally {
            sourceSnapshot.close();
        }
        return connection;
    }

    /**
     * Qualifies one proposed direct connection without changing durable
     * revision metadata.
     */
    static ContextConnection qualifyConnect(
            Path sourceLocation,
            Path targetLocation) throws Exception {
        RevisionRef source;
        RevisionRef target;

        ContextSnapshot sourceSnapshot =
                ContextSnapshot.open(sourceLocation);
        try {
            source = new RevisionRef(
                    sourceSnapshot.getContextId(),
                    sourceSnapshot.getRevision());
        } finally {
            sourceSnapshot.close();
        }

        ContextSnapshot targetSnapshot =
                ContextSnapshot.open(targetLocation);
        try {
            target = new RevisionRef(
                    targetSnapshot.getContextId(),
                    targetSnapshot.getRevision());
        } finally {
            targetSnapshot.close();
        }

        if (source.getContextId().equals(
                target.getContextId())) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                    "A Context cannot connect to itself: "
                            + source.getContextId());
        }

        PairQualification.Result qualification =
                PairQualification.qualify(
                        sourceLocation,
                        source.getRevision(),
                        targetLocation,
                        target.getRevision());
        if (!qualification.isCompatible()
                || qualification.getCertificate() == null) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                    "Context pair is not compatible: "
                            + source + " / " + target);
        }

        ContextSnapshot currentSource =
                ContextSnapshot.open(sourceLocation);
        try {
            RevisionRef now = new RevisionRef(
                    currentSource.getContextId(),
                    currentSource.getRevision());
            if (!source.equals(now)) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                        "Source Context advanced during pair qualification: "
                                + source + " -> " + now);
            }
            return new ContextConnection(
                    targetLocation,
                    target,
                    qualification.getCertificate());
        } finally {
            currentSource.close();
        }
    }

    /**
     * Legacy package-level publication helper retained for existing storage
     * regression fixtures. Runtime/operator code uses the working-vector
     * qualification overload below.
     */
    static ContextConnection switchRevision(
            Path sourceLocation,
            java.util.UUID targetContextId,
            long targetRevision) throws Exception {
        ContextSnapshot source =
                ContextSnapshot.open(sourceLocation);
        RevisionRef sourceRef;
        ConnectionVector original;
        try {
            sourceRef = new RevisionRef(
                    source.getContextId(),
                    source.getRevision());
            original = ConnectionStore.read(
                    sourceLocation, sourceRef);
        } finally {
            source.close();
        }

        ContextConnection replacement =
                qualifySwitchRevision(
                        sourceLocation,
                        original,
                        targetContextId,
                        targetRevision);
        ConnectionStore.write(
                sourceLocation,
                sourceRef,
                original.with(replacement));
        return replacement;
    }

    /**
     * Qualifies a deliberate repin against one session-local working vector
     * without rewriting the published vector of the source revision.
     */
    static ContextConnection qualifySwitchRevision(
            Path sourceLocation,
            ConnectionVector original,
            java.util.UUID targetContextId,
            long targetRevision) throws Exception {
        if (original == null || targetContextId == null) {
            throw new NullPointerException();
        }
        if (targetRevision < RevisionStore.INITIAL_REVISION) {
            throw new IllegalArgumentException(
                    "targetRevision must be non-negative");
        }

        RevisionRef sourceRef;
        ContextConnection existing;

        ContextSnapshot source =
                ContextSnapshot.open(sourceLocation);
        try {
            sourceRef = new RevisionRef(
                    source.getContextId(),
                    source.getRevision());
            existing = original.find(targetContextId);
            if (existing == null) {
                throw conflict(
                        "No direct Context connection exists for target "
                                + targetContextId
                                + " from " + sourceRef);
            }
        } finally {
            source.close();
        }

        RevisionRef requestedTarget;
        ContextSnapshot target =
                ContextSnapshot.open(
                        existing.getTargetLocation(),
                        targetRevision);
        try {
            requestedTarget = new RevisionRef(
                    target.getContextId(),
                    target.getRevision());
            if (!targetContextId.equals(
                    requestedTarget.getContextId())) {
                throw conflict(
                        "Target Context identity differs at existing locator: expected "
                                + targetContextId
                                + " found "
                                + requestedTarget.getContextId());
            }
        } finally {
            target.close();
        }

        if (requestedTarget.equals(
                existing.getTarget())) {
            return existing;
        }

        PairQualification.Result pair =
                PairQualification.qualify(
                        sourceLocation,
                        sourceRef.getRevision(),
                        existing.getTargetLocation(),
                        requestedTarget.getRevision());
        if (!sourceRef.equals(pair.getLeft())
                || !requestedTarget.equals(pair.getRight())
                || !pair.isCompatible()
                || pair.getCertificate() == null) {
            throw conflict(
                    "Requested Context revision is not compatible: "
                            + sourceRef + " / " + requestedTarget);
        }

        ContextConnection replacement =
                new ContextConnection(
                        existing.getTargetLocation(),
                        requestedTarget,
                        pair.getCertificate());
        ConnectionVector candidate =
                original.with(replacement);

        PairQualification.CompositionQualification originalComposition =
                PairQualification.qualifyCompositionState(
                        sourceLocation,
                        sourceRef.getRevision(),
                        original);
        PairQualification.CompositionQualification candidateComposition =
                PairQualification.qualifyCompositionState(
                        sourceLocation,
                        sourceRef.getRevision(),
                        candidate);
        if (candidateComposition
                .introducesNewCollisionComparedTo(
                        originalComposition)) {
            throw conflict(
                    "Requested Context revision introduces a new direct multi-context composition conflict: "
                            + requestedTarget);
        }

        ContextSnapshot current =
                ContextSnapshot.open(sourceLocation);
        try {
            RevisionRef now = new RevisionRef(
                    current.getContextId(),
                    current.getRevision());
            if (!sourceRef.equals(now)) {
                throw conflict(
                        "Source Context advanced during revision switch: "
                                + sourceRef + " -> " + now);
            }
            return replacement;
        } finally {
            current.close();
        }
    }

    static void disconnect(
            Path sourceLocation,
            java.util.UUID targetContextId) throws Exception {
        ContextSnapshot source =
                ContextSnapshot.open(sourceLocation);
        try {
            RevisionRef sourceRef = new RevisionRef(
                    source.getContextId(),
                    source.getRevision());
            ConnectionVector vector = ConnectionStore.read(
                    sourceLocation,
                    sourceRef);
            ConnectionStore.write(
                    sourceLocation,
                    sourceRef,
                    vector.without(targetContextId));
        } finally {
            source.close();
        }
    }

    private static StorageLifecycleException conflict(
            String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                message);
    }
}
