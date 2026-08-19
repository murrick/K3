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
 * Diagnostic-only comparison of query-demand selection over the historical
 * abstractive RAW hypothesis formation path.
 *
 * <p>Abstractive hypotheses are enabled only in this qualification Mind.
 * TRACE, SOLVE and SOLVE_ALT remain shadow selectors. EXACT is the semantic
 * authority and no selector feeds back into inference or production query
 * behavior.</p>
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
        List<IHypothesis> trace = traceSnapshot.selectCandidates(mind, raw);
        List<IHypothesis> solve = solveSnapshot.selectCandidates(mind, raw);
        List<IHypothesis> solveAlt = QueryTaintSolveAlternatives.expand(
                mind, query, raw, solve);
        List<IHypothesis> traceSolve = union(mind, trace, solve);
        List<IHypothesis> traceSolveAlt = union(mind, trace, solveAlt);

        long exactAllStart = System.nanoTime();
        Map<String, Boolean> exactAll = exact(mind, query, raw);
        long exactAllNanos = System.nanoTime() - exactAllStart;

        if (exactAll.size() != expectedExact) {
            throw new AssertionError("Unexpected abstractive EXACT cardinality for "
                    + query + ": expected " + expectedExact + ", got "
                    + exactAll.size() + " -> " + exactAll);
        }

        Audit traceAudit = audit(mind, query, exactAll, trace);
        Audit solveAudit = audit(mind, query, exactAll, solve);
        Audit solveAltAudit = audit(mind, query, exactAll, solveAlt);
        Audit traceSolveAudit = audit(mind, query, exactAll, traceSolve);
        Audit traceSolveAltAudit = audit(mind, query, exactAll, traceSolveAlt);

        System.out.printf("ABSTRACTIVE_DEMAND_SUMMARY query=%s raw=%d exact=%d trace=%d traceFound=%d traceFP=%d traceFN=%d solve=%d solveFound=%d solveFP=%d solveFN=%d solveAlt=%d solveAltFound=%d solveAltFP=%d solveAltFN=%d traceSolve=%d traceSolveFound=%d traceSolveFP=%d traceSolveFN=%d traceSolveAlt=%d traceSolveAltFound=%d traceSolveAltFP=%d traceSolveAltFN=%d roots=%d edges=%d operations=%d tuples=%d exactAllMs=%.3f%n",
                query,
                raw.size(),
                exactAll.size(),
                trace.size(), traceAudit.found, traceAudit.falsePositives,
                traceAudit.missed.size(),
                solve.size(), solveAudit.found, solveAudit.falsePositives,
                solveAudit.missed.size(),
                solveAlt.size(), solveAltAudit.found, solveAltAudit.falsePositives,
                solveAltAudit.missed.size(),
                traceSolve.size(), traceSolveAudit.found,
                traceSolveAudit.falsePositives, traceSolveAudit.missed.size(),
                traceSolveAlt.size(), traceSolveAltAudit.found,
                traceSolveAltAudit.falsePositives,
                traceSolveAltAudit.missed.size(),
                traceSnapshot.getQueryRootCount(),
                traceSnapshot.getRecordedEdgeCount(),
                solveSnapshot.getRelevantOperationCount(),
                solveSnapshot.getRelevantTupleCount(),
                exactAllNanos / 1_000_000.0);

        printMisses(query, "TRACE", traceAudit);
        printMisses(query, "SOLVE", solveAudit);
        printMisses(query, "SOLVE_ALT", solveAltAudit);
        printMisses(query, "TRACE_SOLVE", traceSolveAudit);
        printMisses(query, "TRACE_SOLVE_ALT", traceSolveAltAudit);

        Set<String> exactText = exactAll.keySet();
        for (IHypothesis hypothesis : raw) {
            String text = ((Hypothesis) hypothesis).toString(mind);
            if (!exactText.contains(text)) {
                continue;
            }
            System.out.printf("ABSTRACTIVE_DEMAND_EXACT_H query=%s answer=%s trace=%s solve=%s solveAlt=%s traceSolveAlt=%s h=%s%n",
                    query,
                    exactAll.get(text).toString(),
                    Boolean.toString(contains(mind, trace, text)),
                    Boolean.toString(contains(mind, solve, text)),
                    Boolean.toString(contains(mind, solveAlt, text)),
                    Boolean.toString(contains(mind, traceSolveAlt, text)),
                    text);
        }
    }

    private static Audit audit(Mind mind,
                               String query,
                               Map<String, Boolean> exactAll,
                               Collection<IHypothesis> candidates)
            throws Exception {
        Map<String, Boolean> exactSelected = exact(mind, query, candidates);
        Set<String> missed = new LinkedHashSet<String>(exactAll.keySet());
        missed.removeAll(exactSelected.keySet());
        return new Audit(exactSelected.size(),
                candidates.size() - exactSelected.size(), missed);
    }

    private static void printMisses(String query, String label, Audit audit) {
        if (!audit.missed.isEmpty()) {
            System.out.printf("ABSTRACTIVE_DEMAND_MISSED query=%s selector=%s missed=%s%n",
                    query, label, audit.missed.toString());
        }
    }

    private static List<IHypothesis> union(Mind mind,
                                            Collection<IHypothesis> left,
                                            Collection<IHypothesis> right) {
        Map<String, IHypothesis> result = new LinkedHashMap<String, IHypothesis>();
        for (IHypothesis hypothesis : left) {
            result.put(((Hypothesis) hypothesis).toString(mind), hypothesis);
        }
        for (IHypothesis hypothesis : right) {
            result.put(((Hypothesis) hypothesis).toString(mind), hypothesis);
        }
        return new ArrayList<IHypothesis>(result.values());
    }

    private static boolean contains(Mind mind,
                                    Collection<IHypothesis> candidates,
                                    String text) {
        for (IHypothesis hypothesis : candidates) {
            if (text.equals(((Hypothesis) hypothesis).toString(mind))) {
                return true;
            }
        }
        return false;
    }

    private static List<IHypothesis> copy(Iterable<IHypothesis> source) {
        List<IHypothesis> result = new ArrayList<IHypothesis>();
        for (IHypothesis hypothesis : source) {
            result.add(hypothesis);
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

    private static final class Audit {
        private final int found;
        private final int falsePositives;
        private final Set<String> missed;

        private Audit(int found, int falsePositives, Set<String> missed) {
            this.found = found;
            this.falsePositives = falsePositives;
            this.missed = missed;
        }
    }
}
