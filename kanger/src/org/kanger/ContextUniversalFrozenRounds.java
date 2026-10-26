/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Synchronous supplied-cut positive pool stabilization, never full family qualification. */
final class ContextUniversalFrozenRounds {
    static final class Round {
        final int number;
        final Packet input;
        final Map<Cut,PrivatePool> privateInput;
        final ContextUniversalFrozenBranch.Barrier barrier;
        final boolean commonChanged,privateChanged;
        private Round(int number,Packet input,Map<Cut,PrivatePool> privateInput,ContextUniversalFrozenBranch.Barrier barrier,boolean commonChanged,boolean privateChanged) {
            this.number=number;this.input=input;this.privateInput=Collections.unmodifiableMap(new LinkedHashMap<>(privateInput));
            this.barrier=barrier;this.commonChanged=commonChanged;this.privateChanged=privateChanged;
        }
    }
    static final class Result {
        final boolean stabilized;
        final String phase,reason;
        final int completedRounds;
        final Packet input;
        final ContextUniversalFrozenBranch.Barrier output;
        final List<Round> history;
        private Result(boolean stabilized,String phase,String reason,int completedRounds,Packet input,ContextUniversalFrozenBranch.Barrier output,List<Round> history) {
            this.stabilized=stabilized;this.phase=phase;this.reason=reason;this.completedRounds=completedRounds;this.input=input;this.output=output;
            this.history=Collections.unmodifiableList(new ArrayList<>(history));
        }
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
    static Result run(Snapshot x,List<Snapshot> foreign,Packet initial,Map<Cut,PrivatePool> initialPools,
            ContextUniversalFrozenBranch.Limits limits,int maximumRounds,int maximumWork) {
        if(maximumRounds<1||maximumRounds>64||maximumWork<1)throw new IllegalArgumentException("Bounded positive pool rounds required");
        String phase="preflight";int completed=0;Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            require(x!=null&&foreign!=null&&foreign.size()<=15&&initial!=null&&initialPools!=null&&limits!=null,"Actual supplied native/common/private inputs required");
            List<Snapshot> snapshots=new ArrayList<>(foreign);Map<Cut,Snapshot> targets=new LinkedHashMap<>();
            ContextProductionFamilyAssembly.identity(x,true,work);Cut self=Cut.of(x);targets.put(self,null);
            for(Snapshot snapshot:snapshots) {
                work.run();require(snapshot!=null,"Actual foreign snapshot required");ContextProductionFamilyAssembly.identity(snapshot,false,work);
                ContextProductionFamilyAssembly.disjoint(x,snapshot,work);Cut cut=Cut.of(snapshot);
                require(!targets.containsKey(cut),"Repeated supplied foreign cut");targets.put(cut,snapshot);
            }
            List<Cut> cuts=new ArrayList<>(targets.keySet());ContextWitnessResultTransport.family(initial,cuts,maximumWork);
            Map<Cut,PrivatePool> pools=new LinkedHashMap<>(initialPools);
            for(Map.Entry<Cut,PrivatePool> entry:pools.entrySet()) {
                work.run();PrivatePool pool=entry.getValue();require(pool!=null&&targets.containsKey(entry.getKey())&&!entry.getKey().equals(self)
                    &&pool.cut.equals(entry.getKey())&&pool.common==initial&&pool.x==x&&pool.snapshot==targets.get(entry.getKey()),"Different exact initial private frame");
            }
            Packet input=initial;List<Round> history=new ArrayList<>();
            for(int number=1;number<=maximumRounds;++number) {
                work.run();phase="branches";Map<Cut,ContextUniversalFrozenBranch.Result> branches=new LinkedHashMap<>();
                // Every pass sees this same immutable common input, irrespective of execution order.
                for(Map.Entry<Cut,Snapshot> target:targets.entrySet()) {
                    work.run();ContextUniversalFrozenBranch.Result branch=ContextUniversalFrozenBranch.runWithPremises(x,target.getValue(),input,pools.get(target.getKey()),limits);
                    require(branch.produced,"Round "+number+" branch "+branch.phase+": "+branch.reason);branches.put(target.getKey(),branch);
                }
                phase="barrier";ContextUniversalFrozenBranch.Barrier barrier=ContextUniversalFrozenBranch.merge(ContextWitnessResultTransport.family(input,cuts,maximumWork),branches,maximumWork);
                require(barrier.merged,barrier.reason);phase="pool-comparison";
                boolean commonChanged=!ContextWitnessResultTransport.sameContents(input,barrier.output.packet,maximumWork),privateChanged=false;
                require(commonChanged==barrier.output.changed,"Different common change receipt");
                require(pools.keySet().equals(barrier.output.privatePools.keySet()),"Changed owning private cut inventory");
                for(Map.Entry<Cut,PrivatePool> entry:pools.entrySet()) {
                    work.run();PrivatePool next=barrier.output.privatePools.get(entry.getKey());
                    require(next.common==barrier.output.packet&&next.x==x&&next.snapshot==entry.getValue().snapshot,"Different rebased private frame");
                    privateChanged|=!ContextWitnessResultTransport.sameContents(entry.getValue().local,next.local,maximumWork);
                }
                history.add(new Round(number,input,pools,barrier,commonChanged,privateChanged));completed=number;
                if(!commonChanged&&!privateChanged&&!barrier.output.localChanged)
                    return new Result(true,"stable-positive-pools","Supplied positive projection pools unchanged after a whole checked round; symbolic obligations and full family qualification remain open",completed,initial,barrier,history);
                input=barrier.output.packet;pools=new LinkedHashMap<>(barrier.output.privatePools);
            }
            phase="round-limit";throw new IllegalStateException("Positive pool round limit; no stabilized output");
        }catch(Exception failure){return new Result(false,phase,failure.toString(),completed,initial,null,Collections.emptyList());}
    }
}
