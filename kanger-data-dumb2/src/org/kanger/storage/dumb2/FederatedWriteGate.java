/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/**
 * Semantic M3.8 gate installed by the DUMB2 runtime for federated Contexts.
 */
final class FederatedWriteGate implements CandidateGate {

    @Override
    public void qualify(ContextCandidate candidate) throws Exception {
        ConnectionVector connections =
                ConnectionStore.read(
                        candidate.getLocation(),
                        candidate.getContextId());

        if (connections.isEmpty()) {
            return;
        }

        ContextSnapshot snapshot =
                candidate.openSnapshot();
        CandidateQualification.Result local =
                CandidateQualification.qualifyLocal(snapshot);
        if (!local.isValid()) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                    "DUMB2 federated candidate failed local qualification: "
                            + local.getCandidate());
        }
    }
}
