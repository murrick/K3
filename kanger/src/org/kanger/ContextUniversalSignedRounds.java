/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextWitnessResultTransport.*;

/** All supplied cuts on immutable signed barriers; stable fixed-unit projection, not family admission. */
final class ContextUniversalSignedRounds {
    enum Status { STABLE_SIGNED_POOLS, REFUTED, INCOMPLETE }
    static final class Refutation {
        final Cut cut;final int round;final boolean xRefuted,foreignRefuted;
        final UUID inputIdentity;
        final List<Proof> proofs;
        private Refutation(Cut cut,int round,ContextUniversalFrozenConstraints.Result checked) {
            this.cut=cut;this.round=round;inputIdentity=checked.common.identity;xRefuted=checked.xRefuted;foreignRefuted=checked.foreignRefuted;
            proofs=Collections.unmodifiableList(new ArrayList<>(checked.emptyProofs));
        }
    }
    static final class Round {
        final int number;final Packet input;final Map<Cut,PrivatePool> privateInput;
        final Map<Cut,ContextUniversalFrozenConstraints.Result> checks;
        final Map<Cut,ContextUniversalSignedProductAudit.Result> products;
        final ConstraintBarrier barrier;final boolean commonChanged,privateChanged;
        private Round(int number,Packet input,Map<Cut,PrivatePool> pools,Map<Cut,ContextUniversalFrozenConstraints.Result> checks,Map<Cut,ContextUniversalSignedProductAudit.Result> products,ConstraintBarrier barrier,boolean commonChanged,boolean privateChanged) {
            this.number=number;this.input=input;privateInput=Collections.unmodifiableMap(new LinkedHashMap<>(pools));this.checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks));
            this.products=Collections.unmodifiableMap(new LinkedHashMap<>(products));this.barrier=barrier;this.commonChanged=commonChanged;this.privateChanged=privateChanged;
        }
    }
    static final class Result {
        final Status status;final String phase,reason;final int completedRounds;
        final Packet input;final ConstraintBarrier output;final List<Round> history;final Map<Cut,Refutation> refutations;
        private Result(Status status,String phase,String reason,int completedRounds,Packet input,ConstraintBarrier output,List<Round> history,Map<Cut,Refutation> refutations) {
            this.status=status;this.phase=phase;this.reason=reason;this.completedRounds=completedRounds;this.input=input;this.output=output;
            this.history=Collections.unmodifiableList(new ArrayList<>(history));this.refutations=Collections.unmodifiableMap(new LinkedHashMap<>(refutations));
        }
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
    static Result run(Snapshot x,List<Snapshot> foreign,Packet initial,Map<Cut,PrivatePool> initialPools,ContextUniversalFrozenBranch.Limits limits,int maximumRounds,int maximumWork) {
        if(maximumRounds<1||maximumRounds>64||maximumWork<1)throw new IllegalArgumentException("Bounded signed rounds required");
        String phase="preflight";int completed=0;Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            require(x!=null&&foreign!=null&&foreign.size()<=15&&initial!=null&&initialPools!=null&&limits!=null,"Actual supplied signed native/common/private inputs required");
            Map<Cut,Snapshot> targets=new LinkedHashMap<>();ContextProductionFamilyAssembly.identity(x,true,work);Cut self=Cut.of(x);targets.put(self,null);
            for(Snapshot snapshot:new ArrayList<>(foreign)){work.run();require(snapshot!=null,"Actual foreign snapshot required");ContextProductionFamilyAssembly.identity(snapshot,false,work);ContextProductionFamilyAssembly.disjoint(x,snapshot,work);Cut cut=Cut.of(snapshot);require(!targets.containsKey(cut),"Repeated supplied signed cut");targets.put(cut,snapshot);}
            List<Cut> cuts=new ArrayList<>(targets.keySet());ContextWitnessResultTransport.family(initial,cuts,maximumWork);Map<Cut,PrivatePool> pools=new LinkedHashMap<>(initialPools);
            for(Map.Entry<Cut,PrivatePool> e:pools.entrySet()){work.run();PrivatePool pool=e.getValue();require(pool!=null&&targets.containsKey(e.getKey())&&!e.getKey().equals(self)&&pool.cut.equals(e.getKey())&&pool.common==initial&&pool.x==x&&pool.snapshot==targets.get(e.getKey()),"Different exact initial signed private frame");}
            Packet input=initial;List<Round> history=new ArrayList<>();
            for(int number=1;number<=maximumRounds;++number) {
                phase="checks";Map<Cut,ContextUniversalFrozenConstraints.Result> checks=new LinkedHashMap<>();
                for(Map.Entry<Cut,Snapshot> target:targets.entrySet()) {
                    work.run();ContextUniversalNativeRows.Result rows=ContextUniversalNativeRows.inspect(x,target.getValue(),64,limits.work);require(rows.classified,rows.reason);require(rows.withinShape,"Unsupported whole original signed row plan");
                    ContextUniversalFrozenConstraints.Result checked=ContextUniversalFrozenConstraints.closeSigned(rows,input,pools.get(target.getKey()),limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work);
                    require(checked.closed,"Round "+number+" signed cut: "+checked.reason);checks.put(target.getKey(),checked);
                }
                // Finish checking every supplied cut before reporting a refutation or creating a product.
                Map<Cut,Refutation> refutations=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:checks.entrySet()){work.run();if(e.getValue().xRefuted||e.getValue().foreignRefuted)refutations.put(e.getKey(),new Refutation(e.getKey(),number,e.getValue()));}
                if(!refutations.isEmpty())return new Result(Status.REFUTED,"checks","All supplied cuts checked; sourced refutation at common barrier "+number,completed,initial,null,Collections.emptyList(),refutations);
                phase="transfers";Map<Cut,ConstraintTransfer> transfers=new LinkedHashMap<>();
                for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:checks.entrySet()){work.run();PrivatePool pool=pools.get(e.getKey());ConstraintTransfer t=pool==null?ContextWitnessResultTransport.transferConstraints(e.getValue(),input,limits.factDepth,limits.work):ContextWitnessResultTransport.transferConstraintsPrivate(e.getValue(),pool,limits.factDepth,limits.work);require(t.transferred,t.reason);transfers.put(e.getKey(),t);}
                phase="product-correspondence";Map<Cut,ContextUniversalSignedProductAudit.Result> products=new LinkedHashMap<>();
                for(Map.Entry<Cut,ConstraintTransfer> e:transfers.entrySet()){work.run();ContextUniversalSignedProductAudit.Result audit=ContextUniversalSignedProductAudit.bind(checks.get(e.getKey()),e.getValue(),65536,limits.work);require(audit.bound,audit.reason);products.put(e.getKey(),audit);}
                phase="barrier";ConstraintBarrier barrier=ContextWitnessResultTransport.mergeConstraints(ContextWitnessResultTransport.family(input,cuts,maximumWork),transfers,maximumWork);require(barrier.merged,barrier.reason);
                phase="pool-comparison";boolean changed=!ContextWitnessResultTransport.sameContents(input,barrier.packet,maximumWork),privateChanged=false;require(changed==barrier.changed&&pools.keySet().equals(barrier.privatePools.keySet()),"Different signed pool change/inventory receipt");
                for(Map.Entry<Cut,PrivatePool> e:pools.entrySet()){work.run();PrivatePool next=barrier.privatePools.get(e.getKey());require(next.common==barrier.packet&&next.x==x&&next.snapshot==e.getValue().snapshot,"Different rebased signed private frame");privateChanged|=!ContextWitnessResultTransport.sameContents(e.getValue().local,next.local,maximumWork);}
                history.add(new Round(number,input,pools,checks,products,barrier,changed,privateChanged));completed=number;
                if(!changed&&!privateChanged&&!barrier.localChanged)return new Result(Status.STABLE_SIGNED_POOLS,"stable-signed-pools","Complete supplied signed fixed-unit pools unchanged; symbolic/native/family qualification remains open",completed,initial,barrier,history,Collections.emptyMap());
                input=barrier.packet;pools=new LinkedHashMap<>(barrier.privatePools);
            }
            phase="round-limit";throw new IllegalStateException("Signed round limit; no qualified stable output");
        }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),completed,initial,null,Collections.emptyList(),Collections.emptyMap());}
    }
}
