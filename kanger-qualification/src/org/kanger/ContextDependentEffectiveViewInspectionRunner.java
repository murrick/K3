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
        System.out.println("CONTEXT_DEPENDENT_EFFECTIVE_VIEW_INSPECTION_PASS checks="+checks);
    }
}
