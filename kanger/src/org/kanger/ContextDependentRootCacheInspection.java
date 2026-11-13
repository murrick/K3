/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.units.*;
import org.kanger.enums.ArgumentType;

/** Independent current-primary instantiation and structural witness-anchor diagnostic. No cache admission authority. */
public final class ContextDependentRootCacheInspection {
    private ContextDependentRootCacheInspection() {}
    public enum Status {INSPECTED,INCOMPLETE}
    public static final class Call {
        public final long root;public final int binder;public final List<SemanticTermSnapshot> parameters;
        private Call(long root,int binder,List<SemanticTermSnapshot> parameters){this.root=root;this.binder=binder;this.parameters=Collections.unmodifiableList(new ArrayList<>(parameters));}
        private boolean same(Call other,Runnable work){work.run();if(root!=other.root||binder!=other.binder||parameters.size()!=other.parameters.size())return false;for(int i=0;i<parameters.size();i++){work.run();if(!parameters.get(i).semanticallyEquals(other.parameters.get(i)))return false;}return true;}
    }
    public static final class Occurrence {
        public final long row,symbol;public final int slot;public final boolean primaryAnchor;public final Call call;
        private Occurrence(long row,int slot,long symbol,boolean primaryAnchor,Call call){this.row=row;this.slot=slot;this.symbol=symbol;this.primaryAnchor=primaryAnchor;this.call=call;}
    }
    public static final class Result {
        public final Status status;public final String reason,fingerprint;public final ContextDependentCacheAliasInspection.Status aliases;
        public final Map<Long,Long> instances;public final Set<Long> remaining;public final List<Occurrence> occurrences;
        private Result(Status status,String reason,String fingerprint,ContextDependentCacheAliasInspection.Status aliases,Map<Long,Long> instances,Set<Long> remaining,List<Occurrence> occurrences){this.status=status;this.reason=reason;this.fingerprint=fingerprint;this.aliases=aliases;this.instances=Collections.unmodifiableMap(new LinkedHashMap<>(instances));this.remaining=Collections.unmodifiableSet(new LinkedHashSet<>(remaining));this.occurrences=Collections.unmodifiableList(new ArrayList<>(occurrences));}
    }
    private static void need(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    private static void current(Mind mind,ContextProductionInventory inventory)throws Exception{need(DmzTerminalRestriction.activeNoGoods(mind).isEmpty(),"Restricted root cache requires separate qualification");need(inventory.fingerprint.equals(ContextProductionInventory.inspect(mind,inventory.context,inventory.revision,inventory.localX,inventory.initialization,1000).fingerprint),"Changed root-cache inventory");}
    private static Rule source(Mind mind,Rule generated,Set<Long> primary,Runnable work)throws Exception{
        need(!generated.getCauses().isEmpty(),"Generated root candidate has no current cause");Long selected=null;boolean other=false;
        for(ICause cause:generated.getCauses()){work.run();IRule producer=cause.getRule(mind);need(producer!=null,"Unresolved current producer");Rule actual=(Rule)mind.getRules().get(producer.getId());need(actual!=null,"Current producer required");
            if(primary.contains(actual.getId())&&NativeDependentProductionRows.unit(actual)&&cause.getDonor(mind)==null){if(selected==null)selected=actual.getId();else need(selected==actual.getId(),"Different primary root alternatives require separate qualification");}else other=true;
        }
        need(selected==null||!other,"Mixed primary-instance and projection causes require separate qualification");return selected==null?null:(Rule)mind.getRules().get(selected);
    }
    private static List<Occurrence> instance(Mind mind,Rule row,NativePrenexUnitScope root,boolean reverse,Runnable work)throws Exception{
        need(NativeDependentProductionRows.unit(row),"Positive single native root instance required");Domain domain=row.getDomain();need(root.predicate.equals(domain.getPredicate(mind).getName(mind))&&domain.getRange()==root.slots.size(),"Exact root-instance predicate and arity required");
        Map<Integer,SemanticTermSnapshot> values=new HashMap<>();
        // Instantiate universal binders before constructing any dependent call.
        for(int offset=0;offset<root.slots.size();offset++){work.run();int i=reverse?root.slots.size()-1-offset:offset;NativePrenexUnitScope.Slot slot=root.slots.get(i);IArgument argument=domain.getArguments().get(i);need(argument.getType()==ArgumentType.TERM,"Closed flat root-instance slot required");ITerm value=argument.getValue(mind);
            if(slot.binder<0){need(SemanticTermSnapshot.isOrdinaryValue(value)&&slot.value.semanticallyEquals(SemanticTermSnapshot.capture(value)),"Fixed root value differs");}
            else if(root.binders.get(slot.binder).universal){need(SemanticTermSnapshot.isOrdinaryValue(value),"Root universal must have an ordinary closed value");SemanticTermSnapshot snapshot=SemanticTermSnapshot.capture(value),old=values.put(slot.binder,snapshot);need(old==null||old.semanticallyEquals(snapshot),"Repeated universal root slot differs");}
            else need(value instanceof Term&&value.isCVariable(),"Root existential must remain symbolic");
        }
        List<Occurrence> out=new ArrayList<>();for(int i=0;i<root.slots.size();i++){work.run();int binder=root.slots.get(i).binder;if(binder<0||root.binders.get(binder).universal)continue;List<SemanticTermSnapshot> dependencies=new ArrayList<>();for(int preceding:root.binders.get(binder).precedingUniversals){work.run();SemanticTermSnapshot value=values.get(preceding);need(value!=null,"Every preceding universal dependency must be instantiated");dependencies.add(value);}out.add(new Occurrence(row.getId(),i,domain.getArguments().get(i).getValue(mind).getId(),false,new Call(root.nativeRule,binder,dependencies)));}return out;
    }
    public static Result inspect(Mind mind,ContextProductionInventory inventory)throws Exception{
        if(mind==null||inventory==null)throw new IllegalArgumentException("Actual root-cache inventory required");current(mind,inventory);
        try{try{
            ContextDependentSymbolicCacheSnapshot.retiredCacheRows(inventory);
            NativeDependentPrimaryTheory theory=NativeDependentPrimaryTheory.rebuild(mind,inventory,null);Runnable work=ContextGroundProductionRounds.budget(1000000);
            Map<Long,NativePrenexUnitScope> roots=new LinkedHashMap<>();Set<Long> primary=new HashSet<>();List<Occurrence> occurrences=new ArrayList<>();
            for(ContextProductionInventory.Row row:inventory.primary){work.run();primary.add(row.nativeRule);Rule actual=(Rule)mind.getRules().get(row.nativeRule);if(!NativeDependentProductionRows.unit(actual))continue;
                NativePrenexUnitScope scope=NativePrenexUnitScope.observeFactoryRow(row.nativeRule,mind,work);Long copied=theory.ids.get(row.nativeRule);need(copied!=null,"Fresh primary replay occurrence required");need(NativePrenexUnitScope.same(scope,NativePrenexUnitScope.observeFactoryRow(copied,theory.copy,work),work),"Independent primary root differs");roots.put(row.nativeRule,scope);
                for(int i=0;i<scope.binders.size();i++){work.run();NativePrenexUnitScope.Binder binder=scope.binders.get(i);if(!binder.universal&&binder.precedingUniversals.isEmpty())occurrences.add(new Occurrence(row.nativeRule,-1,binder.nativeId,true,new Call(row.nativeRule,i,Collections.emptyList())));}
            }
            Map<Long,Long> instances=new LinkedHashMap<>();Set<Long> remaining=new LinkedHashSet<>();
            for(ContextProductionInventory.Row row:inventory.generated){ContextDependentRuntimeExecution.interrupt();work.run();Rule actual=(Rule)mind.getRules().get(row.nativeRule);Rule original=source(mind,actual,primary,work);if(original==null){remaining.add(row.nativeRule);continue;}NativePrenexUnitScope root=roots.get(original.getId());need(root!=null,"Observed exact root source required");
                List<Occurrence> forward=instance(mind,actual,root,false,work),backward=instance(mind,actual,NativePrenexUnitScope.observeFactoryRow(original.getId(),mind,work),true,work);need(forward.size()==backward.size(),"Root instance audit size differs");for(int i=0;i<forward.size();i++){work.run();Occurrence a=forward.get(i),b=backward.get(i);need(a.slot==b.slot&&a.symbol==b.symbol&&a.call.same(b.call,work),"Reverse root-slot reconstruction differs");}occurrences.addAll(forward);instances.put(row.nativeRule,original.getId());
            }
            need(instances.size()+remaining.size()==inventory.generated.size(),"Every generated occurrence classified");boolean conflated=false,split=false;
            for(int i=0;i<occurrences.size();i++)for(int j=0;j<i;j++){work.run();Occurrence a=occurrences.get(i),b=occurrences.get(j);boolean n=a.symbol==b.symbol,t=a.call.same(b.call,work);conflated|=n&&!t;split|=!n&&t;}
            ContextDependentCacheAliasInspection.Status aliases=conflated?(split?ContextDependentCacheAliasInspection.Status.CONFLATED_AND_SPLIT:ContextDependentCacheAliasInspection.Status.CONFLATED):(split?ContextDependentCacheAliasInspection.Status.SPLIT:ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED);
            return new Result(Status.INSPECTED,"Current primary instantiations and nullary witness anchors checked structurally; projected rows, native history and topology admission remain separate",inventory.fingerprint,aliases,instances,remaining,occurrences);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){return new Result(Status.INCOMPLETE,failure.getMessage(),inventory.fingerprint,ContextDependentCacheAliasInspection.Status.INCOMPLETE,Collections.emptyMap(),Collections.emptySet(),Collections.emptyList());}}finally{current(mind,inventory);}
    }
}
