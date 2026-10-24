/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import java.util.*;
import org.kanger.ContextProductionCapture.Atom;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Result;

/** A disjunctive result is one whole residual, never two mandatory heads. */
public final class ContextDisjunctiveOuterExistentialScopeRunner {
    static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static Clause capture(String text,boolean leading,boolean negative)throws Exception{
        Result r=ContextConjunctiveOuterExistentialScopeRunner.bind(text,1);
        Entry e=r.snapshot.primary.get(0);require(e.kind==ContextProductionNativeBinding.Kind.CLAUSES&&e.witnesses()==0&&e.clauses.size()==1,"one whole Source, one universal clause, no witness");Clause c=e.clauses.get(0);require(c.variables.size()==3&&c.literals.size()==3,"three variables and all disjunctive literals");Atom rel=c.literals.stream().filter(a->a.predicate.equals("rel")).findFirst().get();require(rel.arguments.size()==3&&rel.positive==negative&&rel.arguments.stream().allMatch(a->a.variable>=0)&&rel.arguments.stream().map(a->a.variable).distinct().count()==3,"input variables distinct and sign exact");int u=rel.arguments.get(leading?2:0).variable,v=rel.arguments.get(leading?1:2).variable;Set<Integer> first=new HashSet<>();for(Atom a:c.literals)if(a.predicate.equals("out")){require(a.positive!=negative&&a.arguments.size()==2&&a.arguments.get(1).variable==u,"both alternatives share outer second slot and exact sign");first.add(a.arguments.get(0).variable);}require(first.equals(new HashSet<>(Arrays.asList(u,v))),"diagonal and independent alternatives retained");return c;
    }
    static void one(boolean leading,boolean negative)throws Exception{String s=negative?"~":"",args=leading?"x,v,u":"u,x,v",alpha=leading?"z,w,p":"p,z,w",head="("+s+"out(u,u)||"+s+"out(v,u));";Clause c=capture("!@u @v ($x "+s+"rel("+args+"))->"+head,leading,negative);require(c.equivalent(capture("!@u @v @x "+s+"rel("+args+")->"+head,leading,negative)),"nested and prenex equivalent");require(c.equivalent(capture("!@p @w ($z "+s+"rel("+alpha+"))->("+s+"out(w,p)||"+s+"out(p,p));",leading,negative)),"renaming and head order preserve whole clause");}
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("disjunctive-outer-scope-").toString());for(boolean l:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})one(l,n);System.out.println("CONTEXT_DISJUNCTIVE_OUTER_EXISTENTIAL_SCOPE_PASS checks="+(checks+ContextConjunctiveOuterExistentialScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
