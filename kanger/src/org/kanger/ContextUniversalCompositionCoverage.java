/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextUniversalCompositionProof.*;

/** Audits detached compound-resolution evidence, including standardized-apart self pairs. */
public final class ContextUniversalCompositionCoverage {
    public static final class Audit {
        public final boolean covered;
        public final String reason;
        private Audit(boolean covered,String reason) { this.covered=covered; this.reason=reason; }
    }
    // Independent feasibility check: connected equalities on argument positions.
    // Variable labels from opposite parents are deliberately never compared.
    private static boolean unifiable(Atom a,Atom b,Runnable work) {
        if (a.positive==b.positive || !a.predicate.equals(b.predicate) || a.arguments.size()!=b.arguments.size()) return false;
        int n=a.arguments.size(); if(n>256) throw new IllegalStateException("Universal pivot arity audit limit");
        List<Slot> slots=new ArrayList<>(a.arguments); slots.addAll(b.arguments);
        boolean[][] equal=new boolean[2*n][2*n];
        for(int i=0;i<2*n;i++) for(int j=0;j<2*n;j++) {
            work.run(); Slot x=slots.get(i),y=slots.get(j);
            equal[i][j]=i==j || (i%n==j%n && i/n!=j/n)
                || (i/n==j/n && x.variable>=0 && x.variable==y.variable);
        }
        for(int k=0;k<2*n;k++) for(int i=0;i<2*n;i++) for(int j=0;j<2*n;j++) {
            work.run(); equal[i][j] |= equal[i][k] && equal[k][j];
        }
        for(int i=0;i<2*n;i++) for(int j=0;j<2*n;j++) {
            work.run(); Slot x=slots.get(i),y=slots.get(j);
            if(equal[i][j] && x.variable<0 && y.variable<0 && !x.value.semanticallyEquals(y.value)) return false;
        }
        return true;
    }
    /** One exact receipt ledger, not a closure, family certificate or transport permission. */
    public static Audit audit(Receipt receipt,List<Evidence> supplied,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded universal coverage required");
        return audit(receipt,supplied,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Audit audit(Receipt receipt,List<Evidence> supplied,int maximumDepth,Runnable work) {
        try {
            if(receipt==null || !receipt.verified || !receipt.collisions.isEmpty()) throw new IllegalStateException("Verified collision-free receipt required");
            List<Evidence> evidence=new ArrayList<>(supplied);
            for(Evidence e:evidence) {
                work.run();
                if(e==null || !e.proved || e.input!=receipt.input || e.target!=receipt.target)
                    throw new IllegalStateException("Detached evidence has wrong input, cut or status");
            }
            List<Clause> clauses=new ArrayList<>();
            for(Product p:receipt.localProofs) {
                work.run();
                if(p.proof.depth>maximumDepth) throw new IllegalStateException("Parent depth limit");
                if(p.clause.literals.size()<2) continue;
                boolean seen=false;
                for(Clause old:clauses) { work.run(); if(old.equivalent(p.clause)) { seen=true; break; } }
                if(!seen) clauses.add(p.clause);
            }
            for(int i=0;i<clauses.size();i++) for(int j=i;j<clauses.size();j++) {
                Clause a=clauses.get(i),b=clauses.get(j);
                for(int x=0;x<a.literals.size();x++) for(int y=0;y<b.literals.size();y++) {
                    work.run(); if(!unifiable(a.literals.get(x),b.literals.get(y),work)) continue;
                    Evidence expected=ContextUniversalCompositionProof.prove(receipt,a,x,b,y,maximumDepth,work);
                    if(!expected.proved) throw new IllegalStateException(expected.reason);
                    for(Proof required:expected.proofs) {
                        boolean found=false;
                        for(Evidence e:evidence) for(Proof actual:e.proofs) {
                            work.run();
                            if(actual.support.equals(required.support) && actual.conclusion.equivalent(required.conclusion)) found=true;
                        }
                        if(!found) throw new IllegalStateException("Missing checked universal compound-pair consequence (self pairs included)");
                    }
                }
            }
            return new Audit(true,"Detached compound-pair obligations covered; units, transport, closure and admission unknown");
        } catch(Exception failure) { return new Audit(false,failure.getMessage()); }
    }
}
