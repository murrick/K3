/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.LogMode;
import org.kanger.interfaces.ILogEntry;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Focused regression gate for Mind transaction and transient-state lifecycle.
 */
public final class KangerMindSafetyRunner {

    private static final String[] TRANSIENT_MAPS = {
            "cvarChilds", "cvarParents", "usedRules", "usedDomains",
            "excludedDomains", "calculatedDomains", "producedDomains",
            "domainCauses", "domainSolves", "queryValues", "ruleSolves",
            "floodControl"
    };

    private KangerMindSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-mind-safety-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            testConcurrentChildren();
            System.out.println("MIND_SAFETY_PASS concurrent-children");

            testTransientClear();
            System.out.println("MIND_SAFETY_PASS transient-clear");

            testDeleteWarning();
            System.out.println("MIND_SAFETY_PASS delete-warning");

            testAbstractivePolicy();
            System.out.println("MIND_SAFETY_PASS abstractive-policy");

            System.out.println("MIND_SAFETY_OK");
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

    private static void testConcurrentChildren() throws Exception {
        final Mind parent = newMind("mind-safety-concurrency");
        final int workers = 32;
        final int rounds = 30;

        for (int round = 0; round < rounds; ++round) {
            final ExecutorService executor = Executors.newFixedThreadPool(workers);
            final CountDownLatch ready = new CountDownLatch(workers);
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(workers);
            final List<Mind> children = Collections.synchronizedList(new ArrayList<Mind>());
            final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());

            for (int i = 0; i < workers; ++i) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        ready.countDown();
                        try {
                            start.await();
                            children.add(new Mind(parent));
                        } catch (Throwable error) {
                            failures.add(error);
                        } finally {
                            done.countDown();
                        }
                    }
                });
            }

            require(ready.await(10, TimeUnit.SECONDS), "Children did not reach the start barrier");
            start.countDown();
            require(done.await(30, TimeUnit.SECONDS), "Children did not finish construction");
            executor.shutdown();
            require(executor.awaitTermination(10, TimeUnit.SECONDS), "Constructor executor did not terminate");
            require(failures.isEmpty(), "Child construction failed: " + failures);
            require(children.size() == workers,
                    "Expected " + workers + " children, got " + children.size());

            Set<Long> ids = new HashSet<Long>();
            for (Mind child : children) {
                ids.add(child.getId());
            }
            require(ids.size() == workers,
                    "Duplicate Mind ids in round " + round + ": " + ids.size());
            require(transactionCounter(parent) == workers,
                    "Lost child reservation in round " + round + ": " + transactionCounter(parent));

            final ExecutorService releases = Executors.newFixedThreadPool(workers);
            final CountDownLatch released = new CountDownLatch(workers);
            for (final Mind child : children) {
                releases.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            parent.release(child);
                        } catch (Throwable error) {
                            failures.add(error);
                        } finally {
                            released.countDown();
                        }
                    }
                });
            }
            require(released.await(30, TimeUnit.SECONDS), "Children did not finish release");
            releases.shutdown();
            require(releases.awaitTermination(10, TimeUnit.SECONDS), "Release executor did not terminate");
            require(failures.isEmpty(), "Child release failed: " + failures);
            require(transactionCounter(parent) == 0,
                    "Transaction counter did not return to zero in round " + round);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void testTransientClear() throws Exception {
        Mind mind = newMind("mind-safety-clear");
        require(Boolean.TRUE.equals(mind.query("!clear_marker(value);")),
                "Failed to create accepted-rule state before clear");
        require(mind.getAcceptedRule() != null, "Expected accepted rule before clear");

        Object oldLinker = field(Mind.class, "linker").get(mind);
        Object sentinel = new Object();
        for (String name : TRANSIENT_MAPS) {
            Map map = (Map) field(Mind.class, name).get(mind);
            map.put(sentinel, sentinel);
        }

        mind = (Mind) mind.clearWorkspace();
        for (String name : TRANSIENT_MAPS) {
            Map map = (Map) field(Mind.class, name).get(mind);
            require(map.isEmpty(), "Transient map was retained after clear: " + name);
        }
        require(mind.getAcceptedRule() == null, "Accepted rule survived workspace clear");
        require(mind.getQueryResult() == null, "Query result survived workspace clear");
        require("".equals(mind.getQueryString()), "Query source survived workspace clear");
        require(field(Mind.class, "linker").get(mind) != oldLinker,
                "Linker query-local indexes were not discarded by clear");
    }

    private static void testDeleteWarning() throws Exception {
        Mind mind = newMind("mind-safety-delete");
        Boolean result = mind.query("-missing(value);");
        require(result == null, "Missing delete should remain unresolved, got " + result);
        ILogEntry entry = mind.getCurrentLogRecord(LogMode.ANALYZER);
        require(String.valueOf(entry).contains("WARNING: No candidates to delete"),
                "Missing-delete warning was not transferred to the parent log: " + entry);
    }

    private static void testAbstractivePolicy() throws Exception {
        Mind parent = newMind("mind-safety-abstractive");
        require(!parent.includeAbstractiveHypothesis(), "Policy must be disabled by default");

        Mind disabledChild = new Mind(parent);
        require(!disabledChild.includeAbstractiveHypothesis(),
                "Default-disabled policy was not inherited");
        parent.release(disabledChild);

        parent.includeAbstractiveHypothesis(true);
        Mind enabledChild = new Mind(parent);
        require(enabledChild.includeAbstractiveHypothesis(),
                "Enabled policy was not inherited by child Mind");
        parent.release(enabledChild);

        parent = (Mind) parent.clearWorkspace();
        require(parent.includeAbstractiveHypothesis(),
                "Workspace clear unexpectedly changed the caller-selected policy");
        Mind afterClear = new Mind(parent);
        require(afterClear.includeAbstractiveHypothesis(),
                "Enabled policy was not inherited after workspace clear");
        parent.release(afterClear);
    }

    private static int transactionCounter(Mind mind) throws Exception {
        return field(Mind.class, "transactionCounter").getInt(mind);
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
