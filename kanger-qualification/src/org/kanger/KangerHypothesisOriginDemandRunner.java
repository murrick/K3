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
 * Diagnostic-only test of origin-aware demand routing over the historical
 * abstractive hypothesis mechanism.
 *
 * <p>Analyzer hypotheses are complete/materialized repairs and are routed
 * through TRACE. Temporary Linker residual alternatives promoted by
 * {@code Hypothesis moved} are routed through SOLVE_ALT. EXACT remains the
 * semantic authority. Nothing in this runner feeds back into inference.</p>
 */
public final class KangerHypothesisOriginDemandRunner {

    private static final String BAD = "?$x son(John,x);";
    private static final String GOOD = "?male(Tom);";

    private KangerHypothesisOriginDemandRunner() {
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
        Path home = Files.createTempDirectory("kanger-origin-demand-");
        System.setProperty("user.home", home.toAbsolutePath().toString());

        User user = (User) UserFactory.createUser(
                "origin-demand", "origin-demand");
        new UDF().init(user);
        new DB().init(user);

        inspect(user, BAD, 6);
        inspect(user, GOOD, 12);
        System.out.println("HYPOTHESIS_ORIGIN_DEMAND_OK");
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
            known = mind.query(query, null, true);
        } finally {
            solveSnapshot = QueryTaintSolve.end();
            traceSnapshot = QueryDemandTrace.end();
        }
        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + known);
        }

        List<IHypothesis> raw = copy(mind.getHypothesis());
        Map<String, Origins> origins = parseOrigins(mind.getLog());

        mind.optimizeHypothesis();
        List<IHypothesis> optimized = copy(mind.getHypothesis());

        List<IHypothesis> trace = traceSnapshot.selectCandidates(mind, optimized);
        List<IHypothesis> solve = solveSnapshot.selectCandidates(mind, optimized);
        List<IHypothesis> solveAlt = QueryTaintSolveAlternatives.expand(
                mind, query, optimized, solve);

        Set<String> traceText = textSet(mind, trace);
        Set<String> solveAltText = textSet(mind, solveAlt);

        List<IHypothesis> routed = new ArrayList<IHypothesis>();
        int analyzerFinal = 0;
        int linkerFinal = 0;
        int unknownFinal = 0;
        for (IHypothesis hypothesis : optimized) {
            String text = ((Hypothesis) hypothesis).toString(mind);
            Origins birth = origins.get(text);
            FinalOrigin finalOrigin = finalOrigin(birth);
            boolean selected;
            switch (finalOrigin) {
                case LINKER_RESIDUAL:
                    ++linkerFinal;
                    selected = solveAltText.contains(text);
                    break;
                case ANALYZER:
                    ++analyzerFinal;
                    selected = traceText.contains(text);
                    break;
                default:
                    ++unknownFinal;
                    selected = false;
                    break;
            }
            if (selected) {
                routed.add(hypothesis);
            }
        }

        long exactStart = System.nanoTime();
        Map<String, Boolean> exactAll = exact(mind, query, optimized);
        long exactNanos = System.nanoTime() - exactStart;
        if (exactAll.size() != expectedExact) {
            throw new AssertionError("Unexpected origin-demand EXACT cardinality for "
                    + query + ": expected " + expectedExact + ", got "
                    + exactAll.size() + " -> " + exactAll);
        }

        Audit traceAudit = audit(mind, exactAll, trace);
        Audit solveAltAudit = audit(mind, exactAll, solveAlt);
        Audit routedAudit = audit(mind, exactAll, routed);

        System.out.printf("ORIGIN_DEMAND_SUMMARY query=%s raw=%d optimized=%d exact=%d finalAnalyzer=%d finalLinker=%d finalUnknown=%d trace=%d traceFound=%d traceFN=%d solveAlt=%d solveAltFound=%d solveAltFN=%d routed=%d routedFound=%d routedFP=%d routedFN=%d exactMs=%.3f%n",
                query,
                raw.size(),
                optimized.size(),
                exactAll.size(),
                analyzerFinal,
                linkerFinal,
                unknownFinal,
                traceAudit.size, traceAudit.found, traceAudit.missed.size(),
                solveAltAudit.size, solveAltAudit.found,
                solveAltAudit.missed.size(),
                routedAudit.size, routedAudit.found,
                routedAudit.falsePositives, routedAudit.missed.size(),
                exactNanos / 1_000_000.0);

        if (!routedAudit.missed.isEmpty()) {
            System.out.printf("ORIGIN_DEMAND_MISSED query=%s missed=%s%n",
                    query, routedAudit.missed.toString());
        }

        for (IHypothesis hypothesis : optimized) {
            String text = ((Hypothesis) hypothesis).toString(mind);
            if (!exactAll.containsKey(text)) {
                continue;
            }
            Origins birth = origins.get(text);
            FinalOrigin finalOrigin = finalOrigin(birth);
            System.out.printf("ORIGIN_DEMAND_EXACT_H query=%s answer=%s finalOrigin=%s analyzerBorn=%s linkerBorn=%s moved=%s trace=%s solveAlt=%s routed=%s h=%s%n",
                    query,
                    exactAll.get(text).toString(),
                    finalOrigin.name(),
                    Boolean.toString(birth != null && birth.analyzer),
                    Boolean.toString(birth != null && birth.linker),
                    Boolean.toString(birth != null && birth.moved),
                    Boolean.toString(traceText.contains(text)),
                    Boolean.toString(solveAltText.contains(text)),
                    Boolean.toString(contains(mind, routed, text)),
                    text);
        }
    }

    private static Map<String, Origins> parseOrigins(Iterable<ILogEntry> log) {
        Map<String, Origins> result = new LinkedHashMap<String, Origins>();
        for (ILogEntry entry : log) {
            String record = entry.getRecord().trim();
            if (record.startsWith("Hypothesis assumed:")) {
                origin(result, afterColon(record)).analyzer = true;
            } else if (record.startsWith("Hypothesis alternate assumed:")) {
                origin(result, afterColon(record)).linker = true;
            } else if (record.startsWith("Hypothesis moved:")) {
                origin(result, afterColon(record)).moved = true;
            }
        }
        return result;
    }

    private static Origins origin(Map<String, Origins> map, String hypothesis) {
        Origins origins = map.get(hypothesis);
        if (origins == null) {
            origins = new Origins();
            map.put(hypothesis, origins);
        }
        return origins;
    }

    private static FinalOrigin finalOrigin(Origins origins) {
        if (origins == null) {
            return FinalOrigin.UNKNOWN;
        }
        if (origins.moved) {
            return FinalOrigin.LINKER_RESIDUAL;
        }
        if (origins.analyzer) {
            return FinalOrigin.ANALYZER;
        }
        if (origins.linker) {
            return FinalOrigin.LINKER_RESIDUAL;
        }
        return FinalOrigin.UNKNOWN;
    }

    private static String afterColon(String record) {
        int colon = record.indexOf(':');
        return colon < 0 ? record.trim() : record.substring(colon + 1).trim();
    }

    private static Audit audit(Mind mind,
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
        return new Audit(selected.size(), found,
                selected.size() - found, missed);
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

    private static Set<String> textSet(Mind mind,
                                       Collection<IHypothesis> source) {
        Set<String> result = new LinkedHashSet<String>();
        for (IHypothesis hypothesis : source) {
            result.add(((Hypothesis) hypothesis).toString(mind));
        }
        return result;
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

    private enum FinalOrigin {
        ANALYZER,
        LINKER_RESIDUAL,
        UNKNOWN
    }

    private static final class Origins {
        private boolean analyzer;
        private boolean linker;
        private boolean moved;
    }

    private static final class Audit {
        private final int size;
        private final int found;
        private final int falsePositives;
        private final Set<String> missed;

        private Audit(int size, int found, int falsePositives,
                      Set<String> missed) {
            this.size = size;
            this.found = found;
            this.falsePositives = falsePositives;
            this.missed = missed;
        }
    }
}
