/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.DmzConditionalResult;
import org.kanger.udf.UDF;

/** A provider's initial branch must preserve every Q proof before the first candidate choice. */
public final class DmzNeutralCandidateWorkflowRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            for (boolean negative : new boolean[] {false, true})
                for (boolean aliases : new boolean[] {false, true})
                    for (boolean independent : new boolean[] {false, true}) run(negative, aliases, independent);
            primaryOnly(false); primaryOnly(true); refusals(); unsupportedCache(); authorityLabelRefusal();
            unsupportedArgument("!word(item);"); unsupportedArgument("!fraction(1.5);");
            System.out.println("DMZ_NEUTRAL_CANDIDATE_WORKFLOW_PASS checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static DmzStoredRetractionTransaction.ConditionalQueryLimits limits(int nodes) {
        return new DmzStoredRetractionTransaction.ConditionalQueryLimits(10000,10000,10000,10000,4,nodes,1000000);
    }
    private static void accept(Mind q, IContextResults.Revision source, long id, String statement) throws Exception {
        require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,source,id,DmzReplayProvenance.Authority.EXTERNAL,statement)), "settled Q source");
    }
    private static void run(boolean negative, boolean aliases, boolean independent) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source = new IContextResults.Revision(UUID.randomUUID(),1);
        IContextResults.Revision external = new IContextResults.Revision(UUID.randomUUID(),7);
        DmzStoredRetractionTransaction.ConditionalQueryResult retained;
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q,source,1,"!@x source(x) -> middle(x);");
            accept(q,source,2,negative ? "!@x middle(x) -> ~derived(x);" : "!@x middle(x) -> derived(x);");
            if (aliases) DmzReplayProvenance.replayRule(q,source,102,DmzReplayProvenance.Authority.EXTERNAL,
                    negative ? "!@x middle(x) -> ~derived(x);" : "!@x middle(x) -> derived(x);");
            if (independent) accept(q,source,3,"!@x independent(x) -> middle(x);");
            accept(q,source,4,"!source(1);");
            String state = DmzObservationStateFingerprint.capture(q);
            String query = negative ? "?~derived(1);" : "?derived(1);";
            String candidate = negative ? "!derived(2);" : "!~derived(2);";
            if (!negative && !aliases && !independent) {
                DmzCurrentUnaryProofInventory inventory=DmzCurrentUnaryProofInventory.capture(q,10000);
                for (DmzObservedProofGraph.Node node:inventory.graph.observed.nodes) if(node.ground.predicate.equals("derived")) {
                    DmzProofWitnesses.Witness witness=inventory.proofs(node.ground,java.util.Collections.<DmzProofWitnesses.NoGood>emptyList(),10000).witnesses.get(0);
                    try(DmzTerminalRestriction active=DmzTerminalRestriction.beginContinuation(q,witness)) {
                        boolean refused=false;
                        try(DmzStoredRetractionTransaction unexpected=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000)) {}
                        catch(IllegalStateException expected) { refused=expected.getMessage().contains("cannot inherit Q exclusions"); }
                        require(refused,"neutral entry refuses inherited Q no-goods");
                    }
                }
            }
            try (DmzStoredRetractionTransaction workflow = DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000)) {
                require(workflow.classifyInput(query.replace('?', '!'),10000)==DmzStoredRetractionTransaction.InputKind.SUPPORTED_DERIVED,
                        "initial branch retains Q generated proof rather than an arbitrary seed cut");
                require(Boolean.TRUE.equals(workflow.queryContinuation(query)), "unrestricted initial Q query");
                require(workflow.deniedCount()==0 && workflow.queryDeniedCount()==0 && workflow.inventoryCheckCount()==0
                        && workflow.cacheAdmissionCount()==0, "no initial restriction or cache retraction");
                require(Boolean.TRUE.equals(workflow.accept(external,10,DmzReplayProvenance.Authority.EXTERNAL,"!source(2);")), "pending external input");
                if (independent) require(Boolean.TRUE.equals(workflow.accept(external,11,DmzReplayProvenance.Authority.EXTERNAL,"!independent(2);")), "independent external route");
                DmzStoredRetractionTransaction.CandidateEvaluation completed=workflow.evaluateCandidate(external,12,candidate,query,10000,10000,limits(64));
                retained=completed.conditional;
                require(retained!=null && retained.family!=null && retained.family.outcome==DmzStoredRetractionTransaction.ReplayTraversalOutcome.COMPLETE
                        && !retained.restartRequired && retained.family.opinions.size()>=2, "complete consequence family from neutral root");
                DmzConditionalResultContractRunner.verify(retained);
                for (DmzStoredRetractionTransaction.ReplayOpinion opinion : retained.family.opinions)
                    require(Boolean.TRUE.equals(opinion.application.query.value), "every opinion preserves Q derived proof");
                DmzStoredRetractionTransaction.CandidateEvaluation stopped=workflow.evaluateCandidate(external,12,candidate,query,10000,10000,limits(1));
                require(stopped.conditional.restartRequired && stopped.conditional.family.unstartedRoot!=null,
                        "neutral coordinated limit retains root and requires fresh restart");
                DmzConditionalResultContractRunner.verify(stopped.conditional);
                String opposedQ=negative ? "!derived(1);" : "!~derived(1);";
                DmzStoredRetractionTransaction.CandidateEvaluation rejected=workflow.evaluateCandidate(external,13,opposedQ,query,10000,10000,limits(64));
                require(rejected.status==DmzStoredRetractionTransaction.CandidateStatus.DECLINED_Q && rejected.query==null,
                        "Q priority does not depend on an initial exclusion");
                DmzConditionalResultContractRunner.verify(rejected.conditional);
                require(Boolean.TRUE.equals(workflow.queryContinuation(query)) && state.equals(DmzObservationStateFingerprint.capture(q)),
                        "family, stop and refusal restore branch and Q");
            }
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "neutral overlay closure preserves complete Q state");
            try (DmzStoredRetractionTransaction fresh= DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000)) {
                require(fresh.classifyInput("!source(2);",10000)==DmzStoredRetractionTransaction.InputKind.FRESH,
                        "fresh neutral owner contains no earlier pending input or no-good");
            }
        }
        DmzConditionalResultContractRunner.verify(retained);
        DmzConditionalResult publicResult=retained.toContextResult();
        require(publicResult.family.outcome==DmzConditionalResult.Stop.COMPLETE, "provider snapshot after all owners close");
    }
    private static void primaryOnly(boolean negative) throws Exception {
        User user=new User(); new UDF().init(user); Mind q=new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source=new IContextResults.Revision(UUID.randomUUID(),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            String fact=negative ? "!~primary(1);" : "!primary(1);";
            accept(q,source,1,fact);
            String state=DmzObservationStateFingerprint.capture(q);
            DmzStoredRetractionTransaction workflow=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000);
            try {
                require(workflow.classifyInput(fact,10000)==DmzStoredRetractionTransaction.InputKind.EXISTING_PRIMARY, "Q requires no fabricated unit-production anchor");
                require(Boolean.TRUE.equals(workflow.queryContinuation(fact.replace('!', '?'))), "primary-only Q query");
                DmzStoredRetractionTransaction.CandidateEvaluation reuse=workflow.evaluateCandidate(source,2,fact,fact.replace('!', '?'),10000,10000,limits(64));
                require(reuse.status==DmzStoredRetractionTransaction.CandidateStatus.REUSED_PRIMARY && reuse.conditional==null,
                        "neutral entry preserves explicit legacy status for nonbranching reuse");
            } finally { workflow.close(); workflow.close(); }
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "idempotent neutral close");
            boolean closed=false;
            try { workflow.queryContinuation("?primary(1);"); } catch(IllegalStateException expected) { closed=true; }
            require(closed,"closed neutral owner refuses inference");
        }
    }
    private static void refusals() throws Exception {
        User user=new User(); new UDF().init(user); Mind q=new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source=new IContextResults.Revision(UUID.randomUUID(),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            accept(q,source,1,"!primary(1);");
            String state=DmzObservationStateFingerprint.capture(q);
            for(int budget:new int[]{0,10001,1}) {
                boolean refused=false;
                try(DmzStoredRetractionTransaction unexpected=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,budget)) {}
                catch(IllegalArgumentException expected) { refused=true; }
                catch(IllegalStateException expected) { refused=true; }
                require(refused && state.equals(DmzObservationStateFingerprint.capture(q)),"invalid/exhausted preparation fails before opening child");
            }
            try(TechnicalMindTransaction pending=TechnicalMindTransaction.beginIsolated(q)) {
                DmzReplayProvenance.acceptRule(pending.mind(),source,2,DmzReplayProvenance.Authority.EXTERNAL,"!pending(2);");
                boolean refused=false;
                try(DmzStoredRetractionTransaction unexpected=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,pending.mind(),10000)) {}
                catch(IllegalStateException expected) { refused=expected.getMessage().contains("Settled qualified Q"); }
                require(refused,"pending external transaction cannot become authoritative Q");
            }
            require(state.equals(DmzObservationStateFingerprint.capture(q)),"refused pending authority rolls back");
        }
        Mind unknown=new Mind(user); user.setCurrentMind(unknown);
        unknown.query("!unknown(1);",null,false);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin(unknown)) {
            boolean refused=false;
            try(DmzStoredRetractionTransaction unexpected=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,unknown,10000)) {}
            catch(IllegalStateException expected) { refused=expected.getMessage().contains("missing-live-source"); }
            require(refused,"unobserved storage state is not relabeled as a sourced Q");
        }
    }
    private static void unsupportedCache() throws Exception {
        User user=new User(); new UDF().init(user); Mind q=new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source=new IContextResults.Revision(UUID.randomUUID(),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            accept(q,source,1,"!@x source(x) -> derived(x);");
            accept(q,source,2,"!source(1);");
            for(org.kanger.interfaces.IRule rule:q.getRules())
                if(!rule.isDeleted(q) && !q.getRules().isGenerated(rule) && rule.getOrigin().equals("!source(1);"))
                    ((org.kanger.units.Rule)rule).setDeleted(true,q);
            String state=DmzObservationStateFingerprint.capture(q);
            boolean refused=false;
            try(DmzStoredRetractionTransaction unexpected=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000)) {}
            catch(IllegalStateException expected) { refused=expected.getMessage().contains("Cached Q atom has no complete sourced support"); }
            require(refused && state.equals(DmzObservationStateFingerprint.capture(q)),"unsupported native cache is refused rather than promoted to Q evidence");
        }
    }
    private static void authorityLabelRefusal() throws Exception {
        User user=new User(); new UDF().init(user); Mind q=new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source=new IContextResults.Revision(UUID.randomUUID(),1);
        IContextResults.Revision external=new IContextResults.Revision(UUID.randomUUID(),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            DmzReplayProvenance.acceptRule(q,source,1,DmzReplayProvenance.Authority.TARGET_Q,"!@x source(x) -> middle(x);");
            DmzReplayProvenance.acceptRule(q,source,2,DmzReplayProvenance.Authority.TARGET_Q,"!@x middle(x) -> derived(x);");
            accept(q,source,3,"!source(1);");
            String state=DmzObservationStateFingerprint.capture(q);
            try(DmzStoredRetractionTransaction workflow=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000)) {
                workflow.accept(external,4,DmzReplayProvenance.Authority.EXTERNAL,"!source(2);");
                boolean refused=false;
                try { workflow.evaluateCandidate(external,5,"!~derived(2);","?derived(1);",10000,10000,limits(64)); }
                catch(IllegalArgumentException expected) { refused=expected.getMessage().contains("External derived unit witness required"); }
                require(refused && state.equals(DmzObservationStateFingerprint.capture(q)),"neutral entry does not expand no-good rights to TARGET_Q productions");
                require(Boolean.TRUE.equals(workflow.queryContinuation("?derived(1);")),"unsupported application gate unwinds owned child and preserves Q");
            }
            require(state.equals(DmzObservationStateFingerprint.capture(q)),"authority-label phase refusal preserves Q on closure");
        }
    }
    private static void unsupportedArgument(String statement) throws Exception {
        User user=new User(); new UDF().init(user); Mind q=new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source=new IContextResults.Revision(UUID.randomUUID(),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            accept(q,source,1,statement);
            String state=DmzObservationStateFingerprint.capture(q);
            boolean refused=false;
            try(DmzStoredRetractionTransaction unexpected=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000)) {}
            catch(IllegalStateException expected) { refused=expected.getMessage().contains("Only unary integer Q grounds"); }
            require(refused && state.equals(DmzObservationStateFingerprint.capture(q)),"broader Q grammar is refused before child creation");
        }
    }
    private static void require(boolean value,String reason) { ++checks; if(!value)throw new AssertionError("Neutral candidate workflow: "+reason); }
}
