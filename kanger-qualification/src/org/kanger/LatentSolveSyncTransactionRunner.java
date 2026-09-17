/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Rule;
import org.kanger.udf.UDF;

/** Complete textual projections across nested publication/rollback, in independent JVM modes. */
public final class LatentSolveSyncTransactionRunner {
    private static final List<String> states = new ArrayList<>();
    private static final List<String> work = new ArrayList<>();
    private static final Map<String, List<String>> snapshots = new HashMap<>();
    private static int operations;

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("solve-transactions-").toString());
        User user = new User();
        new UDF().init(user);
        Mind root = new Mind(user);
        require(root.compile("!edge(A,B); !@x @y edge(x,y) -> edge(y,x); !@x @y edge(x,y) -> path(x,y);"), "root compile");
        query(root, "root-before", "?$x $y path(x,y);", true);
        Mind child = new Mind(root);
        require(child.compile("!edge(B,C);"), "child compile");
        query(child, "child-before-nested", "?$x $y path(x,y);", true);
        query(root, "parent-isolated", "?edge(B,C);", false);

        Mind nested = new Mind(child);
        require(nested.compile("!edge(C,D);"), "nested compile");
        query(nested, "nested-before-rollback", "?$x $y path(x,y);", true);
        child.release(nested);
        query(child, "child-after-nested-rollback", "?edge(C,D);", false);
        query(child, "child-repeat-after-rollback", "?$x $y path(x,y);", true);

        nested = new Mind(child);
        require(nested.compile("!edge(C,E);"), "nested commit compile");
        query(nested, "nested-before-commit", "?$x $y path(x,y);", true);
        require(child.commit(nested), "nested commit");
        query(child, "child-after-nested-commit", "?edge(C,E);", true);
        query(child, "child-all-after-commit", "?$x $y path(x,y);", true);
        root.release(child);
        query(root, "root-after-outer-rollback-C", "?edge(B,C);", false);
        query(root, "root-after-outer-rollback-E", "?edge(C,E);", false);
        query(root, "root-repeat-after-rollback", "?$x $y path(x,y);", true);

        child = new Mind(root);
        require(child.compile("!edge(B,F);"), "committing child compile");
        query(child, "child-before-outer-commit", "?$x $y path(x,y);", true);
        require(root.commit(child), "outer commit");
        query(root, "root-after-outer-commit", "?edge(B,F);", true);
        query(root, "root-all-after-commit", "?$x $y path(x,y);", true);
        query(root, "rejected-collision", "!~edge(B,F);", false);
        query(root, "root-after-rejection", "?$x $y path(x,y);", true);
        // Record deletion behavior from the oracle; do not assume derived productions retract.
        query(root, "delete", "-edge(B,F);", null);
        query(root, "root-after-delete", "?$x $y path(x,y);", true);
        query(root, "root-repeat-after-delete", "?$x $y path(x,y);", true);
        same("root-before", "root-repeat-after-rollback");
        same("child-before-nested", "child-repeat-after-rollback");
        same("root-all-after-commit", "root-after-rejection");
        same("root-after-delete", "root-repeat-after-delete");
        Files.write(Paths.get(args[0]), states, StandardCharsets.UTF_8);
        Files.write(Paths.get(args[1]), work, StandardCharsets.UTF_8);
        System.out.println("LATENT_SOLVE_TRANSACTIONS_PASS operations=" + operations);
    }

    private static void query(Mind mind, String label, String source, Boolean expectedTrue) throws Exception {
        SemanticEffectTelemetry.begin();
        Boolean result;
        SemanticEffectTelemetry.Snapshot effects;
        try { result = mind.query(source, null, false); }
        finally { effects = SemanticEffectTelemetry.end(); }
        if (expectedTrue != null) require(Boolean.TRUE.equals(result) == expectedTrue, label + " result=" + result);
        ++operations;
        states.add(label + " " + source + " => " + result);
        int projectionStart = states.size();
        List<String> rows = new ArrayList<>();
        for (Map<String, ITerm> row : mind.getValues()) {
            Map<String, String> sorted = new TreeMap<>();
            for (Map.Entry<String, ITerm> e : row.entrySet()) sorted.put(e.getKey(), e.getValue().toString());
            rows.add(sorted.toString());
        }
        record("values", rows);
        rows.clear();
        for (IRule r : mind.getSolutions()) rows.add(((Rule) r).toString(mind));
        record("solutions", rows);
        rows.clear();
        for (IHypothesis h : mind.getHypothesis()) rows.add(((Hypothesis) h).toString(mind));
        record("hypotheses", rows);
        rows.clear();
        for (IRule r : mind.getRules()) if (!r.isDeleted(mind)) rows.add(r.isGenerated() + ":" + ((Rule) r).toString(mind));
        record("rules", rows);
        snapshots.put(label, new ArrayList<>(states.subList(projectionStart, states.size())));
        LinkerStatistics s = mind.getLinkerStatistics();
        states.add("passes=" + s.getPasses());
        states.add("effects=" + s.getUnificationAttempts() + "," + s.getNewTValues() + ","
                + effects.getNewCauses() + "," + effects.getNewTSolves() + "," + effects.getNewGeneratedRules()
                + "," + effects.getSolveCandidates());
        work.add(label + " sync=" + Arrays.toString(Linker.experimentalSolveScanProfile()) + " new-tsolves=" + effects.getNewTSolves());
    }

    private static void record(String label, List<String> rows) {
        Collections.sort(rows);
        states.add(label + "=" + rows);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void same(String before, String after) {
        require(snapshots.get(before).equals(snapshots.get(after)), "State changed: " + before + " -> " + after);
    }
}
