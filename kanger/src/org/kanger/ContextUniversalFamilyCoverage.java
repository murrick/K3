/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitLedger.Row;
import org.kanger.ContextUniversalUnitLedger.Step;
import org.kanger.ContextUniversalUnitRounds.Frontier;
import org.kanger.ContextUniversalUnitRounds.Receipt;

/** Diagnostic exact-family binary coverage and explicit factoring boundary. Never satisfiability/admission. */
public final class ContextUniversalFamilyCoverage {
    public enum Mode { CLOSED_CUTS, STABLE_SHARED, FACTORED_CUTS, FACTORED_STABLE_SHARED }
    public static final class Audit {
        public final boolean covered;
        public final Mode mode;
        public final String reason;
        private Audit(boolean covered,Mode mode,String reason) { this.covered=covered; this.mode=mode; this.reason=reason; }
    }
    private static boolean present(List<Row> rows,Clause clause,Support proof,boolean minimum,Runnable work) {
        for(Row row:rows) {
            work.run(); if(row.clause.equivalent(clause) && row.support.sources.equals(proof.sources)
                && (minimum ? row.support.depth<=proof.depth : row.support.depth==proof.depth)) return true;
        }
        return false;
    }
    public static Audit audit(Frontier input,List<Receipt> receipts,Mode mode,int maximumDepth,int maximumWork) {
        if(mode==null || maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded family coverage required");
        return audit(input,receipts,mode,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Audit audit(Frontier input,List<Receipt> receipts,Mode mode,int maximumDepth,Runnable work) {
        boolean factoring=mode==Mode.FACTORED_CUTS || mode==Mode.FACTORED_STABLE_SHARED;
        boolean stable=mode==Mode.STABLE_SHARED || mode==Mode.FACTORED_STABLE_SHARED;
        try {
            if(input==null || !input.cuts.contains(input.x)) throw new IllegalStateException("Sealed combined family input required");
            List<Receipt> outputs=new ArrayList<>(receipts);
            if(outputs.size()!=input.cuts.size()) throw new IllegalStateException("Incomplete combined coverage family");
            Set<Snapshot> seen=new HashSet<>(); Set<Source> familySources=new HashSet<>(); Set<UUID> contexts=new HashSet<>();
            for(Snapshot cut:input.cuts) {
                work.run(); ContextProductionFamilyAssembly.identity(cut,cut==input.x,work);
                if(!contexts.add(cut.inventory.context)) throw new IllegalStateException("Repeated family context");
                for(Entry entry:cut.primary) { work.run(); familySources.add(entry.source); }
            }
            for(Receipt receipt:outputs) {
                work.run();
                if(receipt==null || !receipt.verified || !receipt.includesCompounds || receipt.input!=input
                    || !input.cuts.contains(receipt.target) || !seen.add(receipt.target) || !receipt.oppositions.isEmpty())
                    throw new IllegalStateException("Complete collision-free exact-input combined receipts required");
                if(factoring && !receipt.includesFactoring) throw new IllegalStateException("Factoring-enabled receipt required");
                Step step=receipt.closureStep;
                if(step==null || step.status!=ContextUniversalUnitLedger.Status.ADVANCED || step.changed || step.next==null
                    || step.input.x!=input.x || step.input.target!=receipt.target)
                    throw new IllegalStateException("Sealed stable combined private step required");
                if(factoring && !step.includesFactoring) throw new IllegalStateException("Sealed factoring closure step required");
                List<Row> rows=step.input.rows;
                for(Row row:rows) {
                    work.run(); if(row.support.depth>maximumDepth) throw new IllegalStateException("Private depth outside exact family");
                    for(Source source:row.support.sources) { work.run(); if(!familySources.contains(source)) throw new IllegalStateException("Private source outside exact family"); }
                    if(!present(step.next.rows,row.clause,row.support,false,work)) throw new IllegalStateException("Private stability lost an original recipe");
                }
                for(Row row:step.next.rows) if(!present(rows,row.clause,row.support,false,work)) throw new IllegalStateException("Private stability added a recipe");
                for(Entry entry:ContextUniversalUnitProof.declarations(input.x,receipt.target,work)) for(Clause clause:entry.clauses) {
                    work.run(); if(!present(rows,clause,new Support(Collections.singleton(entry.source),0),false,work))
                        throw new IllegalStateException("Original whole declaration missing from closed ledger");
                }
                for(Row row:input.products) if(!present(rows,row.clause,row.support,true,work)) throw new IllegalStateException("Common carrier missing from closed ledger");
                ContextUniversalUnitCoverage.Audit unit=ContextUniversalUnitCoverage.audit(step.input,step.evidence,maximumDepth,work);
                if(!unit.covered) throw new IllegalStateException(unit.reason);
                ContextUniversalCompositionCoverage.Audit compound=ContextUniversalCompositionCoverage.audit(step.input,step.compoundEvidence,maximumDepth,work);
                if(!compound.covered) throw new IllegalStateException(compound.reason);
                for(ContextUniversalUnitProof.Evidence e:step.evidence) for(ContextUniversalUnitProof.Proof p:e.proofs) {
                    work.run(); if(p.contradiction || !present(rows,p.conclusion,p.support,true,work)) throw new IllegalStateException("Unit consequence not retained in closed ledger");
                }
                for(ContextUniversalCompositionProof.Evidence e:step.compoundEvidence) for(ContextUniversalCompositionProof.Proof p:e.proofs) {
                    work.run(); if(!present(rows,p.conclusion,p.support,true,work)) throw new IllegalStateException("Compound consequence not retained in closed ledger");
                }
                if(factoring) {
                    ContextUniversalFactoringCoverage.Audit factors=ContextUniversalFactoringCoverage.audit(step.input,step.factoringEvidence,maximumDepth,work);
                    if(!factors.covered) throw new IllegalStateException(factors.reason);
                    for(ContextUniversalFactoringProof.Evidence e:step.factoringEvidence) for(ContextUniversalFactoringProof.Proof p:e.proofs) {
                        work.run(); if(!present(rows,p.conclusion,p.support,true,work)) throw new IllegalStateException("Factoring consequence not retained in closed ledger");
                    }
                }
                for(Row row:rows) if(row.support.depth>0 && row.support.sources.stream().anyMatch(s -> s.localX)) {
                    if(!present(receipt.products,row.clause,row.support,false,work)) throw new IllegalStateException("Qualified private consequence not exported");
                }
                for(Row row:receipt.products) {
                    work.run(); if(row.support.depth<=0 || row.support.sources.stream().noneMatch(s -> s.localX)
                        || !present(rows,row.clause,row.support,false,work)) throw new IllegalStateException("Unqualified or invented exported recipe");
                }
                if(mode==Mode.STABLE_SHARED) for(Row row:rows) for(int a=0;a<row.clause.literals.size();a++) for(int b=a+1;b<row.clause.literals.size();b++) {
                    work.run(); if(ContextUniversalCompositionCoverage.factorable(row.clause.literals.get(a),row.clause.literals.get(b),work))
                        throw new IllegalStateException("Unresolved same-sign factoring boundary; binary stability is not consistency");
                }
            }
            ContextUniversalUnitRounds.Barrier barrier=ContextUniversalUnitRounds.mergeBound(input,outputs,maximumDepth,work);
            if(barrier.status!=ContextUniversalUnitRounds.Status.MERGED) throw new IllegalStateException("Combined family has collision or incomplete merge: "+barrier.reason);
            if(stable && barrier.changed) throw new IllegalStateException("New shared consequences require another common round");
            return new Audit(true,mode,factoring?"Exact resolution/factoring obligations covered; satisfiability and write admission unknown":"Exact combined binary obligations covered; satisfiability and write admission unknown");
        } catch(Exception failure) { return new Audit(false,mode,failure.getMessage()); }
    }
}
