/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.List;
import org.kanger.interfaces.IContextResults;

/** Single-use pre-input boundary for explicitly sourced acceptance.
 * Checks observed lineage and source membership, never general inference coverage.
 */
final class DmzAcceptanceProofGuard {
    private final TerminalSupportCapture capture;
    private final DmzReplayProvenance journal;
    private final Mind target;
    private final TerminalSupportCapture.Checkpoint before;
    private final DmzReplayProvenance.SourceCheckpoint baselineSources;
    private final DmzUnaryStateDelta delta;
    private TerminalSupportCapture.Checkpoint after;
    private DmzReplayProvenance.SourceCheckpoint afterSources;
    private final List<DmzReplayProvenance.Binding> incoming = new ArrayList<DmzReplayProvenance.Binding>();
    private boolean used, accepted;

    private DmzAcceptanceProofGuard(TerminalSupportCapture capture, DmzReplayProvenance journal,
            Mind target) throws Exception {
        this.capture = capture; this.journal = journal; this.target = target;
        before = capture.baselineCheckpoint(target);
        baselineSources = DmzReplayProvenance.sourceCheckpoint(target);
        delta = DmzUnaryStateDelta.beforeInput(target, 10000);
        // Also verifies that this journal owns the active source boundary.
        journal.requireActiveJournal();
    }

    static DmzAcceptanceProofGuard beforeInput(TerminalSupportCapture capture,
            DmzReplayProvenance journal, Mind target) throws Exception {
        return new DmzAcceptanceProofGuard(capture, journal, target);
    }

    Boolean accept(IContextResults.Revision source, long sourceRule,
            DmzReplayProvenance.Authority authority, String statement) throws Exception {
        if (used) throw new IllegalStateException("Acceptance boundary already consumed");
        used = true;
        journal.requireActiveJournal();
        if (!capture.isCurrent(before, target) || !baselineSources.isCurrent(target))
            throw new IllegalStateException("Acceptance baseline changed");
        List<DmzReplayProvenance.Binding> prior = journal.snapshot();
        Boolean result = DmzReplayProvenance.acceptRule(target, source, sourceRule, authority, statement);
        after = capture.baselineCheckpoint(target);
        afterSources = DmzReplayProvenance.sourceCheckpoint(target);
        for (DmzReplayProvenance.Binding binding : journal.snapshot()) {
            if (prior.contains(binding)) continue;
            if (!binding.context.equals(source.getContextId()) || binding.revision != source.getRevision()
                    || binding.sourceRule != sourceRule || binding.authority != authority) return result;
            incoming.add(binding);
        }
        accepted = Boolean.TRUE.equals(result) && incoming.size() == 1 && afterSources.contains(incoming.get(0));
        return result;
    }

    boolean isCurrent(DmzStoredProof proof, DmzProofWitnesses.Witness witness, Mind candidate) throws Exception {
        if (!accepted || candidate != target || proof == null || witness == null
                || !capture.isCurrent(after, target) || !afterSources.isCurrent(target)
                || !capture.followsStored(before, after, proof.stored) || !contains(witness)) return false;
        return DmzStoredProofGuard.atCurrentState(capture, target, proof, witness).isCurrent(target);
    }

    private boolean contains(DmzProofWitnesses.Witness witness) {
        if (!baselineSources.contains(witness.source) && !incoming.contains(witness.source)) return false;
        for (DmzProofWitnesses.Witness premise : witness.premises) if (!contains(premise)) return false;
        return true;
    }

    DmzUnaryStateDelta.Result auditDelta(DmzStoredProof proof,
            DmzProofWitnesses.Witness witness, Mind candidate) throws Exception {
        if (!isCurrent(proof, witness, candidate)) return new DmzUnaryStateDelta.Result(false, false,
                java.util.Collections.singletonList("input-proof-boundary-not-current"));
        return delta.audit(candidate, incoming.get(0).nativeRule);
    }

    DmzUnaryStateDelta.Result auditRoutes(DmzStoredProof proof,
            DmzProofWitnesses.Witness witness, Mind candidate) throws Exception {
        if (!isCurrent(proof, witness, candidate)) return new DmzUnaryStateDelta.Result(false, false,
                java.util.Collections.singletonList("input-proof-boundary-not-current"));
        return delta.auditRoutes(candidate, incoming.get(0), proof.graph, proof.root);
    }
}
