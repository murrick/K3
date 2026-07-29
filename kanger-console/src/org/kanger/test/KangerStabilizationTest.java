/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.test;

import org.kanger.Mind;
import org.kanger.enums.DataType;
import org.kanger.factory.DictionaryFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Term;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Additive stabilization characterization corpus.
 *
 * <p>The historical {@link KangerTest} corpus remains unchanged. This class
 * contains narrowly scoped tests that make recovered invariants executable
 * before implementation changes are opened.</p>
 */
public final class KangerStabilizationTest {

    private IMind mind;

    private KangerStabilizationTest(IMind mind) {
        this.mind = mind;
    }

    public static boolean test(IMind mind, String prefix) throws Exception {
        KangerStabilizationTest suite = new KangerStabilizationTest(mind);
        Map<String, Method> methods = new TreeMap<>();
        for (Method method : KangerStabilizationTest.class.getDeclaredMethods()) {
            if (method.getName().startsWith(prefix) && method.getParameterTypes().length == 0) {
                method.setAccessible(true);
                methods.put(method.getName(), method);
            }
        }

        int success = 0;
        int failures = 0;
        long start = System.currentTimeMillis();
        System.out.println("====================================================");
        System.out.println("S5A stabilization characterization tests");

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

        System.out.println("Stabilization Timing: " + ((System.currentTimeMillis() - start) / 1000.0));
        System.out.println("Stabilization Success: " + success);
        System.out.println("Stabilization Fails: " + failures);
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

    private Set<String> rows(String... names) throws Exception {
        Set<String> rows = new LinkedHashSet<>();
        for (Map<String, ITerm> row : mind.getValues()) {
            StringBuilder value = new StringBuilder();
            for (String name : names) {
                if (value.length() > 0) {
                    value.append('|');
                }
                ITerm term = row.get(name);
                value.append(name).append('=');
                value.append(term == null ? "<missing>" : String.valueOf(term.getValue()));
            }
            rows.add(value.toString());
        }
        return rows;
    }

    public void set_s5a_01_exact_arithmetic_rows() throws Exception {
        resetWorkspace();
        Boolean result = mind.query("?$x $y x=y*2, (y=4 || y=5);");
        require(Boolean.TRUE.equals(result), "Expected TRUE arithmetic query");

        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                "x=8.0|y=4.0",
                "x=10.0|y=5.0"
        ));
        Set<String> actual = rows("x", "y");
        require(expected.equals(actual), "Expected exact rows " + expected + ", actual " + actual);
    }

    public void set_s5a_02_runtime_id_commit_release() throws Exception {
        resetWorkspace();
        Mind parent = (Mind) mind;

        Mind committed = new Mind(parent);
        require(Boolean.TRUE.equals(committed.query("!committed(value);")), "Child insertion failed");
        IRule accepted = committed.getAcceptedRule();
        require(accepted != null, "Accepted child rule is missing");
        long committedId = accepted.getId();
        require(parent.getRules().get(committedId) == null, "Child rule leaked before commit");
        require(parent.commit(committed), "Child commit was rejected");

        IRule published = parent.getRules().get(committedId);
        require(published != null, "Committed rule is not addressable by its runtime id");
        require(((org.kanger.units.Rule) published).getMindId() == parent.getId(),
                "Committed rule was not rebound to the parent Mind");

        Mind discarded = new Mind(parent);
        require(Boolean.TRUE.equals(discarded.query("!discarded(value);")), "Discarded child insertion failed");
        IRule rejected = discarded.getAcceptedRule();
        require(rejected != null, "Discarded child accepted rule is missing");
        long discardedId = rejected.getId();
        parent.release(discarded);
        require(parent.getRules().get(discardedId) == null, "Released child rule remains published");

        mind = parent;
    }

    public void set_s5a_03_set_order_identity() throws Exception {
        resetWorkspace();
        DictionaryFactory dictionary = (DictionaryFactory) mind.getTerms();

        ITerm first = dictionary.add(new Object[]{1, "alpha"});
        ITerm reordered = dictionary.add(new Object[]{"alpha", 1});
        ITerm empty = dictionary.add(new Object[]{});

        require(first.getId() == reordered.getId(), "SET order changed canonical identity");
        require(first.getType() == DataType.SET, "Expected SET term");
        require(empty.getType() == DataType.SET, "Expected empty SET term");
        require(((java.util.Collection<?>) empty.getValue()).isEmpty(), "Expected empty SET value");
        require(((Term) first).equalsTo((Term) reordered), "SET structural equality is inconsistent");
    }
}
