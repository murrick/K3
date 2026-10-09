/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Native collision observations retain independent source-backed alternatives. */
public final class DmzNativeAlternativeProofRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-native-alternatives-").toString());
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] order : orders) { run(order, false); run(order, true); }
        lateInputs();
        preparedScope();
        System.out.println("DMZ_NATIVE_ALTERNATIVE_PROOF_PASS checks=" + checks);
    }
    private static void run(int[] order, boolean recursive) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision[] sources = new IContextResults.Revision[3];
        for (int i = 0; i < sources.length; ++i)
            sources[i] = new IContextResults.Revision(UUID.randomUUID(), i + 1);
        String[] predicates = {"a", "c", "b"};
        DmzCollisionProofGuard closedGuard;
        DmzProvisionalCollisionProof detachedProof;
        Mind closedTarget;
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            for (int i : order) {
                DmzReplayProvenance.replayRule(q, sources[i], 10, DmzReplayProvenance.Authority.EXTERNAL,
                        "!@x " + predicates[i] + "(x) -> " + (i == 2 ? "~male" : recursive ? "middle" : "male") + "(x);");
            }
            if (recursive) DmzReplayProvenance.replayRule(q, sources[0], 20, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x middle(x) -> male(x);");
            try (TechnicalMindTransaction branch = TechnicalMindTransaction.begin(q);
                    TerminalSupportCapture applications = TerminalSupportCapture.begin(branch.mind());
                    CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                for (int i : order) {
                    DmzReplayProvenance.replayRule(branch.mind(), sources[i], 11,
                            DmzReplayProvenance.Authority.EXTERNAL, "!" + predicates[i] + "(John);");
                }
                TerminalSupportCapture.Checkpoint stateOnly = applications.checkpoint(branch.mind());
                DmzCollisionProofGuard baseline = DmzCollisionProofGuard.beforeOperation(applications, branch.mind());
                require(!branch.mind().compile("!anchor(Trigger);", null, false), "conflicting operation rejected");
                require(!collisions.snapshot().isEmpty(), "native collision events");
                for (CollisionProofCapture.Conflict event : collisions.snapshot()) {
                    DmzProvisionalCollisionProof proof = DmzProvisionalCollisionProof.build(event, 1000, 100);
                    require(!proof.truncated && proof.rootsAvailable, "bounded roots available");
                    require(baseline.isCurrent(proof, branch.mind()), "rejected operation proof matches unchanged pre-operation state");
                    DmzCollisionProofGuard after = DmzCollisionProofGuard.beforeOperation(applications, branch.mind());
                    require(!after.isCurrent(proof, branch.mind()), "post-event checkpoint cannot retroactively bind old proof");
                    require(!baseline.isCurrent(proof, q), "proof guard cannot cross target branch");
                    Set<UUID> positive = new HashSet<UUID>();
                    DmzWitnessConflicts.Combination first = null, second = null;
                    for (DmzWitnessConflicts.Combination pair : proof.combinations) {
                        require(pair.policy == DmzWitnessConflicts.Policy.SYMMETRIC_ALTERNATIVES,
                                "external opinions use symmetric alternatives");
                        DmzProofWitnesses.Witness witness = pair.left.graph.observed.nodes.get(pair.left.node).ground.sign
                                ? pair.left : pair.right;
                        DmzProofWitnesses.Witness leaf = primary(witness);
                        positive.add(leaf.source.context);
                        if (leaf.source.context.equals(sources[0].getContextId())) first = pair;
                        if (leaf.source.context.equals(sources[1].getContextId())) second = pair;
                        require(leaf.source.sourceRule == 11 && leaf.step == -1,
                                "independent derivation reaches exact primary fact occurrence");
                    }
                    require(positive.contains(sources[0].getContextId()) && positive.contains(sources[1].getContextId()),
                            "both independent positive derivations retained at each native collision");
                    require(first.matches(first.left, first.right) && first.matches(first.right, first.left),
                            "concrete no-good symmetric");
                    require(!first.matches(second.left, second.right), "blocking one proof combination preserves independent proof");
                    require(!proof.complete, "two observed alternatives do not certify general coverage");
                    try (TerminalSupportCapture other = TerminalSupportCapture.begin(branch.mind())) {
                        require(!baseline.isCurrent(proof, branch.mind()), "other support scope cannot use guard");
                    }
                    try (DmzReplayProvenance other = DmzReplayProvenance.begin(branch.mind())) {
                        require(!baseline.isCurrent(proof, branch.mind()), "other replay scope cannot use guard");
                    }
                    require(baseline.isCurrent(proof, branch.mind()), "owning scopes restored");
                    require(!baseline.isCurrent(DmzProvisionalCollisionProof.build(event, 1, 1), branch.mind()),
                            "budget truncation cannot pass proof guard");
                }
                require(applications.settlementSnapshot().accepted.isEmpty(), "rejected proof observations not accepted");
                DmzProvisionalCollisionProof firstProof = DmzProvisionalCollisionProof.build(collisions.snapshot().get(0), 1000, 100);
                closedGuard = baseline; detachedProof = firstProof; closedTarget = branch.mind();
                DmzReplayProvenance.replayRule(branch.mind(), sources[0], 12, DmzReplayProvenance.Authority.EXTERNAL,
                        "!a(John);");
                require(applications.isCurrent(stateOnly, branch.mind()), "duplicate replay preserves canonical state checkpoint");
                require(!baseline.isCurrent(firstProof, branch.mind()), "canonical duplicate replay invalidates source baseline");
                branch.rollback();
                require(!baseline.isCurrent(firstProof, branch.mind()), "outer rollback cannot revive proof guard");
            }
        }
        require(!closedGuard.isCurrent(detachedProof, closedTarget), "closed scope guard unusable while detached proof remains readable");
        require(detachedProof.rootsAvailable && !detachedProof.combinations.isEmpty(), "detached alternatives survive scope close");
        require(!Boolean.TRUE.equals(q.query("?male(John);", null, false)), "rollback preserves Q");
    }
    private static DmzProofWitnesses.Witness primary(DmzProofWitnesses.Witness witness) {
        while (witness.step >= 0) {
            require(witness.premises.size() == 1, "bounded fixture has one input per production");
            witness = witness.premises.get(0);
        }
        return witness;
    }
    /** A real collision can use child inputs absent from the unchanged baseline. */
    private static void lateInputs() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin(q);
                CollisionProofCapture collisions = CollisionProofCapture.begin()) {
            DmzReplayProvenance.replayRule(q, pin, 1, DmzReplayProvenance.Authority.EXTERNAL, "!@x a(x) -> male(x);");
            DmzReplayProvenance.replayRule(q, pin, 2, DmzReplayProvenance.Authority.EXTERNAL, "!@x b(x) -> ~male(x);");
            TerminalSupportCapture.Checkpoint state = capture.checkpoint(q);
            DmzCollisionProofGuard baseline = DmzCollisionProofGuard.beforeOperation(capture, q);
            try (TechnicalMindTransaction operation = TechnicalMindTransaction.begin(q)) {
                DmzReplayProvenance.replayRule(operation.mind(), pin, 3, DmzReplayProvenance.Authority.EXTERNAL, "!a(John);");
                DmzReplayProvenance.replayRule(operation.mind(), pin, 4, DmzReplayProvenance.Authority.EXTERNAL, "!b(John);");
                require(!operation.mind().compile("!anchor(Trigger);", null, false), "late inputs cause native collision");
                require(!collisions.snapshot().isEmpty(), "late-input collision observed");
                for (CollisionProofCapture.Conflict event : collisions.snapshot()) {
                    DmzProvisionalCollisionProof proof = DmzProvisionalCollisionProof.build(event, 1000, 100);
                    require(proof.rootsAvailable && !proof.truncated && !proof.combinations.isEmpty(), "late-input proof is source-backed");
                    require(capture.isCurrent(state, q) && capture.follows(state, event.observations),
                            "late-input event follows unchanged baseline in correct operation");
                    require(!baseline.isCurrent(proof, q), "child input absent from baseline cannot qualify old target state");
                }
                operation.rollback();
            }
            require(capture.isCurrent(state, q), "discarded child inputs leave target unchanged");
        }
    }
    private static void preparedScope() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            DmzReplayProvenance.replayRule(q, pin, 1, DmzReplayProvenance.Authority.EXTERNAL, "!@x a(x) -> male(x);");
            DmzReplayProvenance.replayRule(q, pin, 2, DmzReplayProvenance.Authority.EXTERNAL, "!@x b(x) -> ~male(x);");
            DmzReplayProvenance.replayRule(q, pin, 3, DmzReplayProvenance.Authority.EXTERNAL, "!a(John);");
            DmzReplayProvenance.replayRule(q, pin, 4, DmzReplayProvenance.Authority.EXTERNAL, "!b(John);");
            try (TerminalSupportCapture capture = TerminalSupportCapture.begin(q);
                    CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                try { capture.checkpoint(q); throw new AssertionError("unobserved inference checkpoint"); }
                catch (IllegalStateException expected) { ++checks; }
                DmzCollisionProofGuard baseline = DmzCollisionProofGuard.beforeOperation(capture, q);
                require(!q.compile("!anchor(Trigger);", null, false), "prepared context first observed operation rejects collision");
                require(!collisions.snapshot().isEmpty(), "prepared context event captured");
                for (CollisionProofCapture.Conflict event : collisions.snapshot())
                    require(baseline.isCurrent(DmzProvisionalCollisionProof.build(event, 1000, 100), q),
                            "state baseline works before any observed target commit");
            }
        }
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
