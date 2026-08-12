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
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED characterization for authorial semantic deltas that must survive a
 * storage-base rebase without carrying storage-local runtime identities.
 */
class StorageRebaseSemanticDeltaTest {

    @Test
    void storageUseReplaysUdfAtSameExplicitBoundary() throws Exception {
        Fixture fixture = fixture("udf-rebase");
        try {
            IMind root = createStorage(fixture, "udf-a", "a_fact");
            createStorage(fixture, "udf-b", "b_fact");
            root = open(fixture, root, "udf-a");

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("=txfn(a){return a;};")),
                    "test fixture did not create the U1 UDF");
            Operation udf = u1.getLibrary().find("txfn(1)");
            assertNotNull(udf, "U1 UDF is absent before rebase");
            assertFalse(udf.isDeleted(u1));
            assertEquals(1, u1.getTransactionLevel());

            IMind rebased = u1.useStorage("udf-b");
            fixture.user.setCurrentMind(rebased);

            assertEquals(1, rebased.getTransactionLevel());
            Operation rebasedUdf = ((Mind) rebased).getLibrary().find("txfn(1)");
            assertNotNull(rebasedUdf, "U1 UDF was not replayed over target U0");
            assertFalse(rebasedUdf.isDeleted(rebased));

            Mind rebasedU1 = (Mind) rebased;
            Mind newRoot = (Mind) rebasedU1.getNext();
            newRoot.release(rebasedU1);
            fixture.user.setCurrentMind(newRoot);

            assertEquals(0, newRoot.getTransactionLevel());
            Operation rootUdf = newRoot.getLibrary().find("txfn(1)");
            assertTrue(rootUdf == null || rootUdf.isDeleted(newRoot),
                    "rollback did not remove the U1 UDF delta");
        } finally {
            fixture.close();
        }
    }

    @Test
    void storageUseReplaysRuleCommentOverrideAtSameExplicitBoundary() throws Exception {
        Fixture fixture = fixture("comment-rebase");
        try {
            IMind root = createStorage(fixture, "comment-a", "comment_fact");
            createStorage(fixture, "comment-b", "comment_fact");

            root = open(fixture, root, "comment-b");
            Rule bRule = findPrimaryRule((Mind) root, "!comment_fact;");
            root.getComments().add(bRule.getId(), "base-b-comment");
            fixture.user.checkpoint(root);
            root = root.closeStorage();
            fixture.user.setCurrentMind(root);

            root = open(fixture, root, "comment-a");
            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            Rule aRule = findPrimaryRule(u1, "!comment_fact;");
            u1.getComments().add(aRule.getId(), "u1-comment");
            assertEquals("u1-comment", u1.getComments().get(aRule.getId()).getComment(),
                    "test fixture did not establish the U1 comment override");
            assertEquals(1, u1.getTransactionLevel());

            IMind rebased = u1.useStorage("comment-b");
            fixture.user.setCurrentMind(rebased);

            Mind rebasedU1 = (Mind) rebased;
            assertEquals(1, rebasedU1.getTransactionLevel());
            Rule rebasedRule = findPrimaryRule(rebasedU1, "!comment_fact;");
            assertEquals("u1-comment",
                    rebasedU1.getComments().get(rebasedRule.getId()).getComment(),
                    "U1 comment override was not replayed over target U0");

            Mind newRoot = (Mind) rebasedU1.getNext();
            newRoot.release(rebasedU1);
            fixture.user.setCurrentMind(newRoot);

            Rule targetRule = findPrimaryRule(newRoot, "!comment_fact;");
            assertEquals("base-b-comment",
                    newRoot.getComments().get(targetRule.getId()).getComment(),
                    "rollback did not reveal target U0 comment");
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

    private IMind createStorage(Fixture fixture, String name, String fact) throws Exception {
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
        String identity = "storage-rebase-delta-" + purpose + "-" + UUID.randomUUID();
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
