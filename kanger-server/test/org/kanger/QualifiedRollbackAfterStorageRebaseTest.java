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
 * Characterizes rollback after a storage rebase leaves a historical U-level
 * semantically incompatible with the replacement baseline while the published
 * top remains valid because a newer U-level masks the conflict.
 */
class QualifiedRollbackAfterStorageRebaseTest {

    @Test
    void rollbackRejectsAtomicallyWhenItWouldExposeConflictedHistoricalLevel()
            throws Exception {
        Fixture fixture = fixture("ghost-collision");
        try {
            IMind root = createStorage(fixture, "qualified-rb-a", "!a_anchor;");
            createStorage(fixture, "qualified-rb-b", "!ghost;");
            root = open(fixture, root, "qualified-rb-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!~ghost;")),
                    "fixture did not create the historical opposite-polarity Rule");
            Rule historical = findRule(u1, "!~ghost;");

            Mind u2 = new Mind(u1);
            historical.setDeleted(true, u2);
            fixture.user.setCurrentMind(u2);
            assertEquals(2, u2.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(u2.query("?")),
                    "masking U2 is not a valid published context before rebase");

            Mind rebasedU2 = (Mind) u2.useStorage("qualified-rb-b");
            fixture.user.setCurrentMind(rebasedU2);

            assertEquals("qualified-rb-b", rebasedU2.getStorageName());
            assertEquals(2, rebasedU2.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?")),
                    "current U2 must remain valid on replacement baseline");
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?ghost;")),
                    "U2 mask should leave the replacement U0 Rule visible");

            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            assertEquals(1, transactionCounter(rebasedU1),
                    "fixture lost the live U2 reservation before rollback attempt");

            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            CanonicalCommandProcessor.Result rollback = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);

            assertTrue(rollback.isHandled());
            assertFalse(rollback.isSuccess(),
                    "rollback published a historical U1 that conflicts with replacement U0");
            assertSame(rebasedU2, rollback.getMind(),
                    "rejected rollback did not return the still-published U2");
            assertSame(rebasedU2, fixture.user.getCurrentMind(),
                    "rejected rollback changed User.currentMind");
            assertEquals(2, fixture.user.getCurrentMind().getTransactionLevel(),
                    "rejected rollback changed explicit U-stack depth");
            assertEquals(1, transactionCounter(rebasedU1),
                    "rejected rollback consumed the child reservation");
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?")),
                    "rejected rollback damaged the current valid context");
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?ghost;")),
                    "rejected rollback damaged replacement-baseline visibility");
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
        String identity = "qualified-rollback-" + purpose + "-" + UUID.randomUUID();
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
