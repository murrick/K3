/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Detached pairing fixture; native opposite programs run in separate contexts. */
public final class DmzWitnessConflictsRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-witness-pairs-").toString());
        for (boolean leftQ : new boolean[] {false, true})
            for (boolean rightQ : new boolean[] {false, true}) run(leftQ, rightQ);
        System.out.println("DMZ_WITNESS_CONFLICTS_PASS checks=" + checks);
    }
    private static Mind root() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q); return q;
    }
    private static void program(Mind q, boolean negative, boolean local) throws Exception {
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 1);
        DmzReplayProvenance.Authority authority = local ? DmzReplayProvenance.Authority.TARGET_Q
                : DmzReplayProvenance.Authority.EXTERNAL;
        DmzReplayProvenance.replayRule(q, pin, 1, authority,
                negative ? "!@x b(x) -> ~male(x);" : "!@x a(x) -> male(x);");
        DmzReplayProvenance.replayRule(q, pin, 2, authority, negative ? "!b(John);" : "!a(John);");
        if (negative) DmzReplayProvenance.replayRule(q, new IContextResults.Revision(UUID.randomUUID(), 1),
                3, authority, "!b(John);");
        require(q.compile("!anchor(Trigger);", null, false), "separate opinion inferred");
    }
    private static void run(boolean leftQ, boolean rightQ) throws Exception {
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            program(root(), false, leftQ); program(root(), true, rightQ);
            List<TerminalSupportCapture.ApplicationSources> original = capture.sourceSnapshot(journal);
            require(DmzWitnessConflicts.collect(DmzSourcedProofGraph.build(original), 1000, 100).combinations.isEmpty(),
                    "different native Minds do not collide");
            // Algorithm fixture normalizes only detached Mind IDs to test opposite opinion pairing.
            List<TerminalSupportCapture.ApplicationSources> normalized = new ArrayList<>();
            for (TerminalSupportCapture.ApplicationSources sources : original) {
                TerminalSupportCapture.Application a = sources.application;
                TerminalSupportCapture.Application copy = new TerminalSupportCapture.Application(0, a.rule,
                        a.ruleOrigin, a.conclusion, a.ground, a.bindings, a.supports, a.sourceCandidates);
                normalized.add(new TerminalSupportCapture.ApplicationSources(copy, sources.ruleSources, sources.supportSources));
            }
            DmzSourcedProofGraph graph = DmzSourcedProofGraph.build(normalized);
            DmzWitnessConflicts.Result result = DmzWitnessConflicts.collect(graph, 1000, 100);
            require(!result.truncated && result.combinations.size() == 2, "two independent concrete opposite pairs");
            DmzWitnessConflicts.Combination pair = result.combinations.get(0);
            DmzWitnessConflicts.Policy expected = leftQ && rightQ ? DmzWitnessConflicts.Policy.Q_CONFLICT
                    : leftQ ? DmzWitnessConflicts.Policy.KEEP_LEFT_Q : rightQ ? DmzWitnessConflicts.Policy.KEEP_RIGHT_Q
                    : DmzWitnessConflicts.Policy.SYMMETRIC_ALTERNATIVES;
            // Graph input order follows successful native program order.
            require(pair.policy == expected, "Q protection classification");
            require(pair.left.premises.size() == 1 && pair.right.premises.size() == 1,
                    "both full chains retained");
            require(pair.matches(pair.right, pair.left), "no-good is symmetric");
            DmzWitnessConflicts.Combination independent = result.combinations.get(1);
            require(!pair.matches(independent.left, independent.right), "independent input source survives exact no-good");
            DmzWitnessConflicts.Combination rebuilt = DmzWitnessConflicts.collect(
                    DmzSourcedProofGraph.build(normalized), 1000, 100).combinations.get(0);
            require(!pair.matches(rebuilt.left, rebuilt.right), "no-good cannot cross graph ownership");
            require(DmzWitnessConflicts.collect(graph, 1, 100).truncated, "partial side marked incomplete");
            require(DmzWitnessConflicts.collect(graph, 1000, 1).truncated, "pair budget explicitly incomplete");
            try { result.combinations.clear(); throw new AssertionError("mutable"); }
            catch (UnsupportedOperationException expectedException) { ++checks; }
        }
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
