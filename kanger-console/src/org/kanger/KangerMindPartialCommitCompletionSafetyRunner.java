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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

/** Regression gate for failure during composite commit completion. */
public final class KangerMindPartialCommitCompletionSafetyRunner {

    private KangerMindPartialCommitCompletionSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            IUser user = UserFactory.createUser("mind-partial-commit-completion",
                    "mind-partial-commit-completion");
            new UDF().init(user);
            new DB().init(user);

            Mind parent = new Mind(user);
            Mind child = new Mind(parent);
            replaceAnalyzer(parent, new SuccessfulAnalyzer(parent));

            Object tVars = parent.getTVars();
            Field cacheField = findField(tVars.getClass(), "cache");
            cacheField.setAccessible(true);
            ICache realCache = (ICache) cacheField.get(tVars);
            cacheField.set(tVars, failingCommitCache(realCache));

            boolean failureObserved = false;
            try {
                parent.commit(child);
            } catch (InjectedCommitCompletionFailure expected) {
                failureObserved = true;
            }
            require(failureObserved, "fault injection did not reach factory commit completion");

            require(transactionCounter(parent) == 0,
                    "partial commit completion leaked the child transaction reservation");

            assertFactoryCheckpointDepth(parent.getFunctions(), 0, "functions");
            assertFactoryCheckpointDepth(parent.getFValues(), 0, "fValues");
            assertFactoryCheckpointDepth(parent.getTVars(), 0, "tVars");
            assertFactoryCheckpointDepth(parent.getTValues(), 0, "tValues");
            assertFactoryCheckpointDepth(parent.getDomains(), 0, "domains");
            assertFactoryCheckpointDepth(parent.getRules(), 0, "rules");
            assertFactoryCheckpointDepth(parent.getComments(), 0, "comments");
            assertFactoryCheckpointDepth(parent.getLibrary(), 0, "library");

            System.out.println("MIND_PARTIAL_COMMIT_COMPLETION_PASS reservation");
            System.out.println("MIND_PARTIAL_COMMIT_COMPLETION_PASS checkpoints");
            System.out.println("MIND_PARTIAL_COMMIT_COMPLETION_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static ICache failingCommitCache(final ICache delegate) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("commit".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    throw new InjectedCommitCompletionFailure();
                }
                try {
                    return method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException error) {
                    throw error.getCause();
                }
            }
        };
        return (ICache) Proxy.newProxyInstance(ICache.class.getClassLoader(),
                new Class<?>[]{ICache.class}, handler);
    }

    private static void replaceAnalyzer(Mind mind, Analyzer analyzer) throws Exception {
        Field field = Mind.class.getDeclaredField("analyzer");
        field.setAccessible(true);
        field.set(mind, analyzer);
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
        if (Proxy.isProxyClass(cache.getClass())) {
            Field handlerField = Proxy.getInvocationHandler(cache).getClass()
                    .getDeclaredField("val$delegate");
            handlerField.setAccessible(true);
            cache = handlerField.get(Proxy.getInvocationHandler(cache));
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
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class SuccessfulAnalyzer extends Analyzer {
        private SuccessfulAnalyzer(Mind mind) {
            super(mind);
        }

        @Override
        public boolean checkDatabase(Set<Long> list, boolean logging) {
            return false;
        }
    }

    private static final class InjectedCommitCompletionFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
