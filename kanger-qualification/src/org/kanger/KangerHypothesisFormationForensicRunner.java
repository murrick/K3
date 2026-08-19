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
 * observes the existing execution log and a passive residual-formation trace,
 * then correlates hypothesis birth with final raw admission, historical
 * optimizeHypothesis(), EXACT, TRACE and deferred SOLVE. The corpus is
 * intentionally limited to one known pathological query and one known-good
 * historical query.</p>
 */
public final class KangerHypothesisFormationForensicRunner {

    private static final String BAD = "?$x son(John,x);";
    private static final String GOOD = "?male(Tom);";

    private KangerHypothesisFormationForensicRunner() {
    }

    private static final class AnalyzeEpisode {
        private final String pass;
        private final int ordinal;
        private final Set<String> unresolved = new LinkedHashSet<>();
        private final Set<String> analyzerHypotheses = new LinkedHashSet<>();

        private AnalyzeEpisode(String pass, int ordinal) {
            this.pass = pass;
            this.ordinal = ordinal;
        }

        private String id() {
            return pass + "#" + ordinal;
        }
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
        QueryHypothesisFormationTrace.begin();
        Boolean known;
        QueryDemandTrace.Snapshot trace;
        QueryTaintSolve.Snapshot solve;
        QueryHypothesisFormationTrace.Snapshot formation;
        try {
            known = mind.query(query, null, true);
        } finally {
            formation = QueryHypothesisFormationTrace.end();
            solve = QueryTaintSolve.end();
            trace = QueryDemandTrace.end();
        }

        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + known);
        }

        List<ILogEntry> executionLog = copyLog(mind);
        List<IHypothesis> raw = copy(mind.getHypothesis());
        List<AnalyzeEpisode> episodes = parseEpisodes(executionLog);
        Map<String, Set<String>> origins = parseOrigins(executionLog, episodes);

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

        System.out.printf("FORENSIC_SUMMARY query=%s raw=%d optimized=%d exactRaw=%d exactOptimized=%d trace=%d solve=%d roots=%d edges=%d operations=%d tuples=%d observed=%d marked=%d errors=%d residualEvents=%d%n",
                query, raw.size(), optimized.size(), exactRaw.size(),
                exactOptimized.size(), traceCandidates.size(), solveCandidates.size(),
                trace.getQueryRootCount(), trace.getRecordedEdgeCount(),
                solve.getRelevantOperationCount(), solve.getRelevantTupleCount(),
                solve.getObservedHypothesisCount(), solve.getTaintedHypothesisCount(),
                solve.getInstrumentationErrorCount(), formation.getEvents().size());

        printEpisodes(query, episodes, rawText, exactRaw);
        printResiduals(query, formation, rawText, exactRaw);

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

    private static void printResiduals(String query,
                                       QueryHypothesisFormationTrace.Snapshot formation,
                                       Set<String> rawText,
                                       Map<String, Boolean> exactRaw) {
        Set<String> unique = new LinkedHashSet<>();
        Set<String> finalUnique = new LinkedHashSet<>();
        int queryDomainEvents = 0;
        int queryVariableEvents = 0;
        int generatedRuleEvents = 0;
        int queryRuleEvents = 0;

        for (QueryHypothesisFormationTrace.Event event : formation.getEvents()) {
            String hypothesis = event.getHypothesis();
            unique.add(hypothesis);
            if (rawText.contains(hypothesis)) {
                finalUnique.add(hypothesis);
            }
            if (event.hasBranchQueryDomain()) {
                ++queryDomainEvents;
            }
            if (event.hasBranchQueryVariable()) {
                ++queryVariableEvents;
            }
            if (event.isRuleGenerated()) {
                ++generatedRuleEvents;
            }
            if (event.isRuleQuery()) {
                ++queryRuleEvents;
            }

            Boolean exact = exactRaw.get(hypothesis);
            System.out.printf("FORENSIC_RESIDUAL query=%s pass=%s mind=%d rule=%d branch=%d branchSize=%d ruleQuery=%s ruleGenerated=%s branchQueryDomain=%s branchQueryVar=%s candidateQueryDomain=%s candidateQueryVar=%s rawFinal=%s exact=%s candidate=%s h=%s solve=%s branchState=%s%n",
                    query,
                    event.getPass(),
                    event.getMindId(),
                    event.getRuleId(),
                    event.getBranchIndex(),
                    event.getBranchSize(),
                    Boolean.toString(event.isRuleQuery()),
                    Boolean.toString(event.isRuleGenerated()),
                    Boolean.toString(event.hasBranchQueryDomain()),
                    Boolean.toString(event.hasBranchQueryVariable()),
                    Boolean.toString(event.hasCandidateQueryDomain()),
                    Boolean.toString(event.hasCandidateQueryVariable()),
                    Boolean.toString(rawText.contains(hypothesis)),
                    exact == null ? "WHO_KNOWS_OR_REJECTED" : exact.toString(),
                    event.getCandidate(),
                    hypothesis,
                    event.getSolve().toString(),
                    event.getBranchState().toString());
        }

        System.out.printf("FORENSIC_RESIDUAL_SUMMARY query=%s events=%d unique=%d finalUnique=%d queryDomainEvents=%d queryVariableEvents=%d queryRuleEvents=%d generatedRuleEvents=%d%n",
                query,
                formation.getEvents().size(),
                unique.size(),
                finalUnique.size(),
                queryDomainEvents,
                queryVariableEvents,
                queryRuleEvents,
                generatedRuleEvents);
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

    private static List<AnalyzeEpisode> parseEpisodes(List<ILogEntry> log) {
        List<AnalyzeEpisode> result = new ArrayList<>();
        Map<String, Integer> ordinals = new LinkedHashMap<>();
        String pass = "UNKNOWN";
        AnalyzeEpisode current = null;

        for (ILogEntry entry : log) {
            String record = entry.getRecord().trim();
            if (record.contains("FALSE CHECKING")) {
                pass = "FALSE";
                current = null;
                continue;
            }
            if (record.contains("TRUE CHECKING")) {
                pass = "TRUE";
                current = null;
                continue;
            }
            if (record.contains("============= ANALYZER")) {
                int ordinal = ordinals.containsKey(pass) ? ordinals.get(pass) + 1 : 1;
                ordinals.put(pass, ordinal);
                current = new AnalyzeEpisode(pass, ordinal);
                result.add(current);
                continue;
            }
            if (current == null) {
                continue;
            }
            if (record.startsWith("Unresolved:")) {
                current.unresolved.add(afterColon(record));
            } else if (record.startsWith("Hypothesis assumed:")) {
                current.analyzerHypotheses.add(afterColon(record));
            }
        }
        return result;
    }

    private static Map<String, Set<String>> parseOrigins(List<ILogEntry> log,
                                                          List<AnalyzeEpisode> episodes) {
        Map<String, Set<String>> result = new LinkedHashMap<>();

        for (AnalyzeEpisode episode : episodes) {
            for (String hypothesis : episode.analyzerHypotheses) {
                addOrigin(result, hypothesis, "ANALYZER_GLOBAL@" + episode.id());
            }
        }

        String pass = "UNKNOWN";
        for (ILogEntry entry : log) {
            String record = entry.getRecord().trim();
            if (record.contains("FALSE CHECKING")) {
                pass = "FALSE";
            } else if (record.contains("TRUE CHECKING")) {
                pass = "TRUE";
            } else if (record.startsWith("Hypothesis alternate assumed:")) {
                addOrigin(result, afterColon(record), "LINKER_RESIDUAL@" + pass);
            } else if (record.startsWith("Hypothesis moved:")) {
                // Hypothesis moved is written to the parent log before the
                // child TRUE-pass log is committed, so temporal pass parsing
                // cannot reliably label this event. Keep the origin neutral.
                addOrigin(result, afterColon(record), "FINAL_MOVE");
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

    private static void printEpisodes(String query,
                                      List<AnalyzeEpisode> episodes,
                                      Set<String> rawText,
                                      Map<String, Boolean> exactRaw) {
        for (AnalyzeEpisode episode : episodes) {
            int rawFinal = 0;
            int exactRelevant = 0;
            for (String hypothesis : episode.analyzerHypotheses) {
                if (rawText.contains(hypothesis)) {
                    ++rawFinal;
                }
                if (exactRaw.containsKey(hypothesis)) {
                    ++exactRelevant;
                }
            }

            System.out.printf("FORENSIC_EPISODE query=%s pass=%s analyze=%d unresolved=%d analyzerBorn=%d rawFinal=%d exactRelevant=%d%n",
                    query, episode.pass, episode.ordinal, episode.unresolved.size(),
                    episode.analyzerHypotheses.size(), rawFinal, exactRelevant);

            for (String unresolved : episode.unresolved) {
                System.out.printf("FORENSIC_ORPHAN query=%s episode=%s obligation=%s%n",
                        query, episode.id(), unresolved);
            }
            for (String hypothesis : episode.analyzerHypotheses) {
                Boolean exact = exactRaw.get(hypothesis);
                System.out.printf("FORENSIC_EPISODE_H query=%s episode=%s rawFinal=%s exact=%s h=%s%n",
                        query, episode.id(), Boolean.toString(rawText.contains(hypothesis)),
                        exact == null ? "WHO_KNOWS_OR_REJECTED" : exact.toString(),
                        hypothesis);
            }
        }
    }

    private static void printInterestingLog(List<ILogEntry> log) {
        String pass = "UNKNOWN";
        int analyze = 0;
        int index = 0;
        for (ILogEntry entry : log) {
            String record = entry.getRecord().trim();
            if (record.contains("FALSE CHECKING")) {
                pass = "FALSE";
                analyze = 0;
            } else if (record.contains("TRUE CHECKING")) {
                pass = "TRUE";
                analyze = 0;
            } else if (record.contains("============= ANALYZER")) {
                ++analyze;
            }
            if (interesting(record)) {
                System.out.printf("FORENSIC_LOG %04d pass=%s analyze=%d type=%s %s%n",
                        index, pass, analyze, entry.getType(), record);
            }
            ++index;
        }
    }

    private static boolean interesting(String record) {
        return record.contains("FALSE CHECKING")
                || record.contains("TRUE CHECKING")
                || record.contains("============= ANALYZER")
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
