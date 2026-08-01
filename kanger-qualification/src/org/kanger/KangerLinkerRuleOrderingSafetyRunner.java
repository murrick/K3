/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IRule;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Focused regression gate for Linker's ascending and descending rule
 * traversals over the complete long identifier domain.
 */
public final class KangerLinkerRuleOrderingSafetyRunner {

    private KangerLinkerRuleOrderingSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            assertPair(0L, 1L);
            assertPair(0L, (long) Integer.MAX_VALUE + 1L);
            assertPair(0L, 1L << 32);
            assertPair(Long.MIN_VALUE, Long.MAX_VALUE);
            assertPair(-1L, Long.MAX_VALUE);

            List<IRule> ascending = rules(
                    1L << 32,
                    Long.MAX_VALUE,
                    0L,
                    Long.MIN_VALUE,
                    (long) Integer.MAX_VALUE + 1L,
                    -1L);
            Collections.sort(ascending, Linker::compareRuleIdsAscending);
            assertIds(ascending,
                    Long.MIN_VALUE,
                    -1L,
                    0L,
                    (long) Integer.MAX_VALUE + 1L,
                    1L << 32,
                    Long.MAX_VALUE);

            List<IRule> descending = new ArrayList<>(ascending);
            Collections.sort(descending, Linker::compareRuleIdsDescending);
            assertIds(descending,
                    Long.MAX_VALUE,
                    1L << 32,
                    (long) Integer.MAX_VALUE + 1L,
                    0L,
                    -1L,
                    Long.MIN_VALUE);

            IRule equalLeft = rule(42L);
            IRule equalRight = rule(42L);
            require(Linker.compareRuleIdsAscending(equalLeft, equalRight) == 0,
                    "equal IDs must compare as zero in ascending order");
            require(Linker.compareRuleIdsDescending(equalLeft, equalRight) == 0,
                    "equal IDs must compare as zero in descending order");

            System.out.println("LINKER_RULE_ORDERING_PASS ascending");
            System.out.println("LINKER_RULE_ORDERING_PASS descending");
            System.out.println("LINKER_RULE_ORDERING_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void assertPair(long lowerId, long higherId) {
        IRule lower = rule(lowerId);
        IRule higher = rule(higherId);

        int ascending = Linker.compareRuleIdsAscending(lower, higher);
        int ascendingReverse = Linker.compareRuleIdsAscending(higher, lower);
        require(ascending < 0,
                "ascending comparator must place " + lowerId + " before " + higherId);
        require(ascendingReverse > 0,
                "ascending reverse comparison must be positive");
        require(Integer.signum(ascending) == -Integer.signum(ascendingReverse),
                "ascending comparator must be antisymmetric");

        int descending = Linker.compareRuleIdsDescending(lower, higher);
        int descendingReverse = Linker.compareRuleIdsDescending(higher, lower);
        require(descending > 0,
                "descending comparator must place " + higherId + " before " + lowerId);
        require(descendingReverse < 0,
                "descending reverse comparison must be negative");
        require(Integer.signum(descending) == -Integer.signum(descendingReverse),
                "descending comparator must be antisymmetric");
    }

    private static List<IRule> rules(long... ids) {
        List<IRule> result = new ArrayList<>();
        for (long id : ids) {
            result.add(rule(id));
        }
        return result;
    }

    private static void assertIds(List<IRule> rules, long... expected) {
        long[] actual = new long[rules.size()];
        for (int i = 0; i < rules.size(); ++i) {
            actual[i] = rules.get(i).getId();
        }
        require(Arrays.equals(actual, expected),
                "unexpected rule order: " + Arrays.toString(actual)
                        + ", expected " + Arrays.toString(expected));
    }

    private static IRule rule(final long id) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("getId".equals(method.getName())) {
                    return id;
                }
                if ("toString".equals(method.getName())) {
                    return "Rule(" + id + ")";
                }
                if ("hashCode".equals(method.getName())) {
                    return Long.valueOf(id).hashCode();
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                throw new UnsupportedOperationException(method.getName());
            }
        };
        return (IRule) Proxy.newProxyInstance(
                IRule.class.getClassLoader(),
                new Class<?>[]{IRule.class},
                handler);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
