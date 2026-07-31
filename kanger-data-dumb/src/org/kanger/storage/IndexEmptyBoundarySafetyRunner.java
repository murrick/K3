/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.storage;

import org.kanger.interfaces.IUser;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Characterization runner for the observable empty-index boundary.
 *
 * <p>This runner intentionally records current behavior. It does not declare
 * the behavior desirable: {@link Index#firstKey()} throws, while both iterator
 * factories return {@code null} after swallowing the constructor failure.</p>
 */
public final class IndexEmptyBoundarySafetyRunner {

    private IndexEmptyBoundarySafetyRunner() {
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
        require(index.isEmpty(), "fresh Index must be empty");

        boolean firstKeyThrew = false;
        try {
            index.firstKey();
        } catch (NoSuchElementException expected) {
            firstKeyThrew = true;
        }
        require(firstKeyThrew,
                "current empty firstKey contract must throw NoSuchElementException");

        Iterator<Index.IndexOne> forward = index.iterator();
        Iterator<Index.IndexOne> backward = index.iterator(true);
        require(forward == null,
                "current empty forward iterator factory must return null");
        require(backward == null,
                "current empty backward iterator factory must return null");

        System.out.println("IndexEmptyBoundarySafetyRunner: OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
