/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.factory.DictionaryFactory;
import org.kanger.factory.DomainFactory;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.DB;
import org.kanger.storage.Sapato;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
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
 * close/reopen with lazy hydration. Semantic value comparison is independent
 * of presentation details such as integral versus floating-point formatting.
 */
public final class KangerRuleRenderingRunner {

    private static final int THREADS = 3;
    private static final int VALUES_PER_THREAD = 8;
    private static final int DEFAULT_ITERATIONS = 8;

    private KangerRuleRenderingRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            Path home = Files.createTempDirectory("kanger-rule-rendering-");
            System.setProperty("user.home", home.toAbsolutePath().toString());
            System.out.println("Rule rendering home: " + home.toAbsolutePath());

            int iterations = Integer.getInteger(
                    "kanger.rule.rendering.iterations", DEFAULT_ITERATIONS);
            if (iterations <= 0) {
                throw new IllegalArgumentException("iterations must be positive");
            }
            for (int iteration = 1; iteration <= iterations; ++iteration) {
                runScenario(false, iteration, iterations);
                runScenario(true, iteration, iterations);
            }

            System.out.println("RULE_RENDERING_OK iterations=" + iterations);
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void runScenario(boolean persistent, int iteration,
                                    int iterations) throws Exception {
        String suffix = persistent ? "persistent" : "memory";
        String instance = suffix + "-" + iteration;
        String userName = "rule-rendering-" + instance;
        IUser user = UserFactory.createUser(userName, userName);
        new UDF().init(user);
        new DB().init(user);

        Mind root = new Mind(user);
        String storageName = "data/rule-rendering-" + instance;
        if (persistent) {
            root = (Mind) root.useStorage(storageName);
        }
        root = (Mind) root.clearWorkspace();

        Set<String> expected = populateInParallel(root);
        assertRows(root, expected, instance + "-live");

        if (persistent) {
            String liveState = factoryState(root);
            root = (Mind) root.closeStorage();
            root = (Mind) root.useStorage(storageName);
            try {
                assertRows(root, expected, instance + "-reopen");
            } catch (AssertionError failure) {
                throw new AssertionError(failure.getMessage()
                        + "\nlive-state=" + liveState
                        + "\nreopen-state=" + factoryState(root), failure);
            }
        }

        System.out.println("RULE_RENDERING_PASS " + suffix + " "
                + iteration + "/" + iterations);
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
                expected.add(valueKey(left, right));
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
            if (rendered.indexOf(';') < 0) {
                throw new AssertionError(phase + ": malformed solution row: " + rendered);
            }

            ArgumentsList arguments = (ArgumentsList) solution.getArguments();
            Object left = arguments.get(1).getValue(mind).getValue();
            Object right = arguments.get(2).getValue(mind).getValue();
            actual.add(valueKey(left, right));

            Map<String, Object> row = ((Rule) solution).createMap(mind);
            if (!rendered.equals(row.get("text"))) {
                throw new AssertionError(phase + ": API map text differs from contextual rendering");
            }
            if (origin == null ? row.get("origin") != null : !origin.equals(row.get("origin"))) {
                throw new AssertionError(phase + ": source origin was not preserved");
            }
        }

        if (mind.getSolutions().size() != expected.size()) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            Set<String> unexpected = new LinkedHashSet<>(actual);
            unexpected.removeAll(expected);
            throw new AssertionError(phase + ": expected " + expected.size()
                    + " solutions, got " + mind.getSolutions().size()
                    + "\nmissing=" + missing + "\nunexpected=" + unexpected
                    + "\nstate=" + factoryState(mind));
        }
        if (templateOrigins != expected.size()) {
            throw new AssertionError(phase + ": expected all source origins to retain '?' templates");
        }
        if (!actual.equals(expected)) {
            throw new AssertionError(phase + ": materialized rowset mismatch\nexpected="
                    + expected + "\nactual=" + actual);
        }
    }

    private static String factoryState(Mind mind) throws Exception {
        return factoryState(mind, "rules", mind.getRules(), RuleFactory.SCHEMA)
                + "," + factoryState(mind, "domains", mind.getDomains(), DomainFactory.SCHEMA)
                + "," + factoryState(mind, "terms", mind.getTerms(), DictionaryFactory.SCHEMA);
    }

    private static String factoryState(Mind mind, String name, Object factory,
                                       String schema) throws Exception {
        ChainState chain = chainState(factory);
        return name + "(size=" + size(factory)
                + ",chain=" + chain.total
                + ",memory=" + chain.memory
                + ",persistent=" + chain.persistent
                + ",base=" + baseSize(mind, schema) + ")";
    }

    private static int size(Object factory) throws Exception {
        return ((Number) factory.getClass().getMethod("size").invoke(factory)).intValue();
    }

    private static ChainState chainState(Object factory) throws Exception {
        Field cacheField = factory.getClass().getDeclaredField("cache");
        cacheField.setAccessible(true);
        ICache cache = (ICache) cacheField.get(factory);
        ChainState state = new ChainState();
        for (IStep step = cache.getRoot(); step != null; step = step.getNext()) {
            ++state.total;
            if (step instanceof Sapato) {
                ++state.persistent;
            } else {
                ++state.memory;
            }
        }
        return state;
    }

    private static int baseSize(Mind mind, String schema) {
        if (!mind.isStorageUsed()) {
            return -1;
        }
        int count = 0;
        IBase base = ((User) mind.getUser()).getStorage(schema);
        if (!(base instanceof Iterable)) {
            return -2;
        }
        Iterator<?> iterator = ((Iterable<?>) base).iterator();
        while (iterator != null && iterator.hasNext()) {
            if (iterator.next() != null) {
                ++count;
            }
        }
        return count;
    }

    private static String valueKey(Object left, Object right) {
        return normalize(left) + ":" + normalize(right);
    }

    private static String normalize(Object value) {
        if (value instanceof Number) {
            return Long.toString(((Number) value).longValue());
        }
        return String.valueOf(value);
    }

    private static final class ChainState {
        private int total;
        private int memory;
        private int persistent;
    }
}
