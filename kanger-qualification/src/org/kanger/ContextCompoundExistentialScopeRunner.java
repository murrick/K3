/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;

/** One shared existential antecedent must not become two independent existential conjuncts. */
public final class ContextCompoundExistentialScopeRunner {
    static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static Clause capture(String text,boolean shared,boolean negative,boolean groundHead)throws Exception{
        Result r=ContextAntecedentQuantifierScopeRunner.capture(text);require(r.bound,"negative compound existential binds: "+r.reason);require(r.snapshot.primary.size()==1,"one whole Source");Entry e=r.snapshot.primary.get(0);require(e.kind==Kind.CLAUSES&&e.witnesses()==0&&e.clauses.size()==1,"one universal clause, zero witness objects");Clause c=e.clauses.get(0);require(c.variables.size()==(shared?1:2)&&c.literals.size()==3,"exact shared/independent universal variable count");Atom a=c.literals.stream().filter(v->v.predicate.equals("a")).findFirst().get(),b=c.literals.stream().filter(v->v.predicate.equals("b")).findFirst().get(),q=c.literals.stream().filter(v->v.predicate.equals("q")).findFirst().get();require(a.positive==negative&&b.positive==negative&&q.positive!=negative&&a.arguments.size()==1&&b.arguments.size()==1,"exact antecedent/complement literal signs");require(a.arguments.get(0).variable>=0&&b.arguments.get(0).variable>=0&&((a.arguments.get(0).variable==b.arguments.get(0).variable)==shared),"shared slot preserved, independent slots distinct");require(q.arguments.size()==(groundHead?1:0)&&(!groundHead||q.arguments.get(0).variable<0),"consequent independent of input variables");return c;
    }
    static void one(boolean negative,boolean groundHead)throws Exception{
        String s=negative?"~":"",q=s+(groundHead?"q(1)":"q()");
        Clause shared=capture("!($x ("+s+"a(x)&&"+s+"b(x)))->"+q+";",true,negative,groundHead),independent=capture("!(($x "+s+"a(x))&&($y "+s+"b(y)))->"+q+";",false,negative,groundHead);
        require(!shared.equivalent(independent),"common witness and independent witnesses are not alpha-equivalent");
        require(shared.equivalent(capture("!@x "+s+"a(x)&&"+s+"b(x)->"+q+";",true,negative,groundHead)),"shared existential antecedent equals shared universal implication");
        require(independent.equivalent(capture("!@x @y "+s+"a(x)&&"+s+"b(y)->"+q+";",false,negative,groundHead)),"independent existential antecedents equal independent universal implication");
        require(shared.equivalent(capture("!($u ("+s+"a(u)&&"+s+"b(u)))->"+q+";",true,negative,groundHead)),"shared alpha-renaming");
        require(independent.equivalent(capture("!(($u "+s+"a(u))&&($v "+s+"b(v)))->"+q+";",false,negative,groundHead)),"independent alpha-renaming");
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("compound-existential-scope-").toString());for(boolean n:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})one(n,g);System.out.println("CONTEXT_COMPOUND_EXISTENTIAL_SCOPE_PASS checks="+(checks+ContextAntecedentQuantifierScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
