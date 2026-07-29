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
 * Deterministic operation-count profile for Linker. Absolute timing is
 * observational; counters define the algorithmic baseline for P4.
 */
public final class KangerLinkerProfileRunner {

    private KangerLinkerProfileRunner() {
    }

    public static void main(String[] args) {
        try {
            Path home = Files.createTempDirectory("kanger-linker-profile-");
            System.setProperty("user.home", home.toAbsolutePath().toString());
            int[] sizes = parseSizes(args);

            System.out.println("size,operation,millis,rows,passes,rule_visits,branch_visits,"
                    + "terminal_rotations,candidate_rule_visits,domain_pairs,unification_attempts,"
                    + "new_tvalues,database_evaluations,function_evaluations");
            for (int size : sizes) {
                runCase(size);
            }
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runCase(int size) throws Exception {
        String suffix = size + "-" + System.nanoTime();
        User user = (User) UserFactory.createUser("linker-" + suffix, "linker-" + suffix);
        new UDF().init(user);
        new DB().init(user);

        Mind mind = (Mind) new Mind(user).clearWorkspace();
        LinkerStatistics inserts = new LinkerStatistics();
        long insertStarted = System.nanoTime();
        for (int i = 1; i <= size; ++i) {
            Boolean result = mind.query(
                    "!value(" + i + "," + (1000 + i) + ",7);",
                    null,
                    false);
            if (!Boolean.TRUE.equals(result)) {
                throw new IllegalStateException("Insert failed at row " + i);
            }
            inserts.add(mind.getLinkerStatistics());
        }
        print(size, "insert-sequential", insertStarted, 0, inserts);

        int key = Math.max(1, size / 2);
        runQuery(mind, size, "query-exact",
                "?value(" + key + "," + (1000 + key) + ",7);");
        runQuery(mind, size, "query-two-constants",
                "?$z value(" + key + "," + (1000 + key) + ",z);");
        runQuery(mind, size, "query-one-constant",
                "?$y $z value(" + key + ",y,z);");
        runQuery(mind, size, "query-all-variables",
                "?$x $y $z value(x,y,z);");
    }

    private static void runQuery(Mind mind,
                                 int size,
                                 String operation,
                                 String query) throws Exception {
        long started = System.nanoTime();
        Boolean result = mind.query(query, null, false);
        if (!Boolean.TRUE.equals(result)) {
            throw new IllegalStateException("Query failed: " + query);
        }
        print(size, operation, started, mind.getValues().size(), mind.getLinkerStatistics());
    }

    private static void print(int size,
                              String operation,
                              long started,
                              int rows,
                              LinkerStatistics statistics) {
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        System.out.printf(
                "%d,%s,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                size,
                operation,
                millis,
                rows,
                statistics.getPasses(),
                statistics.getRuleVisits(),
                statistics.getBranchVisits(),
                statistics.getTerminalRotations(),
                statistics.getCandidateRuleVisits(),
                statistics.getDomainPairs(),
                statistics.getUnificationAttempts(),
                statistics.getNewTValues(),
                statistics.getDatabaseEvaluations(),
                statistics.getFunctionEvaluations());
    }

    private static int[] parseSizes(String[] args) {
        List<Integer> values = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                addSizes(values, arg);
            }
        }
        if (values.isEmpty()) {
            addSizes(values, System.getProperty("kanger.linker.profile.sizes", "100,500"));
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
                throw new IllegalArgumentException("Profile size must be positive: " + value);
            }
            values.add(value);
        }
    }
}
