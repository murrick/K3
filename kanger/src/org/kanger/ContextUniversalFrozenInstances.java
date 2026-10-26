/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextUniversalFrozenEvents.Donor;

/** Checked instances for explicitly requested concrete units; no enumeration or coverage authority. */
final class ContextUniversalFrozenInstances {
    static final class Instance {
        final Residual fact;
        final List<Donor> proofs;
        private Instance(Residual fact,List<Donor> proofs){this.fact=fact;this.proofs=proofs;}
    }
    static final class Result {
        final boolean proved;
        final String reason;
        final ContextUniversalFrozenUnits.Result input;
        final List<Instance> instances;
        private Result(boolean proved,String reason,ContextUniversalFrozenUnits.Result input,List<Instance> instances) {
            this.proved=proved;this.reason=reason;this.input=input;
            this.instances=Collections.unmodifiableList(new ArrayList<>(instances));
        }
    }
    static Result prove(ContextUniversalFrozenUnits.Result input,List<Residual> requests,int proofDepth,int factDepth,int maximumReceipts,int maximumWork) {
        if(proofDepth<0||proofDepth>64||factDepth<0||factDepth>64||maximumReceipts<1||maximumReceipts>65536||maximumWork<1)
            throw new IllegalArgumentException("Bounded concrete unit instantiation required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);int[] capacity={maximumReceipts};
        try {
            if(input==null||!input.projected||requests==null)throw new IllegalStateException("Sealed unit projection and concrete requests required");
            ContextUniversalFrozenClosure.check(input.input.theory,proofDepth,work);
            List<Instance> out=new ArrayList<>();
            for(Residual fact:requests) {
                work.run();if(fact==null||fact.literals.size()!=1||!fact.variables.isEmpty()||!fact.literals.get(0).positive)
                    throw new IllegalStateException("Concrete positive unit request required");
                boolean duplicate=false;for(Instance prior:out){work.run();if(prior.fact.equivalent(fact,work)){duplicate=true;break;}}
                if(duplicate)continue;
                if(--capacity[0]<0)throw new IllegalStateException("Concrete instance receipt limit");
                List<Donor> proofs=ContextUniversalFrozenEvents.instances(fact,input,capacity,work);
                // The shared kernel already minimizes each exact Source set. Never drop a deeper alternative.
                for(Donor proof:proofs){work.run();if(proof.support.depth>factDepth)throw new IllegalStateException("Concrete instance fact depth limit");}
                out.add(new Instance(fact,proofs));
            }
            return new Result(true,"Requested concrete units proved; complete instantiation and family coverage remain open",input,out);
        }catch(Exception failure){return new Result(false,failure.toString(),input,Collections.emptyList());}
    }
}
