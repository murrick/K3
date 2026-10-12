/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.IRule;

/** General native ground transport characterization; not general DMZ dispatch. */
public final class DmzGroundIdentityRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }

    public static void main(String[] args) {
        try {
            System.setProperty("user.home", Files.createTempDirectory("dmz-ground-identity-").toString());
            for (String atom : new String[]{"father(John,Tom)", "father(John,Mary)",
                    "marker(John)", "marker(\"John\")", "marker(1)", "marker(\"1\")",
                    "ready()", "marker(2.5)", "family([])", "family([John,Tom,Sarah])", "tuple(John,4,\"Tom\",[Mary,Sarah])"}) {
                characterize(atom, false); characterize(atom, true);
            }
            require(!identity("!father(John,Tom);").equivalent(identity("!father(Tom,John);")), "argument positions retained");
            require(!identity("!marker(1);").equivalent(identity("!marker(\"1\");")), "number and string remain distinct");
            require(identity("!marker(John);").equivalent(identity("!marker(\"John\");")), "bare and quoted names retain native semantic equality");
            require(!identity("!marker(John);").equivalent(identity("!other(John);")), "predicate retained");
            require(!identity("!marker(John);").opposes(identity("!~marker(Mary);")), "different payload is not opposition");
            binaryConsequence();
            reject(() -> identity("!@x marker(x);"));
            reject(() -> identity("!@x marker(x) -> derived(x);"));
            System.out.println("DMZ_GROUND_IDENTITY_PASS checks=" + checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }

    private static Mind source(String statement) throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        require(mind.compile(statement, null, false), "native source accepted: " + statement);
        return mind;
    }

    private static IRule primary(Mind mind, String statement) throws Exception {
        for (IRule rule : mind.getRules())
            if (!mind.getRules().isGenerated(rule) && statement.equals(rule.getOrigin())) return rule;
        throw new AssertionError("Source primary absent: " + statement);
    }

    private static DmzGroundIdentity identity(String statement) throws Exception {
        Mind mind = source(statement);
        return DmzGroundIdentity.capture(primary(mind, statement), mind);
    }

    private static void characterize(String atom, boolean reverse) throws Exception {
        String positive = "!" + atom + ";", negative = "!~" + atom + ";";
        String first = reverse ? negative : positive, second = reverse ? positive : negative;
        Mind a = source(first), b = source(second);
        IRule ar = primary(a, first), br = primary(b, second);
        DmzGroundIdentity original = DmzGroundIdentity.capture(ar, a);
        DmzGroundIdentity opposite = DmzGroundIdentity.capture(br, b);
        require(original.opposes(opposite) && opposite.opposes(original), "symmetric native opposition");
        String aState = DmzObservationStateFingerprint.capture(a), bState = DmzObservationStateFingerprint.capture(b);
        Mind q = source("!authority(1);");
        String state = DmzObservationStateFingerprint.capture(q);
        IContextResults.Revision ap = new IContextResults.Revision(UUID.randomUUID(), 7);
        IContextResults.Revision bp = new IContextResults.Revision(UUID.randomUUID(), 12);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            for (int side = 0; side < 2; ++side) {
                String statement = side == 0 ? first : second;
                IContextResults.Revision pin = side == 0 ? ap : bp;
                DmzGroundIdentity expected = side == 0 ? original : opposite;
                try (TechnicalMindTransaction branch = TechnicalMindTransaction.beginIsolated(q)) {
                    Mind child = branch.mind();
                    require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(child, pin, 3,
                            DmzReplayProvenance.Authority.EXTERNAL, statement)), "replay primary through native compiler");
                    IRule replayed = primary(child, statement);
                    require(expected.equivalent(DmzGroundIdentity.capture(replayed, child)), "typed replay identity matches source");
                    java.util.List<DmzReplayProvenance.SourceObservation> bindings = DmzReplayProvenance.observedSources(child, replayed.getId());
                    require(bindings.size() == 1 && bindings.get(0).binding.context.equals(pin.getContextId())
                            && bindings.get(0).binding.revision == pin.getRevision() && bindings.get(0).binding.sourceRule == 3
                            && bindings.get(0).outcome == DmzReplayProvenance.Outcome.PENDING,
                            "same source-local ID preserves exact pending UUID/revision");
                    require(journal.sources(child, replayed.getId()).isEmpty(), "pending branch provenance is not promoted to accepted");
                    require(Boolean.valueOf(side == 0 ? !reverse : reverse).equals(child.query("?" + atom + ";", null, false)),
                            "branch native answer preserves sign");
                    require(Boolean.TRUE.equals(child.query("?authority(1);", null, false)), "Q retained in both alternatives");
                    if (side == 0) {
                        try (CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                            require(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(child, bp, 3,
                                    DmzReplayProvenance.Authority.EXTERNAL, second)), "opposite candidate rejected atomically");
                            require(!collisions.snapshot().isEmpty(), "native collision captured");
                            boolean typedPair = false;
                            for (CollisionProofCapture.Conflict conflict : collisions.snapshot()) {
                                TerminalSupportCapture.Ground left = conflict.left.nodes.get(conflict.left.root).ground;
                                TerminalSupportCapture.Ground right = conflict.right.nodes.get(conflict.right.root).ground;
                                if (left != null && right != null)
                                    typedPair |= DmzGroundIdentity.fromGround(left).opposes(DmzGroundIdentity.fromGround(right));
                            }
                            require(typedPair, "detached collision retains typed opposition");
                            require(original.equivalent(DmzGroundIdentity.capture(primary(child, first), child)), "rejected opposite preserves first fact");
                        }
                    }
                }
                require(state.equals(DmzObservationStateFingerprint.capture(q)), "rolled-back branch preserves Q");
            }
            require(journal.settlementSnapshot().accepted.isEmpty(), "disposed branches promote no source provenance into Q");
        }
        require(aState.equals(DmzObservationStateFingerprint.capture(a)) && bState.equals(DmzObservationStateFingerprint.capture(b)),
                "source snapshots preserved");
        require(state.equals(DmzObservationStateFingerprint.capture(q)), "capture cleanup preserves Q");
    }

    private static void binaryConsequence() throws Exception {
        Mind q = source("!authority(1);");
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 12);
        IContextResults.Revision facts = new IContextResults.Revision(UUID.randomUUID(), 7);
        DmzGroundIdentity expected = identity("!child(Tom,John);");
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, rules, 3,
                    DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x @y father(x,y) -> child(y,x);")), "native binary production accepted");
            String state = DmzObservationStateFingerprint.capture(q);
            try (TechnicalMindTransaction branch = TechnicalMindTransaction.beginIsolated(q)) {
                Mind child = branch.mind();
                require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(child, facts, 3,
                        DmzReplayProvenance.Authority.EXTERNAL, "!father(John,Tom);")), "binary donor accepted");
                require(Boolean.TRUE.equals(child.query("?child(Tom,John);", null, false)), "binary argument substitution inferred");
                require(Boolean.TRUE.equals(child.query("?$x child(x,John);", null, false)), "native binary variable query remains available");
                boolean observed = false;
                for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot()) {
                    CollisionProofCapture.Graph graph = observation.materialization.causes;
                    TerminalSupportCapture.Ground conclusion = graph.nodes.get(graph.root).ground;
                    if (conclusion == null || !expected.equivalent(DmzGroundIdentity.fromGround(conclusion))) continue;
                    boolean ruleSource = false, factSource = false;
                    for (CollisionProofCapture.Node node : graph.nodes) {
                        for (DmzReplayProvenance.SourceObservation source : node.sources) {
                            DmzReplayProvenance.Binding binding = source.binding;
                            ruleSource |= binding.context.equals(rules.getContextId()) && binding.revision == 12
                                    && binding.sourceRule == 3 && source.outcome == DmzReplayProvenance.Outcome.ACCEPTED;
                            factSource |= binding.context.equals(facts.getContextId()) && binding.revision == 7
                                    && binding.sourceRule == 3 && source.outcome == DmzReplayProvenance.Outcome.PENDING;
                        }
                    }
                    require(ruleSource && factSource, "generated binary consequence retains distinct production and donor sources");
                    observed = true;
                }
                require(observed, "typed binary terminal consequence captured");
            }
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "binary inference branch rollback preserves Q");
            require(journal.settlementSnapshot().accepted.size() == 1, "only original Q production remains accepted");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); ++checks;
    }
    private static void reject(Attempt attempt) throws Exception {
        try { attempt.run(); throw new AssertionError("Expected non-ground refusal"); }
        catch (IllegalArgumentException expected) { ++checks; }
    }
}
