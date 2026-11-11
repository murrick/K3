/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentCutInspection.Cut;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Effective content must match a complete separate replay before initialized models are attempted. */
public final class ContextDependentEffectiveViewInspectionRunner {
    private static int checks;
    private static final UUID ID=new UUID(0,6141);
    private static final String UNIT="!@a $u @b rel(a,u,b);", MARKER="!private_marker(1);";
    private static final List<String> INIT=Collections.singletonList(MARKER);
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static void reject(Attempt attempt,String why)throws Exception{boolean stopped=false;try{attempt.run();}catch(Exception expected){stopped=true;}require(stopped,why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static ContextProductionInventory inventory(Mind m,List<String> init)throws Exception{return ContextProductionInventory.inspect(m,ID,1,false,init,1000);}
    private static ContextProductionInventory verify(Mind a,ContextProductionInventory x,Mind b,ContextProductionInventory y,int work)throws Exception{return ContextDependentEffectiveViewInspection.verify(a,x,b,y,1000,work);}
    private static ContextDependentModelSearchInspection.Result model(Mind m,ContextProductionInventory inventory)throws Exception{
        Mind local=memory();Member x=new Member(local,ContextProductionInventory.inspect(local,new UUID(0,6140),1,true,Collections.emptyList(),1000));
        return ContextDependentAutomaticInspection.inspect(x,Collections.singletonList(new Cut(null,Collections.singletonList(new Member(m,inventory)))),
                new ContextDependentIterationInspection.Request(new ContextDependentReplayInspection.Policy(2000000,1000,1000,64000000,200000),10000,8),
                new ContextDependentModelSearchInspection.Limits(30,100000,100000,128,64000000,64000000,64000000,64000000,64000000),
                new ContextDependentRuntimeContinuationInspection.Projection(100000,1000000,64000000,64000000,64000000,64000000));
    }
    private static void cached()throws Exception{
        Mind actual=memory(),cold=memory();
        String first="!@x seed(x)->middle(x);",second="!@x middle(x)->tail(x);",seed="!seed(1);";
        require(actual.compile(UNIT+first+second+seed,null,false),"real initialized ground cache fixture");
        raw(cold,seed);raw(cold,second);raw(cold,first);raw(cold,UNIT);
        List<String> init=Collections.singletonList(seed);
        ContextProductionInventory full=inventory(actual,init),plain=inventory(cold,init);
        require(full.generated.size()>=2&&plain.generated.isEmpty(),"same declarations with different legitimate cache population");
        reject(()->verify(actual,full,cold,plain,4000000),"unproved warm cache is not hidden by a matching cold primary set");
        ContextDependentGroundCacheInspection.Limits limits=new ContextDependentGroundCacheInspection.Limits(1000,1000,16,4000000,4000000);
        ContextProductionInventory prepared=ContextDependentGroundCacheInspection.prepare(actual,full,limits);
        require(!prepared.dependentPrimaryView(),"cache proof alone cannot qualify an initialized view");
        ContextProductionInventory verified=verify(actual,prepared,cold,plain,4000000);
        require(verified.dependentPrimaryView()&&verified.groundCacheProof()==prepared.groundCacheProof()
                &&verified.fingerprint.equals(full.fingerprint)&&verified.generated.size()==full.generated.size(),"cold equivalence composes with actual full cache coverage without changing working rows or sources");
        require(model(actual,verified).status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING,"composed initialized-cache receipt reaches complete model audits");
        raw(cold,"!middle(1);").setGenerated(true);
        ContextProductionInventory sparse=ContextDependentGroundCacheInspection.prepare(cold,inventory(cold,init),limits);
        require(sparse.generated.size()==1&&verify(actual,prepared,cold,sparse,4000000).dependentPrimaryView(),"both independently proved cache populations may differ while primary declarations remain equivalent");
        raw(cold,"!forged(9);").setGenerated(true);
        reject(()->verify(actual,prepared,cold,sparse,4000000),"old cold cache receipt invalidates when reconstruction changes");
        reject(()->verify(actual,prepared,cold,inventory(cold,init),4000000),"a fresh unverified cold cache cannot reuse the warm proof");
        reject(()->ContextDependentGroundCacheInspection.prepare(cold,inventory(cold,init),limits),"unproved extra cold cache refuses composition");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("effective-dependent-view-").toString());
        Mind actual=memory(),cold=memory();raw(actual,UNIT);raw(actual,MARKER);raw(cold,MARKER);raw(cold,UNIT);
        ContextProductionInventory expected=inventory(actual,INIT),replay=inventory(cold,INIT);
        String before=DmzObservationStateFingerprint.capture(actual);int terms=actual.getTerms().size();
        require(!expected.dependentPrimaryView(),"metadata alone does not authorize initialized content");
        reject(()->model(actual,expected),"unverified initialized view still refused by the full model path");
        ContextProductionInventory verified=verify(actual,expected,cold,replay,1000000);
        require(verified.dependentPrimaryView()&&verified.fingerprint.equals(expected.fingerprint)&&verified.rows.size()==2,"complete reordered cold replay retains actual fingerprint and all native rows");
        ContextDependentModelSearchInspection.Result result=model(actual,verified);
        require(result.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&result.originalRows==2&&result.finalObligations==result.runtimeObligations,"verified initialized originals reach native and indexed audits: "+result.phase+"/"+result.reason);
        require(terms==actual.getTerms().size()&&before.equals(DmzObservationStateFingerprint.capture(actual)),"verification and modeling do not mutate actual state");
        reject(()->verify(actual,expected,actual,expected,1000000),"same factory cannot stand in for an independent cold replay");
        reject(()->verify(actual,expected,cold,inventory(cold,Collections.singletonList("!private_marker(2);")),1000000),"recipe identity is exact");
        reject(()->verify(actual,expected,cold,replay,1),"work limit cannot produce a partial verification");
        Mind missing=memory();raw(missing,UNIT);reject(()->verify(actual,expected,missing,inventory(missing,INIT),1000000),"missing initialized declaration refused");
        Mind wrong=memory();Rule altered=raw(wrong,"!@a @b $u rel(a,u,b);");altered.setOrigin(new Term("\""+UNIT+"\"",wrong));raw(wrong,MARKER);
        reject(()->verify(actual,expected,wrong,inventory(wrong,INIT),1000000),"matching origin strings cannot hide different native witness dependencies");
        Mind generated=memory();raw(generated,UNIT);Rule g=raw(generated,MARKER);g.setGenerated(true);
        reject(()->verify(actual,expected,generated,inventory(generated,INIT),1000000),"generated rows remain explicitly outside this primary-view receipt");
        ContextProductionInventory reinspected=inventory(actual,INIT);require(!reinspected.dependentPrimaryView(),"fresh inventory does not inherit a previous receipt implicitly");
        raw(actual,"!changed(1);");reject(()->model(actual,verified),"changed actual content invalidates the verified view");
        cached();
        System.out.println("CONTEXT_DEPENDENT_EFFECTIVE_VIEW_INSPECTION_PASS checks="+checks);
    }
}
