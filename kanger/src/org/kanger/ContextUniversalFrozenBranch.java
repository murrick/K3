/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** One owning positive branch pipeline and supplied-family product barrier, never full family qualification. */
final class ContextUniversalFrozenBranch {
    static final class Limits {
        final int rounds,proofDepth,factDepth,clauses,alternatives,work,events,receipts;
        Limits(int rounds,int proofDepth,int factDepth,int clauses,int alternatives,int work,int events,int receipts) {
            if(rounds<1||rounds>64||proofDepth<0||proofDepth>64||factDepth<0||factDepth>64||clauses<1||clauses>512
                    ||alternatives<1||alternatives>256||work<1||events<1||receipts<1||receipts>65536)
                throw new IllegalArgumentException("Bounded positive branch phases required");
            this.rounds=rounds;this.proofDepth=proofDepth;this.factDepth=factDepth;this.clauses=clauses;
            this.alternatives=alternatives;this.work=work;this.events=events;this.receipts=receipts;
        }
    }
    static final class Result {
        final boolean produced;
        final String phase,reason;
        final Snapshot x,foreign;
        final Packet common;
        final PrivatePool pool;
        final Limits limits;
        /** Available only after every phase succeeded, including bidirectional product audit. */
        final ContextUniversalFrozenProducts.Result products;
        final ContextUniversalFrozenPremises.Result premises;
        private Result(boolean produced,String phase,String reason,Snapshot x,Snapshot foreign,Packet common,PrivatePool pool,Limits limits,ContextUniversalFrozenProducts.Result products,ContextUniversalFrozenPremises.Result premises) {
            this.produced=produced;this.phase=phase;this.reason=reason;this.x=x;this.foreign=foreign;
            this.common=common;this.pool=pool;this.limits=limits;this.products=products;this.premises=premises;
        }
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
    static Result run(Snapshot x,Snapshot foreign,Packet common,Limits limits){return run(x,foreign,common,null,limits);}
    static Result runPrivate(PrivatePool pool,Limits limits){return run(pool==null?null:pool.x,pool==null?null:pool.snapshot,pool==null?null:pool.common,pool,limits);}
    static Result run(Snapshot x,Snapshot foreign,Packet common,PrivatePool pool,Limits limits) {
        return run(x,foreign,common,pool,limits,false);
    }
    static Result runWithPremises(Snapshot x,Snapshot foreign,Packet common,PrivatePool pool,Limits limits) {
        return run(x,foreign,common,pool,limits,true);
    }
    private static Result run(Snapshot x,Snapshot foreign,Packet common,PrivatePool pool,Limits limits,boolean withPremises) {
        String phase="preflight";
        try {
            require(limits!=null&&x!=null&&common!=null,"Actual native/common inputs and phase limits required");
            Runnable work=ContextGroundProductionRounds.budget(limits.work);
            ContextProductionFamilyAssembly.identity(x,true,work);if(foreign!=null){ContextProductionFamilyAssembly.identity(foreign,false,work);ContextProductionFamilyAssembly.disjoint(x,foreign,work);}
            List<Cut> cuts=foreign==null?Collections.singletonList(Cut.of(x)):Arrays.asList(Cut.of(x),Cut.of(foreign));
            ContextWitnessResultTransport.family(common,cuts,limits.work);
            if(pool!=null)require(pool.common==common&&pool.x==x&&pool.snapshot==foreign,"Different exact private native/common frame");
            phase="schemas";
            ContextUniversalUnitClosure.Result schemas=ContextUniversalUnitClosure.close(x,foreign,limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work);
            require(schemas.unitClosed,schemas.reason);
            phase="closure";
            ContextUniversalFrozenClosure.Result typed=pool==null?ContextUniversalFrozenClosure.close(schemas,common,limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work)
                    :ContextUniversalFrozenClosure.closePrivate(schemas,pool,limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work);
            require(typed.closed,typed.reason);
            phase="coverage";
            ContextUniversalFrozenCoverage.Result coverage=ContextUniversalFrozenCoverage.inspect(typed,x,foreign,limits.proofDepth,limits.receipts,limits.work);
            require(coverage.covered,coverage.reason);
            phase="replay";
            ContextUniversalFrozenReplay.Result replay=ContextUniversalFrozenReplay.replay(typed,limits.proofDepth,limits.work,limits.events);
            require(replay.bound,replay.reason);
            phase="units";
            ContextUniversalFrozenUnits.Result units=ContextUniversalFrozenUnits.project(replay,limits.proofDepth,limits.receipts,limits.work);
            require(units.projected,units.reason);
            phase="events";
            ContextUniversalFrozenEvents.Result events=ContextUniversalFrozenEvents.reconstruct(units,limits.proofDepth,limits.receipts,limits.work);
            require(events.reconstructed,events.reason);
            ContextUniversalFrozenPremises.Result premises=null;
            if(withPremises) {
                phase="premises";premises=ContextUniversalFrozenPremises.prepare(units,limits.proofDepth,limits.receipts,limits.work);
                require(premises.prepared,premises.reason);
            }
            phase="transfer";
            TypedTransfer transfer=premises==null?(pool==null?ContextWitnessResultTransport.transferTyped(events,common,limits.factDepth,limits.work)
                    :ContextWitnessResultTransport.transferTypedPrivate(events,pool,limits.factDepth,limits.work))
                    :(pool==null?ContextWitnessResultTransport.transferTypedInstances(events,premises.instances,common,limits.factDepth,limits.work)
                    :ContextWitnessResultTransport.transferTypedInstancesPrivate(events,premises.instances,pool,limits.factDepth,limits.work));
            require(transfer.transferred,transfer.reason);
            phase="products";
            ContextUniversalFrozenProducts.Result products=ContextUniversalFrozenProducts.bind(coverage,transfer,limits.receipts,limits.work);
            require(products.bound,products.reason);
            return new Result(true,phase,"Owning positive projection and observed products verified; complete instantiation and family qualification remain open",x,foreign,common,pool,limits,products,premises);
        }catch(Exception failure){return new Result(false,phase,failure.toString(),x,foreign,common,pool,limits,null,null);}
    }
    static final class Barrier {
        final boolean merged;
        final String reason;
        final TypedBarrier output;
        final Map<Cut,Result> branches;
        private Barrier(boolean merged,String reason,TypedBarrier output,Map<Cut,Result> branches) {
            this.merged=merged;this.reason=reason;this.output=output;this.branches=Collections.unmodifiableMap(new LinkedHashMap<>(branches));
        }
    }
    static Barrier merge(Family family,Map<Cut,Result> outputs,int maximumWork) {
        if(maximumWork<1)throw new IllegalArgumentException("Bounded owning branch barrier required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            require(family!=null&&outputs!=null&&outputs.keySet().equals(new HashSet<>(family.expected)),"Incomplete supplied owning family");
            Map<Cut,Result> branches=new LinkedHashMap<>();Map<Cut,TypedTransfer> transfers=new LinkedHashMap<>();Snapshot x=null;boolean self=false;
            for(Cut cut:family.expected) {
                work.run();Result branch=outputs.get(cut);
                require(branch!=null&&branch.produced&&branch.products!=null&&branch.products.bound,"Failed or missing owning branch pipeline");
                require(branch.common==family.input&&cut.equals(branch.products.transfer.target),"Different owning branch/common barrier");
                if(x==null)x=branch.x;else require(x==branch.x,"Different actual shared X snapshot");
                if(cut.localX){require(!self&&branch.foreign==null&&branch.pool==null,"Invalid self-X branch");self=true;}
                else require(branch.foreign!=null&&cut.equals(Cut.of(branch.foreign)),"Different native foreign cut");
                branches.put(cut,branch);transfers.put(cut,branch.products.transfer);
            }
            require(self&&x!=null&&family.expected.contains(Cut.of(x)),"Owning supplied barrier requires exact self-X pipeline");
            TypedBarrier merged=ContextWitnessResultTransport.mergeTyped(family,transfers,maximumWork);require(merged.merged,merged.reason);
            return new Barrier(true,"All supplied positive branch pipelines collected before typed merge/private rebase; full family qualification remains open",merged,branches);
        }catch(Exception failure){return new Barrier(false,failure.toString(),null,Collections.emptyMap());}
    }
}
