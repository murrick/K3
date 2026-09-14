/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused matrix for storage switching across explicit transaction contexts.
 *
 * <p>Persistent U0 is a baseline, not a collision-free-storage contract.
 * Compatibility is qualified only after the explicit U1..Un stack has been
 * replayed over the replacement baseline. A rejected switch must restore the
 * original storage and explicit stack atomically.</p>
 */
class StorageRebaseCollisionMatrixTest {

    @Test
    void directU1ConflictRejectsAtomicallyAndSucceedsAfterConflictingLevelIsRemoved()
            throws Exception {
        Fixture fixture = fixture("direct-u1");
        try {
            IMind root = createStorage(fixture, "matrix-a", "!a_anchor;");
            createStorage(fixture, "matrix-b", "!ghost;");
            root = open(fixture, root, "matrix-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!~ghost;")));
            fixture.user.setCurrentMind(u1);

            StorageLifecycleException conflict = assertThrows(
                    StorageLifecycleException.class,
                    () -> u1.useStorage("matrix-b"));
            assertEquals(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT.name(),
                    conflict.getCode());

            IMind restored = fixture.user.getCurrentMind();
            assertEquals("matrix-a", restored.getStorageName());
            assertEquals(1, restored.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(restored.query("?a_anchor;")));
            assertTrue(Boolean.TRUE.equals(restored.query("?~ghost;")));
            assertFalse(Boolean.TRUE.equals(restored.query("?ghost;")),
                    "rejected target baseline leaked into restored context");

            Mind restoredU1 = (Mind) restored;
            Mind restoredRoot = (Mind) restoredU1.getNext();
            restoredRoot.release(restoredU1);
            fixture.user.setCurrentMind(restoredRoot);

            IMind switched = restoredRoot.useStorage("matrix-b");
            fixture.user.setCurrentMind(switched);
            assertEquals("matrix-b", switched.getStorageName());
            assertEquals(0, switched.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(switched.query("?ghost;")),
                    "target baseline did not become visible after conflicting U1 was removed");
            assertFalse(Boolean.TRUE.equals(switched.query("?~ghost;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void emptyUpperLevelsDoNotMaskLowerConflictAndAreRestoredOnRejection()
            throws Exception {
        Fixture fixture = fixture("empty-upper");
        try {
            IMind root = createStorage(fixture, "matrix-empty-a", "!a_anchor;");
            createStorage(fixture, "matrix-empty-b", "!ghost;");
            root = open(fixture, root, "matrix-empty-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!~ghost;")));
            Mind u2 = new Mind(u1);
            Mind u3 = new Mind(u2);
            fixture.user.setCurrentMind(u3);
            assertEquals(3, u3.getTransactionLevel());

            StorageLifecycleException conflict = assertThrows(
                    StorageLifecycleException.class,
                    () -> u3.useStorage("matrix-empty-b"));
            assertEquals(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT.name(),
                    conflict.getCode());

            IMind restored = fixture.user.getCurrentMind();
            assertEquals("matrix-empty-a", restored.getStorageName());
            assertEquals(3, restored.getTransactionLevel(),
                    "rejected switch changed explicit transaction depth");
            assertTrue(Boolean.TRUE.equals(restored.query("?a_anchor;")));
            assertTrue(Boolean.TRUE.equals(restored.query("?~ghost;")));
            assertFalse(Boolean.TRUE.equals(restored.query("?ghost;")),
                    "empty U2/U3 allowed target baseline to leak after rejection");
            assertEquals(2, restored.getNext().getTransactionLevel());
            assertEquals(1, restored.getNext().getNext().getTransactionLevel());
            assertEquals(0, restored.getNext().getNext().getNext().getTransactionLevel());
        } finally {
            fixture.close();
        }
    }

    @Test
    void compatibleU1U2StackRebasesWithoutChangingExplicitDepth()
            throws Exception {
        Fixture fixture = fixture("compatible");
        try {
            IMind root = createStorage(fixture, "matrix-compatible-a", "!a_anchor;");
            createStorage(fixture, "matrix-compatible-b", "!b_anchor;");
            root = open(fixture, root, "matrix-compatible-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!u1_fact;")));
            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!u2_fact;")));
            fixture.user.setCurrentMind(u2);

            IMind rebased = u2.useStorage("matrix-compatible-b");
            fixture.user.setCurrentMind(rebased);

            assertEquals("matrix-compatible-b", rebased.getStorageName());
            assertEquals(2, rebased.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(rebased.query("?b_anchor;")));
            assertTrue(Boolean.TRUE.equals(rebased.query("?u1_fact;")));
            assertTrue(Boolean.TRUE.equals(rebased.query("?u2_fact;")));
            assertFalse(Boolean.TRUE.equals(rebased.query("?a_anchor;")),
                    "source baseline leaked through compatible rebase");
        } finally {
            fixture.close();
        }
    }

    private static IMind createStorage(Fixture fixture, String name, String source)
            throws Exception {
        IMind mind = fixture.user.getCurrentMind();
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(mind.query(source)));
        fixture.user.checkpoint(mind);
        mind = mind.closeStorage();
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private static IMind open(Fixture fixture, IMind mind, String name) throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private static Fixture fixture(String purpose) throws Exception {
        String identity = "storage-rebase-matrix-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user);
    }

    private static final class Fixture {
        private final IUser user;

        private Fixture(IUser user) {
            this.user = user;
        }

        private void close() throws Exception {
            UserFactory.dropUser(user);
        }
    }
}
