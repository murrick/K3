/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDemand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Minimal detached evidence scoped to a blocked invocation at one exact target. */
final class FrontierRequest {
    private final FrontierInvocation invocation;
    private final RevisionRef target;
    private final List<SuppliedEvidence> evidence;
    private final EvidenceFingerprint fingerprint;

    private FrontierRequest(FrontierInvocation invocation, RevisionRef target,
                            List<SuppliedEvidence> evidence) {
        if (invocation == null) {
            throw new NullPointerException("invocation");
        }
        this.invocation = invocation;
        this.target = target;
        this.evidence = Collections.unmodifiableList(new ArrayList<SuppliedEvidence>(evidence));
        this.fingerprint = new EvidenceFingerprint(evidence);
    }

    static FrontierRequest initial(FrontierInvocation invocation) {
        return new FrontierRequest(invocation, null, Collections.<SuppliedEvidence>emptyList());
    }

    static FrontierRequest continueWith(FrontierAnswer blocked, List<SuppliedEvidence> proven) {
        if (blocked.getTruth() != FrontierAnswer.Truth.NULL) {
            throw new IllegalArgumentException("Only a blocked target invocation accepts continuation evidence");
        }
        // Reproof also needs previously supplied premises; retain only this
        // invocation's already filtered history, never a query-wide X layer.
        List<SuppliedEvidence> relevant = new ArrayList<SuppliedEvidence>(
                blocked.getRequest().getEvidence());
        for (SuppliedEvidence fact : proven) {
            for (FrontierDemand demand : blocked.getUnresolvedFrontiers()) {
                if (fact.matches(demand.getQuery())
                        && demand.getParentProjection().project(fact.getArguments()) != null) {
                    SuppliedEvidence.add(relevant, fact);
                    break;
                }
            }
        }
        return new FrontierRequest(blocked.getInvocation(), blocked.getSource(), relevant);
    }

    FrontierInvocation getInvocation() {
        return invocation;
    }

    List<SuppliedEvidence> getEvidence() {
        return evidence;
    }

    EvidenceFingerprint getEvidenceFingerprint() {
        return fingerprint;
    }

    FrontierExecutionState executionState(RevisionRef target) {
        if (this.target != null && !this.target.equals(target)) {
            throw new IllegalArgumentException("Continuation request belongs to a different exact target");
        }
        return new FrontierExecutionState(invocation.getFrontier(), target, fingerprint);
    }
}
