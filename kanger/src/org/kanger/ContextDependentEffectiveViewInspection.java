/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.units.Rule;

/** Complete effective primary-view equivalence. Provider owns cold pin/recipe acquisition. */
public final class ContextDependentEffectiveViewInspection {
    private ContextDependentEffectiveViewInspection() {}
    private static void current(Mind actual,ContextProductionInventory expected,int rows)throws Exception{
        if(!expected.fingerprint.equals(ContextProductionInventory.inspect(actual,expected.context,expected.revision,expected.localX,expected.initialization,rows).fingerprint))throw new IllegalStateException("Effective initialized view changed during verification");
    }
    private static Map<String,Deque<ContextProductionInventory.Row>> rows(ContextProductionInventory inventory,Runnable work){
        if(inventory.rows.size()!=inventory.primary.size()+inventory.generated.size()
                ||(!inventory.generated.isEmpty()&&inventory.groundCacheProof()==null))
            throw new IllegalStateException("Complete effective primary replay requires independent coverage of every ground cache row; deleted rows are not covered");
        Map<String,Deque<ContextProductionInventory.Row>> result=new LinkedHashMap<>();
        for(ContextProductionInventory.Row row:inventory.primary){
            work.run();if(row.origin==null)throw new IllegalStateException("Whole effective origin required");for(int i=0;i<row.origin.length();i++)work.run();
            result.computeIfAbsent(row.origin,key->new ArrayDeque<>()).add(row);
        }
        return result;
    }
    public static ContextProductionInventory verify(Mind actual,ContextProductionInventory expected,Mind cold,ContextProductionInventory replay,int maximumRows,int maximumWork)throws Exception{
        if(actual==null||expected==null||cold==null||replay==null||actual==cold||actual.getRules()==cold.getRules())throw new IllegalArgumentException("Distinct actual and independently acquired cold effective views required");
        if(!expected.context.equals(replay.context)||expected.revision!=replay.revision||expected.localX!=replay.localX||!expected.initialization.equals(replay.initialization))throw new IllegalArgumentException("Exact physical pin and complete initialization recipe required");
        if(expected.localX||replay.localX)throw new IllegalArgumentException("Foreign physical effective views required");
        current(actual,expected,maximumRows);current(cold,replay,maximumRows);
        boolean cached=!expected.generated.isEmpty()||!replay.generated.isEmpty();
        if(cached&&(!DmzTerminalRestriction.activeNoGoods(actual).isEmpty()||!DmzTerminalRestriction.activeNoGoods(cold).isEmpty()))throw new IllegalStateException("Restricted effective cache view is outside cold equivalence scope");
        try{
            Runnable work=ContextGroundProductionRounds.budget(maximumWork);
            Map<String,Deque<ContextProductionInventory.Row>> left=rows(expected,work),right=rows(replay,work);
            if(expected.primary.size()!=replay.primary.size()||!left.keySet().equals(right.keySet()))throw new IllegalStateException("Different complete effective declarations");
            for(String origin:left.keySet()){
                ContextDependentRuntimeExecution.interrupt();Deque<ContextProductionInventory.Row> a=left.get(origin),b=right.get(origin);
                if(a.size()!=b.size())throw new IllegalStateException("Different effective original multiplicity");
                for(ContextProductionInventory.Row row:a){
                    ContextDependentRuntimeExecution.interrupt();work.run();ContextProductionInventory.Row other=b.removeFirst();
                    Rule p=(Rule)actual.getRules().get(row.nativeRule),q=(Rule)cold.getRules().get(other.nativeRule);
                    if(p==null||q==null||!origin.equals(p.getOrigin())||!origin.equals(q.getOrigin()))throw new IllegalStateException("Missing exact effective factory original");
                    boolean unit=NativeDependentProductionRows.unit(p);
                    if(unit!=NativeDependentProductionRows.unit(q))throw new IllegalStateException("Different effective original kind");
                    if(unit){
                        if(!NativePrenexUnitScope.same(NativePrenexUnitScope.observeFactoryRow(row.nativeRule,actual,work),NativePrenexUnitScope.observeFactoryRow(other.nativeRule,cold,work),work))throw new IllegalStateException("Different effective quantified original");
                    }else if(!NativeDependentProductionRows.ordinary(p,actual,work).equivalent(NativeDependentProductionRows.ordinary(q,cold,work),work))throw new IllegalStateException("Different effective whole Horn original");
                }
            }
            return expected.verifiedEffectiveReplay();
        }finally{
            current(actual,expected,maximumRows);current(cold,replay,maximumRows);
            if(cached&&(!DmzTerminalRestriction.activeNoGoods(actual).isEmpty()||!DmzTerminalRestriction.activeNoGoods(cold).isEmpty()))throw new IllegalStateException("Restricted effective cache view changed during verification");
        }
    }
}
