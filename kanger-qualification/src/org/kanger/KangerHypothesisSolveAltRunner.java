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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Focused qualification for deferred SOLVE relevance plus one-shot terminal
 * alternative closure seeded by the original query demand.
 *
 * <p>EXACT remains semantic authority. Raw SOLVE is reported independently so
 * the qualification distinguishes provenance misses from the narrow semantic
 * alternative closure. Production WHEN never reads either shadow result.</p>
 */
public final class KangerHypothesisSolveAltRunner {

    private static final String SOURCE =
            "!@x $y parent(y,x);" +
            "!@x ~parent(x,x);" +
            "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
            "!@x @y daughter(x,y) -> female(x), child(x,y);" +
            "!@x @y son(x,y) -> male(x), child(x,y);" +
            "!@x @y father(x,y) -> male(x), parent(x,y);" +
            "!@x @y mother(x,y) -> female(x), parent(x,y);" +
            "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
            "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
            "!@x @y ~(parent(x,y), parent(y,x));" +
            "!@x @y ($z parent(z,x) && parent(z,y)) && x != y -> sibling(x,y);" +
            "!@x @y ~(sibling(x,y), parent(x,y));" +
            "!@x @y sibling(x,y) -> sibling(y,x);" +
            "!@x @y ($z parent(x,z), parent(y,z)), x != y -> spouse(x,y) || divorced(x,y);" +
            "!father(John, Tom);" +
            "!daughter(Sarah, John);" +
            "!mother(Mary,Sarah);" +
            "!child(Tom,Mary);" +
            "!age(John, 37);" +
            "!age(Tom, 12);" +
            "!age(Sarah, 4);";

    private KangerHypothesisSolveAltRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path home = Files.createTempDirectory("kanger-hypothesis-solve-alt-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            User user = (User) UserFactory.createUser("hypothesis-solve-alt", "hypothesis-solve-alt");
            new UDF().init(user);
            new DB().init(user);

            verify(user, "?$y son(John,y);", 0);
            verify(user, "?male(Tom);", 10);
            verify(user, "?female(Tom);", 10);
            verify(user, "?spouse(Mary,John);", 1);
            verify(user, "?spouse(John,Tom);", 0);
            verify(user, "?$x male(x) && age(x,12);", 6);

            discover(user, "?son(Tom,John);");
            discover(user, "?daughter(Tom,John);");
            discover(user, "?$x son(x,John);");
            discover(user, "?$x daughter(x,John);");
            discover(user, "?$x spouse(x,John);");
            discover(user, "?$x spouse(John,x);");
            discover(user, "?divorced(John,Tom);");
            discover(user, "?$x male(x) && parent(John,x);");
            discover(user, "?$x female(x) && parent(John,x);");
            discover(user, "?$x sibling(x,Tom);");

            System.out.println("HYPOTHESIS_SOLVE_ALT_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void verify(IUser user, String query, int expectedExact)
            throws Exception {
        Result result = evaluate(user, query);
        if (result.known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + result.known);
        }
        if (result.exactAll.size() != expectedExact) {
            throw new AssertionError("Unexpected EXACT cardinality for " + query
                    + ": expected " + expectedExact + ", got "
                    + result.exactAll.size() + " -> " + result.exactAll);
        }
        assertNoMisses("SOLVE_ALT", query, result.exactAll, result.exactAlt);
        print("HYPOTHESIS_SOLVE_ALT_PASS", query, result);
    }

    private static void discover(IUser user, String query) throws Exception {
        Result result = evaluate(user, query);
        if (result.known != null) {
            System.out.printf("HYPOTHESIS_SOLVE_ALT_KNOWN %s result=%s roots=%d operations=%d tuples=%d errors=%d%n",
                    query, result.known.toString(), result.snapshot.getQueryRootCount(),
                    result.snapshot.getRelevantOperationCount(),
                    result.snapshot.getRelevantTupleCount(),
                    result.snapshot.getInstrumentationErrorCount());
            return;
        }
        assertNoMisses("SOLVE_ALT-DISCOVERY", query,
                result.exactAll, result.exactAlt);
        print("HYPOTHESIS_SOLVE_ALT_DISCOVERY", query, result);
    }

    private static Result evaluate(IUser user, String query) throws Exception {
        Mind mind = prepared(user);
        QueryTaintSolve.begin();
        Boolean known;
        QueryTaintSolve.Snapshot snapshot;
        try {
            known = mind.query(query, null, false);
        } finally {
            snapshot = QueryTaintSolve.end();
        }

        if (known != null) {
            return Result.known(known, snapshot);
        }

        mind.optimizeHypothesis();
        List<IHypothesis> legacy = copy(mind.getHypothesis());
        List<IHypothesis> solve = snapshot.selectCandidates(mind, legacy);
        List<IHypothesis> solveAlt = QueryTaintSolveAlternatives.expand(
                mind, query, legacy, solve);

        long exactAllStart = System.nanoTime();
        Map<String, Boolean> exactAll = exact(mind, query, legacy);
        long exactAllNanos = System.nanoTime() - exactAllStart;

        long exactSolveStart = System.nanoTime();
        Map<String, Boolean> exactSolve = exact(mind, query, solve);
        long exactSolveNanos = System.nanoTime() - exactSolveStart;

        long exactAltStart = System.nanoTime();
        Map<String, Boolean> exactAlt = exact(mind, query, solveAlt);
        long exactAltNanos = System.nanoTime() - exactAltStart;

        return Result.unknown(snapshot, legacy.size(), solve.size(), solveAlt.size(),
                exactAll, exactSolve, exactAlt,
                exactAllNanos, exactSolveNanos, exactAltNanos);
    }

    private static void print(String label, String query, Result result) {
        Set<String> rawMissed = missed(result.exactAll, result.exactSolve);
        System.out.printf("%s %s legacy=%d solve=%d solveAlt=%d solveReduced=%s solveAltReduced=%s rawSolveMissed=%d exact=%d roots=%d operations=%d contributions=%d tuples=%d rules=%d groundBridges=%d ground=%d observed=%d marked=%d errors=%d exactAllMs=%.3f exactSolveMs=%.3f exactAltMs=%.3f%n",
                label, query, result.legacy, result.solve, result.solveAlt,
                Boolean.toString(result.solve < result.legacy),
                Boolean.toString(result.solveAlt < result.legacy),
                rawMissed.size(), result.exactAll.size(),
                result.snapshot.getQueryRootCount(),
                result.snapshot.getRelevantOperationCount(),
                result.snapshot.getDeferredContributionCount(),
                result.snapshot.getRelevantTupleCount(),
                result.snapshot.getRelevantRuleCount(),
                result.snapshot.getGroundBridgeCount(),
                result.snapshot.getRelevantGroundCount(),
                result.snapshot.getObservedHypothesisCount(),
                result.snapshot.getTaintedHypothesisCount(),
                result.snapshot.getInstrumentationErrorCount(),
                result.exactAllNanos / 1_000_000.0,
                result.exactSolveNanos / 1_000_000.0,
                result.exactAltNanos / 1_000_000.0);
        if (!rawMissed.isEmpty()) {
            System.out.println("HYPOTHESIS_SOLVE_ALT_RAW_MISSED " + query + " " + rawMissed);
        }
    }

    private static Set<String> missed(Map<String, Boolean> exactAll,
                                      Map<String, Boolean> exactFiltered) {
        Set<String> result = new LinkedHashSet<>(exactAll.keySet());
        result.removeAll(exactFiltered.keySet());
        return result;
    }

    private static void assertNoMisses(String label, String query,
                                       Map<String, Boolean> exactAll,
                                       Map<String, Boolean> exactFiltered) {
        Set<String> result = missed(exactAll, exactFiltered);
        if (!result.isEmpty()) {
            throw new AssertionError(label + " false negatives for " + query
                    + ": " + result);
        }
    }

    private static Mind prepared(IUser user) throws Exception {
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        if (!mind.compile(SOURCE)) {
            throw new AssertionError("Qualification source compilation rejected");
        }
        return mind;
    }

    private static List<IHypothesis> copy(Iterable<IHypothesis> source) {
        List<IHypothesis> result = new ArrayList<>();
        for (IHypothesis hypothesis : source) {
            result.add(hypothesis);
        }
        return result;
    }

    private static Map<String, Boolean> exact(Mind base, String query,
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

    private static final class Result {
        private final Boolean known;
        private final QueryTaintSolve.Snapshot snapshot;
        private final int legacy;
        private final int solve;
        private final int solveAlt;
        private final Map<String, Boolean> exactAll;
        private final Map<String, Boolean> exactSolve;
        private final Map<String, Boolean> exactAlt;
        private final long exactAllNanos;
        private final long exactSolveNanos;
        private final long exactAltNanos;

        private Result(Boolean known,
                       QueryTaintSolve.Snapshot snapshot,
                       int legacy,
                       int solve,
                       int solveAlt,
                       Map<String, Boolean> exactAll,
                       Map<String, Boolean> exactSolve,
                       Map<String, Boolean> exactAlt,
                       long exactAllNanos,
                       long exactSolveNanos,
                       long exactAltNanos) {
            this.known = known;
            this.snapshot = snapshot;
            this.legacy = legacy;
            this.solve = solve;
            this.solveAlt = solveAlt;
            this.exactAll = exactAll;
            this.exactSolve = exactSolve;
            this.exactAlt = exactAlt;
            this.exactAllNanos = exactAllNanos;
            this.exactSolveNanos = exactSolveNanos;
            this.exactAltNanos = exactAltNanos;
        }

        private static Result known(Boolean known, QueryTaintSolve.Snapshot snapshot) {
            return new Result(known, snapshot, 0, 0, 0,
                    new LinkedHashMap<String, Boolean>(),
                    new LinkedHashMap<String, Boolean>(),
                    new LinkedHashMap<String, Boolean>(), 0L, 0L, 0L);
        }

        private static Result unknown(QueryTaintSolve.Snapshot snapshot,
                                      int legacy,
                                      int solve,
                                      int solveAlt,
                                      Map<String, Boolean> exactAll,
                                      Map<String, Boolean> exactSolve,
                                      Map<String, Boolean> exactAlt,
                                      long exactAllNanos,
                                      long exactSolveNanos,
                                      long exactAltNanos) {
            return new Result(null, snapshot, legacy, solve, solveAlt,
                    exactAll, exactSolve, exactAlt,
                    exactAllNanos, exactSolveNanos, exactAltNanos);
        }
    }
}
