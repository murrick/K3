/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.storage;

import org.kanger.interfaces.IUser;

import java.lang.reflect.Proxy;

/**
 * Focused regression runner for the public {@link Index.IndexOne}
 * {@link Comparable} contract.
 *
 * <p>The runner deliberately exercises distances that cannot be represented by
 * an {@code int}. It therefore fails against subtraction followed by narrowing
 * and passes only when comparison preserves the full {@code long} ordering.</p>
 */
public final class IndexOneComparableSafetyRunner {

    private IndexOneComparableSafetyRunner() {
    }

    public static void main(String[] args) throws Exception {
        IUser user = (IUser) Proxy.newProxyInstance(
                IUser.class.getClassLoader(),
                new Class<?>[]{IUser.class},
                (proxy, method, methodArgs) -> {
                    if ("getProperty".equals(method.getName())) {
                        return methodArgs != null && methodArgs.length > 1
                                ? methodArgs[1]
                                : null;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == byte.class || returnType == short.class
                            || returnType == int.class || returnType == long.class) {
                        return 0;
                    }
                    if (returnType == float.class || returnType == double.class) {
                        return 0.0;
                    }
                    if (returnType == char.class) {
                        return '\0';
                    }
                    return null;
                });

        Index index = new Index(0, new Object(), user);

        assertOrder(index, 0L, 1L);
        assertOrder(index, -1L, 0L);
        assertOrder(index, Long.MIN_VALUE, Long.MAX_VALUE);
        assertOrder(index, Long.MIN_VALUE, 0L);
        assertOrder(index, 0L, Long.MAX_VALUE);

        // The historical narrowing implementation collapses this pair to 0.
        assertOrder(index, 0L, 1L << 32);

        // The historical narrowing implementation reverses the sign here.
        assertOrder(index, 0L, (long) Integer.MAX_VALUE + 1L);

        Index.IndexOne equalLeft = index.new IndexOne(0).setId(42L);
        Index.IndexOne equalRight = index.new IndexOne(0).setId(42L);
        require(equalLeft.compareTo(equalRight) == 0,
                "equal ids must compare as zero");

        System.out.println("IndexOneComparableSafetyRunner: OK");
    }

    private static void assertOrder(Index index, long lowerId, long higherId) {
        Index.IndexOne lower = index.new IndexOne(0).setId(lowerId);
        Index.IndexOne higher = index.new IndexOne(0).setId(higherId);

        int forward = lower.compareTo(higher);
        int reverse = higher.compareTo(lower);

        require(forward < 0,
                "expected " + lowerId + " < " + higherId + ", got " + forward);
        require(reverse > 0,
                "expected " + higherId + " > " + lowerId + ", got " + reverse);
        require(Integer.signum(forward) == -Integer.signum(reverse),
                "comparison must be antisymmetric for " + lowerId + " and " + higherId);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
