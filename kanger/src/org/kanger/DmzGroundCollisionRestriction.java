/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Guarded detached typed no-good projection. Never edits Q or enforces a native cut. */
final class DmzGroundCollisionRestriction {
    static final class Result {
        final boolean eligible,truncated;
        final boolean complete=false;
        final DmzSourcedProofGraph graph;
        final int removed;
        final List<DmzProofWitnesses.Witness> retained;
        private Result(boolean eligible,boolean truncated,DmzSourcedProofGraph graph,int removed,List<DmzProofWitnesses.Witness> retained) {
            this.eligible=eligible;this.truncated=truncated;this.graph=graph;this.removed=removed;
            this.retained=Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.Witness>(retained));
        }
    }
    private final DmzGroundProofCoverage coverage;
    private DmzGroundCollisionRestriction(TerminalSupportCapture capture,Mind target,int budget) throws Exception {
        coverage=DmzGroundProofCoverage.beforeOperation(capture,target,budget);
    }
    static DmzGroundCollisionRestriction beforeOperation(TerminalSupportCapture capture,Mind target,int budget) throws Exception {
        return new DmzGroundCollisionRestriction(capture,target,budget);
    }
    Result project(DmzProvisionalCollisionProof proof,DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        if (budget<1 || budget>10000) throw new IllegalArgumentException("Bounded projection required");
        boolean member=false;
        if (proof!=null) for (DmzWitnessConflicts.Combination pair:proof.combinations)
            member |= selected==pair.left || selected==pair.right;
        if (!member || selected==null || selected.graph!=proof.graph) return refused(false);
        DmzGroundProofCoverage.Result audit=coverage.audit(proof,target);
        if (!audit.covered) return refused(audit.truncated);
        DmzProofWitnesses.NoGood noGood;
        try {noGood=DmzProofWitnesses.NoGood.fromTyped(selected);}
        catch(IllegalArgumentException unsupported) {return refused(false);}
        DmzCurrentGroundProofInventory.Proofs expected=coverage.expectedProofs(proof.graph.observed.nodes.get(selected.node).ground,budget);
        if (expected.truncated) return refused(true);
        boolean recognized=false;
        for (DmzProofWitnesses.Witness witness:expected.witnesses)
            if (witness.step>=0 && witness.source.authority==DmzReplayProvenance.Authority.EXTERNAL
                    && DmzProofWitnesses.NoGood.fromTyped(witness).matches(selected.graph,selected.node,selected.step,selected.source,selected.premises))
                {recognized=true;break;}
        if (!recognized) return refused(false);
        DmzProofWitnesses.Result before=DmzProofWitnesses.enumerate(proof.graph,selected.node,budget);
        if (before.truncated || before.created>=budget) return refused(true);
        DmzSourcedProofGraph branch=proof.graph.restrict(Collections.singletonList(noGood));
        DmzProofWitnesses.Result after=DmzProofWitnesses.enumerate(branch,selected.node,budget-before.created);
        if (after.truncated) return refused(true);
        int removed=before.witnesses.size()-after.witnesses.size();
        if (removed!=1) return refused(false);
        return new Result(true,false,branch,removed,after.witnesses);
    }
    private static Result refused(boolean truncated) {
        return new Result(false,truncated,null,0,Collections.<DmzProofWitnesses.Witness>emptyList());
    }
}
