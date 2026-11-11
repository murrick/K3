/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentCutInspection.Cut;
import org.kanger.ContextProductionCapture.Atom;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Fresh deterministic palette acquisition before automatic model search; no admission authority. */
public final class ContextDependentAutomaticInspection {
    private ContextDependentAutomaticInspection() {}
    private static void current(Member x,List<Cut> cuts,ContextDependentReplayInspection.Policy p)throws Exception{
        ContextDependentReplayInspection.current(x.actual,x.inventory,p);
        for(Cut cut:cuts)for(Member m:cut.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,p);
    }
    private static void point(List<SemanticTermSnapshot> points,SemanticTermSnapshot value,int maximum,Runnable work)throws Exception{
        work.run();if(!SemanticTermSnapshot.isOrdinaryValue(value.materialize()))throw new IllegalStateException("Ordinary automatic palette value required");
        for(SemanticTermSnapshot old:points){work.run();if(old.semanticallyEquals(value))return;}
        if(points.size()==maximum)throw new IllegalStateException("Whole automatic palette point bound");points.add(value);
    }
    public static ContextDependentModelSearchInspection.Result inspect(Member x,List<Cut> supplied,
            ContextDependentIterationInspection.Request iteration,ContextDependentModelSearchInspection.Limits limits,
            ContextDependentRuntimeContinuationInspection.Projection projection)throws Exception{
        if(x==null||supplied==null||supplied.contains(null)||iteration==null||limits==null||projection==null)throw new IllegalArgumentException("Actual complete automatic family and limits required");
        List<Cut> cuts=new ArrayList<>(supplied);current(x,cuts,iteration.policy);
        try{
            Runnable work=ContextGroundProductionRounds.budget(limits.schemaWork);
            List<Member> members=new ArrayList<>();members.add(x);for(Cut cut:cuts)members.addAll(cut.members);
            Set<UUID> identities=new HashSet<>();List<SemanticTermSnapshot> points=new ArrayList<>();
            for(Member member:members){
                ContextDependentRuntimeExecution.interrupt();work.run();
                if(!identities.add(member.inventory.context))throw new IllegalStateException("Distinct physical automatic palette members required");
                if(!member.inventory.initialization.isEmpty()||member.inventory.rows.size()!=member.inventory.primary.size())throw new IllegalStateException("Whole primary-only automatic palette view required");
                for(ContextProductionInventory.Row row:member.inventory.primary){
                    ContextDependentRuntimeExecution.interrupt();work.run();Rule rule=(Rule)member.actual.getRules().get(row.nativeRule);
                    if(rule==null||!Objects.equals(rule.getOrigin(),row.origin))throw new IllegalStateException("Exact actual palette original required");
                    if(NativeDependentProductionRows.unit(rule)){
                        NativePrenexUnitScope prefix=NativePrenexUnitScope.observeFactoryRow(row.nativeRule,member.actual,work);
                        for(NativePrenexUnitScope.Slot slot:prefix.slots){work.run();if(slot.binder<0)point(points,slot.value,limits.cells,work);}
                    }else{
                        Clause clause=NativeDependentProductionRows.ordinary(rule,member.actual,work);
                        for(Atom atom:clause.literals)for(ContextProductionCapture.Slot slot:atom.arguments){work.run();if(slot.variable<0)point(points,slot.value,limits.cells,work);}
                    }
                }
            }
            // At most points.size() distinct strings can be occupied: no randomness or compiler writes.
            SemanticTermSnapshot other=null;
            for(int n=0;n<=points.size();n++){
                ContextDependentRuntimeExecution.interrupt();work.run();
                Term detached=new Term();
                detached.setPersistentState(org.kanger.enums.DataType.STRING,"__kanger_all_other_"+n,0,0,-1,-1,false);
                SemanticTermSnapshot candidate=SemanticTermSnapshot.capture(detached);
                boolean present=false;for(SemanticTermSnapshot value:points){work.run();if(candidate.semanticallyEquals(value)){present=true;break;}}
                if(!present){other=candidate;break;}
            }
            if(other==null)throw new IllegalStateException("Fresh automatic ALL-other representative required");
            // Search re-captures the schema and all receiving obligations; the palette is not a receipt.
            return ContextDependentModelSearchInspection.inspect(x,cuts,new ContextDependentModelSearchInspection.Request(iteration,Collections.emptyList(),other,limits,projection));
        }finally{current(x,cuts,iteration.policy);}
    }
}
