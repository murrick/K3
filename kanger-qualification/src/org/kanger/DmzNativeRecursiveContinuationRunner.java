/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Native positive-input continuation and isolated signed recursive query qualification. */
public final class DmzNativeRecursiveContinuationRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-native-recursive-").toString());
        for (boolean alternative : new boolean[] {false, true})
            for (boolean duplicate : new boolean[] {false, true})
                for (boolean primaryDuplicate : new boolean[] {false, true})
                    for (boolean negative : new boolean[] {false, true})
                        for (int failureMode : new int[] {0, 1, 2, 3, 4, 5, 6})
                            run(alternative, duplicate, primaryDuplicate, negative, failureMode);
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
            require(guard.auditRoutes(proof, blocked, q).matched,
                    "pre-branch route audit: " + guard.auditRoutes(proof, blocked, q).gaps);
            DmzStoredRetractionTransaction overlay = DmzStoredRetractionTransaction.beginContinuation(guard, proof, blocked, q, 10000);
            try {
                require(overlay.hasLiveRule(stored.nativeRule) == (original > 1)
                        && overlay.hasLiveRule(tail.nativeRule) == (original > 1), "native last-support retraction or retained alternative");
                count(overlay, root, original - 1, false);
                count(overlay, tailGround, original - 1, false);
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
                boolean negativeRefused = false;
                try { overlay.accept(fact, 30, DmzReplayProvenance.Authority.EXTERNAL, "!~derived(1);"); }
                catch (IllegalArgumentException expected) { negativeRefused = true; }
                require(negativeRefused, "negative continuation remains outside qualification");
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
                require(Boolean.TRUE.equals(overlay.accept(fact, 22, DmzReplayProvenance.Authority.EXTERNAL, "!independent(1);")), "new independent native branch input accepted");
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
                boolean auditRejected = false;
                try {
                    if (failureMode == 5) overlay.probeCandidate(fact, 86, "!~source(1);", "?~source(1);", 10000);
                    else if (failureMode == 6) overlay.probeCandidate(fact, 87, "!negativePrimary(7);", "?negativePrimary(7);", 10000);
                    else if (failureMode == 1) overlay.queryContinuation(signed(negative, "?derived(1);"), 1);
                    else if (failureMode == 2) overlay.probeCandidate(fact, 70, "!~negativeSeed(66);",
                            signed(negative, "?derived(66);"), 1);
                    else if (failureMode == 4) overlay.probeCandidate(fact, 74, "!~negativeSeed(66);",
                            signed(negative, "?derived(66);"), 10000, 1);
                    else if (failureMode == 3) overlay.probeCandidate(
                            new IContextResults.Revision(fact.getContextId(), 2), 71, "!~negativeSeed(66);",
                            signed(negative, "?derived(66);"), 10000);
                    else overlay.accept(new IContextResults.Revision(fact.getContextId(), 2), 31,
                            DmzReplayProvenance.Authority.EXTERNAL, "!independent(3);");
                } catch (IllegalStateException expected) {
                    auditRejected = true;
                    if (failureMode == 5 || failureMode == 6) require(expected.getMessage().contains("Candidate was not accepted in isolated probe"),
                            "opposite signed parent primary fails at native acceptance, not duplicate lookup or query audit");
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
            boolean closedProbe = false;
            try { overlay.probeCandidate(fact, 72, "!source(5);", signed(negative, "?derived(5);"), 10000); }
            catch (IllegalStateException expected) { closedProbe = true; }
            require(closedProbe, "closed branch candidate probe rejects reuse");
            require(Boolean.TRUE.equals(q.query(signed(negative, "?derived(1);"), null, false)), "parent query unaffected after scope closes");
            if (failureMode == 5 || failureMode == 6) {
                require(Boolean.TRUE.equals(q.query("?source(1);", null, false))
                        && Boolean.FALSE.equals(q.query("?~source(1);", null, false)),
                        "positive Q primary keeps its authority after rejected candidate");
                require(Boolean.TRUE.equals(q.query("?~negativePrimary(7);", null, false))
                        && Boolean.FALSE.equals(q.query("?negativePrimary(7);", null, false)),
                        "negative Q primary keeps its authority after rejected candidate");
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
