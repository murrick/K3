/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;

/** An outer parameter stays shared with the head, independently of inner existential position. */
public final class ContextOuterExistentialScopeRunner {
    static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static Clause capture(String text,boolean last,boolean negative,boolean innerHead)throws Exception{
        Result r=ContextAntecedentQuantifierScopeRunner.capture(text);require(r.bound,"outer-parameter declaration binds: "+r.reason);require(r.snapshot.primary.size()==1,"one whole Source");Entry e=r.snapshot.primary.get(0);require(e.kind==Kind.CLAUSES&&e.witnesses()==0&&e.clauses.size()==1,"one universal clause with no witness");Clause c=e.clauses.get(0);require(c.variables.size()==2&&c.literals.size()==2,"two used variables/two literals");Atom rel=c.literals.stream().filter(v->v.predicate.equals("rel")).findFirst().get(),out=c.literals.stream().filter(v->v.predicate.equals("out")).findFirst().get();require(rel.positive==negative&&out.positive!=negative&&rel.arguments.size()==2&&out.arguments.size()==1,"exact binary antecedent/unary head and signs");int outer=last?1:0,inner=1-outer;require(rel.arguments.get(outer).variable>=0&&rel.arguments.get(inner).variable>=0&&rel.arguments.get(outer).variable!=rel.arguments.get(inner).variable,"outer and inner slots distinct");require(out.arguments.get(0).variable==rel.arguments.get(innerHead?inner:outer).variable,"head retains exact selected parameter slot");return c;
    }
    static void one(boolean last,boolean negative)throws Exception{
        String s=negative?"~":"",args=last?"x,u":"u,x",renamed=last?"y,v":"v,y";
        Clause nested=capture("!@u ($x "+s+"rel("+args+"))->"+s+"out(u);",last,negative,false),prenex=capture("!@u @x "+s+"rel("+args+")->"+s+"out(u);",last,negative,false),alpha=capture("!@v ($y "+s+"rel("+renamed+"))->"+s+"out(v);",last,negative,false),wrong=capture("!@u @x "+s+"rel("+args+")->"+s+"out(x);",last,negative,true);
        require(nested.equivalent(prenex)&&nested.equivalent(alpha),"negative existential normalization and alpha-renaming preserve outer head");require(!nested.equivalent(wrong),"inner-head capture is a different rule");
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("outer-existential-scope-").toString());for(boolean l:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})one(l,n);System.out.println("CONTEXT_OUTER_EXISTENTIAL_SCOPE_PASS checks="+(checks+ContextAntecedentQuantifierScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
