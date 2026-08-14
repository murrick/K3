/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;

/**
 * Focused qualification for the canonical Console after the storage/source
 * architecture moved to semantic Mind authority.
 */
public final class KangerCanonicalConsoleLifecycleConvergenceRunner {

    private KangerCanonicalConsoleLifecycleConvergenceRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            exitCode = test() ? 0 : 1;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    public static boolean test() {
        try {
            String suffix = Long.toString(System.nanoTime());
            String userName = "autotest-canonical-console-lifecycle-" + suffix;
            String storageA = "console_lifecycle_a_" + suffix;
            String storageB = "console_lifecycle_b_" + suffix;

            IUser user = UserFactory.createUser(userName, userName);
            new UDF().init(user);
            new DB().init(user);
            Files.createDirectories(new File(user.getSourceDir()).toPath());

            Method erase = privateMethod(
                    CanonicalConsole.class, "erase", IMind.class, Scanner.class);
            Method useStorage = privateMethod(
                    CanonicalConsole.class, "useStorage", IMind.class, String.class);
            Method loadSource = privateMethod(
                    CanonicalConsole.class, "loadSource", IMind.class, String.class);
            Method processCore = privateMethod(
                    CanonicalConsole.class, "processCore", String.class, IMind.class);

            IMind root = new Mind(user);

            /* erase must settle an explicit child instead of orphaning its reservation. */
            Mind eraseChild = new Mind(root);
            require(Boolean.TRUE.equals(eraseChild.query("!erasechild;")),
                    "erase fixture did not create child content");
            Scanner yes = new Scanner("y\n");
            try {
                root = (IMind) invoke(erase, eraseChild, yes);
            } finally {
                yes.close();
            }
            require(root.getTransactionLevel() == 0,
                    "erase did not return the published root");
            require(root.isEmptyLevel(),
                    "erase did not clear the root workspace");
            root = (IMind) invoke(useStorage, root, storageA);
            require(root.isStorageUsed(),
                    "storage use after erase was blocked by an orphan reservation");
            require(storageA.equals(root.getStorageName()),
                    "storage use after erase opened the wrong storage");
            root = root.closeStorage();

            /* malformed Core execution must not leak its operation-local Console overlay. */
            boolean failed = false;
            try {
                invoke(processCore, "!broken(;", root);
            } catch (Exception expected) {
                failed = true;
            }
            require(failed,
                    "malformed Core fixture unexpectedly succeeded");
            root = (IMind) invoke(useStorage, root, storageA);
            require(root.isStorageUsed(),
                    "storage use after Core failure saw a leaked child reservation");
            root = root.closeStorage();

            /* Build two durable roots for an A->B semantic-stack rebase. */
            root = root.useStorage(storageA);
            require(Boolean.TRUE.equals(root.query("!abase;")),
                    "storage A baseline did not compile");
            user.checkpoint(root);
            root = root.closeStorage();

            root = root.useStorage(storageB);
            require(Boolean.TRUE.equals(root.query("!bbase;")),
                    "storage B baseline did not compile");
            user.checkpoint(root);
            root = root.closeStorage();

            root = root.useStorage(storageA);
            Mind u1 = new Mind(root);
            require(Boolean.TRUE.equals(u1.query("!consoleu1;")),
                    "U1 fixture did not compile");
            Mind u2 = new Mind(u1);
            require(Boolean.TRUE.equals(u2.query("!consoleu2;")),
                    "U2 fixture did not compile");

            IMind rebased = (IMind) invoke(useStorage, u2, storageB);
            require(rebased.getTransactionLevel() == 2,
                    "Console A->B use changed explicit transaction depth");
            require(storageB.equals(rebased.getStorageName()),
                    "Console A->B use did not switch storage identity");
            String rebasedSource = rebased.getSourceCode();
            require(rebasedSource.contains("bbase"),
                    "target storage baseline is missing after rebase");
            require(rebasedSource.contains("consoleu1"),
                    "U1 semantic delta is missing after rebase");
            require(rebasedSource.contains("consoleu2"),
                    "U2 semantic delta is missing after rebase");
            require(!rebasedSource.contains("abase"),
                    "source storage baseline leaked across A->B rebase");

            IMind rebasedU1 = rebased.getNext();
            require(rebasedU1 != null,
                    "rebased U2 lost its parent");
            rebasedU1.release(rebased);
            IMind rebasedRoot = rebasedU1.getNext();
            require(rebasedRoot != null,
                    "rebased U1 lost its root");
            rebasedRoot.release(rebasedU1);
            root = rebasedRoot;
            require(root.getTransactionLevel() == 0,
                    "rebased stack did not roll back to root");
            String rolledBack = root.getSourceCode();
            require(rolledBack.contains("bbase"),
                    "target baseline disappeared after rollback");
            require(!rolledBack.contains("consoleu1") && !rolledBack.contains("consoleu2"),
                    "Console deltas survived rollback after A->B rebase");

            /* get imports into the current explicit level; it must not create U(n+1). */
            Mind getLevel = new Mind(root);
            String sourceName = "console-get-" + suffix + ".k";
            File sourceFile = new File(user.getSourceDir(), sourceName);
            Files.write(sourceFile.toPath(),
                    "!consolegetdelta;\n".getBytes(StandardCharsets.UTF_8));
            IMind loaded = (IMind) invoke(loadSource, getLevel, sourceName);
            require(loaded == getLevel,
                    "Console get replaced the current explicit Mind");
            require(loaded.getTransactionLevel() == 1,
                    "Console get created an implicit transaction level");
            require(loaded.getSourceCode().contains("consolegetdelta"),
                    "Console get did not import source into current U1");
            root.release(loaded);
            Files.deleteIfExists(sourceFile.toPath());

            /* same-generation use is presentation-idempotent, not a second rebase. */
            IMind same = (IMind) invoke(useStorage, root, storageB);
            require(same == root,
                    "same-storage Console use unexpectedly replaced the Mind");

            root = root.closeStorage();

            /* No-storage U0 insertion remains a deliberate compatibility case. */
            require(Boolean.TRUE.equals(root.query("!offlineworkspace;")),
                    "offline workspace fixture did not compile");
            IMind inserted = (IMind) invoke(useStorage, root, storageA);
            require(inserted.getTransactionLevel() == 1,
                    "offline workspace insertion did not create U1");
            require(inserted.getSourceCode().contains("offlineworkspace"),
                    "offline workspace disappeared during baseline insertion");
            require(!inserted.getTop().getSourceCode().contains("offlineworkspace"),
                    "offline workspace was committed into persistent U0");
            IMind insertedRoot = inserted.getNext();
            insertedRoot.release(inserted);
            root = insertedRoot;
            root = root.closeStorage();

            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS erase-settlement");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS core-exception-settlement");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS storage-stack-rebase");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS source-get-current-level");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS same-storage-idempotent");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS offline-baseline-insertion");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_OK");
            return true;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            return false;
        }
    }

    private static Method privateMethod(
            Class<?> type,
            String name,
            Class<?>... parameterTypes) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object... arguments) throws Exception {
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
