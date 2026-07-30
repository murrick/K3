/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Observational P5a runner that relates Linker execution cost to externally
 * visible semantic delta. It does not alter planning, proof execution,
 * materialization, transactions, or persistence.
 *
 * Each measured operation receives an independent Mind populated with the same
 * durable fixture. This prevents result materialized by an earlier query from
 * suppressing solution or value-row deltas in a later query.
 */
public final class KangerSemanticYieldRunner {

    private KangerSemanticYieldRunner() {
    }

    public static void main(String[] args) {
        try {
            Path home = Files.createTempDirectory("kanger-semantic-yield-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            System.out.println("size,operation,result_rows,passes,executed_operations,"
                    + "new_tvalues,new_causes,solve_candidates,new_tsolves,"
                    + "duplicate_solve_candidates,new_generated_rules,rule_delta,"
                    + "solution_delta,value_row_delta,materialization_delta,"
                    + "knowledge_delta,effect_delta,direct_delta,proof_yield,effect_yield");

            for (int size : parseSizes(args)) {
                runCase(size);
            }
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runCase(int size) throws Exception {
        int key = Math.max(1, size / 2);
        measure(size, "query-exact",
                "?value(" + key + "," + (1000 + key) + ",7);");
        measure(size, "query-two-constants",
                "?$z value(" + key + "," + (1000 + key) + ",z);");
        measure(size, "query-one-constant",
                "?$y $z value(" + key + ",y,z);");
        measure(size, "query-all-variables",
                "?$x $y $z value(x,y,z);");
    }

    private static void measure(int size,
                                String operation,
                                String query) throws Exception {
        Mind mind = createFixture(size, operation);
        Snapshot before = Snapshot.capture(mind);

        Boolean result;
        SemanticEffectTelemetry.Snapshot effects;
        SemanticEffectTelemetry.begin();
        try {
            result = mind.query(query, null, false);
        } finally {
            effects = SemanticEffectTelemetry.end();
        }
        if (!Boolean.TRUE.equals(result)) {
            throw new IllegalStateException("Query failed: " + query);
        }
        Snapshot after = Snapshot.capture(mind);

        LinkerStatistics statistics = mind.getLinkerStatistics();
        long ruleDelta = nonNegativeDelta(after.rules, before.rules);
        long solutionDelta = nonNegativeDelta(after.solutions, before.solutions);
        long valueRowDelta = nonNegativeDelta(after.valueRows, before.valueRows);
        long materializationDelta = solutionDelta + valueRowDelta;
        long newTValues = statistics.getNewTValues();
        long newCauses = effects.getNewCauses();
        long solveCandidates = effects.getSolveCandidates();
        long newTSolves = effects.getNewTSolves();
        long duplicateSolveCandidates = effects.getDuplicateSolveCandidates();
        long newGeneratedRules = effects.getNewGeneratedRules();

        if (solveCandidates != newTSolves + duplicateSolveCandidates) {
            throw new IllegalStateException(
                    "TSolve candidate accounting mismatch for " + operation
                            + ": candidates=" + solveCandidates
                            + ", new=" + newTSolves
                            + ", duplicates=" + duplicateSolveCandidates);
        }

        // Coarse historical measure: proof-internal TValue creation plus externally
        // visible rule/result materialization.
        long knowledgeDelta = newTValues + ruleDelta + materializationDelta;

        // Canonical proof-internal effect inventory. Candidate attempts and duplicate
        // candidates are diagnostic costs, not additional semantic production.
        long effectDelta = newTValues + newCauses + newTSolves + newGeneratedRules;
        long executed = statistics.getUnificationAttempts();
        long directDelta = executed == 0L ? knowledgeDelta : 0L;
        double proofYield = executed == 0L
                ? 0.0
                : ((double) knowledgeDelta) / executed;
        double effectYield = executed == 0L
                ? 0.0
                : ((double) effectDelta) / executed;

        System.out.printf(Locale.ROOT,
                "%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.9f,%.9f%n",
                size,
                operation,
                valueRowDelta,
                statistics.getPasses(),
                executed,
                newTValues,
                newCauses,
                solveCandidates,
                newTSolves,
                duplicateSolveCandidates,
                newGeneratedRules,
                ruleDelta,
                solutionDelta,
                valueRowDelta,
                materializationDelta,
                knowledgeDelta,
                effectDelta,
                directDelta,
                proofYield,
                effectYield);
    }

    private static Mind createFixture(int size, String operation) throws Exception {
        String suffix = operation + "-" + size + "-" + System.nanoTime();
        User user = (User) UserFactory.createUser(
                "semantic-yield-" + suffix,
                "semantic-yield-" + suffix);
        new UDF().init(user);
        new DB().init(user);

        Mind mind = (Mind) new Mind(user).clearWorkspace();
        for (int i = 1; i <= size; ++i) {
            Boolean result = mind.query(
                    "!value(" + i + "," + (1000 + i) + ",7);",
                    null,
                    false);
            if (!Boolean.TRUE.equals(result)) {
                throw new IllegalStateException("Insert failed at row " + i);
            }
        }
        return mind;
    }

    private static long nonNegativeDelta(long after, long before) {
        return Math.max(0L, after - before);
    }

    private static int[] parseSizes(String[] args) {
        List<Integer> values = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                addSizes(values, arg);
            }
        }
        if (values.isEmpty()) {
            addSizes(values,
                    System.getProperty("kanger.semantic.yield.sizes", "100,500,1000"));
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static void addSizes(List<Integer> values, String source) {
        if (source == null || source.trim().isEmpty()) {
            return;
        }
        for (String token : source.split(",")) {
            int value = Integer.parseInt(token.trim());
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "Semantic-yield size must be positive: " + value);
            }
            values.add(value);
        }
    }

    private static final class Snapshot {
        private final long rules;
        private final long solutions;
        private final long valueRows;

        private Snapshot(long rules, long solutions, long valueRows) {
            this.rules = rules;
            this.solutions = solutions;
            this.valueRows = valueRows;
        }

        private static Snapshot capture(Mind mind) throws Exception {
            return new Snapshot(
                    mind.getRules().size(),
                    mind.getSolutions().size(),
                    mind.getValues().size());
        }
    }
}
