/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import java.util.*;
import org.kanger.ContextProductionCapture.*;

/** Negative quantifier occurrence must not be captured as a flat universal implication. */
public final class ContextAntecedentQuantifierScopeRunner {
    static int checks,ids;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static ContextProductionNativeBinding.Result capture(String text)throws Exception{
        User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);
        require(m.compile(text,null,false),"native declaration parses: "+text);
        ContextProductionInventory inv=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,false,Collections.emptyList(),1000);
        require(inv.primary.size()==1,"one whole primary declaration");
        return ContextProductionNativeBinding.bind(m,inv,64000000,500000);
    }
    static void one(boolean negative,boolean groundHead)throws Exception{
        String s=negative?"~":"",q=groundHead?"q(1)":"q()";
        ContextProductionNativeBinding.Result nested=capture("!(@x "+s+"p(x))->"+s+q+";");
        require(!nested.bound&&nested.reason.contains("conditional witness"),"antecedent quantifier remains outside flat universal binding");
        ContextProductionNativeBinding.Result prenex=capture("!@x "+s+"p(x)->"+s+q+";");
        require(prenex.bound,"prenex universal implication supported");
        require(prenex.snapshot.primary.size()==1&&prenex.snapshot.primary.get(0).clauses.size()==1,"whole Source owns one prenex clause");
        Clause c=prenex.snapshot.primary.get(0).clauses.get(0);
        require(c.variables.size()==1&&c.literals.size()==2,"exact universal implication shape");
        Atom p=c.literals.stream().filter(a->a.predicate.equals("p")).findFirst().get(),head=c.literals.stream().filter(a->a.predicate.equals("q")).findFirst().get();
        require(p.positive==negative&&head.positive!=negative&&p.arguments.size()==1&&p.arguments.get(0).variable>=0,"input negated by implication, head polarity retained");
        require(head.arguments.size()==(groundHead?1:0)&&(!groundHead||head.arguments.get(0).variable<0),"head retains exact ground arity");
        ContextProductionNativeBinding.Result renamed=capture("!@z "+s+"p(z)->"+s+q+";");
        require(renamed.bound&&c.equivalent(renamed.snapshot.primary.get(0).clauses.get(0)),"prenex alpha-renaming preserves clause");
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("antecedent-quantifier-scope-").toString());for(boolean n:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})one(n,g);System.out.println("CONTEXT_ANTECEDENT_QUANTIFIER_SCOPE_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
