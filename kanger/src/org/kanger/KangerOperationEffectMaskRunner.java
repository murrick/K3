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

/**
 * Isolated P5a profile of exact immediate-effect mask combinations. Every
 * operation receives a fresh Mind with an equivalent durable value/3 fixture.
 * Deferred solve candidates are reported as candidates, not as created TSolve
 * objects, and generated rules remain query-level until provenance is carried
 * from the originating unification.
 */
public final class KangerOperationEffectMaskRunner {

    private static final int MASK_LIMIT = 1 << 4;
    private static final int MASK_DEFERRED =
            LinkerStatistics.EFFECT_DEFERRED_SOLVE_CANDIDATE;
    private static final int MASK_PRODUCING =
            LinkerStatistics.EFFECT_NEW_TVALUE
                    | LinkerStatistics.EFFECT_NEW_CAUSE
                    | LinkerStatistics.EFFECT_DEFERRED_SOLVE_CANDIDATE;

    private KangerOperationEffectMaskRunner() {
    }

    public static void main(String[] args) {
        try {
            Path home = Files.createTempDirectory("kanger-operation-effects-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            System.out.println("size,operation,mask,effects,count,"
                    + "classified_operations,executed_operations");
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
        Boolean result = mind.query(query, null, false);
        if (!Boolean.TRUE.equals(result)) {
            throw new IllegalStateException("Query failed: " + query);
        }

        LinkerStatistics statistics = mind.getLinkerStatistics();
        long executed = statistics.getUnificationAttempts();
        long classified = statistics.getClassifiedOperations();
        if (classified != executed) {
            throw new IllegalStateException(
                    "Incomplete operation classification for " + operation
                            + ": classified=" + classified
                            + ", executed=" + executed);
        }

        verifyBaseline(size, operation, statistics);

        if (executed == 0L) {
            print(size, operation, 0, "NO_LINKER_OPERATION", 0L,
                    classified, executed);
            return;
        }

        long total = 0L;
        for (int mask = 0; mask < MASK_LIMIT; ++mask) {
            long count = statistics.getOperationEffectMaskCount(mask);
            if (count > 0L) {
                print(size, operation, mask, describe(mask), count,
                        classified, executed);
                total += count;
            }
        }
        if (total != executed) {
            throw new IllegalStateException(
                    "Operation mask histogram mismatch for " + operation
                            + ": total=" + total + ", executed=" + executed);
        }
    }

    private static void verifyBaseline(int size,
                                       String operation,
                                       LinkerStatistics statistics) {
        if ("query-exact".equals(operation)) {
            requireCount(operation, statistics, 0, 0L);
            if (statistics.getUnificationAttempts() != 0L) {
                throw new IllegalStateException(
                        "Exact-query Linker baseline changed: "
                                + statistics.getUnificationAttempts());
            }
            return;
        }

        long scale = "query-all-variables".equals(operation) ? size : 1L;
        requireCount(operation, statistics, 0, 3L * scale);
        requireCount(operation, statistics, MASK_DEFERRED, 4L * scale);
        requireCount(operation, statistics, MASK_PRODUCING, scale);

        for (int mask = 0; mask < MASK_LIMIT; ++mask) {
            if (mask != 0 && mask != MASK_DEFERRED && mask != MASK_PRODUCING) {
                requireCount(operation, statistics, mask, 0L);
            }
        }
        long expectedExecuted = 8L * scale;
        if (statistics.getUnificationAttempts() != expectedExecuted) {
            throw new IllegalStateException(
                    "Executed-operation baseline changed for " + operation
                            + ": expected=" + expectedExecuted
                            + ", actual=" + statistics.getUnificationAttempts());
        }
    }

    private static void requireCount(String operation,
                                     LinkerStatistics statistics,
                                     int mask,
                                     long expected) {
        long actual = statistics.getOperationEffectMaskCount(mask);
        if (actual != expected) {
            throw new IllegalStateException(
                    "Operation mask baseline changed for " + operation
                            + ", mask=" + mask
                            + ": expected=" + expected
                            + ", actual=" + actual);
        }
    }

    private static void print(int size,
                              String operation,
                              int mask,
                              String effects,
                              long count,
                              long classified,
                              long executed) {
        System.out.println(size + "," + operation + "," + mask + ","
                + effects + "," + count + "," + classified + "," + executed);
    }

    private static String describe(int mask) {
        if (mask == 0) {
            return "NO_IMMEDIATE_EFFECT";
        }
        StringBuilder result = new StringBuilder();
        append(result, mask, LinkerStatistics.EFFECT_NEW_TVALUE, "NEW_TVALUE");
        append(result, mask, LinkerStatistics.EFFECT_NEW_CAUSE, "NEW_CAUSE");
        append(result, mask, LinkerStatistics.EFFECT_DEFERRED_SOLVE_CANDIDATE,
                "DEFERRED_SOLVE_CANDIDATE");
        append(result, mask, LinkerStatistics.EFFECT_USED_ONLY, "USED_ONLY");
        return result.toString();
    }

    private static void append(StringBuilder result,
                               int mask,
                               int effect,
                               String name) {
        if ((mask & effect) == 0) {
            return;
        }
        if (result.length() > 0) {
            result.append('|');
        }
        result.append(name);
    }

    private static Mind createFixture(int size, String operation) throws Exception {
        String suffix = operation + "-" + size + "-" + System.nanoTime();
        User user = (User) UserFactory.createUser(
                "operation-effects-" + suffix,
                "operation-effects-" + suffix);
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

    private static int[] parseSizes(String[] args) {
        List<Integer> values = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                addSizes(values, arg);
            }
        }
        if (values.isEmpty()) {
            addSizes(values, System.getProperty(
                    "kanger.operation.effects.sizes", "100,500,1000"));
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
                        "Operation-effect size must be positive: " + value);
            }
            values.add(value);
        }
    }
}
