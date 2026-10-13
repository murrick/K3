/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;

/** Symmetric native answers obtained by cuts of actual observed external causal subchains. */
public final class DmzCausalConflictChoicesRunner {
    private static int checks;
    private interface Attempt {void run() throws Exception;}
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",Files.createTempDirectory("dmz-causal-choice-").toString());
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})for(boolean warmA:new boolean[]{false,true}) {
                run(negative,recursive,false,false,warmA);run(negative,recursive,true,false,warmA);run(negative,recursive,false,true,warmA);
            }
            System.out.println("DMZ_CAUSAL_CONFLICT_CHOICES_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
    private static IContextResults.Revision pin(){return new IContextResults.Revision(UUID.randomUUID(),1);}
    private static void raw(Mind q,IContextResults.Revision source,long id,DmzReplayProvenance.Authority role,String text)throws Exception {
        DmzReplayProvenance.replayRule(q,source,id,role,text);
    }
    private static boolean present(DmzTypedNativeResume.Result result,String text)throws Exception {
        TerminalSupportCapture.Ground wanted=DmzGroundReplay.parseIdentity(text).ground();
        for(DmzTypedNativeResume.Materialization item:result.materializations)if(wanted.equivalent(item.ground))return true;
        return false;
    }
    private static void run(boolean negative,boolean recursive,boolean aliases,boolean protectedRule,boolean warmA)throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        IContextResults.Revision facts=pin(),a=pin(),b=pin(),alias=pin();
        DmzReplayProvenance.Authority aRole=protectedRule?DmzReplayProvenance.Authority.TARGET_Q:DmzReplayProvenance.Authority.EXTERNAL;
        String sign=negative?"~":"",opposite=negative?"":"~",premise=recursive?"middle":"a";
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            if(recursive)raw(q,a,0,aRole,"!@x a(x) -> middle(x);");
            raw(q,a,1,aRole,"!@x "+premise+"(x) -> "+sign+"p(x);");
            if(aliases && warmA) {
                if(recursive)raw(q,alias,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x a(x) -> middle(x);");
                raw(q,alias,1,DmzReplayProvenance.Authority.EXTERNAL,"!@x "+premise+"(x) -> "+sign+"p(x);");
            }
            raw(q,b,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x b(x) -> "+opposite+"p(x);");
            if(aliases && !warmA)raw(q,alias,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x b(x) -> "+opposite+"p(x);");
            for(int i:new int[]{2,3,1})require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,facts,i,
                    DmzReplayProvenance.Authority.TARGET_Q,"!"+(i==3 || i==1 && !warmA?"b":"a")+"("+i+");")),"warm native independent substitutions");
            String contrary="!"+(warmA?"b":"a")+"(1);";
            require(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,facts,4,DmzReplayProvenance.Authority.TARGET_Q,contrary)),
                    "opposite source implication conflicts via a contrapositive derived cache");
            raw(q,facts,4,DmzReplayProvenance.Authority.TARGET_Q,contrary);
            String state=DmzObservationStateFingerprint.capture(q);
            DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);
            CollisionProofCapture.Conflict event;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"actual immediate native conflict");event=collisions.snapshot().get(0);
            }
            DmzCacheCollisionEvidence evidence=DmzCacheCollisionEvidence.bind(baseline,event,q,10000);
            if(protectedRule && !warmA) {
                require(!evidence.eligible && !evidence.truncated,"Q-authored terminal cache lacks current typed historical cut coverage");
                require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q)
                        && !q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),
                        "uncovered Q-authored endpoint refuses without fabricating a causal permission");
                return;
            }
            require(evidence.eligible && !evidence.truncated,"complete current historical endpoint coverage: "+negative+"/"+recursive+"/"+aliases+"/"+protectedRule+"/"+warmA);
            DmzProofWitnesses.Witness endpoint=evidence.combinations.get(0).left.step>=0
                    ?evidence.combinations.get(0).left:evidence.combinations.get(0).right;
            DmzProofWitnesses.Witness cursor=endpoint.premises.get(0);
            while(!cursor.graph.observed.nodes.get(cursor.node).ground.predicate.equals("p"))cursor=cursor.premises.get(0);
            final DmzProofWitnesses.Witness stem=cursor;
            DmzProofWitnesses.Witness primary=stem;
            while(primary.step>=0)primary=primary.premises.get(0);
            String retainedSign=warmA?sign:opposite;
            String selectedRoot="!~"+(warmA?"b":"a")+"(1);";
            require(endpoint.graph.observed.nodes.get(endpoint.node).ground.equivalent(DmzGroundReplay.parseIdentity(selectedRoot).ground())
                    && stem.graph.observed.nodes.get(stem.node).ground.equivalent(DmzGroundReplay.parseIdentity("!"+retainedSign+"p(1);").ground()),
                    "actual conflict contains the forward source chain inside its contrapositive endpoint");
            DmzCacheRestrictionSet empty=DmzCacheRestrictionSet.begin(evidence,q);
            require(!evidence.project(stem,q,10000).eligible,"endpoint-only permission does not widen implicitly");
            final DmzProofWitnesses.Witness donor=primary;
            require(!evidence.projectCausal(donor,q,10000).eligible,"Q primary cannot be selected through causal ancestry");
            require(!evidence.projectCausal(stem,new Mind(user),10000).eligible,"another Mind cannot borrow causal ancestry");
            require(evidence.projectCausal(stem,q,1).truncated || protectedRule && warmA,"bounded ancestry lookup refuses before permission");
            reject(() -> empty.append(stem,q,10000),"default append retains endpoint-only contract");
            DmzCacheRestrictionSet retained=empty.append(endpoint,q,10000);
            {
                DmzTypedNativeResume.Result original=DmzTypedNativeResume.queryAccumulated(retained,q,10000,"?p(1);");
                require(original.compiled==!aliases,"remaining independent support controls endpoint continuation");
                if(!aliases)require(original.queryAnswer!=null && Boolean.valueOf(warmA?!negative:negative).equals(original.queryAnswer.value),
                        "endpoint cut retains the original external signed opinion");
            }
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q),"first native sibling rolls back completely");
            if(protectedRule && warmA) {
                require(!evidence.projectCausal(stem,q,10000).eligible,"Q production's derived stem is protected");
                reject(() -> empty.appendCausal(stem,q,10000),"no opposite branch obtained by weakening Q");
                return;
            }
            DmzCacheCollisionEvidence.Projection permission=evidence.projectCausal(stem,q,10000);
            require(permission.eligible && permission.removed==1 && permission.causalRoute!=null && permission.causalRoute.isCurrent(q),
                    "actual external stem has separate live native storage coverage and one exact cut");
            DmzProofWitnesses.Witness rebuilt=DmzProofWitnesses.enumerate(evidence.graph,stem.node,10000).witnesses.get(0);
            require(!evidence.projectCausal(rebuilt,q,10000).eligible,"reconstructed lookalike is not an offered observed subchain object");
            DmzCacheRestrictionSet oppositeBranch=empty.appendCausal(stem,q,10000);
            require(empty.selections.isEmpty() && retained.selections.get(0)==endpoint && oppositeBranch.selections.get(0)==stem,
                    "two sibling prefixes remain immutable and independent");
            DmzTypedNativeResume.Result alternative=DmzTypedNativeResume.queryAccumulated(oppositeBranch,q,10000,"?p(1);");
            require(alternative.compiled==!aliases,"causal cut does not discard independent alternative support");
            if(!aliases) {
                require(alternative.queryAnswer!=null && Boolean.valueOf(warmA?negative:!negative).equals(alternative.queryAnswer.value)
                        && !alternative.queryAnswer.proofs.isEmpty(),"cutting the other actual external chain returns the opposing native opinion");
                require(!present(alternative,"!"+retainedSign+"p(1);") && !present(alternative,selectedRoot)
                        && present(alternative,"!"+(warmA?opposite:sign)+"p(1);"),"dependent caches retract and the other source conclusion is materialized");
            } else require(!alternative.conflicts.isEmpty() && alternative.retractedCaches.isEmpty()
                    && present(alternative,"!"+retainedSign+"p(1);") && present(alternative,selectedRoot),
                    "other exact source routes retain actual caches and the real conflict");
            require(present(alternative,"!a(1);") && present(alternative,"!b(1);")
                    && present(alternative,"!"+sign+"p(2);") && present(alternative,"!"+opposite+"p(3);"),
                    "both Q donors and unrelated source substitutions survive");
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && oppositeBranch.isCurrent(q)
                    && !q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty() && user.getCurrentMind()==q,
                    "opposite sibling restores Q, sources, owner and restriction scope");
            DmzTypedNativeResume.Result repeated=DmzTypedNativeResume.queryAccumulated(retained,q,10000,"?p(1);");
            require(repeated.compiled==!aliases && (aliases || repeated.queryAnswer!=null
                    && Boolean.valueOf(warmA?!negative:negative).equals(repeated.queryAnswer.value))
                    && state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q),
                    "evaluating the opposite sibling does not change the original branch or its native answer");
            reject(() -> oppositeBranch.appendCausal(stem,q,10000),"causal entry point requires a fresh initial prefix");
            try(TechnicalMindTransaction occupied=TechnicalMindTransaction.beginIsolated(q)) {
                reject(() -> DmzTypedNativeResume.queryAccumulated(oppositeBranch,q,10000,"?p(1);"),"native continuation requires exclusive ownership");
            }
            raw(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,"!later(9);");
            require(!oppositeBranch.isCurrent(q) && !evidence.projectCausal(stem,q,10000).eligible,"later source identity invalidates both causal receipts");
        }
    }
    private static void reject(Attempt attempt,String message)throws Exception {
        try {attempt.run();throw new AssertionError(message);}catch(IllegalStateException expected){++checks;}
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
