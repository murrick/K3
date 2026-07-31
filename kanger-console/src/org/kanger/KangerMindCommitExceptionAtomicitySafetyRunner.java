/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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
            IUser user = UserFactory.createUser("mind-commit-exception-atomicity-" + + System.nanoTime(),
                    "mind-commit-exception-atomicity");
            new UDF().init(user);
            new DB().init(user);

            Mind parent = new Mind(user);
            Mind child = new Mind(parent);
            addDirectRule(child, "commit_exception_atomicity_probe");

            Mind sibling = new Mind(parent);
            addDirectRule(sibling, "commit_exception_atomicity_sibling");
            require(transactionCounter(parent) == 2,
                    "two open children did not reserve two transactions");
            require(parent.commit(sibling),
                    "fixture sibling commit unexpectedly failed");
            require(transactionCounter(parent) == 1,
                    "sibling commit did not leave exactly the original child reservation");

            replaceAnalyzer(parent, new FailingAnalyzer(parent));

            boolean failureObserved = false;
            try {
                parent.commit(child);
            } catch (InjectedCommitFailure expected) {
                failureObserved = true;
            }
            require(failureObserved, "fault injection did not reach composite commit");

            List<String> leaks = new ArrayList<>();
            int counter = transactionCounter(parent);
            if (counter != 0) {
                leaks.add("transactionCounter expected 0 but was " + counter);
            }

            collectFactoryCheckpointLeaks(parent.getFunctions(), "functions", leaks);
            collectFactoryCheckpointLeaks(parent.getFValues(), "fValues", leaks);
            collectFactoryCheckpointLeaks(parent.getTVars(), "tVars", leaks);
            collectFactoryCheckpointLeaks(parent.getTValues(), "tValues", leaks);
            collectFactoryCheckpointLeaks(parent.getDomains(), "domains", leaks);
            collectFactoryCheckpointLeaks(parent.getRules(), "rules", leaks);
            collectFactoryCheckpointLeaks(parent.getComments(), "comments", leaks);
            collectFactoryCheckpointLeaks(parent.getLibrary(), "library", leaks);

            if (!leaks.isEmpty()) {
                for (String leak : leaks) {
                    System.err.println("MIND_COMMIT_EXCEPTION_ATOMICITY_LEAK " + leak);
                }
                throw new AssertionError("failed commit leaked " + leaks.size()
                        + " composite transaction resources");
            }

            System.out.println("MIND_COMMIT_EXCEPTION_ATOMICITY_PASS reservation");
            System.out.println("MIND_COMMIT_EXCEPTION_ATOMICITY_PASS checkpoints");
            System.out.println("MIND_COMMIT_EXCEPTION_ATOMICITY_OK");
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

    private static int transactionCounter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private static void collectFactoryCheckpointLeaks(Object factory,
                                                       String name,
                                                       List<String> leaks)
            throws Exception {
        Field cacheField = findField(factory.getClass(), "cache");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(factory);

        Field stackField = findField(cache.getClass(), "stack");
        stackField.setAccessible(true);
        int cacheDepth = ((java.util.Stack<?>) stackField.get(cache)).size();
        if (cacheDepth != 0) {
            leaks.add(name + ".cache checkpoint depth expected 0 but was " + cacheDepth);
        }

        for (Field field : factory.getClass().getDeclaredFields()) {
            if (java.util.Stack.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                int depth = ((java.util.Stack<?>) field.get(factory)).size();
                if (depth != 0) {
                    leaks.add(name + "." + field.getName()
                            + " depth expected 0 but was " + depth);
                }
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
