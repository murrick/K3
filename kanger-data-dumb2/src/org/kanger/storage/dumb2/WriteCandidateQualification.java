/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.ContextQualification;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/**
 * M3.8 semantic gate for one fully materialized but unpublished source
 * revision candidate.
 */
final class WriteCandidateQualification {

    private WriteCandidateQualification() {
    }

    static Result qualify(
            ContextCandidate candidate,
            ConnectionVector currentConnections)
            throws Exception {
        if (candidate == null
                || currentConnections == null) {
            throw new NullPointerException();
        }

        ContextQualification local =
                PairQualification.qualifyLocalState(candidate);
        if (!local.isValid()) {
            throw conflict(
                    "Candidate Context is locally inconsistent: "
                            + candidate.getRef(),
                    local.getCollisions());
        }

        ConnectionVector refreshed =
                ConnectionVector.empty();
        for (ContextConnection connection
                : currentConnections.getConnections()) {
            PairQualification.Result pair =
                    PairQualification.qualify(
                            candidate,
                            connection.getTargetLocation(),
                            connection.getTarget().getRevision());

            if (!connection.getTarget().equals(
                    pair.getRight())) {
                throw conflict(
                        "Pinned target identity changed during candidate qualification: expected "
                                + connection.getTarget()
                                + " found " + pair.getRight());
            }
            if (!pair.isCompatible()
                    || pair.getCertificate() == null) {
                throw conflict(
                        "Candidate Context pair is not compatible: "
                                + candidate.getRef()
                                + " / "
                                + connection.getTarget(),
                        pair.getCollisions());
            }

            refreshed = refreshed.with(
                    new ContextConnection(
                            connection.getTargetLocation(),
                            connection.getTarget(),
                            pair.getCertificate()));
        }

        PairQualification.CompositionQualification composition =
                PairQualification.qualifyCompositionState(
                        candidate, currentConnections);
        if (!composition.isValid()) {
            throw conflict(
                    "Candidate Context fails X-anchored multi-context composition qualification: "
                            + candidate.getRef(),
                    composition.getCollisions());
        }

        return new Result(
                candidate.getRef(),
                refreshed);
    }

    private static StorageLifecycleException conflict(
            String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                message);
    }

    private static StorageLifecycleException conflict(
            String message,
            java.util.List<ContextQualification.CollisionWitness> collisions) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                message,
                collisions);
    }

    static final class Result {

        private final RevisionRef candidate;
        private final ConnectionVector connections;

        private Result(
                RevisionRef candidate,
                ConnectionVector connections) {
            this.candidate = candidate;
            this.connections = connections;
        }

        RevisionRef getCandidate() {
            return candidate;
        }

        ConnectionVector getConnections() {
            return connections;
        }
    }
}
