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

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Direct regression gates for the DUMB integrity lifecycle. */
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
        verifyIncrementalDeltaReopenAndCompaction(work.resolve("delta/db"));
        verifyCorruptedDeltaFails(work.resolve("delta-corrupt/db"));
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

    private static void verifyIncrementalDeltaReopenAndCompaction(Path prefix)
            throws Exception {
        Files.createDirectories(prefix.getParent());
        IUser user = openUser();
        Base writer = new Base(prefix.toString(), 1, new Object(), false, user);
        addLongRecord(writer, 0L, 0x4444444444444444L);
        writer.flush();

        Path delta = Paths.get(prefix.toString() + ".integrity.delta");
        if (!Files.exists(delta) || Files.size(delta) == 0L) {
            throw new AssertionError("incremental integrity delta was not published");
        }

        Base reader = new Base(prefix.toString(), 1, new Object(), false, user);
        assertLong(reader.get(0L), 0x4444444444444444L, "delta reopen");
        reader.close();
        writer.close();

        if (Files.exists(delta)) {
            throw new AssertionError("integrity delta was not compacted on close");
        }
        Base reopened = new Base(prefix.toString(), 1, new Object(), false, user);
        assertLong(reopened.get(0L), 0x4444444444444444L,
                "compacted reopen");
        reopened.close();
    }

    private static void verifyCorruptedDeltaFails(Path prefix) throws Exception {
        Files.createDirectories(prefix.getParent());
        IUser user = openUser();
        Base writer = new Base(prefix.toString(), 1, new Object(), false, user);
        addLongRecord(writer, 0L, 0x5555555555555555L);
        writer.flush();

        Path delta = Paths.get(prefix.toString() + ".integrity.delta");
        try (RandomAccessFile file = new RandomAccessFile(delta.toFile(), "rw")) {
            long position = Math.max(0L, file.length() - 1L);
            file.seek(position);
            int value = file.readUnsignedByte();
            file.seek(position);
            file.writeByte(value ^ 0x5A);
        }

        boolean rejected = false;
        try {
            new Base(prefix.toString(), 1, new Object(), false, user);
        } catch (DatabaseErrorException expected) {
            rejected = expected.toString().contains("integrity delta checksum");
        }
        if (!rejected) {
            throw new AssertionError("corrupted integrity delta was not rejected");
        }
        // Do not close writer: compaction of the deliberately damaged test
        // fixture is expected to fail and the temporary directory is disposable.
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
