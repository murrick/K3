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
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for symmetric deleted/restored state merge. */
class TransactionVisibilityMergeInvariantTest {

    @Test
    void childDeleteCancelsParentRestoreOnCommit() throws Exception {
        String identity = "visibility-merge-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            user.setCurrentMind(root);
            assertTrue(Boolean.TRUE.equals(root.query("!base;")));
            Rule base = findRule(root, "!base;");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("-base;")));
            assertTrue(base.isDeleted(u1));

            Mind restore = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(restore.query("!base;")));
            assertFalse(base.isDeleted(restore));
            assertTrue(base.isRestored(restore));
            assertTrue(u1.commit(restore), "U2 restore -> U1 commit failed");
            assertFalse(base.isDeleted(u1));
            assertTrue(base.isRestored(u1),
                    "Committed restore did not remain local to U1");

            Mind deleteAgain = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(deleteAgain.query("-base;")));
            assertTrue(base.isDeleted(deleteAgain));
            assertTrue(u1.commit(deleteAgain), "U2 delete -> U1 commit failed");

            assertTrue(base.isDeleted(u1),
                    "Child delete failed to cancel the parent restore marker");
            assertFalse(base.isRestored(u1),
                    "Stale parent restore survived a committed child delete");

            root.release(u1);
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private Rule findRule(Mind mind, String origin) throws Exception {
        for (IRule candidate : mind.getRules()) {
            if (origin.equals(candidate.getOrigin())) {
                return (Rule) candidate;
            }
        }
        assertNotNull(null, "Rule not found: " + origin);
        throw new AssertionError("unreachable");
    }
}
