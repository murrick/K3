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
        public final ContextUniversalUnitLedger ledger;
        public final Snapshot target;
        public final List<Proof> proofs;
        private Evidence(boolean proved,String reason,Receipt receipt,List<Proof> proofs) {
            this(proved,reason,receipt,proofs,null);
        }
        private Evidence(boolean proved,String reason,Receipt receipt,List<Proof> proofs,ContextUniversalUnitLedger ledger) {
            this.proved = proved; this.reason = reason; this.ledger=ledger;
            input = ledger!=null ? ledger.input : receipt == null ? null : receipt.input;
            target = ledger!=null ? ledger.target : receipt == null ? null : receipt.target;
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
        return prove(receipt,owner,ownerPivot,partner,partnerPivot,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Evidence prove(Receipt receipt,Clause owner,int ownerPivot,Clause partner,int partnerPivot,int maximumDepth,Runnable work) {
        try {
            if (receipt == null || !receipt.verified || !receipt.collisions.isEmpty()) throw new IllegalStateException("Verified collision-free exact-input receipt required");
            List<Support> left = authenticate(owner,receipt,work),right = authenticate(partner,receipt,work);
            return build(receipt,null,owner,ownerPivot,partner,partnerPivot,left,right,maximumDepth,work);
        } catch (Exception failure) { return new Evidence(false,failure.getMessage(),receipt,Collections.emptyList()); }
    }
    public static Evidence proveFromLedger(ContextUniversalUnitLedger ledger,Clause owner,int ownerPivot,Clause partner,int partnerPivot,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded ledger composition required");
        return proveLedger(ledger,owner,ownerPivot,partner,partnerPivot,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Evidence proveLedger(ContextUniversalUnitLedger ledger,Clause owner,int ownerPivot,Clause partner,int partnerPivot,int maximumDepth,Runnable work) {
        try {
            if(ledger==null || owner==null || partner==null || owner.literals.size()<2 || partner.literals.size()<2)
                throw new IllegalStateException("Sealed ledger and whole compound parents required");
            List<Support> left=new ArrayList<>(),right=new ArrayList<>();
            for(ContextUniversalUnitLedger.Row row:ledger.rows) {
                work.run(); if(row.clause.equivalent(owner) && !left.contains(row.support)) left.add(row.support);
                work.run(); if(row.clause.equivalent(partner) && !right.contains(row.support)) right.add(row.support);
            }
            if(left.isEmpty() || right.isEmpty()) throw new IllegalStateException("Whole compound parent absent from exact ledger");
            return build(null,ledger,owner,ownerPivot,partner,partnerPivot,left,right,maximumDepth,work);
        } catch(Exception failure) { return new Evidence(false,failure.getMessage(),null,Collections.emptyList(),ledger); }
    }
    private static Evidence build(Receipt receipt,ContextUniversalUnitLedger ledger,Clause owner,int ownerPivot,Clause partner,int partnerPivot,
            List<Support> left,List<Support> right,int maximumDepth,Runnable work) {
        try {
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
            return new Evidence(true,"Checked flat universal resolution; coverage/transport/admission unknown",receipt,result,ledger);
        } catch (Exception failure) { return new Evidence(false,failure.getMessage(),receipt,Collections.emptyList(),ledger); }
    }
}
