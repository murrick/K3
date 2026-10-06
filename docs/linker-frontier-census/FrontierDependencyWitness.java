package org.kanger;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.primitives.Cause;
import org.kanger.units.*;

/** Native dependency witnesses, without replacing the saturation scheduler. */
public final class FrontierDependencyWitness {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void reset(Mind m){m.getRules().dropAction();m.getTValues().dropAction();m.getFValues().dropAction();m.getHypothesis().dropAction();m.getTempHypothesis().dropAction();}
    private static boolean noActions(Mind m){return !m.getRules().isAction()&&!m.getTValues().isAction()&&!m.getFValues().isAction()&&!m.getHypothesis().isAction()&&!m.getTempHypothesis().isAction();}
    private static Method method(String name,Class<?>...types)throws Exception{Method r=Linker.class.getDeclaredMethod(name,types);r.setAccessible(true);return r;}
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("frontier-dependencies-").toString());
        Mind m=new Mind(new User());Linker l=new Linker(m);Rule owner=new Rule(m);
        TVariable x=m.getTVars().createTVar(owner,m.getTerms().add("x"));
        TVariable y=m.getTVars().createTVar(owner,m.getTerms().add("y"));
        TValue xa=m.getTValues().add(x,m.getTerms().add("a"));
        TValue xb=m.getTValues().add(x,m.getTerms().add("b"));
        TValue yc=m.getTValues().add(y,m.getTerms().add("c"));
        x.setCurrent(xb);y.setCurrent(yc);SortedSet<TVariable> tail=new TreeSet<>(Arrays.asList(x,y));
        Method valid=method("isValidFor",SortedSet.class);reset(m);
        check((Boolean)valid.invoke(l,tail),"without TSolve tuple allowed");
        check(noActions(m),"initial compatibility creates no factory action");
        long version=m.ruleSolvesVersion();m.addTSolve(Arrays.asList(xa,yc));
        check(m.ruleSolvesVersion()>version,"canonical TSolve advances version");
        check(noActions(m),"canonical TSolve has no factory action");
        check(!(Boolean)valid.invoke(l,tail),"same current values rejected by new tuple");
        version=m.ruleSolvesVersion();m.addTSolve(Arrays.asList(xa,yc));
        check(version==m.ruleSolvesVersion(),"duplicate TSolve stable version");
        check(!(Boolean)valid.invoke(l,tail),"duplicate tuple leaves rejection");
        m.addTSolve(Arrays.asList(xb,yc));
        check(noActions(m),"second TSolve has no factory action");
        check((Boolean)valid.invoke(l,tail),"second tuple admits same current values");
        System.out.println("NATIVE_TSOLVE_WITNESS unchanged_values=true factory_actions=false decisions=[true,false,true]");
        // Existing append-only public alias support must remain visible.
        TValue yd=m.getTValues().add(y,m.getTerms().add("d"));
        y.setCurrent(yd);reset(m);
        check(!(Boolean)valid.invoke(l,tail),"new current pair absent from existing tuple set");
        Map<org.kanger.primitives.TVariableSet,List<TSolve>> alias=m.getRuleSolves();
        version=m.ruleSolvesVersion();
        org.kanger.primitives.TVariableSet key=new org.kanger.primitives.TVariableSet(Arrays.asList(xb,yd),m);
        alias.get(key).add(new TSolve(Arrays.asList(xb,yd),m));
        check(version==m.ruleSolvesVersion(),"native public alias append bypasses version");
        check(m.ruleSolvesExposed(),"public alias forces existing fallback");
        check(noActions(m),"alias append creates no factory action");
        check((Boolean)valid.invoke(l,tail),"alias append changes compatibility despite stable version");
        System.out.println("NATIVE_ALIAS_WITNESS unchanged_values=true unchanged_version=true factory_actions=false decisions=[false,true] exposed=true");
        Mind clause=new Mind(new User());
        Rule r=(Rule)clause.compileLine("!premise(item) -> conclusion(item);",false,null);
        check(r!=null&&r.getTree().size()==1&&r.getTree().get(0).size()==2,"native two-domain clause");
        List<Domain> tree=r.getTree().get(0);Domain premise=tree.get(0);
        Linker linker=new Linker(clause);Method database=method("linkDatabase",List.class,Map.class,Set.class,boolean.class);
        Map<IRule,Set<Cause>> causes=new HashMap<>();Set<TVariable> tvars=new TreeSet<>();
        boolean before=(Boolean)database.invoke(linker,tree,causes,tvars,false);
        check(!before&&clause.getProducedDomains().isEmpty(),"unexcluded clause produces no domain");
        reset(clause);version=clause.ruleSolvesVersion();
        check(!premise.isExcluded(clause),"domain initially unexcluded");premise.setExcluded(premise.getArguments(),clause);
        check(premise.isExcluded(clause),"native exclusion survives");
        check(noActions(clause),"native exclusion creates no factory action");
        check(version==clause.ruleSolvesVersion(),"exclusion does not change TSolve version");
        boolean after=(Boolean)database.invoke(linker,tree,causes,tvars,false);
        check(after&&!clause.getProducedDomains().isEmpty(),"same clause now produces remaining domain");
        check(noActions(clause),"classification precedes deferred Rule creation");
        check(clause.pendingTransactionCount()==0&&m.pendingTransactionCount()==0,"native contexts settled");
        System.out.println("NATIVE_EXCLUSION_WITNESS unchanged_rule_and_values=true unchanged_tsolve_version=true factory_actions=false produced=[false,true]");
        Mind donorBase=new Mind(new User());
        check(donorBase.compile("!seed(item);"),"native donor base compiles");
        boolean[] binding=new boolean[2];
        for(int full=0;full<2;full++){
            Mind child=new Mind(donorBase);
            try{
                Rule receiver=(Rule)child.compileLine("!@x ~seed(x);",false,null);
                check(receiver!=null&&receiver.getTree().size()==1&&receiver.getTree().get(0).size()==1,"native receiver is single domain");
                Domain domain=receiver.getTree().get(0).get(0);
                List<IRule> universe=new ArrayList<>();
                if(full==0)universe.add(receiver);else for(IRule candidate:child.getRules())if(!candidate.isDeleted(child))universe.add(candidate);
                Linker executor=new Linker(child);
                Object index=method("buildDomainIndex",Collection.class).invoke(executor,universe);
                Collection<IRule> donors=(Collection<IRule>)method("selectDomainCandidates",List.class,Map.class).invoke(executor,receiver.getTree().get(0),index);
                check(donors.isEmpty()==(full==0),"donor disappears from filtered index");
                reset(child);
                method("linkDomains",List.class,Collection.class,Map.class,boolean.class).invoke(executor,receiver.getTree().get(0),donors,new HashMap<IRule,Set<Cause>>(),false);
                TVariable variable=domain.getArguments().getTVariables(child).iterator().next();
                binding[full]=child.getTValues().find(variable,child.getTerms().add("item"))!=null;
                check(binding[full]==(full==1),"native item binding requires retained donor universe");
            }finally{donorBase.release(child);}
        }
        check(donorBase.pendingTransactionCount()==0,"donor children settled");
        System.out.println("NATIVE_DONOR_UNIVERSE_WITNESS receiver_unchanged=true item_binding=["+binding[0]+","+binding[1]+"]");
        System.out.println("FRONTIER_DEPENDENCY_WITNESS_OK checks="+checks);
    }
}
