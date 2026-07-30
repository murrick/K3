/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.DatabaseErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.Base;
import org.kanger.storage.Sapato;
import org.kanger.storage.Step;

import java.nio.file.Files;
import java.nio.file.Path;

/** Direct regression gates for the 3.4.4.1 integrity lifecycle. */
public final class KangerDumbIntegrityTestRunner {

    private static final String LOGIN = "dumb-integrity-regression";
    private static final String PASSWORD = "dumb-integrity-regression";
    private static final String BOOTSTRAP_PROPERTY = "kanger.dumb.integrity.bootstrap";

    private KangerDumbIntegrityTestRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("kanger-dumb-integrity-");
        Path home = work.resolve("home");
        Files.createDirectories(home);
        System.setProperty("user.home", home.toAbsolutePath().toString());

        verifySharedPhysicalManifest(work.resolve("shared/db"));
        verifyMissingManifestFailsAndExplicitBootstrapWorks(
                work.resolve("bootstrap/db"));

        System.out.println("INTEGRITY_REGRESSION_OK work=" + work);
    }

    private static void verifySharedPhysicalManifest(Path prefix) throws Exception {
        Files.createDirectories(prefix.getParent());
        IUser user = openUser();
        Object locker = new Object();

        Base first = new Base(prefix.toString(), 1, locker, false, user);
        Base second = new Base(prefix.toString(), 2, locker, false, user);
        addLongRecord(first, 0L, 0x1111111111111111L);
        addLongRecord(second, 0L, 0x2222222222222222L);

        // Deliberately publish in the opposite order to prove merge semantics.
        second.flush();
        first.flush();
        second.close();
        first.close();

        locker = new Object();
        first = new Base(prefix.toString(), 1, locker, false, user);
        second = new Base(prefix.toString(), 2, locker, false, user);
        assertLong(first.get(0L), 0x1111111111111111L, "base 1");
        assertLong(second.get(0L), 0x2222222222222222L, "base 2");
        first.close();
        second.close();
    }

    private static void verifyMissingManifestFailsAndExplicitBootstrapWorks(
            Path prefix) throws Exception {
        Files.createDirectories(prefix.getParent());
        IUser user = openUser();
        Object locker = new Object();
        Base base = new Base(prefix.toString(), 1, locker, false, user);
        addLongRecord(base, 0L, 0x3333333333333333L);
        base.flush();
        base.close();

        Files.delete(prefix.resolveSibling(prefix.getFileName() + ".integrity"));

        boolean rejected = false;
        try {
            new Base(prefix.toString(), 1, new Object(), false, user);
        } catch (DatabaseErrorException expected) {
            rejected = expected.toString().contains(BOOTSTRAP_PROPERTY);
        }
        if (!rejected) {
            throw new AssertionError("missing manifest was not rejected explicitly");
        }

        System.setProperty(BOOTSTRAP_PROPERTY, "true");
        try {
            base = new Base(prefix.toString(), 1, new Object(), false, user);
            assertLong(base.get(0L), 0x3333333333333333L,
                    "explicit bootstrap");
            base.close();
        } finally {
            System.clearProperty(BOOTSTRAP_PROPERTY);
        }

        base = new Base(prefix.toString(), 1, new Object(), false, user);
        assertLong(base.get(0L), 0x3333333333333333L,
                "protected reopen after bootstrap");
        base.close();
    }

    private static void addLongRecord(Base base, long id, long value)
            throws Exception {
        Step step = new Step();
        step.setId(id);
        step.setHash((int) (1000L + id));
        step.setData(Long.valueOf(value));
        step.setNext(null);
        new Sapato(base, step).append();
    }

    private static void assertLong(IStep step, long expected, String label)
            throws Exception {
        if (step == null) {
            throw new AssertionError(label + " record is missing");
        }
        Object value = step.getData();
        if (!(value instanceof Long) || ((Long) value).longValue() != expected) {
            throw new AssertionError(label + " value mismatch: " + value);
        }
    }

    private static IUser openUser() throws Exception {
        try {
            return UserFactory.createUser(LOGIN, PASSWORD);
        } catch (AuthenticationErrorException exists) {
            return UserFactory.getUser(LOGIN, PASSWORD);
        }
    }
}
