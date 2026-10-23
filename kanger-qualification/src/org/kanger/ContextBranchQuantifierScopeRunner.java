/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.*;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.exception.ParseErrorException;

/** Native branch quantifiers preserve independent variables and accepted language boundaries. */
public final class ContextBranchQuantifierScopeRunner {
    static int checks,ids;static void require(boolean yes,String why){++checks;if(!yes)throw new AssertionError(why);}
    static Mind mind()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    static Clause capture(String text)throws Exception{Mind m=mind();require(m.compile(text,null,false),"native universally quantified declaration");ContextProductionInventory inv=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,false,Collections.emptyList(),1000);ContextProductionNativeBinding.Result b=ContextProductionNativeBinding.bind(m,inv,64000000,500000);require(b.bound,"whole native scope binding: "+b.reason);require(b.snapshot.primary.size()==1&&b.snapshot.primary.get(0).clauses.size()==1,"one primary Source/whole clause");return b.snapshot.primary.get(0).clauses.get(0);}
    static Map<String,Atom> atoms(Clause c){Map<String,Atom> r=new HashMap<>();for(Atom a:c.literals)r.put(a.predicate,a);return r;}
    static void one(boolean negative)throws Exception{String s=negative?"~":"";
        Clause nested=capture("!@x "+s+"out(x)->((@y "+s+"tail(y))||(@z "+s+"extra(z)));");
        Clause prenex=capture("!@x @y @z "+s+"out(x)->"+s+"tail(y)||"+s+"extra(z);");
        Clause renamed=capture("!@u "+s+"out(u)->((@v "+s+"tail(v))||(@w "+s+"extra(w)));");
        Clause diagonal=capture("!@x "+s+"out(x)->(@y ("+s+"tail(y)||"+s+"extra(y)));");
        require(nested.equivalent(prenex),"independent universal branches equal the explicit prenex form");require(nested.equivalent(renamed),"alpha-renaming each binder preserves whole clause");require(!nested.equivalent(diagonal),"one shared binder is not two independent binders");
        Map<String,Atom> a=atoms(nested),d=atoms(diagonal);int x=a.get("out").arguments.get(0).variable,y=a.get("tail").arguments.get(0).variable,z=a.get("extra").arguments.get(0).variable;
        require(nested.variables.size()==3&&x>=0&&y>=0&&z>=0&&x!=y&&x!=z&&y!=z,"antecedent and both branch variables have distinct native identities");require(diagonal.variables.size()==2&&d.get("tail").arguments.get(0).variable==d.get("extra").arguments.get(0).variable&&d.get("out").arguments.get(0).variable!=d.get("tail").arguments.get(0).variable,"diagonal control shares only head variable");
        Mind invalid=mind();boolean rejected=false;try{invalid.compile("!@x "+s+"out(x)->((@y "+s+"tail(y))||(@y "+s+"extra(y)));",null,false);}catch(ParseErrorException e){rejected=e.getMessage().contains("duplicated");}require(rejected,"current grammar explicitly rejects duplicate binder names within one declaration");require(ContextProductionInventory.inspect(invalid,new UUID(0,++ids),1,false,Collections.emptyList(),1000).primary.isEmpty(),"rejected duplicate-name syntax imports no primary assertion");
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("branch-quantifier-scope-").toString());one(false);one(true);System.out.println("CONTEXT_BRANCH_QUANTIFIER_SCOPE_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
