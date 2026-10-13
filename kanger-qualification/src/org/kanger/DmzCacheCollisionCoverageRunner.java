/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;

/** Historical native cache routes remain separate from current collision permission. */
public final class DmzCacheCollisionCoverageRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-cache-history-").toString());
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})
                for(String[] values:new String[][]{{"John","Tom"},{"1","2"},{"[1,2]","[3]"}})run(negative,recursive,values);
            pending();
            System.out.println("DMZ_CACHE_COLLISION_COVERAGE_PASS checks="+checks);System.exit(0);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static IContextResults.Revision pin(){return new IContextResults.Revision(UUID.randomUUID(),1);}
    private static void input(Mind q,IContextResults.Revision source,long id,DmzReplayProvenance.Authority authority,String text,boolean infer) throws Exception {
        if(infer)require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,source,id,authority,text)),"native accepted input");
        else {DmzReplayProvenance.replayRule(q,source,id,authority,text);++checks;}
    }
    private static long find(Mind q,String text) throws Exception {
        TerminalSupportCapture.Ground expected=DmzGroundReplay.parseIdentity(text).ground();
        for(IRule item:q.getRules())if(!item.isDeleted(q) && expected.equivalent(DmzGroundIdentity.capture(item,q).ground()))return item.getId();
        return -1;
    }
    private static void run(boolean negative,boolean recursive,String[] values) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        IContextResults.Revision rules=pin(),facts=pin(),alias=pin();String sign=negative?"~":"",opposite=negative?"":"~";
        String forward=values[0]+","+values[1],reverse=values[1]+","+values[0];
        DmzStoredGroundRoutes receipt;
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            input(q,rules,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y ~("+sign+"pair(x,y),"+sign+"pair(y,x));",true);
            if(recursive)input(q,rules,1,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y seed(x,y) -> "+sign+"pair(x,y);",true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!"+(recursive?"seed":sign+"pair")+"("+forward+");",true);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!"+sign+"pair("+reverse+");",false);
            long cache=find(q,"!"+opposite+"pair("+reverse+");");require(cache>=0,"actual opposite cache exists");
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            receipt=DmzStoredGroundRoutes.capture(capture,q,cache,10000);
            require(receipt.covered && !receipt.complete && !receipt.truncated && receipt.isCurrent(q),"current historical native route coverage");
            require(receipt.expectedProofs==2 && receipt.observedProofs==2,"both distinct native assignments are retained");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q),"historical audit is read only");
            DmzStoredGroundRoutes shortPlan=DmzStoredGroundRoutes.capture(capture,q,cache,1);
            require(!shortPlan.covered && shortPlan.truncated && shortPlan.graph==null,"short budget refuses without partial graph");
            long primary=find(q,"!"+(recursive?"seed":sign+"pair")+"("+forward+");");
            require(!DmzStoredGroundRoutes.capture(capture,q,primary,10000).covered,"primary input is not historical generated cache permission");
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof event;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"actual cache-only native conflict");
                event=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            require(event.combinations.isEmpty(),"current event does not silently inherit historical routes");
            DmzProofWitnesses.Witness selected=null;
            for(int i=0;i<receipt.graph.observed.nodes.size();++i)if(DmzGroundReplay.parseIdentity("!"+opposite+"pair("+reverse+");").ground().equivalent(receipt.graph.observed.nodes.get(i).ground))
                selected=DmzProofWitnesses.enumerate(receipt.graph,i,10000).witnesses.get(0);
            require(selected!=null && !guard.project(event,selected,q,10000).eligible,"historical witness alone is not current event cut permission");
            require(receipt.isCurrent(q) && state.equals(DmzObservationStateFingerprint.capture(q)) && !q.hasPendingTransactions(),"rejected operation preserves receipt and Q");
            try(TerminalSupportCapture fresh=TerminalSupportCapture.begin(q)) {
                require(!DmzStoredGroundRoutes.capture(fresh,q,cache,10000).covered,"fresh scope cannot invent absent storage history");
            }
            require(receipt.isCurrent(q),"nested capture cannot replace historical owner");
            input(q,alias,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y ~("+sign+"pair(x,y),"+sign+"pair(y,x));",false);
            require(!receipt.isCurrent(q),"later source alias invalidates receipt");
            DmzStoredGroundRoutes enlarged=DmzStoredGroundRoutes.capture(capture,q,cache,10000);
            require(!enlarged.covered && !enlarged.truncated && enlarged.observedProofs==2 && enlarged.expectedProofs==4,"later alias cannot acquire historical application authorship");
            require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty() && user.getCurrentMind()==q,"history qualification leaves no child or restriction");
        }
        require(!receipt.isCurrent(q),"closed capture invalidates receipt while detached graph survives");
        require(receipt.graph!=null && receipt.observedProofs==2,"historical detached observations survive closure");
    }
    private static void pending() throws Exception {
        User user=new User();Mind root=new Mind(user);user.setCurrentMind(root);String state=DmzObservationStateFingerprint.capture(root);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(root);TechnicalMindTransaction tx=TechnicalMindTransaction.beginIsolated(root);
                TerminalSupportCapture capture=TerminalSupportCapture.begin(tx.mind())) {
            Mind q=tx.mind();
            input(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> b(x,y);",true);
            input(q,pin(),0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);",true);
            long cache=find(q,"!b(John,Tom);");require(cache>=0,"pending native cache exists");
            require(!DmzStoredGroundRoutes.capture(capture,q,cache,10000).covered,"pending storage is not committed historical permission");
        }
        require(state.equals(DmzObservationStateFingerprint.capture(root)) && !root.hasPendingTransactions(),"pending history refusal rolls back authority");
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
