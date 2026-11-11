/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.ContextDependentCacheAliasInspection.Binding;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Automatic current-cause recipe diagnostic. Neither native history nor symbolic-cache admission authority. */
public final class ContextDependentCacheAncestryInspection {
    private ContextDependentCacheAncestryInspection() {}
    public enum Status {PLANNED,PARTIAL,INCOMPLETE}
    public static final class Result {
        public final Status status;public final String fingerprint,reason;
        public final Request recipe;public final List<Binding> bindings;public final Set<Long> unplanned;
        public final ContextDependentCacheAliasInspection.Result aliases;
        private Result(Status status,String fingerprint,String reason,Request recipe,List<Binding> bindings,Set<Long> unplanned,ContextDependentCacheAliasInspection.Result aliases){this.status=status;this.fingerprint=fingerprint;this.reason=reason;this.recipe=recipe;this.bindings=Collections.unmodifiableList(new ArrayList<>(bindings));this.unplanned=Collections.unmodifiableSet(new LinkedHashSet<>(unplanned));this.aliases=aliases;}
    }
    private static void need(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    private static void current(Mind mind,ContextProductionInventory inventory)throws Exception{
        need(DmzTerminalRestriction.activeNoGoods(mind).isEmpty(),"Restricted ancestry requires separate qualification");
        need(inventory.fingerprint.equals(ContextProductionInventory.inspect(mind,inventory.context,inventory.revision,inventory.localX,inventory.initialization,1000).fingerprint),"Changed native ancestry inventory");
    }
    private static final class Planner {
        final Mind mind;final Map<Long,Rule> rows=new LinkedHashMap<>();final Map<Long,Integer> products=new HashMap<>();
        final Set<Long> active=new HashSet<>(),unplanned=new LinkedHashSet<>();final List<Step> steps=new ArrayList<>();final List<Binding> bindings=new ArrayList<>();
        final Runnable work=ContextGroundProductionRounds.budget(1000000);
        Planner(Mind mind,ContextProductionInventory inventory)throws Exception{this.mind=mind;for(ContextProductionInventory.Row row:inventory.rows){need(!row.deleted&&!row.query,"Live assertion ancestry required");rows.put(row.nativeRule,(Rule)mind.getRules().get(row.nativeRule));}}
        Rule actual(IRule item)throws Exception{work.run();need(item!=null&&rows.containsKey(item.getId()),"Cause must resolve inside the exact current inventory");Rule rule=rows.get(item.getId());need(rule!=null,"Live resolved cause required");return rule;}
        ICause cause(Rule row)throws Exception{
            work.run();need(!row.getCauses().isEmpty(),"Resolved cause required");ICause first=null;long rule=-1,donor=-1;
            for(ICause cause:row.getCauses()){work.run();Rule r=actual(cause.getRule(mind));IRule d=cause.getDonor(mind);long id=d==null?-1:actual(d).getId();
                if(first==null){first=cause;rule=r.getId();donor=id;}else need(rule==r.getId()&&donor==id,"Different resolved cause alternatives require separate qualification");}
            return first;
        }
        Rule rootInstance(Rule row)throws Exception{
            if(!row.isGenerated())return null;ICause cause=cause(row);IRule donor=cause.getDonor(mind);Rule rule=actual(cause.getRule(mind));
            return donor==null&&!rule.isGenerated()&&NativeDependentProductionRows.unit(rule)?rule:null;
        }
        Donor donor(Rule row,int depth)throws Exception{
            if(!row.isGenerated()){need(NativeDependentProductionRows.unit(row),"Positive primary unit donor required");return Donor.original(row.getId());}
            Rule root=rootInstance(row);if(root!=null){unplanned.add(row.getId());return Donor.original(root.getId());}
            return Donor.product(plan(row,depth));
        }
        int plan(Rule row,int depth)throws Exception{
            ContextDependentRuntimeExecution.interrupt();work.run();Integer previous=products.get(row.getId());if(previous!=null)return previous;
            need(depth<32&&active.add(row.getId()),"Cyclic or excessive resolved ancestry");
            try{
                need(row.isGenerated()&&NativeDependentProductionRows.unit(row),"Positive generated unit projection required");ICause cause=cause(row);
                Rule rule=actual(cause.getRule(mind));need(!rule.isGenerated(),"Generated rules require separate residual ancestry qualification");
                org.kanger.ContextProductionCapture.Clause clause=NativeDependentProductionRows.ordinary(rule,mind,work);
                need(clause.literals.size()==2,"Single-body ordinary projection required");int heads=0;for(org.kanger.ContextProductionCapture.Atom atom:clause.literals)if(atom.positive)heads++;need(heads==1,"One positive projection head required");
                Rule donated=actual(cause.getDonor(mind));Donor donor=donor(donated,depth+1);
                need(steps.size()<64,"Resolved ancestry recipe bound");int index=steps.size();steps.add(new Step(rule.getId(),Collections.singletonList(donor)));products.put(row.getId(),index);bindings.add(new Binding(row.getId(),index));return index;
            }finally{active.remove(row.getId());}
        }
    }
    public static Result inspect(Mind mind,ContextProductionInventory inventory)throws Exception{
        if(mind==null||inventory==null)throw new IllegalArgumentException("Actual complete native inventory required");current(mind,inventory);
        try{try{
            Planner planner=new Planner(mind,inventory);
            // Deterministic inventory order; every generated occurrence must be classified.
            for(ContextProductionInventory.Row row:inventory.generated){Rule actual=planner.rows.get(row.nativeRule);if(planner.rootInstance(actual)!=null)planner.unplanned.add(row.nativeRule);else planner.plan(actual,0);}
            need(planner.bindings.size()+planner.unplanned.size()==inventory.generated.size(),"Complete generated occurrence classification required");
            Request recipe=new Request(new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000),1000,planner.steps);
            ContextDependentCacheAliasInspection.Result aliases=planner.bindings.isEmpty()?null:ContextDependentCacheAliasInspection.inspect(mind,inventory,recipe,planner.bindings);
            need(aliases==null||aliases.status!=ContextDependentCacheAliasInspection.Status.INCOMPLETE,"Resolved recipe cannot be independently bound to actual closed products"+(aliases==null?"":": "+aliases.reason));
            return new Result(planner.unplanned.isEmpty()?Status.PLANNED:Status.PARTIAL,inventory.fingerprint,"Current resolved causes reconstructed and typed products independently checked; root-instance coverage, primary witness anchors and native event history are not certified",recipe,planner.bindings,planner.unplanned,aliases);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){return new Result(Status.INCOMPLETE,inventory.fingerprint,failure.getMessage(),null,Collections.emptyList(),Collections.emptySet(),null);}}finally{current(mind,inventory);}
    }
}
