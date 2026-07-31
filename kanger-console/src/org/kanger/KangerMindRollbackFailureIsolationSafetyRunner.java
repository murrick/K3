/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.factory.FunctionFactory;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.ICache;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * Regression gate for best-effort unwind of a failed composite rollback.
 *
 * <p>The analyzer deterministically requests rollback. The first factory cache
 * then throws from release(). Mind must still unwind every remaining factory
 * frame and close the child transaction reservation before propagating the
 * rollback failure.</p>
 */
public final class KangerMindRollbackFailureIsolationSafetyRunner {

    private KangerMindRollbackFailureIsolationSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            IUser user = UserFactory.createUser("mind-rollback-failure-isolation-" + System.nanoTime(),
                    "mind-rollback-failure-isolation");
            new UDF().init(user);
            new DB().init(user);

            Mind parent = new Mind(user);

            Mind child = new Mind(parent);
            addDirectRule(child, "rollback_failure_isolation_probe");

            Mind sibling = new Mind(parent);
            addDirectRule(sibling, "rollback_failure_isolation_sibling");

            require(transactionCounter(parent) == 2,
                    "two open children did not reserve two transactions");

            require(parent.commit(sibling),
                    "fixture sibling commit unexpectedly failed");

            require(transactionCounter(parent) == 1,
                    "sibling commit did not leave exactly the original child reservation");

            replaceAnalyzer(parent, new RejectingAnalyzer(parent));
            installReleaseFailure(parent.getFunctions());

            boolean failureObserved = false;
            try {
                parent.commit(child);
            } catch (InjectedReleaseFailure expected) {
                failureObserved = true;
            }
            require(failureObserved, "release fault was not propagated");

            require(transactionCounter(parent) == 0,
                    "rollback failure leaked the child transaction reservation");

            assertFactoryCheckpointDepth(parent.getFValues(), 0, "fValues");
            assertFactoryCheckpointDepth(parent.getTVars(), 0, "tVars");
            assertFactoryCheckpointDepth(parent.getTValues(), 0, "tValues");
            assertFactoryCheckpointDepth(parent.getDomains(), 0, "domains");
            assertFactoryCheckpointDepth(parent.getRules(), 0, "rules");
            assertFactoryCheckpointDepth(parent.getComments(), 0, "comments");
            assertFactoryCheckpointDepth(parent.getLibrary(), 0, "library");

            System.out.println("MIND_ROLLBACK_FAILURE_ISOLATION_PASS reservation");
            System.out.println("MIND_ROLLBACK_FAILURE_ISOLATION_PASS remaining-frames");
            System.out.println("MIND_ROLLBACK_FAILURE_ISOLATION_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void addDirectRule(Mind mind, String origin) throws Exception {
        Rule rule = new Rule(mind);
        mind.getRules().register(rule);
        rule.setOrigin(mind.getTerms().add(origin));
        require(mind.getRules().add(rule) == rule,
                "fixture did not create direct local rule " + origin);
    }

    private static void replaceAnalyzer(Mind mind, Analyzer analyzer) throws Exception {
        Field field = Mind.class.getDeclaredField("analyzer");
        field.setAccessible(true);
        field.set(mind, analyzer);
    }

    private static void installReleaseFailure(FunctionFactory factory) throws Exception {
        Field cacheField = FunctionFactory.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        final ICache delegate = (ICache) cacheField.get(factory);

        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("release".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    throw new InjectedReleaseFailure();
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                }
            }
        };

        ICache failing = (ICache) Proxy.newProxyInstance(
                ICache.class.getClassLoader(), new Class<?>[]{ICache.class}, handler);
        cacheField.set(factory, failing);
    }

    private static int transactionCounter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private static void assertFactoryCheckpointDepth(Object factory, int expected, String name)
            throws Exception {
        Field cacheField = findField(factory.getClass(), "cache");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(factory);

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

    private static final class RejectingAnalyzer extends Analyzer {

        private RejectingAnalyzer(Mind mind) {
            super(mind);
        }

        @Override
        public boolean checkDatabase(Set<Long> list, boolean logging) {
            return true;
        }
    }

    private static final class InjectedReleaseFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
