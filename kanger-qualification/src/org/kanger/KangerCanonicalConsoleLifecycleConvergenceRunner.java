/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.command.CommandParser;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;

/**
 * Focused qualification for the canonical Console after the storage/source
 * architecture moved to semantic Mind authority.
 *
 * <p>Canonical storage-use semantics are exercised through the same shared
 * {@link CanonicalCommandProcessor} used by interactive adapters. Reflection is
 * retained only for Console-local presentation/source/Core helpers that have
 * not yet converged into the shared command boundary.</p>
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
            String storageCollision = "console_lifecycle_collision_" + suffix;

            IUser user = UserFactory.createUser(userName, userName);
            new UDF().init(user);
            new DB().init(user);
            Files.createDirectories(new File(user.getSourceDir()).toPath());

            CanonicalCommandProcessor commandProcessor =
                    new CanonicalCommandProcessor();
            CommandParser commandParser = new CommandParser();

            Method erase = privateMethod(
                    CanonicalConsole.class, "erase", IMind.class, Scanner.class);
            Method loadSource = privateMethod(
                    CanonicalConsole.class, "loadSource", IMind.class, String.class);
            Method processCore = privateMethod(
                    CanonicalConsole.class, "processCore", String.class, IMind.class);

            IMind root = new Mind(user);
            user.setCurrentMind(root);

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
            root = useStorage(commandProcessor, commandParser, user, root, storageA);
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
            root = useStorage(commandProcessor, commandParser, user, root, storageA);
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

            IMind rebased = useStorage(
                    commandProcessor, commandParser, user, u2, storageB);
            require(rebased.getTransactionLevel() == 2,
                    "canonical A->B use changed explicit transaction depth");
            require(storageB.equals(rebased.getStorageName()),
                    "canonical A->B use did not switch storage identity");
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

            /* same-generation use follows the shared Core lifecycle contract. */
            boolean sameRejected = false;
            try {
                useStorage(commandProcessor, commandParser, user, root, storageB);
            } catch (StorageLifecycleException expected) {
                sameRejected = true;
                require(StorageLifecycleErrorCode.STORAGE_ALREADY_OPEN.name()
                                .equals(expected.getCode()),
                        "same-storage rejection used the wrong lifecycle code");
                require("EXPLICIT_CLOSE_REQUIRED".equals(expected.getRequiredAction()),
                        "same-storage rejection lost required action");
            }
            require(sameRejected,
                    "same-storage canonical use unexpectedly succeeded");
            require(user.getCurrentMind() == root,
                    "same-storage rejection replaced the published Mind");

            root = root.closeStorage();

            /* No-storage U0 authorial state is assimilated into persistent U0. */
            require(Boolean.TRUE.equals(root.query("!offlineworkspace;")),
                    "offline workspace fixture did not compile");
            IMind inserted = useStorage(
                    commandProcessor, commandParser, user, root, storageA);
            require(inserted.getTransactionLevel() == 0,
                    "offline U0 assimilation created an implicit transaction level");
            require(inserted.getSourceCode().contains("offlineworkspace"),
                    "offline workspace disappeared during baseline assimilation");
            require(inserted.getTop().getSourceCode().contains("offlineworkspace"),
                    "offline workspace was not assimilated into persistent U0");
            root = inserted.closeStorage();
            root = root.useStorage(storageA);
            require(root.getTransactionLevel() == 0,
                    "reopened assimilated storage changed transaction depth");
            require(root.getSourceCode().contains("offlineworkspace"),
                    "assimilated offline workspace did not persist across reopen");
            root = root.closeStorage();

            /* Closed storage must be inserted under the whole explicit stack. */
            Mind offlineU1 = new Mind(root);
            require(Boolean.TRUE.equals(offlineU1.query("!offlineu1;")),
                    "offline U1 fixture did not compile");
            Mind offlineU2 = new Mind(offlineU1);
            require(Boolean.TRUE.equals(offlineU2.query("!offlineu2;")),
                    "offline U2 fixture did not compile");
            user.setCurrentMind(offlineU2);
            IMind offlineRebased = useStorage(
                    commandProcessor, commandParser, user, offlineU2, storageA);
            require(offlineRebased.getTransactionLevel() == 2,
                    "empty offline U0 incorrectly shifted explicit stack depth");
            require(storageA.equals(offlineRebased.getStorageName()),
                    "offline stack insertion opened the wrong storage");
            String offlineRebasedSource = offlineRebased.getSourceCode();
            require(offlineRebasedSource.contains("abase")
                            && offlineRebasedSource.contains("offlineu1")
                            && offlineRebasedSource.contains("offlineu2"),
                    "offline stack insertion lost baseline or explicit delta");
            IMind offlineParent = offlineRebased.getNext();
            offlineParent.release(offlineRebased);
            IMind offlineRoot = offlineParent.getNext();
            offlineRoot.release(offlineParent);
            root = offlineRoot.closeStorage();

            /* Rejected replay must restore the exact offline stack and keep storage closed. */
            root = root.useStorage(storageCollision);
            require(Boolean.TRUE.equals(root.query(
                    "!@x (collisionleft(x) || collisionright(x)) && "
                            + "~(collisionleft(x) && collisionright(x));")),
                    "collision exclusivity fixture did not compile");
            require(Boolean.TRUE.equals(root.query("!collisionleft(One);")),
                    "collision baseline fixture did not compile");
            user.checkpoint(root);
            root = root.closeStorage();

            Mind collisionU1 = new Mind(root);
            require(Boolean.TRUE.equals(collisionU1.query("!collisionright(One);")),
                    "offline collision fixture did not compile before storage insertion");
            user.setCurrentMind(collisionU1);
            boolean collisionRejected = false;
            try {
                useStorage(commandProcessor, commandParser, user,
                        collisionU1, storageCollision);
            } catch (Exception expected) {
                collisionRejected = true;
            }
            require(collisionRejected,
                    "colliding offline stack unexpectedly attached storage");
            IMind restoredOffline = user.getCurrentMind();
            require(restoredOffline != null && !restoredOffline.isStorageUsed(),
                    "rejected storage insertion did not restore offline state");
            require(restoredOffline.getTransactionLevel() == 1,
                    "rejected storage insertion changed explicit transaction depth");
            require(restoredOffline.getSourceCode().contains("collisionright(One)"),
                    "rejected storage insertion lost offline semantic delta");
            IMind restoredRoot = restoredOffline.getNext();
            require(restoredRoot != null,
                    "rejected storage insertion lost offline root");
            restoredRoot.release(restoredOffline);
            root = restoredRoot;

            /* rule level is semantic stack observability, never Rule.mindId provenance. */
            IUser ruleUser = UserFactory.createUser(userName + "-rule-level", userName + "-rule-level");
            new UDF().init(ruleUser);
            new DB().init(ruleUser);
            IMind ruleRoot = new Mind(ruleUser);

            Mind rootBuilder = new Mind(ruleRoot);
            require(Boolean.TRUE.equals(rootBuilder.query("!rulelevelroot;")),
                    "rule-level root fixture did not compile");
            require(ruleRoot.commit(rootBuilder),
                    "rule-level root fixture did not commit");

            Mind ruleU1 = new Mind(ruleRoot);
            require(Boolean.TRUE.equals(ruleU1.query("!rulelevelone;")),
                    "rule-level U1 fixture did not compile");
            Mind ruleU2 = new Mind(ruleU1);
            require(Boolean.TRUE.equals(ruleU2.query("!ruleleveltwo;")),
                    "rule-level U2 fixture did not compile");

            String level0 = captureRuleListing(ruleU2, "rule level 0");
            require(level0.contains("transaction level 0") && level0.contains("rulelevelroot"),
                    "rule level 0 lost committed root semantics");
            require(!level0.contains("rulelevelone") && !level0.contains("ruleleveltwo"),
                    "rule level 0 leaked child semantics");

            String level1 = captureRuleListing(ruleU2, "rule level 1");
            require(level1.contains("transaction level 1") && level1.contains("rulelevelone"),
                    "rule level 1 did not show its semantic delta");
            require(!level1.contains("rulelevelroot") && !level1.contains("ruleleveltwo"),
                    "rule level 1 mixed parent or child semantics");

            String level2 = captureRuleListing(ruleU2, "rule level 2");
            require(level2.contains("transaction level 2") && level2.contains("ruleleveltwo"),
                    "rule level 2 did not show its semantic delta");
            require(!level2.contains("rulelevelroot") && !level2.contains("rulelevelone"),
                    "rule level 2 mixed parent semantics");

            String allLevels = captureRuleListing(ruleU2, "rule level");
            int level0Pos = allLevels.indexOf("transaction level 0");
            int level1Pos = allLevels.indexOf("transaction level 1");
            int level2Pos = allLevels.indexOf("transaction level 2");
            require(level2Pos >= 0 && level1Pos > level2Pos && level0Pos > level1Pos,
                    "rule level did not enumerate the stack current-to-root");
            require(allLevels.contains("rulelevelroot")
                            && allLevels.contains("rulelevelone")
                            && allLevels.contains("ruleleveltwo"),
                    "rule level aggregate lost one or more semantic deltas");

            ruleU1.release(ruleU2);
            ruleRoot.release(ruleU1);

            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS erase-settlement");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS core-exception-settlement");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS storage-stack-rebase");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS source-get-current-level");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS same-storage-rejected");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS offline-baseline-insertion");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS offline-stack-storage-insertion");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS offline-stack-collision-restore");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_PASS rule-level-semantic-stack-top-down");
            System.out.println("CANONICAL_CONSOLE_LIFECYCLE_OK");
            return true;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            return false;
        }
    }

    private static IMind useStorage(CanonicalCommandProcessor processor,
                                    CommandParser parser,
                                    IUser user,
                                    IMind mind,
                                    String logicalName) throws Exception {
        user.setCurrentMind(mind);
        CanonicalCommandProcessor.Result result = processor.execute(
                parser.parse("storage use " + logicalName), user);
        require(result.isHandled(),
                "shared processor did not handle storage use");
        require(result.isSuccess(),
                "shared processor rejected storage use without an exception");
        require(result.getMind() != null,
                "shared processor returned no Mind for storage use");
        return result.getMind();
    }

    private static String captureRuleListing(IMind mind, String command) throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8.name());
        try {
            System.setOut(capture);
            Console.showRules(mind, command);
        } finally {
            System.setOut(previous);
            capture.close();
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
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
