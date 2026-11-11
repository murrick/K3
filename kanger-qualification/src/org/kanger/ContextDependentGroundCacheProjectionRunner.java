/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Full physical guard, primary-only model sources, and separately proved cache coverage. */
public final class ContextDependentGroundCacheProjectionRunner {
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static void reject(Attempt attempt,String why)throws Exception{boolean stopped=false;try{attempt.run();}catch(Exception expected){stopped=true;}require(stopped,why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static ContextProductionInventory inventory(Mind m,int id,boolean local,List<String> init)throws Exception{return ContextProductionInventory.inspect(m,new UUID(0,id),1,local,init,1000);}
    private static ContextProductionInventory prepare(Mind m,ContextProductionInventory inventory)throws Exception{return ContextDependentGroundCacheInspection.prepare(m,inventory,new ContextDependentGroundCacheInspection.Limits(1000,1000,16,4000000,4000000));}
    private static ContextDependentModelSearchInspection.Result model(Member x,List<Cut> cuts)throws Exception{
        return ContextDependentAutomaticInspection.inspect(x,cuts,new ContextDependentIterationInspection.Request(new ContextDependentReplayInspection.Policy(2000000,1000,1000,64000000,200000),10000,8),
                new ContextDependentModelSearchInspection.Limits(30,100000,100000,128,64000000,64000000,64000000,64000000,64000000),
                new ContextDependentRuntimeContinuationInspection.Projection(100000,1000000,64000000,64000000,64000000,64000000));
    }
    private static void restricted()throws Exception{
        User user=new User();new org.kanger.udf.UDF().init(user);Mind q=new Mind(user);user.setCurrentMind(q);
        org.kanger.interfaces.IContextResults.Revision pin=new org.kanger.interfaces.IContextResults.Revision(new UUID(0,6169),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin();TerminalSupportCapture capture=TerminalSupportCapture.begin(q)){
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x seed(x)->hit(x);")),"accepted rule fixture for actual branch restriction");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,1,DmzReplayProvenance.Authority.EXTERNAL,"!seed(1);")),"accepted fact fixture for actual branch restriction");
            ContextProductionInventory prepared=prepare(q,inventory(q,6169,true,Collections.emptyList()));
            DmzCurrentGroundProofInventory proofs=DmzCurrentGroundProofInventory.capture(q,10000,false);require(proofs.eligible,"current proof inventory for restriction control");
            DmzProofWitnesses.Witness selected=null;for(DmzObservedProofGraph.Node node:proofs.graph.observed.nodes)if(node.ground.predicate.equals("hit")){selected=proofs.proofs(node.ground,10000).witnesses.get(0);break;}
            require(selected!=null,"real cache proof selected");
            ContextProductionInventory foreign=prepare(q,inventory(q,6169,false,Collections.emptyList()));
            Mind cold=memory();raw(cold,"!@x seed(x)->hit(x);");raw(cold,"!seed(1);");
            ContextProductionInventory coldInventory=inventory(cold,6169,false,Collections.emptyList());
            require(ContextDependentEffectiveViewInspection.verify(q,foreign,cold,coldInventory,1000,4000000).dependentPrimaryView(),"unrestricted cached cold equivalence control");
            try(DmzTerminalRestriction active=DmzTerminalRestriction.begin(q,selected)){
                reject(()->ContextDependentEffectiveViewInspection.verify(q,foreign,cold,coldInventory,1000,4000000),"cold reconstruction cannot bypass an active cache branch restriction");
                reject(()->model(new Member(q,prepared),Collections.emptyList()),"active branch restriction invalidates unrestricted cached model basis even with unchanged physical rows");
                reject(()->prepare(q,inventory(q,6169,true,Collections.emptyList())),"fresh cache preparation cannot bypass an active restriction");
            }
            require(model(new Member(q,prepared),Collections.emptyList()).status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING,"closing restriction restores the unchanged unrestricted basis");
        }
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("ground-cache-projection-").toString());
        for(boolean foreign:new boolean[]{false,true}){
            Mind source=memory();require(source.compile("!@a $u @b rel(a,u,b); !seed(1); !@x seed(x)->middle(x); !@x middle(x)->tail(x);",null,false),"real native cached source");
            ContextProductionInventory full=inventory(source,6161,!foreign,Collections.emptyList());require(full.generated.size()>=2&&!full.dependentPrimaryView(),"unverified cache remains outside model scope");
            Mind emptyMind=memory();Member empty=new Member(emptyMind,inventory(emptyMind,6162,true,Collections.emptyList()));
            reject(()->model(foreign?empty:new Member(source,full),foreign?Collections.singletonList(new Cut(null,Collections.singletonList(new Member(source,full)))):Collections.emptyList()),"raw inventory cannot silently ignore cached rows");
            String before=DmzObservationStateFingerprint.capture(source);ContextProductionInventory prepared=prepare(source,full);
            require(prepared.dependentPrimaryView()&&prepared.groundCacheProof()!=null&&prepared.rows.size()==full.rows.size()
                    &&prepared.generated.size()==full.generated.size()&&prepared.fingerprint.equals(full.fingerprint),"complete physical inventory and exact fingerprint retained alongside checked cache ledger");
            Member actual=new Member(source,prepared);List<Cut> cuts=foreign?Collections.singletonList(new Cut(null,Collections.singletonList(actual))):Collections.emptyList();
            ContextDependentModelSearchInspection.Result r=model(foreign?empty:actual,cuts);
            require(r.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&r.originalRows==full.primary.size()&&r.finalObligations==r.runtimeObligations,"cached source reaches complete native and indexed family audits: "+r.phase+"/"+r.reason);
            Set<Long> originals=new HashSet<>();for(ContextProductionInventory.Row row:full.primary)originals.add(row.nativeRule);
            for(ContextDependentRoundInspection.Barrier barrier:r.family.checked.barriers)for(ContextDependentProductionInspection.Result cut:barrier.cuts.values())
                for(ContextProductionCapture.Source s:cut.originals)require(originals.contains(s.rule)&&s.view.equals("inventory:"+full.fingerprint),"cache IDs never become original model sources; full physical fingerprint retained");
            require(before.equals(DmzObservationStateFingerprint.capture(source)),"proof preparation and family qualification are read-only");
            require(!inventory(source,6161,!foreign,Collections.emptyList()).dependentPrimaryView(),"reinspection never inherits cache evidence implicitly");
            raw(source,"!unsupported_cache(9);").setGenerated(true);
            reject(()->model(foreign?empty:actual,cuts),"adding an unproved cache invalidates old model basis");
            reject(()->prepare(source,inventory(source,6161,!foreign,Collections.emptyList())),"fresh complete proof coverage refuses the new unsupported cache");
        }
        Mind initialized=memory();require(initialized.compile("!seed(1); !@x seed(x)->hit(x);",null,false),"initialized cache fixture");
        ContextProductionInventory grade=prepare(initialized,inventory(initialized,6163,false,Collections.singletonList("!marker(1);")));
        require(!grade.dependentPrimaryView(),"cache evidence cannot replace an initialized-view cold reconstruction");
        restricted();
        System.out.println("CONTEXT_DEPENDENT_GROUND_CACHE_PROJECTION_PASS checks="+checks);
    }
}
