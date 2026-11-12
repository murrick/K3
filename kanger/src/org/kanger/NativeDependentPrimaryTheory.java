/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.units.Rule;

/** Fresh semantic replay of actual primary declarations; the full physical inventory remains guarded. */
final class NativeDependentPrimaryTheory {
    private static final ContextDependentReplayInspection.Policy POLICY=new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000);
    final Mind copy;final ContextProductionInventory inventory;final Map<Long,Long> ids;
    private NativeDependentPrimaryTheory(Mind copy,ContextProductionInventory inventory,Map<Long,Long> ids){this.copy=copy;this.inventory=inventory;this.ids=Collections.unmodifiableMap(new LinkedHashMap<>(ids));}
    /** Full fingerprint remains a guard. Only selected actual declarations are copied; generated data is never input knowledge. */
    static NativeDependentPrimaryTheory rebuild(Mind actual,ContextProductionInventory expected,Set<Long> selected)throws Exception{
        if(!DmzTerminalRestriction.activeNoGoods(actual).isEmpty())throw new IllegalStateException("Restricted branches require separate primary qualification");
        ContextDependentReplayInspection.currentMember(actual,expected,POLICY);User user=new User();Mind copy=new Mind(user);user.setCurrentMind(copy);Map<Long,Long> ids=new LinkedHashMap<>();Runnable work=ContextGroundProductionRounds.budget(POLICY.work);
        try{
            for(ContextProductionInventory.Row row:expected.primary){
                ContextDependentRuntimeExecution.interrupt();work.run();if(selected!=null&&!selected.contains(row.nativeRule))continue;
                Rule nativeRule=(Rule)actual.getRules().get(row.nativeRule),replayed=(Rule)copy.compileLineForReplay(row.origin);
                if(nativeRule==null||replayed==null)throw new IllegalStateException("Whole native chain declaration required");
                if(NativeDependentProductionRows.unit(nativeRule)){
                    if(!NativePrenexUnitScope.same(NativePrenexUnitScope.observeFactoryRow(row.nativeRule,actual,work),NativePrenexUnitScope.observe(replayed,copy,work),work))throw new IllegalStateException("Chain declaration/native prefix differs");
                }else{
                    Clause a=NativeDependentProductionRows.ordinary(nativeRule,actual,work),b=NativeDependentProductionRows.ordinary(replayed,copy,work);
                    if(!a.equivalent(b,work))throw new IllegalStateException("Chain declaration/native Horn shape differs");
                }
                ids.put(row.nativeRule,replayed.getId());
            }
            if(selected!=null&&!ids.keySet().equals(selected))throw new IllegalStateException("Every selected native chain original required");
            return new NativeDependentPrimaryTheory(copy,ContextProductionInventory.inspect(copy,expected.context,0,true,Collections.emptyList(),POLICY.rows),ids);
        }finally{ContextDependentReplayInspection.currentMember(actual,expected,POLICY);}
    }
}
