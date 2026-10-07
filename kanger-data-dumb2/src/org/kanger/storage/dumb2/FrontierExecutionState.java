/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;

/** Exact target + alpha-equivalent frontier + relevant semantic evidence. */
final class FrontierExecutionState {
    private final FrontierDomain frontier;
    private final RevisionRef target;
    private final EvidenceFingerprint evidence;

    FrontierExecutionState(FrontierDomain frontier, RevisionRef target, EvidenceFingerprint evidence) {
        this.frontier = frontier;
        this.target = target;
        this.evidence = evidence;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * frontier.semanticQueryHash() + target.hashCode()) + evidence.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FrontierExecutionState)) {
            return false;
        }
        FrontierExecutionState state = (FrontierExecutionState) other;
        return target.equals(state.target) && frontier.sameSemanticQuery(state.frontier)
                && evidence.equals(state.evidence);
    }
}
