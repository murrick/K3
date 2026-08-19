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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Qualification-only proof of the historical hypothesis optimization pipeline.
 *
 * <p>The production implementation is deliberately untouched here. This runner
 * restores the dormant abstractive formation policy, captures RAW hypotheses,
 * runs the current consistency-only optimizeHypothesis(), and then applies the
 * missing semantic phase in shadow form: KB + H must make the ORIGINAL query
 * determinate. The result is compared with an independent EXACT audit over RAW.</p>
 */
public final class KangerHypothesisCompletedOptimizeRunner {

    private static final String BAD = "?$x son(John,x);";
    private static final String GOOD = "?male(Tom);";

    private KangerHypothesisCompletedOptimizeRunner() {
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
        Path home = Files.createTempDirectory("kanger-completed-optimize-");
        System.setProperty("user.home", home.toAbsolutePath().toString());

        User user = (User) UserFactory.createUser(
                "completed-optimize", "completed-optimize");
        new UDF().init(user);
        new DB().init(user);

        inspect(user, BAD, 29, 6);
        inspect(user, GOOD, 12, 12);
        System.out.println("HYPOTHESIS_COMPLETED_OPTIMIZE_OK");
    }

    private static void inspect(IUser user,
                                String query,
                                int expectedRaw,
                                int expectedExact) throws Exception {
        Mind mind = prepared(user);
        enableAbstractiveHypothesis(mind);

        Boolean known = mind.query(query, null, false);
        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + known);
        }

        Set<String> raw = snapshot(mind);
        if (raw.size() != expectedRaw) {
            throw new AssertionError("Unexpected RAW hypothesis count for "
                    + query + ": expected " + expectedRaw
                    + ", actual " + raw.size());
        }

        Map<String, Boolean> exact = determined(mind, query, raw);
        if (exact.size() != expectedExact) {
            throw new AssertionError("Unexpected EXACT hypothesis count for "
                    + query + ": expected " + expectedExact
                    + ", actual " + exact.size());
        }

        mind.optimizeHypothesis();
        Set<String> legacyOptimized = snapshot(mind);

        if (!legacyOptimized.containsAll(exact.keySet())) {
            Set<String> missing = new LinkedHashSet<String>(exact.keySet());
            missing.removeAll(legacyOptimized);
            throw new AssertionError("Legacy consistency optimize removed EXACT "
                    + "hypotheses for " + query + ": " + missing);
        }

        Map<String, Boolean> completed = determined(
                mind, query, legacyOptimized);

        if (!completed.equals(exact)) {
            Set<String> missing = new LinkedHashSet<String>(exact.keySet());
            missing.removeAll(completed.keySet());
            Set<String> extra = new LinkedHashSet<String>(completed.keySet());
            extra.removeAll(exact.keySet());
            throw new AssertionError("Completed optimize differs from EXACT for "
                    + query + ": missing=" + missing + ", extra=" + extra);
        }

        System.out.printf("COMPLETED_OPTIMIZE_SUMMARY query=%s raw=%d legacyOptimized=%d completed=%d exact=%d equal=true%n",
                query,
                raw.size(),
                legacyOptimized.size(),
                completed.size(),
                exact.size());

        for (Map.Entry<String, Boolean> entry : completed.entrySet()) {
            System.out.printf("COMPLETED_OPTIMIZE_H query=%s answer=%s h=%s%n",
                    query,
                    entry.getValue().toString(),
                    entry.getKey());
        }
    }

    private static Map<String, Boolean> determined(Mind base,
                                                    String query,
                                                    Set<String> candidates)
            throws Exception {
        Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        for (String source : candidates) {
            Boolean answer = exact(base, query, source);
            if (answer != null) {
                result.put(source, answer);
            }
        }
        return result;
    }

    private static Boolean exact(Mind base, String query, String source)
            throws Exception {
        Mind child = new Mind(base);
        try {
            Rule rule = (Rule) child.compileLine(source, false, null);
            if (rule == null) {
                return null;
            }
            child.link(rule, false);
            boolean collision = child.analyze(rule, false);
            if (collision) {
                return null;
            }
            return child.query(query, null, false);
        } finally {
            base.release(child);
        }
    }

    private static Set<String> snapshot(Mind mind) throws Exception {
        Set<String> result = new LinkedHashSet<String>();
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            result.add(((Hypothesis) hypothesis).toString(mind));
        }
        return result;
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
}
