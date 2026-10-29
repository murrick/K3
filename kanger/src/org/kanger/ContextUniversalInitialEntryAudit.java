/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalInitialEntry.Entry;
import org.kanger.ContextWitnessResultTransport.*;

/** Independent bounded native initial replay. A receipt is not live-family/admission authority. */
final class ContextUniversalInitialEntryAudit {
    static final class Receipt {
        final Entry entry;final ContextUniversalFrozenBranch.Limits limits;
        private Receipt(Entry entry,ContextUniversalFrozenBranch.Limits limits){this.entry=entry;this.limits=limits;}
    }
    static final class Inspection {
        final boolean audited;final String phase,reason;final Receipt output;
        private Inspection(boolean audited,String phase,String reason,Receipt output){this.audited=audited;this.phase=phase;this.reason=reason;this.output=output;}
    }
    static final class SessionInspection {
        final Inspection inspection;final ContextUniversalValidationSession.Metrics metrics;
        private SessionInspection(Inspection inspection,ContextUniversalValidationSession.Metrics metrics){this.inspection=inspection;this.metrics=metrics;}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void unmarked(Packet p){require(p!=null&&p.basis==null&&p.producers.isEmpty()&&!p.observedStep&&p.emptyCut==null&&p.coveredCut==null&&p.roundCut==null&&p.privateTarget==null&&p.emptyTarget==null,"Initial packets must not have coverage/STEP stamps");}
    private static void declarations(PrivatePool actual,PrivatePool expected,Runnable work){
        require(actual.sources.equals(expected.sources)&&actual.declarations.size()==expected.declarations.size(),"Different original private Source/declaration inventory");
        for(int i=0;i<expected.declarations.size();++i){work.run();ContextProductionInputs.Declaration a=actual.declarations.get(i),b=expected.declarations.get(i);require(a.source.equals(b.source)&&a.statement.equals(b.statement),"Different original private declaration");}
    }
    private static void rows(ContextUniversalNativeRows.Result actual,ContextUniversalNativeRows.Result expected,Runnable work){
        require(actual!=null&&actual.classified&&actual.withinShape&&actual.x==expected.x&&actual.foreign==expected.foreign&&actual.parent.equals(expected.parent)&&actual.target.equals(expected.target)&&actual.sources.equals(expected.sources)&&actual.rows.size()==expected.rows.size(),"Different complete initial native row frame");
        for(int i=0;i<expected.rows.size();++i){work.run();ContextUniversalNativeRows.Row a=actual.rows.get(i),b=expected.rows.get(i);require(a.original==b.original&&a.withinShape&&a.parts.size()==b.parts.size(),"Different whole original initial row");for(int j=0;j<b.parts.size();++j){work.run();ContextUniversalNativeRows.Part p=a.parts.get(j),q=b.parts.get(j);require(p.clause==q.clause&&p.clauseIndex==q.clauseIndex&&p.role==q.role,"Different original initial part/index/role");}}
    }
    /** Fresh lexical audit scope. Existing replay operations retain their own original work budgets. */
    static SessionInspection inspectWithValidationReuse(Entry e,ContextUniversalFrozenBranch.Limits limits,int maximumWork,int capacity){
        ContextUniversalValidationSession.Scope scope=ContextUniversalValidationSession.open(maximumWork,capacity);Inspection result;
        try{result=inspect(e,limits,scope.work,capacity);}finally{scope.close();}
        return new SessionInspection(result,scope.metrics());
    }
    private static Inspection inspect(Entry e,ContextUniversalFrozenBranch.Limits limits,Runnable work,int capacity){
        String phase="topology";
        try {
            require(e!=null&&e.x!=null&&e.foreign!=null&&e.foreign.size()<=15&&e.common!=null&&e.pools!=null&&e.checks!=null&&e.units!=null&&e.compounds!=null&&limits!=null,"Actual complete bounded initial Entry required");
            Snapshot x=e.x;ContextProductionFamilyAssembly.identity(x,true,work);Set<UUID> physical=new HashSet<>();ContextProductionFamilyAssembly.claimContexts(x,physical,work);
            Map<Cut,Snapshot> targets=new LinkedHashMap<>();targets.put(Cut.of(x),null);
            for(Snapshot f:e.foreign){work.run();require(f!=null,"Actual foreign snapshot required");ContextProductionFamilyAssembly.identity(f,false,work);ContextProductionFamilyAssembly.claimContexts(f,physical,work);require(!targets.containsKey(Cut.of(f)),"Repeated initial cut");targets.put(Cut.of(f),f);}
            require(e.checks.keySet().equals(targets.keySet())&&e.units.x==x&&e.compounds.x==x&&e.units.common==e.common&&e.compounds.common==e.common&&e.units.checks.equals(e.checks)&&e.compounds.checks.equals(e.checks),"Different complete initial owning ledger");
            require(e.units.prior==null&&e.units.barrier==null&&e.compounds.prior==null&&e.compounds.barrier==null,"Rebased continuation is not a primary initial entry");
            require(e.compounds.maximumClauses==limits.clauses&&e.compounds.maximumAlternatives==limits.alternatives&&e.compounds.maximumDepth==limits.factDepth,"Different initial carrier policy");
            phase="primary-replay";unmarked(e.common);ContextWitnessResultTransport.Result seeds=ContextWitnessResultTransport.primarySeeds(x,limits.work);require(seeds.exported,seeds.reason);require(ContextWitnessResultTransport.sameContents(seeds.packet,e.common,limits.work),"Initial common is not exactly original primary seeds");
            phase="private-replay";Set<Cut> expectedPools=new LinkedHashSet<>();
            for(Snapshot f:e.foreign){boolean witness=false;for(ContextProductionNativeBinding.Entry original:f.primary){work.run();witness|=original.bundle!=null;}if(!witness)continue;Cut cut=Cut.of(f);expectedPools.add(cut);PrivatePool actual=e.pools.get(cut);require(actual!=null&&actual.common==e.common&&actual.x==x&&actual.snapshot==f&&actual.cut.equals(cut),"Different actual initial private frame");unmarked(actual.local);PrivatePool fresh=ContextWitnessResultTransport.privatePoolSigned(e.common,x,f,limits.work);declarations(actual,fresh,work);require(ContextWitnessResultTransport.sameContents(actual.local,fresh.local,limits.work),"Initial private pool differs from exact original materialization");}
            require(e.pools.keySet().equals(expectedPools),"Different original witness-pool inventory");
            phase="ordinary-replay";Map<Cut,ContextUniversalFrozenConstraints.Result> replay=new LinkedHashMap<>();
            for(Map.Entry<Cut,Snapshot> target:targets.entrySet()){
                work.run();ContextUniversalNativeRows.Result nativeRows=ContextUniversalNativeRows.inspect(x,target.getValue(),64,limits.work);require(nativeRows.classified&&nativeRows.withinShape,nativeRows.reason);
                ContextUniversalFrozenConstraints.Result actual=e.checks.get(target.getKey());require(actual!=null&&actual.closed&&!actual.xRefuted&&!actual.foreignRefuted&&actual.emptyProofs.isEmpty()&&actual.common==e.common&&actual.pool==e.pools.get(target.getKey())&&actual.exchange==null&&actual.compounds==null&&actual.groupedBase==null&&actual.groupedInputs.isEmpty(),"Different consistent primary initial check");rows(actual.input,nativeRows,work);ContextUniversalFrozenConstraints.check(actual,limits.proofDepth,work);
                ContextUniversalFrozenConstraints.Result fresh=ContextUniversalFrozenConstraints.closeSignedInstantiatedUnits(nativeRows,e.common,e.pools.get(target.getKey()),null,limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work);
                require(fresh.closed&&!fresh.xRefuted&&!fresh.foreignRefuted&&ContextUniversalGroupedLocalRounds.sameContents(actual,fresh,work),"Initial ordinary proof inventory differs from independent native replay");replay.put(target.getKey(),fresh);
            }
            phase="carrier-replay";ContextUniversalSignedUnitExchange.check(e.units,x,e.common,limits.factDepth,work);ContextUniversalCompoundExchange.check(e.compounds,x,e.common,limits.factDepth,work);List<Cut> cuts=new ArrayList<>(targets.keySet());
            ContextUniversalSignedUnitExchange.Collection u=ContextUniversalSignedUnitExchange.collectWithValidationReuse(x,e.common,cuts,replay,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;
            ContextUniversalCompoundExchange.Collection c=ContextUniversalCompoundExchange.collectWithValidationReuse(x,e.common,cuts,replay,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;
            require(u.collected&&c.collected&&ContextUniversalSignedUnitExchange.sameContents(u.output,e.units,limits.work)&&ContextUniversalCompoundExchange.sameContents(c.output,e.compounds,limits.work),"Different independently replayed initial carrier Source minima");
            return new Inspection(true,"initial-entry-replayed","Bounded exact primary/private/ordinary/carrier replay; native full-family/admission gates remain open",new Receipt(e,limits));
        }catch(Exception failure){return new Inspection(false,phase,failure.toString(),null);}
    }
    static ContextUniversalValidationSession.Metrics checkWithValidationReuse(Receipt receipt,int maximumWork,int capacity){require(receipt!=null,"Actual initial-entry receipt required");SessionInspection r=inspectWithValidationReuse(receipt.entry,receipt.limits,maximumWork,capacity);require(r.inspection.audited,r.inspection.phase+": "+r.inspection.reason);return r.metrics;}
}
