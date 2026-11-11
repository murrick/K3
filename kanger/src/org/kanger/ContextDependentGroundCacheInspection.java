/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

/** One independently checked local implication witness per ground cache row, not family admission or proof-history coverage. */
public final class ContextDependentGroundCacheInspection {
    private ContextDependentGroundCacheInspection() {}
    public enum Status {VERIFIED,INCOMPLETE}
    public static final class Limits {
        public final int rows,facts,rounds,work,auditWork;
        public Limits(int rows,int facts,int rounds,int work,int auditWork){if(rows<1||rows>10000||facts<1||facts>100000||rounds<1||rounds>1000||work<1||auditWork<1)throw new IllegalArgumentException("Separate bounded cache inventory, closure and audit limits required");this.rows=rows;this.facts=facts;this.rounds=rounds;this.work=work;this.auditWork=auditWork;}
    }
    /** Unit-resolution depth; direct universal instantiation is a leaf, not a reconstructed native event. */
    public static final class Proof {
        public final Fact fact;public final Source rule;public final int retainedLiteral,depth;
        public final List<Proof> donors;public final Set<Source> sources;
        private Proof(Fact fact,Source rule,int retained,List<Proof> donors,Runnable work){
            this.fact=fact;this.rule=rule;retainedLiteral=retained;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));
            Set<Source> support=new LinkedHashSet<>();support.add(rule);int d=0;
            for(Proof donor:donors){work.run();d=Math.max(d,donor.depth+1);for(Source source:donor.sources){work.run();support.add(source);}}
            depth=d;sources=Collections.unmodifiableSet(support);
        }
    }
    public static final class Covered {
        public final long nativeRule;public final Proof proof;
        private Covered(long nativeRule,Proof proof){this.nativeRule=nativeRule;this.proof=proof;}
    }
    public static final class Result {
        public final Status status;public final String reason,fingerprint;
        public final List<Covered> covered;public final Set<Long> unusedUnsupportedPrimary;
        public final int facts,rounds;
        private Result(Status status,String reason,String fingerprint,List<Covered> covered,Set<Long> unused,int facts,int rounds){this.status=status;this.reason=reason;this.fingerprint=fingerprint;this.covered=Collections.unmodifiableList(new ArrayList<>(covered));unusedUnsupportedPrimary=Collections.unmodifiableSet(new LinkedHashSet<>(unused));this.facts=facts;this.rounds=rounds;}
        private static Result incomplete(String reason,ContextProductionInventory inventory){return new Result(Status.INCOMPLETE,reason,inventory.fingerprint,Collections.emptyList(),Collections.emptySet(),0,0);}
    }
    private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException(why);}
    private static void current(Mind mind,ContextProductionInventory inventory,Limits limits)throws Exception{need(inventory.fingerprint.equals(ContextProductionInventory.inspect(mind,inventory.context,inventory.revision,inventory.localX,inventory.initialization,limits.rows).fingerprint),"Changed native cache inspection inventory");}
    private static Source source(ContextProductionInventory inventory,long id){return new Source(inventory.context,inventory.revision,id,inventory.localX,"inventory:"+inventory.fingerprint);}
    private static boolean eligible(Rule rule,Mind mind,Runnable work)throws Exception{
        if(rule.getTree().size()!=1||rule.getTree().get(0).isEmpty())return false;int heads=0;
        for(Domain domain:rule.getTree().get(0)){
            work.run();if(domain.isSystem(mind)||domain.isCalculated(mind))return false;if(domain.isAntc())heads++;
            for(IArgument argument:domain.getArguments()){
                work.run();if(argument.getType()==ArgumentType.TVARIABLE){need(((TVariable)argument.getObject(mind)).getRuleId()==rule.getId(),"Local cache-clause universal owner required");}
                else if(argument.getType()!=ArgumentType.TERM||!SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind)))return false;
            }
        }
        return heads<=1;
    }
    private static Map<Long,Clause> declarations(Mind mind,ContextProductionInventory inventory,Set<Long> unused,Runnable work)throws Exception{
        Map<Long,Clause> out=new LinkedHashMap<>();for(ContextProductionInventory.Row row:inventory.rows){
            ContextDependentRuntimeExecution.interrupt();work.run();need(!row.deleted,"Deleted native rows are outside ground cache coverage");if(row.generated)continue;
            Rule rule=(Rule)mind.getRules().get(row.nativeRule);need(rule!=null&&!rule.isGenerated()&&!rule.isQuery(),"Actual primary cache basis required");
            if(eligible(rule,mind,work))out.put(row.nativeRule,NativeDependentProductionRows.ordinary(rule,mind,work));else unused.add(row.nativeRule);
        }return out;
    }
    private static boolean match(Atom atom,Fact fact,boolean opposite,Map<Integer,SemanticTermSnapshot> bindings,Runnable work){
        work.run();if(!atom.predicate.equals(fact.predicate)||fact.positive!=(opposite?!atom.positive:atom.positive)||atom.arguments.size()!=fact.arguments.size())return false;
        for(int i=0;i<atom.arguments.size();i++){
            work.run();Slot slot=atom.arguments.get(i);SemanticTermSnapshot value=fact.arguments.get(i);
            if(slot.variable<0){if(!slot.value.semanticallyEquals(value))return false;}
            else{SemanticTermSnapshot old=bindings.get(slot.variable);if(old!=null&&!old.semanticallyEquals(value))return false;bindings.put(slot.variable,value);}
        }return true;
    }
    private static Fact instantiate(Atom atom,Map<Integer,SemanticTermSnapshot> bindings,Runnable work){
        List<SemanticTermSnapshot> args=new ArrayList<>();for(Slot slot:atom.arguments){work.run();SemanticTermSnapshot value=slot.variable<0?slot.value:bindings.get(slot.variable);if(value==null)return null;args.add(value);}return Fact.ground(atom.predicate,atom.positive,args);
    }
    private static Proof find(List<Proof> pool,Fact fact,Runnable work){for(Proof proof:pool){work.run();if(proof.fact.equivalent(fact))return proof;}return null;}
    private static boolean add(List<Proof> pool,Proof proof,Limits limits,Runnable work){if(find(pool,proof.fact,work)!=null)return false;need(pool.size()<limits.facts,"Ground cache fact bound");pool.add(proof);return true;}
    private static Map<Integer,SemanticTermSnapshot> copy(Map<Integer,SemanticTermSnapshot> source,Runnable work){Map<Integer,SemanticTermSnapshot> result=new HashMap<>();for(Map.Entry<Integer,SemanticTermSnapshot> e:source.entrySet()){work.run();result.put(e.getKey(),e.getValue());}return result;}
    private static final class Join {
        final int next;final Map<Integer,SemanticTermSnapshot> bindings;final List<Proof> donors;
        Join(int next,Map<Integer,SemanticTermSnapshot> bindings,List<Proof> donors){this.next=next;this.bindings=bindings;this.donors=donors;}
    }
    private static void derive(Clause clause,Source source,int retained,List<Proof> frozen,List<Proof> out,Limits limits,Runnable work)throws Exception{
        List<Integer> body=new ArrayList<>();for(int i=0;i<clause.literals.size();i++){work.run();if(i!=retained)body.add(i);}
        Deque<Join> todo=new ArrayDeque<>();todo.push(new Join(0,Collections.emptyMap(),Collections.emptyList()));
        while(!todo.isEmpty()){
            ContextDependentRuntimeExecution.interrupt();work.run();Join state=todo.pop();
            if(state.next==body.size()){Fact fact=instantiate(clause.literals.get(retained),state.bindings,work);if(fact!=null)add(out,new Proof(fact,source,retained,state.donors,work),limits,work);continue;}
            Atom atom=clause.literals.get(body.get(state.next));
            for(Proof donor:frozen){work.run();Map<Integer,SemanticTermSnapshot> bindings=copy(state.bindings,work);if(!match(atom,donor.fact,true,bindings,work))continue;
                List<Proof> donors=new ArrayList<>();for(Proof previous:state.donors){work.run();donors.add(previous);}donors.add(donor);need(todo.size()<limits.facts,"Ground cache join frontier bound");todo.push(new Join(state.next+1,bindings,donors));}
        }
    }
    /** Starts from the conclusion, unlike construction's donor-first join. No cached row is an audit assumption. */
    private static void audit(Proof root,Map<Long,Clause> declarations,ContextProductionInventory inventory,Limits limits,Runnable work)throws Exception{
        Set<Proof> checked=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());Set<Proof> active=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());
        Deque<Proof> todo=new ArrayDeque<>();todo.push(root);
        while(!todo.isEmpty()){
            ContextDependentRuntimeExecution.interrupt();work.run();Proof p=todo.peek();if(checked.contains(p)){todo.pop();continue;}
            if(active.add(p)){need(active.size()+checked.size()<=limits.facts,"Cache proof graph bound");for(Proof donor:p.donors){work.run();need(!active.contains(donor),"Cyclic cache implication proof");if(!checked.contains(donor))todo.push(donor);}if(todo.peek()!=p)continue;}
            Clause clause=declarations.get(p.rule.rule);need(clause!=null&&p.rule.equals(source(inventory,p.rule.rule)),"Proof leaves must be exact current primary Sources");
            need(p.retainedLiteral>=0&&p.retainedLiteral<clause.literals.size()&&p.donors.size()+1==clause.literals.size(),"Whole clausal implication proof required");
            Map<Integer,SemanticTermSnapshot> bindings=new HashMap<>();need(match(clause.literals.get(p.retainedLiteral),p.fact,false,bindings,work),"Cache conclusion differs from retained native literal");
            Set<Source> sources=new LinkedHashSet<>();sources.add(p.rule);int next=0,depth=0;
            for(int i=0;i<clause.literals.size();i++)if(i!=p.retainedLiteral){work.run();Proof donor=p.donors.get(next++);need(checked.contains(donor)&&match(clause.literals.get(i),donor.fact,true,bindings,work),"Cache donor does not discharge the exact native literal");depth=Math.max(depth,donor.depth+1);for(Source s:donor.sources){work.run();sources.add(s);}}
            need(depth==p.depth&&sources.equals(p.sources),"Exact original support and depth required");checked.add(p);active.remove(p);todo.pop();
        }
    }
    public static Result inspect(Mind mind,ContextProductionInventory inventory,Limits limits)throws Exception{
        if(mind==null||inventory==null||limits==null)throw new IllegalArgumentException("Actual cache inventory and limits required");current(mind,inventory,limits);
        try{try{
            need(DmzTerminalRestriction.activeNoGoods(mind).isEmpty(),"Restricted branch cache is outside local implication coverage");Runnable work=ContextGroundProductionRounds.budget(limits.work);
            Set<Long> unused=new LinkedHashSet<>();Map<Long,Clause> rules=declarations(mind,inventory,unused,work);Map<Long,Fact> targets=new LinkedHashMap<>();
            for(ContextProductionInventory.Row row:inventory.generated){ContextDependentRuntimeExecution.interrupt();work.run();Rule rule=(Rule)mind.getRules().get(row.nativeRule);
                need(rule!=null&&rule.getTree().size()==1&&rule.getTree().get(0).size()==1,"Generated cache must be one signed ground literal");
                for(IArgument argument:rule.getDomain().getArguments()){work.run();need(argument.getType()==ArgumentType.TERM,"Generated cache must have flat ordinary value arguments");}
                Fact fact=Fact.of(rule.getDomain(),mind,false);need(fact!=null,"Non-ground or symbolic cache is outside this proof scope");targets.put(row.nativeRule,fact);}
            List<Proof> pool=new ArrayList<>();
            for(Map.Entry<Long,Clause> rule:rules.entrySet())if(rule.getValue().literals.size()==1){
                Atom atom=rule.getValue().literals.get(0);Fact fixed=instantiate(atom,Collections.emptyMap(),work);
                if(fixed!=null)add(pool,new Proof(fixed,source(inventory,rule.getKey()),0,Collections.emptyList(),work),limits,work);
                else for(Fact target:targets.values()){Map<Integer,SemanticTermSnapshot> bindings=new HashMap<>();if(match(atom,target,false,bindings,work))add(pool,new Proof(target,source(inventory,rule.getKey()),0,Collections.emptyList(),work),limits,work);}
            }
            int rounds=0;boolean complete=false;
            while(rounds<limits.rounds){
                boolean all=true;for(Fact target:targets.values()){work.run();all&=find(pool,target,work)!=null;}if(all){complete=true;break;}
                ContextDependentRuntimeExecution.interrupt();List<Proof> frozen=new ArrayList<>(pool);int before=pool.size();rounds++;
                for(Map.Entry<Long,Clause> rule:rules.entrySet())if(rule.getValue().literals.size()>1)for(int retained=0;retained<rule.getValue().literals.size();retained++)derive(rule.getValue(),source(inventory,rule.getKey()),retained,frozen,pool,limits,work);
                if(pool.size()==before){complete=true;break;}
            }
            // Coverage is sufficient even if the closure allowance ends after all demanded rows were proved.
            List<Covered> covered=new ArrayList<>();for(Map.Entry<Long,Fact> target:targets.entrySet()){work.run();Proof proof=find(pool,target.getValue(),work);need(proof!=null,complete?"Ground cache has no primary-only implication witness":"Ground cache coverage round bound");covered.add(new Covered(target.getKey(),proof));}
            Runnable auditWork=ContextGroundProductionRounds.budget(limits.auditWork);Set<Long> auditUnused=new LinkedHashSet<>();Map<Long,Clause> fresh=declarations(mind,inventory,auditUnused,auditWork);
            for(Covered item:covered){auditWork.run();audit(item.proof,fresh,inventory,limits,auditWork);Rule actual=(Rule)mind.getRules().get(item.nativeRule);need(actual!=null&&item.proof.fact.equivalent(Fact.of(actual.getDomain(),mind,false)),"Audited proof must conclude the actual cached fact");}
            return new Result(Status.VERIFIED,"Every actual ground cache row has an independently checked local primary-only implication witness; not family consistency or branch/history completeness",inventory.fingerprint,covered,unused,pool.size(),rounds);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){return Result.incomplete(failure.getMessage(),inventory);}}finally{current(mind,inventory,limits);}
    }
}
