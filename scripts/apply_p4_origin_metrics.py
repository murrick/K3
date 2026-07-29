from pathlib import Path

statistics = '''/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Query-local operational counters for Linker planning. This is observational
 * execution state only; it is neither serialized nor part of logical results.
 */
public final class LinkerStatistics {

    private long passes;
    private long ruleVisits;
    private long branchVisits;
    private long terminalRotations;
    private long candidateRuleVisits;
    private long domainPairs;
    private long unificationAttempts;
    private long newTValues;
    private long databaseEvaluations;
    private long functionEvaluations;

    private long firstPassDurableUnifications;
    private long firstPassQueryUnifications;
    private long firstPassGeneratedUnifications;
    private long laterPassDurableUnifications;
    private long laterPassQueryUnifications;
    private long laterPassGeneratedUnifications;

    public LinkerStatistics() {
    }

    private LinkerStatistics(LinkerStatistics source) {
        passes = source.passes;
        ruleVisits = source.ruleVisits;
        branchVisits = source.branchVisits;
        terminalRotations = source.terminalRotations;
        candidateRuleVisits = source.candidateRuleVisits;
        domainPairs = source.domainPairs;
        unificationAttempts = source.unificationAttempts;
        newTValues = source.newTValues;
        databaseEvaluations = source.databaseEvaluations;
        functionEvaluations = source.functionEvaluations;
        firstPassDurableUnifications = source.firstPassDurableUnifications;
        firstPassQueryUnifications = source.firstPassQueryUnifications;
        firstPassGeneratedUnifications = source.firstPassGeneratedUnifications;
        laterPassDurableUnifications = source.laterPassDurableUnifications;
        laterPassQueryUnifications = source.laterPassQueryUnifications;
        laterPassGeneratedUnifications = source.laterPassGeneratedUnifications;
    }

    void reset() {
        passes = 0L;
        ruleVisits = 0L;
        branchVisits = 0L;
        terminalRotations = 0L;
        candidateRuleVisits = 0L;
        domainPairs = 0L;
        unificationAttempts = 0L;
        newTValues = 0L;
        databaseEvaluations = 0L;
        functionEvaluations = 0L;
        firstPassDurableUnifications = 0L;
        firstPassQueryUnifications = 0L;
        firstPassGeneratedUnifications = 0L;
        laterPassDurableUnifications = 0L;
        laterPassQueryUnifications = 0L;
        laterPassGeneratedUnifications = 0L;
    }

    LinkerStatistics snapshot() {
        return new LinkerStatistics(this);
    }

    void incrementPasses() { ++passes; }
    void incrementRuleVisits() { ++ruleVisits; }
    void incrementBranchVisits() { ++branchVisits; }
    void incrementTerminalRotations() { ++terminalRotations; }
    void incrementCandidateRuleVisits() { ++candidateRuleVisits; }
    void incrementDomainPairs() { ++domainPairs; }

    void incrementUnificationAttempts(boolean firstPass,
                                       boolean query,
                                       boolean generated) {
        ++unificationAttempts;
        if (firstPass) {
            if (query) {
                ++firstPassQueryUnifications;
            } else if (generated) {
                ++firstPassGeneratedUnifications;
            } else {
                ++firstPassDurableUnifications;
            }
        } else {
            if (query) {
                ++laterPassQueryUnifications;
            } else if (generated) {
                ++laterPassGeneratedUnifications;
            } else {
                ++laterPassDurableUnifications;
            }
        }
    }

    void incrementNewTValues() { ++newTValues; }
    void incrementDatabaseEvaluations() { ++databaseEvaluations; }
    void incrementFunctionEvaluations() { ++functionEvaluations; }

    public long getPasses() { return passes; }
    public long getRuleVisits() { return ruleVisits; }
    public long getBranchVisits() { return branchVisits; }
    public long getTerminalRotations() { return terminalRotations; }
    public long getCandidateRuleVisits() { return candidateRuleVisits; }
    public long getDomainPairs() { return domainPairs; }
    public long getUnificationAttempts() { return unificationAttempts; }
    public long getNewTValues() { return newTValues; }
    public long getDatabaseEvaluations() { return databaseEvaluations; }
    public long getFunctionEvaluations() { return functionEvaluations; }
    public long getFirstPassDurableUnifications() { return firstPassDurableUnifications; }
    public long getFirstPassQueryUnifications() { return firstPassQueryUnifications; }
    public long getFirstPassGeneratedUnifications() { return firstPassGeneratedUnifications; }
    public long getLaterPassDurableUnifications() { return laterPassDurableUnifications; }
    public long getLaterPassQueryUnifications() { return laterPassQueryUnifications; }
    public long getLaterPassGeneratedUnifications() { return laterPassGeneratedUnifications; }

    public void add(LinkerStatistics other) {
        if (other == null) {
            return;
        }
        passes += other.passes;
        ruleVisits += other.ruleVisits;
        branchVisits += other.branchVisits;
        terminalRotations += other.terminalRotations;
        candidateRuleVisits += other.candidateRuleVisits;
        domainPairs += other.domainPairs;
        unificationAttempts += other.unificationAttempts;
        newTValues += other.newTValues;
        databaseEvaluations += other.databaseEvaluations;
        functionEvaluations += other.functionEvaluations;
        firstPassDurableUnifications += other.firstPassDurableUnifications;
        firstPassQueryUnifications += other.firstPassQueryUnifications;
        firstPassGeneratedUnifications += other.firstPassGeneratedUnifications;
        laterPassDurableUnifications += other.laterPassDurableUnifications;
        laterPassQueryUnifications += other.laterPassQueryUnifications;
        laterPassGeneratedUnifications += other.laterPassGeneratedUnifications;
    }
}
'''

runner = '''/*
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
                    + "new_tvalues,database_evaluations,function_evaluations,"
                    + "first_pass_durable_unifications,first_pass_query_unifications,"
                    + "first_pass_generated_unifications,later_pass_durable_unifications,"
                    + "later_pass_query_unifications,later_pass_generated_unifications");
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
                "%d,%s,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
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
                statistics.getFunctionEvaluations(),
                statistics.getFirstPassDurableUnifications(),
                statistics.getFirstPassQueryUnifications(),
                statistics.getFirstPassGeneratedUnifications(),
                statistics.getLaterPassDurableUnifications(),
                statistics.getLaterPassQueryUnifications(),
                statistics.getLaterPassGeneratedUnifications());
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
'''

Path("kanger/src/org/kanger/LinkerStatistics.java").write_text(statistics, encoding="utf-8")
Path("kanger/src/org/kanger/KangerLinkerProfileRunner.java").write_text(runner, encoding="utf-8")

linker_path = Path("kanger/src/org/kanger/Linker.java")
linker = linker_path.read_text(encoding="utf-8")

old = "    private final LinkerStatistics statistics = new LinkerStatistics();\n"
new = old + "    private int currentPass = 0;\n"
if old not in linker or "private int currentPass" in linker:
    raise RuntimeError("Linker currentPass field anchor not found or already applied")
linker = linker.replace(old, new, 1)

old = "        statistics.reset();\n"
new = "        statistics.reset();\n        currentPass = 0;\n"
if old not in linker:
    raise RuntimeError("Linker statistics reset anchor not found")
linker = linker.replace(old, new, 1)

old = "            ++passCounter;\n            statistics.incrementPasses();\n"
new = "            ++passCounter;\n            currentPass = passCounter;\n            statistics.incrementPasses();\n"
if old not in linker:
    raise RuntimeError("Linker pass anchor not found")
linker = linker.replace(old, new, 1)

old = "                                statistics.incrementUnificationAttempts();\n"
new = "                                statistics.incrementUnificationAttempts(\n                                        currentPass == 1, rule.isQuery(), rule.isGenerated());\n"
if linker.count(old) != 1:
    raise RuntimeError("Linker unification anchor not unique")
linker = linker.replace(old, new, 1)

linker_path.write_text(linker, encoding="utf-8")
