/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Checked ground binary resolution of an observed pair. No saturation or publication authority. */
public final class ContextGroundCompositionProof {
    public static final class Proof {
        public final Clause conclusion;
        public final Support support;
        public final CompositionCandidate observation;
        private Proof(Clause conclusion,Support support,CompositionCandidate observation) {
            this.conclusion = conclusion; this.support = support; this.observation = observation;
        }
    }
    public static final class Evidence {
        public final boolean proved;
        public final String reason;
        public final Frontier input;
        public final Snapshot target;
        public final List<Proof> proofs;
        private Evidence(boolean proved,String reason,Receipt receipt,List<Proof> proofs) {
            this.proved = proved; this.reason = reason;
            input = receipt == null ? null : receipt.input; target = receipt == null ? null : receipt.target;
            this.proofs = Collections.unmodifiableList(new ArrayList<>(proofs));
        }
    }
    private static void authenticate(Clause clause,List<Support> supports,Receipt receipt,Runnable work) {
        if (clause == null || !clause.variables.isEmpty() || clause.literals.size() < 2 || supports.isEmpty())
            throw new IllegalStateException("Whole ground compound branches required");
        for (Support support : supports) {
            boolean found = false;
            for (Product actual : receipt.localProofs) {
                work.run(); if (actual.proof.equals(support) && actual.clause.equivalent(clause)) { found = true; break; }
            }
            if (!found) throw new IllegalStateException("Composition whole-branch source/depth mismatch");
        }
    }
    private static void remainder(Clause clause,Fact pivot,List<Fact> result,Runnable work) {
        boolean removed = false;
        for (Atom literal : clause.literals) {
            work.run(); Fact fact = literal.fact();
            if (fact == null) throw new IllegalStateException("Ground composition cannot bind universal slots");
            if (!removed && fact.equivalent(pivot)) removed = true;
            else result.add(fact);
        }
        if (!removed) throw new IllegalStateException("Composition pivot absent from whole branch");
    }
    /** Exact receipt-bound proof; all candidate alternatives must authenticate, with no partial success. */
    public static Evidence prove(Receipt receipt,CompositionCandidate candidate,int maximumDepth,int maximumWork) {
        if (maximumDepth < 0 || maximumWork < 1) throw new IllegalArgumentException("Bounded ground composition proof required");
        return prove(receipt,candidate,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Evidence prove(Receipt receipt,CompositionCandidate candidate,int maximumDepth,Runnable work) {
        try {
            if (receipt == null || !receipt.verified || !receipt.collisions.isEmpty() || candidate == null)
                throw new IllegalStateException("Complete collision-free receipt and native candidate required");
            authenticate(candidate.owner,candidate.ownerSupports,receipt,work);
            authenticate(candidate.partner,candidate.partnerSupports,receipt,work);
            List<Fact> remaining = new ArrayList<>();
            remainder(candidate.owner,candidate.pivot,remaining,work);
            remainder(candidate.partner,candidate.pivot.opposite(),remaining,work);
            Clause conclusion = new Clause(remaining);
            List<Proof> result = new ArrayList<>();
            for (Support owner : candidate.ownerSupports) for (Support partner : candidate.partnerSupports) {
                work.run(); int depth = Math.max(owner.depth,partner.depth);
                if (depth == Integer.MAX_VALUE || ++depth > maximumDepth) throw new IllegalStateException("Composition cumulative depth limit");
                Set<Source> sources = new LinkedHashSet<>(owner.sources); sources.addAll(partner.sources);
                Support support = new Support(sources,depth);
                boolean duplicate = false;
                for (Proof old : result) { work.run(); if (old.support.equals(support)) { duplicate = true; break; } }
                if (!duplicate) {
                    if (result.size() >= 256) throw new IllegalStateException("Composition alternative proof limit");
                    result.add(new Proof(conclusion,support,candidate));
                }
            }
            return new Evidence(true,"Checked ground binary resolution; composition coverage/admission unknown",receipt,result);
        } catch (Exception failure) { return new Evidence(false,failure.getMessage(),receipt,Collections.emptyList()); }
    }
}
