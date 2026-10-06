package org.kanger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import org.kanger.enums.FunctionBinding;
import org.kanger.enums.LibMode;
import org.kanger.interfaces.*;
import org.kanger.primitives.*;
import org.kanger.units.*;

/** Native lifecycle observations only; never schedules or suppresses work. */
public final class DependencyLifecycleWitness {
    private static int checks;
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void reset(Mind m){m.getRules().dropAction();m.getTValues().dropAction();m.getFValues().dropAction();m.getHypothesis().dropAction();m.getTempHypothesis().dropAction();}
    private static boolean quiet(Mind m){return !m.getRules().isAction()&&!m.getTValues().isAction()&&!m.getFValues().isAction()&&!m.getHypothesis().isAction()&&!m.getTempHypothesis().isAction();}
    private static List<String> values(Mind m,TVariable v)throws Exception{
        List<String> result=new ArrayList<>();
        m.getTValues().forEach(v,o->{result.add(((TValue)o).getValue(m).toString());return true;});
        return result;
    }
    private static Method method(String name,Class<?>...types)throws Exception{Method m=Linker.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("dependency-lifecycle-").toString());
        nestedTValues();nestedFValues();currentAndStampedMarks();excludedAlias();functionEffects();
        System.out.println("DEPENDENCY_LIFECYCLE_OK checks="+checks);
    }
    private static void nestedTValues()throws Exception{
        Mind m=new Mind(new User());Rule owner=new Rule(m);
        TVariable x=m.getTVars().createTVar(owner,m.getTerms().add("x"));
        TValue base=m.getTValues().add(x,m.getTerms().add("base"));x.setCurrent(base);reset(m);
        m.getTValues().mark();TValue outer=m.getTValues().add(x,m.getTerms().add("outer"));
        m.getTValues().mark();m.getTValues().add(x,m.getTerms().add("inner"));
        require(values(m,x).equals(Arrays.asList("base","outer","inner")),"nested native enumeration order");
        m.getTValues().commit();require(values(m,x).size()==3,"inner commit remains provisional to outer");
        x.setCurrent(outer);m.getTValues().release();
        require(values(m,x).equals(Collections.singletonList("base")),"outer release removes both additions and index entries");
        require(m.getTValues().size()==1&&m.getTValues().find(x,m.getTerms().add("outer"))==null,"canonical additions rolled back");
        require(quiet(m),"rollback restores continuation flags");
        require(x.getCurrent()==outer,"current projection is outside canonical checkpoint journal");
        x.setCurrent(base);require(x.getCurrent()==base,"caller reestablishes current projection");
        m.getTValues().mark();m.getTValues().add(x,m.getTerms().add("surviving"));m.getTValues().commit();
        require(values(m,x).equals(Arrays.asList("base","surviving")),"top-level checkpoint commit survives");
        require(m.getTValues().isAction(),"surviving canonical addition signals continuation");
        require(m.pendingTransactionCount()==0,"TValue context settled");
        System.out.println("NESTED_TVALUE committed_inner_then_released_outer=[base] action=false transient_current=outer surviving_commit=[base,surviving]");
    }
    private static void nestedFValues()throws Exception{
        Mind m=new Mind(new User());ArgumentsList args=new ArgumentsList();args.add(new Argument(m.getTerms().add(1.0)));
        Function f=m.getFunctions().add(m.getTerms().add("lifecycle_fvalue"),args);
        require(f.setParameter(f.getRange(),m.getTerms().add(2.0)),"baseline FValue parameter");
        FValue baseline=m.getFValues().add(f);reset(m);
        m.getFValues().mark();f.clear();require(f.setParameter(f.getRange(),m.getTerms().add(3.0)),"outer FValue parameter");
        FValue outer=m.getFValues().add(f);
        m.getFValues().mark();f.clear();require(f.setParameter(f.getRange(),m.getTerms().add(4.0)),"inner FValue parameter");
        FValue inner=m.getFValues().add(f);require(m.getFValues().size()==3,"nested canonical FValues");
        m.getFValues().commit();m.getFValues().release();
        require(m.getFValues().size()==1&&m.getFValues().get(outer.getId())==null&&m.getFValues().get(inner.getId())==null,"outer release removes nested FValues");
        require(quiet(m),"FValue rollback restores flags");
        require(f.getResult().getValue(m).getValue().equals(4.0),"transient result slot is not checkpointed");
        f.clear();require(f.setParameter(f.getRange(),m.getTerms().add(2.0)),"baseline FValue projection restored");
        require(m.getFValues().find(f)==baseline,"canonical baseline remains applicable after explicit projection restore");
        require(m.pendingTransactionCount()==0,"FValue context settled");
        System.out.println("NESTED_FVALUE committed_inner_then_released_outer=baseline action=false transient_result=4.0 baseline_lookup_restored=true");
    }
    private static void currentAndStampedMarks()throws Exception{
        Mind m=new Mind(new User());Rule r=(Rule)m.compileLine("!@x facet(x);",false,null);
        require(r!=null,"native stamped rule compiles");Domain d=r.getTree().get(0).get(0);
        TVariable x=d.getArguments().getTVariables(m).iterator().next();
        TValue a=m.getTValues().add(x,m.getTerms().add("a")),b=m.getTValues().add(x,m.getTerms().add("b"));
        x.setCurrent(a);d.setExcluded(d.getArguments(),m);reset(m);long version=m.ruleSolvesVersion();
        require(d.isExcluded(m),"a stamp excluded");List<String> registry=values(m,x);
        x.setCurrent(b);require(!d.isExcluded(m),"same domain with b stamp is not excluded");
        require(values(m,x).equals(registry)&&quiet(m)&&version==m.ruleSolvesVersion(),"current switch leaves registry, flags and TSolve version stable");
        m.getTValues().getCurrent().put(x,a);require(d.isExcluded(m),"public current-map alias restores a stamp");
        require(quiet(m)&&version==m.ruleSolvesVersion(),"current alias bypasses all checked continuation/version signals");
        require(m.getExcludedDomains().get(d).size()==1,"same single stored exclusion stamp throughout");
        require(m.pendingTransactionCount()==0,"stamped context settled");
        System.out.println("CURRENT_STAMP registry_unchanged=true domain_marks_unchanged=true action=false tsolve_version_unchanged=true exclusion=[true,false,true] alias=true");
    }
    private static void excludedAlias()throws Exception{
        Mind m=new Mind(new User());Rule r=(Rule)m.compileLine("!premise(item) -> conclusion(item);",false,null);
        require(r!=null&&r.getTree().get(0).size()==2,"native alias clause compiles");
        Domain premise=r.getTree().get(0).get(0);premise.setExcluded(premise.getArguments(),m);reset(m);
        Linker linker=new Linker(m);Method db=method("linkDatabase",List.class,Map.class,Set.class,boolean.class);
        Map<IRule,Set<Cause>> causes=new HashMap<>();Set<TVariable> vars=new TreeSet<>();long version=m.ruleSolvesVersion();
        require((Boolean)db.invoke(linker,r.getTree().get(0),causes,vars,false),"excluded clause produces remaining domain");
        require(!m.getProducedDomains().isEmpty(),"native deferred output recorded");
        m.getProducedDomains().clear();m.getDomainCauses().clear();m.getDomainSolves().clear();
        m.getExcludedDomains().get(premise).clear();
        require(!premise.isExcluded(m),"public domain-state alias removes exclusion");
        require(quiet(m)&&version==m.ruleSolvesVersion(),"domain alias mutation has no continuation flag or TSolve version change");
        require(m.getTempHypothesis().size()==0,"classification begins without provisional hypotheses");
        require(!(Boolean)db.invoke(linker,r.getTree().get(0),causes,vars,false)&&m.getProducedDomains().isEmpty(),"same clause stops producing after alias removal");
        require(m.getTempHypothesis().isAction()&&m.getTempHypothesis().size()==2,"false classification result still publishes two provisional hypotheses");
        require(version==m.ruleSolvesVersion(),"classification does not change TSolve version");
        require(m.pendingTransactionCount()==0,"domain alias context settled");
        System.out.println("EXCLUDED_ALIAS same_rule_and_values=true alias_action=false tsolve_version_unchanged=true produced=[true,false] second_return=false second_temp_hypotheses=2 second_temp_action=true");
    }
    private static void functionEffects()throws Exception{
        Mind m=new Mind(new User());final int[] calls={0,0};
        Operation op=new Operation(LibMode.FUNCTION,"lifecycle_callback",1,o->{calls[0]++;return -1;});
        op.getParams().add("x");op.getParams().add("lifecycle_callback");op.getScripts().add("// native diagnostic callback");
        m.getLibrary().add(op);
        Rule r=(Rule)m.compileLine("!@x effect(lifecycle_callback(x));",false,null);
        require(r!=null,"native function rule compiles");Domain d=r.getTree().get(0).get(0);
        Collection<Function> functions=d.getArguments().getFunctions(m);require(functions.size()==1,"one native function occurrence");
        Function f=functions.iterator().next();require(f.getBinding()==FunctionBinding.UDF_DYNAMIC&&f.isCalculable(),"dynamic native calculable function");
        TVariable x=f.getArguments().getTVariables(m).iterator().next();x.setValue(m.getTerms().add(1.0));reset(m);
        Linker linker=new Linker(m);Map<IRule,Set<Cause>> causes=new HashMap<>();long version=m.ruleSolvesVersion();int fv=m.getFValues().size();
        require(!linker.calcFunctions(r.getTree().get(0),causes,false),"empty-result callback creates no canonical effect");
        require(!linker.calcFunctions(r.getTree().get(0),causes,false),"same function runs again while unresolved");
        require(calls[0]==2&&calls[1]==0,"two externally observable callback invocations");
        require(f.isEmpty(m)&&m.getFValues().size()==fv&&quiet(m)&&version==m.ruleSolvesVersion(),"native inference counters unchanged despite callback effects");
        Operation nativeOp=(Operation)m.getLibrary().find("lifecycle_callback(1)");nativeOp.setProc(o->{calls[1]++;return -1;});
        require(!linker.calcFunctions(r.getTree().get(0),causes,false),"mutated callback dispatches without new result");
        require(calls[0]==2&&calls[1]==1,"operation object mutation changes resolved callback");
        require(quiet(m)&&version==m.ruleSolvesVersion()&&f.isEmpty(m),"operation alias mutation bypasses checked signals");
        require(m.pendingTransactionCount()==0,"callback context settled");
        System.out.println("FUNCTION_CALLBACK native_dynamic=true action=false tsolve_version_unchanged=true canonical_results=0 calls=[2,1] unresolved_reexecution=true mutable_operation=true");
    }
}
