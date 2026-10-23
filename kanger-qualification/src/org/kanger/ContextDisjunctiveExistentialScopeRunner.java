/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;

/** Existential disjunction yields two universal clauses owned by one whole Source. */
public final class ContextDisjunctiveExistentialScopeRunner {
    static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static Map<String,Clause> capture(String text,boolean negative,boolean groundHead)throws Exception{
        Result r=ContextAntecedentQuantifierScopeRunner.capture(text);require(r.bound,"negative existential disjunction binds: "+r.reason);require(r.snapshot.primary.size()==1,"one whole Source");Entry e=r.snapshot.primary.get(0);require(e.kind==Kind.CLAUSES&&e.witnesses()==0&&e.clauses.size()==2,"two clauses under the same whole Source, no witness");Map<String,Clause> result=new HashMap<>();for(Clause c:e.clauses){require(c.variables.size()==1&&c.literals.size()==2,"one used universal input per branch");Atom a=c.literals.stream().filter(v->!v.predicate.equals("q")).findFirst().get(),q=c.literals.stream().filter(v->v.predicate.equals("q")).findFirst().get();require(a.positive==negative&&q.positive!=negative&&a.arguments.size()==1&&a.arguments.get(0).variable>=0,"exact complemented input and head signs");require(q.arguments.size()==(groundHead?1:0)&&(!groundHead||q.arguments.get(0).variable<0),"consequent independent of branch input");require(result.put(a.predicate,c)==null,"each branch retained once");}require(result.keySet().equals(new HashSet<>(Arrays.asList("a","b"))),"both disjuncts retained");return result;
    }
    static void equivalent(Map<String,Clause> a,Map<String,Clause> b){for(String key:a.keySet())require(a.get(key).equivalent(b.get(key)),"corresponding whole-source clause alpha-equivalent: "+key);}
    static void one(boolean negative,boolean groundHead)throws Exception{String s=negative?"~":"",q=s+(groundHead?"q(1)":"q()");Map<String,Clause> shared=capture("!($x ("+s+"a(x)||"+s+"b(x)))->"+q+";",negative,groundHead),independent=capture("!(($x "+s+"a(x))||($y "+s+"b(y)))->"+q+";",negative,groundHead);equivalent(shared,independent);equivalent(shared,capture("!@x "+s+"a(x)||"+s+"b(x)->"+q+";",negative,groundHead));equivalent(shared,capture("!@x @y "+s+"a(x)||"+s+"b(y)->"+q+";",negative,groundHead));equivalent(shared,capture("!($u ("+s+"a(u)||"+s+"b(u)))->"+q+";",negative,groundHead));equivalent(shared,capture("!(($u "+s+"a(u))||($v "+s+"b(v)))->"+q+";",negative,groundHead));}
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("disjunctive-existential-scope-").toString());for(boolean n:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})one(n,g);System.out.println("CONTEXT_DISJUNCTIVE_EXISTENTIAL_SCOPE_PASS checks="+(checks+ContextAntecedentQuantifierScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
