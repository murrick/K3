/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandFormatter;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParseException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for explicit user-owned transaction history squash. */
class TransactionSquashConvergenceTest {

    @Test
    void parserRecognizesCanonicalTransactionSquash() throws Exception {
        CommandParser parser = new CommandParser();
        CommandInvocation invocation = parser.parse("transaction squash");
        assertEquals("TX_SQUASH", invocation.getIntent().name());
        assertFalse(invocation.isCoreLanguage());
        assertEquals("transaction squash", new CommandFormatter().format(invocation));
        assertEquals("TX_START", parser.parse("transaction st").getIntent().name());
        assertEquals("TX_SQUASH", parser.parse("transaction sq").getIntent().name());

        CommandParseException ambiguous = assertThrows(
                CommandParseException.class,
                () -> parser.parse("transaction s"));
        assertEquals("AMBIGUOUS_PREFIX", ambiguous.getReason().name());
    }

    @Test
    void squashCollapsesEveryLevelAboveU0AndPreservesCurrentSemantics()
            throws Exception {
        Fixture fixture = fixture("nested-offline");
        try {
            Mind root = (Mind) fixture.user.getCurrentMind();
            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!squash_hidden;")));
            Rule hidden = findRule(u1, "!squash_hidden;");

            Mind u2 = new Mind(u1);
            hidden.setDeleted(true, u2);
            assertTrue(Boolean.TRUE.equals(u2.query("!squash_u2;")));

            Mind u3 = new Mind(u2);
            assertTrue(Boolean.TRUE.equals(u3.query("!squash_u3;")));
            fixture.user.setCurrentMind(u3);

            assertEquals(3, u3.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(u3.query("?squash_hidden;")));
            assertTrue(Boolean.TRUE.equals(u3.query("?squash_u2;")));
            assertTrue(Boolean.TRUE.equals(u3.query("?squash_u3;")));

            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            CanonicalCommandProcessor.Result squashed = processor.execute(
                    parser.parse("transaction squash"), fixture.user);

            assertTrue(squashed.isHandled());
            assertTrue(squashed.isSuccess());
            Mind compact = (Mind) squashed.getMind();
            assertSame(compact, fixture.user.getCurrentMind());
            assertEquals(1, compact.getTransactionLevel());
            assertFalse(compact.isStorageUsed());
            assertFalse(Boolean.TRUE.equals(compact.query("?squash_hidden;")));
            assertTrue(Boolean.TRUE.equals(compact.query("?squash_u2;")));
            assertTrue(Boolean.TRUE.equals(compact.query("?squash_u3;")));
            assertEquals(0, transactionCounter(compact),
                    "squashed U1 retained a hidden child reservation");
            assertEquals(1, transactionCounter(root),
                    "U0 must own exactly the single squashed U1 reservation");

            CanonicalCommandProcessor.Result rollback = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);
            assertTrue(rollback.isSuccess());
            assertSame(root, rollback.getMind(),
                    "squash replaced or modified the user-owned U0 object");
            assertEquals(0, root.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(root.query("?squash_hidden;")));
            assertFalse(Boolean.TRUE.equals(root.query("?squash_u2;")));
            assertFalse(Boolean.TRUE.equals(root.query("?squash_u3;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void squashKeepsPublishedGhostMaskAfterStorageRebase() throws Exception {
        Fixture fixture = fixture("ghost-rebase");
        try {
            IMind root = createStorage(fixture, "squash-a", "!a_anchor;");
            createStorage(fixture, "squash-b", "!ghost;");
            root = open(fixture, root, "squash-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!~ghost;")));
            Rule historical = findRule(u1, "!~ghost;");

            Mind u2 = new Mind(u1);
            historical.setDeleted(true, u2);
            fixture.user.setCurrentMind(u2);
            assertTrue(Boolean.TRUE.equals(u2.query("?")));

            Mind rebasedU2 = (Mind) u2.useStorage("squash-b");
            fixture.user.setCurrentMind(rebasedU2);
            assertEquals(2, rebasedU2.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?")));
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?ghost;")));

            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            CanonicalCommandProcessor.Result squashed = processor.execute(
                    parser.parse("transaction squash"), fixture.user);

            assertTrue(squashed.isSuccess(),
                    "squash rejected a valid published context because of ghost history");
            Mind compact = (Mind) squashed.getMind();
            assertEquals(1, compact.getTransactionLevel());
            assertEquals("squash-b", compact.getStorageName());
            assertTrue(Boolean.TRUE.equals(compact.query("?")));
            assertTrue(Boolean.TRUE.equals(compact.query("?ghost;")));
            assertEquals(0, transactionCounter(compact));
            Mind rootB = (Mind) compact.getTop();
            assertEquals(1, transactionCounter(rootB));

            CanonicalCommandProcessor.Result rollback = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);
            assertTrue(rollback.isSuccess());
            assertSame(rootB, rollback.getMind());
            assertEquals(0, rootB.getTransactionLevel());
            assertEquals("squash-b", rootB.getStorageName());
            assertTrue(Boolean.TRUE.equals(rootB.query("?ghost;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void squashIsIdempotentWhenStackIsAlreadyU0OrU1() throws Exception {
        Fixture fixture = fixture("idempotent");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            Mind root = (Mind) fixture.user.getCurrentMind();

            CanonicalCommandProcessor.Result rootResult = processor.execute(
                    parser.parse("transaction squash"), fixture.user);
            assertTrue(rootResult.isSuccess());
            assertSame(root, rootResult.getMind());
            assertEquals(0, transactionCounter(root));

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!already_compact;")));
            fixture.user.setCurrentMind(u1);
            CanonicalCommandProcessor.Result levelOneResult = processor.execute(
                    parser.parse("transaction squash"), fixture.user);

            assertTrue(levelOneResult.isSuccess());
            assertSame(u1, levelOneResult.getMind());
            assertEquals(1, u1.getTransactionLevel());
            assertEquals(1, transactionCounter(root));
            assertEquals(0, transactionCounter(u1));
            assertTrue(Boolean.TRUE.equals(u1.query("?already_compact;")));
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
        String identity = "transaction-squash-" + purpose + "-" + UUID.randomUUID();
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
