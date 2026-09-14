/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Regression for dropping the active storage through ordinary close semantics. */
public final class StorageDropCloseSemanticsTest {

    @Test
    public void activeTransactionDropRebasesLikeCloseAfterConfirmation() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String userName = "autotest-storage-drop-close-" + suffix;
        String storageName = "drop_close_" + suffix;

        IUser user = UserFactory.createUser(userName, userName);
        new UDF().init(user);
        new DB().init(user);

        IMind root = new Mind(user);
        user.setCurrentMind(root);
        root = root.useStorage(storageName);
        user.setCurrentMind(root);

        Mind child = new Mind(root);
        user.setCurrentMind(child);

        CommandInvocation drop = new CommandParser().parse(
                "storage drop " + storageName);
        Method dispatch = CanonicalConsole.class.getDeclaredMethod(
                "dispatch",
                CommandInvocation.class,
                IMind.class,
                ConsoleLineInput.class,
                ShutdownHook.class);
        dispatch.setAccessible(true);

        ByteArrayOutputStream cancelledOutput = new ByteArrayOutputStream();
        Throwable cancelled = invokeDispatch(
                dispatch, drop, child, user, "n\n", cancelledOutput);
        if (cancelled != null) {
            fail("cancelled drop unexpectedly failed", cancelled);
        }

        assertTrue(text(cancelledOutput).contains(
                        "Drop storage " + storageName + "?"),
                "drop must request destructive confirmation before closing storage");
        assertSame(child, user.getCurrentMind(),
                "declined drop must not replace the current Mind");
        assertEquals(1, child.getTransactionLevel(),
                "declined drop must preserve the active transaction");
        assertTrue(child.isStorageUsed(),
                "declined drop must keep the current storage open");
        assertEquals(storageName, child.getStorageName(),
                "declined drop must preserve storage identity");
        assertTrue(hasStorage(child, storageName),
                "declined drop must leave the storage intact");

        ByteArrayOutputStream confirmedOutput = new ByteArrayOutputStream();
        Throwable confirmed = invokeDispatch(
                dispatch, drop, child, user, "y\n", confirmedOutput);
        if (confirmed != null) {
            fail("confirmed active-transaction drop unexpectedly failed", confirmed);
        }

        String output = text(confirmedOutput);
        assertTrue(output.contains("Drop storage " + storageName + "?"),
                "confirmed drop must retain destructive confirmation");
        assertTrue(output.contains("Database " + storageName + " dropped"),
                "confirmed drop must report successful deletion");

        IMind dropped = user.getCurrentMind();
        assertNotNull(dropped,
                "confirmed drop must publish the rebased Mind");
        assertEquals(1, dropped.getTransactionLevel(),
                "drop must preserve the explicit U-stack exactly as close does");
        assertNotNull(dropped.getNext(),
                "rebased U1 must retain its offline U0 parent");
        assertEquals(0, dropped.getNext().getTransactionLevel(),
                "rebased parent must remain U0");
        assertFalse(dropped.isStorageUsed(),
                "confirmed active-storage drop must leave the session offline");
        assertFalse(hasStorage(dropped, storageName),
                "confirmed drop must remove the physical storage");
    }

    private static Throwable invokeDispatch(Method dispatch,
                                            CommandInvocation invocation,
                                            IMind mind,
                                            IUser user,
                                            String inputText,
                                            ByteArrayOutputStream output)
            throws Exception {
        InputStream previousIn = System.in;
        PrintStream previousOut = System.out;
        ConsoleLineInput input = null;
        try {
            System.setIn(new ByteArrayInputStream(
                    inputText.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(
                    output, true, StandardCharsets.UTF_8.name()));
            input = ConsoleLineInput.open(user);
            try {
                dispatch.invoke(null, invocation, mind, input, null);
                return null;
            } catch (InvocationTargetException wrapper) {
                return wrapper.getCause();
            }
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } finally {
                System.setOut(previousOut);
                System.setIn(previousIn);
            }
        }
    }

    private static boolean hasStorage(IMind mind, String expected) throws Exception {
        for (String name : mind.getStoragesList()) {
            if (expected.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String text(ByteArrayOutputStream output) throws Exception {
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
