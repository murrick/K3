/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Native signed-input continuation and isolated signed recursive query qualification. */
public final class DmzNativeRecursiveContinuationRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-native-recursive-").toString());
        for (boolean alternative : new boolean[] {false, true})
            for (boolean duplicate : new boolean[] {false, true})
                for (boolean primaryDuplicate : new boolean[] {false, true})
                    for (boolean negative : new boolean[] {false, true})
                        for (int failureMode : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8})
                            run(alternative, duplicate, primaryDuplicate, negative, failureMode);
        authorityStaleness(false); authorityStaleness(true);
        System.out.println("DMZ_NATIVE_RECURSIVE_CONTINUATION_PASS checks=" + checks);
    }
    private static void run(boolean alternative, boolean duplicate, boolean primaryDuplicate, boolean negative, int failureMode) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, rules, 10, "!@x source(x) -> middle(x);");
            if (alternative) {
                accept(q, rules, 11, "!@x source(x) -> other(x);");
                accept(q, rules, 12, "!@x other(x) -> middle(x);");
            }
            accept(q, rules, 13, negative ? "!@x middle(x) -> ~derived(x);" : "!@x middle(x) -> derived(x);");
            accept(q, rules, 14, negative ? "!@x ~derived(x) -> ~tail(x);" : "!@x derived(x) -> tail(x);");
            accept(q, rules, 16, "!@x independent(x) -> middle(x);");
            accept(q, rules, 98, "!@x ~negativeIndependent(x) -> middle(x);");
            accept(q, rules, 80, "!@x ~negativeSeed(x) -> negativeResult(x);");
            accept(q, fact, 81, "!~negativePrimary(7);");
            if (duplicate) DmzReplayProvenance.replayRule(q, rules, 15,
                    DmzReplayProvenance.Authority.EXTERNAL, negative ? "!@x middle(x) -> ~derived(x);" : "!@x middle(x) -> derived(x);");
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            require(Boolean.TRUE.equals(guard.acceptAliases(fact, primaryDuplicate ? new long[] {20,24} : new long[] {20},
                    DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "native input");
            TerminalSupportCapture.Materialization stored = stored(capture, "derived", null);
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored, 10000);
            DmzProofWitnesses.Witness blocked = null;
            for (DmzProofWitnesses.Witness witness : proof.witnesses)
                if (witness.source.sourceRule == 13 && witness.premises.get(0).source.sourceRule == 10
                        && witness.premises.get(0).premises.get(0).source.sourceRule == 20) blocked = witness;
            require(blocked != null, "exact generated-support chain selected");
            TerminalSupportCapture.Ground root = proof.graph.observed.nodes.get(proof.root).ground;
            TerminalSupportCapture.Materialization tail = stored(capture, "tail", null);
            TerminalSupportCapture.Ground tailGround = tail.causes.nodes.get(tail.causes.root).ground;
            int original = (alternative ? 2 : 1) * (duplicate ? 2 : 1) * (primaryDuplicate ? 2 : 1);
            require(q.query(signed(negative, "?derived(88);"), null, false) == null,
                    "parent unknown query seeds its own hypothesis store");
            require(!q.getHypothesis().isEmpty(), "parent hypothesis store is populated before branch opens");
            String state = DmzObservationStateFingerprint.capture(q);
            String parentHypotheses = hypothesisText(q);
            if (negative) {
                try (TechnicalMindTransaction admission = TechnicalMindTransaction.beginIsolated(q)) {
                    require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(admission.mind(), fact, 96,
                            DmzReplayProvenance.Authority.EXTERNAL, "!~derived(1);")),
                            "explicit negative primary accepted over generated negative target");
                    DmzCurrentUnaryProofInventory.Proofs admitted = DmzCurrentUnaryProofInventory.capture(
                            admission.mind(), 10000, true).proofs(root, java.util.Collections.emptyList(), 10000);
                    require(!admitted.truncated && admitted.provisional && admitted.witnesses.size() == original + 1,
                            "negative primary adds exactly one support beside generated paths");
                    boolean primary = false;
                    for (DmzProofWitnesses.Witness witness : admitted.witnesses)
                        if (witness.source.sourceRule == 96 && witness.premises.isEmpty()) primary = true;
                    require(primary, "new negative support is a distinct primary source witness");
                }
                DmzCurrentUnaryProofInventory.Proofs restored = DmzCurrentUnaryProofInventory.capture(q, 10000)
                        .proofs(root, java.util.Collections.emptyList(), 10000);
                require(!restored.truncated && !restored.provisional && restored.witnesses.size() == original,
                        "negative primary support disappears after isolated admission rollback");
                require(state.equals(DmzObservationStateFingerprint.capture(q)) && parentHypotheses.equals(hypothesisText(q)),
                        "negative admission preserves Q native and hypothesis boundary");
            }
            require(guard.auditRoutes(proof, blocked, q).matched,
                    "pre-branch route audit: " + guard.auditRoutes(proof, blocked, q).gaps);
            DmzStoredRetractionTransaction overlay = DmzStoredRetractionTransaction.beginContinuation(guard, proof, blocked, q, 10000);
            try {
                require(overlay.hasLiveRule(stored.nativeRule) == (original > 1)
                        && overlay.hasLiveRule(tail.nativeRule) == (original > 1), "native last-support retraction or retained alternative");
                count(overlay, root, original - 1, false);
                count(overlay, tailGround, original - 1, false);
                require(overlay.classifyInput("!" + (negative ? "~" : "") + "derived(1);", 10000)
                        == (original > 1 ? DmzStoredRetractionTransaction.InputKind.SUPPORTED_DERIVED
                                : DmzStoredRetractionTransaction.InputKind.FRESH),
                        "classification uses surviving current proofs rather than historical cache");
                require(overlay.classifyInput("!" + (negative ? "" : "~") + "derived(1);", 10000)
                        == (original > 1 ? DmzStoredRetractionTransaction.InputKind.CONFLICT
                                : DmzStoredRetractionTransaction.InputKind.FRESH),
                        "removed last support does not become a current conflict proof");
                require(overlay.classifyAuthorityInput("!" + (negative ? "~" : "") + "derived(1);", 10000)
                        == DmzStoredRetractionTransaction.InputKind.SUPPORTED_DERIVED
                        && overlay.classifyAuthorityInput("!" + (negative ? "" : "~") + "derived(1);", 10000)
                        == DmzStoredRetractionTransaction.InputKind.CONFLICT,
                        "Q authority remains supported and conflicting independently of branch last-support removal");
                DmzStoredRetractionTransaction.CandidateAssessment initialSame = overlay.assessCandidate(
                        "!" + (negative ? "~" : "") + "derived(1);", 10000);
                require(initialSame.authority == DmzStoredRetractionTransaction.InputKind.SUPPORTED_DERIVED
                        && initialSame.branch == (original > 1 ? DmzStoredRetractionTransaction.InputKind.SUPPORTED_DERIVED
                                : DmzStoredRetractionTransaction.InputKind.FRESH)
                        && initialSame.disposition == DmzStoredRetractionTransaction.CandidateDisposition.PROBE_NEW_INPUT,
                        "combined assessment retains Q and branch support distinctions");
                require(overlay.assessCandidate("!" + (negative ? "" : "~") + "derived(1);", 10000).disposition
                        == DmzStoredRetractionTransaction.CandidateDisposition.REJECT_Q,
                        "Q conflict wins even when branch has no proof");
                DmzReplayProvenance.Settlement beforeDecline = journal.settlementSnapshot();
                DmzStoredRetractionTransaction.CandidateEvaluation declined = overlay.evaluateCandidate(fact, 110,
                        "!" + (negative ? "" : "~") + "derived(1);", signed(negative, "?derived(1);"), 10000, 10000);
                require(declined.status == DmzStoredRetractionTransaction.CandidateStatus.DECLINED_Q && declined.query == null,
                        "Q conflict is declined without a truth-query result even if branch has no support");
                require(journal.settlementSnapshot().pending == beforeDecline.pending
                        && journal.settlementSnapshot().discarded == beforeDecline.discarded,
                        "declined candidate opens no input transaction or source binding");
                count(overlay, root, original - 1, false);
                boolean invalidBudget = false;
                try { overlay.queryContinuation(signed(negative, "?derived(1);"), 0); }
                catch (IllegalArgumentException expected) { invalidBudget = true; }
                require(invalidBudget, "invalid query budget rejected before scope creation");
                require(java.util.Objects.equals(overlay.queryContinuation(signed(negative, "?derived(1);")), original > 1 ? Boolean.TRUE : null),
                        "recursive native query respects exact chain restriction");
                require(java.util.Objects.equals(overlay.queryContinuation(signed(negative, "?tail(1);")), original > 1 ? Boolean.TRUE : null),
                        "downstream native query respects exact chain restriction");
                require(java.util.Objects.equals(overlay.queryContinuation(signed(negative, "?derived(1);")), original > 1 ? Boolean.TRUE : null),
                        "repeated query uses a fresh audit");
                require(!Boolean.TRUE.equals(overlay.queryContinuation(signed(negative, "?derived(99);"))), "absent substitution remains unknown");
                DmzStoredRetractionTransaction.QueryResult unknown = overlay.queryContinuationResult(
                        signed(negative, "?derived(99);"), 10000);
                require(unknown.value == null && !unknown.hypotheses.isEmpty(),
                        "unknown native query produces detached diagnostic hypotheses");
                boolean immutable = false;
                try { unknown.hypotheses.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
                require(immutable, "detached hypothesis list is immutable");
                require(parentHypotheses.equals(hypothesisText(q)), "query hypotheses cannot escape to Q");
                String hypotheses = unknown.hypotheses.toString();
                String candidate = null;
                for (String assertion : unknown.hypothesisAssertions) {
                    String normalized = assertion.replaceAll("\\s+", "").replace("(99.0)", "(99)");
                    if (normalized.equals("!source(99);")) { candidate = normalized; break; }
                }
                require(candidate != null, "native hypothesis has an assertion-ready positive candidate: " + unknown.hypothesisAssertions);
                boolean lookupBudgetRefused = false;
                try { overlay.probeCandidate(fact, 75, candidate, signed(negative, "?derived(99);"), 10000, 0); }
                catch (IllegalArgumentException expected) { lookupBudgetRefused = true; }
                require(lookupBudgetRefused, "invalid primary lookup budget rejected before mutation");
                DmzStoredRetractionTransaction.QueryResult candidateResult = overlay.probeCandidate(fact, 60, candidate,
                        signed(negative, "?derived(99);"), 10000);
                require(Boolean.TRUE.equals(candidateResult.value), "isolated native hypothesis replay supports its query: "
                        + candidate + ":" + negative + ":" + candidateResult.value + ":" + candidateResult.hypotheses);
                require(overlay.queryContinuation(signed(negative, "?derived(99);")) == null,
                        "candidate success does not become accepted branch evidence");
                require(overlay.probeCandidate(fact, 61, "!source(77);",
                        signed(negative, "?derived(99);"), 10000).value == null, "unrelated candidate cannot answer the query");
                require(java.util.Objects.equals(overlay.queryContinuation(signed(negative, "?derived(1);")),
                        original > 1 ? Boolean.TRUE : null), "candidate probes preserve the exact old restriction");
                DmzStoredRetractionTransaction.QueryResult duplicateCandidate = overlay.probeCandidate(fact, 73,
                        "!source(1);", signed(negative, "?derived(1);"), 10000);
                require(duplicateCandidate.candidateMode == DmzStoredRetractionTransaction.QueryResult.CandidateMode.EXISTING_PRIMARY,
                        "primary duplicate explicitly reports no new input");
                require(java.util.Objects.equals(duplicateCandidate.value, original > 1 ? Boolean.TRUE : null),
                        "primary duplicate cannot manufacture an alternative to the blocked chain");
                require(candidateResult.candidateMode == DmzStoredRetractionTransaction.QueryResult.CandidateMode.NEW_INPUT,
                        "fresh candidate reports conditional new input");
                require(Boolean.TRUE.equals(overlay.probeCandidate(fact, 64, "!middle(1);",
                        signed(negative, "?derived(1);"), 10000).value),
                        "explicit primary occurrence of cached generated support authorizes the same ground");
                require(java.util.Objects.equals(overlay.queryContinuation(signed(negative, "?derived(1);")),
                        original > 1 ? Boolean.TRUE : null), "same-ground candidate aliases disappear after probe rollback");
                if (negative) {
                    DmzStoredRetractionTransaction.QueryResult generatedNegative = overlay.probeCandidate(fact, 97,
                            "!~derived(1);", "?~tail(1);", 10000);
                    require(Boolean.TRUE.equals(generatedNegative.value)
                            && generatedNegative.candidateMode == DmzStoredRetractionTransaction.QueryResult.CandidateMode.NEW_INPUT,
                            "negative generated target admits conditional primary rather than duplicate shortcut");
                    count(overlay, root, original - 1, false);
                    count(overlay, tailGround, original - 1, false);
                }
                DmzStoredRetractionTransaction.QueryResult negativeDuplicate = overlay.probeCandidate(fact, 82,
                        "!~negativePrimary(7);", "?~negativePrimary(7);", 10000);
                require(Boolean.TRUE.equals(negativeDuplicate.value)
                        && negativeDuplicate.candidateMode == DmzStoredRetractionTransaction.QueryResult.CandidateMode.EXISTING_PRIMARY,
                        "negative primary duplicate reuses the existing signed source");
                DmzStoredRetractionTransaction.QueryResult negativeFresh = overlay.probeCandidate(fact, 83,
                        "!~negativeSeed(8);", "?negativeResult(8);", 10000);
                require(Boolean.TRUE.equals(negativeFresh.value)
                        && negativeFresh.candidateMode == DmzStoredRetractionTransaction.QueryResult.CandidateMode.NEW_INPUT,
                        "fresh negative candidate supports positive consequence");
                require(overlay.queryContinuation("?negativeResult(8);") == null,
                        "negative candidate consequences disappear on rollback");
                require(Boolean.FALSE.equals(overlay.probeCandidate(fact, 84, "!~negativeSeed(8);",
                        "?~negativeResult(8);", 10000).value), "negative candidate refutes opposite consequence query");
                if (!negative) require(Boolean.FALSE.equals(overlay.probeCandidate(fact, 85, "!~tail(99);",
                        "?derived(99);", 10000).value), "negative downstream candidate refutes upstream through contraposition");
                if (negative) require(Boolean.FALSE.equals(overlay.probeCandidate(fact, 62, "!tail(99);",
                        signed(negative, "?derived(99);"), 10000).value),
                        "opposite downstream candidate refutes the signed query rather than supporting it");
                if (original == 1) require(overlay.queryDeniedCount() > 0, "native recursive query pair veto exercised");
                count(overlay, root, original - 1, false);
                boolean queryRefused = false;
                try { overlay.query(signed(negative, "?derived(1);")); } catch (IllegalArgumentException expected) { queryRefused = true; }
                require(queryRefused, "legacy query entry point remains unavailable in continuation mode");
                require(java.util.Objects.equals(overlay.queryContinuation(signed(!negative, "?derived(1);")),
                        original > 1 ? Boolean.FALSE : null), "negative query distinguishes refutation from missing proof");
                require(overlay.queryContinuation(signed(!negative, "?derived(99);")) == null, "absent negative query remains unknown");
                require(Boolean.TRUE.equals(overlay.accept(fact, 21, DmzReplayProvenance.Authority.EXTERNAL, "!source(2);")), "native other substitution continues");
                require(overlay.inventoryCheckCount() > 0, "real terminal resolution consults current inventory");
                require(overlay.hasLiveRule(stored.nativeRule) == (original > 1), "blocked old ground cannot rematerialize through old chain");
                if (original == 1) require(overlay.deniedCount() > 0, "native generated-support veto exercised");
                count(overlay, root, original - 1, true);
                TerminalSupportCapture.Materialization fresh = stored(capture, "derived", root);
                require(overlay.hasLiveRule(fresh.nativeRule), "native fresh derived result stored");
                count(overlay, fresh.causes.nodes.get(fresh.causes.root).ground,
                        (alternative ? 2 : 1) * (duplicate ? 2 : 1), true);
                require(hypotheses.equals(unknown.hypotheses.toString()), "later inputs cannot change detached hypotheses");
                require(Boolean.TRUE.equals(overlay.queryContinuation(signed(negative, "?derived(2);"))), "pending fresh substitution query succeeds");
                DmzStoredRetractionTransaction.QueryResult known = overlay.queryContinuationResult(
                        signed(negative, "?derived(2);"), 10000);
                require(Boolean.TRUE.equals(known.value) && known.hypotheses.isEmpty(),
                        "known query does not reuse earlier unknown hypotheses");
                require(java.util.Objects.equals(overlay.queryContinuation(signed(negative, "?derived(1);")), original > 1 ? Boolean.TRUE : null),
                        "pending unrelated fact cannot authorize blocked ground");
                require(Boolean.TRUE.equals(overlay.accept(fact, 22, DmzReplayProvenance.Authority.EXTERNAL, "!~negativeIndependent(1);")), "new negative independent native branch input accepted");
                require(overlay.hasLiveGround(root) && overlay.hasLiveGround(tailGround),
                        "new independent support restores native result and downstream continuation");
                if (original == 1) require(overlay.cacheAdmissionCount() > 0,
                        "current allowed proof readmits the retracted native cache");
                count(overlay, root, original - 1 + (duplicate ? 2 : 1), true);
                count(overlay, tailGround, original - 1 + (duplicate ? 2 : 1), true);
                require(Boolean.TRUE.equals(overlay.queryContinuation(signed(negative, "?derived(1);")))
                        && Boolean.TRUE.equals(overlay.queryContinuation(signed(negative, "?tail(1);"))),
                        "pending independent route authorizes root and downstream queries");
                require(Boolean.FALSE.equals(overlay.queryContinuation(signed(!negative, "?derived(1);")))
                        && Boolean.FALSE.equals(overlay.queryContinuation(signed(!negative, "?tail(1);"))),
                        "independent route refutes opposite signed root and downstream queries");
                count(overlay, root, original - 1 + (duplicate ? 2 : 1), true);
                require(Boolean.TRUE.equals(overlay.accept(fact, 26, DmzReplayProvenance.Authority.EXTERNAL, "!middle(1);")),
                        "explicit primary alias of cached middle accepted");
                count(overlay, root, original - 1 + 2 * (duplicate ? 2 : 1), true);
                int receipts = 0;
                for (TerminalSupportCapture.ProvisionalApplication application : capture.provisionalSourceSnapshot())
                    if (application.application.ground.equivalent(root) && !application.noGoods.isEmpty()) ++receipts;
                require(receipts > 0, "new native application associations freeze the recursive no-good");
                require(state.equals(DmzObservationStateFingerprint.capture(q)), "continuation remains isolated from Q");
                require(Boolean.TRUE.equals(overlay.accept(fact, 100, DmzReplayProvenance.Authority.EXTERNAL,
                        "!~negativeSeed(9);")), "direct negative input accepted");
                require(Boolean.TRUE.equals(overlay.queryContinuation("?negativeResult(9);"))
                        && Boolean.FALSE.equals(overlay.queryContinuation("?~negativeResult(9);")),
                        "direct negative input supports and refutes signed consequence queries");
                require(Boolean.TRUE.equals(overlay.accept(fact, 101, DmzReplayProvenance.Authority.EXTERNAL,
                        "!independent(1);")), "additional positive independent support accepted");
                count(overlay, root, original - 1 + 3 * (duplicate ? 2 : 1), true);
                require(overlay.hasLiveGround(root) && overlay.hasLiveGround(tailGround),
                        "signed independent supports retain native root and downstream cache");
                require(overlay.classifyInput("!source(1);", 10000) == DmzStoredRetractionTransaction.InputKind.EXISTING_PRIMARY
                        && overlay.classifyInput("!~negativePrimary(7);", 10000) == DmzStoredRetractionTransaction.InputKind.EXISTING_PRIMARY,
                        "classification distinguishes signed primary repeats");
                require(overlay.classifyInput("!~source(1);", 10000) == DmzStoredRetractionTransaction.InputKind.CONFLICT
                        && overlay.classifyInput("!negativePrimary(7);", 10000) == DmzStoredRetractionTransaction.InputKind.CONFLICT,
                        "classification identifies opposite signed primary proofs");
                require(overlay.classifyInput("!" + (negative ? "~" : "") + "derived(1);", 10000)
                        == DmzStoredRetractionTransaction.InputKind.SUPPORTED_DERIVED,
                        "generated supported target is distinct from primary repeat");
                require(overlay.classifyInput("!" + (negative ? "" : "~") + "derived(1);", 10000)
                        == DmzStoredRetractionTransaction.InputKind.CONFLICT,
                        "classification identifies opposite derived proof");
                require(overlay.classifyInput("!source(555);", 10000) == DmzStoredRetractionTransaction.InputKind.FRESH,
                        "absent ground classification is fresh");
                boolean classificationBounded = false;
                try { overlay.classifyInput("!source(555);", 1); }
                catch (IllegalStateException expected) { classificationBounded = expected.getMessage().contains("Classification inventory unavailable"); }
                require(classificationBounded, "classification fails closed on unavailable bounded inventory");
                require(overlay.classifyAuthorityInput("!source(1);", 10000) == DmzStoredRetractionTransaction.InputKind.EXISTING_PRIMARY
                        && overlay.classifyAuthorityInput("!~negativePrimary(7);", 10000) == DmzStoredRetractionTransaction.InputKind.EXISTING_PRIMARY,
                        "authority identifies both settled primary signs");
                require(overlay.classifyAuthorityInput("!~negativeSeed(9);", 10000) == DmzStoredRetractionTransaction.InputKind.FRESH,
                        "pending branch input is absent from Q authority");
                DmzStoredRetractionTransaction.CandidateAssessment branchConflict = overlay.assessCandidate("!negativeSeed(9);", 10000);
                require(branchConflict.authority == DmzStoredRetractionTransaction.InputKind.FRESH
                        && branchConflict.branch == DmzStoredRetractionTransaction.InputKind.CONFLICT
                        && branchConflict.disposition == DmzStoredRetractionTransaction.CandidateDisposition.NEEDS_BRANCHING,
                        "branch-only conflict is distinct from Q rejection");
                require(overlay.assessCandidate("!source(1);", 10000).disposition
                        == DmzStoredRetractionTransaction.CandidateDisposition.REUSE_PRIMARY,
                        "combined primary repeat recommends no new source import");
                require(overlay.assessCandidate("!source(555);", 10000).disposition
                        == DmzStoredRetractionTransaction.CandidateDisposition.PROBE_NEW_INPUT,
                        "combined fresh candidate recommends isolated probe only");
                boolean assessmentBounded = false;
                try { overlay.assessCandidate("!source(555);", 1); }
                catch (IllegalStateException expected) { assessmentBounded = expected.getMessage().contains("Classification inventory unavailable"); }
                require(assessmentBounded, "bounded combined assessment refuses unavailable evidence");
                DmzReplayProvenance.Settlement beforeValidation = journal.settlementSnapshot();
                invalidEvaluation(overlay, null, 116, "!~source(1);", "?source(1);", 10000, 10000);
                invalidEvaluation(overlay, fact, -1, "!~source(1);", "?source(1);", 10000, 10000);
                invalidEvaluation(overlay, fact, 116, "!~source(1);", "!source(1);", 10000, 10000);
                invalidEvaluation(overlay, fact, 116, "!source([1]);", "?source(1);", 10000, 10000);
                invalidEvaluation(overlay, fact, 116, "!~source(1);", "?source(1);", 0, 10000);
                invalidEvaluation(overlay, fact, 116, "!~source(1);", "?source(1);", 10000, 0);
                invalidEvaluation(overlay, fact, 116, "!~source(1);", "?source(1);", 10001, 10000);
                invalidEvaluation(overlay, fact, 116, "!~source(1);", "?source(1);", 10000, 10001);
                boolean evaluationBounded = false;
                try { overlay.evaluateCandidate(fact, 116, "!source(560);", "?source(560);", 1, 10000); }
                catch (IllegalStateException expected) { evaluationBounded = expected.getMessage().contains("Classification inventory unavailable"); }
                require(evaluationBounded, "evaluation evidence budget fails at read-only preflight");
                require(journal.settlementSnapshot().pending == beforeValidation.pending
                        && journal.settlementSnapshot().discarded == beforeValidation.discarded,
                        "invalid and evidence-limited evaluation imports no source or transaction");
                DmzReplayProvenance.Settlement beforeDeferred = journal.settlementSnapshot();
                DmzStoredRetractionTransaction.CandidateEvaluation deferred = overlay.evaluateCandidate(fact, 111,
                        "!negativeSeed(9);", "?negativeResult(9);", 10000, 10000);
                require(deferred.status == DmzStoredRetractionTransaction.CandidateStatus.BRANCHING_REQUIRED && deferred.query == null,
                        "branch conflict is deferred without native candidate admission");
                DmzStoredRetractionTransaction.CandidateEvaluation reusable = overlay.evaluateCandidate(fact, 112,
                        "!source(1);", signed(negative, "?derived(1);"), 10000, 10000);
                require(reusable.status == DmzStoredRetractionTransaction.CandidateStatus.REUSED_PRIMARY
                        && reusable.query.candidateMode == DmzStoredRetractionTransaction.QueryResult.CandidateMode.EXISTING_PRIMARY
                        && Boolean.TRUE.equals(reusable.query.value), "existing primary queried after recoverable rejection");
                require(journal.settlementSnapshot().pending == beforeDeferred.pending
                        && journal.settlementSnapshot().discarded == beforeDeferred.discarded,
                        "deferred candidate and primary reuse import no proposed source");
                DmzStoredRetractionTransaction.CandidateEvaluation negativeReusable = overlay.evaluateCandidate(
                        new IContextResults.Revision(fact.getContextId(), 2), 117,
                        "!~negativePrimary(7);", "?~negativePrimary(7);", 10000, 10000);
                require(negativeReusable.status == DmzStoredRetractionTransaction.CandidateStatus.REUSED_PRIMARY
                        && negativeReusable.query.candidateMode == DmzStoredRetractionTransaction.QueryResult.CandidateMode.EXISTING_PRIMARY
                        && Boolean.TRUE.equals(negativeReusable.query.value),
                        "negative primary reuse ignores unimported proposed source revision");
                require(journal.settlementSnapshot().pending == beforeDeferred.pending
                        && journal.settlementSnapshot().discarded == beforeDeferred.discarded,
                        "negative repeat imports neither new revision nor alias");
                DmzStoredRetractionTransaction.CandidateEvaluation evaluatedFresh = overlay.evaluateCandidate(fact, 113,
                        "!source(556);", signed(negative, "?derived(556);"), 10000, 10000);
                require(evaluatedFresh.status == DmzStoredRetractionTransaction.CandidateStatus.PROBED_NEW_INPUT
                        && evaluatedFresh.query.candidateMode == DmzStoredRetractionTransaction.QueryResult.CandidateMode.NEW_INPUT
                        && Boolean.TRUE.equals(evaluatedFresh.query.value), "valid fresh candidate still probes after rejection");
                require(overlay.queryContinuation(signed(negative, "?derived(556);")) == null,
                        "evaluated fresh candidate fully rolls back");
                DmzStoredRetractionTransaction.CandidateEvaluation negativeEvaluated = overlay.evaluateCandidate(fact, 118,
                        "!~negativeSeed(557);", "?negativeResult(557);", 10000, 10000);
                require(negativeEvaluated.status == DmzStoredRetractionTransaction.CandidateStatus.PROBED_NEW_INPUT
                        && Boolean.TRUE.equals(negativeEvaluated.query.value), "fresh signed evaluated input proves consequence");
                require(overlay.queryContinuation("?negativeResult(557);") == null,
                        "evaluated negative consequence disappears after rollback");
                DmzStoredRetractionTransaction.CandidateEvaluation evaluatedUnknown = overlay.evaluateCandidate(fact, 119,
                        "!~negativeSeed(558);", signed(negative, "?derived(559);"), 10000, 10000);
                require(evaluatedUnknown.status == DmzStoredRetractionTransaction.CandidateStatus.PROBED_NEW_INPUT
                        && evaluatedUnknown.query != null && evaluatedUnknown.query.value == null
                        && !evaluatedUnknown.query.hypotheses.isEmpty(),
                        "executed unknown query snapshot differs from absence of rejected query");
                Boolean positiveRepeat = overlay.accept(fact, 102, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
                Boolean negativeRepeat = overlay.accept(fact, 103, DmzReplayProvenance.Authority.EXTERNAL, "!~negativePrimary(7);");
                require(positiveRepeat == null && negativeRepeat == null,
                        "direct primary duplicates return null: " + positiveRepeat + "/" + negativeRepeat);
                count(overlay, root, original - 1 + 3 * (duplicate ? 2 : 1), true);
                Boolean positiveConflict = overlay.accept(fact, 104, DmzReplayProvenance.Authority.EXTERNAL, "!~source(1);");
                Boolean negativeConflict = overlay.accept(fact, 105, DmzReplayProvenance.Authority.EXTERNAL, "!negativePrimary(7);");
                Boolean generatedConflict = overlay.accept(fact, 106, DmzReplayProvenance.Authority.EXTERNAL,
                        "!" + (negative ? "" : "~") + "derived(1);");
                require(positiveConflict == null && negativeConflict == null
                        && generatedConflict == null, "direct conflicts return null");
                count(overlay, root, original - 1 + 3 * (duplicate ? 2 : 1), true);
                require(Boolean.TRUE.equals(overlay.queryContinuation(signed(negative, "?derived(1);")))
                        && Boolean.FALSE.equals(overlay.queryContinuation(signed(!negative, "?derived(1);"))),
                        "direct rejection keeps branch usable and original sign authoritative");
                require(Boolean.TRUE.equals(overlay.accept(fact, 107, DmzReplayProvenance.Authority.EXTERNAL,
                        "!" + (negative ? "~" : "") + "derived(1);")),
                        "same-sign generated input adds explicit primary support");
                count(overlay, root, original - 1 + 3 * (duplicate ? 2 : 1) + 1, true);
                count(overlay, tailGround, original - 1 + 3 * (duplicate ? 2 : 1) + 1, true);
                require(overlay.classifyInput("!" + (negative ? "~" : "") + "derived(1);", 10000)
                        == DmzStoredRetractionTransaction.InputKind.EXISTING_PRIMARY,
                        "classification changes after actual explicit primary admission");
                require(overlay.classifyAuthorityInput("!" + (negative ? "~" : "") + "derived(1);", 10000)
                        == DmzStoredRetractionTransaction.InputKind.SUPPORTED_DERIVED,
                        "new branch primary cannot relabel Q derived proof");
                DmzStoredRetractionTransaction.CandidateAssessment changedAssessment = overlay.assessCandidate(
                        "!" + (negative ? "~" : "") + "derived(1);", 10000);
                require(changedAssessment.branch == DmzStoredRetractionTransaction.InputKind.EXISTING_PRIMARY
                        && changedAssessment.authority == DmzStoredRetractionTransaction.InputKind.SUPPORTED_DERIVED
                        && changedAssessment.disposition == DmzStoredRetractionTransaction.CandidateDisposition.REUSE_PRIMARY,
                        "fresh assessment observes admitted branch primary without relabeling Q");
                require(initialSame.disposition == DmzStoredRetractionTransaction.CandidateDisposition.PROBE_NEW_INPUT,
                        "old detached assessment stays diagnostic rather than refreshing into permission");
                DmzReplayProvenance.Settlement beforeGeneratedReuse = journal.settlementSnapshot();
                DmzStoredRetractionTransaction.CandidateEvaluation generatedReuse = overlay.evaluateCandidate(fact, 114,
                        "!" + (negative ? "~" : "") + "derived(1);", signed(negative, "?tail(1);"), 10000, 10000);
                require(generatedReuse.status == DmzStoredRetractionTransaction.CandidateStatus.REUSED_PRIMARY
                        && Boolean.TRUE.equals(generatedReuse.query.value),
                        "explicit primary over generated cache reuses proof without native repeat admission");
                count(overlay, root, original - 1 + 3 * (duplicate ? 2 : 1) + 1, true);
                require(journal.settlementSnapshot().pending == beforeGeneratedReuse.pending
                        && journal.settlementSnapshot().discarded == beforeGeneratedReuse.discarded,
                        "generated primary reuse imports no alias or input transaction");
                boolean auditRejected = false;
                try {
                    if (failureMode == 7 || failureMode == 8) {
                        String predicate = failureMode == 7 ? "derived" : "tail";
                        require(overlay.hasLiveGround(failureMode == 7 ? root : tailGround),
                                "conflicting derived candidate has live supported target before probe");
                        overlay.probeCandidate(fact, 88 + failureMode, "!" + (negative ? "" : "~") + predicate + "(1);",
                                signed(!negative, "?" + predicate + "(1);"), 10000);
                    }
                    else if (failureMode == 5) overlay.probeCandidate(fact, 86, "!~source(1);", "?~source(1);", 10000);
                    else if (failureMode == 6) overlay.probeCandidate(fact, 87, "!negativePrimary(7);", "?negativePrimary(7);", 10000);
                    else if (failureMode == 1) overlay.queryContinuation(signed(negative, "?derived(1);"), 1);
                    else if (failureMode == 2) overlay.probeCandidate(fact, 70, "!~negativeSeed(66);",
                            signed(negative, "?derived(66);"), 1);
                    else if (failureMode == 4) overlay.evaluateCandidate(fact, 74, "!~negativeSeed(66);",
                            signed(negative, "?derived(66);"), 10000, 1);
                    else if (failureMode == 3) overlay.evaluateCandidate(
                            new IContextResults.Revision(fact.getContextId(), 2), 71, "!~negativeSeed(66);",
                            signed(negative, "?derived(66);"), 10000, 10000);
                    else overlay.accept(new IContextResults.Revision(fact.getContextId(), 2), 31,
                            DmzReplayProvenance.Authority.EXTERNAL, "!~negativeIndependent(3);");
                } catch (IllegalStateException expected) {
                    auditRejected = true;
                    if (failureMode >= 5) require(expected.getMessage().contains("Candidate was not accepted in isolated probe"),
                            "opposite signed live parent fact fails at native acceptance, not duplicate lookup or query audit");
                    if (failureMode == 3) require(expected.getMessage().contains("inventory unavailable"),
                            "evaluated incoherent revision fails after native candidate admission at query audit");
                    if (failureMode == 4) require(expected.getMessage().contains("primary lookup budget exceeded"),
                            "candidate fails at the separately bounded primary lookup");
                    if (failureMode == 1 || failureMode == 2) require(expected.getMessage().contains("inventory unavailable"),
                            "query fails at the bounded inventory audit");
                }
                require(auditRejected, "audit failure closes continuation, mode=" + failureMode);
                boolean failureClosed = false;
                try { overlay.proofs(root, 10000); }
                catch (IllegalStateException expected) { failureClosed = true; }
                require(failureClosed, "failed audit branch cannot expose proofs");
                boolean inputClosed = false;
                try { overlay.accept(fact, 32, DmzReplayProvenance.Authority.EXTERNAL, "!source(4);"); }
                catch (IllegalStateException expected) { inputClosed = true; }
                require(inputClosed, "failed audit branch cannot accept another input");
                require(state.equals(DmzObservationStateFingerprint.capture(q)),
                        "audit failure rolls back all previous successful branch inputs");
                require(hypotheses.equals(unknown.hypotheses.toString()), "detached hypotheses survive branch rollback");
            } finally { overlay.close(); }
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "recursive continuation fully rolls back");
            require(parentHypotheses.equals(hypothesisText(q)), "rollback preserves Q hypothesis store");
            boolean closed = false;
            try { overlay.proofs(root, 10000); } catch (IllegalStateException expected) { closed = true; }
            require(closed, "closed branch proof API rejects reuse");
            boolean closedQuery = false;
            try { overlay.queryContinuation(signed(negative, "?derived(1);")); }
            catch (IllegalStateException expected) { closedQuery = true; }
            require(closedQuery, "closed branch query API rejects reuse");
            boolean closedClassification = false;
            try { overlay.classifyInput("!source(1);", 10000); }
            catch (IllegalStateException expected) { closedClassification = true; }
            require(closedClassification, "closed branch rejects classification reuse");
            boolean closedAuthority = false;
            try { overlay.classifyAuthorityInput("!source(1);", 10000); }
            catch (IllegalStateException expected) { closedAuthority = true; }
            require(closedAuthority, "closed branch rejects authority classification");
            boolean closedAssessment = false;
            try { overlay.assessCandidate("!source(1);", 10000); }
            catch (IllegalStateException expected) { closedAssessment = true; }
            require(closedAssessment, "closed branch rejects combined assessment");
            boolean closedEvaluation = false;
            try { overlay.evaluateCandidate(fact, 115, "!source(1);", "?source(1);", 10000, 10000); }
            catch (IllegalStateException expected) { closedEvaluation = true; }
            require(closedEvaluation, "closed branch rejects evaluated candidate");
            boolean closedProbe = false;
            try { overlay.probeCandidate(fact, 72, "!source(5);", signed(negative, "?derived(5);"), 10000); }
            catch (IllegalStateException expected) { closedProbe = true; }
            require(closedProbe, "closed branch candidate probe rejects reuse");
            require(Boolean.TRUE.equals(q.query(signed(negative, "?derived(1);"), null, false)), "parent query unaffected after scope closes");
            if (failureMode >= 5) {
                require(Boolean.TRUE.equals(q.query("?source(1);", null, false))
                        && Boolean.FALSE.equals(q.query("?~source(1);", null, false)),
                        "positive Q primary keeps its authority after rejected candidate");
                require(Boolean.TRUE.equals(q.query("?~negativePrimary(7);", null, false))
                        && Boolean.FALSE.equals(q.query("?negativePrimary(7);", null, false)),
                        "negative Q primary keeps its authority after rejected candidate");
                require(Boolean.TRUE.equals(q.query(signed(negative, "?tail(1);"), null, false))
                        && Boolean.FALSE.equals(q.query(signed(!negative, "?derived(1);"), null, false))
                        && Boolean.FALSE.equals(q.query(signed(!negative, "?tail(1);"), null, false)),
                        "Q derived root and downstream signs survive rejected candidate");
            }
        }
    }
    private static void invalidEvaluation(DmzStoredRetractionTransaction overlay, IContextResults.Revision source,
            long label, String candidate, String query, int auditBudget, int lookupBudget) throws Exception {
        boolean invalid = false;
        try { overlay.evaluateCandidate(source, label, candidate, query, auditBudget, lookupBudget); }
        catch (IllegalArgumentException expected) { invalid = true; }
        require(invalid, "invalid evaluation parameters fail before recoverable classification");
    }
    private static void authorityStaleness(boolean sourceOnly) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, source, 1, "!@x source(x) -> derived(x);");
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            require(Boolean.TRUE.equals(guard.accept(source, 2, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "stale fixture input");
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored(capture, "derived", null), 10000);
            try (DmzStoredRetractionTransaction overlay = DmzStoredRetractionTransaction.beginContinuation(guard, proof, proof.witnesses.get(0), q, 10000)) {
                require(overlay.classifyAuthorityInput("!~derived(1);", 10000) == DmzStoredRetractionTransaction.InputKind.CONFLICT,
                        "authority valid before external change");
                String before = DmzObservationStateFingerprint.capture(q);
                if (sourceOnly) DmzReplayProvenance.replayRule(q, source, 3, DmzReplayProvenance.Authority.EXTERNAL,
                        "!@x source(x) -> derived(x);");
                else accept(q, source, 4, "!source(2);");
                if (sourceOnly) require(before.equals(DmzObservationStateFingerprint.capture(q)),
                        "source-only authority mutation leaves native fingerprint unchanged");
                String changed = DmzObservationStateFingerprint.capture(q);
                boolean stale = false;
                try { overlay.classifyAuthorityInput("!~derived(1);", 10000); }
                catch (IllegalStateException expected) { stale = expected.getMessage().contains("Pinned Q authority boundary changed"); }
                require(stale, "changed authority rejected, sourceOnly=" + sourceOnly);
                boolean assessmentStale = false;
                try { overlay.assessCandidate("!~derived(1);", 10000); }
                catch (IllegalStateException expected) { assessmentStale = expected.getMessage().contains("Pinned Q authority boundary changed"); }
                require(assessmentStale, "combined assessment rejects stale Q authority");
                boolean evaluationStale = false;
                try { overlay.evaluateCandidate(source, 8, "!~derived(1);", "?derived(1);", 10000, 10000); }
                catch (IllegalStateException expected) { evaluationStale = expected.getMessage().contains("Pinned Q authority boundary changed"); }
                require(evaluationStale, "candidate evaluation refuses stale Q authority without classifying rejection");
                require(changed.equals(DmzObservationStateFingerprint.capture(q)), "classification refusal preserves changed Q");
            }
        }
    }
    private static TerminalSupportCapture.Materialization stored(TerminalSupportCapture capture, String predicate,
            TerminalSupportCapture.Ground differentFrom) {
        TerminalSupportCapture.Materialization result = null;
        for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot()) {
            TerminalSupportCapture.Ground ground = observation.materialization.causes.nodes.get(observation.materialization.causes.root).ground;
            if (ground.predicate.equals(predicate) && (differentFrom == null || !ground.equivalent(differentFrom))) result = observation.materialization;
        }
        if (result == null) throw new AssertionError("Missing native result " + predicate);
        return result;
    }
    private static String signed(boolean negative, String query) {
        return negative ? "?~" + query.substring(1) : query;
    }
    private static String hypothesisText(Mind mind) throws Exception {
        java.util.List<String> result = new java.util.ArrayList<String>();
        for (org.kanger.interfaces.IHypothesis hypothesis : mind.getHypothesis())
            result.add(((org.kanger.primitives.Hypothesis) hypothesis).toString(mind));
        return result.toString();
    }
    private static void count(DmzStoredRetractionTransaction overlay, TerminalSupportCapture.Ground ground,
            int expected, boolean provisional) throws Exception {
        DmzCurrentUnaryProofInventory.Proofs proofs = overlay.proofs(ground, 10000);
        require(!proofs.truncated && proofs.witnesses.size() == expected && proofs.provisional == provisional,
                "branch proof count " + expected + " and explicit provisional state");
    }
    private static void accept(Mind q, IContextResults.Revision source, long id, String statement) throws Exception {
        require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, id,
                DmzReplayProvenance.Authority.EXTERNAL, statement)), "native production");
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
