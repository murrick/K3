/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Input;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextUniversalInputResiduals.Residual;

/** Fixed positive unit theorems, not native application conclusions or family coverage. */
final class ContextUniversalFrozenUnits {
    static final class Unit {
        final Residual conclusion;
        final Support support;
        final Proof proof;
        /** Null only for an authenticated original local input fact. */
        final Input nativeInput;
        private Unit(Proof proof,Input nativeInput) {
            this.proof=proof;this.nativeInput=nativeInput;conclusion=proof.clause;support=proof.support;
        }
    }
    static final class Result {
        final boolean projected;
        final String reason;
        final ContextUniversalFrozenReplay.Result input;
        final List<Unit> units;
        final int universalUnits,conditionals;
        private Result(boolean projected,String reason,ContextUniversalFrozenReplay.Result input,List<Unit> units,int universalUnits,int conditionals) {
            this.projected=projected;this.reason=reason;this.input=input;
            this.units=Collections.unmodifiableList(new ArrayList<>(units));this.universalUnits=universalUnits;this.conditionals=conditionals;
        }
    }
    static Result project(ContextUniversalFrozenReplay.Result input,int maximumDepth,int maximumUnits,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumUnits<1||maximumUnits>65536||maximumWork<1)
            throw new IllegalArgumentException("Bounded fixed unit projection required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(input==null||!input.bound||input.observation==null||input.replay==null)
                throw new IllegalStateException("Sealed successful typed native replay required");
            ContextUniversalFrozenClosure.check(input.theory,maximumDepth,work);
            Map<Proof,Input> receipts=new IdentityHashMap<>();
            for(Input receipt:input.observation.inputs) {
                work.run();List<Proof> proofs=input.bindings.get(receipt.nativeRule);
                if(proofs==null||receipt.source!=null||receipt.typedClause==null||receipt.branches!=1)
                    throw new IllegalStateException("Missing typed native unit receipt");
                for(Proof proof:proofs) {
                    work.run();if(!proof.clause.equivalent(receipt.typedClause,work))throw new IllegalStateException("Different typed native unit receipt");
                    receipts.put(proof,receipt);
                }
            }
            Set<Proof> imported=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());
            for(Proof proof:input.imported){work.run();imported.add(proof);}
            List<Unit> units=new ArrayList<>();int universal=0,conditional=0;
            for(Proof proof:input.theory.proofs) {
                work.run();
                if(proof.input!=null?!imported.contains(proof):!receipts.containsKey(proof))
                    throw new IllegalStateException("Typed theorem lacks its actual imported/native receipt");
                if(proof.clause.literals.size()!=1){++conditional;continue;}
                if(!proof.clause.literals.get(0).positive)throw new IllegalStateException("Positive typed unit required");
                if(!proof.clause.variables.isEmpty()){++universal;continue;}
                if(units.size()==maximumUnits)throw new IllegalStateException("Fixed unit receipt limit");
                // This is an already checked theorem. No inference, new anchor or depth increment occurs here.
                units.add(new Unit(proof,receipts.get(proof)));
            }
            return new Result(true,"Fixed checked unit theorems projected; universal instantiation, event donors and family coverage remain open",input,units,universal,conditional);
        }catch(Exception failure){return new Result(false,failure.toString(),input,Collections.emptyList(),0,0);}
    }
}
