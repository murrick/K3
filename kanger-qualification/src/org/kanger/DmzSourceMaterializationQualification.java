/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IContextResults;

/** Real provider declarations rebuilt under capture; no source cache is imported. */
public final class DmzSourceMaterializationQualification {
    public static String fingerprint(Mind mind) throws Exception { return DmzObservationStateFingerprint.capture(mind); }
    public static int verify(DmzSourceShape.Observation inputs,IContextResults.Revision pin) throws Exception {
        User user=new User(); Mind root=new Mind(user); user.setCurrentMind(root);
        Mind q=Mind.contextConnectionLayer(root); user.setCurrentMind(q);
        int checks=0;
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin()) {
            try(DmzReplayProvenance.Preparation preparation=DmzReplayProvenance.preparation(q)) {
                for(DmzSourceShape.Declaration input:inputs.declarations) {
                    // EXTERNAL is the existing application-gate transport capability.
                    // Semantic authority is fixed independently by the neutral Q owner.
                    if(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,input.sourceRule,
                            DmzReplayProvenance.Authority.EXTERNAL,input.statement)))
                        throw new AssertionError("Q primary materialization refused");
                    ++checks;
                }
                preparation.accept();
            }
            if(journal.settlementSnapshot().accepted.size()!=inputs.primaryRules)
                throw new AssertionError("Each primary declaration retains its exact source occurrence");
            ++checks;
            java.util.Set<Long> expectedIds=new java.util.HashSet<Long>();
            for(DmzSourceShape.Declaration input:inputs.declarations)expectedIds.add(input.sourceRule);
            for(DmzReplayProvenance.Binding binding:journal.settlementSnapshot().accepted) {
                if(!binding.context.equals(pin.getContextId()) || binding.revision!=pin.getRevision() || !expectedIds.remove(binding.sourceRule))
                    throw new AssertionError("Materialization floated the source pin");
                ++checks;
            }
            if(!expectedIds.isEmpty())throw new AssertionError("Materialization lost source-local IDs");
            String state=DmzObservationStateFingerprint.capture(q);
            try(DmzStoredRetractionTransaction workflow=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000)) {
                if(!Boolean.TRUE.equals(workflow.queryContinuation("?derived(1);")))throw new AssertionError("Q consequence was not rebuilt");
                ++checks;
                IContextResults.Revision foreign=new IContextResults.Revision(java.util.UUID.randomUUID(),1);
                DmzStoredRetractionTransaction.CandidateEvaluation refusal=workflow.evaluateCandidate(foreign,1,
                        "!~derived(1);","?derived(1);",10000,10000,
                        new DmzStoredRetractionTransaction.ConditionalQueryLimits(10000,10000,10000,10000,4,64,1000000));
                if(refusal.status!=DmzStoredRetractionTransaction.CandidateStatus.DECLINED_Q)
                    throw new AssertionError("Transport labels weakened reconstructed Q authority");
                ++checks;
                if(!Boolean.TRUE.equals(workflow.accept(foreign,2,DmzReplayProvenance.Authority.EXTERNAL,"!authority(2);")))
                    throw new AssertionError("Foreign candidate support refused");
                ++checks;
                DmzStoredRetractionTransaction.CandidateEvaluation branched=workflow.evaluateCandidate(foreign,3,
                        "!~derived(2);","?derived(1);",10000,10000,
                        new DmzStoredRetractionTransaction.ConditionalQueryLimits(10000,10000,10000,10000,4,64,1000000));
                if(branched.conditional==null || branched.conditional.family==null
                        || branched.conditional.family.outcome!=DmzStoredRetractionTransaction.ReplayTraversalOutcome.COMPLETE
                        || branched.conditional.family.opinions.size()<2 || branched.conditional.restartRequired)
                    throw new AssertionError("Rebuilt Q production cannot support exact conditional gating");
                ++checks;
                for(DmzStoredRetractionTransaction.ReplayOpinion opinion:branched.conditional.family.opinions) {
                    if(!Boolean.TRUE.equals(opinion.application.query.value))throw new AssertionError("Conditional gating weakened original Q answer");
                    ++checks;
                }
                if(branched.conditional.toContextResult().family.outcome!=org.kanger.interfaces.DmzConditionalResult.Stop.COMPLETE)
                    throw new AssertionError("Detached result projection failed");
                ++checks;
            }
            if(!state.equals(DmzObservationStateFingerprint.capture(q)))throw new AssertionError("Workflow modified reconstructed Q");
            ++checks;
        } finally { root.discardEphemeral(q); user.setCurrentMind(root); }
        return checks;
    }
}
