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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualification for storage rebase state that is historical at the published
 * top but cannot be structurally materialized on an intermediate baseline.
 *
 * <p>A lower explicit U-level may therefore remain latent while the current
 * published level is valid on the replacement storage. The latent authorial
 * state must survive exactly and rematerialize at the same explicit boundary
 * when a compatible baseline reappears. No target-baseline artifact may leak
 * through the round trip.</p>
 */
class StorageRebaseFailureAtomicityTest {

    @Test
    void latentHistoricalCommentOverrideSurvivesIntermediateBaselineAndRematerializes()
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

            IMind onB = u2.useStorage("atomic-b");
            fixture.user.setCurrentMind(onB);

            assertEquals("atomic-b", onB.getStorageName());
            assertEquals(2, onB.getTransactionLevel(),
                    "rebase changed explicit transaction depth");
            assertTrue(Boolean.TRUE.equals(onB.query("?b_anchor;")));
            assertTrue(Boolean.TRUE.equals(onB.query("?u2_fact;")));
            assertFalse(Boolean.TRUE.equals(onB.query("?a_anchor;")),
                    "historical A-only anchor was incorrectly materialized on B");

            IMind backOnA = onB.useStorage("atomic-a");
            fixture.user.setCurrentMind(backOnA);

            assertEquals("atomic-a", backOnA.getStorageName());
            assertEquals(2, backOnA.getTransactionLevel(),
                    "round-trip rebase changed explicit transaction depth");
            assertTrue(Boolean.TRUE.equals(backOnA.query("?a_anchor;")));
            assertTrue(Boolean.TRUE.equals(backOnA.query("?u2_fact;")));
            assertFalse(Boolean.TRUE.equals(backOnA.query("?b_anchor;")),
                    "intermediate B/U0 leaked through the round trip");

            Mind restoredU2 = (Mind) backOnA;
            Mind restoredU1 = (Mind) restoredU2.getNext();
            Rule restoredAnchor = findPrimaryRule(restoredU1, "!a_anchor;");
            assertEquals("u1-anchor-comment",
                    restoredU1.getComments().get(restoredAnchor.getId()).getComment(),
                    "latent U1 comment override did not rematerialize at U1");

            restoredU1.release(restoredU2);
            fixture.user.setCurrentMind(restoredU1);
            assertEquals(1, restoredU1.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(restoredU1.query("?u2_fact;")));
            assertTrue(Boolean.TRUE.equals(restoredU1.query("?a_anchor;")));
            Rule afterRollbackAnchor = findPrimaryRule(restoredU1, "!a_anchor;");
            assertEquals("u1-anchor-comment",
                    restoredU1.getComments().get(afterRollbackAnchor.getId()).getComment(),
                    "U1 comment override changed after ordinary rollback of U2");

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
