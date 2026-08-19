/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.test;

import org.kanger.Diagnostics;
import org.kanger.Mind;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IMind;
import org.kanger.primitives.Hypothesis;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.LinkedHashSet;

/**
 * Completed-hypothesis regression overlay for the historical KangerTest corpus.
 *
 * <p>The historical source remains untouched as an archaeological baseline.
 * Exactly two old tests have legitimate completed-semantics deltas. Their
 * overrides run every original assertion first and accept only the historical
 * final size failure before asserting the new abstract repairs. Any other old
 * failure is propagated unchanged.</p>
 */
public final class KangerCompletedTest extends KangerTest {

    public KangerCompletedTest(IMind mind) {
        super(mind);
    }

    public static boolean test(IMind mind, String prefix) throws Exception {
        System.out.println("Init completed test system...");
        int successCount = 0;
        long startTime = System.currentTimeMillis();
        List<String> fails = new ArrayList<>();
        Map<String, Double> list = new TreeMap<>();
        String dbName = mind.getStorageName();
        try {
            if (mind.isStorageUsed()) {
                mind = mind.closeStorage();
                mind = mind.useStorage("data/auto-test");
                mind = mind.clearWorkspace();
            }

            mind = mind.clearWorkspace();
            KangerCompletedTest cls = new KangerCompletedTest(mind);
            cls.setUp();

            for (Method method : KangerTest.class.getDeclaredMethods()) {
                if (method.getName().startsWith(prefix)) {
                    list.put(method.getName(), 0.0);
                }
            }
            for (Method method : KangerCompletedTest.class.getDeclaredMethods()) {
                if (method.getName().startsWith(prefix)) {
                    list.put(method.getName(), 0.0);
                }
            }

            System.out.println("Done.");
            System.out.println("----------------------------------------------------");

            for (String name : list.keySet()) {
                try {
                    System.out.println("Testing: " + name);
                    long t = System.currentTimeMillis();
                    Diagnostics.resetStorageCounters(cls.mind);
                    if (Diagnostics.isEnabled(cls.mind)) {
                        System.out.println(Diagnostics.snapshot(cls.mind,
                                "before " + name));
                    }
                    Method method = cls.getClass().getMethod(name);
                    try (Diagnostics.Watchdog watchdog =
                                 Diagnostics.watch(name, cls.mind)) {
                        method.invoke(cls);
                    }
                    if (Diagnostics.isEnabled(cls.mind)) {
                        System.out.println(Diagnostics.snapshot(cls.mind,
                                "after " + name));
                    }
                    double timing = (System.currentTimeMillis() - t) / 1000.0;
                    System.out.println("Timing: " + timing + " sec");
                    System.out.println("====================================================");
                    list.put(name, timing);
                    ++successCount;
                } catch (Exception e) {
                    fails.add(name);
                    System.err.println(new Date());
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    cause.printStackTrace(System.err);
                }
            }

            for (Map.Entry<String, Double> e : list.entrySet()) {
                System.out.println(e.getKey() + "\t" + e.getValue() + " sec");
            }
            if (!fails.isEmpty()) {
                System.out.println("====================================================");
                System.out.println("Fails:");
                for (String s : fails) {
                    System.out.println(s);
                }
            }
            System.out.println("====================================================");
            System.out.println(" Timing: "
                    + ((System.currentTimeMillis() - startTime) / 1000.0));
            System.out.println("Success: " + successCount);
            System.out.println("  Fails: " + fails.size());
        } finally {
            if (mind.isStorageUsed()) {
                mind.removeStorage(null);
                try {
                    mind = mind.useStorage(dbName);
                } catch (RuntimeErrorException e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                }
            }
        }
        return fails.isEmpty();
    }

    @Override
    public void set_06_07() throws Exception {
        expectHistoricalSizeFailure("Expected 12 hypothesis",
                new CheckedCall() {
                    @Override
                    public void run() throws Exception {
                        KangerCompletedTest.super.set_06_07();
                    }
                });

        require(mind.getHypothesis().size() == 14,
                "Expected 14 hypothesis");
        Set<String> hypotheses = sources();
        require(hypotheses.contains("!$y mother(Tom,y);"),
                "Expected abstract hypothesis: !$y mother(Tom,y);");
        require(hypotheses.contains("!$y father(Tom,y);"),
                "Expected abstract hypothesis: !$y father(Tom,y);");
        System.out.println("Completed hypothesis migration: 12 -> 14 OK");
        System.out.println("====================================================");
    }

    @Override
    public void set_06_0A() throws Exception {
        expectHistoricalSizeFailure("Expected 7 hypothesis",
                new CheckedCall() {
                    @Override
                    public void run() throws Exception {
                        KangerCompletedTest.super.set_06_0A();
                    }
                });

        require(mind.getHypothesis().size() == 8,
                "Expected 8 hypothesis");
        Set<String> hypotheses = sources();
        require(hypotheses.contains("!$y father(Tom,y);"),
                "Expected abstract hypothesis: !$y father(Tom,y);");
        System.out.println("Completed hypothesis migration: 7 -> 8 OK");
        System.out.println("====================================================");
    }

    public void set_06_0F() throws Exception {
        mind = mind.clearWorkspace();
        String source = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("natives.k")),
                java.nio.charset.StandardCharsets.UTF_8);
        require(mind.compile(source), "natives.k compilation rejected");

        Boolean result = mind.query("?$x son(John,x);");
        System.out.println("Query: " + mind.getQueryString());
        System.out.println("Result: " + mind.getQueryResult());
        require(result == null, "Expected WHO KNOWS for ?$x son(John,x);");

        int rawSize = mind.getHypothesis().size();
        long optimizeStart = System.nanoTime();
        mind.optimizeHypothesis();
        double optimizeSeconds = (System.nanoTime() - optimizeStart) / 1_000_000_000.0;

        System.out.println("Hypothesis RAW (" + rawSize + ") -> optimized ("
                + mind.getHypothesis().size() + "):");
        int i = 0;
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            System.out.printf("\t%3d:\t%s%n", ++i,
                    ((Hypothesis) hypothesis).toString((Mind) mind));
        }
        System.out.printf("Hypothesis optimize timing: %.3f sec%n", optimizeSeconds);

        require(mind.getHypothesis().size() == 6,
                "Expected 6 completed hypotheses for ?$x son(John,x);");
        System.out.println("Completed hypothesis showcase: son(John,x) -> 6 OK");
        System.out.println("====================================================");
    }

    private void expectHistoricalSizeFailure(String expected,
                                             CheckedCall call) throws Exception {
        try {
            call.run();
            throw new RuntimeErrorException(
                    "FAIL: Historical hypothesis expectation unexpectedly passed: "
                            + expected);
        } catch (RuntimeErrorException error) {
            if (!("Runtime error: FAIL: " + expected).equals(error.toString())) {
                throw error;
            }
        }
    }

    private Set<String> sources() throws Exception {
        Set<String> result = new LinkedHashSet<>();
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            result.add(((Hypothesis) hypothesis).toString((Mind) mind));
        }
        return result;
    }

    private static void require(boolean condition, String message)
            throws RuntimeErrorException {
        if (!condition) {
            throw new RuntimeErrorException("FAIL: " + message);
        }
    }

    private interface CheckedCall {
        void run() throws Exception;
    }
}
