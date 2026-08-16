/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalCommandProcessorTest {

    @Test
    void explicitTransactionFamilyOwnsOneSharedUserStackTransition() throws Exception {
        Fixture fixture = fixture("stack");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();

            CanonicalCommandProcessor.Result status = processor.execute(
                    parser.parse("transaction"), fixture.user);
            assertTrue(status.isHandled());
            assertTrue(status.isSuccess());
            assertEquals(0, status.getMind().getTransactionLevel());

            CanonicalCommandProcessor.Result started = processor.execute(
                    parser.parse("transaction start"), fixture.user);
            assertTrue(started.isSuccess());
            assertEquals(1, started.getMind().getTransactionLevel());
            assertSame(started.getMind(), fixture.user.getCurrentMind());

            CanonicalCommandProcessor.Result rolledBack = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);
            assertTrue(rolledBack.isSuccess());
            assertEquals(0, rolledBack.getMind().getTransactionLevel());
            assertSame(fixture.root, rolledBack.getMind());
            assertSame(fixture.root, fixture.user.getCurrentMind());

            CanonicalCommandProcessor.Result rejected = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);
            assertTrue(rejected.isHandled());
            assertFalse(rejected.isSuccess());
            assertSame(fixture.root, rejected.getMind());
            assertSame(fixture.root, fixture.user.getCurrentMind());
        } finally {
            fixture.close();
        }
    }

    @Test
    void rootCommitUsesCoreCheckpointContractRatherThanConsoleNoOp() throws Exception {
        Fixture fixture = fixture("root-commit");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();

            StorageLifecycleException rejected = assertThrows(
                    StorageLifecycleException.class,
                    () -> processor.execute(parser.parse("transaction commit"), fixture.user));

            assertEquals("NO_STORAGE_OPEN", rejected.getCode());
            assertSame(fixture.root, fixture.user.getCurrentMind());
        } finally {
            fixture.close();
        }
    }

    @Test
    void nestedCommitPublishesChildAndReturnsParentAsCurrent() throws Exception {
        Fixture fixture = fixture("nested-commit");
        try {
            fixture.root = (Mind) fixture.root.useStorage("canonical-tx-shared");
            fixture.user.setCurrentMind(fixture.root);

            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            CanonicalCommandProcessor.Result started = processor.execute(
                    parser.parse("transaction start"), fixture.user);
            assertTrue(Boolean.TRUE.equals(started.getMind().query("!shared_tx_fact;")));

            CanonicalCommandProcessor.Result committed = processor.execute(
                    parser.parse("transaction commit"), fixture.user);

            assertTrue(committed.isSuccess(), committed.getDescription());
            assertEquals(0, committed.getMind().getTransactionLevel());
            assertSame(committed.getMind(), fixture.user.getCurrentMind());
            assertTrue(Boolean.TRUE.equals(committed.getMind().query("?shared_tx_fact;")));
        } finally {
            fixture.close();
        }
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "canonical-command-processor-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user, root);
    }

    private static final class Fixture {
        private final IUser user;
        private Mind root;

        private Fixture(IUser user, Mind root) {
            this.user = user;
            this.root = root;
        }

        private void close() throws Exception {
            UserFactory.dropUser(user);
        }
    }
}
