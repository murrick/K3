/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import java.util.*;
import org.kanger.ContextProductionCapture.*;

/** Universal quantification distributes over conjunction for both independent and shared binders. */
public final class ContextConjunctiveQuantifierScopeRunner {
    static int checks,ids;static void require(boolean yes,String why){++checks;if(!yes)throw new AssertionError(why);}
    static Map<String,Clause> capture(String text)throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);require(m.compile(text,null,false),"native conjunctive declaration");ContextProductionInventory inv=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,false,Collections.emptyList(),1000);ContextProductionNativeBinding.Result b=ContextProductionNativeBinding.bind(m,inv,64000000,500000);require(b.bound,"whole native scope binding: "+b.reason);require(b.snapshot.primary.size()==1&&b.snapshot.primary.get(0).clauses.size()==2,"one whole primary Source with two conjunctive clauses");Map<String,Clause> result=new HashMap<>();for(Clause c:b.snapshot.primary.get(0).clauses){require(c.literals.size()==2&&c.variables.size()==2,"each clause has input and independent universal head slot");Atom input=c.literals.stream().filter(a->a.predicate.equals("out")).findFirst().get(),head=c.literals.stream().filter(a->!a.predicate.equals("out")).findFirst().get();require(input.arguments.size()==1&&head.arguments.size()==1&&input.arguments.get(0).variable>=0&&head.arguments.get(0).variable>=0&&input.arguments.get(0).variable!=head.arguments.get(0).variable&&result.put(head.predicate,c)==null,"exact unary input/head mapping, no lost or duplicate conjunct");}require(result.keySet().equals(new HashSet<>(Arrays.asList("tail","extra"))),"both required heads retained");return result;}
    static void equivalent(Map<String,Clause> a,Map<String,Clause> b){for(String head:a.keySet())require(a.get(head).equivalent(b.get(head)),"corresponding whole conjunct alpha-equivalent for "+head);}
    static void one(boolean negative)throws Exception{String s=negative?"~":"";
        Map<String,Clause> nested=capture("!@x "+s+"out(x)->((@y "+s+"tail(y))&&(@z "+s+"extra(z)));");
        Map<String,Clause> prenex=capture("!@x @y @z "+s+"out(x)->"+s+"tail(y)&&"+s+"extra(z);");
        Map<String,Clause> renamed=capture("!@u "+s+"out(u)->((@v "+s+"tail(v))&&(@w "+s+"extra(w)));");
        Map<String,Clause> shared=capture("!@x "+s+"out(x)->(@y ("+s+"tail(y)&&"+s+"extra(y)));");
        equivalent(nested,prenex);equivalent(nested,renamed);equivalent(nested,shared);
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("conjunctive-quantifier-scope-").toString());one(false);one(true);System.out.println("CONTEXT_CONJUNCTIVE_QUANTIFIER_SCOPE_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
