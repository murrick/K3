/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Diagnostic wrapper around the existing factory concurrency regression.
 *
 * <p>The wrapped regression remains the source of pass/fail semantics. This
 * runner only emits JVM thread snapshots while the regression is still active,
 * so a platform-specific timeout exposes the actual wait/lock locations.</p>
 */
public final class KangerFactoryConcurrencyDiagnosticRunner {

    private static final long FIRST_SNAPSHOT_MILLIS = 30_000L;
    private static final long SECOND_SNAPSHOT_MILLIS = 45_000L;
    private static final long FINAL_WAIT_MILLIS = 30_000L;

    private KangerFactoryConcurrencyDiagnosticRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
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
        }, "kanger-factory-concurrency-regression");

        try {
            regression.start();
            regression.join(FIRST_SNAPSHOT_MILLIS);
            if (regression.isAlive()) {
                dumpThreads("30-second factory concurrency snapshot");
                regression.join(SECOND_SNAPSHOT_MILLIS);
            }
            if (regression.isAlive()) {
                dumpThreads("75-second factory concurrency snapshot");
                regression.join(FINAL_WAIT_MILLIS);
            }

            if (regression.isAlive()) {
                dumpThreads("factory concurrency did not terminate");
                throw new AssertionError(
                        "Factory concurrency regression exceeded diagnostic window");
            }
            if (failure.get() != null) {
                throw failure.get();
            }

            System.out.println("FACTORY_CONCURRENCY_DIAGNOSTIC_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
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
