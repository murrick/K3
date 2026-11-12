/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.units.*;
import org.kanger.ContextDependentSymbolicCacheSnapshot.*;

public final class ContextDependentSymbolicCacheSnapshotRunner {
    private static int checks;
    private static final UUID CONTEXT=new UUID(0,624);
    private static final String DECLARATIONS="!$u rel(1,u,1); !@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);";
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory(String declarations)throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);need(m.compile(declarations,null,false),"snapshot fixture declarations");return m;}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,CONTEXT,1,true,Collections.emptyList(),1000);}
    private static boolean refuses(Snapshot snapshot,Mind mind,ContextProductionInventory inventory)throws Exception{try{snapshot.requireCurrent(mind,inventory);return false;}catch(IllegalStateException expected){return true;}}
    private static void binding()throws Exception{
        Mind m=memory(DECLARATIONS);ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);Result result=ContextDependentSymbolicCacheSnapshot.capture(m,inventory);
        need(result.status==Status.BOUND&&result.snapshot!=null,"fresh matching symbolic cache snapshot: "+result.reason);Snapshot snapshot=result.snapshot;snapshot.requireCurrent(m,inventory);snapshot.requireCurrent(m,inventory(m));need(snapshot.inventory==inventory&&snapshot.evidence.ledger.productRows==2,"complete immutable original inventory and checked evidence retained");
        need(before.equals(DmzObservationStateFingerprint.capture(m))&&!inventory.dependentPrimaryView()&&inventory.groundCacheProof()==null,"snapshot binding is read-only and cannot promote the primary view");
        boolean groundRefused=false;try{ContextDependentGroundCacheInspection.prepare(m,inventory,new ContextDependentGroundCacheInspection.Limits(1000,1000,8,1000000,1000000));}catch(IllegalStateException expected){groundRefused=true;}need(groundRefused,"symbolic snapshot is not a ground-cache receipt");
        byte[] packet=snapshot.evidence.proof.bytes();packet[0]^=1;need(snapshot.evidence.proof.bytes()[0]!=packet[0],"packet accessor cannot mutate frozen evidence");boolean immutable=false;try{snapshot.evidence.ledger.occurrences.clear();}catch(UnsupportedOperationException expected){immutable=true;}need(immutable,"joint evidence immutable");
        need(refuses(snapshot,m,ContextProductionInventory.inspect(m,new UUID(0,625),1,true,Collections.emptyList(),1000)),"different context identity refuses snapshot reuse");need(refuses(snapshot,m,ContextProductionInventory.inspect(m,CONTEXT,2,true,Collections.emptyList(),1000)),"different revision refuses snapshot reuse");need(refuses(snapshot,m,ContextProductionInventory.inspect(m,CONTEXT,1,false,Collections.emptyList(),1000)),"different local/foreign role refuses reuse");need(refuses(snapshot,m,ContextProductionInventory.inspect(m,CONTEXT,1,true,Collections.singletonList("!private(9);"),1000)),"different declared initialization refuses reuse");
        Mind other=memory(DECLARATIONS);ContextProductionInventory cloned=inventory(other);need(inventory.fingerprint.equals(cloned.fingerprint),"control runtime has identical complete inventory fingerprint");need(refuses(snapshot,other,cloned),"identical logical inventory is not authority for another runtime");
        need(m.compile("!unrelated(9);",null,false),"actual primary mutation");need(refuses(snapshot,m,inventory)&&refuses(snapshot,m,inventory(m)),"both old and newly inspected inventories reject the stale snapshot");
        need(ContextDependentSymbolicCacheSnapshot.capture(m,inventory(m)).status==Status.BOUND,"fresh proof can bind a changed but independently supported primary inventory");
    }
    private static void mutation(boolean cause)throws Exception{
        Mind m=memory(DECLARATIONS+" !marker(9);");ContextProductionInventory inventory=inventory(m);Snapshot snapshot=ContextDependentSymbolicCacheSnapshot.capture(m,inventory).snapshot;need(snapshot!=null,"snapshot before actual cache mutation");Rule generated=null;ITerm nine=null;for(IRule item:m.getRules()){Rule row=(Rule)item;if(item.isGenerated())generated=row;else if(row.getDomain().getPredicate(m).getName(m).equals("marker"))nine=row.getDomain().getArguments().get(0).getValue(m);}need(generated!=null&&nine!=null,"actual cached row and ordinary value");
        if(cause)generated.getCauses().clear();else generated.getDomain().getArguments().set(0,new org.kanger.primitives.Argument(nine));
        need(refuses(snapshot,m,inventory)&&refuses(snapshot,m,inventory(m)),"changed "+(cause?"cause":"cached value")+" invalidates old checked snapshot");Result fresh=ContextDependentSymbolicCacheSnapshot.capture(m,inventory(m));need(fresh.status==Status.INCOMPLETE&&fresh.snapshot==null,"fresh unsupported actual state gets no snapshot");
    }
    private static void aliases()throws Exception{
        for(boolean full:new boolean[]{false,true}){
            String root=full?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);";Mind m=memory(root+" !@u rel(1,u,1)->out(1,u); !@u rel(2,u,1)->out(2,u);");ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);
            need(ContextDependentCacheProofInspection.inspect(m,inventory).status==ContextDependentCacheProofInspection.Status.INSPECTED,"valid declaration proofs still expose a native alias mismatch");Result result=ContextDependentSymbolicCacheSnapshot.capture(m,inventory);need(result.status==Status.INCOMPLETE&&result.snapshot==null&&result.reason.contains("joint witness identities"),"conflated/split native symbols cannot obtain a reusable snapshot");need(before.equals(DmzObservationStateFingerprint.capture(m)),"mismatched snapshot refusal is read-only");
        }
    }
    private static void restricted()throws Exception{
        Mind m=memory(DECLARATIONS);ContextProductionInventory inventory=inventory(m);Snapshot snapshot=ContextDependentSymbolicCacheSnapshot.capture(m,inventory).snapshot;need(snapshot!=null,"unrestricted snapshot before branch scope");
        User user=new User();new org.kanger.udf.UDF().init(user);Mind q=new Mind(user);user.setCurrentMind(q);org.kanger.interfaces.IContextResults.Revision pin=new org.kanger.interfaces.IContextResults.Revision(new UUID(0,6249),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin();TerminalSupportCapture capture=TerminalSupportCapture.begin(q)){
            need(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x seed(x)->hit(x);")),"real restriction rule");need(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,1,DmzReplayProvenance.Authority.EXTERNAL,"!seed(1);")),"real restriction seed");
            DmzCurrentGroundProofInventory proofs=DmzCurrentGroundProofInventory.capture(q,10000,false);need(proofs.eligible,"restriction proof inventory");DmzProofWitnesses.Witness selected=null;for(DmzObservedProofGraph.Node node:proofs.graph.observed.nodes)if(node.ground.predicate.equals("hit")){selected=proofs.proofs(node.ground,10000).witnesses.get(0);break;}need(selected!=null,"real no-good witness");
            try(DmzTerminalRestriction active=DmzTerminalRestriction.begin(m,selected)){
                need(inventory.fingerprint.equals(inventory(m).fingerprint),"branch restriction is not a physical inventory mutation");need(refuses(snapshot,m,inventory),"active restriction cannot reuse unrestricted snapshot despite identical fingerprint");boolean rejected=false;try{ContextDependentSymbolicCacheSnapshot.capture(m,inventory);}catch(IllegalStateException expected){rejected=true;}need(rejected,"active branch cannot manufacture an unrestricted snapshot");
            }
            snapshot.requireCurrent(m,inventory);need(inventory.fingerprint.equals(inventory(m).fingerprint),"closing restriction restores only the unchanged unrestricted runtime check");
        }
    }
    public static void main(String[] args)throws Exception{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("symbolic-cache-snapshot-").toString());binding();mutation(false);mutation(true);aliases();restricted();System.out.println("CONTEXT_DEPENDENT_SYMBOLIC_CACHE_SNAPSHOT_PASS checks="+checks);}
}
