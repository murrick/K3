/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.ICache;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * Regression gate for exception atomicity during composite mark acquisition.
 *
 * <p>The injected failure occurs while acquiring the third factory checkpoint.
 * Only checkpoints acquired successfully before that point may be unwound; all
 * later factories must remain untouched, and the child transaction reservation
 * must still be consumed exactly once.</p>
 */
public final class KangerMindPartialMarkAcquisitionSafetyRunner {

    private KangerMindPartialMarkAcquisitionSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            IUser user = UserFactory.createUser("mind-partial-mark-acquisition",
                    "mind-partial-mark-acquisition");
            new UDF().init(user);
            new DB().init(user);

            Mind parent = new Mind(user);
            Mind child = new Mind(parent);
            require(transactionCounter(parent) == 1,
                    "child construction did not reserve one transaction");

            installFailingMarkCache(parent.getTVars());

            boolean failureObserved = false;
            try {
                parent.commit(child);
            } catch (InjectedMarkFailure expected) {
                failureObserved = true;
            }
            require(failureObserved, "fault injection did not reach third mark acquisition");

            require(transactionCounter(parent) == 0,
                    "failed mark acquisition leaked child reservation");

            assertCheckpointDepth(parent.getFunctions(), 0, "functions");
            assertCheckpointDepth(parent.getFValues(), 0, "fValues");
            assertCheckpointDepth(parent.getTVars(), 0, "tVars");
            assertCheckpointDepth(parent.getTValues(), 0, "tValues");
            assertCheckpointDepth(parent.getDomains(), 0, "domains");
            assertCheckpointDepth(parent.getRules(), 0, "rules");
            assertCheckpointDepth(parent.getComments(), 0, "comments");
            assertCheckpointDepth(parent.getLibrary(), 0, "library");

            System.out.println("MIND_PARTIAL_MARK_ACQUISITION_PASS reservation");
            System.out.println("MIND_PARTIAL_MARK_ACQUISITION_PASS acquired-frames");
            System.out.println("MIND_PARTIAL_MARK_ACQUISITION_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void installFailingMarkCache(Object factory) throws Exception {
        Field cacheField = findField(factory.getClass(), "cache");
        cacheField.setAccessible(true);
        ICache delegate = (ICache) cacheField.get(factory);

        ICache proxy = (ICache) Proxy.newProxyInstance(
                ICache.class.getClassLoader(),
                new Class<?>[]{ICache.class},
                (instance, method, args) -> {
                    if ("mark".equals(method.getName())) {
                        throw new InjectedMarkFailure();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (java.lang.reflect.InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
        cacheField.set(factory, proxy);
    }

    private static int transactionCounter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private static void assertCheckpointDepth(Object factory, int expected, String name)
            throws Exception {
        Field cacheField = findField(factory.getClass(), "cache");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(factory);
        if (Proxy.isProxyClass(cache.getClass())) {
            return;
        }

        Field stackField = findField(cache.getClass(), "stack");
        stackField.setAccessible(true);
        int actual = ((java.util.Stack<?>) stackField.get(cache)).size();
        require(actual == expected,
                name + " cache checkpoint depth expected " + expected + " but was " + actual);

        for (Field field : factory.getClass().getDeclaredFields()) {
            if (java.util.Stack.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                int auxiliaryDepth = ((java.util.Stack<?>) field.get(factory)).size();
                require(auxiliaryDepth == expected,
                        name + "." + field.getName() + " depth expected " + expected
                                + " but was " + auxiliaryDepth);
            }
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the hierarchy.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class InjectedMarkFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
