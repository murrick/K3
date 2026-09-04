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
    void canonicalStatusProjectsParsedSelectorWithoutReplacingMind() throws Exception {
        Fixture fixture = fixture("status");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();

            CanonicalCommandProcessor.Result result = processor.execute(
                    parser.parse("status core objects"), fixture.user);

            assertTrue(result.isHandled());
            assertTrue(result.isSuccess());
            assertEquals("count=unavailable", result.getDescription());
            assertSame(fixture.root, result.getMind());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertEquals(0, fixture.root.getTransactionLevel());
        } finally {
            fixture.close();
        }
    }

    @Test
    void canonicalRuntimeStatusExposesCheapProcessMetrics() throws Exception {
        Fixture fixture = fixture("runtime-status");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();

            CanonicalCommandProcessor.Result result = processor.execute(
                    parser.parse("status runtime"), fixture.user);

            assertTrue(result.isHandled());
            assertTrue(result.isSuccess());
            String status = result.getDescription();
            assertTrue(status.contains("version=" + Version.PRODUCT_VERSION_S));
            assertTrue(status.contains("\nsource.branch="));
            assertTrue(status.contains("\nbuild.date="));
            assertTrue(status.contains("\njava="));
            assertTrue(status.contains("\njvm="));
            assertNonNegativeMetric(status, "uptime.ms");
            assertNonNegativeMetric(status, "heap.used.bytes");
            assertNonNegativeMetric(status, "heap.committed.bytes");
            assertTrue(status.contains("\nheap.max.bytes="));
            assertTrue(status.contains("\nos="));
            assertTrue(status.contains("\narch="));
            assertSame(fixture.root, result.getMind());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertEquals(0, fixture.root.getTransactionLevel());
        } finally {
            fixture.close();
        }
    }

    @Test
    void canonicalSessionStatusProjectsLoadedUserContextWithoutMutation() throws Exception {
        Fixture fixture = fixture("session-status");
        try {
            fixture.user.setUserDir("/status/user/");
            fixture.user.setDatabaseDir("/status/database/");
            fixture.user.setSourceDir("/status/sources/");

            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();

            CanonicalCommandProcessor.Result result = processor.execute(
                    parser.parse("status session"), fixture.user);

            assertTrue(result.isHandled());
            assertTrue(result.isSuccess());
            assertEquals(
                    "user=" + fixture.user.getId()
                            + "\nmind=" + fixture.root.getId()
                            + "\nuser.dir=/status/user/"
                            + "\ndatabase.dir=/status/database/"
                            + "\nsources.dir=/status/sources/",
                    result.getDescription());
            assertSame(fixture.root, result.getMind());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertEquals(0, fixture.root.getTransactionLevel());
        } finally {
            fixture.close();
        }
    }

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

    private void assertNonNegativeMetric(String status, String key) {
        for (String line : status.split("\\n")) {
            if (line.startsWith(key + "=")) {
                long value = Long.parseLong(line.substring(key.length() + 1));
                assertTrue(value >= 0, key + " must be non-negative");
                return;
            }
        }
        throw new AssertionError("Missing status metric " + key);
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
