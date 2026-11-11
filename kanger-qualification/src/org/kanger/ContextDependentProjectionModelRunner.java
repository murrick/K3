/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentModelInspection.Choice;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Automatic guesses must explore real dependency positions and retain the general fallback. */
public final class ContextDependentProjectionModelRunner {
    private static int checks;
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static SemanticTermSnapshot value(String text)throws Exception{Mind m=memory();Rule r=raw(m,"!value("+text+");");return SemanticTermSnapshot.capture(r.getDomain().get(0).getValue(m));}
    private static Member member(Mind m,int id)throws Exception{return new Member(m,ContextProductionInventory.inspect(m,new UUID(0,id),1,true,Collections.emptyList(),1000));}
    private static ContextDependentModelSearchInspection.Request request(int choices)throws Exception{
        return new ContextDependentModelSearchInspection.Request(new ContextDependentIterationInspection.Request(new ContextDependentReplayInspection.Policy(2000000,1000,1000,64000000,200000),10000,8),Collections.emptyList(),value("9"),
                new ContextDependentModelSearchInspection.Limits(10,100000,choices,64,64000000,64000000,64000000,64000000,64000000),
                new ContextDependentRuntimeContinuationInspection.Projection(10000,1000000,64000000,64000000,64000000,64000000));
    }
    private static void lateProjection(int width,boolean fixed)throws Exception{
        Mind m=memory();StringBuilder prefix=new StringBuilder("!"),constraint=new StringBuilder("!");
        List<String> args=new ArrayList<>(),left=new ArrayList<>(),right=new ArrayList<>();
        if(fixed){args.add("\"fixed\"");left.add("\"fixed\"");right.add("\"fixed\"");}
        for(int i=0;i<width;i++){
            prefix.append("@x").append(i).append(' ');args.add("x"+i);
            if(i+1<width){constraint.append("@x").append(i).append(' ');left.add("x"+i);right.add("x"+i);}
            else{left.add("1");right.add("2");}
        }
        args.add("u");left.add("u");right.add("u");
        Rule root=raw(m,prefix+"$u rel("+String.join(",",args)+");");
        raw(m,constraint+"@u ~(rel("+String.join(",",left)+")&&rel("+String.join(",",right)+"));");
        Member x=member(m,6120+width+(fixed?100:0));String before=DmzObservationStateFingerprint.capture(m);
        ContextDependentModelSearchInspection.Result r=ContextDependentModelSearchInspection.inspect(x,Collections.emptyList(),request(100000));
        require(r.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&r.originalRows==2&&r.finalObligations==r.runtimeObligations,
                "automatic last-dependency model, width="+width+", fixed="+fixed+": "+r.phase+"/"+r.reason);
        Source source=new Source(x.inventory.context,1,root.getId(),true,"inventory:"+x.inventory.fingerprint);
        Choice choice=r.models.values().iterator().next().functions.get(new Function(source,width));
        List<Integer> allZero=new ArrayList<>(Collections.nCopies(width,0)),lastOne=new ArrayList<>(allZero);lastOne.set(width-1,1);
        require(choice!=null&&choice.arity==width&&choice.exceptions.getOrDefault(allZero,choice.otherwise)==0&&choice.exceptions.getOrDefault(lastOne,choice.otherwise)==1,
                "selected witness actually depends on last universal, not just the first");
        require(before.equals(DmzObservationStateFingerprint.capture(m)),"automatic guessing does not assign native witnesses");
    }
    private static void fallback()throws Exception{
        Mind m=memory();Rule root=raw(m,"!$u p(u);");raw(m,"!~p(1);");Member x=member(m,6130);
        ContextDependentModelSearchInspection.Result r=ContextDependentModelSearchInspection.inspect(x,Collections.emptyList(),request(100000));
        require(r.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&r.tried>5,"failed constant-zero projection retains full table search: "+r.phase+"/"+r.reason);
        Source source=new Source(x.inventory.context,1,root.getId(),true,"inventory:"+x.inventory.fingerprint);
        Choice choice=r.models.values().iterator().next().functions.get(new Function(source,0));
        require(choice.arity==0&&choice.exceptions.getOrDefault(Collections.emptyList(),choice.otherwise)==1,"fallback picks a class outside the forbidden named value");
        Mind wider=memory();raw(wider,"!@a $u p(a,u);");raw(wider,"!@u ~(p(1,u)&&p(2,u));");
        ContextDependentModelSearchInspection.Result limited=ContextDependentModelSearchInspection.inspect(member(wider,6131),Collections.emptyList(),request(2));
        require(limited.status==ContextDependentModelSearchInspection.Status.INCOMPLETE&&"table-search".equals(limited.phase)
                &&limited.reason.contains("BEFORE tuple allocation")&&limited.models.isEmpty()&&limited.family==null&&limited.frame==null,
                "oversized projection skips to bounded general search; no false refutation or partial receipt: "+limited.phase+"/"+limited.reason);
    }
    private static void interruption()throws Exception{
        Map<Function,Integer> declarations=Collections.singletonMap(new Function(new Source(new UUID(0,6190),1,0,true,"test"),0),2);
        int[] work={0};boolean stopped=false;
        try{
            ContextDependentProjectionModel.propose(Arrays.asList(value("1"),value("2")),value("9"),Collections.singleton(new org.kanger.ContextDependentModelInspection.Signature("p",1)),
                    declarations,Collections.emptyList(),Collections.emptyList(),0,100,100,()->{if(++work[0]==5)Thread.currentThread().interrupt();});
        }catch(InterruptedException expected){stopped=Thread.currentThread().isInterrupted();}
        finally{Thread.interrupted();}
        require(stopped,"interruption inside projection-table construction throws and preserves the flag");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("dependent-projection-model-").toString());
        lateProjection(2,false);lateProjection(7,false);lateProjection(2,true);fallback();interruption();
        System.out.println("CONTEXT_DEPENDENT_PROJECTION_MODEL_PASS checks="+checks);
    }
}
