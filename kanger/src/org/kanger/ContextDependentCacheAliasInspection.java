/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Read-only structural renaming diagnostic for explicitly selected cache rows. Never an entailment or admission receipt. */
public final class ContextDependentCacheAliasInspection {
    private ContextDependentCacheAliasInspection() {}
    public enum Status {RENAMING_MATCHED,CONFLATED,SPLIT,CONFLATED_AND_SPLIT,INCOMPLETE}
    public static final class Binding {
        public final long nativeRule;public final int product;
        public Binding(long nativeRule,int product){if(nativeRule<0||product<0)throw new IllegalArgumentException("Actual generated row and checked product required");this.nativeRule=nativeRule;this.product=product;}
    }
    public static final class Result {
        public final Status status;public final String reason,fingerprint;public final int rows,symbols;
        private Result(Status status,String reason,String fingerprint,int rows,int symbols){this.status=status;this.reason=reason;this.fingerprint=fingerprint;this.rows=rows;this.symbols=symbols;}
    }
    private static void need(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    private static void current(Mind mind,ContextProductionInventory inventory)throws Exception{
        need(DmzTerminalRestriction.activeNoGoods(mind).isEmpty(),"Restricted cache needs separate qualification");
        need(inventory.fingerprint.equals(ContextProductionInventory.inspect(mind,inventory.context,inventory.revision,inventory.localX,inventory.initialization,1000).fingerprint),"Changed cache alias inventory");
    }
    private static boolean same(TermView a,TermView b,Runnable work){
        Deque<TermView[]> todo=new ArrayDeque<>();todo.push(new TermView[]{a,b});
        while(!todo.isEmpty()){work.run();TermView[] p=todo.pop();a=p[0];b=p[1];if(a.kind!=b.kind)return false;
            if(a.kind==Kind.VALUE){if(!a.value.semanticallyEquals(b.value))return false;}
            else if(a.kind==Kind.APPLICATION){if(a.binder!=b.binder||!a.symbolSource.equals(b.symbolSource)||a.arguments.size()!=b.arguments.size())return false;for(int i=0;i<a.arguments.size();i++)todo.push(new TermView[]{a.arguments.get(i),b.arguments.get(i)});}
            else throw new IllegalStateException("Open typed cache projection is outside closed alias inspection");
        }return true;
    }
    private static void closed(TermView root,Runnable work){Deque<TermView> todo=new ArrayDeque<>();todo.push(root);while(!todo.isEmpty()){work.run();TermView term=todo.pop();need(term.kind!=Kind.VARIABLE,"Closed typed witness dependency required");for(TermView child:term.arguments)todo.push(child);}}
    public static Result inspect(Mind mind,ContextProductionInventory inventory,Request recipe,List<Binding> supplied)throws Exception{
        if(mind==null||inventory==null||recipe==null||supplied==null)throw new IllegalArgumentException("Actual inventory, original-only recipe and explicit bindings required");
        List<Binding> bindings=new ArrayList<>(supplied);current(mind,inventory);
        try{try{
            need(!bindings.isEmpty()&&bindings.size()<=1000&&!bindings.contains(null),"Bounded nonempty cache selection required");
            for(ContextProductionInventory.Row row:inventory.rows)need(!row.deleted&&!row.query,"Live assertion inventory required");
            org.kanger.ContextDependentReplayInspection.Policy policy=recipe.policy;
            need(policy.bytes<=2000000&&policy.rows<=1000&&policy.steps<=64&&policy.work<=64000000&&policy.nodes<=100000&&recipe.capacity<=1000,"Alias recipe policy bound");
            Runnable mappingWork=ContextGroundProductionRounds.budget(1000000);
            NativeDependentPrimaryTheory theory=NativeDependentPrimaryTheory.rebuild(mind,inventory,null);
            List<Step> steps=new ArrayList<>();for(Step step:recipe.steps){mappingWork.run();Long rule=theory.ids.get(step.rule);need(rule!=null,"Production rule must be an actual primary declaration");List<Donor> donors=new ArrayList<>();for(Donor donor:step.donors){mappingWork.run();if(donor.original){Long id=theory.ids.get(donor.reference);need(id!=null,"Only primary original donors allowed");donors.add(Donor.original(id));}else donors.add(Donor.product((int)donor.reference));}steps.add(new Step(rule,step.retainedLiteral,donors));}
            need(steps.size()<=policy.steps,"Alias recipe step bound");
            ContextDependentProductionInspection.Result checked=ContextDependentProductionInspection.inspect(theory.copy,theory.inventory,new Request(policy,recipe.capacity,steps,recipe.signed));
            Runnable work=ContextGroundProductionRounds.budget(1000000);Set<Long> seen=new HashSet<>();
            List<Long> nativeSymbols=new ArrayList<>();List<TermView> typedSymbols=new ArrayList<>();
            boolean conflated=false,split=false;int count=0;
            for(Binding binding:bindings){work.run();need(seen.add(binding.nativeRule),"Unique actual cache occurrence required");need(binding.product<checked.products.size(),"Checked product index required");
                Rule actual=(Rule)mind.getRules().get(binding.nativeRule);need(actual!=null&&actual.isGenerated()&&!actual.isDeleted(mind)&&actual.getTree().size()==1&&actual.getTree().get(0).size()==1,"Actual generated unit required");
                Domain domain=actual.getDomain();Row product=checked.products.get(binding.product);
                need(domain.isAntc()==product.positive&&domain.getPredicate(mind).getName(mind).equals(product.predicate)&&domain.getRange()==product.arguments.size(),"Exact predicate, sign and arity required");
                for(int i=0;i<product.arguments.size();i++){work.run();TermView typed=product.arguments.get(i);closed(typed,work);IArgument arg=domain.getArguments().get(i);need(arg.getType()==ArgumentType.TERM,"Flat actual cache term required");ITerm value=arg.getValue(mind);
                    if(typed.kind==Kind.VALUE){need(SemanticTermSnapshot.isOrdinaryValue(value)&&typed.value.semanticallyEquals(SemanticTermSnapshot.capture(value)),"Exact ordinary cache value required");continue;}
                    need(value instanceof Term&&value.isCVariable(),"Dependent application must map to an actual native symbolic term");long symbol=((Term)value).getId();
                    for(int j=0;j<nativeSymbols.size();j++){work.run();boolean n=nativeSymbols.get(j)==symbol,t=same(typedSymbols.get(j),typed,work);if(n&&!t)conflated=true;if(!n&&t)split=true;}
                    nativeSymbols.add(symbol);typedSymbols.add(typed);
                }count++;
            }
            Status mismatch=conflated?(split?Status.CONFLATED_AND_SPLIT:Status.CONFLATED):(split?Status.SPLIT:null);
            return new Result(mismatch==null?Status.RENAMING_MATCHED:mismatch,mismatch==null?"Selected closed products preserve a bijective structural witness renaming; not cache coverage or semantic equality/inequality":"Selected cache symbols do not preserve structural dependency identity; not a declaration inconsistency proof",inventory.fingerprint,count,new HashSet<Long>(nativeSymbols).size());
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){return new Result(Status.INCOMPLETE,failure.getMessage(),inventory.fingerprint,0,0);}}finally{current(mind,inventory);}
    }
}
