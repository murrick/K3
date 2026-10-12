/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.units.Rule;

/** Owned materialized native continuation and rollback, with actual cache state observations. */
public final class DmzTypedMaterializedResumeRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-typed-cache-").toString());
            for(String shape:new String[]{"ordinary","permutation","constants","sets"})
                for(String support:new String[]{"sole","aliases","independent","primary"})
                    for(boolean negative:new boolean[]{false,true})run(shape,support,negative);
            symmetric();pending();recursive();
            System.out.println("DMZ_TYPED_MATERIALIZED_RESUME_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
    private static IContextResults.Revision pin(){return new IContextResults.Revision(UUID.randomUUID(),1);}
    private static void input(Mind q,IContextResults.Revision source,long id,DmzReplayProvenance.Authority authority,String text,boolean infer) throws Exception {
        if(infer)require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,source,id,authority,text)),"native accepted input: "+text);
        else {DmzReplayProvenance.replayRule(q,source,id,authority,text);++checks;}
    }
    private static void run(String shape,String support,boolean negative) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        IContextResults.Revision a=pin(),b=pin(),facts=pin(),other=pin();
        String sign=negative?"~":"",opposite=negative?"":"~";
        String signature=shape.equals("permutation")?"y,x":shape.equals("constants")?"x,y,Fixed":"x,y";
        String values=shape.equals("sets")?"[John,Tom],7":"John,Tom";
        String root=shape.equals("permutation")?"Tom,John":shape.equals("constants")?"John,Tom,Fixed":values;
        String otherValues=shape.equals("sets")?"[Mary,Sarah],9":"Mary,Sarah";
        String otherRoot=shape.equals("permutation")?"Sarah,Mary":shape.equals("constants")?"Mary,Sarah,Fixed":otherValues;
        String familyRule=shape.equals("constants")?"!@x @y @z "+sign+"male(x,y,z) -> "+sign+"downstream(x,y,z);":"!@x @y "+sign+"male(x,y) -> "+sign+"downstream(x,y);";
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            input(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> "+sign+"male("+signature+");",true);
            input(q,a,1,DmzReplayProvenance.Authority.EXTERNAL,familyRule,true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a("+values+");",true);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!a("+otherValues+");",true);
            if(support.equals("aliases")) {
                input(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> "+sign+"male("+signature+");",false);
                input(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!a("+values+");",false);
            } else if(support.equals("independent")) {
                input(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y c(x,y) -> "+sign+"male("+signature+");",true);
                input(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!c("+values+");",true);
            } else if(support.equals("primary"))
                input(q,facts,2,DmzReplayProvenance.Authority.TARGET_Q,"!"+sign+"male("+root+");",true);
            int liveCaches=0;for(IRule rule:q.getRules())if(!rule.isDeleted(q)&&q.getRules().isGenerated(rule))++liveCaches;
            require(liveCaches==(support.equals("primary")?3:4),"warm cache count accounts for asserted root promotion: "+shape+"/"+support+" "+liveCaches);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> "+opposite+"male("+signature+");",false);
            input(q,facts,3,DmzReplayProvenance.Authority.TARGET_Q,"!b("+values+");",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"warm native conflict operation refused");
                require(!collisions.snapshot().isEmpty(),"warm native collision captured");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()) && side.source.sourceRule==0
                        && side.premises.size()==1 && side.premises.get(0).step<0 && side.premises.get(0).source.context.equals(facts.getContextId()))selected=side;
            require(selected!=null,"direct external witness with Q primary donor captured");
            require(guard.project(proof,selected,q,10000).eligible,"warm native typed routes covered");
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzTypedCacheProjection plan=DmzTypedCacheProjection.project(guard,proof,selected,q,10000);
            require(plan.eligible,"current warm cache plan");
            DmzTypedNativeResume.Result result=DmzTypedNativeResume.runMaterialized(guard,proof,selected,q,10000);
            require(!result.complete,"native diagnostic is not general completeness");
            require(result.compiled==support.equals("sole"),"actual native result with "+shape+"/"+support+": "+result.compiled);
            require(result.retractedCaches.size()==(support.equals("sole")?2:0),"only root and dependent last-support caches are retracted");
            require(present(result,sign+"male("+root+")")==!support.equals("sole"),"actual root cache/primary follows retained support");
            require(present(result,sign+"downstream("+root+")")==!support.equals("sole"),"actual dependent materialization follows retained support");
            require(present(result,sign+"male("+otherRoot+")") && present(result,sign+"downstream("+otherRoot+")"),"actual other substitution retains its native caches");
            require(present(result,"a("+values+")") && present(result,"b("+values+")"),"protected primary donors stay live in native branch");
            if(support.equals("sole"))require(result.applicationDenied>0 && result.queryDenied>0 && result.conflicts.isEmpty(),"both native restriction points prevent resurrection");
            else require(!result.conflicts.isEmpty(),"retained proof keeps the actual conflict");
            TerminalSupportCapture.Ground rootGround=DmzGroundReplay.parseIdentity("!"+sign+"male("+root+");").ground();
            boolean seen=false;
            for(int i=0;i<result.graph.observed.nodes.size();++i)if(rootGround.equivalent(result.graph.observed.nodes.get(i).ground)) {
                seen=true;DmzProofWitnesses.Result actual=DmzProofWitnesses.enumerate(result.graph,i,10000);
                require(!actual.truncated,"native trace enumeration bounded");
                int expected=support.equals("sole")?0:support.equals("aliases")?3:1;
                require(actual.witnesses.size()==expected,"actual observed root proofs after restriction: "+actual.witnesses.size()+" expected "+expected);
            }
            if(!support.equals("sole"))require(seen,"retained root proof appears in native trace");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && !q.hasPendingTransactions() && user.getCurrentMind()==q,"warm native resume restores Q, source state, reservations and User");
            require(plan.isCurrent(q) && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"cache receipt current after rollback and scope released");
            try(TechnicalMindTransaction sibling=TechnicalMindTransaction.beginIsolated(q)) {
                try {DmzTypedNativeResume.runMaterialized(guard,proof,selected,q,10000);throw new AssertionError("Expected exclusive ownership refusal");}
                catch(IllegalStateException expected){require(expected.getMessage().contains("Exclusive"),"concurrent child refuses before cache mutation");}
            }
            try {DmzTypedNativeResume.runMaterialized(guard,proof,selected,q,1);throw new AssertionError("Expected budget refusal");}
            catch(IllegalStateException expected){++checks;}
            require(state.equals(DmzObservationStateFingerprint.capture(q)),"budget refusal preserves warm Q");
            final DmzProofWitnesses.Witness leaf=selected.premises.get(0);
            try {DmzTypedNativeResume.runMaterialized(guard,proof,leaf,q,10000);throw new AssertionError("Expected primary refusal");}
            catch(IllegalStateException expected){++checks;}
            input(q,other,9,DmzReplayProvenance.Authority.EXTERNAL,"!a("+values+");",false);
            try {DmzTypedNativeResume.runMaterialized(guard,proof,selected,q,10000);throw new AssertionError("Expected stale receipt refusal");}
            catch(IllegalStateException expected){++checks;}
            require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"stale refusal releases ownership");
        }
    }
    private static void pending() throws Exception {
        User user=new User();Mind root=new Mind(user);user.setCurrentMind(root);IContextResults.Revision a=pin(),b=pin(),facts=pin();
        String state=DmzObservationStateFingerprint.capture(root);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(root);TechnicalMindTransaction tx=TechnicalMindTransaction.beginIsolated(root);
                TerminalSupportCapture capture=TerminalSupportCapture.begin(tx.mind())) {
            Mind q=tx.mind();
            input(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> male(x,y);",true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);",true);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> ~male(x,y);",false);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!b(John,Tom);",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"pending warm native conflict");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()))selected=side;
            require(selected!=null && guard.project(proof,selected,q,10000).eligible,"pending detached collision still inspectable");
            String local=DmzObservationStateFingerprint.capture(q);
            try {DmzTypedNativeResume.runMaterialized(guard,proof,selected,q,10000);throw new AssertionError("Expected pending refusal");}
            catch(IllegalStateException expected){require(expected.getMessage().contains("settled typed cache plan"),"pending sources refuse warm enforcement");}
            require(local.equals(DmzObservationStateFingerprint.capture(q)) && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"pending refusal preserves branch caches and scope");
        }
        require(state.equals(DmzObservationStateFingerprint.capture(root)) && !root.hasPendingTransactions(),"pending planning refusal and rollback restore physical Q");
    }
    private static void recursive() throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision a=pin(),b=pin(),facts=pin();
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            input(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> middle(x,y);",true);
            input(q,a,1,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y middle(x,y) -> male(x,y);",true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);",true);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> ~male(x,y);",false);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!b(John,Tom);",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"recursive warm native conflict");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()) && side.premises.get(0).step>=0)selected=side;
            require(selected!=null && guard.project(proof,selected,q,10000).eligible,"recursive detached restriction recognized independently");
            String state=DmzObservationStateFingerprint.capture(q);
            try {DmzTypedNativeResume.runMaterialized(guard,proof,selected,q,10000);throw new AssertionError("Expected recursive refusal");}
            catch(IllegalStateException expected){require(expected.getMessage().contains("direct typed collision"),"recursive selected cuts refuse before cache mutation");}
            require(state.equals(DmzObservationStateFingerprint.capture(q)),"recursive refusal preserves caches and Q");
        }
    }
    private static void symmetric() throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision rules=pin(),facts=pin();
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            input(q,rules,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y ~(pair(x,y),pair(y,x));",true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!pair(John,Tom);",true);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!pair(Tom,John);",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"symmetric warm native conflict");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            require(proof.combinations.isEmpty() && proof.graph.observed.nodes.isEmpty(),"symmetric cache-only conflict has no current observed source route");
            DmzCurrentGroundProofInventory model=DmzCurrentGroundProofInventory.capture(q,10000,false);
            require(model.eligible,"independent symmetric typed closure is still reconstructable");
            TerminalSupportCapture.Ground cached=DmzGroundReplay.parseIdentity("!~pair(Tom,John);").ground();
            DmzCurrentGroundProofInventory.Proofs reconstructed=model.proofs(cached,10000);
            require(reconstructed.witnesses.size()==2,"model recognizes both full symmetric assignments");
            String state=DmzObservationStateFingerprint.capture(q);
            try {DmzTypedNativeResume.runMaterialized(guard,proof,reconstructed.witnesses.get(0),q,10000);throw new AssertionError("Expected missing native coverage refusal");}
            catch(IllegalStateException expected){require(expected.getMessage().contains("direct typed collision"),"a reconstructed path is not permission for an unobserved native cut");}
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && !q.hasPendingTransactions()
                    && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"cache-only diagnostic refusal preserves Q and ownership");
        }
    }
    private static boolean present(DmzTypedNativeResume.Result result,String statement) throws Exception {
        TerminalSupportCapture.Ground ground=DmzGroundReplay.parseIdentity("!"+statement+";").ground();
        for(DmzTypedNativeResume.Materialization m:result.materializations)if(m.ground.equivalent(ground))return true;
        return false;
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
