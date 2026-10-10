/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Native positive unary continuation and isolated recursive query qualification. */
public final class DmzNativeRecursiveContinuationRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-native-recursive-").toString());
        for (boolean alternative : new boolean[] {false, true})
            for (boolean duplicate : new boolean[] {false, true})
                for (boolean primaryDuplicate : new boolean[] {false, true}) run(alternative, duplicate, primaryDuplicate);
        System.out.println("DMZ_NATIVE_RECURSIVE_CONTINUATION_PASS checks=" + checks);
    }
    private static void run(boolean alternative, boolean duplicate, boolean primaryDuplicate) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, rules, 10, "!@x source(x) -> middle(x);");
            if (alternative) {
                accept(q, rules, 11, "!@x source(x) -> other(x);");
                accept(q, rules, 12, "!@x other(x) -> middle(x);");
            }
            accept(q, rules, 13, "!@x middle(x) -> derived(x);");
            accept(q, rules, 14, "!@x derived(x) -> tail(x);");
            accept(q, rules, 16, "!@x independent(x) -> middle(x);");
            if (duplicate) DmzReplayProvenance.replayRule(q, rules, 15,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x middle(x) -> derived(x);");
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
            String state = DmzObservationStateFingerprint.capture(q);
            DmzStoredRetractionTransaction overlay = DmzStoredRetractionTransaction.beginContinuation(guard, proof, blocked, q, 10000);
            try {
                require(overlay.hasLiveRule(stored.nativeRule) == (original > 1)
                        && overlay.hasLiveRule(tail.nativeRule) == (original > 1), "native last-support retraction or retained alternative");
                count(overlay, root, original - 1, false);
                count(overlay, tailGround, original - 1, false);
                require(Boolean.TRUE.equals(overlay.queryContinuation("?derived(1);")) == (original > 1),
                        "recursive native query respects exact chain restriction");
                require(Boolean.TRUE.equals(overlay.queryContinuation("?tail(1);")) == (original > 1),
                        "downstream native query respects exact chain restriction");
                require(Boolean.TRUE.equals(overlay.queryContinuation("?derived(1);")) == (original > 1),
                        "repeated query uses a fresh audit");
                require(!Boolean.TRUE.equals(overlay.queryContinuation("?derived(99);")), "absent substitution remains unknown");
                if (original == 1) require(overlay.queryDeniedCount() > 0, "native recursive query pair veto exercised");
                count(overlay, root, original - 1, false);
                boolean queryRefused = false;
                try { overlay.query("?derived(1);"); } catch (IllegalArgumentException expected) { queryRefused = true; }
                require(queryRefused, "legacy query entry point remains unavailable in continuation mode");
                boolean negativeQueryRefused = false;
                try { overlay.queryContinuation("?~derived(1);"); }
                catch (IllegalArgumentException expected) { negativeQueryRefused = true; }
                require(negativeQueryRefused, "negative recursive queries remain outside qualification");
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
                require(Boolean.TRUE.equals(overlay.queryContinuation("?derived(2);")), "pending fresh substitution query succeeds");
                require(Boolean.TRUE.equals(overlay.queryContinuation("?derived(1);")) == (original > 1),
                        "pending unrelated fact cannot authorize blocked ground");
                require(Boolean.TRUE.equals(overlay.accept(fact, 22, DmzReplayProvenance.Authority.EXTERNAL, "!independent(1);")), "new independent native branch input accepted");
                require(overlay.hasLiveGround(root) && overlay.hasLiveGround(tailGround),
                        "new independent support restores native result and downstream continuation");
                if (original == 1) require(overlay.cacheAdmissionCount() > 0,
                        "current allowed proof readmits the retracted native cache");
                count(overlay, root, original - 1 + (duplicate ? 2 : 1), true);
                count(overlay, tailGround, original - 1 + (duplicate ? 2 : 1), true);
                require(Boolean.TRUE.equals(overlay.queryContinuation("?derived(1);"))
                        && Boolean.TRUE.equals(overlay.queryContinuation("?tail(1);")),
                        "pending independent route authorizes root and downstream queries");
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
                    overlay.accept(new IContextResults.Revision(fact.getContextId(), 2), 31,
                            DmzReplayProvenance.Authority.EXTERNAL, "!independent(3);");
                } catch (IllegalStateException expected) { auditRejected = true; }
                require(auditRejected, "inconsistent source revision closes continuation");
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
            } finally { overlay.close(); }
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "recursive continuation fully rolls back");
            boolean closed = false;
            try { overlay.proofs(root, 10000); } catch (IllegalStateException expected) { closed = true; }
            require(closed, "closed branch proof API rejects reuse");
            boolean closedQuery = false;
            try { overlay.queryContinuation("?derived(1);"); }
            catch (IllegalStateException expected) { closedQuery = true; }
            require(closedQuery, "closed branch query API rejects reuse");
            require(Boolean.TRUE.equals(q.query("?derived(1);", null, false)), "parent query unaffected after scope closes");
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
