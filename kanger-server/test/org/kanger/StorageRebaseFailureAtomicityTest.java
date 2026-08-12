/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualification for compensating storage rebase.
 *
 * <p>The target generation is allowed to open successfully and replay is then
 * forced to fail on a semantic anchor that exists only in the original base.
 * The caller must observe the original generation with the complete explicit
 * U-stack reconstructed; no partially rebased target state may escape.</p>
 */
class StorageRebaseFailureAtomicityTest {

    @Test
    void replayFailureRestoresOriginalGenerationAndFullExplicitStack()
            throws Exception {
        Fixture fixture = fixture("comment-anchor");
        try {
            IMind root = createStorage(fixture, "atomic-a", "a_anchor");
            createStorage(fixture, "atomic-b", "b_anchor");
            root = open(fixture, root, "atomic-a");

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            Rule anchor = findPrimaryRule(u1, "!a_anchor;");
            u1.getComments().add(anchor.getId(), "u1-anchor-comment");

            Mind u2 = new Mind(u1);
            fixture.user.setCurrentMind(u2);
            assertTrue(Boolean.TRUE.equals(u2.query("!u2_fact;")));
            assertEquals(2, u2.getTransactionLevel());

            Exception failure = assertThrows(Exception.class,
                    () -> u2.useStorage("atomic-b"));
            assertTrue(failure.toString().contains("Cannot replay comment"),
                    "fixture did not fail during semantic replay: " + failure);

            IMind restored = fixture.user.getCurrentMind();
            assertEquals("atomic-a", restored.getStorageName(),
                    "failed rebase did not restore the original generation");
            assertEquals(2, restored.getTransactionLevel(),
                    "failed rebase did not restore both explicit boundaries");
            assertTrue(Boolean.TRUE.equals(restored.query("?a_anchor;")));
            assertTrue(Boolean.TRUE.equals(restored.query("?u2_fact;")));
            assertFalse(Boolean.TRUE.equals(restored.query("?b_anchor;")),
                    "target U0 leaked after compensating restore");

            Rule restoredAnchor = findPrimaryRule((Mind) restored, "!a_anchor;");
            assertEquals("u1-anchor-comment",
                    ((Mind) restored).getComments()
                            .get(restoredAnchor.getId()).getComment(),
                    "U1 comment delta was not restored");

            Mind restoredU2 = (Mind) restored;
            Mind restoredU1 = (Mind) restoredU2.getNext();
            restoredU1.release(restoredU2);
            fixture.user.setCurrentMind(restoredU1);
            assertEquals(1, restoredU1.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(restoredU1.query("?u2_fact;")));

            Mind restoredRoot = (Mind) restoredU1.getNext();
            restoredRoot.release(restoredU1);
            fixture.user.setCurrentMind(restoredRoot);
            assertEquals(0, restoredRoot.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(restoredRoot.query("?a_anchor;")));
        } finally {
            fixture.close();
        }
    }

    private Rule findPrimaryRule(Mind mind, String origin) throws Exception {
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (!rule.isGenerated()
                    && !rule.isDeleted(mind)
                    && origin.equals(rule.getOrigin())) {
                return rule;
            }
        }
        throw new AssertionError("Primary rule not found: " + origin);
    }

    private IMind createStorage(Fixture fixture, String name, String fact)
            throws Exception {
        IMind mind = fixture.user.getCurrentMind();
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(mind.query("!" + fact + ";")));
        fixture.user.checkpoint(mind);
        mind = mind.closeStorage();
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private IMind open(Fixture fixture, IMind mind, String name) throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "storage-rebase-atomic-" + purpose + "-"
                + UUID.randomUUID();
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
