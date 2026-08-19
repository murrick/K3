/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.lang.reflect.Field;
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
 * Diagnostic-only comparison of demand selection over historical abstractive
 * RAW hypotheses, before and after the historical consistency optimizer.
 * EXACT remains semantic authority and no selector feeds back into inference.
 */
public final class KangerHypothesisAbstractiveDemandRunner {

    private static final String BAD = "?$x son(John,x);";
    private static final String GOOD = "?male(Tom);";

    private KangerHypothesisAbstractiveDemandRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            run();
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    public static void run() throws Exception {
        Path home = Files.createTempDirectory("kanger-abstractive-demand-");
        System.setProperty("user.home", home.toAbsolutePath().toString());

        User user = (User) UserFactory.createUser(
                "abstractive-demand", "abstractive-demand");
        new UDF().init(user);
        new DB().init(user);

        inspect(user, BAD, 6);
        inspect(user, GOOD, 12);
        System.out.println("HYPOTHESIS_ABSTRACTIVE_DEMAND_OK");
    }

    private static void inspect(IUser user,
                                String query,
                                int expectedExact) throws Exception {
        Mind mind = prepared(user);
        enableAbstractiveHypothesis(mind);

        QueryDemandTrace.begin();
        QueryTaintSolve.begin();
        Boolean known;
        QueryDemandTrace.Snapshot traceSnapshot;
        QueryTaintSolve.Snapshot solveSnapshot;
        try {
            known = mind.query(query, null, false);
        } finally {
            solveSnapshot = QueryTaintSolve.end();
            traceSnapshot = QueryDemandTrace.end();
        }

        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + known);
        }

        List<IHypothesis> raw = copy(mind.getHypothesis());
        long exactAllStart = System.nanoTime();
        Map<String, Boolean> exactAll = exact(mind, query, raw);
        long exactAllNanos = System.nanoTime() - exactAllStart;

        if (exactAll.size() != expectedExact) {
            throw new AssertionError("Unexpected abstractive EXACT cardinality for "
                    + query + ": expected " + expectedExact + ", got "
                    + exactAll.size() + " -> " + exactAll);
        }

        Selection rawSelection = selections(
                mind, query, raw, traceSnapshot, solveSnapshot, exactAll);
        printSelection("RAW", query, raw.size(), exactAll,
                rawSelection, traceSnapshot, solveSnapshot,
                exactAllNanos, -1L);

        long optimizeStart = System.nanoTime();
        mind.optimizeHypothesis();
        long optimizeNanos = System.nanoTime() - optimizeStart;
        List<IHypothesis> optimized = copy(mind.getHypothesis());

        Set<String> optimizedText = textSet(mind, optimized);
        Set<String> optimizeMissed = new LinkedHashSet<String>(exactAll.keySet());
        optimizeMissed.removeAll(optimizedText);

        Selection optimizedSelection = selections(
                mind, query, optimized, traceSnapshot, solveSnapshot, exactAll);
        printSelection("OPT", query, optimized.size(), exactAll,
                optimizedSelection, traceSnapshot, solveSnapshot,
                exactAllNanos, optimizeNanos);

        System.out.printf("ABSTRACTIVE_OPTIMIZE_SUMMARY query=%s raw=%d optimized=%d reduced=%d exact=%d optimizeFN=%d optimizeMs=%.3f%n",
                query,
                raw.size(),
                optimized.size(),
                raw.size() - optimized.size(),
                exactAll.size(),
                optimizeMissed.size(),
                optimizeNanos / 1_000_000.0);
        if (!optimizeMissed.isEmpty()) {
            System.out.printf("ABSTRACTIVE_OPTIMIZE_MISSED query=%s missed=%s%n",
                    query, optimizeMissed.toString());
        }

        for (IHypothesis hypothesis : raw) {
            String text = ((Hypothesis) hypothesis).toString(mind);
            if (!exactAll.containsKey(text)) {
                continue;
            }
            System.out.printf("ABSTRACTIVE_DEMAND_EXACT_H query=%s answer=%s oldOptimize=%s rawTrace=%s rawSolve=%s rawSolveAlt=%s rawTraceSolve=%s optTrace=%s optSolve=%s optSolveAlt=%s optTraceSolve=%s h=%s%n",
                    query,
                    exactAll.get(text).toString(),
                    Boolean.toString(optimizedText.contains(text)),
                    Boolean.toString(rawSelection.trace.text.contains(text)),
                    Boolean.toString(rawSelection.solve.text.contains(text)),
                    Boolean.toString(rawSelection.solveAlt.text.contains(text)),
                    Boolean.toString(rawSelection.traceSolve.text.contains(text)),
                    Boolean.toString(optimizedSelection.trace.text.contains(text)),
                    Boolean.toString(optimizedSelection.solve.text.contains(text)),
                    Boolean.toString(optimizedSelection.solveAlt.text.contains(text)),
                    Boolean.toString(optimizedSelection.traceSolve.text.contains(text)),
                    text);
        }
    }

    private static Selection selections(Mind mind,
                                        String query,
                                        List<IHypothesis> source,
                                        QueryDemandTrace.Snapshot traceSnapshot,
                                        QueryTaintSolve.Snapshot solveSnapshot,
                                        Map<String, Boolean> exactAll)
            throws Exception {
        List<IHypothesis> trace = traceSnapshot.selectCandidates(mind, source);
        List<IHypothesis> solve = solveSnapshot.selectCandidates(mind, source);
        List<IHypothesis> solveAlt = QueryTaintSolveAlternatives.expand(
                mind, query, source, solve);
        List<IHypothesis> traceSolve = union(mind, trace, solve);
        List<IHypothesis> traceSolveAlt = union(mind, trace, solveAlt);

        return new Selection(
                audit(mind, exactAll, trace),
                audit(mind, exactAll, solve),
                audit(mind, exactAll, solveAlt),
                audit(mind, exactAll, traceSolve),
                audit(mind, exactAll, traceSolveAlt));
    }

    private static void printSelection(String stage,
                                       String query,
                                       int sourceCount,
                                       Map<String, Boolean> exactAll,
                                       Selection s,
                                       QueryDemandTrace.Snapshot traceSnapshot,
                                       QueryTaintSolve.Snapshot solveSnapshot,
                                       long exactAllNanos,
                                       long optimizeNanos) {
        System.out.printf("ABSTRACTIVE_DEMAND_SUMMARY stage=%s query=%s source=%d exact=%d trace=%d traceFound=%d traceFP=%d traceFN=%d solve=%d solveFound=%d solveFP=%d solveFN=%d solveAlt=%d solveAltFound=%d solveAltFP=%d solveAltFN=%d traceSolve=%d traceSolveFound=%d traceSolveFP=%d traceSolveFN=%d traceSolveAlt=%d traceSolveAltFound=%d traceSolveAltFP=%d traceSolveAltFN=%d roots=%d edges=%d operations=%d tuples=%d exactAllMs=%.3f optimizeMs=%.3f%n",
                stage,
                query,
                sourceCount,
                exactAll.size(),
                s.trace.size, s.trace.found, s.trace.falsePositives,
                s.trace.missed.size(),
                s.solve.size, s.solve.found, s.solve.falsePositives,
                s.solve.missed.size(),
                s.solveAlt.size, s.solveAlt.found, s.solveAlt.falsePositives,
                s.solveAlt.missed.size(),
                s.traceSolve.size, s.traceSolve.found,
                s.traceSolve.falsePositives, s.traceSolve.missed.size(),
                s.traceSolveAlt.size, s.traceSolveAlt.found,
                s.traceSolveAlt.falsePositives,
                s.traceSolveAlt.missed.size(),
                traceSnapshot.getQueryRootCount(),
                traceSnapshot.getRecordedEdgeCount(),
                solveSnapshot.getRelevantOperationCount(),
                solveSnapshot.getRelevantTupleCount(),
                exactAllNanos / 1_000_000.0,
                optimizeNanos < 0 ? -1.0 : optimizeNanos / 1_000_000.0);

        printMisses(stage, query, "TRACE", s.trace);
        printMisses(stage, query, "SOLVE", s.solve);
        printMisses(stage, query, "SOLVE_ALT", s.solveAlt);
        printMisses(stage, query, "TRACE_SOLVE", s.traceSolve);
        printMisses(stage, query, "TRACE_SOLVE_ALT", s.traceSolveAlt);
    }

    private static CandidateAudit audit(Mind mind,
                                        Map<String, Boolean> exactAll,
                                        Collection<IHypothesis> candidates) {
        Set<String> selected = textSet(mind, candidates);
        int found = 0;
        for (String exact : exactAll.keySet()) {
            if (selected.contains(exact)) {
                ++found;
            }
        }
        Set<String> missed = new LinkedHashSet<String>(exactAll.keySet());
        missed.removeAll(selected);
        return new CandidateAudit(selected.size(), found,
                selected.size() - found, missed, selected);
    }

    private static void printMisses(String stage,
                                    String query,
                                    String label,
                                    CandidateAudit audit) {
        if (!audit.missed.isEmpty()) {
            System.out.printf("ABSTRACTIVE_DEMAND_MISSED stage=%s query=%s selector=%s missed=%s%n",
                    stage, query, label, audit.missed.toString());
        }
    }

    private static List<IHypothesis> union(Mind mind,
                                           Collection<IHypothesis> left,
                                           Collection<IHypothesis> right) {
        Map<String, IHypothesis> result =
                new LinkedHashMap<String, IHypothesis>();
        for (IHypothesis hypothesis : left) {
            result.put(((Hypothesis) hypothesis).toString(mind), hypothesis);
        }
        for (IHypothesis hypothesis : right) {
            result.put(((Hypothesis) hypothesis).toString(mind), hypothesis);
        }
        return new ArrayList<IHypothesis>(result.values());
    }

    private static List<IHypothesis> copy(Iterable<IHypothesis> source) {
        List<IHypothesis> result = new ArrayList<IHypothesis>();
        for (IHypothesis hypothesis : source) {
            result.add(hypothesis);
        }
        return result;
    }

    private static Set<String> textSet(Mind mind,
                                       Collection<IHypothesis> source) {
        Set<String> result = new LinkedHashSet<String>();
        for (IHypothesis hypothesis : source) {
            result.add(((Hypothesis) hypothesis).toString(mind));
        }
        return result;
    }

    private static Map<String, Boolean> exact(Mind base,
                                               String query,
                                               Collection<IHypothesis> candidates)
            throws Exception {
        Map<String, Boolean> relevant = new LinkedHashMap<String, Boolean>();
        for (IHypothesis candidate : candidates) {
            String source = ((Hypothesis) candidate).toString(base);
            Mind child = new Mind(base);
            try {
                Rule rule = (Rule) child.compileLine(source, false, null);
                if (rule == null) {
                    continue;
                }
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

    private static void enableAbstractiveHypothesis(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("includeAbstractiveHypothesis");
        field.setAccessible(true);
        field.setBoolean(mind, true);
    }

    private static Mind prepared(IUser user) throws Exception {
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        String source = new String(
                Files.readAllBytes(Paths.get("natives.k")),
                StandardCharsets.UTF_8);
        if (!mind.compile(source)) {
            throw new AssertionError("natives.k compilation rejected");
        }
        return mind;
    }

    private static final class Selection {
        private final CandidateAudit trace;
        private final CandidateAudit solve;
        private final CandidateAudit solveAlt;
        private final CandidateAudit traceSolve;
        private final CandidateAudit traceSolveAlt;

        private Selection(CandidateAudit trace,
                          CandidateAudit solve,
                          CandidateAudit solveAlt,
                          CandidateAudit traceSolve,
                          CandidateAudit traceSolveAlt) {
            this.trace = trace;
            this.solve = solve;
            this.solveAlt = solveAlt;
            this.traceSolve = traceSolve;
            this.traceSolveAlt = traceSolveAlt;
        }
    }

    private static final class CandidateAudit {
        private final int size;
        private final int found;
        private final int falsePositives;
        private final Set<String> missed;
        private final Set<String> text;

        private CandidateAudit(int size,
                               int found,
                               int falsePositives,
                               Set<String> missed,
                               Set<String> text) {
            this.size = size;
            this.found = found;
            this.falsePositives = falsePositives;
            this.missed = missed;
            this.text = text;
        }
    }
}
