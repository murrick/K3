package org.kanger;
import java.nio.file.Files;
import java.lang.reflect.*;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.ICache;
import org.kanger.primitives.*;
import org.kanger.units.*;

/** Built-in objects: skipping a shape-incompatible rule loses binding effects. */
public final class AlphaScanWitness {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private static Object invoke(String name,Class<?>[] types,Object...args)throws Exception {
        Method m=GeneratedCVarMaterializer.class.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(null,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static Domain domain(Predicate p,Rule owner,Mind mind,ITerm...values)throws Exception {
        ArgumentsList args=new ArgumentsList();for(ITerm t:values)args.add(new Argument(t));
        Domain d=new Domain(p,false,args,owner);d.setMind(mind);return d;
    }
    private static String run(boolean narrowed,int mode)throws Exception {
        Mind root=new Mind(new User()),child=new Mind(root);
        ITerm a=root.getTerms().add("a"),b=root.getTerms().add("b"),name=root.getTerms().add("witness");
        Rule target=new Rule(root),other=new Rule(root);target.setId(900001);other.setId(900002);
        target.setMindId(root.getId());other.setMindId(root.getId());
        TVariable shared=root.getTVars().createTVar(other,name);
        root.getTValues().set(shared,new TValue(shared,a));child.getTValues().set(shared,new TValue(shared,b));
        Predicate desired=new Predicate(root.getTerms().add("desired"),2),unrelated=new Predicate(root.getTerms().add("unrelated"),1);
        desired.setId(910001);unrelated.setId(910002);
        ITerm leftC=root.getTerms().createCVar(target,name,null),rightC=root.getTerms().createCVar(target,name,null);
        target.getTree().get(0).add(domain(desired,target,root,rightC,a));target.setStored(root);
        ArgumentsList otherArgs=new ArgumentsList();otherArgs.add(new Argument(shared));
        Domain otherDomain=new Domain(unrelated,false,otherArgs,other);other.getTree().get(0).add(otherDomain);
        if(mode!=2)other.setStored(root);
        other.setMind(root);
        ArgumentsList sourceArgs=new ArgumentsList();sourceArgs.add(new Argument(leftC));sourceArgs.add(new Argument(shared));
        Domain source=new Domain(desired,false,sourceArgs,target);source.setMind(root);
        if(mode==1){Predicate missing=new Predicate(root.getTerms().add("missing"),2);missing.setId(910003);source.setPredicate(missing);}
        Field field=RuleFactory.class.getDeclaredField("cache");field.setAccessible(true);
        ICache cache=(ICache)field.get(root.getRules());cache.add(target);cache.add(other);
        shared.setMind(child);
        check(shared.getMind()==child && shared.getValue()==b,"child projection before search");
        check(other.getDomain().getPredicateId()!=source.getPredicateId(),"skipped rule predicate mismatch");
        check(!source.isQuery(root),"source not a query");
        check((Boolean)invoke("containsCVariable",new Class<?>[]{Domain.class,Mind.class},source,root),"source C-variable gate");
        IRule result;
        if(narrowed){
            // A metadata-filtered singleton hydrates only the matching candidate.
            if(mode==1)result=null;
            else{
                Rule candidate=root.getRules().get(target.getId());
                boolean matches=(Boolean)invoke("alphaEquivalent",new Class<?>[]{Domain.class,Domain.class,Mind.class},source,candidate.getDomain(),root);
                result=matches?candidate:null;
            }
        }else result=GeneratedCVarMaterializer.findAlphaEquivalent(root.getRules(),source,root);
        check(result==(narrowed || mode==1?null:target),"returned match");
        check(shared.getMind()==(narrowed?child:root),"final variable context");
        check(shared.getValue()==(narrowed?b:a),"final visible projection");
        return (result==null?"null":"target")+":"+(shared.getValue()==a?"root-a":"child-b");
    }
    public static void main(String[] ignored)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("alpha-scan-witness-").toString());
        for(int mode=0;mode<3;mode++){
            String reference=run(false,mode),narrowed=run(true,mode);
            check(!reference.equals(narrowed),"observable divergence");
            System.out.println("ALPHA_WITNESS mode="+mode+" reference="+reference+" narrowed="+narrowed);
        }
        System.out.println("ALPHA_SCAN_WITNESS_OK scenarios=3 checks="+checks);
    }
}
