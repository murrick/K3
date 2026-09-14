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

/**
 * Regression for destructive Console confirmation and active-storage drop.
 *
 * <p>Dropping the active storage reuses the normal close lifecycle: explicit
 * U1..Un are rebased onto an offline U0, then the physical storage is removed.
 * Confirmation remains the only Console-specific step.</p>
 */
public final class StorageDropConfirmationOrderTest {

    @Test
    public void activeTransactionDropConfirmsThenClosesAndRemoves() throws Exception {
        Fixture fixture = fixture("confirmed");
        CommandInvocation drop = new CommandParser().parse(
                "storage drop " + fixture.storageName);
        Method dispatch = dispatchMethod();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Throwable failure = invokeDispatch(
                dispatch, drop, fixture.child, fixture.user, "y\n", output);
        if (failure != null) {
            fail("confirmed active-transaction drop unexpectedly failed", failure);
        }

        assertTrue(text(output).contains(
                        "Drop storage " + fixture.storageName + "?"),
                "destructive drop must request confirmation");

        IMind dropped = fixture.user.getCurrentMind();
        assertNotNull(dropped,
                "confirmed drop must publish the rebased Mind");
        assertEquals(1, dropped.getTransactionLevel(),
                "drop must preserve the explicit transaction level");
        assertNotNull(dropped.getNext(),
                "rebased U1 must retain an offline U0 parent");
        assertEquals(0, dropped.getNext().getTransactionLevel(),
                "rebased parent must remain U0");
        assertFalse(dropped.isStorageUsed(),
                "confirmed active-storage drop must leave the session offline");
        assertFalse(hasStorage(dropped, fixture.storageName),
                "confirmed drop must remove the physical storage");
    }

    @Test
    public void cancelledActiveTransactionDropLeavesStateUntouched() throws Exception {
        Fixture fixture = fixture("cancelled");
        CommandInvocation drop = new CommandParser().parse(
                "storage drop " + fixture.storageName);
        Method dispatch = dispatchMethod();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Throwable failure = invokeDispatch(
                dispatch, drop, fixture.child, fixture.user, "n\n", output);
        if (failure != null) {
            fail("cancelled drop unexpectedly failed", failure);
        }

        assertTrue(text(output).contains(
                        "Drop storage " + fixture.storageName + "?"),
                "destructive drop must request confirmation before mutation");
        assertSame(fixture.child, fixture.user.getCurrentMind(),
                "cancelled drop must not replace the current Mind");
        assertEquals(1, fixture.child.getTransactionLevel(),
                "cancelled drop must preserve the active transaction");
        assertSame(fixture.root, fixture.child.getNext(),
                "cancelled drop must preserve transaction ancestry");
        assertTrue(fixture.child.isStorageUsed(),
                "cancelled drop must keep the current storage open");
        assertEquals(fixture.storageName, fixture.child.getStorageName(),
                "cancelled drop must preserve storage identity");
        assertTrue(hasStorage(fixture.child, fixture.storageName),
                "cancelled drop must leave the storage intact");

        ByteArrayOutputStream cleanupOutput = new ByteArrayOutputStream();
        Throwable cleanupFailure = invokeDispatch(
                dispatch, drop, fixture.child, fixture.user, "y\n", cleanupOutput);
        if (cleanupFailure != null) {
            fail("test cleanup drop unexpectedly failed", cleanupFailure);
        }
    }

    private static Fixture fixture(String label) throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String userName = "autotest-storage-drop-" + label + "-" + suffix;
        String storageName = "drop_" + label + "_" + suffix;

        IUser user = UserFactory.createUser(userName, userName);
        new UDF().init(user);
        new DB().init(user);

        IMind root = new Mind(user);
        user.setCurrentMind(root);
        root = root.useStorage(storageName);
        user.setCurrentMind(root);

        Mind child = new Mind(root);
        user.setCurrentMind(child);
        return new Fixture(user, root, child, storageName);
    }

    private static Method dispatchMethod() throws Exception {
        Method dispatch = CanonicalConsole.class.getDeclaredMethod(
                "dispatch",
                CommandInvocation.class,
                IMind.class,
                ConsoleLineInput.class,
                ShutdownHook.class);
        dispatch.setAccessible(true);
        return dispatch;
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

    private static final class Fixture {
        private final IUser user;
        private final IMind root;
        private final Mind child;
        private final String storageName;

        private Fixture(IUser user, IMind root, Mind child, String storageName) {
            this.user = user;
            this.root = root;
            this.child = child;
            this.storageName = storageName;
        }
    }
}
