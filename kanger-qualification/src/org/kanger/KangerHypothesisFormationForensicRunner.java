/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.ILogEntry;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diagnostic-only archaeology of the historical hypothesis formation path.
 *
 * <p>This runner deliberately changes no production selection semantics. It
 * observes the existing human-readable execution log, where Linker and Analyzer
 * already expose distinct hypothesis birth messages, and correlates those birth
 * events with the final raw store, historical optimizeHypothesis(), EXACT, TRACE
 * and deferred SOLVE. The first corpus is intentionally limited to one known
 * pathological query and one known-good historical query.</p>
 */
public final class KangerHypothesisFormationForensicRunner {

    private static final String BAD = "?$x son(John,x);";
    private static final String GOOD = "?male(Tom);";

    private KangerHypothesisFormationForensicRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path home = Files.createTempDirectory("kanger-hypothesis-formation-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            User user = (User) UserFactory.createUser(
                    "hypothesis-formation", "hypothesis-formation");
            new UDF().init(user);
            new DB().init(user);

            inspect(user, BAD, 0);
            inspect(user, GOOD, 10);

            System.out.println("HYPOTHESIS_FORMATION_FORENSIC_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void inspect(IUser user, String query, int expectedExact)
            throws Exception {
        Mind mind = prepared(user);

        QueryDemandTrace.begin();
        QueryTaintSolve.begin();
        Boolean known;
        QueryDemandTrace.Snapshot trace;
        QueryTaintSolve.Snapshot solve;
        try {
            known = mind.query(query, null, true);
        } finally {
            solve = QueryTaintSolve.end();
            trace = QueryDemandTrace.end();
        }

        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + known);
        }

        List<ILogEntry> executionLog = copyLog(mind);
        List<IHypothesis> raw = copy(mind.getHypothesis());
        Map<String, Set<String>> origins = parseOrigins(executionLog);

        System.out.println("FORENSIC_BEGIN " + query);
        printInterestingLog(executionLog);

        mind.optimizeHypothesis();
        List<IHypothesis> optimized = copy(mind.getHypothesis());

        Map<String, Boolean> exactRaw = exact(mind, query, raw);
        Map<String, Boolean> exactOptimized = exact(mind, query, optimized);

        if (exactOptimized.size() != expectedExact) {
            throw new AssertionError("Unexpected optimized EXACT cardinality for "
                    + query + ": expected " + expectedExact + ", got "
                    + exactOptimized.size() + " -> " + exactOptimized);
        }

        List<IHypothesis> traceCandidates = trace.selectCandidates(mind, optimized);
        List<IHypothesis> solveCandidates = solve.selectCandidates(mind, optimized);

        Set<String> rawText = textSet(mind, raw);
        Set<String> optimizedText = textSet(mind, optimized);
        Set<String> rows = new LinkedHashSet<>();
        rows.addAll(origins.keySet());
        rows.addAll(rawText);

        System.out.printf("FORENSIC_SUMMARY query=%s raw=%d optimized=%d exactRaw=%d exactOptimized=%d trace=%d solve=%d roots=%d edges=%d operations=%d tuples=%d observed=%d marked=%d errors=%d%n",
                query, raw.size(), optimized.size(), exactRaw.size(),
                exactOptimized.size(), traceCandidates.size(), solveCandidates.size(),
                trace.getQueryRootCount(), trace.getRecordedEdgeCount(),
                solve.getRelevantOperationCount(), solve.getRelevantTupleCount(),
                solve.getObservedHypothesisCount(), solve.getTaintedHypothesisCount(),
                solve.getInstrumentationErrorCount());

        for (String hypothesis : rows) {
            Set<String> source = origins.get(hypothesis);
            Boolean exact = exactRaw.get(hypothesis);
            System.out.printf("FORENSIC_HYPOTHESIS query=%s rawFinal=%s oldOptimize=%s exact=%s origins=%s h=%s%n",
                    query,
                    Boolean.toString(rawText.contains(hypothesis)),
                    Boolean.toString(optimizedText.contains(hypothesis)),
                    exact == null ? "WHO_KNOWS_OR_REJECTED" : exact.toString(),
                    source == null ? "[]" : source.toString(),
                    hypothesis);
        }

        Set<String> exactLostByOptimize = new LinkedHashSet<>(exactRaw.keySet());
        exactLostByOptimize.removeAll(exactOptimized.keySet());
        if (!exactLostByOptimize.isEmpty()) {
            throw new AssertionError("Historical optimize removed EXACT-relevant hypotheses for "
                    + query + ": " + exactLostByOptimize);
        }

        System.out.println("FORENSIC_END " + query);
    }

    private static Mind prepared(IUser user) throws Exception {
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        String source = new String(Files.readAllBytes(Paths.get("natives.k")),
                StandardCharsets.UTF_8);
        if (!mind.compile(source)) {
            throw new AssertionError("natives.k compilation rejected");
        }
        return mind;
    }

    private static List<ILogEntry> copyLog(Mind mind) {
        List<ILogEntry> result = new ArrayList<>();
        for (ILogEntry entry : mind.getLog()) {
            result.add(entry);
        }
        return result;
    }

    private static Map<String, Set<String>> parseOrigins(List<ILogEntry> log) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        String pass = "UNKNOWN";
        for (ILogEntry entry : log) {
            String record = entry.getRecord().trim();
            if (record.contains("FALSE CHECKING")) {
                pass = "FALSE";
            } else if (record.contains("TRUE CHECKING")) {
                pass = "TRUE";
            } else if (record.startsWith("Hypothesis alternate assumed:")) {
                addOrigin(result, afterColon(record), "LINKER_RESIDUAL@" + pass);
            } else if (record.startsWith("Hypothesis assumed:")) {
                addOrigin(result, afterColon(record), "ANALYZER_GLOBAL@" + pass);
            } else if (record.startsWith("Hypothesis moved:")) {
                addOrigin(result, afterColon(record), "FINAL_MOVE@" + pass);
            }
        }
        return result;
    }

    private static void addOrigin(Map<String, Set<String>> map,
                                  String hypothesis,
                                  String origin) {
        Set<String> origins = map.get(hypothesis);
        if (origins == null) {
            origins = new LinkedHashSet<>();
            map.put(hypothesis, origins);
        }
        origins.add(origin);
    }

    private static String afterColon(String record) {
        int index = record.indexOf(':');
        return index < 0 ? record.trim() : record.substring(index + 1).trim();
    }

    private static void printInterestingLog(List<ILogEntry> log) {
        String pass = "UNKNOWN";
        int index = 0;
        for (ILogEntry entry : log) {
            String record = entry.getRecord().trim();
            if (record.contains("FALSE CHECKING")) {
                pass = "FALSE";
            } else if (record.contains("TRUE CHECKING")) {
                pass = "TRUE";
            }
            if (interesting(record)) {
                System.out.printf("FORENSIC_LOG %04d pass=%s type=%s %s%n",
                        index, pass, entry.getType(), record);
            }
            ++index;
        }
    }

    private static boolean interesting(String record) {
        return record.contains("FALSE CHECKING")
                || record.contains("TRUE CHECKING")
                || record.startsWith("Unresolved:")
                || record.startsWith("From right:")
                || record.startsWith("Acceptor:")
                || record.startsWith("Donor")
                || record.startsWith("DB assumed record")
                || record.startsWith("DB add record")
                || record.startsWith("Hypothesis assumed:")
                || record.startsWith("Hypothesis alternate assumed:")
                || record.startsWith("Hypothesis moved:")
                || record.startsWith("Calculated coincidence:")
                || record.startsWith("Database coincidence:");
    }

    private static List<IHypothesis> copy(Iterable<IHypothesis> source) {
        List<IHypothesis> result = new ArrayList<>();
        for (IHypothesis hypothesis : source) {
            result.add(hypothesis);
        }
        return result;
    }

    private static Set<String> textSet(Mind mind, Collection<IHypothesis> source) {
        Set<String> result = new LinkedHashSet<>();
        for (IHypothesis hypothesis : source) {
            result.add(((Hypothesis) hypothesis).toString(mind));
        }
        return result;
    }

    private static Map<String, Boolean> exact(Mind base,
                                               String query,
                                               Collection<IHypothesis> candidates)
            throws Exception {
        Map<String, Boolean> relevant = new LinkedHashMap<>();
        for (IHypothesis candidate : candidates) {
            String source = ((Hypothesis) candidate).toString(base);
            Mind child = new Mind(base);
            try {
                Rule rule = (Rule) child.compileLine(source, false, null);
                child.link(rule, false);
                boolean collision = child.analyze(rule, false);
                if (!collision) {
                    Boolean answer = child.query(query, null, false);
                    if (answer != null) {
                        relevant.put(source, answer);
                    }
                }
            } finally {
                base.release(child);
            }
        }
        return relevant;
    }
}
