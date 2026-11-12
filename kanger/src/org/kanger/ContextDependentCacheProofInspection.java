/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.ContextDependentCacheAliasInspection.Binding;
import org.kanger.interfaces.*;
import org.kanger.units.*;
import org.kanger.enums.ArgumentType;

/** Bounded primary-only proof discovery for projected cache rows, independent of projection donor resolution. */
public final class ContextDependentCacheProofInspection {
    private ContextDependentCacheProofInspection() {}
    private static final ContextDependentReplayInspection.Policy POLICY=new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000);
    public enum Status {INSPECTED,INCOMPLETE}
    public static final class Result {
        public final Status status;public final String reason,fingerprint;public final int rounds;
        public final ContextDependentCacheLedgerInspection.Result ledger;public final ContextDependentProductionReplay.Saved proof;
        public final Request recipe;public final List<Binding> bindings;
        private Result(Status status,String reason,String fingerprint,int rounds,ContextDependentCacheLedgerInspection.Result ledger,ContextDependentProductionReplay.Saved proof,Request recipe,List<Binding> bindings){this.status=status;this.reason=reason;this.fingerprint=fingerprint;this.rounds=rounds;this.ledger=ledger;this.proof=proof;this.recipe=recipe;this.bindings=Collections.unmodifiableList(new ArrayList<>(bindings));}
    }
    private static void need(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    private static void current(Mind mind,ContextProductionInventory inventory)throws Exception{need(DmzTerminalRestriction.activeNoGoods(mind).isEmpty(),"Restricted primary-only cache proof requires separate qualification");need(inventory.fingerprint.equals(ContextProductionInventory.inspect(mind,inventory.context,inventory.revision,inventory.localX,inventory.initialization,1000).fingerprint),"Changed primary-only cache proof inventory");}
    private static boolean sameStep(Step a,Step b,Runnable work){work.run();if(a.rule!=b.rule||a.retainedLiteral!=b.retainedLiteral||a.donors.size()!=b.donors.size())return false;for(int i=0;i<a.donors.size();i++){work.run();Donor x=a.donors.get(i),y=b.donors.get(i);if(x.original!=y.original||x.reference!=y.reference)return false;}return true;}
    private static boolean same(Row a,Row b,Runnable work){work.run();if(a.positive!=b.positive||!a.predicate.equals(b.predicate)||a.arguments.size()!=b.arguments.size())return false;Deque<TermView[]> todo=new ArrayDeque<>();for(int i=0;i<a.arguments.size();i++)todo.push(new TermView[]{a.arguments.get(i),b.arguments.get(i)});while(!todo.isEmpty()){work.run();TermView[] p=todo.pop();TermView x=p[0],y=p[1];if(x.kind!=y.kind)return false;if(x.kind==Kind.VALUE){if(!x.value.semanticallyEquals(y.value))return false;}else{need(x.kind==Kind.APPLICATION,"Only closed proof products can be compared");if(x.binder!=y.binder||!x.symbolSource.equals(y.symbolSource)||x.arguments.size()!=y.arguments.size())return false;for(int i=0;i<x.arguments.size();i++)todo.push(new TermView[]{x.arguments.get(i),y.arguments.get(i)});}}return true;}
    private static boolean matches(Mind mind,Rule actual,Row product,Runnable work)throws Exception{
        work.run();if(actual==null||!NativeDependentProductionRows.unit(actual)||!product.positive)return false;Domain domain=actual.getDomain();if(!product.predicate.equals(domain.getPredicate(mind).getName(mind))||product.arguments.size()!=domain.getRange())return false;
        for(int i=0;i<product.arguments.size();i++){work.run();IArgument arg=domain.getArguments().get(i);if(arg.getType()!=ArgumentType.TERM)return false;ITerm value=arg.getValue(mind);TermView term=product.arguments.get(i);Deque<TermView> todo=new ArrayDeque<>();todo.push(term);while(!todo.isEmpty()){work.run();TermView node=todo.pop();if(node.kind==Kind.VARIABLE)return false;for(TermView child:node.arguments)todo.push(child);}
            if(term.kind==Kind.VALUE){if(!SemanticTermSnapshot.isOrdinaryValue(value)||!term.value.semanticallyEquals(SemanticTermSnapshot.capture(value)))return false;}else if(!(value instanceof Term)||!value.isCVariable())return false;
        }return true;
    }
    public static Result inspect(Mind mind,ContextProductionInventory inventory)throws Exception{
        if(mind==null||inventory==null)throw new IllegalArgumentException("Actual primary-only cache proof target required");current(mind,inventory);int rounds=0;
        try{try{
            ContextDependentRootCacheInspection.Result roots=ContextDependentRootCacheInspection.inspect(mind,inventory);need(roots.status==ContextDependentRootCacheInspection.Status.INSPECTED,"Root classification incomplete: "+roots.reason);
            NativeDependentPrimaryTheory theory=NativeDependentPrimaryTheory.rebuild(mind,inventory,null);Map<Long,Long> originals=new HashMap<>();for(Map.Entry<Long,Long> entry:theory.ids.entrySet())originals.put(entry.getValue(),entry.getKey());
            Runnable work=ContextGroundProductionRounds.budget(1000000);Set<Long> active=new LinkedHashSet<>();for(ContextProductionInventory.Row row:theory.inventory.primary){work.run();Rule original=(Rule)theory.copy.getRules().get(row.nativeRule);if(NativeDependentProductionRows.unit(original))active.add(row.nativeRule);else{org.kanger.ContextProductionCapture.Clause clause=NativeDependentProductionRows.ordinary(original,theory.copy,work);int heads=0;for(org.kanger.ContextProductionCapture.Atom atom:clause.literals)if(atom.positive)heads++;if(clause.literals.size()==2&&heads==1)active.add(row.nativeRule);}}
            List<Step> steps=new ArrayList<>();ContextDependentProductionInspection.Result checked=null;List<Binding> bindings=null;
            for(int pass=0;pass<=8;pass++){
                ContextDependentRuntimeExecution.interrupt();Request prefix=new Request(POLICY,1000,steps,true);checked=ContextDependentProductionInspection.inspect(theory.copy,theory.inventory,new Request(POLICY,1000,steps));List<Binding> selected=new ArrayList<>();boolean all=true;
                for(long target:roots.remaining){work.run();Rule actual=(Rule)mind.getRules().get(target);int found=-1;for(int i=0;i<checked.products.size();i++){work.run();Row candidate=checked.products.get(i);if(!matches(mind,actual,candidate,work))continue;if(found<0)found=i;else need(same(checked.products.get(found),candidate,work),"Different primary witness proofs match one native cache row; binding alternatives require separate qualification");}if(found<0)all=false;else selected.add(new Binding(target,found));}
                if(all){bindings=selected;break;}need(pass<8,"Primary-only proof discovery round bound");List<Step> found=ContextDependentProductionInspection.discoverPrimary(theory.copy,theory.inventory,prefix,active);List<Step> added=new ArrayList<>();for(Step discovered:found){work.run();org.kanger.ContextProductionCapture.Clause clause=NativeDependentProductionRows.ordinary((Rule)theory.copy.getRules().get(discovered.rule),theory.copy,work);need(clause.literals.size()==2&&discovered.retainedLiteral>=0&&discovered.retainedLiteral<clause.literals.size()&&clause.literals.get(discovered.retainedLiteral).positive&&discovered.donors.size()==1,"Only positive single-body discovery can enter the portable proof recipe");Step candidate=new Step(discovered.rule,discovered.donors);boolean duplicate=false;for(Step old:steps)if(sameStep(old,candidate,work)){duplicate=true;break;}if(!duplicate)for(Step old:added)if(sameStep(old,candidate,work)){duplicate=true;break;}if(!duplicate)added.add(candidate);}need(!added.isEmpty(),"No primary-only proof for every demanded projected cache row");steps.addAll(added);rounds++;
            }
            need(bindings!=null&&checked!=null,"Complete demanded primary-only coverage required");List<Step> nativeSteps=new ArrayList<>();for(Step step:steps){work.run();Long rule=originals.get(step.rule);need(rule!=null,"Actual primary producer mapping required");List<Donor> donors=new ArrayList<>();for(Donor donor:step.donors){work.run();if(donor.original){Long original=originals.get(donor.reference);need(original!=null,"Actual primary donor mapping required");donors.add(Donor.original(original));}else donors.add(Donor.product((int)donor.reference));}nativeSteps.add(new Step(rule,step.retainedLiteral,donors));}
            Request recipe=new Request(POLICY,1000,nativeSteps);ContextDependentCacheLedgerInspection.Result ledger=ContextDependentCacheLedgerInspection.inspectProved(mind,inventory,recipe,bindings);need(ledger.status==ContextDependentCacheLedgerInspection.Status.INSPECTED,"Fresh independent joint ledger refused: "+ledger.reason);
            ContextDependentProductionReplay.Saved proof=ContextDependentProductionReplay.capture(theory.copy,theory.inventory,new Request(POLICY,1000,steps));
            return new Result(Status.INSPECTED,"Demanded projected rows have independently reconstructed primary-only proofs and a joint witness ledger; current projection donor alternatives and native event history are not certified",inventory.fingerprint,rounds,ledger,proof,recipe,bindings);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){return new Result(Status.INCOMPLETE,failure.getMessage(),inventory.fingerprint,rounds,null,null,null,Collections.emptyList());}}finally{current(mind,inventory);}
    }
}
