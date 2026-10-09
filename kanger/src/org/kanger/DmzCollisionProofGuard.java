/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

/** Rechecks a bounded rejected-operation proof against its pre-operation state.
 * This guard never certifies independent coverage or authorizes branch restrictions.
 */
final class DmzCollisionProofGuard {
    private final TerminalSupportCapture capture;
    private final TerminalSupportCapture.Checkpoint state;
    private final DmzReplayProvenance.SourceCheckpoint sources;
    private DmzCollisionProofGuard(TerminalSupportCapture capture, Mind target) throws Exception {
        this.capture = capture;
        state = capture.baselineCheckpoint(target);
        sources = DmzReplayProvenance.sourceCheckpoint(target);
    }
    static DmzCollisionProofGuard beforeOperation(TerminalSupportCapture capture, Mind target) throws Exception {
        return new DmzCollisionProofGuard(capture, target);
    }
    boolean isCurrent(DmzProvisionalCollisionProof proof, Mind target) throws Exception {
        if (proof == null || proof.truncated || !proof.rootsAvailable || proof.combinations.isEmpty()
                || !capture.isCurrent(state, target) || !sources.isCurrent(target)
                || !capture.follows(state, proof.event.observations)) return false;
        for (DmzWitnessConflicts.Combination pair : proof.combinations)
            if (!contains(proof, pair.left) || !contains(proof, pair.right)) return false;
        return true;
    }
    private boolean contains(DmzProvisionalCollisionProof proof, DmzProofWitnesses.Witness witness) {
        if (witness.graph != proof.graph || !sources.contains(witness.source)) return false;
        for (DmzProofWitnesses.Witness premise : witness.premises)
            if (!contains(proof, premise)) return false;
        return true;
    }
}
