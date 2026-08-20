/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.test;

import org.kanger.Diagnostics;
import org.kanger.interfaces.IMind;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs the historical interactive test corpus against a caller-owned test Mind.
 *
 * <p>This runner deliberately performs no User or storage lifecycle operations.
 * The caller chooses whether the supplied Mind is offline or database-backed
 * and owns creation and disposal of that complete test runtime.</p>
 */
public final class KangerVisualTestRunner {

    private KangerVisualTestRunner() {
    }

    public static boolean test(IMind mind, String prefix) throws Exception {
        System.out.println("Init test system...");
        int successCount = 0;
        long startTime = System.currentTimeMillis();
        List<String> fails = new ArrayList<String>();
        Map<String, Double> timings = new TreeMap<String, Double>();

        KangerTest test = new KangerTest(mind);
        test.setUp();

        for (Method method : KangerTest.class.getDeclaredMethods()) {
            if (method.getName().startsWith(prefix)) {
                timings.put(method.getName(), 0.0);
            }
        }

        System.out.println("Done.");
        System.out.println("----------------------------------------------------");

        for (String name : timings.keySet()) {
            try {
                System.out.println("Testing: " + name);
                long started = System.currentTimeMillis();
                Diagnostics.resetStorageCounters(test.mind);
                if (Diagnostics.isEnabled(test.mind)) {
                    System.out.println(Diagnostics.snapshot(test.mind,
                            "before " + name));
                }
                Method method = KangerTest.class.getDeclaredMethod(name);
                method.setAccessible(true);
                try (Diagnostics.Watchdog watchdog = Diagnostics.watch(name, test.mind)) {
                    method.invoke(test);
                }
                if (Diagnostics.isEnabled(test.mind)) {
                    System.out.println(Diagnostics.snapshot(test.mind,
                            "after " + name));
                }
                double timing = (System.currentTimeMillis() - started) / 1000.0;
                System.out.println("Timing: " + timing + " sec");
                System.out.println("====================================================");
                timings.put(name, timing);
                ++successCount;
            } catch (Exception error) {
                fails.add(name);
                System.err.println(new Date());
                Throwable cause = error.getCause() == null ? error : error.getCause();
                cause.printStackTrace(System.err);
            }
        }

        for (Map.Entry<String, Double> timing : timings.entrySet()) {
            System.out.println(timing.getKey() + "\t" + timing.getValue() + " sec");
        }
        if (!fails.isEmpty()) {
            System.out.println("====================================================");
            System.out.println("Fails:");
            for (String fail : fails) {
                System.out.println(fail);
            }
        }
        System.out.println("====================================================");
        System.out.println(" Timing: "
                + ((System.currentTimeMillis() - startTime) / 1000.0));
        System.out.println("Success: " + successCount);
        System.out.println("  Fails: " + fails.size());
        return fails.isEmpty();
    }
}
