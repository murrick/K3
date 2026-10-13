/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;

/** Historical native cache routes remain separate from current collision permission. */
public final class DmzCacheCollisionEvidenceRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-cache-history-").toString());
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})
                for(String[] values:new String[][]{{"John","Tom"},{"1","2"},{"[1,2]","[3]"}})run(negative,recursive,values);
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})sole(negative,recursive);
            pending();
            System.out.println("DMZ_CACHE_COLLISION_EVIDENCE_PASS checks="+checks);System.exit(0);
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
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof event;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"actual cache-only native conflict");
                event=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            require(event.combinations.isEmpty(),"current event does not silently inherit historical routes");
            DmzCacheCollisionEvidence linked=DmzCacheCollisionEvidence.bind(baseline,event.event,q,10000);
            require(linked.eligible && linked.isCurrent(q) && !linked.complete && linked.combinations.size()==2,"exact historical routes bound to both actual event endpoints");
            require(DmzCacheCollisionEvidence.bind(baseline,event.event,new Mind(user),10000).graph==null,"different Mind cannot borrow event evidence");
            DmzCacheCollisionEvidence shortLink=DmzCacheCollisionEvidence.bind(baseline,event.event,q,1);
            require(!shortLink.eligible && shortLink.truncated && shortLink.graph==null,"bounded endpoint lookup exposes no partial graph");
            DmzProofWitnesses.Witness chosen=null,primarySide=null;
            for(DmzWitnessConflicts.Combination pair:linked.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0)chosen=side;else primarySide=side;
            require(chosen!=null && primarySide!=null,"external derived choice and exact primary endpoint are separate");
            DmzCacheCollisionEvidence.Projection projected=linked.project(chosen,q,10000);
            require(projected.eligible && projected.removed==1 && !projected.complete,"one exact cache source/substitution choice projected");
            require(DmzProofWitnesses.enumerate(projected.graph,chosen.node,10000).witnesses.size()==1,"other full native assignment remains a proof");
            DmzTypedCacheProjection cachePlan=DmzTypedCacheProjection.projectCache(linked,chosen,q,10000);
            require(cachePlan.eligible && cachePlan.isCurrent(q),"current native cache plan from exact endpoint evidence");
            DmzTypedNativeResume.Result resumed=DmzTypedNativeResume.runCache(linked,chosen,q,10000);
            require(!resumed.compiled && !resumed.complete && !resumed.provisional && resumed.retractedCaches.isEmpty(),"other full assignment preserves actual cache conflict");
            require(!resumed.conflicts.isEmpty(),"native alternative still produces an actual conflict");
            require(present(resumed,"!"+opposite+"pair("+reverse+");"),"shared root cache survives in actual native child");
            require(linked.isCurrent(q) && state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q)
                    && !q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"cache continuation rolls back authority and releases ownership");
            require(!linked.project(primarySide,q,10000).eligible,"primary endpoint cannot be selected for a cut");
            require(linked.project(chosen,q,1).truncated,"short projection budget refuses");
            DmzCacheCollisionEvidence.Baseline after=DmzCacheCollisionEvidence.beforeOperation(capture,q);
            require(!DmzCacheCollisionEvidence.bind(after,event.event,q,10000).eligible,"later baseline cannot borrow earlier event");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q),"endpoint binding and projection remain read only");

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
            try {DmzTypedNativeResume.runCache(linked,chosen,q,10000);throw new AssertionError("Expected stale cache refusal");}
            catch(IllegalStateException expected){++checks;}
            require(!linked.isCurrent(q) && !linked.project(chosen,q,10000).eligible
                    && !DmzCacheCollisionEvidence.bind(baseline,event.event,q,10000).eligible,"source change invalidates event binding and projected choice");
            DmzStoredGroundRoutes enlarged=DmzStoredGroundRoutes.capture(capture,q,cache,10000);
            require(!enlarged.covered && !enlarged.truncated && enlarged.observedProofs==2 && enlarged.expectedProofs==4,"later alias cannot acquire historical application authorship");
            require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty() && user.getCurrentMind()==q,"history qualification leaves no child or restriction");
        }
        require(!receipt.isCurrent(q),"closed capture invalidates receipt while detached graph survives");
        require(receipt.graph!=null && receipt.observedProofs==2,"historical detached observations survive closure");
    }
    private static void sole(boolean negative,boolean recursive) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision rules=pin(),facts=pin();
        String sign=negative?"~":"",opposite=negative?"":"~";
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            if(recursive)input(q,rules,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> middle(x,y);",true);
            input(q,rules,1,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+(recursive?"middle":"a")+"(x,y) -> "+sign+"b(x,y);",true);
            input(q,rules,2,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+sign+"b(x,y) -> "+sign+"tail(x,y);",true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);",true);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!a(Mary,Sarah);",true);
            input(q,facts,2,DmzReplayProvenance.Authority.TARGET_Q,"!"+opposite+"b(John,Tom);",false);
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);
            CollisionProofCapture.Conflict event;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"sole supported warm cache conflict");event=collisions.snapshot().get(0);
            }
            require(DmzProvisionalCollisionProof.build(event,10000,10000).combinations.isEmpty(),"sole cache-only event has no current source combinations");
            DmzCacheCollisionEvidence evidence=DmzCacheCollisionEvidence.bind(baseline,event,q,10000);
            require(evidence.eligible && evidence.combinations.size()==1,"sole exact historical cache chain bound to actual event");
            DmzProofWitnesses.Witness selected=evidence.combinations.get(0).left.step>=0?evidence.combinations.get(0).left:evidence.combinations.get(0).right;
            DmzTypedNativeResume.Result result=DmzTypedNativeResume.runCache(evidence,selected,q,10000);
            require(result.compiled && result.retractedCaches.size()==2 && result.applicationDenied>0 && result.queryDenied>0,"actual sole-support cache continuation retracts root/tail and blocks resurrection");
            require(!present(result,"!"+sign+"b(John,Tom);") && !present(result,"!"+sign+"tail(John,Tom);"),"lost root and dependent cache absent in native child");
            require(present(result,"!a(John,Tom);") && present(result,"!"+opposite+"b(John,Tom);"),"both Q primary donors survive");
            require(present(result,"!"+sign+"b(Mary,Sarah);") && present(result,"!"+sign+"tail(Mary,Sarah);"),"other substitution keeps its native caches");
            if(recursive)require(present(result,"!middle(John,Tom);"),"upstream generated ground remains live");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && evidence.isCurrent(q)
                    && !q.hasPendingTransactions() && user.getCurrentMind()==q && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"cache-only continuation restores Q and ownership");
            try(TechnicalMindTransaction occupied=TechnicalMindTransaction.beginIsolated(q)) {
                try {DmzTypedNativeResume.runCache(evidence,selected,q,10000);throw new AssertionError("Expected occupied boundary refusal");}
                catch(IllegalStateException expected){require(expected.getMessage().contains("Exclusive"),"cache continuation requires exclusive child ownership");}
            }
        }
    }
    private static boolean present(DmzTypedNativeResume.Result result,String text) throws Exception {
        TerminalSupportCapture.Ground expected=DmzGroundReplay.parseIdentity(text).ground();
        for(DmzTypedNativeResume.Materialization item:result.materializations)if(expected.equivalent(item.ground))return true;
        return false;
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
