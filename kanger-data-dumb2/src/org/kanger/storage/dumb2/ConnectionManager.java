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

    static ContextConnection connect(
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

        /*
         * Re-read the source identity before publication. If CURRENT advanced
         * while qualification was running, do not publish a stale certificate.
         */
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

            ConnectionVector vector = ConnectionStore.read(
                    sourceLocation,
                    source);
            ContextConnection connection =
                    new ContextConnection(
                            targetLocation,
                            target,
                            qualification.getCertificate());
            ConnectionStore.write(
                    sourceLocation,
                    source,
                    vector.with(connection));
            return connection;
        } finally {
            currentSource.close();
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
}
