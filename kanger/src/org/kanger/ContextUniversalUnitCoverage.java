/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitProof.*;

/** Independent unit-pair obligations over actual cut sources and sealed compound evidence. */
public final class ContextUniversalUnitCoverage {
    public static final class Audit {
        public final boolean covered;
        public final String reason;
        private Audit(boolean covered,String reason) { this.covered=covered; this.reason=reason; }
    }
    private static void add(List<Clause> clauses,Clause clause,Runnable work) {
        for(Clause old:clauses) { work.run(); if(old.equivalent(clause)) return; }
        clauses.add(clause);
    }
    public static Audit auditPrimary(Snapshot x,Snapshot foreign,List<Evidence> supplied,int maximumDepth,int maximumWork) {
        return audit(x,null,foreign==null?x:foreign,Collections.emptyList(),supplied,maximumDepth,maximumWork);
    }
    public static Audit audit(Frontier input,Snapshot target,List<ContextUniversalCompositionProof.Evidence> compositions,
            List<Evidence> supplied,int maximumDepth,int maximumWork) {
        return audit(input==null?null:input.x,input,target,compositions,supplied,maximumDepth,maximumWork);
    }
    private static Audit audit(Snapshot x,Frontier input,Snapshot target,List<ContextUniversalCompositionProof.Evidence> compositions,
            List<Evidence> supplied,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded unit coverage required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(x==null || target==null || input!=null && !input.cuts.contains(target)) throw new IllegalStateException("Exact unit cut required");
            List<Evidence> evidence=new ArrayList<>(supplied);
            for(Evidence e:evidence) {
                work.run(); if(e==null || !e.proved || e.ledger!=null || e.x!=x || e.input!=input || e.target!=target)
                    throw new IllegalStateException("Unit evidence has wrong snapshots, input, cut or status");
            }
            List<ContextUniversalCompositionProof.Evidence> compound=new ArrayList<>(compositions);
            List<Clause> clauses=new ArrayList<>();
            for(Entry entry:ContextUniversalUnitProof.declarations(x,target,work)) for(Clause c:entry.clauses) { work.run(); add(clauses,c,work); }
            for(Product p:input==null?Collections.<Product>emptyList():input.products) {
                work.run(); if(p.proof.depth>maximumDepth) throw new IllegalStateException("Carried parent depth limit");
                add(clauses,p.clause,work);
            }
            for(ContextUniversalCompositionProof.Evidence e:compound) {
                work.run(); if(input==null || e==null || !e.proved || e.ledger!=null || e.input!=input || e.target!=target)
                    throw new IllegalStateException("Compound evidence has wrong input, cut or status");
                for(ContextUniversalCompositionProof.Proof p:e.proofs) {
                    work.run(); if(p.support.depth>maximumDepth) throw new IllegalStateException("Compound parent depth limit");
                    add(clauses,p.conclusion,work);
                }
            }
            for(int i=0;i<clauses.size();i++) for(int j=i;j<clauses.size();j++) {
                Clause left=clauses.get(i),right=clauses.get(j);
                work.run(); if(left.literals.size()!=1 && right.literals.size()!=1) continue;
                for(int a=0;a<left.literals.size();a++) for(int b=0;b<right.literals.size();b++) {
                    work.run(); if(!ContextUniversalCompositionCoverage.unifiable(left.literals.get(a),right.literals.get(b),work)) continue;
                    Evidence expected=ContextUniversalUnitProof.proveBound(x,input,target,left,a,right,b,compound,maximumDepth,work);
                    if(!expected.proved) throw new IllegalStateException(expected.reason);
                    for(Proof required:expected.proofs) {
                        boolean found=false;
                        for(Evidence e:evidence) for(Proof actual:e.proofs) {
                            work.run();
                            if(actual.support.equals(required.support) && actual.contradiction==required.contradiction
                                    && (required.contradiction || actual.conclusion.equivalent(required.conclusion))) found=true;
                        }
                        if(!found) throw new IllegalStateException(required.contradiction?"Missing checked unit opposition":"Missing checked unit consequence");
                    }
                }
            }
            return new Audit(true,"Detached unit-pair consequences/oppositions covered; transport, closure and admission unknown");
        } catch(Exception failure) { return new Audit(false,failure.getMessage()); }
    }
    /** Exact historical ledger step, independent of the production sweep's MGU trials. */
    public static Audit audit(ContextUniversalUnitLedger ledger,List<Evidence> supplied,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded ledger coverage required");
        return audit(ledger,supplied,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Audit audit(ContextUniversalUnitLedger ledger,List<Evidence> supplied,int maximumDepth,Runnable work) {
        try {
            if(ledger==null) throw new IllegalStateException("Sealed unit ledger required");
            List<Evidence> evidence=new ArrayList<>(supplied);
            for(Evidence e:evidence) {
                work.run(); if(e==null || !e.proved || e.ledger!=ledger) throw new IllegalStateException("Wrong historical unit ledger evidence");
            }
            List<Clause> clauses=new ArrayList<>();
            for(ContextUniversalUnitLedger.Row row:ledger.rows) {
                work.run(); if(row.support.depth>maximumDepth) throw new IllegalStateException("Ledger parent depth limit");
                add(clauses,row.clause,work);
            }
            for(int i=0;i<clauses.size();i++) for(int j=i;j<clauses.size();j++) {
                Clause left=clauses.get(i),right=clauses.get(j);
                work.run(); if(left.literals.size()!=1 && right.literals.size()!=1) continue;
                for(int a=0;a<left.literals.size();a++) for(int b=0;b<right.literals.size();b++) {
                    work.run(); if(!ContextUniversalCompositionCoverage.unifiable(left.literals.get(a),right.literals.get(b),work)) continue;
                    Evidence expected=ContextUniversalUnitProof.proveLedger(ledger,left,a,right,b,maximumDepth,work);
                    if(!expected.proved) throw new IllegalStateException(expected.reason);
                    for(Proof required:expected.proofs) {
                        boolean found=false;
                        for(Evidence e:evidence) for(Proof actual:e.proofs) {
                            work.run(); if(actual.support.equals(required.support) && actual.contradiction==required.contradiction
                                && (required.contradiction || actual.conclusion.equivalent(required.conclusion))) found=true;
                        }
                        if(!found) throw new IllegalStateException("Missing checked ledger unit consequence/opposition");
                    }
                }
            }
            return new Audit(true,"Exact private ledger unit step covered; family closure/admission unknown");
        } catch(Exception failure) { return new Audit(false,failure.getMessage()); }
    }

}
