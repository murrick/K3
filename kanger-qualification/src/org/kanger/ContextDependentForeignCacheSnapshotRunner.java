/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
public final class ContextDependentForeignCacheSnapshotRunner {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);need(m.compile("!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u); !$u rel(1,u,1);",null,false),"foreign-role fixture");return m;}
    public interface Checked {void run()throws Exception;}
    /** A real no-good scope for provider-source tests in the storage package. */
    public static void withNoGood(Mind target,Checked action)throws Exception{
        User u=new User();new org.kanger.udf.UDF().init(u);Mind q=new Mind(u);u.setCurrentMind(q);org.kanger.interfaces.IContextResults.Revision pin=new org.kanger.interfaces.IContextResults.Revision(new java.util.UUID(0,6269),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin();TerminalSupportCapture capture=TerminalSupportCapture.begin(q)){
            if(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x seed(x)->hit(x);"))||!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,1,DmzReplayProvenance.Authority.EXTERNAL,"!seed(1);")))throw new AssertionError("No-good fixture failed");
            DmzCurrentGroundProofInventory proofs=DmzCurrentGroundProofInventory.capture(q,10000,false);if(!proofs.eligible)throw new AssertionError("No-good inventory ineligible");DmzProofWitnesses.Witness selected=null;for(DmzObservedProofGraph.Node node:proofs.graph.observed.nodes)if(node.ground.predicate.equals("hit")){selected=proofs.proofs(node.ground,10000).witnesses.get(0);break;}if(selected==null)throw new AssertionError("No-good witness absent");
            try(DmzTerminalRestriction restriction=DmzTerminalRestriction.begin(target,selected)){action.run();}
        }
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("foreign-cache-role-").toString());Mind a=memory(),b=memory();List<String> recipe=Collections.singletonList("!$u rel(1,u,1);");java.util.UUID id=new java.util.UUID(0,626);
        ContextProductionInventory left=ContextProductionInventory.inspect(a,id,7,false,recipe,1000),right=ContextProductionInventory.inspect(b,id,7,false,recipe,1000);ContextDependentSymbolicCacheSnapshot.Result warm=ContextDependentSymbolicCacheSnapshot.capture(a,left),cold=ContextDependentSymbolicCacheSnapshot.capture(b,right);need(warm.status==ContextDependentSymbolicCacheSnapshot.Status.BOUND&&cold.status==ContextDependentSymbolicCacheSnapshot.Status.BOUND,"foreign metadata can be checked without promoting it to local-X authority");ContextDependentSymbolicCacheEquivalence.verify(a,warm.snapshot,b,cold.snapshot);need(!left.localX&&!right.localX&&left.initialization.equals(recipe)&&!left.dependentPrimaryView()&&!right.dependentPrimaryView(),"foreign role, full recipe and raw inventory boundary retained");
        ContextProductionInventory relabeled=ContextProductionInventory.inspect(a,id,7,true,recipe,1000);boolean refused=false;try{warm.snapshot.requireCurrent(a,relabeled);}catch(IllegalStateException expected){refused=true;}need(refused,"local role relabel cannot reuse a foreign snapshot");ContextDependentSymbolicCacheSnapshot.Snapshot wrongRecipe=ContextDependentSymbolicCacheSnapshot.capture(b,ContextProductionInventory.inspect(b,id,7,false,Collections.singletonList("!other(9);"),1000)).snapshot;refused=false;try{ContextDependentSymbolicCacheEquivalence.verify(a,warm.snapshot,b,wrongRecipe);}catch(IllegalStateException expected){refused=true;}need(refused,"individually valid current inventories with different recipes are not cold-equivalent");
        System.out.println("CONTEXT_DEPENDENT_FOREIGN_CACHE_SNAPSHOT_PASS checks="+checks);
    }
}
