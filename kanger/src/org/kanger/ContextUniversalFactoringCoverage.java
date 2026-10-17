/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalUnitLedger.Row;
import org.kanger.ContextUniversalFactoringProof.*;

/** Independent same-parent factoring obligations; detached proof coverage only. */
public final class ContextUniversalFactoringCoverage {
    public static final class Audit {
        public final boolean covered;
        public final String reason;
        private Audit(boolean covered,String reason) { this.covered=covered; this.reason=reason; }
    }
    public static Audit audit(ContextUniversalUnitLedger ledger,List<Evidence> supplied,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded factoring coverage required");
        return audit(ledger,supplied,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Audit audit(ContextUniversalUnitLedger ledger,List<Evidence> supplied,int maximumDepth,Runnable work) {
        try {
            if(ledger==null) throw new IllegalStateException("Exact factoring ledger required");
            List<Evidence> evidence=new ArrayList<>(supplied);
            for(Evidence e:evidence) { work.run(); if(e==null || !e.proved || e.ledger!=ledger) throw new IllegalStateException("Wrong historical factoring ledger evidence"); }
            List<Clause> clauses=new ArrayList<>();
            for(Row row:ledger.rows) {
                work.run(); if(row.support.depth>maximumDepth) throw new IllegalStateException("Factoring parent depth limit");
                if(row.clause.literals.size()<2) continue;
                boolean seen=false; for(Clause c:clauses) { work.run(); if(c.equivalent(row.clause)) { seen=true; break; } }
                if(!seen) clauses.add(row.clause);
            }
            for(Clause owner:clauses) for(int a=0;a<owner.literals.size();a++) for(int b=a+1;b<owner.literals.size();b++) {
                work.run(); if(!ContextUniversalCompositionCoverage.factorable(owner.literals.get(a),owner.literals.get(b),work)) continue;
                Evidence expected=ContextUniversalFactoringProof.proveBound(ledger,owner,a,b,maximumDepth,work);
                if(!expected.proved) throw new IllegalStateException(expected.reason);
                for(Proof required:expected.proofs) {
                    boolean found=false;
                    for(Evidence e:evidence) for(Proof actual:e.proofs) {
                        work.run(); if(actual.support.equals(required.support) && actual.conclusion.equivalent(required.conclusion)) found=true;
                    }
                    if(!found) throw new IllegalStateException("Missing checked whole-source factoring consequence");
                }
            }
            return new Audit(true,"Detached same-parent factoring obligations covered; transport and admission unknown");
        } catch(Exception failure) { return new Audit(false,failure.getMessage()); }
    }
}
