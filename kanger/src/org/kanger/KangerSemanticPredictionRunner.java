/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.QueryPass;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P5b.1 calibration runner. It captures a pre-execution estimate at the
 * Analyzer candidate boundary and compares it with the canonical P5a effects
 * observed after the positive query pass.
 */
public final class KangerSemanticPredictionRunner {

    private KangerSemanticPredictionRunner() {
    }

    public static void main(String[] args) {
        try {
            Path home = Files.createTempDirectory("kanger-semantic-prediction-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            System.out.println("size,operation,calibration,calibrated,prediction_events,"
                    + "calibrated_events,candidate_pool,bound_positions,query_variables,"
                    + "predicted_matches,predicted_result_rows,actual_result_rows,"
                    + "predicted_executed_operations,actual_executed_operations,"
                    + "predicted_effect_delta,actual_effect_delta,predicted_direct_delta,"
                    + "actual_direct_delta,predicted_effect_yield,actual_effect_yield");

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
        SemanticPlanningTelemetry.Snapshot planning;
        SemanticPlanningTelemetry.begin(QueryPass.CHECKTRUE);
        SemanticEffectTelemetry.begin();
        try {
            result = mind.query(query, null, false);
        } finally {
            effects = SemanticEffectTelemetry.end();
            planning = SemanticPlanningTelemetry.end();
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
        long knowledgeDelta = statistics.getNewTValues()
                + ruleDelta + materializationDelta;
        long actualEffectDelta = statistics.getNewTValues()
                + effects.getNewCauses()
                + effects.getNewTSolves()
                + effects.getNewGeneratedRules();
        long actualExecuted = statistics.getUnificationAttempts();
        long actualDirectDelta = actualExecuted == 0L ? knowledgeDelta : 0L;
        double actualEffectYield = actualExecuted == 0L
                ? 0.0
                : ((double) actualEffectDelta) / actualExecuted;

        SemanticPlanEstimate estimate = planning.getEstimate();
        if (estimate == null || !estimate.isCalibrated()) {
            throw new IllegalStateException(
                    "Missing calibrated estimate for " + operation
                            + "; pass=" + planning.getTargetPass()
                            + "; shape=" + planning.getQueryShape());
        }
        if (estimate.getExpectedResultRows() != valueRowDelta
                || estimate.getExpectedExecutedOperations() != actualExecuted
                || estimate.getExpectedEffectDelta() != actualEffectDelta
                || estimate.getExpectedDirectDelta() != actualDirectDelta) {
            throw new IllegalStateException(
                    "Prediction mismatch for " + operation
                            + ": rows=" + estimate.getExpectedResultRows()
                            + "/" + valueRowDelta
                            + ", operations="
                            + estimate.getExpectedExecutedOperations()
                            + "/" + actualExecuted
                            + ", effects=" + estimate.getExpectedEffectDelta()
                            + "/" + actualEffectDelta
                            + ", direct=" + estimate.getExpectedDirectDelta()
                            + "/" + actualDirectDelta
                            + "; pass=" + planning.getTargetPass()
                            + "; shape=" + planning.getQueryShape());
        }
        if (estimate.getCandidatePool() != size) {
            throw new IllegalStateException(
                    "Candidate-pool mismatch for " + operation
                            + ": " + estimate.getCandidatePool()
                            + " != " + size
                            + "; pass=" + planning.getTargetPass()
                            + "; shape=" + planning.getQueryShape());
        }

        System.out.printf(Locale.ROOT,
                "%d,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.9f,%.9f%n",
                size,
                operation,
                estimate.getCalibration(),
                estimate.isCalibrated(),
                planning.getEvents(),
                planning.getCalibratedEvents(),
                estimate.getCandidatePool(),
                estimate.getBoundPositions(),
                estimate.getQueryVariables(),
                estimate.getMatchedCandidates(),
                estimate.getExpectedResultRows(),
                valueRowDelta,
                estimate.getExpectedExecutedOperations(),
                actualExecuted,
                estimate.getExpectedEffectDelta(),
                actualEffectDelta,
                estimate.getExpectedDirectDelta(),
                actualDirectDelta,
                estimate.getExpectedEffectYield(),
                actualEffectYield);
    }

    private static Mind createFixture(int size, String operation) throws Exception {
        String suffix = operation + "-" + size + "-" + System.nanoTime();
        User user = (User) UserFactory.createUser(
                "semantic-prediction-" + suffix,
                "semantic-prediction-" + suffix);
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
                    System.getProperty(
                            "kanger.semantic.prediction.sizes",
                            "100,500,1000"));
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
                        "Semantic-prediction size must be positive: " + value);
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
