/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.interfaces.IContextResults;

/** Rebinds accumulated exact chains to freshly prepared, pinned source declarations. */
public final class DmzAccumulatedSourcePreparationRunner {
    private static int checks;
    public static void main(String[] args) {
        try { run();System.out.println("DMZ_ACCUMULATED_SOURCE_PREPARATION_PASS checks="+checks);System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    private static void run() throws Exception {
        User sourceUser=new User();
        Mind qSource=new Mind(sourceUser);sourceUser.setCurrentMind(qSource);
        Mind rulesSource=new Mind(sourceUser);
        require(rulesSource.compile("!@x a(x) -> b(x); !@x c(x) -> d(x);",null,false),"physical source productions");
        require(qSource.compile("!a(1); !~b(1); !c(2); !~d(2);",null,false),"physical Q primary inputs");
        DmzSourceShape.Observation qInputs=DmzSourceShape.inspect(qSource,100);
        DmzSourceShape.Observation ruleInputs=DmzSourceShape.inspect(rulesSource,100);
        IContextResults.Revision qRevision=pin(),rulesRevision=pin();
        DmzSourceShape.Declaration a=declaration(qInputs,"!a(1);"),c=declaration(qInputs,"!c(2);");
        DmzSourceShape.Declaration opposite=declaration(qInputs,"!~b(1);"),oppositeD=declaration(qInputs,"!~d(2);");
        DmzSourceShape.Declaration production=declaration(ruleInputs,"!@x a(x) -> b(x);");
        DmzSourceShape.Declaration productionD=declaration(ruleInputs,"!@x c(x) -> d(x);");

        User owner=new User();Mind q=new Mind(owner);owner.setCurrentMind(q);
        DmzCacheRestrictionSet set;
        DmzCandidateSession.PreparedRestrictions validReceipt;
        DmzCacheRestrictionSet.Continuation firstContinuation;
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            DmzReplayProvenance.replayRule(q,rulesRevision,production.sourceRule,DmzReplayProvenance.Authority.EXTERNAL,production.statement);
            DmzReplayProvenance.replayRule(q,rulesRevision,productionD.sourceRule,DmzReplayProvenance.Authority.EXTERNAL,productionD.statement);
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,qRevision,a.sourceRule,
                    DmzReplayProvenance.Authority.TARGET_Q,a.statement)),"accepted Q source materializes first cache");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,qRevision,c.sourceRule,
                    DmzReplayProvenance.Authority.TARGET_Q,c.statement)),"accepted Q source materializes second cache");
            DmzReplayProvenance.replayRule(q,qRevision,opposite.sourceRule,DmzReplayProvenance.Authority.TARGET_Q,opposite.statement);
            DmzReplayProvenance.replayRule(q,qRevision,oppositeD.sourceRule,DmzReplayProvenance.Authority.TARGET_Q,oppositeD.statement);
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);
            CollisionProofCapture.Conflict event;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                require(!q.compile("!anchor();",null,false),"native source conflict is observed");
                event=collisions.snapshot().get(0);
            }
            DmzCacheCollisionEvidence evidence=DmzCacheCollisionEvidence.bind(baseline,event,q,10000);
            require(evidence.eligible && !evidence.combinations.isEmpty(),"exact cache conflict has covered source routes");
            DmzCacheRestrictionSet empty=DmzCacheRestrictionSet.begin(evidence,q);
            DmzProofWitnesses.Witness selected=null;
            for(DmzWitnessConflicts.Combination pair:evidence.combinations)
                for(DmzProofWitnesses.Witness witness:Arrays.asList(pair.left,pair.right))
                    if(witness.step>=0 && witness.source.authority==DmzReplayProvenance.Authority.EXTERNAL)selected=witness;
            require(selected!=null,"restriction selects an external derived chain");
            set=empty.append(selected,q,10000);

            DmzCandidateSession.Participant authority=new DmzCandidateSession.Participant(qRevision,qInputs);
            DmzCandidateSession.Participant external=new DmzCandidateSession.Participant(rulesRevision,ruleInputs);
            DmzCacheRestrictionSet.SourceReceipt sourceReceipt=set.prepareSourceReceipt(Arrays.asList(authority,external),q,10000);
            String originalQState=DmzObservationStateFingerprint.capture(q);
            try(DmzCandidateSession prepared=DmzCandidateSession.open(authority,Collections.singletonList(external),10000)) {
                validReceipt=prepared.prepareAccumulatedRestrictions(set,sourceReceipt,10000);
                require(validReceipt.isCurrent(set,prepared),"exact acquired source revisions bind the complete chain");
                require(!validReceipt.proofs().isEmpty(),"prepared restriction receipt exposes detached source proof trees");
                require(prepared.isCurrent(),"prepared source owner remains current before native continuation");
                try { validReceipt.runAccumulated(q,0);throw new AssertionError("invalid prepared branch budget accepted"); }
                catch(IllegalStateException expected) { ++checks; }
                require(prepared.isCurrent(),"rejected invalid budget preserves prepared owner");
                DmzCandidateSession.SequenceResult projected=prepared.evaluateSequence(Collections.<DmzCandidateSession.SourceRule>emptyList(),
                        Collections.<DmzCandidateSession.SourceRule>emptyList(),Collections.<DmzCandidateSession.SourceRule>emptyList(),"?b(1);",
                        new DmzCandidateSession.Limits(10000,10000,10000,10000,10000,10000,10000,8,128,1000000),1000);
                require(projected.restrictionProofs.size()==validReceipt.proofs().size(),
                        "sequence result carries the prepared restriction provenance");
                org.kanger.interfaces.DmzSourcesQuery.Proof detached=projected.restrictionProofs.get(0);
                require(detached.source.revision.getContextId().equals(rulesRevision.getContextId())
                        && detached.source.revision.getRevision()==rulesRevision.getRevision()
                        && detached.source.rule==selected.source.sourceRule
                        && detached.premises.size()==1,"detached proof preserves exact source identity and premise chain");
                require(detached.premises.get(0).source.revision.getContextId().equals(qRevision.getContextId())
                        && detached.premises.get(0).source.rule==selected.premises.get(0).source.sourceRule,
                        "detached primary premise identifies exact Q source revision");
                DmzCacheRestrictionSet.Continuation applied=validReceipt.continueAccumulated(q,10000);
                firstContinuation=applied;
                DmzTypedNativeResume.Result appliedResult=applied.result;
                require(applied.status==DmzCacheRestrictionSet.ContinuationStatus.NEXT_CONFLICT
                        && !appliedResult.compiled && !appliedResult.conflicts.isEmpty()
                        && !appliedResult.complete && !appliedResult.provisional,
                        "prepared first choice leaves the second native cache conflict");
                require(applied.detached().status==org.kanger.interfaces.DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT
                        && applied.detached().choices==1,"intermediate continuation projects a detached public outcome");
                require(applied.next!=null && appliedResult.belongsTo(set,q),"prepared native result remains bound to the restriction owner");
                require(set.isCurrent(q),"prepared accumulated restriction boundary restored");
                require(originalQState.equals(DmzObservationStateFingerprint.capture(q)),"prepared native child restores Q facts");
                require(DmzReplayProvenance.sourceCheckpoint(q).isCurrent(q),"prepared native child restores source observations");
                require(!q.hasPendingTransactions() && DmzTerminalRestriction.activeNoGoods(q).isEmpty(),
                        "prepared native child restores transaction and restriction scopes");
                require(!q.hasPendingTransactions() && owner.getCurrentMind()==q,"source preparation leaves original Q ownership intact");
            }
            require(!validReceipt.isCurrent(set,null),"closed prepared source owner invalidates its receipt");

            DmzCacheRestrictionSet.Next next=firstContinuation.next;
            DmzProofWitnesses.Witness selectedNext=null;
            for(DmzWitnessConflicts.Combination pair:next.combinations)
                for(DmzProofWitnesses.Witness witness:Arrays.asList(pair.left,pair.right))
                    if(witness.step>=0 && witness.source.authority==DmzReplayProvenance.Authority.EXTERNAL)selectedNext=witness;
            require(selectedNext!=null,"successive conflict exposes another exact external source chain");
            set=set.append(next,selectedNext,q,10000);
            DmzCacheRestrictionSet.SourceReceipt secondSourceReceipt=set.prepareSourceReceipt(Arrays.asList(authority,external),q,10000);
            DmzCandidateSession.PreparedRestrictions secondReceipt;
            try(DmzCandidateSession prepared=DmzCandidateSession.open(authority,Collections.singletonList(external),10000)) {
                secondReceipt=prepared.prepareAccumulatedRestrictions(set,secondSourceReceipt,10000);
                require(secondReceipt.proofs().size()==2,"rebound receipt carries both accumulated source choices");
                try(DmzCandidateSession other=DmzCandidateSession.open(authority,Collections.singletonList(external),10000)) {
                    require(!secondReceipt.isCurrent(set,other),"successive receipt cannot be borrowed by another owner");
                }
                DmzCacheRestrictionSet.Continuation completed=secondReceipt.continueAccumulated(q,10000);
                DmzTypedNativeResume.Result completedResult=completed.result;
                require(completed.status==DmzCacheRestrictionSet.ContinuationStatus.COMPLETE
                        && completedResult.compiled && !completedResult.retractedCaches.isEmpty()
                        && !completedResult.complete && !completedResult.provisional,
                        "second exact choice completes native continuation after both conflicts");
                require(completed.detached().status==org.kanger.interfaces.DmzSourcesQuery.RestrictionStatus.COMPLETE
                        && completed.detached().choices==2,"completed continuation projects its detached choice count");
                require(completedResult.belongsTo(set,q) && completed.next==null && set.isCurrent(q),
                        "completed continuation remains bound to the final restriction prefix");
                require(DmzObservationStateFingerprint.capture(q).equals(originalQState)
                        && DmzReplayProvenance.sourceCheckpoint(q).isCurrent(q),
                        "successive native continuation restores Q facts and source observations");
            }
            sourceReceipt=secondSourceReceipt;
            validReceipt=secondReceipt;

            DmzSourceShape.Observation emptyRules=DmzSourceShape.inspect(new Mind(new User()),100);
            DmzCandidateSession.Participant missing=new DmzCandidateSession.Participant(rulesRevision,emptyRules);
            try(DmzCandidateSession prepared=DmzCandidateSession.open(authority,Collections.singletonList(missing),10000)) {
                try { prepared.prepareAccumulatedRestrictions(set,sourceReceipt,10000);throw new AssertionError("missing selected source accepted"); }
                catch(IllegalStateException expected) { ++checks; }
            }
            DmzCandidateSession.Participant wrongRevision=new DmzCandidateSession.Participant(
                    new IContextResults.Revision(rulesRevision.getContextId(),rulesRevision.getRevision()+1),ruleInputs);
            try(DmzCandidateSession prepared=DmzCandidateSession.open(authority,Collections.singletonList(wrongRevision),10000)) {
                try { prepared.prepareAccumulatedRestrictions(set,sourceReceipt,10000);throw new AssertionError("floating source revision accepted"); }
                catch(IllegalStateException expected) { ++checks; }
            }
            require(set.isCurrent(q),"refused source preparations preserve the restriction prefix");
            DmzReplayProvenance.replayRule(q,pin(),0,DmzReplayProvenance.Authority.EXTERNAL,production.statement);
            require(!set.isCurrent(q),"late source alias invalidates the restriction boundary");
            try { set.prepareSourceReceipt(Arrays.asList(authority,external),q,10000);throw new AssertionError("stale restriction minted a source receipt"); }
            catch(IllegalStateException expected) { ++checks; }
        }
        require(!set.isCurrent(q),"closing the source capture invalidates the originating restriction owner");
    }
    private static DmzSourceShape.Declaration declaration(DmzSourceShape.Observation inputs,String statement) {
        for(DmzSourceShape.Declaration declaration:inputs.declarations)
            if(declaration.statement.replaceAll("\\s+","").equals(statement.replaceAll("\\s+", "")))return declaration;
        throw new AssertionError("Missing source declaration: "+statement);
    }
    private static IContextResults.Revision pin() { return new IContextResults.Revision(UUID.randomUUID(),1); }
    private static void require(boolean value,String message) { ++checks;if(!value)throw new AssertionError(message); }
}
