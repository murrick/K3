/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.units.Rule;

/** Read-only primary equivalence between independently checked symbolic caches. Provider acquires the physical pin. */
public final class ContextDependentSymbolicCacheEquivalence {
    private ContextDependentSymbolicCacheEquivalence() {}
    private static void need(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    private static Map<String,Deque<ContextProductionInventory.Row>> rows(ContextProductionInventory inventory,Runnable work){Map<String,Deque<ContextProductionInventory.Row>> result=new LinkedHashMap<>();for(ContextProductionInventory.Row row:inventory.primary){work.run();need(row.origin!=null,"Whole primary declaration required for symbolic cold equivalence");result.computeIfAbsent(row.origin,key->new ArrayDeque<>()).add(row);}return result;}
    public static void verify(Mind actual,ContextDependentSymbolicCacheSnapshot.Snapshot warm,Mind cold,ContextDependentSymbolicCacheSnapshot.Snapshot replay)throws Exception{
        if(actual==null||cold==null||warm==null||replay==null||actual==cold||actual.getRules()==cold.getRules())throw new IllegalArgumentException("Independent runtimes with fresh symbolic-cache snapshots required");
        ContextProductionInventory left=warm.inventory,right=replay.inventory;need(left.context.equals(right.context)&&left.revision==right.revision&&left.localX==right.localX&&left.initialization.equals(right.initialization),"Same declared physical pin, role and recipe required for cold comparison");warm.requireCurrent(actual,left);replay.requireCurrent(cold,right);
        try{
            Runnable work=ContextGroundProductionRounds.budget(64000000);Map<String,Deque<ContextProductionInventory.Row>> a=rows(left,work),b=rows(right,work);need(left.primary.size()==right.primary.size()&&a.keySet().equals(b.keySet()),"Different complete warm/cold primary declarations");
            for(String origin:a.keySet()){need(a.get(origin).size()==b.get(origin).size(),"Different warm/cold primary multiplicity");for(ContextProductionInventory.Row row:a.get(origin)){ContextDependentRuntimeExecution.interrupt();work.run();ContextProductionInventory.Row other=b.get(origin).removeFirst();Rule p=(Rule)actual.getRules().get(row.nativeRule),q=(Rule)cold.getRules().get(other.nativeRule);need(p!=null&&q!=null&&origin.equals(p.getOrigin())&&origin.equals(q.getOrigin()),"Missing actual warm/cold factory original");boolean unit=NativeDependentProductionRows.unit(p);need(unit==NativeDependentProductionRows.unit(q),"Different warm/cold primary kind");if(unit)need(NativePrenexUnitScope.same(NativePrenexUnitScope.observeFactoryRow(row.nativeRule,actual,work),NativePrenexUnitScope.observeFactoryRow(other.nativeRule,cold,work),work),"Different warm/cold native primary prefix");else need(NativeDependentProductionRows.ordinary(p,actual,work).equivalent(NativeDependentProductionRows.ordinary(q,cold,work),work),"Different warm/cold whole native Horn clause");}}
        }finally{warm.requireCurrent(actual,left);replay.requireCurrent(cold,right);}
    }
}
