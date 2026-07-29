/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.test;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Regression corpus for C4 interval boundaries.
 */
public final class KangerC4IntervalTest {

    private IMind mind;

    private KangerC4IntervalTest(IMind mind) {
        this.mind = mind;
    }

    public static boolean test(IMind mind, String prefix) throws Exception {
        KangerC4IntervalTest suite = new KangerC4IntervalTest(mind);
        Map<String, Method> methods = new TreeMap<>();
        for (Method method : KangerC4IntervalTest.class.getDeclaredMethods()) {
            if (method.getName().startsWith(prefix) && method.getParameterTypes().length == 0) {
                method.setAccessible(true);
                methods.put(method.getName(), method);
            }
        }

        int success = 0;
        int failures = 0;
        long start = System.currentTimeMillis();
        System.out.println("====================================================");
        System.out.println("C4 interval regression tests");

        for (Map.Entry<String, Method> entry : methods.entrySet()) {
            System.out.println("Testing: " + entry.getKey());
            try {
                entry.getValue().invoke(suite);
                ++success;
                System.out.println("OK");
            } catch (InvocationTargetException error) {
                ++failures;
                Throwable cause = error.getCause() == null ? error : error.getCause();
                cause.printStackTrace(System.err);
            }
            System.out.println("----------------------------------------------------");
        }

        System.out.println("C4 Timing: " + ((System.currentTimeMillis() - start) / 1000.0));
        System.out.println("C4 Success: " + success);
        System.out.println("C4 Fails: " + failures);
        System.out.println("====================================================");
        return failures == 0;
    }

    private void resetWorkspace() throws Exception {
        mind = mind.clearWorkspace();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private Set<Double> values(String name) throws Exception {
        Set<Double> values = new LinkedHashSet<>();
        for (Map<String, ITerm> row : mind.getValues()) {
            ITerm value = row.get(name);
            require(value != null, "Missing value for " + name + " in row " + row);
            values.add((Double) value.getValue());
        }
        return values;
    }

    public void set_c4_01_zero_step_empty() throws Exception {
        resetWorkspace();

        Boolean result = mind.query("?$x x : 1..10 0;");
        require(result == null, "Zero-step interval must remain undefined, actual " + result);
        require(mind.getValues().isEmpty(), "Zero-step interval produced Values");
        require(mind.getSolutions().isEmpty(), "Zero-step interval produced Solutions");
    }

    public void set_c4_02_ascending_interval_endpoints() throws Exception {
        resetWorkspace();

        Boolean result = mind.query("?$x x : 1..10 3;");
        require(Boolean.TRUE.equals(result), "Ascending interval query was not TRUE");
        Set<Double> expected = new LinkedHashSet<>(Arrays.asList(1.0, 4.0, 7.0, 10.0));
        require(expected.equals(values("x")),
                "Ascending interval endpoints changed: expected " + expected + ", actual " + values("x"));
    }

    public void set_c4_03_descending_interval_endpoints() throws Exception {
        resetWorkspace();

        Boolean result = mind.query("?$x x : 10..1 3;");
        require(Boolean.TRUE.equals(result), "Descending interval query was not TRUE");
        Set<Double> expected = new LinkedHashSet<>(Arrays.asList(10.0, 7.0, 4.0, 1.0));
        require(expected.equals(values("x")),
                "Descending interval endpoints changed: expected " + expected + ", actual " + values("x"));
    }
}
