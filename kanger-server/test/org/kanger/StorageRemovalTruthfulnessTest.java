/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of truthful physical storage removal below command adapters. */
class StorageRemovalTruthfulnessTest {

    private static final String[] SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    @Test
    void absentGenerationIsTypedRejectionWithoutStateMutation() throws Exception {
        Fixture fixture = fixture("absent");
        try {
            String name = "missing-" + UUID.randomUUID();

            StorageLifecycleException failure = assertThrows(
                    StorageLifecycleException.class,
                    () -> fixture.root.removeStorage(name));

            assertEquals(StorageLifecycleErrorCode.STORAGE_NOT_FOUND.name(),
                    failure.getCode());
            assertNull(failure.getRequiredAction());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertFalse(fixture.root.isStorageUsed());
            assertEquals(0, fixture.root.getTransactionLevel());
        } finally {
            fixture.close();
        }
    }

    @Test
    void activeGenerationClosesBeforeTruthfulPhysicalRemoval() throws Exception {
        Fixture fixture = fixture("active");
        try {
            String name = "active-" + UUID.randomUUID();
            IMind root = fixture.root.useStorage(name);
            fixture.user.setCurrentMind(root);
            assertTrue(Boolean.TRUE.equals(root.query("!truthful_drop_active;")));
            assertTrue(generationExists(fixture.user, name));

            IMind removed = root.removeStorage(name);
            fixture.user.setCurrentMind(removed);

            assertFalse(removed.isStorageUsed());
            assertEquals("", removed.getStorageName());
            assertTrue(removed.isEmptyLevel());
            assertFalse(generationExists(fixture.user, name));
        } finally {
            fixture.close();
        }
    }

    @Test
    void partialGenerationAndWalRemnantsAreRecognizedAndRemoved() throws Exception {
        Fixture fixture = fixture("partial");
        try {
            String name = "partial-" + UUID.randomUUID();
            Path base = storageBase(fixture.user, name);
            Files.createDirectories(base.getParent());
            Path delta = Paths.get(base.toString() + ".integrity.delta");
            Path wal = Paths.get(base.toString() + ".wal.7");
            Files.write(delta, new byte[] {1, 2, 3});
            Files.write(wal, new byte[] {4, 5, 6});
            assertTrue(generationExists(fixture.user, name));

            IMind result = fixture.root.removeStorage(name);

            assertSame(fixture.root, result);
            assertFalse(generationExists(fixture.user, name));
        } finally {
            fixture.close();
        }
    }

    @Test
    void undeletableArtifactIsTypedIncompleteRemoval() throws Exception {
        Fixture fixture = fixture("incomplete");
        String name = "incomplete-" + UUID.randomUUID();
        Path base = storageBase(fixture.user, name);
        Path fakeStore = Paths.get(base.toString() + ".store");
        Path blocker = fakeStore.resolve("blocker");
        try {
            Files.createDirectories(fakeStore);
            Files.write(blocker, "keep".getBytes(StandardCharsets.UTF_8));

            StorageLifecycleException failure = assertThrows(
                    StorageLifecycleException.class,
                    () -> fixture.root.removeStorage(name));

            assertEquals(StorageLifecycleErrorCode.STORAGE_DELETE_INCOMPLETE.name(),
                    failure.getCode());
            assertEquals("VERIFY_CURRENT_STATE", failure.getRequiredAction());
            assertTrue(Files.exists(fakeStore));
            assertTrue(Files.exists(blocker));
            assertTrue(failure.getSuppressed().length > 0,
                    "Physical delete failure was not retained for diagnostics");
            assertSame(fixture.root, fixture.user.getCurrentMind());
        } finally {
            Files.deleteIfExists(blocker);
            Files.deleteIfExists(fakeStore);
            fixture.close();
        }
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "storage-removal-truth-" + purpose + "-"
                + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user, root);
    }

    private Path storageBase(IUser user, String name) {
        return Paths.get(user.getDatabaseDir()).resolve(name);
    }

    private boolean generationExists(IUser user, String name) throws Exception {
        Path base = storageBase(user, name);
        for (String suffix : SUFFIXES) {
            if (Files.exists(Paths.get(base.toString() + suffix))) {
                return true;
            }
        }
        Path directory = base.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        String prefix = base.getFileName().toString() + ".wal.";
        try (java.nio.file.DirectoryStream<Path> stream =
                     Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)
                        && entry.getFileName().toString().startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class Fixture {
        private final IUser user;
        private final Mind root;

        private Fixture(IUser user, Mind root) {
            this.user = user;
            this.root = root;
        }

        private void close() throws Exception {
            UserFactory.dropUser(user);
        }
    }
}
