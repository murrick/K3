/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParser;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
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

/**
 * Regression for destructive Console confirmation ordering.
 *
 * <p>A drop that is already known to be impossible must fail its runtime
 * preflight before Console asks for destructive consent. Once the transaction
 * is settled, the same command must retain the ordinary confirmation flow.</p>
 */
public final class StorageDropConfirmationOrderTest {

    @Test
    public void activeTransactionRejectsDropBeforeConfirmation() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String userName = "autotest-storage-drop-preflight-" + suffix;
        String storageName = "drop_preflight_" + suffix;

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

        ByteArrayOutputStream rejectedOutput = new ByteArrayOutputStream();
        Throwable rejected = invokeDispatch(
                dispatch, drop, child, user, "y\n", rejectedOutput);

        assertTrue(rejected instanceof StorageLifecycleException,
                "active storage drop must fail with the existing lifecycle error");
        StorageLifecycleException lifecycle = (StorageLifecycleException) rejected;
        assertEquals(StorageLifecycleErrorCode.ACTIVE_TRANSACTION.name(),
                lifecycle.getCode());
        assertEquals("TRANSACTION_RESOLUTION_REQUIRED",
                lifecycle.getRequiredAction());
        assertFalse(text(rejectedOutput).contains(
                        "Drop storage " + storageName + "?"),
                "impossible drop must not ask for destructive confirmation");

        assertSame(child, user.getCurrentMind(),
                "rejected preflight must not replace the current Mind");
        assertEquals(1, child.getTransactionLevel(),
                "rejected preflight must preserve the active transaction");
        assertSame(root, child.getNext(),
                "rejected preflight must preserve transaction ancestry");
        assertTrue(child.isStorageUsed(),
                "rejected preflight must keep the current storage open");
        assertEquals(storageName, child.getStorageName(),
                "rejected preflight must preserve storage identity");
        assertTrue(hasStorage(child, storageName),
                "rejected preflight must leave the storage intact");

        root.release(child);
        user.setCurrentMind(root);

        ByteArrayOutputStream confirmedOutput = new ByteArrayOutputStream();
        Throwable confirmed = invokeDispatch(
                dispatch, drop, root, user, "y\n", confirmedOutput);
        if (confirmed != null) {
            fail("quiescent confirmed drop unexpectedly failed", confirmed);
        }

        String output = text(confirmedOutput);
        assertTrue(output.contains("Drop storage " + storageName + "?"),
                "eligible destructive drop must still request confirmation");

        IMind dropped = user.getCurrentMind();
        assertNotNull(dropped,
                "confirmed drop must publish the resulting Mind");
        assertEquals(0, dropped.getTransactionLevel(),
                "confirmed root drop must remain at U0");
        assertFalse(dropped.isStorageUsed(),
                "confirmed active-storage drop must leave storage closed");
        assertFalse(hasStorage(dropped, storageName),
                "confirmed drop must remove the storage");
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
