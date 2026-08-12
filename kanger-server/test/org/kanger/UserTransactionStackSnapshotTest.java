/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTransactionStackSnapshotTest {

    @Test
    void replayPreservesExplicitBoundariesWithoutReusingSourceIds() throws Exception {
        Fixture source = fixture("source");
        Fixture target = fixture("target");
        try {
            assertTrue(Boolean.TRUE.equals(source.root.query("!source_base;")));
            Mind u1 = new Mind(source.root);
            assertTrue(Boolean.TRUE.equals(u1.query("!u1_fact;")));
            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!u2_fact;")));

            UserTransactionStackSnapshot snapshot =
                    UserTransactionStackSnapshot.capture(u2);
            assertEquals(2, snapshot.depth());

            assertTrue(Boolean.TRUE.equals(target.root.query("!target_base;")));
            Mind replayed = snapshot.replay(target.root);

            assertEquals(2, replayed.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(replayed.query("?target_base;")));
            assertTrue(Boolean.TRUE.equals(replayed.query("?u1_fact;")));
            assertTrue(Boolean.TRUE.equals(replayed.query("?u2_fact;")));
            assertFalse(Boolean.TRUE.equals(replayed.query("?source_base;")));

            Mind replayedU1 = (Mind) replayed.getNext();
            replayedU1.release(replayed);
            assertTrue(Boolean.TRUE.equals(replayedU1.query("?u1_fact;")));
            assertFalse(Boolean.TRUE.equals(replayedU1.query("?u2_fact;")));

            target.root.release(replayedU1);
            assertFalse(Boolean.TRUE.equals(target.root.query("?u1_fact;")));
            assertTrue(Boolean.TRUE.equals(target.root.query("?target_base;")));
        } finally {
            source.close();
            target.close();
        }
    }

    @Test
    void replayPreservesDeletionUdfAndRuleCommentSemantics() throws Exception {
        Fixture source = fixture("semantic-source");
        Fixture target = fixture("semantic-target");
        try {
            assertTrue(Boolean.TRUE.equals(source.root.query("!shared_fact;")));
            Rule sourceRule = findPrimaryRule(source.root, "!shared_fact;");
            source.root.getComments().add(sourceRule.getId(), "source-base-comment");

            Mind u1 = new Mind(source.root);
            assertTrue(Boolean.TRUE.equals(u1.query("-shared_fact;")));
            assertTrue(Boolean.TRUE.equals(u1.query("!comment_fact;")));
            Rule commentRule = findPrimaryRule(u1, "!comment_fact;");
            u1.getComments().add(commentRule.getId(), "u1-comment");
            assertTrue(Boolean.TRUE.equals(u1.query("=txfn(a){return a;};")));

            UserTransactionStackSnapshot snapshot =
                    UserTransactionStackSnapshot.capture(u1);

            assertTrue(Boolean.TRUE.equals(target.root.query("!shared_fact;")));
            Mind replayed = snapshot.replay(target.root);

            assertEquals(1, replayed.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(replayed.query("?shared_fact;")));
            assertTrue(Boolean.TRUE.equals(replayed.query("?comment_fact;")));
            Rule replayedCommentRule = findPrimaryRule(replayed, "!comment_fact;");
            assertEquals("u1-comment",
                    replayed.getComments().get(replayedCommentRule.getId()).getComment());

            Operation udf = replayed.getLibrary().find("txfn(1)");
            assertNotNull(udf);
            assertFalse(udf.isDeleted(replayed));

            target.root.release(replayed);
            assertTrue(Boolean.TRUE.equals(target.root.query("?shared_fact;")));
            assertFalse(Boolean.TRUE.equals(target.root.query("?comment_fact;")));
            Operation rootUdf = target.root.getLibrary().find("txfn(1)");
            assertTrue(rootUdf == null || rootUdf.isDeleted(target.root));
        } finally {
            source.close();
            target.close();
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

    private Fixture fixture(String purpose) throws Exception {
        String identity = "tx-stack-snapshot-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user, root);
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
