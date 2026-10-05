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
     * Package-level durable publication helper for storage qualification.
     * Runtime/operator code must use {@link #qualifyConnect(Path, Path)} and
     * keep the result in its session-local working topology until an authorized
     * publication boundary is invoked.
     */
    static ContextConnection connect(
            Path sourceLocation,
            Path targetLocation) throws Exception {
        ContextStore source =
                ContextStore.open(sourceLocation);
        try {
            RevisionRef sourceRef =
                    new RevisionRef(
                            source.getContextId(),
                            source.getRevision());
            ConnectionVector current =
                    ConnectionStore.read(
                            sourceLocation,
                            sourceRef);
            ContextConnection proposed =
                    qualifyConnect(
                            sourceLocation,
                            targetLocation);
            java.util.UUID targetContextId =
                    proposed.getTarget()
                            .getContextId();

            long published =
                    source.publishTopology(
                            current.with(proposed),
                            "");
            ConnectionVector result =
                    ConnectionStore.read(
                            sourceLocation,
                            new RevisionRef(
                                    source.getContextId(),
                                    published));
            ContextConnection connection =
                    result.find(targetContextId);
            if (connection == null) {
                throw new IllegalStateException(
                        "Published topology lost target "
                                + targetContextId);
            }
            return connection;
        } finally {
            source.close();
        }
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
     * Package-level durable repin publication helper used by storage
     * qualification. A successful durable switch creates a new source Revision.
     */
    static ContextConnection switchRevision(
            Path sourceLocation,
            java.util.UUID targetContextId,
            long targetRevision) throws Exception {
        ContextStore source =
                ContextStore.open(sourceLocation);
        try {
            RevisionRef sourceRef =
                    new RevisionRef(
                            source.getContextId(),
                            source.getRevision());
            ConnectionVector original =
                    ConnectionStore.read(
                            sourceLocation,
                            sourceRef);
            ContextConnection replacement =
                    qualifySwitchRevision(
                            sourceLocation,
                            original,
                            targetContextId,
                            targetRevision);

            long published =
                    source.publishTopology(
                            original.with(replacement),
                            "");
            ContextConnection result =
                    ConnectionStore.read(
                            sourceLocation,
                            new RevisionRef(
                                    source.getContextId(),
                                    published))
                            .find(targetContextId);
            if (result == null) {
                throw new IllegalStateException(
                        "Published topology lost target "
                                + targetContextId);
            }
            return result;
        } finally {
            source.close();
        }
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
        ContextStore source =
                ContextStore.open(sourceLocation);
        try {
            RevisionRef sourceRef =
                    new RevisionRef(
                            source.getContextId(),
                            source.getRevision());
            ConnectionVector current =
                    ConnectionStore.read(
                            sourceLocation,
                            sourceRef);
            source.publishTopology(
                    current.without(targetContextId),
                    "");
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
