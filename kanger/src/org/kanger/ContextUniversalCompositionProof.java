/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Receipt-authenticated flat universal binary resolution. A proposed pair is not native coverage. */
public final class ContextUniversalCompositionProof {
    public enum Scope { ORDINARY_FLAT_UNIVERSAL_BINARY_RESOLUTION }
    public static final class Proof {
        public final Clause owner,partner,conclusion;
        public final int ownerPivot,partnerPivot;
        public final Support support,ownerSupport,partnerSupport;
        private Proof(Clause owner,int ownerPivot,Clause partner,int partnerPivot,Clause conclusion,Support support,Support ownerSupport,Support partnerSupport) {
            this.owner = owner; this.ownerPivot = ownerPivot; this.partner = partner; this.partnerPivot = partnerPivot;
            this.conclusion = conclusion; this.support = support; this.ownerSupport = ownerSupport; this.partnerSupport = partnerSupport;
        }
    }
    public static final class Evidence {
        public final Scope scope = Scope.ORDINARY_FLAT_UNIVERSAL_BINARY_RESOLUTION;
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
    private static List<Support> authenticate(Clause clause,Receipt receipt,Runnable work) {
        if (clause == null || clause.literals.size() < 2) throw new IllegalStateException("Whole ordinary compound parent required");
        List<Support> supports = new ArrayList<>();
        for (Product actual : receipt.localProofs) {
            work.run(); if (!actual.clause.equivalent(clause)) continue;
            if (!supports.contains(actual.proof)) supports.add(actual.proof);
        }
        if (supports.isEmpty()) throw new IllegalStateException("Universal composition parent absent from exact receipt ledger");
        return supports;
    }
    /** Select an actual complete ledger pair; no candidate clause, constant sampling or new primary Source. */
    public static Evidence prove(Receipt receipt,Clause owner,int ownerPivot,Clause partner,int partnerPivot,int maximumDepth,int maximumWork) {
        if (maximumDepth < 0 || maximumWork < 1) throw new IllegalArgumentException("Bounded universal composition required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            if (receipt == null || !receipt.verified || !receipt.collisions.isEmpty()) throw new IllegalStateException("Verified collision-free exact-input receipt required");
            List<Support> left = authenticate(owner,receipt,work),right = authenticate(partner,receipt,work);
            Clause conclusion = owner.resolve(partner,ownerPivot,partnerPivot,work);
            List<Proof> result = new ArrayList<>();
            for (Support a : left) for (Support b : right) {
                work.run(); int depth = Math.max(a.depth,b.depth);
                if (depth == Integer.MAX_VALUE || ++depth > maximumDepth) throw new IllegalStateException("Universal composition cumulative depth limit");
                Set<Source> sources = new LinkedHashSet<>();
                for (Source source : a.sources) { work.run(); sources.add(source); }
                for (Source source : b.sources) { work.run(); sources.add(source); }
                Support support = new Support(sources,depth); boolean duplicate = false;
                for (Proof old : result) { work.run(); if (old.support.equals(support)) { duplicate = true; break; } }
                if (!duplicate) {
                    if (result.size() >= 256) throw new IllegalStateException("Universal composition alternative proof limit");
                    result.add(new Proof(owner,ownerPivot,partner,partnerPivot,conclusion,support,a,b));
                }
            }
            return new Evidence(true,"Checked flat universal resolution; coverage/transport/admission unknown",receipt,result);
        } catch (Exception failure) { return new Evidence(false,failure.getMessage(),receipt,Collections.emptyList()); }
    }
}
