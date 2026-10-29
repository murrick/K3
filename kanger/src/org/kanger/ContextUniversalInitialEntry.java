/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextWitnessResultTransport.*;

/** Detached, explicitly requested native initial assembly; no family admission or writer authority. */
final class ContextUniversalInitialEntry {
    enum Status { READY, REFUTED, INCOMPLETE }
    static final class Entry {
        final Snapshot x;
        final List<Snapshot> foreign;
        final Packet common;
        final Map<Cut,PrivatePool> pools;
        final Map<Cut,ContextUniversalFrozenConstraints.Result> checks;
        final ContextUniversalSignedUnitExchange.Exchange units;
        final ContextUniversalCompoundExchange.Exchange compounds;
        private Entry(Snapshot x,List<Snapshot> foreign,Packet common,Map<Cut,PrivatePool> pools,
                Map<Cut,ContextUniversalFrozenConstraints.Result> checks,
                ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds) {
            this.x=x;this.foreign=Collections.unmodifiableList(new ArrayList<>(foreign));this.common=common;
            this.pools=Collections.unmodifiableMap(new LinkedHashMap<>(pools));
            this.checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks));this.units=units;this.compounds=compounds;
        }
    }
    static final class Rejection {
        final boolean xRefuted,foreignRefuted;
        final List<Proof> proofs;
        private Rejection(ContextUniversalFrozenConstraints.Result checked,int work) {
            xRefuted=checked.xRefuted;foreignRefuted=checked.foreignRefuted;
            proofs=ContextUniversalFrozenConstraints.rejectionEvidence(checked,work);
        }
    }
    static final class Result {
        final Status status;
        final String phase,reason;
        final Entry entry;
        final Map<Cut,Rejection> refutations;
        private Result(Status status,String phase,String reason,Entry entry,Map<Cut,Rejection> refutations) {
            this.status=status;this.phase=phase;this.reason=reason;this.entry=entry;
            this.refutations=Collections.unmodifiableMap(new LinkedHashMap<>(refutations));
        }
    }
    private static void require(boolean valid,String why){if(!valid)throw new IllegalStateException(why);}
    /**
     * One cumulative orchestration/authentication budget. Each existing inspector, seed/pool,
     * closure, rejection and carrier operation retains its OWN original limits.work budget.
     * No aggregate child-work bound, continuation stability, or minimum/family coverage is claimed.
     */
    static Result assemble(Snapshot x,List<Snapshot> foreign,ContextUniversalFrozenBranch.Limits limits,int capacity) {
        String phase="preflight";
        try {
            require(x!=null&&foreign!=null&&foreign.size()<=15&&limits!=null,"Actual bounded native family required");
            require(capacity>=1&&capacity<=4096,"Bounded validation capacity required");
            Runnable work=ContextGroundProductionRounds.budget(limits.work);
            List<Snapshot> supplied=new ArrayList<>(foreign);
            Map<Cut,Snapshot> targets=new LinkedHashMap<>();
            ContextProductionFamilyAssembly.identity(x,true,work);
            Set<UUID> contexts=new HashSet<>();ContextProductionFamilyAssembly.claimContexts(x,contexts,work);
            targets.put(Cut.of(x),null);
            for(Snapshot target:supplied) {
                work.run();require(target!=null,"Actual foreign snapshot required");
                ContextProductionFamilyAssembly.identity(target,false,work);
                ContextProductionFamilyAssembly.claimContexts(target,contexts,work);
                require(!targets.containsKey(Cut.of(target)),"Repeated initial cut");targets.put(Cut.of(target),target);
            }
            phase="whole-rows";
            Map<Cut,ContextUniversalNativeRows.Result> rows=new LinkedHashMap<>();
            for(Map.Entry<Cut,Snapshot> target:targets.entrySet()) {
                work.run();ContextUniversalNativeRows.Result r=ContextUniversalNativeRows.inspect(x,target.getValue(),64,limits.work);
                require(r.classified,r.reason);require(r.withinShape,"Unsupported whole original initial row");rows.put(target.getKey(),r);
            }
            phase="primary-seeds";
            ContextWitnessResultTransport.Result seeds=ContextWitnessResultTransport.primarySeeds(x,limits.work);
            require(seeds.exported,seeds.reason);Packet common=seeds.packet;
            phase="private-readiness";Map<Cut,PrivatePool> pools=new LinkedHashMap<>();
            for(Snapshot target:supplied) {
                boolean witness=false;
                for(ContextProductionNativeBinding.Entry original:target.primary){work.run();witness|=original.bundle!=null;}
                // Ground foreign facts are already native closure inputs; only witnesses require a pool.
                if(witness)pools.put(Cut.of(target),ContextWitnessResultTransport.privatePoolSigned(common,x,target,limits.work));
            }
            phase="checks";Map<Cut,ContextUniversalFrozenConstraints.Result> checks=new LinkedHashMap<>();
            for(Map.Entry<Cut,ContextUniversalNativeRows.Result> row:rows.entrySet()) {
                work.run();ContextUniversalFrozenConstraints.Result r=ContextUniversalFrozenConstraints.closeSignedInstantiatedUnits(
                        row.getValue(),common,pools.get(row.getKey()),null,limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work);
                require(r.closed,r.reason);checks.put(row.getKey(),r);
            }
            // Authenticate EVERY completed cut before exposing even a genuine earlier contradiction.
            phase="authentication";
            for(ContextUniversalFrozenConstraints.Result r:checks.values())ContextUniversalFrozenConstraints.check(r,limits.proofDepth,work);
            phase="rejection-evidence";Map<Cut,Rejection> refutations=new LinkedHashMap<>();
            for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> checked:checks.entrySet()) {
                work.run();ContextUniversalFrozenConstraints.Result r=checked.getValue();
                if(r.xRefuted||r.foreignRefuted)refutations.put(checked.getKey(),new Rejection(r,limits.work));
            }
            if(!refutations.isEmpty())return new Result(Status.REFUTED,phase,"All supplied initial cuts checked; sourced contradiction",null,refutations);
            List<Cut> cuts=new ArrayList<>(targets.keySet());
            phase="unit-collection";
            ContextUniversalSignedUnitExchange.Collection u=ContextUniversalSignedUnitExchange.collectWithValidationReuse(
                    x,common,cuts,checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;
            require(u.collected,u.reason);
            phase="compound-collection";
            ContextUniversalCompoundExchange.Collection c=ContextUniversalCompoundExchange.collectWithValidationReuse(
                    x,common,cuts,checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;
            require(c.collected,c.reason);
            require(u.output.checks.equals(checks)&&c.output.checks.equals(checks)&&u.output.barrier==c.output.barrier,
                    "Initial carriers must share actual owning checks/barrier");
            return new Result(Status.READY,"complete-entry","Detached initial assembly only; continuation/family gates remain open",
                    new Entry(x,supplied,common,pools,checks,u.output,c.output),Collections.emptyMap());
        } catch(Exception failure) {
            return new Result(Status.INCOMPLETE,phase,failure.toString(),null,Collections.emptyMap());
        }
    }
}
