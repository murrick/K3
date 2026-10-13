/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;

/** Exact full-chain restrictions with actual Core cache state and rollback. */
public final class DmzTypedRecursiveResumeRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-typed-recursive-").toString());
            for(boolean warm:new boolean[]{false,true})for(boolean negative:new boolean[]{false,true})
                for(String support:new String[]{"sole","aliases","independent","primary"})run(warm,negative,support,3);
            run(false,false,"sole",4);run(true,false,"sole",4);
            bounds();
            pending();
            System.out.println("DMZ_TYPED_RECURSIVE_RESUME_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static IContextResults.Revision pin(){return new IContextResults.Revision(UUID.randomUUID(),1);}
    private static void input(Mind q,IContextResults.Revision pin,long id,DmzReplayProvenance.Authority authority,String text,boolean warm) throws Exception {
        if(warm)require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,id,authority,text)),"native sourced input "+text);
        else {DmzReplayProvenance.replayRule(q,pin,id,authority,text);++checks;}
    }
    private static boolean present(DmzTypedNativeResume.Result result,String atom) throws Exception {
        TerminalSupportCapture.Ground expected=DmzGroundReplay.parseIdentity("!"+atom+";").ground();
        for(DmzTypedNativeResume.Materialization m:result.materializations)if(m.ground.equivalent(expected))return true;
        return false;
    }
    private static void run(boolean warm,boolean negative,String support,int depth) throws Exception {

        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision rules=pin(),facts=pin(),b=pin(),other=pin();
        String sign=negative?"~":"",opposite=negative?"":"~";
        String root=sign+"p"+(depth-1),end=sign+"p"+depth,tail=sign+"tail";
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            for(int i=0;i<depth;++i)
                input(q,rules,i,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+(i==0?"":sign)+"p"+i+"(x,y) -> "+sign+"p"+(i+1)+"(x,y);",warm);
            input(q,rules,depth,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y "+end+"(x,y) -> "+tail+"(x,y);",warm);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!p0(John,Tom);",warm);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!p0(Mary,Sarah);",warm);
            if(support.equals("aliases")) {
                input(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y p0(x,y) -> "+sign+"p1(x,y);",false);
                input(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!p0(John,Tom);",false);
            } else if(support.equals("independent")) {
                input(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y c(x,y) -> "+sign+"p1(x,y);",warm);
                input(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!c(John,Tom);",warm);
            } else if(support.equals("primary"))
                input(q,facts,2,DmzReplayProvenance.Authority.TARGET_Q,"!"+sign+"p1(John,Tom);",warm);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> "+opposite+"p"+depth+"(x,y);",false);
            input(q,facts,3,DmzReplayProvenance.Authority.TARGET_Q,"!b(John,Tom);",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"recursive native baseline conflict");
                require(!collisions.snapshot().isEmpty(),"recursive native event captured");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(rules.getContextId())
                        && side.premises.get(0).step>=0 && leaf(side).source.context.equals(facts.getContextId()) && leaf(side).source.sourceRule==0)selected=side;
            require(selected!=null,"actual recursive proof from the protected p0 donor selected");
            int selectedIndex=(int)selected.source.sourceRule+1;
            root=sign+"p"+selectedIndex;
            require(DmzProofWitnesses.NoGood.fromTyped(selected).depth==selectedIndex+1,"every selected chain step retains its typed identity");
            require(guard.project(proof,selected,q,10000).eligible,"whole recursive native route covered");
            String state=DmzObservationStateFingerprint.capture(q);DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzTypedCacheProjection plan=DmzTypedCacheProjection.projectRecursive(guard,proof,selected,q,10000);
            require(plan.eligible && plan.isCurrent(q),"current full-chain cache plan: warm="+warm+" declared="+depth+" selected="+selectedIndex+" witness="+DmzProofWitnesses.NoGood.fromTyped(selected).depth+" truncated="+plan.truncated);
            DmzTypedNativeResume.Result result=DmzTypedNativeResume.runRecursive(guard,proof,selected,q,10000);
            require(!result.complete && result.compiled==support.equals("sole"),"actual recursive result: "+warm+"/"+negative+"/"+support+"/"+depth);
            require(result.retractedCaches.size()==(warm && support.equals("sole")?depth-selectedIndex+2:0),"only root/downstream materializations lose full-chain support");
            boolean retainedVisible=warm && !support.equals("sole");
            require(present(result,root+"(John,Tom)")==retainedVisible && present(result,end+"(John,Tom)")==retainedVisible
                    && present(result,tail+"(John,Tom)")==retainedVisible,"actual retained caches or failed cold-frame rollback: "+warm+"/"+negative+"/"+support);
            for(int i=1;i<selectedIndex;++i)require(present(result,sign+"p"+i+"(John,Tom)")==(warm || result.compiled || (support.equals("primary") && i==1)),"upstream native state respects inner settlement: "+i);
            require(present(result,root+"(Mary,Sarah)")== (warm || result.compiled) && present(result,tail+"(Mary,Sarah)")== (warm || result.compiled),"other substitution remains warm or rolls back with a refused cold frame");
            require(present(result,"p0(John,Tom)") && present(result,"b(John,Tom)"),"Q primary donors stay live");
            if(support.equals("sole"))require(result.applicationDenied>0 && result.queryDenied>0,"both actual native gates block recursive resurrection");
            else require(!result.conflicts.isEmpty(),"remaining full-chain path keeps actual native conflict");
            TerminalSupportCapture.Ground expectedRoot=DmzGroundReplay.parseIdentity("!"+root+"(John,Tom);").ground();boolean seen=false;
            for(int i=0;i<result.graph.observed.nodes.size();++i)if(result.graph.observed.nodes.get(i).ground.equivalent(expectedRoot)) {
                seen=true;DmzProofWitnesses.Result actual=DmzProofWitnesses.enumerate(result.graph,i,10000);
                require(!actual.truncated && actual.witnesses.size()==(support.equals("sole")?0:support.equals("aliases")?3:1),"observed recursive root proof count");
            }
            if(!support.equals("sole"))require(seen,"retained recursive root observed");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && user.getCurrentMind()==q
                    && !q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty() && plan.isCurrent(q),"recursive rollback restores Q, source state and ownership");
            try {DmzTypedNativeResume.runRecursive(guard,proof,selected,q,1);throw new AssertionError("Expected budget refusal");}
            catch(IllegalStateException expected){++checks;}
            require(state.equals(DmzObservationStateFingerprint.capture(q)),"budget refusal preserves Q");
            input(q,other,9,DmzReplayProvenance.Authority.EXTERNAL,"!p0(John,Tom);",false);
            try {DmzTypedNativeResume.runRecursive(guard,proof,selected,q,10000);throw new AssertionError("Expected stale refusal");}
            catch(IllegalStateException expected){++checks;}
            require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"stale full-chain receipt cannot acquire scope");
        }
    }
    private static DmzProofWitnesses.Witness leaf(DmzProofWitnesses.Witness witness) {
        while(witness.step>=0)witness=witness.premises.get(0);return witness;
    }
    private static void bounds() throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision rules=pin(),facts=pin();
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q)) {
            for(int i=0;i<8;++i)input(q,rules,i,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y p"+i+"(x,y) -> p"+(i+1)+"(x,y);",false);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!p0(John,Tom);",false);
            DmzCurrentGroundProofInventory model=DmzCurrentGroundProofInventory.capture(q,10000,false);
            require(model.eligible,"bounded chain reconstruction is diagnostic only");
            for(int length:new int[]{1,2,7,8}) {
                DmzCurrentGroundProofInventory.Proofs proofs=model.proofs(DmzGroundReplay.parseIdentity("!p"+length+"(John,Tom);").ground(),10000);
                require(!proofs.truncated && proofs.witnesses.size()==1,"unique reconstructed chain for shape bound");
                DmzProofWitnesses.NoGood typed=DmzProofWitnesses.NoGood.fromTyped(proofs.witnesses.get(0));
                require(typed.depth==length+1 && typed.recursiveWithin(8)==(length>=2 && length<=7),"typed recursive shape bound counts primary plus all production steps");
                require(!DmzProofWitnesses.NoGood.from(proofs.witnesses.get(0)).recursiveWithin(8),"legacy chain shape is not a typed native permission");
            }
            require(!q.hasPendingTransactions(),"shape checks create no native child");
        }
    }
    private static void pending() throws Exception {
        User user=new User();Mind root=new Mind(user);user.setCurrentMind(root);IContextResults.Revision a=pin(),b=pin(),facts=pin();
        String state=DmzObservationStateFingerprint.capture(root);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(root);TechnicalMindTransaction tx=TechnicalMindTransaction.beginIsolated(root);
                TerminalSupportCapture capture=TerminalSupportCapture.begin(tx.mind())) {
            Mind q=tx.mind();
            input(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> middle(x,y);",true);
            input(q,a,1,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y middle(x,y) -> male(x,y);",true);
            input(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);",true);
            input(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> ~male(x,y);",false);
            input(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!b(John,Tom);",false);
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"pending full-chain conflict");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()) && side.premises.get(0).step>=0)selected=side;
            require(selected!=null && guard.project(proof,selected,q,10000).eligible,"pending chain remains diagnostic evidence");
            String local=DmzObservationStateFingerprint.capture(q);
            try {DmzTypedNativeResume.runRecursive(guard,proof,selected,q,10000);throw new AssertionError("Expected pending recursive refusal");}
            catch(IllegalStateException expected){require(expected.getMessage().contains("settled typed cache plan"),"pending chain cannot acquire settled native permission");}
            require(local.equals(DmzObservationStateFingerprint.capture(q)) && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"pending recursive refusal preserves branch");
        }
        require(state.equals(DmzObservationStateFingerprint.capture(root)) && !root.hasPendingTransactions(),"pending scope rollback preserves Q");
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
