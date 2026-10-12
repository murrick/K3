/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.IContextResults;

/** Real Core callbacks, frozen alternatives, private continuation and rollback. */
public final class DmzTypedNativeResumeRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-typed-native-").toString());
            for(String mode:new String[]{"ordinary","permutation","constants","sets"})for(boolean aliases:new boolean[]{false,true})run(mode,aliases);
            symmetric();pending();
            System.out.println("DMZ_TYPED_NATIVE_RESUME_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
    private static void run(String mode,boolean aliases) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        IContextResults.Revision a=pin(),b=pin(),other=pin(),facts=pin();
        String conclusion=mode.equals("permutation")?"male(y,x)":mode.equals("constants")?"male(x,y,Fixed)":"male(x,y)";
        String values=mode.equals("sets")?"[John,Tom],7":"John,Tom";
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            replay(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> "+conclusion+";");
            replay(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> ~"+conclusion+";");
            replay(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a("+values+");");
            replay(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!b("+values+");");
            replay(q,facts,2,DmzReplayProvenance.Authority.TARGET_Q,"!a("+(mode.equals("sets")?"[Mary,Sarah],9":"Mary,Sarah")+");");
            if(aliases) {
                replay(q,other,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> "+conclusion+";");
                replay(q,other,1,DmzReplayProvenance.Authority.EXTERNAL,"!a("+values+");");
            }
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture conflicts=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"ordinary native baseline has a conflict");
                require(!conflicts.snapshot().isEmpty(),"native conflict captured");
                proof=DmzProvisionalCollisionProof.build(conflicts.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()) && side.premises.size()==1 && side.premises.get(0).step<0
                        && side.premises.get(0).source.context.equals(facts.getContextId()))selected=side;
            require(selected!=null,"direct external production with protected primary support selected");
            require(guard.project(proof,selected,q,10000).eligible,"current typed projection before native resume");
            String state=DmzObservationStateFingerprint.capture(q);
            DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzTypedNativeResume.Result result=DmzTypedNativeResume.run(guard,proof,selected,q,10000);

            require(!result.complete,"native resume is not general completeness");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && !q.hasPendingTransactions(),"native resume restores physical Q and exact source state");
            require(user.getCurrentMind()==q && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"native scope does not leak User or restriction ownership");
            if(!aliases)require(result.compiled && result.applicationDenied>0 && result.queryDenied>0,"native forward and consistency gates both exercised, cold continuation succeeds");
            else require(!result.compiled && !result.conflicts.isEmpty(),"other alias proofs keep the actual native conflict");
            boolean rootSeen=false;
            for(int i=0;i<result.graph.observed.nodes.size();++i)if(result.graph.observed.nodes.get(i).ground.equivalent(selected.graph.observed.nodes.get(selected.node).ground)) {
                rootSeen=true;
                DmzProofWitnesses.Result remaining=DmzProofWitnesses.enumerate(result.graph,i,10000);
                require(!remaining.truncated,"native diagnostic proof budget");
                if(aliases)require(remaining.witnesses.size()==3,"three actual alias proofs survive one exact exclusion");
                else require(remaining.witnesses.isEmpty(),"blocked native root is not reintroduced");
                for(DmzProofWitnesses.Witness witness:remaining.witnesses)
                    require(!DmzProofWitnesses.NoGood.fromTyped(selected).matches(witness.graph,witness.node,witness.step,witness.source,witness.premises),"selected exact source application excluded from captured native proofs");
            }
            if(aliases)require(rootSeen,"retained native root is captured");
            if(!aliases) {
                String otherAtom=mode.equals("permutation")?"male(Sarah,Mary)":mode.equals("constants")?"male(Mary,Sarah,Fixed)":mode.equals("sets")?"male([Mary,Sarah],9)":"male(Mary,Sarah)";
                TerminalSupportCapture.Ground otherGround=DmzGroundReplay.parseIdentity("!"+otherAtom+";").ground();
                boolean otherSeen=false;
                for(int i=0;i<result.graph.observed.nodes.size();++i)if(result.graph.observed.nodes.get(i).ground.equivalent(otherGround))
                    otherSeen=!DmzProofWitnesses.enumerate(result.graph,i,10000).witnesses.isEmpty();
                require(otherSeen,"different native substitution of the same production survives");
            }
            try(TechnicalMindTransaction sibling=TechnicalMindTransaction.beginIsolated(q)) {
                boolean ownershipRefused=false;
                try {DmzTypedNativeResume.run(guard,proof,selected,q,10000);}catch(IllegalStateException expected) {
                    ownershipRefused=expected.getMessage().contains("Exclusive typed resume");
                }
                require(ownershipRefused,"another live child cannot share native resume ownership");
            }
            boolean refused=false;try {DmzTypedNativeResume.run(guard,proof,selected,q,1);}catch(IllegalStateException expected){refused=true;}
            require(refused && state.equals(DmzObservationStateFingerprint.capture(q)),"short budget refuses without opening native mutation");
            replay(q,other,9,DmzReplayProvenance.Authority.EXTERNAL,"!a("+values+");");
            refused=false;try {DmzTypedNativeResume.run(guard,proof,selected,q,10000);}catch(IllegalStateException expected){refused=true;}
            require(refused && !q.hasPendingTransactions(),"changed source occurrence refuses stale native receipt");
        }
    }
    private static void pending() throws Exception {
        User user=new User();Mind root=new Mind(user);user.setCurrentMind(root);IContextResults.Revision a=pin(),b=pin(),facts=pin();
        String state=DmzObservationStateFingerprint.capture(root);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(root);TechnicalMindTransaction tx=TechnicalMindTransaction.beginIsolated(root);
                TerminalSupportCapture capture=TerminalSupportCapture.begin(tx.mind())) {
            Mind q=tx.mind();
            replay(q,a,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y a(x,y) -> male(x,y);");
            replay(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y b(x,y) -> ~male(x,y);");
            replay(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!a(John,Tom);");
            replay(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!b(John,Tom);");
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"pending native conflict");
                proof=DmzProvisionalCollisionProof.build(collisions.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(a.getContextId()))selected=side;
            require(selected!=null && guard.project(proof,selected,q,10000).eligible,"pending detached projection remains diagnostic");
            boolean refused=false;
            try {DmzTypedNativeResume.run(guard,proof,selected,q,10000);}catch(IllegalStateException expected) {
                refused=expected.getMessage().contains("Settled typed inventory");
            }
            require(refused && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),"pending sources cannot open settled native enforcement scope");
        }
        require(state.equals(DmzObservationStateFingerprint.capture(root)) && !root.hasPendingTransactions(),"pending refusal and outer rollback preserve Q");
    }
    private static void symmetric() throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);IContextResults.Revision rules=pin(),facts=pin();
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            replay(q,rules,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x @y ~(pair(x,y),pair(y,x));");
            replay(q,facts,0,DmzReplayProvenance.Authority.TARGET_Q,"!pair(John,Tom);");
            replay(q,facts,1,DmzReplayProvenance.Authority.TARGET_Q,"!pair(Tom,John);");
            DmzGroundCollisionRestriction guard=DmzGroundCollisionRestriction.beforeOperation(capture,q,10000);
            DmzProvisionalCollisionProof proof;
            try(CollisionProofCapture conflicts=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"symmetric native conflict");
                proof=DmzProvisionalCollisionProof.build(conflicts.snapshot().get(0),10000,10000);
            }
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:proof.combinations)for(DmzProofWitnesses.Witness side:Arrays.asList(pair.left,pair.right))
                if(side.step>=0 && side.source.context.equals(rules.getContextId()) && side.premises.get(0).step<0)selected=side;
            require(selected!=null,"symmetric external witness");
            String state=DmzObservationStateFingerprint.capture(q);
            DmzGroundCollisionRestriction.Result projection=guard.project(proof,selected,q,10000);
            require(projection.eligible,"symmetric native routes have full current coverage");
            DmzTypedNativeResume.Result result=DmzTypedNativeResume.run(guard,proof,selected,q,10000);
            require(!result.compiled && !result.conflicts.isEmpty(),"other substitution with identical endpoints keeps native conflict");
            require(result.queryDenied==0,"query gate preserves aggregate truth backed by another substitution");
            boolean seen=false;
            for(int i=0;i<result.graph.observed.nodes.size();++i)if(result.graph.observed.nodes.get(i).ground.equivalent(selected.graph.observed.nodes.get(selected.node).ground)) {
                seen=true;
                require(DmzProofWitnesses.enumerate(result.graph,i,10000).witnesses.size()==1,"native graph preserves exactly the other symmetric substitution");
            }
            require(seen,"symmetric root remains in native trace");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && !q.hasPendingTransactions(),"symmetric characterization preserves Q");
        }
    }
    private static IContextResults.Revision pin(){return new IContextResults.Revision(UUID.randomUUID(),1);}
    private static void replay(Mind q,IContextResults.Revision pin,long id,DmzReplayProvenance.Authority authority,String text) throws Exception {
        DmzReplayProvenance.replayRule(q,pin,id,authority,text);++checks;
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
