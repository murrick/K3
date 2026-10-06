package org.kanger;

import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.*;
import org.kanger.interfaces.internal.ICache;
import org.kanger.primitives.*;
import org.kanger.units.*;

/** Native fixture: a discarded hydrated rule still selects shared variables. */
public final class ResolvedCandidateWitness {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static Object invoke(Linker linker,String name,Class<?>[] types,Object...args)throws Exception {
        Method m=Linker.class.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(linker,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static Domain domain(Predicate p,Rule r,Mind mind,Object value)throws Exception {
        ArgumentsList args=new ArgumentsList();args.add(new Argument((org.kanger.interfaces.internal.IUnit)value));
        Domain d=new Domain(p,false,args,r);d.setMind(mind);return d;
    }
    private static String run(boolean shortcut,int mode)throws Exception {
        Mind root=new Mind(new User()),child=new Mind(root);
        ITerm a=root.getTerms().add("a"),b=root.getTerms().add("b");
        Rule retained=new Rule(root),discarded=new Rule(root);retained.setId(900001);discarded.setId(900002);
        retained.setMindId(root.getId());discarded.setMindId(root.getId());
        TVariable variable=root.getTVars().createTVar(discarded,root.getTerms().add("shared"));
        root.getTValues().set(variable,new TValue(variable,a));child.getTValues().set(variable,new TValue(variable,b));
        Predicate p=new Predicate(root.getTerms().add("resolved"),1);p.setId(910001);
        retained.getTree().get(0).add(domain(p,retained,root,a));
        discarded.getTree().get(0).add(domain(p,discarded,root,variable));
        retained.setStored(root);discarded.setStored(root);retained.setMind(root);discarded.setMind(root);
        Field field=RuleFactory.class.getDeclaredField("cache");field.setAccessible(true);
        ICache cache=(ICache)field.get(root.getRules());cache.add(retained);cache.add(discarded);
        Domain source=domain(p,retained,root,a);source.setAntc(true);
        List<IRule> first=root.getRules().findByResolvedDomain(source,false);
        check(first.size()==2 && first.contains(retained) && first.contains(discarded),"two resolved rules");
        Linker linker=new Linker(root);
        Map<?,?> index=(Map<?,?>)invoke(linker,"buildDomainIndex",new Class<?>[]{Collection.class},Collections.singletonList(retained));
        if(mode==1){
            Domain shared=domain(p,discarded,root,variable);shared.setMind(child);
        }else variable.setMind(child);
        check(variable.getMind()==child && variable.getValue()==b,"intervening child selection");
        Collection<IRule> filtered;
        if(shortcut){
            // Models either pre-hydration upstream narrowing (modes 0/1) or
            // replaying the earlier IDs without rehydration (mode 2).
            if(mode<2)check(root.getRules().get(retained.getId())==retained,"retained-only lookup");
            filtered=Collections.singletonList(retained);
        }else filtered=(Collection<IRule>)invoke(linker,"selectDomainCandidates",new Class<?>[]{List.class,Map.class},Collections.singletonList(source),index);
        check(filtered.size()==1 && filtered.iterator().next()==retained,"same downstream candidate");
        check(variable.getMind()==(shortcut?child:root),"final variable context");
        check(variable.getValue()==(shortcut?b:a),"final visible value");
        return "retained:"+(variable.getValue()==a?"root-a":"child-b");
    }
    public static void main(String[] ignored)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("resolved-witness-").toString());
        for(int mode=0;mode<3;mode++){
            String reference=run(false,mode),shortcut=run(true,mode);
            check(!reference.equals(shortcut),"observable divergence");
            System.out.println("RESOLVED_WITNESS mode="+mode+" reference="+reference+" shortcut="+shortcut);
        }
        System.out.println("RESOLVED_CANDIDATE_WITNESS_OK scenarios=3 checks="+checks);
    }
}
