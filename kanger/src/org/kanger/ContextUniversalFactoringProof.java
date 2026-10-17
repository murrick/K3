/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalUnitLedger.Row;

/** Detached same-parent flat factoring with actual sealed-ledger provenance. */
public final class ContextUniversalFactoringProof {
    public enum Scope { ORDINARY_FLAT_SAME_SIGN_FACTORING }
    public static final class Proof {
        public final Clause owner,conclusion;
        public final int firstLiteral,secondLiteral;
        public final Support ownerSupport,support;
        private Proof(Clause owner,int first,int second,Clause conclusion,Support parent,Support support) {
            this.owner=owner; firstLiteral=first; secondLiteral=second; this.conclusion=conclusion;
            ownerSupport=parent; this.support=support;
        }
    }
    public static final class Evidence {
        public final Scope scope=Scope.ORDINARY_FLAT_SAME_SIGN_FACTORING;
        public final boolean proved;
        public final String reason;
        public final ContextUniversalUnitLedger ledger;
        public final List<Proof> proofs;
        private Evidence(boolean proved,String reason,ContextUniversalUnitLedger ledger,List<Proof> proofs) {
            this.proved=proved; this.reason=reason; this.ledger=ledger;
            this.proofs=Collections.unmodifiableList(new ArrayList<>(proofs));
        }
    }
    public static Evidence prove(ContextUniversalUnitLedger ledger,Clause owner,int first,int second,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded factoring proof required");
        return proveBound(ledger,owner,first,second,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Evidence proveBound(ContextUniversalUnitLedger ledger,Clause owner,int first,int second,int maximumDepth,Runnable work) {
        try {
            if(ledger==null || owner==null || owner.literals.size()<2) throw new IllegalStateException("Sealed ledger and whole compound owner required");
            List<Support> alternatives=new ArrayList<>();
            for(Row row:ledger.rows) { work.run(); if(row.clause.equivalent(owner) && !alternatives.contains(row.support)) alternatives.add(row.support); }
            if(alternatives.isEmpty()) throw new IllegalStateException("Whole factoring owner absent from exact ledger");
            Clause conclusion=owner.factor(first,second,work); List<Proof> proofs=new ArrayList<>();
            for(Support parent:alternatives) {
                work.run(); if(parent.depth==Integer.MAX_VALUE || parent.depth+1>maximumDepth) throw new IllegalStateException("Factoring cumulative depth limit");
                if(proofs.size()>=256) throw new IllegalStateException("Factoring alternative proof limit");
                Set<Source> sources=new LinkedHashSet<>(); for(Source source:parent.sources) { work.run(); sources.add(source); }
                proofs.add(new Proof(owner,first,second,conclusion,parent,new Support(sources,parent.depth+1)));
            }
            return new Evidence(true,"Checked flat factoring; transport, closure and admission unknown",ledger,proofs);
        } catch(Exception failure) { return new Evidence(false,failure.getMessage(),ledger,Collections.emptyList()); }
    }
}
