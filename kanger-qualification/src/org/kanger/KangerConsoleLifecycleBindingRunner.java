/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Focused regression gate for Console bindings of the explicit storage
 * lifecycle contract and its diagnostic presentation.
 *
 * <p>The test invokes private command adapters reflectively so qualification
 * does not widen the production Console API merely for test access.</p>
 */
public final class KangerConsoleLifecycleBindingRunner {

    private KangerConsoleLifecycleBindingRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        String originalHome = System.getProperty("user.home");

        try {
            Path testHome = Files.createTempDirectory(
                    "kanger-console-lifecycle-");
            System.setProperty(
                    "user.home",
                    testHome.toAbsolutePath().toString());

            exitCode = test() ? 0 : 1;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }

        System.exit(exitCode);
    }

    public static boolean test() {
        IMind mind = null;

        try {
            Method processTransaction = privateMethod(
                    Console.class,
                    "processTransaction",
                    String.class,
                    IMind.class,
                    Scanner.class);

            Method trackedTransaction = privateMethod(
                    Console.class,
                    "processTransaction",
                    String.class,
                    IMind.class,
                    Scanner.class,
                    ShutdownHook.class);

            Method showDBrief = privateMethod(
                    Console.class,
                    "showDBrief",
                    IMind.class);

            Method closeDatabase = privateMethod(
                    Console.class,
                    "closeDatabase",
                    IMind.class,
                    Scanner.class);

            String suffix = Long.toString(System.nanoTime());
            String userName =
                    "autotest-console-lifecycle-" + suffix;

            IUser user = UserFactory.createUser(
                    userName,
                    userName);
            new UDF().init(user);
            new DB().init(user);

            /*
             * Level-0 commit without storage is an idempotent no-op.
             */
            mind = new Mind(user);
            final IMind noStorageMind = mind;

            Capture<IMind> noStorageCommit =
                    capture(new ThrowingSupplier<IMind>() {
                        @Override
                        public IMind get() throws Exception {
                            return process(
                                    processTransaction,
                                    "transaction commit",
                                    noStorageMind);
                        }
                    });

            require(noStorageCommit.value == noStorageMind,
                    "root no-storage commit replaced the active Mind");
            require(!noStorageCommit.value.isStorageUsed(),
                    "root no-storage commit opened storage");
            require(noStorageCommit.output.contains(
                            "Transaction level 0"),
                    "root no-storage commit did not report level 0");
            require(!noStorageCommit.output.contains(
                            "Storage checkpoint completed"),
                    "root no-storage commit reported a false checkpoint");

            /*
             * Level-0 commit with storage performs a checkpoint but retains
             * the open storage generation.
             */
            String requestedStorage =
                    "console.lifecycle.binding." + suffix;

            mind = mind.useStorage(requestedStorage);
            final IMind openMind = mind;
            final String openedStorage = mind.getStorageName();

            Capture<IMind> checkpoint =
                    capture(new ThrowingSupplier<IMind>() {
                        @Override
                        public IMind get() throws Exception {
                            return process(
                                    processTransaction,
                                    "transaction commit",
                                    openMind);
                        }
                    });

            mind = checkpoint.value;

            require(mind.isStorageUsed(),
                    "root checkpoint closed storage");
            require(openedStorage.equals(mind.getStorageName()),
                    "root checkpoint changed the storage target");
            require(checkpoint.output.contains(
                            "SUCCESS: Storage checkpoint completed"),
                    "root storage commit did not invoke checkpoint");
            require(checkpoint.output.contains(
                            "Transaction level 0"),
                    "root checkpoint changed transaction level");

            /*
             * Presentation must distinguish root logical state from the
             * chain-shared canonical runtime registries.
             */
            require(mind.compile("!consolebinding;"),
                    "qualification assertion was rejected");

            final IMind contentMind = mind;

            Capture<Void> brief =
                    capture(new ThrowingSupplier<Void>() {
                        @Override
                        public Void get() throws Exception {
                            invoke(showDBrief, contentMind);
                            return null;
                        }
                    });

            require(brief.output.contains(
                            "Transaction level: 0"),
                    "database brief omits transaction level");
            require(brief.output.contains(
                            "Root state: Rules "),
                    "database brief omits root logical state");
            require(brief.output.contains(
                            "Runtime canonical cache: Predicates "),
                    "database brief omits runtime canonical cache");
            require(!brief.output.contains("\nRules: "),
                    "database brief retained the ambiguous legacy format");

            /*
             * Predicate diagnostics must use the Mind-aware semantic
             * rendering rather than Object.toString().
             */
            Capture<Void> predicates =
                    capture(new ThrowingSupplier<Void>() {
                        @Override
                        public Void get() throws Exception {
                            Console.showBase(
                                    contentMind,
                                    "base predicates");
                            return null;
                        }
                    });

            require(predicates.output.contains(
                            "consolebinding(0);"),
                    "predicate output is not semantically rendered: "
                            + predicates.output);
            require(!predicates.output.contains(
                            "org.kanger.units.Predicate@"),
                    "predicate output leaked Object.toString(): "
                            + predicates.output);

            /*
             * DUMB does not currently expose optional operation counters.
             * Unsupported metrics must be explicit N/A, never false zeros.
             */
            String diagnostics = Diagnostics.snapshot(
                    contentMind,
                    "console-lifecycle-binding");

            require(diagnostics.contains(
                            "storage.total.get: N/A"),
                    "unsupported get metric is not reported as N/A");
            require(diagnostics.contains(
                            "storage.total.cache.hit: N/A"),
                    "unsupported cache-hit metric is not reported as N/A");
            require(diagnostics.contains(
                            "storage.total.cache.miss: N/A"),
                    "unsupported cache-miss metric is not reported as N/A");
            require(diagnostics.contains(
                            "storage.total.physical.read: N/A"),
                    "unsupported physical-read metric is not reported as N/A");
            require(diagnostics.contains(
                            "storage.total.write: N/A"),
                    "unsupported write metric is not reported as N/A");
            require(diagnostics.contains(
                            "storage.total.delete: N/A"),
                    "unsupported delete metric is not reported as N/A");
            require(diagnostics.contains(
                            "storage.total.flush: N/A"),
                    "unsupported flush metric is not reported as N/A");

            /*
             * Console close must delegate directly to Core. A non-empty
             * active transaction is rejected with the typed lifecycle error;
             * Console must not ask for a force-close confirmation.
             */
            IMind child = new Mind(mind);
            require(child.compile("!consoleactive;"),
                    "active-transaction assertion was rejected");

            boolean activeTransactionRejected = false;
            try {
                close(closeDatabase, child);
            } catch (StorageLifecycleException expected) {
                activeTransactionRejected = true;
                require("ACTIVE_TRANSACTION".equals(
                                expected.getCode()),
                        "wrong active-close code: "
                                + expected.getCode());
                require("TRANSACTION_RESOLUTION_REQUIRED".equals(
                                expected.getRequiredAction()),
                        "wrong active-close required action: "
                                + expected.getRequiredAction());
            }

            require(activeTransactionRejected,
                    "Console close accepted an active transaction");
            require(child.getTransactionLevel() == 1,
                    "failed Console close changed transaction level");
            require(child.isStorageUsed(),
                    "failed Console close detached storage");

            mind.release(child);

            mind = mind.closeStorage();
            require(!mind.isStorageUsed(),
                    "qualification storage did not close cleanly");

            /*
             * JVM shutdown must retain Console ownership of the active Mind.
             * An unfinished child transaction is rolled back rather than
             * committed implicitly, then the root follows the ordinary Core
             * checkpoint/close path. The resulting storage must reopen with
             * committed root content only.
             */
            String shutdownStorage =
                    "console.shutdown.binding." + suffix;
            mind = mind.useStorage(shutdownStorage);
            require(mind.compile("!shutdowncommitted;"),
                    "shutdown committed assertion was rejected");

            ShutdownHook shutdownHook = new ShutdownHook(mind);
            mind = process(
                    trackedTransaction,
                    "transaction start",
                    mind,
                    shutdownHook);

            require(mind.getTransactionLevel() == 1,
                    "tracked transaction start did not create level 1");
            require(shutdownHook.getMind() == mind,
                    "shutdown hook did not receive active child Mind");
            require(mind.compile("!shutdowntransient;"),
                    "shutdown transient assertion was rejected");

            shutdownHook.shutdown();
            mind = shutdownHook.getMind();

            require(mind != null,
                    "shutdown discarded the root Mind");
            require(mind.getTransactionLevel() == 0,
                    "shutdown did not unwind to transaction level 0");
            require(!mind.isStorageUsed(),
                    "shutdown did not close physical storage");

            mind = mind.useStorage(shutdownStorage);
            String reopenedSource = mind.getSourceCode();

            require(reopenedSource.contains("shutdowncommitted"),
                    "shutdown lost committed root content");
            require(!reopenedSource.contains("shutdowntransient"),
                    "shutdown implicitly committed active child content");

            mind = mind.closeStorage();
            shutdownHook.setMind(mind);
            shutdownHook.shutdown();

            require(shutdownHook.getMind() == mind,
                    "closed-storage shutdown replaced the root Mind");
            require(!shutdownHook.getMind().isStorageUsed(),
                    "closed-storage shutdown reopened storage");

            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_PASS idempotent-root-commit");
            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_PASS retained-checkpoint");
            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_PASS typed-active-close");
            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_PASS semantic-presentation");
            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_PASS diagnostic-na");
            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_PASS shutdown-active-rollback");
            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_PASS shutdown-root-close");
            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_PASS shutdown-idempotent-closed");
            System.out.println(
                    "CONSOLE_LIFECYCLE_BINDING_OK");

            return true;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            return false;
        }
    }

    private static Method privateMethod(
            Class<?> type,
            String name,
            Class<?>... parameterTypes) throws Exception {

        Method method = type.getDeclaredMethod(
                name,
                parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static IMind process(
            Method method,
            String line,
            IMind mind) throws Exception {

        Scanner scanner = new Scanner("");
        try {
            return (IMind) invoke(
                    method,
                    line,
                    mind,
                    scanner);
        } finally {
            scanner.close();
        }
    }

    private static IMind process(
            Method method,
            String line,
            IMind mind,
            ShutdownHook shutdownHook) throws Exception {

        Scanner scanner = new Scanner("");
        try {
            return (IMind) invoke(
                    method,
                    line,
                    mind,
                    scanner,
                    shutdownHook);
        } finally {
            scanner.close();
        }
    }

    private static IMind close(
            Method method,
            IMind mind) throws Exception {

        Scanner scanner = new Scanner("");
        try {
            return (IMind) invoke(
                    method,
                    mind,
                    scanner);
        } finally {
            scanner.close();
        }
    }

    private static Object invoke(
            Method method,
            Object... arguments) throws Exception {

        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();

            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }

            throw new RuntimeException(cause);
        }
    }

    private static <T> Capture<T> capture(
            ThrowingSupplier<T> supplier) throws Exception {

        PrintStream original = System.out;
        ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();
        PrintStream redirected =
                new PrintStream(bytes, true, "UTF-8");

        try {
            System.setOut(redirected);
            T value = supplier.get();
            redirected.flush();

            return new Capture<T>(
                    value,
                    bytes.toString("UTF-8"));
        } finally {
            System.setOut(original);
            redirected.close();
        }
    }

    private static void require(
            boolean condition,
            String message) {

        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class Capture<T> {
        private final T value;
        private final String output;

        private Capture(T value, String output) {
            this.value = value;
            this.output = output;
        }
    }
}
