/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

public final class DmzStoredRestrictionProbeRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-restrict-probe-").toString());
        run(false, false, false); run(true, false, false); run(false, true, false); run(false, false, true);
        System.out.println("DMZ_STORED_RESTRICTION_PROBE_PASS checks=" + checks);
    }
    private static void run(boolean alternative, boolean authoritative, boolean duplicate) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, rules, 10,
                    authoritative ? DmzReplayProvenance.Authority.TARGET_Q : DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x source(x) -> derived(x);")), "native root production");
            accept(q, rules, 11, "!@x derived(x) -> tail(x);");
            if (alternative) {
                accept(q, rules, 12, "!@x source(x) -> middle(x);");
                accept(q, rules, 13, "!@x middle(x) -> derived(x);");
            }
            if (duplicate) DmzReplayProvenance.replayRule(q, rules, 14,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> derived(x);");
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            require(Boolean.TRUE.equals(guard.accept(fact, 20, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "native input");
            TerminalSupportCapture.Materialization stored = null;
            for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot())
                if (observation.materialization.causes.nodes.get(observation.materialization.causes.root).ground.predicate.equals("derived")) stored = observation.materialization;
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored, 1000);
            DmzProofWitnesses.Witness blocked = null;
            for (DmzProofWitnesses.Witness witness : proof.witnesses) if (witness.source.sourceRule == 10) blocked = witness;
            String state = DmzObservationStateFingerprint.capture(q);
            DmzStoredRestrictionProbe projected = DmzStoredRestrictionProbe.project(guard, proof, blocked, q, 10000);
            if (authoritative) {
                require(!projected.eligible && projected.nodes.isEmpty(), "Q production cannot be selected for exclusion");
                require(state.equals(DmzObservationStateFingerprint.capture(q)), "rejected Q projection leaves native state unchanged");
                return;
            }
            require(projected.eligible && !projected.truncated && !projected.complete, "bounded projection eligible");
            if (duplicate) {
                boolean refused = false;
                try { DmzStoredRetractionTransaction.begin(guard, proof, blocked, q, 10000); }
                catch (IllegalStateException expected) { refused = true; }
                require(refused, "canonical source alternatives cannot be globally vetoed");
                require(state.equals(DmzObservationStateFingerprint.capture(q)), "unsupported source alternative leaves parent unchanged");
                return;
            }
            int checked = 0;
            for (DmzStoredRestrictionProbe.Node node : projected.nodes) {
                String predicate = proof.graph.observed.nodes.get(node.index).ground.predicate;
                if (predicate.equals("derived") || predicate.equals("tail")) {
                    require(node.removed == 1 && node.retracted() == !alternative,
                            "dependent route removed; independent route preserves " + predicate);
                    require(node.retained.size() == (alternative ? 1 : 0), "exact retained route count"); ++checked;
                }
                if (predicate.equals("source")) require(node.removed == 0 && !node.retained.isEmpty(), "primary survives");
            }
            require(checked == 2, "root and downstream continuation checked");
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "projection does not mutate native state");
            DmzStoredRetractionTransaction overlay = DmzStoredRetractionTransaction.begin(guard, proof, blocked, q, 10000);
            try {
                for (org.kanger.interfaces.IRule candidate : q.getRules()) {
                    org.kanger.units.Rule rule = (org.kanger.units.Rule) candidate;
                    if (candidate.isDeleted(q)) continue;
                    if (q.getRules().isGenerated(rule)) {
                        String predicate = rule.getDomain().getPredicate().getName(q);
                        if (predicate.equals("derived") || predicate.equals("tail"))
                            require(overlay.hasLiveRule(rule.getId()) == alternative,
                                    "native overlay retracts last-dependent atom and preserves independent " + predicate);
                    } else require(overlay.hasLiveRule(rule.getId()), "native primary rule preserved");
                }
                require(state.equals(DmzObservationStateFingerprint.capture(q)), "native child tombstones leave parent unchanged");
                require(Boolean.TRUE.equals(overlay.accept(fact, 21, DmzReplayProvenance.Authority.EXTERNAL,
                        "!source(2);")), "native continuation accepts another substitution");
                require(overlay.hasLiveRule(stored.nativeRule) == alternative,
                        "continued acceptance cannot restore blocked substitution");
                require(overlay.deniedCount() > 0, "terminal veto exercised by real native continuation");
                int fresh = 0;
                for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot()) {
                    TerminalSupportCapture.Materialization materialization = observation.materialization;
                    TerminalSupportCapture.Ground ground = materialization.causes.nodes.get(materialization.causes.root).ground;
                    if ((ground.predicate.equals("derived") || ground.predicate.equals("tail"))
                            && !ground.arguments.get(0).semanticallyEquals(proof.graph.observed.nodes.get(proof.root).ground.arguments.get(0))
                            && materialization.operation != stored.operation && overlay.hasLiveRule(materialization.nativeRule)) ++fresh;
                }
                require(fresh >= 2, "different substitution still produces result and downstream continuation");
                boolean unsupported = false;
                try { overlay.accept(fact, 22, DmzReplayProvenance.Authority.EXTERNAL, "?derived(1);"); }
                catch (IllegalArgumentException expected) { unsupported = true; }
                require(unsupported, "hypothesis/query path cannot bypass qualification boundary");
            } finally { overlay.close(); }
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "rollback restores unchanged parent");
            boolean closed = false;
            try { overlay.hasLiveRule(stored.nativeRule); } catch (IllegalStateException expected) { closed = true; }
            require(closed, "closed native overlay unreadable");
            DmzStoredRestrictionProbe small = DmzStoredRestrictionProbe.project(guard, proof, blocked, q, 1);
            require(!small.eligible && small.truncated && small.nodes.isEmpty(), "budget exhaustion exposes no usable partial branch");
            DmzStoredProof other = DmzStoredProof.build(capture, journal, stored, 1000);
            require(!DmzStoredRestrictionProbe.project(guard, proof, other.witnesses.get(0), q, 10000).eligible, "foreign witness rejected");
            q.query("!anchor(2);", null, false);
            require(!DmzStoredRestrictionProbe.project(guard, proof, blocked, q, 10000).eligible, "stale boundary cannot project");
        }
    }
    private static void accept(Mind q, IContextResults.Revision source, long id, String statement) throws Exception {
        require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, id, DmzReplayProvenance.Authority.EXTERNAL, statement)), "native production");
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
