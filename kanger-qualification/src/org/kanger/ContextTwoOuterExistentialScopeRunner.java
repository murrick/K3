/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;

/** Two outer parameters remain reordered in the head, independent of the inner slot. */
public final class ContextTwoOuterExistentialScopeRunner {
    static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static Clause capture(String text,boolean leading,boolean negative,int head)throws Exception{
        Result r=ContextAntecedentQuantifierScopeRunner.capture(text);require(r.bound,"two-outer declaration binds: "+r.reason);require(r.snapshot.primary.size()==1,"one whole Source");Entry e=r.snapshot.primary.get(0);require(e.kind==Kind.CLAUSES&&e.witnesses()==0&&e.clauses.size()==1,"one universal clause, zero witnesses");Clause c=e.clauses.get(0);require(c.variables.size()==3&&c.literals.size()==2,"three used variables and two literals");Atom rel=c.literals.stream().filter(v->v.predicate.equals("rel")).findFirst().get(),out=c.literals.stream().filter(v->v.predicate.equals("out")).findFirst().get();require(rel.positive==negative&&out.positive!=negative&&rel.arguments.size()==3&&out.arguments.size()==2,"exact ternary input/binary head and signs");int u=leading?2:0,v=leading?1:2,x=leading?0:1;require(rel.arguments.stream().allMatch(a->a.variable>=0)&&rel.arguments.stream().map(a->a.variable).distinct().count()==3,"all three input slots distinct");int left=head==1?u:head==2?x:v,right=head==1?v:head==3?x:u;require(out.arguments.get(0).variable==rel.arguments.get(left).variable&&out.arguments.get(1).variable==rel.arguments.get(right).variable,"exact reordered/swapped/captured head slots");return c;
    }
    static void one(boolean leading,boolean negative)throws Exception{
        String s=negative?"~":"",args=leading?"x,v,u":"u,x,v",alpha=leading?"z,w,p":"p,z,w",prefix="!@u @v @x "+s+"rel("+args+")->"+s;
        Clause nested=capture("!@u @v ($x "+s+"rel("+args+"))->"+s+"out(v,u);",leading,negative,0),prenex=capture(prefix+"out(v,u);",leading,negative,0),renamed=capture("!@p @w ($z "+s+"rel("+alpha+"))->"+s+"out(w,p);",leading,negative,0);
        require(nested.equivalent(prenex)&&nested.equivalent(renamed),"prenex normalization and alpha-renaming preserve both outer parameters");
        require(!nested.equivalent(capture(prefix+"out(u,v);",leading,negative,1)),"swapping head arguments changes the rule");require(!nested.equivalent(capture(prefix+"out(x,u);",leading,negative,2)),"inner variable cannot replace first head parameter");require(!nested.equivalent(capture(prefix+"out(v,x);",leading,negative,3)),"inner variable cannot replace second head parameter");
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("two-outer-existential-scope-").toString());for(boolean l:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})one(l,n);System.out.println("CONTEXT_TWO_OUTER_EXISTENTIAL_SCOPE_PASS checks="+(checks+ContextAntecedentQuantifierScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
