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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Completed-hypothesis regression overlay for the historical KangerTest corpus.
 *
 * <p>The historical source remains untouched as an archaeological baseline.
 * This overlay owns only the intentional completed-WHEN semantic migrations:
 * public hypotheses are executable assertions, and an optimized hypothesis is
 * retained only when accepting it makes the original query TRUE. Historical
 * counter-hypotheses that merely made a query determinate FALSE remain visible
 * only in the old source, not in the completed oracle.</p>
 */
public final class KangerCompletedTest extends KangerTest {

    private static final String CHAIN_SOURCE =
            "!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); "
                    + "!@x a(x) -> ~n(x); "
                    + "!a(nnn); "
                    + "!b(ooo); "
                    + "!d(v);";

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
    public void set_01_03() throws Exception {
        assertSolutionHypotheses(CHAIN_SOURCE, "?a(xx);");
    }

    @Override
    public void set_01_04() throws Exception {
        assertSolutionHypotheses(CHAIN_SOURCE, "?b(xx);",
                "!a(xx);");
    }

    @Override
    public void set_01_05() throws Exception {
        assertSolutionHypotheses(CHAIN_SOURCE, "?c(xx);",
                "!a(xx);", "!b(xx);");
    }

    @Override
    public void set_01_07() throws Exception {
        assertSolutionHypotheses(CHAIN_SOURCE, "?n(xx);");
    }

    @Override
    public void set_06_07() throws Exception {
        assertSolutionHypotheses(nativeSource(), "?male(Tom);",
                "!~daughter(Tom,John);",
                "!~daughter(Tom,Mary);",
                "!~female(Tom);",
                "!son(Tom,Mary);",
                "!son(Tom,John);",
                "!$y father(Tom,y);");
        System.out.println("Completed hypothesis solution oracle: male(Tom) -> 6 OK");
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
        require(mind.compile(nativeSource()), "natives.k compilation rejected");

        Boolean result = mind.query("?$x son(John,x);");
        System.out.println("Query: " + mind.getQueryString());
        System.out.println("Result: " + mind.getQueryResult());
        require(result == null, "Expected WHO KNOWS for ?$x son(John,x);");

        int rawSize = mind.getHypothesis().size();
        require(rawSize == 18,
                "Expected 18 visible RAW hypotheses for ?$x son(John,x); got " + rawSize);
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

        require(mind.getHypothesis().isEmpty(),
                "Expected no single assertion hypothesis to solve ?$x son(John,x);");
        System.out.println("Completed hypothesis solution oracle: son(John,x) -> 0 OK");
        System.out.println("====================================================");
    }

    private void assertSolutionHypotheses(String source,
                                          String query,
                                          String... expected) throws Exception {
        mind = mind.clearWorkspace();
        require(mind.compile(source), "Hypothesis fixture compilation rejected");
        Boolean result = mind.query(query);
        System.out.println("Query: " + mind.getQueryString());
        System.out.println("Result: " + mind.getQueryResult());
        require(result == null, "Expected WHO KNOWS for " + query);

        mind.optimizeHypothesis();
        Set<String> actual = sources();
        Set<String> wanted = new LinkedHashSet<>();
        for (String item : expected) {
            wanted.add(item);
        }
        System.out.println("Hypothesis solutions (" + actual.size() + "): " + actual);
        require(actual.equals(wanted),
                "Hypothesis solution mismatch for " + query
                        + ": expected=" + wanted + " actual=" + actual);
        verifySolutions(query);
    }

    private void verifySolutions(String query) throws Exception {
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            String assertion = ((Hypothesis) hypothesis).toAssertionString((Mind) mind);
            Mind child = new Mind(mind);
            try {
                require(Boolean.TRUE.equals(child.query(assertion, null, false)),
                        "Hypothesis assertion rejected: " + assertion);
                require(Boolean.TRUE.equals(child.query(query, null, false)),
                        "Hypothesis assertion does not solve query " + query
                                + ": " + assertion);
            } finally {
                ((Mind) mind).release(child);
            }
        }
    }

    private String nativeSource() throws Exception {
        return new String(Files.readAllBytes(Paths.get("natives.k")),
                StandardCharsets.UTF_8);
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
