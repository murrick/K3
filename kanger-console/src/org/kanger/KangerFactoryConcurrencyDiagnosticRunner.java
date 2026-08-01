/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Diagnostic wrapper around the existing factory concurrency regression.
 *
 * <p>The wrapped regression remains the source of pass/fail semantics. This
 * runner repeats that exact regression in an isolated temporary user home and
 * emits JVM thread snapshots while an attempt is still active, so a
 * platform-specific timeout exposes the actual wait/lock locations.</p>
 */
public final class KangerFactoryConcurrencyDiagnosticRunner {

    private static final int ATTEMPTS = 8;
    private static final long FIRST_SNAPSHOT_MILLIS = 30_000L;
    private static final long SECOND_SNAPSHOT_MILLIS = 45_000L;
    private static final long FINAL_WAIT_MILLIS = 30_000L;

    private KangerFactoryConcurrencyDiagnosticRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            for (int attempt = 1; attempt <= ATTEMPTS; ++attempt) {
                runAttempt(attempt);
                System.out.println("FACTORY_CONCURRENCY_DIAGNOSTIC_PASS attempt=" + attempt);
            }
            System.out.println("FACTORY_CONCURRENCY_DIAGNOSTIC_OK attempts=" + ATTEMPTS);
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void runAttempt(final int attempt) throws Throwable {
        Path testHome = Files.createTempDirectory(
                "kanger-factory-concurrency-diagnostic-" + attempt + "-");
        System.setProperty("user.home", testHome.toAbsolutePath().toString());

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread regression = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Method method = KangerFactorySafetyRunner.class
                            .getDeclaredMethod("testConcurrentMetadata");
                    method.setAccessible(true);
                    method.invoke(null);
                } catch (InvocationTargetException error) {
                    failure.set(error.getCause());
                } catch (Throwable error) {
                    failure.set(error);
                }
            }
        }, "kanger-factory-concurrency-regression-" + attempt);

        regression.start();
        regression.join(FIRST_SNAPSHOT_MILLIS);
        if (regression.isAlive()) {
            dumpThreads("attempt " + attempt + ": 30-second factory concurrency snapshot");
            regression.join(SECOND_SNAPSHOT_MILLIS);
        }
        if (regression.isAlive()) {
            dumpThreads("attempt " + attempt + ": 75-second factory concurrency snapshot");
            regression.join(FINAL_WAIT_MILLIS);
        }

        if (regression.isAlive()) {
            dumpThreads("attempt " + attempt + ": factory concurrency did not terminate");
            throw new AssertionError(
                    "Factory concurrency regression exceeded diagnostic window on attempt "
                            + attempt);
        }
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private static void dumpThreads(String label) {
        System.err.println("========== " + label + " ==========");
        Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
            Thread thread = entry.getKey();
            String name = thread.getName();
            if (!name.startsWith("pool-")
                    && !name.startsWith("kanger-factory-concurrency")) {
                continue;
            }
            System.err.println('"' + name + '"'
                    + " state=" + thread.getState()
                    + " daemon=" + thread.isDaemon());
            for (StackTraceElement frame : entry.getValue()) {
                System.err.println("    at " + frame);
            }
        }
        System.err.println("============================================");
    }
}
