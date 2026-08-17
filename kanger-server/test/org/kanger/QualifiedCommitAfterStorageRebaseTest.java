/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes commit settlement after storage rebase and after a genuinely
 * divergent parent makes a child commit semantically rejectable.
 */
class QualifiedCommitAfterStorageRebaseTest {

    @Test
    void commitMergesMaskingChildIntoLatentConflictedParentAfterRebase()
            throws Exception {
        Fixture fixture = fixture("rebase-mask");
        try {
            IMind root = createStorage(fixture, "qualified-commit-a", "!a_anchor;");
            createStorage(fixture, "qualified-commit-b", "!ghost;");
            root = open(fixture, root, "qualified-commit-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!~ghost;")));
            Rule historical = findRule(u1, "!~ghost;");

            Mind u2 = new Mind(u1);
            historical.setDeleted(true, u2);
            fixture.user.setCurrentMind(u2);
            assertTrue(Boolean.TRUE.equals(u2.query("?")));

            Mind rebasedU2 = (Mind) u2.useStorage("qualified-commit-b");
            fixture.user.setCurrentMind(rebasedU2);
            assertEquals(2, rebasedU2.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?")));
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?ghost;")));

            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            assertEquals(1, transactionCounter(rebasedU1));

            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            CanonicalCommandProcessor.Result commit = processor.execute(
                    parser.parse("transaction commit"), fixture.user);

            assertTrue(commit.isHandled());
            assertTrue(commit.isSuccess(),
                    "masking U2 should make the merged U1 valid on replacement baseline");
            assertSame(rebasedU1, commit.getMind());
            assertSame(rebasedU1, fixture.user.getCurrentMind());
            assertEquals(1, rebasedU1.getTransactionLevel());
            assertEquals(0, transactionCounter(rebasedU1),
                    "successful commit did not settle exactly one child reservation");
            assertTrue(Boolean.TRUE.equals(rebasedU1.query("?")),
                    "merged U1 is not semantically valid after commit");
            assertTrue(Boolean.TRUE.equals(rebasedU1.query("?ghost;")),
                    "mask merged from U2 did not preserve replacement U0 visibility");
        } finally {
            fixture.close();
        }
    }

    @Test
    void rejectedDivergentCommitKeepsChildReservationOpenForRecovery()
            throws Exception {
        Fixture fixture = fixture("divergent-reject");
        try {
            Mind root = (Mind) fixture.user.getCurrentMind();

            Mind child = new Mind(root);
            assertTrue(Boolean.TRUE.equals(child.query("!~ghost;")));
            fixture.user.setCurrentMind(child);

            Mind sibling = new Mind(root);
            assertTrue(Boolean.TRUE.equals(sibling.query("!ghost;")));
            assertEquals(2, transactionCounter(root));
            assertTrue(root.commit(sibling),
                    "fixture sibling commit unexpectedly failed");
            assertEquals(1, transactionCounter(root),
                    "sibling settlement consumed the original child reservation");

            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            CanonicalCommandProcessor.Result commit = processor.execute(
                    parser.parse("transaction commit"), fixture.user);

            assertTrue(commit.isHandled());
            assertFalse(commit.isSuccess(),
                    "divergent child unexpectedly committed across opposite-polarity parent Rule");
            assertSame(child, commit.getMind());
            assertSame(child, fixture.user.getCurrentMind());
            assertEquals(1, child.getTransactionLevel());
            assertEquals(1, transactionCounter(root),
                    "rejected commit consumed the still-published child reservation");

            CanonicalCommandProcessor.Result rollback = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);
            assertTrue(rollback.isSuccess(),
                    "child must remain a live transaction that can be rolled back after rejection");
            assertSame(root, fixture.user.getCurrentMind());
            assertEquals(0, transactionCounter(root));
            assertTrue(Boolean.TRUE.equals(root.query("?")));
            assertTrue(Boolean.TRUE.equals(root.query("?ghost;")));
        } finally {
            fixture.close();
        }
    }

    private Rule findRule(Mind mind, String origin) throws Exception {
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (origin.equals(rule.getOrigin())) {
                return rule;
            }
        }
        throw new AssertionError("Rule not found: " + origin);
    }

    private int transactionCounter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private IMind createStorage(Fixture fixture, String name, String source)
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

    private IMind open(Fixture fixture, IMind mind, String name) throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "qualified-commit-" + purpose + "-" + UUID.randomUUID();
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
