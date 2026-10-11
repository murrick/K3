/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.IRule;
import org.kanger.udf.UDF;

public final class DmzCurrentUnaryProofInventoryRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-current-unit-").toString());
        for (boolean alternative : new boolean[] {false, true})
            for (boolean duplicate : new boolean[] {false, true})
                for (boolean primaryDuplicate : new boolean[] {false, true}) run(alternative, duplicate, primaryDuplicate);
        unsupported();
        signedAndCycle();
        inconsistentPins();
        System.out.println("DMZ_CURRENT_UNARY_PROOF_INVENTORY_PASS checks=" + checks);
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
                    DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "native aliased input");
            TerminalSupportCapture.Materialization stored = null;
            for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot())
                if (observation.materialization.causes.nodes.get(observation.materialization.causes.root).ground.predicate.equals("derived"))
                    stored = observation.materialization;
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored, 10000);
            DmzProofWitnesses.Witness blocked = null;
            for (DmzProofWitnesses.Witness witness : proof.witnesses)
                if (witness.source.sourceRule == 13 && witness.premises.get(0).source.sourceRule == 10
                        && witness.premises.get(0).premises.get(0).source.sourceRule == 20) blocked = witness;
            require(blocked != null && guard.auditRoutes(proof, blocked, q).matched, "selected original chain is audited");
            DmzProofWitnesses.NoGood noGood = DmzProofWitnesses.NoGood.from(blocked);
            TerminalSupportCapture.Ground root = proof.graph.observed.nodes.get(proof.root).ground;
            TerminalSupportCapture.Ground primary = blocked.graph.observed.nodes.get(blocked.premises.get(0).premises.get(0).node).ground;
            int original = (alternative ? 2 : 1) * (duplicate ? 2 : 1) * (primaryDuplicate ? 2 : 1);
            String state = DmzObservationStateFingerprint.capture(q);
            DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(q, 10000);
            require(inventory.eligible && !inventory.provisional && !inventory.complete && inventory.isCurrent(q), "current settled inventory");
            count(inventory, root, null, original);
            count(inventory, root, noGood, original - 1);
            count(inventory, primary, noGood, primaryDuplicate ? 2 : 1);
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "inventory is read-only");
            DmzCurrentUnaryProofInventory small = DmzCurrentUnaryProofInventory.capture(q, 1);
            require(!small.eligible && small.truncated && small.graph == null, "truncated inventory exposes no graph");
            require(inventory.proofs(root, Collections.singletonList(noGood), 1).truncated, "proof enumeration budget remains explicit");
            DmzCurrentUnaryProofInventory provisional;
            TechnicalMindTransaction child = TechnicalMindTransaction.beginIsolated(q);
            try {
                require(!inventory.isCurrent(child.mind()), "inventory bound to exact Mind");
                for (IRule rule : q.getRules()) if (!rule.isDeleted(q) && q.getRules().isGenerated(rule))
                    ((org.kanger.units.Rule) rule).setDeleted(true, child.mind());
                DmzCurrentUnaryProofInventory uncached = DmzCurrentUnaryProofInventory.capture(child.mind(), 10000);
                require(uncached.eligible, "child inventory works without generated cache atoms");
                count(uncached, root, noGood, original - 1);
                require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(child.mind(), fact, 22,
                        DmzReplayProvenance.Authority.EXTERNAL, "!independent(1);")), "native new branch support");
                require(!uncached.isCurrent(child.mind()), "branch acceptance invalidates old inventory");
                DmzCurrentUnaryProofInventory settledOnly = DmzCurrentUnaryProofInventory.capture(child.mind(), 10000);
                require(!settledOnly.eligible && settledOnly.provisional && settledOnly.graph == null, "pending source cannot masquerade as settled proof");
                provisional = DmzCurrentUnaryProofInventory.capture(child.mind(), 10000, true);
                require(provisional.eligible && provisional.provisional && provisional.isCurrent(child.mind()), "explicit provisional inventory");
                count(provisional, root, noGood, original - 1 + (duplicate ? 2 : 1));
                require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(child.mind(), fact, 26,
                        DmzReplayProvenance.Authority.EXTERNAL, "!middle(1);")), "explicit primary acceptance of cached derived atom");
                require(!provisional.isCurrent(child.mind()), "cached-atom source alias invalidates inventory");
                provisional = DmzCurrentUnaryProofInventory.capture(child.mind(), 10000, true);
                require(provisional.eligible && provisional.provisional, "source-backed cache atom becomes a primary seed");
                count(provisional, root, noGood, original - 1 + 2 * (duplicate ? 2 : 1));
                require(inventory.isCurrent(q), "child source and cache changes do not invalidate parent inventory");
            } finally { child.close(); }
            require(!provisional.isCurrent(child.mind()), "rollback invalidates provisional source boundary");
            require(inventory.isCurrent(q) && state.equals(DmzObservationStateFingerprint.capture(q)), "parent snapshot survives child rollback");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, fact, 23,
                    DmzReplayProvenance.Authority.EXTERNAL, "!independent(1);")), "new parent support accepted");
            require(!inventory.isCurrent(q), "new parent input invalidates inventory");
            boolean stale = false;
            try { inventory.proofs(root, Collections.singletonList(noGood), 10000); }
            catch (IllegalStateException expected) { stale = true; }
            require(stale, "stale proof lookup refused");
            DmzCurrentUnaryProofInventory renewed = DmzCurrentUnaryProofInventory.capture(q, 10000);
            require(renewed.eligible && !renewed.provisional, "renewed settled inventory");
            count(renewed, root, noGood, original - 1 + (duplicate ? 2 : 1));
        }
    }
    private static void unsupported() throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            accept(q, source, 1, "!p(1);");
            accept(q, source, 2, "!@x p(x) -> r(x);");
            DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(q, 10000);
            require(inventory.eligible, "baseline before unsupported input");
            require(Boolean.TRUE.equals(q.query("!unlabelled(9);", null, false)), "unlabelled native input accepted");
            DmzCurrentUnaryProofInventory missing = DmzCurrentUnaryProofInventory.capture(q, 10000);
            require(!missing.eligible && missing.graph == null && missing.gaps.contains("missing-live-source"), "missing labels fail closed");
            accept(q, source, 3, "!binary(1,2);");
            DmzCurrentUnaryProofInventory wide = DmzCurrentUnaryProofInventory.capture(q, 10000);
            require(!wide.eligible && wide.graph == null && wide.gaps.contains("unsupported-primary-atom"), "unsupported arity cannot produce a usable graph");
        }
    }
    private static void count(DmzCurrentUnaryProofInventory inventory, TerminalSupportCapture.Ground ground,
            DmzProofWitnesses.NoGood noGood, int expected) throws Exception {
        DmzCurrentUnaryProofInventory.Proofs result = inventory.proofs(ground,
                noGood == null ? Collections.<DmzProofWitnesses.NoGood>emptyList() : Collections.singletonList(noGood), 10000);
        require(!result.truncated && result.witnesses.size() == expected, "current exact proof count expected " + expected);
    }
    private static void signedAndCycle() throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, source, 1, "!@x a(x) -> b(x);");
            accept(q, source, 2, "!@x b(x) -> a(x);");
            accept(q, source, 3, "!anchor(1);");
            DmzCurrentUnaryProofInventory cycle = DmzCurrentUnaryProofInventory.capture(q, 10000);
            require(cycle.eligible, "unseeded unit cycle inventory");
            TerminalSupportCapture.Ground anchor = cycle.graph.observed.nodes.get(0).ground;
            count(cycle, new TerminalSupportCapture.Ground("a", anchor.sign, anchor.arguments), null, 0);
            accept(q, source, 4, "!~b(John);");
            TerminalSupportCapture.Ground opposite = null;
            for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot()) {
                TerminalSupportCapture.Ground ground = observation.materialization.causes.nodes.get(observation.materialization.causes.root).ground;
                if (ground.predicate.equals("a")) opposite = ground;
            }
            require(opposite != null, "native string-valued contraposition observed");
            DmzCurrentUnaryProofInventory signed = DmzCurrentUnaryProofInventory.capture(q, 10000);
            require(signed.eligible, "signed cyclic unit inventory");
            count(signed, opposite, null, 1);
            count(signed, new TerminalSupportCapture.Ground(opposite.predicate, !opposite.sign, opposite.arguments), null, 0);
        }
    }
    private static void inconsistentPins() throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        UUID context = UUID.randomUUID();
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            accept(q, new IContextResults.Revision(context, 1), 1, "!p(1);");
            accept(q, new IContextResults.Revision(context, 2), 2, "!p(2);");
            DmzCurrentUnaryProofInventory mixed = DmzCurrentUnaryProofInventory.capture(q, 10000);
            require(!mixed.eligible && mixed.graph == null && mixed.gaps.contains("inconsistent-context-revisions"),
                    "inconsistent context revisions fail closed");
        }
    }
    private static void accept(Mind q, IContextResults.Revision source, long id, String statement) throws Exception {
        require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, id,
                DmzReplayProvenance.Authority.EXTERNAL, statement)), "native production/fact");
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
