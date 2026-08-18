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
 * Shadow qualification for hypothesis relevance.
 *
 * <p>TRACE is a conservative in-inference prefilter. EXACT remains the semantic
 * oracle: add one hypothesis to an isolated child Mind and rerun the original
 * query. This runner proves only that the selected corpus has no TRACE false
 * negatives; it does not change production WHEN behavior.</p>
 */
public final class KangerHypothesisRelevanceRunner {

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

    private KangerHypothesisRelevanceRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path home = Files.createTempDirectory("kanger-hypothesis-relevance-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            User user = (User) UserFactory.createUser(
                    "hypothesis-relevance", "hypothesis-relevance");
            new UDF().init(user);
            new DB().init(user);

            verify(user, "?$y son(John,y);", 0, true);
            verify(user, "?male(Tom);", 10, false);
            verify(user, "?female(Tom);", 10, false);
            verify(user, "?spouse(Mary,John);", 1, false);
            verify(user, "?spouse(John,Tom);", 0, true);
            verify(user, "?$x male(x) && age(x,12);", 6, false);
            System.out.println("HYPOTHESIS_RELEVANCE_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void verify(IUser user, String query,
                               int expectedExact,
                               boolean requireReduction) throws Exception {
        Mind mind = prepared(user);

        QueryDemandTrace.begin();
        Boolean result;
        QueryDemandTrace.Snapshot trace;
        try {
            result = mind.query(query, null, false);
        } finally {
            trace = QueryDemandTrace.end();
        }
        if (result != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + result);
        }

        mind.optimizeHypothesis();
        List<IHypothesis> legacy = copy(mind.getHypothesis());
        List<IHypothesis> traced = trace.selectCandidates(mind, legacy);

        long exactAllStart = System.nanoTime();
        Map<String, Boolean> exactAll = exact(mind, query, legacy);
        long exactAllNanos = System.nanoTime() - exactAllStart;

        long exactTraceStart = System.nanoTime();
        Map<String, Boolean> exactTrace = exact(mind, query, traced);
        long exactTraceNanos = System.nanoTime() - exactTraceStart;

        if (exactAll.size() != expectedExact) {
            throw new AssertionError("Unexpected EXACT cardinality for " + query
                    + ": expected " + expectedExact + ", got " + exactAll.size()
                    + " -> " + exactAll);
        }

        Set<String> missed = new LinkedHashSet<>(exactAll.keySet());
        missed.removeAll(exactTrace.keySet());
        if (!missed.isEmpty()) {
            throw new AssertionError("TRACE false negatives for " + query
                    + ": " + missed);
        }
        if (requireReduction && traced.size() >= legacy.size()) {
            throw new AssertionError("TRACE did not reduce legacy list for "
                    + query + ": " + legacy.size());
        }

        System.out.printf("HYPOTHESIS_RELEVANCE_PASS %s legacy=%d trace=%d exact=%d roots=%d edges=%d exactAllMs=%.3f exactTraceMs=%.3f%n",
                query, legacy.size(), traced.size(), exactAll.size(),
                trace.getQueryRootCount(), trace.getRecordedEdgeCount(),
                exactAllNanos / 1_000_000.0, exactTraceNanos / 1_000_000.0);
    }

    private static Mind prepared(IUser user) throws Exception {
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        if (!mind.compile(SOURCE)) {
            throw new AssertionError("Qualification source compilation rejected");
        }
        return mind;
    }

    private static List<IHypothesis> copy(Iterable<IHypothesis> source) {
        List<IHypothesis> copy = new ArrayList<>();
        for (IHypothesis hypothesis : source) {
            copy.add(hypothesis);
        }
        return copy;
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
                    Boolean result = child.query(query, null, false);
                    if (result != null) {
                        relevant.put(source, result);
                    }
                }
            } finally {
                base.release(child);
            }
        }
        return relevant;
    }
}
