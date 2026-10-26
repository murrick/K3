/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextUniversalInputResiduals.*;

/** All fixed premise occurrences in this sealed positive ledger, not all universal instances. */
final class ContextUniversalFrozenPremises {
    static final class Obligation {
        final Proof conditional;
        final int pivot;
        final Residual fact;
        final boolean enabled;
        private Obligation(Proof conditional,int pivot,Residual fact,boolean enabled){this.conditional=conditional;this.pivot=pivot;this.fact=fact;this.enabled=enabled;}
    }
    static final class Result {
        final boolean prepared;
        final String reason;
        final ContextUniversalFrozenUnits.Result input;
        final List<Obligation> obligations;
        final int symbolic;
        final ContextUniversalFrozenInstances.Result instances;
        private Result(boolean prepared,String reason,ContextUniversalFrozenUnits.Result input,List<Obligation> obligations,int symbolic,ContextUniversalFrozenInstances.Result instances) {
            this.prepared=prepared;this.reason=reason;this.input=input;this.obligations=Collections.unmodifiableList(new ArrayList<>(obligations));this.symbolic=symbolic;this.instances=instances;
        }
    }
    static Result prepare(ContextUniversalFrozenUnits.Result input,int proofDepth,int maximumReceipts,int maximumWork) {
        if(proofDepth<0||proofDepth>64||maximumReceipts<1||maximumReceipts>65536||maximumWork<1)throw new IllegalArgumentException("Bounded concrete premise projection required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(input==null||!input.projected)throw new IllegalStateException("Sealed unit projection required");
            List<Obligation> obligations=new ArrayList<>();List<Residual> requests=new ArrayList<>();int symbolic=0;
            for(Proof conditional:input.input.theory.proofs)for(int pivot=0;pivot<conditional.clause.literals.size();++pivot) {
                work.run();Literal literal=conditional.clause.literals.get(pivot);if(literal.positive)continue;
                boolean fixed=true;for(Term term:literal.arguments){work.run();if(term.variable>=0)fixed=false;}
                if(!fixed){++symbolic;continue;}
                Residual fact=ContextUniversalInputResiduals.concretePremise(conditional.clause,pivot,work);boolean enabled=false;
                for(Proof unit:input.input.theory.proofs) {
                    work.run();if(unit.clause.literals.size()==1&&!ContextUniversalInputResiduals.assignments(unit.clause,fact,work).isEmpty())enabled=true;
                }
                if(obligations.size()==maximumReceipts)throw new IllegalStateException("Concrete premise obligation limit");
                obligations.add(new Obligation(conditional,pivot,fact,enabled));
                if(enabled) {
                    boolean duplicate=false;for(Residual prior:requests){work.run();if(prior.equivalent(fact,work))duplicate=true;}
                    if(!duplicate)requests.add(fact);
                }
            }
            // Exploration receipts are not accepted fact bounds. Transfer combines all exact-Source minima later.
            ContextUniversalFrozenInstances.Result instances=ContextUniversalFrozenInstances.prove(input,requests,proofDepth,64,maximumReceipts,maximumWork);
            if(!instances.proved)throw new IllegalStateException(instances.reason);
            return new Result(true,"Fixed premise occurrences and enabled instances checked; symbolic obligations and full discovery remain open",input,obligations,symbolic,instances);
        }catch(Exception failure){return new Result(false,failure.toString(),input,Collections.emptyList(),0,null);}
    }
}
