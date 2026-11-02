/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.NativeDependentMatchFrame.*;
import org.kanger.NativeDependentWitnessTerms.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Whole-chain materialization handoff; no persistence or default writer activation. */
public final class NativeDependentMaterializationRunner {
    private static int checks;
    private static final UUID CONTEXT=new UUID(0,506);
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule compile(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static NativeDependentMatchFrame open(Mind m)throws Exception{return NativeDependentMatchFrame.open(m,CONTEXT,9,true,Collections.emptyList(),100,40,10000000,50000);}
    private static String fingerprint(Mind m)throws Exception{return ContextProductionInventory.inspect(m,CONTEXT,9,true,Collections.emptyList(),100).fingerprint;}
    private static void refinement()throws Exception{
        for(String fixed:new String[]{"2","\"two\""})for(boolean reverse:new boolean[]{false,true})for(boolean pivotReverse:new boolean[]{false,true}){
            Mind m=memory();Rule p,q,n;
            String constraint=pivotReverse?"!@a @u ~(q(a)&&rel(a,u,1));":"!@a @u ~(rel(a,u,1)&&q(a));";
            if(reverse){n=compile(m,constraint);q=compile(m,"!q("+fixed+");");p=compile(m,"!@a $u @b rel(a,u,b);");}
            else{p=compile(m,"!@a $u @b rel(a,u,b);");q=compile(m,"!q("+fixed+");");n=compile(m,constraint);}
            String before=fingerprint(m);Materialization complete;NativeDependentMatchFrame f=open(m);
            try{
                Match first=f.match(f.entry(p),f.instantiate(f.entry(n),0),pivotReverse?1:0);
                Materialization partial=f.materialize(first);f.verify(partial);
                Node pending=partial.producers.get(0).arguments.get(1);
                require(pending.kind==Kind.APPLICATION&&pending.arguments.get(0).kind==Kind.VARIABLE,"partial dependency stays symbolic");
                Match last=f.match(f.entry(q),first.after,0);complete=f.materialize(last);f.verify(complete);
                Node value=complete.producers.get(1).arguments.get(0),witness=complete.producers.get(0).arguments.get(1);
                require(value.kind==Kind.VALUE,"later pivot supplies an ordinary typed value");
                require(witness.kind==Kind.APPLICATION&&witness.arguments.size()==1&&witness.arguments.get(0)==value,"later substitution reaches earlier witness dependency");
                require(complete.producers.get(0).arguments.get(0)==value,"earlier whole producer is refined too");
                require(complete.producers.get(0).binders.get(1)==witness,"declaration/body witness identity preserved");
                require(complete.receiver.get(pivotReverse?1:0).arguments.equals(complete.producers.get(0).arguments),"final receiver pivot agrees with fully composed producer");
                require(complete.remaining.isEmpty()&&complete.support.depth==2&&complete.support.sources.size()==3,"full Sources and grade accompany closed branch");
                require(complete.producerSources.equals(Arrays.asList(f.entry(p).source,f.entry(q).source))&&complete.receiverSource.equals(f.entry(n).source)&&complete.receiverBranch==0,"each materialized row retains exact whole original Source");
                require(pending.arguments.get(0).kind==Kind.VARIABLE,"later handoff never mutates earlier snapshot");
                f.verify(partial);
                try{complete.producers.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
                try{complete.receiver.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
                require(before.equals(fingerprint(m)),"handoff leaves native state untouched");
                try(NativeDependentMatchFrame other=open(m)){boolean refused=false;try{other.verify(complete);}catch(IllegalStateException expected){refused=true;}require(refused,"other frame cannot authenticate detached nodes");}
            }finally{f.close();}
            boolean closed=false;try{f.verify(complete);}catch(IllegalStateException expected){closed=true;}require(closed,"closed handoff is historical data only");
        }
    }
    private static void unresolvedAndStale()throws Exception{
        Mind m=memory();Rule p=compile(m,"!@a $u @b rel(a,u,b);"),n=compile(m,"!@a @u ~(rel(a,u,1)&&rel(a,u,2));");
        try(NativeDependentMatchFrame f=open(m)){
            Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0);Match b=f.match(f.entry(p),a.after,0);
            Materialization result=f.materialize(b);f.verify(result);
            require(result.producers.get(0).arguments.get(1)==result.producers.get(1).arguments.get(1),"same application shared across both producers");
            require(result.producers.get(0).arguments.get(0).kind==Kind.VARIABLE,"closed refutation needs no invented ground parameter");
            require(result.producers.get(0).arguments.get(2)!=result.producers.get(1).arguments.get(2),"different later universal values survive composition");
            compile(m,"!extra(1);");boolean stale=false;try{f.verify(result);}catch(IllegalStateException expected){stale=true;}require(stale,"whole inventory mutation invalidates handoff");
        }
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-materialization-").toString());
        refinement();unresolvedAndStale();System.out.println("NATIVE_DEPENDENT_MATERIALIZATION_PASS checks="+checks);
    }
}
