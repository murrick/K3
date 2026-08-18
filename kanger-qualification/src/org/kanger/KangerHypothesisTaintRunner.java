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
 * Shadow qualification for hypothesis relevance prefilters.
 *
 * <p>EXACT remains semantic authority. TRACE, the original occurrence TAINT,
 * and the concrete substitution CARRIER are independent conservative filters;
 * none changes production WHEN behavior. Every filter must preserve
 * {@code exactRelevant subsetOf candidates}.</p>
 */
public final class KangerHypothesisTaintRunner {

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

    private KangerHypothesisTaintRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path home = Files.createTempDirectory("kanger-hypothesis-taint-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            User user = (User) UserFactory.createUser(
                    "hypothesis-taint", "hypothesis-taint");
            new UDF().init(user);
            new DB().init(user);

            verify(user, "?$y son(John,y);", 0, true);
            verify(user, "?male(Tom);", 10, false);
            verify(user, "?female(Tom);", 10, false);
            verify(user, "?spouse(Mary,John);", 1, false);
            verify(user, "?spouse(John,Tom);", 0, true);
            verify(user, "?$x male(x) && age(x,12);", 6, false);
            System.out.println("HYPOTHESIS_TAINT_OK");
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
        QueryTaint.begin();
        QueryTaintCarrier.begin();
        Boolean result;
        QueryDemandTrace.Snapshot trace;
        QueryTaint.Snapshot taint;
        QueryTaintCarrier.Snapshot carrier;
        try {
            result = mind.query(query, null, false);
        } finally {
            carrier = QueryTaintCarrier.end();
            taint = QueryTaint.end();
            trace = QueryDemandTrace.end();
        }
        if (result != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + result);
        }

        mind.optimizeHypothesis();
        List<IHypothesis> legacy = copy(mind.getHypothesis());
        List<IHypothesis> traced = trace.selectCandidates(mind, legacy);
        List<IHypothesis> tainted = taint.selectCandidates(mind, legacy);
        List<IHypothesis> carried = carrier.selectCandidates(mind, legacy);

        long exactAllStart = System.nanoTime();
        Map<String, Boolean> exactAll = exact(mind, query, legacy);
        long exactAllNanos = System.nanoTime() - exactAllStart;

        long exactTraceStart = System.nanoTime();
        Map<String, Boolean> exactTrace = exact(mind, query, traced);
        long exactTraceNanos = System.nanoTime() - exactTraceStart;

        long exactTaintStart = System.nanoTime();
        Map<String, Boolean> exactTaint = exact(mind, query, tainted);
        long exactTaintNanos = System.nanoTime() - exactTaintStart;

        long exactCarrierStart = System.nanoTime();
        Map<String, Boolean> exactCarrier = exact(mind, query, carried);
        long exactCarrierNanos = System.nanoTime() - exactCarrierStart;

        if (exactAll.size() != expectedExact) {
            throw new AssertionError("Unexpected EXACT cardinality for " + query
                    + ": expected " + expectedExact + ", got " + exactAll.size()
                    + " -> " + exactAll);
        }

        assertNoMisses("TRACE", query, exactAll, exactTrace);
        assertNoMisses("TAINT", query, exactAll, exactTaint);
        assertNoMisses("CARRIER", query, exactAll, exactCarrier);

        if (requireReduction && traced.size() >= legacy.size()) {
            throw new AssertionError("TRACE did not reduce legacy list for "
                    + query + ": " + legacy.size());
        }
        if (requireReduction && tainted.size() >= legacy.size()) {
            throw new AssertionError("TAINT did not reduce legacy list for "
                    + query + ": " + legacy.size());
        }
        if (requireReduction && carried.size() >= legacy.size()) {
            throw new AssertionError("CARRIER did not reduce legacy list for "
                    + query + ": " + legacy.size());
        }

        System.out.printf("HYPOTHESIS_TAINT_PASS %s legacy=%d trace=%d taint=%d carrier=%d exact=%d traceRoots=%d traceEdges=%d taintRoots=%d taintUnifications=%d taintGround=%d taintObserved=%d taintMarked=%d taintErrors=%d carrierRoots=%d carrierCauses=%d carrierGroundBridges=%d carrierBindings=%d carrierGround=%d carrierObserved=%d carrierMarked=%d carrierErrors=%d exactAllMs=%.3f exactTraceMs=%.3f exactTaintMs=%.3f exactCarrierMs=%.3f%n",
                query, legacy.size(), traced.size(), tainted.size(), carried.size(), exactAll.size(),
                trace.getQueryRootCount(), trace.getRecordedEdgeCount(),
                taint.getQueryRootCount(), taint.getRelevantUnificationCount(),
                taint.getGroundBridgeCount(), taint.getObservedHypothesisCount(),
                taint.getTaintedHypothesisCount(), taint.getInstrumentationErrorCount(),
                carrier.getQueryRootCount(), carrier.getRelevantCauseCount(),
                carrier.getGroundBridgeCount(), carrier.getRelevantBindingCount(),
                carrier.getRelevantGroundCount(), carrier.getObservedHypothesisCount(),
                carrier.getTaintedHypothesisCount(), carrier.getInstrumentationErrorCount(),
                exactAllNanos / 1_000_000.0,
                exactTraceNanos / 1_000_000.0,
                exactTaintNanos / 1_000_000.0,
                exactCarrierNanos / 1_000_000.0);
    }

    private static void assertNoMisses(String label, String query,
                                       Map<String, Boolean> exactAll,
                                       Map<String, Boolean> exactFiltered) {
        Set<String> missed = new LinkedHashSet<>(exactAll.keySet());
        missed.removeAll(exactFiltered.keySet());
        if (!missed.isEmpty()) {
            throw new AssertionError(label + " false negatives for " + query
                    + ": " + missed);
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
