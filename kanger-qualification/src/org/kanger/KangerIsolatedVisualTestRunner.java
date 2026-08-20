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

/**
 * Qualification for the interactive visual-test isolation boundary.
 */
public final class KangerIsolatedVisualTestRunner {

    private KangerIsolatedVisualTestRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            exitCode = test() ? 0 : 1;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    public static boolean test() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String userName = "autotest-isolated-visual-" + suffix;
        String storageName = "isolated_visual_live_" + suffix;

        IUser user = UserFactory.createUser(userName, userName);
        new UDF().init(user);
        new DB().init(user);

        IMind root = new Mind(user);
        root = root.useStorage(storageName);
        require(root.compile("!visualtestroot;"),
                "working root fixture was rejected");

        IMind working = new Mind(root);
        require(working.compile("!visualtesttransaction;"),
                "working transaction fixture was rejected");
        user.setCurrentMind(working);

        Object expectedUser = working.getUser();
        IMind expectedMind = working;
        String expectedStorage = working.getStorageName();
        int expectedLevel = working.getTransactionLevel();

        require(IsolatedKangerTestRuntime.run("01_01", false),
                "isolated offline visual test failed");
        assertWorkingContext(user, expectedUser, expectedMind,
                expectedStorage, expectedLevel, "offline");

        require(IsolatedKangerTestRuntime.run("01_01", true),
                "isolated database visual test failed");
        assertWorkingContext(user, expectedUser, expectedMind,
                expectedStorage, expectedLevel, "database");

        require(Boolean.TRUE.equals(working.query("?visualtesttransaction;")),
                "working transaction content changed during visual tests");
        require(Boolean.TRUE.equals(root.query("?visualtestroot;")),
                "working root content changed during visual tests");

        root.release(working);
        user.setCurrentMind(root);
        root = root.closeStorage();

        System.out.println("Isolated visual test context qualification passed");
        return true;
    }

    private static void assertWorkingContext(IUser user,
                                             Object expectedUser,
                                             IMind expectedMind,
                                             String expectedStorage,
                                             int expectedLevel,
                                             String mode) throws Exception {
        require(user == expectedUser,
                mode + " visual test replaced the working User");
        require(user.getCurrentMind() == expectedMind,
                mode + " visual test replaced the working current Mind");
        require(expectedMind.isStorageUsed(),
                mode + " visual test closed the working storage");
        require(expectedStorage.equals(expectedMind.getStorageName()),
                mode + " visual test changed the working storage");
        require(expectedMind.getTransactionLevel() == expectedLevel,
                mode + " visual test changed the working transaction level");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
