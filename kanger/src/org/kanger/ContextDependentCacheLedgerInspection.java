/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.interfaces.*;
import org.kanger.units.*;
import org.kanger.enums.ArgumentType;

/** One fresh structural ledger across primary anchors, root instances and projected rows. No cache admission authority. */
public final class ContextDependentCacheLedgerInspection {
    private ContextDependentCacheLedgerInspection() {}
    public enum Status {INSPECTED,INCOMPLETE}
    public enum Origin {PRIMARY_ANCHOR,ROOT_INSTANCE,PRODUCT}
    public static final class Key {
        public final SemanticTermSnapshot value;public final long root;public final int binder;public final List<Key> parameters;
        private Key(SemanticTermSnapshot value,long root,int binder,List<Key> parameters){this.value=value;this.root=root;this.binder=binder;this.parameters=Collections.unmodifiableList(new ArrayList<>(parameters));}
        private static Key value(SemanticTermSnapshot value){return new Key(value,-1,-1,Collections.emptyList());}
        private static Key call(long root,int binder,List<Key> parameters){return new Key(null,root,binder,parameters);}
    }
    public static final class Occurrence {
        public final Origin origin;public final long row,symbol;public final int slot;public final Key key;
        private Occurrence(Origin origin,long row,int slot,long symbol,Key key){this.origin=origin;this.row=row;this.slot=slot;this.symbol=symbol;this.key=key;}
    }
    public static final class Result {
        public final Status status;public final String reason,fingerprint;public final ContextDependentCacheAliasInspection.Status aliases;
        public final int rootRows,productRows;public final List<Occurrence> occurrences;
        private Result(Status status,String reason,String fingerprint,ContextDependentCacheAliasInspection.Status aliases,int roots,int products,List<Occurrence> occurrences){this.status=status;this.reason=reason;this.fingerprint=fingerprint;this.aliases=aliases;rootRows=roots;productRows=products;this.occurrences=Collections.unmodifiableList(new ArrayList<>(occurrences));}
    }
    private static void need(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    private static void current(Mind mind,ContextProductionInventory inventory)throws Exception{need(DmzTerminalRestriction.activeNoGoods(mind).isEmpty(),"Restricted joint cache ledger requires separate qualification");need(inventory.fingerprint.equals(ContextProductionInventory.inspect(mind,inventory.context,inventory.revision,inventory.localX,inventory.initialization,1000).fingerprint),"Changed joint cache ledger inventory");}
    private static Key typed(TermView view,Map<Long,Long> originals,int depth,Runnable work){
        work.run();need(depth<32,"Joint cache witness nesting bound");if(view.kind==Kind.VALUE)return Key.value(view.value);need(view.kind==Kind.APPLICATION,"Closed typed ledger term required");Long actual=originals.get(view.symbolSource.rule);need(actual!=null,"Typed witness must belong to a freshly replayed actual primary");List<Key> parameters=new ArrayList<>();for(TermView argument:view.arguments)parameters.add(typed(argument,originals,depth+1,work));return Key.call(actual,view.binder,parameters);
    }
    private static boolean same(Key a,Key b,Runnable work){Deque<Key[]> todo=new ArrayDeque<>();todo.push(new Key[]{a,b});while(!todo.isEmpty()){work.run();Key[] pair=todo.pop();a=pair[0];b=pair[1];if(a.value!=null||b.value!=null){if(a.value==null||b.value==null||!a.value.semanticallyEquals(b.value))return false;}else{if(a.root!=b.root||a.binder!=b.binder||a.parameters.size()!=b.parameters.size())return false;for(int i=0;i<a.parameters.size();i++)todo.push(new Key[]{a.parameters.get(i),b.parameters.get(i)});}}return true;}
    public static Result inspect(Mind mind,ContextProductionInventory inventory)throws Exception{return inspectPlan(mind,inventory,null,null);}
    /** Explicit proof recipes still require fresh actual row matching and a complete root/product partition. */
    static Result inspectProved(Mind mind,ContextProductionInventory inventory,Request recipe,List<ContextDependentCacheAliasInspection.Binding> bindings)throws Exception{
        if(recipe==null||bindings==null)throw new IllegalArgumentException("Complete primary-only proof plan required");return inspectPlan(mind,inventory,recipe,new ArrayList<>(bindings));
    }
    private static Result inspectPlan(Mind mind,ContextProductionInventory inventory,Request supplied,List<ContextDependentCacheAliasInspection.Binding> suppliedBindings)throws Exception{
        if(mind==null||inventory==null)throw new IllegalArgumentException("Actual complete joint cache inventory required");current(mind,inventory);
        try{try{
            ContextDependentRootCacheInspection.Result roots=ContextDependentRootCacheInspection.inspect(mind,inventory);need(roots.status==ContextDependentRootCacheInspection.Status.INSPECTED,"Root ledger incomplete: "+roots.reason);
            Request recipe;List<ContextDependentCacheAliasInspection.Binding> bindings;
            if(supplied==null){
                ContextDependentCacheAncestryInspection.Result ancestry=ContextDependentCacheAncestryInspection.inspect(mind,inventory);need(ancestry.status!=ContextDependentCacheAncestryInspection.Status.INCOMPLETE,"Projection ancestry incomplete: "+ancestry.reason);
                need(ancestry.fingerprint.equals(inventory.fingerprint)&&roots.instances.keySet().equals(ancestry.unplanned),"Fresh matching root/ancestry ledger required");recipe=ancestry.recipe;bindings=ancestry.bindings;
            }else{recipe=supplied;bindings=suppliedBindings;}
            need(roots.fingerprint.equals(inventory.fingerprint),"Fresh shared physical ledger fingerprint required");Set<Long> products=new HashSet<>();for(ContextDependentCacheAliasInspection.Binding binding:bindings)need(products.add(binding.nativeRule),"Unique projected cache occurrence required");
            need(roots.remaining.equals(products),"Root and projection ledgers must partition the same complete actual cache");
            Runnable work=ContextGroundProductionRounds.budget(1000000);List<Occurrence> occurrences=new ArrayList<>();for(ContextDependentRootCacheInspection.Occurrence root:roots.occurrences){work.run();List<Key> parameters=new ArrayList<>();for(SemanticTermSnapshot value:root.call.parameters){work.run();parameters.add(Key.value(value));}occurrences.add(new Occurrence(root.primaryAnchor?Origin.PRIMARY_ANCHOR:Origin.ROOT_INSTANCE,root.row,root.slot,root.symbol,Key.call(root.call.root,root.call.binder,parameters)));}
            NativeDependentPrimaryTheory theory=NativeDependentPrimaryTheory.rebuild(mind,inventory,null);Map<Long,Long> originals=new HashMap<>();for(Map.Entry<Long,Long> entry:theory.ids.entrySet()){work.run();need(originals.put(entry.getValue(),entry.getKey())==null,"Unique replay/actual primary occurrence mapping required");}
            List<Step> steps=new ArrayList<>();for(Step step:recipe.steps){work.run();Long rule=theory.ids.get(step.rule);need(rule!=null,"Actual primary production rule required");List<Donor> donors=new ArrayList<>();for(Donor donor:step.donors){work.run();if(donor.original){Long original=theory.ids.get(donor.reference);need(original!=null,"Only actual primary donors may enter the joint replay");donors.add(Donor.original(original));}else donors.add(Donor.product((int)donor.reference));}steps.add(new Step(rule,step.retainedLiteral,donors));}
            ContextDependentProductionInspection.Result checked=ContextDependentProductionInspection.inspect(theory.copy,theory.inventory,new Request(recipe.policy,recipe.capacity,steps,recipe.signed));
            for(ContextDependentCacheAliasInspection.Binding binding:bindings){ContextDependentRuntimeExecution.interrupt();work.run();need(binding.product<checked.products.size(),"Exact checked projected product required");Row product=checked.products.get(binding.product);Rule actual=(Rule)mind.getRules().get(binding.nativeRule);need(actual!=null&&actual.isGenerated()&&actual.getTree().size()==1&&actual.getTree().get(0).size()==1,"Actual generated projected unit required");Domain domain=actual.getDomain();need(domain.isAntc()==product.positive&&domain.getPredicate(mind).getName(mind).equals(product.predicate)&&domain.getRange()==product.arguments.size(),"Exact joint product sign/predicate/arity required");
                for(int i=0;i<product.arguments.size();i++){work.run();IArgument argument=domain.getArguments().get(i);need(argument.getType()==ArgumentType.TERM,"Flat actual joint product term required");ITerm nativeValue=argument.getValue(mind);Key key=typed(product.arguments.get(i),originals,0,work);if(key.value!=null){need(SemanticTermSnapshot.isOrdinaryValue(nativeValue)&&key.value.semanticallyEquals(SemanticTermSnapshot.capture(nativeValue)),"Exact joint product ordinary value required");}else{need(nativeValue instanceof Term&&nativeValue.isCVariable(),"Typed application needs an actual native symbolic occurrence");occurrences.add(new Occurrence(Origin.PRODUCT,binding.nativeRule,i,nativeValue.getId(),key));}}
            }
            need(roots.instances.size()+products.size()==inventory.generated.size(),"No generated cache occurrence may be omitted or counted twice");boolean conflated=false,split=false;for(int i=0;i<occurrences.size();i++)for(int j=0;j<i;j++){work.run();Occurrence a=occurrences.get(i),b=occurrences.get(j);boolean n=a.symbol==b.symbol,t=same(a.key,b.key,work);conflated|=n&&!t;split|=!n&&t;}
            ContextDependentCacheAliasInspection.Status aliases=conflated?(split?ContextDependentCacheAliasInspection.Status.CONFLATED_AND_SPLIT:ContextDependentCacheAliasInspection.Status.CONFLATED):(split?ContextDependentCacheAliasInspection.Status.SPLIT:ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED);
            return new Result(Status.INSPECTED,"Every current generated occurrence classified and all supported witness identities checked jointly; not semantic disequality, native history, family consistency or topology admission",inventory.fingerprint,aliases,roots.instances.size(),products.size(),occurrences);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){return new Result(Status.INCOMPLETE,failure.getMessage(),inventory.fingerprint,ContextDependentCacheAliasInspection.Status.INCOMPLETE,0,0,Collections.emptyList());}}finally{current(mind,inventory);}
    }
}
