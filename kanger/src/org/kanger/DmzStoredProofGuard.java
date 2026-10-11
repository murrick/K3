/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Current-state ticket for one selected historical stored witness.
 * This is not a pre-inference baseline or a completeness certificate.
 */
final class DmzStoredProofGuard {
    private final TerminalSupportCapture capture;
    private final TerminalSupportCapture.Checkpoint state;
    private final DmzReplayProvenance.SourceCheckpoint sources;
    private final DmzStoredProof proof;
    private final DmzProofWitnesses.Witness witness;
    private final boolean eligible;

    private DmzStoredProofGuard(TerminalSupportCapture capture, Mind target,
            DmzStoredProof proof, DmzProofWitnesses.Witness witness) throws Exception {
        this.capture = capture; this.proof = proof; this.witness = witness;
        state = capture.baselineCheckpoint(target);
        sources = DmzReplayProvenance.sourceCheckpoint(target);
        eligible = proof != null && !proof.truncated && witness != null && proof.witnesses.contains(witness)
                && witness.graph == proof.graph && witness.node == proof.root
                && proof.outcome == TerminalSupportCapture.Outcome.COMMITTED
                && capture.storedOutcome(proof.stored) == TerminalSupportCapture.Outcome.COMMITTED
                && liveRoot(target) && contains(witness, target);
    }

    static DmzStoredProofGuard atCurrentState(TerminalSupportCapture capture, Mind target,
            DmzStoredProof proof, DmzProofWitnesses.Witness witness) throws Exception {
        return new DmzStoredProofGuard(capture, target, proof, witness);
    }

    boolean isCurrent(Mind target) throws Exception {
        return eligible && capture.isCurrent(state, target) && sources.isCurrent(target) && liveRoot(target);
    }

    private boolean contains(DmzProofWitnesses.Witness selected, Mind target) throws Exception {
        if (selected.graph != proof.graph || !sources.contains(selected.source)) return false;
        Rule live = liveRule(target, selected.source.nativeRule);
        if (live == null) return false;
        if (selected.step < 0) {
            TerminalSupportCapture.Ground expected = selected.graph.observed.nodes.get(selected.node).ground;
            if (!expected.equivalent(atomicGround(live, target))) return false;
        } else {
            String expected = selected.graph.observed.steps.get(selected.step).application.ruleOrigin;
            if (expected == null || !expected.equals(live.getOrigin())) return false;
        }
        for (DmzProofWitnesses.Witness premise : selected.premises) if (!contains(premise, target)) return false;
        return true;
    }

    private static Rule liveRule(Mind target, long id) {
        for (IRule candidate : target.getRules())
            if (candidate.getId() == id && !candidate.isDeleted(target) && !candidate.isQuery()) return (Rule) candidate;
        return null;
    }

    private static TerminalSupportCapture.Ground atomicGround(Rule rule, Mind target) throws Exception {
        if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) return null;
        Domain domain = rule.getDomain();
        return TerminalSupportCapture.Ground.capture(new Solve(domain.getPredicate(), domain.isAntc(),
                domain.getArguments().convertBase(target)), target);
    }

    private boolean liveRoot(Mind target) throws Exception {
        TerminalSupportCapture.Ground expected = proof.stored.causes.nodes.get(proof.stored.causes.root).ground;
        Rule rule = liveRule(target, proof.stored.nativeRule);
        return rule != null && target.getRules().isGenerated(rule) == proof.stored.generated
                && expected != null && expected.equivalent(atomicGround(rule, target));
    }
}
