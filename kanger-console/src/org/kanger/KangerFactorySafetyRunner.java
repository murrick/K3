/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.LibMode;
import org.kanger.factory.CommentFactory;
import org.kanger.factory.LibraryFactory;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.FValue;
import org.kanger.units.Function;
import org.kanger.units.Operation;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Regression gate for factory ownership, transaction isolation and lifecycle.
 */
public final class KangerFactorySafetyRunner {

    private KangerFactorySafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-factory-safety-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            testRuntimeOwnership();
            System.out.println("FACTORY_SAFETY_PASS runtime-ownership");

            testStorageAnchorReset();
            System.out.println("FACTORY_SAFETY_PASS anchor-reset");

            testOrphanCleanup();
            System.out.println("FACTORY_SAFETY_PASS orphan-cleanup");

            testCommentOverlay();
            System.out.println("FACTORY_SAFETY_PASS comment-overlay");

            testLibraryOverlay();
            System.out.println("FACTORY_SAFETY_PASS library-overlay");

            testConcurrentMetadata();
            System.out.println("FACTORY_SAFETY_PASS concurrent-metadata");

            System.out.println("FACTORY_SAFETY_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static Mind newMind(String name) throws Exception {
        IUser user = UserFactory.createUser(name, name);
        new UDF().init(user);
        new DB().init(user);
        return new Mind(user);
    }

    private static void testRuntimeOwnership() throws Exception {
        Mind parent = newMind("factory-safety-ownership");
        Mind child = new Mind(parent);

        Rule owner = new Rule(child);
        child.getRules().register(owner);
        TVariable variable = child.getTVars().createTVar(
                owner, child.getTerms().add("factory_owner_variable"));
        require(variable.getMindId() == child.getId(),
                "TVariable retained wrong transaction id: " + variable.getMindId());
        require(variable.getMind() == child,
                "TVariable retained another Mind instance");

        ArgumentsList arguments = new ArgumentsList();
        arguments.add(new Argument(child.getTerms().add(1.0)));
        Function function = child.getFunctions().add(
                child.getTerms().add("factory_owner_function"), arguments);
        require(function.setParameter(function.getRange(), child.getTerms().add(2.0)),
                "Unable to complete ownership Function");
        FValue value = child.getFValues().add(function);
        require(value != null, "Ownership Function did not create an FValue");
        require(value.getMindId() == child.getId(),
                "FValue retained wrong transaction id: " + value.getMindId());
        require(value.getMind() == child,
                "FValue retained another Mind instance");

        parent.release(child);
    }

    private static void testStorageAnchorReset() throws Exception {
        Mind mind = newMind("factory-safety-anchors");
        String storageName = "data/factory-safety-anchors";
        try {
            mind = (Mind) mind.useStorage(storageName);
            require(Boolean.TRUE.equals(mind.query("!factory_anchor(value);")),
                    "Failed to populate persistent factory anchors");

            for (Object factory : factories(mind)) {
                require(field(factory.getClass(), "connection").get(factory) != null,
                        "Open storage factory has no connection: " + factory.getClass().getSimpleName());
            }

            mind = (Mind) mind.closeStorage();
            for (Object factory : factories(mind)) {
                require(field(factory.getClass(), "top").get(factory) == null,
                        "Factory retained stale top after close: " + factory.getClass().getSimpleName());
                require(field(factory.getClass(), "connection").get(factory) == null,
                        "Factory retained closed connection: " + factory.getClass().getSimpleName());
            }
            require(field(mind.getRules().getClass(), "bottom").get(mind.getRules()) == null,
                    "RuleFactory retained stale bottom after close");

            mind = (Mind) mind.useStorage(storageName);
            for (Object factory : factories(mind)) {
                require(field(factory.getClass(), "connection").get(factory) != null,
                        "Reopened factory has no current connection: " + factory.getClass().getSimpleName());
                require(field(factory.getClass(), "top").get(factory) == null,
                        "Reopened factory retained an old top: " + factory.getClass().getSimpleName());
            }
        } finally {
            if (mind.isStorageUsed()) {
                mind = (Mind) mind.closeStorage();
            }
            if (mind.isStorageExists(storageName)) {
                mind.removeStorage(storageName);
            }
        }
    }

    private static Object[] factories(Mind mind) {
        return new Object[]{
                mind.getTerms(), mind.getPredicates(), mind.getDomains(), mind.getRules(),
                mind.getTVars(), mind.getTValues(), mind.getFunctions(), mind.getFValues(),
                mind.getComments(), mind.getLibrary()
        };
    }

    private static void testOrphanCleanup() throws Exception {
        Mind parent = newMind("factory-safety-orphan");
        parent.pack();
        Mind child = new Mind(parent);
        ITerm orphan = child.getTerms().add("factory_orphan_" + System.nanoTime());
        long orphanId = orphan.getId();
        require(parent.getTerms().get(orphanId) != null,
                "Shared dictionary did not expose the child-created term");
        parent.release(child);
        require(parent.getTerms().get(orphanId) == null,
                "Unreferenced child term survived root pack");
    }

    private static void testCommentOverlay() throws Exception {
        Mind parent = newMind("factory-safety-comment");
        parent.getComments().add(CommentFactory.HEADER_ID, "original");

        Mind discarded = new Mind(parent);
        discarded.getComments().add(CommentFactory.HEADER_ID, "discarded");
        require("original".equals(parent.getComments().get(CommentFactory.HEADER_ID).getComment()),
                "Child comment update leaked into parent before release");
        require("discarded".equals(discarded.getComments().get(CommentFactory.HEADER_ID).getComment()),
                "Child does not see its comment overlay");
        parent.release(discarded);
        require("original".equals(parent.getComments().get(CommentFactory.HEADER_ID).getComment()),
                "Released comment overlay changed parent");

        Mind committed = new Mind(parent);
        committed.getComments().add(CommentFactory.HEADER_ID, "committed");
        require(parent.commit(committed), "Comment overlay commit failed");
        require("committed".equals(parent.getComments().get(CommentFactory.HEADER_ID).getComment()),
                "Committed comment overlay was not published");
    }

    private static Operation constantOperation(final String name, final double value) {
        Operation operation = new Operation(LibMode.FUNCTION, name, 1, new IReactor<Function>() {
            @Override
            public Object run(Function function) throws Exception {
                Mind context = function.getMind();
                ITerm expected = context.getTerms().add(value);
                IArgument result = function.getArguments().get(function.getRange());
                if (result.isEmpty(context)) {
                    return function.setParameter(function.getRange(), expected) ? 1 : 0;
                }
                return result.getValue(context).getId() == expected.getId() ? 2 : 0;
            }
        });
        operation.getParams().add("x");
        operation.getParams().add(name);
        operation.getScripts().add(name + " = " + value + ";");
        return operation;
    }

    private static void testLibraryOverlay() throws Exception {
        Mind parent = newMind("factory-safety-library");
        LibraryFactory library = parent.getLibrary();
        library.add(constantOperation("factory_policy", 10.0));

        Mind discarded = new Mind(parent);
        discarded.getLibrary().add(constantOperation("factory_policy", 20.0));
        require(scriptValue(parent.getLibrary().find("factory_policy(1)")).contains("10.0"),
                "Child UDF redefine leaked into parent before release");
        require(scriptValue(discarded.getLibrary().find("factory_policy(1)")).contains("20.0"),
                "Child does not see its UDF overlay");
        parent.release(discarded);
        require(scriptValue(parent.getLibrary().find("factory_policy(1)")).contains("10.0"),
                "Released UDF overlay changed parent");

        Mind committed = new Mind(parent);
        committed.getLibrary().add(constantOperation("factory_policy", 20.0));
        require(parent.commit(committed), "UDF overlay commit failed");
        require(scriptValue(parent.getLibrary().find("factory_policy(1)")).contains("20.0"),
                "Committed UDF overlay was not published");
    }

    private static String scriptValue(IOperation operation) {
        require(operation != null, "Expected operation is missing");
        return operation.getScripts().isEmpty() ? "" : operation.getScripts().get(0);
    }

    private static void testConcurrentMetadata() throws Exception {
        final Mind parent = newMind("factory-safety-concurrency");
        require(Boolean.TRUE.equals(parent.query("!@x factory_source(x) -> factory_result(x);")),
                "Failed to install concurrency rule");

        final int workers = 16;
        final CountDownLatch ready = new CountDownLatch(workers);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(workers);
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());
        final ExecutorService executor = Executors.newFixedThreadPool(workers);

        for (int i = 0; i < workers; ++i) {
            final int valueIndex = i;
            final Mind child = new Mind(parent);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        start.await();
                        require(Boolean.TRUE.equals(child.query("!factory_source(v" + valueIndex + ");")),
                                "Child insertion failed: " + valueIndex);
                        require(parent.commit(child), "Child commit failed: " + valueIndex);
                    } catch (Throwable error) {
                        failures.add(error);
                    } finally {
                        done.countDown();
                    }
                }
            });
        }

        require(ready.await(20, TimeUnit.SECONDS), "Workers did not reach start barrier");
        start.countDown();
        require(done.await(90, TimeUnit.SECONDS), "Concurrent factory workers timed out");
        executor.shutdown();
        require(executor.awaitTermination(20, TimeUnit.SECONDS), "Factory executor did not terminate");
        require(failures.isEmpty(), "Concurrent factory failure: " + failures);

        require(Boolean.TRUE.equals(parent.query("?$x factory_result(x);")),
                "Concurrent result query did not resolve");
        require(parent.getValues().size() == workers,
                "Expected " + workers + " committed values, got " + parent.getValues().size());
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
