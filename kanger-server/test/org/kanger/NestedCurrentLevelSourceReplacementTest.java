/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.factory.CommentFactory;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;
import org.kanger.units.Comment;
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for atomic nested Editor source replacement. */
public class NestedCurrentLevelSourceReplacementTest {

    @Test
    public void replacementRebuildsSourceDeltaAndPreservesHiddenControlDelta()
            throws Exception {
        String identity = "nested-source-replace-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            user.setCurrentMind(root);
            assertTrue(Boolean.TRUE.equals(root.query("!parent_keep;")));
            assertTrue(Boolean.TRUE.equals(root.query("!parent_delete;")));
            assertTrue(Boolean.TRUE.equals(root.query("!commented;")));
            assertTrue(Boolean.TRUE.equals(root.query("=txfn(a){return a;};")));
            Rule commented = findPrimaryRule(root, "!commented;");
            root.getComments().add(commented.getId(), "root-comment");
            root.getComments().add(CommentFactory.HEADER_ID, "root-header");

            Mind old = new Mind(root);
            assertTrue(Boolean.TRUE.equals(old.query("!old;")));
            assertTrue(Boolean.TRUE.equals(old.query("-parent_delete;")));
            Operation udf = old.getLibrary().find("txfn(1)");
            assertNotNull(udf);
            udf.setDeleted(true, old);
            old.getComments().add(commented.getId(), "u1-comment");
            old.getComments().add(CommentFactory.HEADER_ID, "");
            user.setCurrentMind(old);

            NestedCurrentLevelSourceReplacement.Outcome accepted =
                    NestedCurrentLevelSourceReplacement.replace(user, "!new;");
            assertTrue(accepted.isAccepted(), accepted.getDescription());
            Mind current = (Mind) user.getCurrentMind();
            assertNotSame(old, current,
                    "Accepted nested replacement did not publish rebuilt sibling");
            assertSame(current, accepted.getMind());
            assertEquals(1, current.getTransactionLevel());

            assertFalse(Boolean.TRUE.equals(current.query("?old;")));
            assertTrue(Boolean.TRUE.equals(current.query("?new;")));
            assertFalse(Boolean.TRUE.equals(current.query("?parent_delete;")),
                    "Inherited Rule deletion was lost during source replacement");
            assertTrue(Boolean.TRUE.equals(root.query("?parent_delete;")),
                    "Sibling replacement mutated parent semantic state");

            Operation currentUdf = current.getLibrary().find("txfn(1)");
            assertNotNull(currentUdf);
            assertTrue(currentUdf.isDeleted(current),
                    "Inherited UDF deletion was lost during source replacement");

            Rule currentCommented = findPrimaryRule(current, "!commented;");
            assertEquals("u1-comment",
                    current.getComments().get(currentCommented.getId()).getComment(),
                    "Inherited Rule comment override was lost");
            Comment header = current.getComments().get(CommentFactory.HEADER_ID);
            assertNotNull(header);
            assertEquals("", header.getComment(),
                    "Hidden header-clear override was lost");

            String projected = SourceContextMaterializer.materializeCurrentLevel(current);
            assertTrue(projected.contains("!new;"));
            assertFalse(projected.contains("!old;"));
            assertFalse(projected.contains("!parent_keep;"));
            assertFalse(projected.contains("!parent_delete;"));
            assertFalse(projected.contains("u1-comment"));

            String beforeReject = projected;
            Mind beforeRejectMind = current;
            NestedCurrentLevelSourceReplacement.Outcome rejected =
                    NestedCurrentLevelSourceReplacement.replace(user, "?new;");
            assertFalse(rejected.isAccepted(),
                    "Query-like Editor source unexpectedly replaced current U1");
            assertSame(beforeRejectMind, user.getCurrentMind(),
                    "Rejected replacement changed published current U_n identity");
            assertEquals(beforeReject,
                    SourceContextMaterializer.materializeCurrentLevel(
                            user.getCurrentMind()),
                    "Rejected replacement changed current-U_n semantic projection");

            root.release((Mind) user.getCurrentMind());
        } finally {
            UserFactory.dropUser(user);
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
        throw new AssertionError("Primary Rule not found: " + origin);
    }
}
