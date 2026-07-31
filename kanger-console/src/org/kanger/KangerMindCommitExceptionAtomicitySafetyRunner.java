/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * Regression gate for exception atomicity of Mind.commit(child).
 *
 * <p>The injected analyzer failure occurs after the parent has opened all
 * composite factory checkpoints and merged child state. The failed commit must
 * close the child reservation and unwind every checkpoint before propagating
 * the exception.</p>
 */
public final class KangerMindCommitExceptionAtomicitySafetyRunner {

    private KangerMindCommitExceptionAtomicitySafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            IUser user = UserFactory.createUser("mind-commit-exception-atomicity",
                    "mind-commit-exception-atomicity");
            new UDF().init(user);
            new DB().init(user);

            Mind parent = new Mind(user);
            Mind child = new Mind(parent);
            require(transactionCounter(parent) == 1,
                    "child construction did not reserve exactly one transaction");

            replaceAnalyzer(parent, new FailingAnalyzer(parent));

            boolean failureObserved = false;
            try {
                parent.commit(child);
            } catch (InjectedCommitFailure expected) {
                failureObserved = true;
            }
            require(failureObserved, "fault injection did not reach composite commit");

            require(transactionCounter(parent) == 0,
                    "failed commit leaked the child transaction reservation");

            assertFactoryCheckpointDepth(parent.getFunctions(), 0, "functions");
            assertFactoryCheckpointDepth(parent.getFValues(), 0, "fValues");
            assertFactoryCheckpointDepth(parent.getTVars(), 0, "tVars");
            assertFactoryCheckpointDepth(parent.getTValues(), 0, "tValues");
            assertFactoryCheckpointDepth(parent.getDomains(), 0, "domains");
            assertFactoryCheckpointDepth(parent.getRules(), 0, "rules");
            assertFactoryCheckpointDepth(parent.getComments(), 0, "comments");
            assertFactoryCheckpointDepth(parent.getLibrary(), 0, "library");

            System.out.println("MIND_COMMIT_EXCEPTION_ATOMICITY_PASS reservation");
            System.out.println("MIND_COMMIT_EXCEPTION_ATOMICITY_PASS checkpoints");
            System.out.println("MIND_COMMIT_EXCEPTION_ATOMICITY_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
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

        Field stackField = findField(cache.getClass(), "stack");
        stackField.setAccessible(true);
        Object stack = stackField.get(cache);
        int actual = ((java.util.Stack<?>) stack).size();
        require(actual == expected,
                name + " cache checkpoint depth expected " + expected + " but was " + actual);

        // Also reject any non-empty auxiliary rollback stack declared by the factory.
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

    private static final class FailingAnalyzer extends Analyzer {

        private FailingAnalyzer(Mind mind) {
            super(mind);
        }

        @Override
        public boolean checkDatabase(Set<Long> list, boolean logging) {
            throw new InjectedCommitFailure();
        }
    }

    private static final class InjectedCommitFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
