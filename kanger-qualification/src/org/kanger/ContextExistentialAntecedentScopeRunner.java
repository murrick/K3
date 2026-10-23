/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;

/** An existential antecedent with variable-independent consequent is a universal implication. */
public final class ContextExistentialAntecedentScopeRunner {
    static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static Clause capture(String text)throws Exception{
        Result r=ContextAntecedentQuantifierScopeRunner.capture(text);
        require(r.bound,"negative existential occurrence binds: "+r.reason);
        require(r.snapshot.primary.size()==1,"one whole primary Source");Entry entry=r.snapshot.primary.get(0);
        require(entry.kind==Kind.CLAUSES&&entry.witnesses()==0&&entry.clauses.size()==1,"universal clause, no witness introduced by existential surface syntax");return entry.clauses.get(0);
    }
    static void one(boolean negative,boolean groundHead)throws Exception{
        String s=negative?"~":"",q=groundHead?"q(1)":"q()";
        Clause nested=capture("!($x "+s+"p(x))->"+s+q+";"),prenex=capture("!@x "+s+"p(x)->"+s+q+";"),renamed=capture("!($z "+s+"p(z))->"+s+q+";");
        require(nested.equivalent(prenex)&&nested.equivalent(renamed),"whole clauses alpha-equivalent across scope form and renaming");
        require(nested.variables.size()==1&&nested.literals.size()==2,"exact universal implication arity");Atom p=nested.literals.stream().filter(a->a.predicate.equals("p")).findFirst().get(),head=nested.literals.stream().filter(a->a.predicate.equals("q")).findFirst().get();
        require(p.positive==negative&&head.positive!=negative&&p.arguments.size()==1&&p.arguments.get(0).variable>=0,"exact antecedent/complement signs and universal slot");
        require(head.arguments.size()==(groundHead?1:0)&&(!groundHead||head.arguments.get(0).variable<0),"consequent independent of bound input variable");
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("existential-antecedent-scope-").toString());for(boolean n:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})one(n,g);System.out.println("CONTEXT_EXISTENTIAL_ANTECEDENT_SCOPE_PASS checks="+(checks+ContextAntecedentQuantifierScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
