/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies the API rendering contract for solution rows created from external
 * parameters. Rule.origin deliberately retains the original '?' template,
 * while IRule.toString(IMind) and Rule.createMap(IMind).text must expose the
 * materialized Domain arguments both after parallel commits and after DUMB
 * close/reopen with lazy hydration.
 */
public final class KangerRuleRenderingRunner {

    private static final int THREADS = 3;
    private static final int VALUES_PER_THREAD = 8;

    private KangerRuleRenderingRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            Path home = Files.createTempDirectory("kanger-rule-rendering-");
            System.setProperty("user.home", home.toAbsolutePath().toString());
            System.out.println("Rule rendering home: " + home.toAbsolutePath());

            runScenario(false);
            runScenario(true);

            System.out.println("RULE_RENDERING_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void runScenario(boolean persistent) throws Exception {
        String suffix = persistent ? "persistent" : "memory";
        String userName = "rule-rendering-" + suffix;
        IUser user = UserFactory.createUser(userName, userName);
        new UDF().init(user);
        new DB().init(user);

        Mind root = new Mind(user);
        String storageName = "data/rule-rendering-" + suffix;
        if (persistent) {
            root = (Mind) root.useStorage(storageName);
        }
        root = (Mind) root.clearWorkspace();

        Set<String> expected = populateInParallel(root);
        assertRows(root, expected, suffix + "-live");

        if (persistent) {
            root = (Mind) root.closeStorage();
            root = (Mind) root.useStorage(storageName);
            assertRows(root, expected, suffix + "-reopen");
        }

        System.out.println("RULE_RENDERING_PASS " + suffix);
    }

    private static Set<String> populateInParallel(final Mind root) throws Exception {
        final CountDownLatch ready = new CountDownLatch(THREADS);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREADS);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Set<String> expected = new LinkedHashSet<>();

        Thread[] workers = new Thread[THREADS];
        for (int threadIndex = 0; threadIndex < THREADS; ++threadIndex) {
            final int worker = threadIndex;
            final Mind child = new Mind(root);
            workers[threadIndex] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ready.countDown();
                        start.await();
                        for (int i = 0; i < VALUES_PER_THREAD; ++i) {
                            int left = worker * 100 + i;
                            int right = 1000 + worker * 100 + i;
                            child.query("!value(1, ?, ?);", new Object[]{left, right});
                        }
                        if (!root.commit(child)) {
                            throw new AssertionError("child commit rolled back: " + worker);
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    } finally {
                        done.countDown();
                    }
                }
            }, "rule-rendering-" + worker);
        }

        for (int worker = 0; worker < THREADS; ++worker) {
            for (int i = 0; i < VALUES_PER_THREAD; ++i) {
                int left = worker * 100 + i;
                int right = 1000 + worker * 100 + i;
                expected.add("!value(1, " + left + ", " + right + ");");
            }
            workers[worker].start();
        }

        ready.await();
        start.countDown();
        done.await();
        if (failure.get() != null) {
            throw new AssertionError("parallel population failed", failure.get());
        }
        return expected;
    }

    private static void assertRows(Mind mind, Set<String> expected, String phase) throws Exception {
        mind.query("?$x $y value(1, x, y);");
        if (mind.getSolutions().size() != expected.size()) {
            throw new AssertionError(phase + ": expected " + expected.size()
                    + " solutions, got " + mind.getSolutions().size());
        }

        Set<String> actual = new LinkedHashSet<>();
        int templateOrigins = 0;
        for (IRule solution : mind.getSolutions()) {
            String origin = solution.getOrigin();
            if (origin != null && origin.contains("?")) {
                ++templateOrigins;
            }

            String rendered = solution.toString(mind);
            if (rendered == null || rendered.isEmpty() || rendered.contains("?")) {
                throw new AssertionError(phase + ": unresolved solution row: " + rendered);
            }
            int semicolon = rendered.indexOf(';');
            if (semicolon < 0) {
                throw new AssertionError(phase + ": malformed solution row: " + rendered);
            }
            actual.add(rendered.substring(0, semicolon + 1));

            Map<String, Object> row = ((Rule) solution).createMap(mind);
            if (!rendered.equals(row.get("text"))) {
                throw new AssertionError(phase + ": API map text differs from contextual rendering");
            }
            if (origin == null ? row.get("origin") != null : !origin.equals(row.get("origin"))) {
                throw new AssertionError(phase + ": source origin was not preserved");
            }
        }

        if (templateOrigins != expected.size()) {
            throw new AssertionError(phase + ": expected all source origins to retain '?' templates");
        }
        if (!actual.equals(expected)) {
            throw new AssertionError(phase + ": materialized rowset mismatch\nexpected="
                    + expected + "\nactual=" + actual);
        }
    }
}
