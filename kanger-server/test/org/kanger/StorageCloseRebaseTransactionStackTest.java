/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes storage close as a baseline rebase to empty offline U0. */
class StorageCloseRebaseTransactionStackTest {

    @Test
    void closePreservesExplicitUStackAndPersistsOnlyDatabaseRoot() throws Exception {
        Fixture fixture = fixture();
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String storage = "close-rebase-" + UUID.randomUUID();

            CanonicalCommandProcessor.Result opened = processor.execute(
                    parser.parse("storage use " + storage), fixture.user);
            Mind root = (Mind) opened.getMind();
            assertTrue(Boolean.TRUE.equals(root.query("!persistent_base;")));

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("!offline_layer;")));
            assertEquals(1, u1.getTransactionLevel());

            CanonicalCommandProcessor.Result closed = processor.execute(
                    parser.parse("storage close"), fixture.user);

            assertTrue(closed.isHandled());
            assertTrue(closed.isSuccess());
            Mind offlineU1 = (Mind) closed.getMind();
            assertSame(offlineU1, fixture.user.getCurrentMind());
            assertFalse(offlineU1.isStorageUsed());
            assertEquals(1, offlineU1.getTransactionLevel(),
                    "storage close changed explicit U-stack depth");
            assertEquals(0, offlineU1.pendingTransactionCount(),
                    "rebased current U1 owns a hidden child reservation");
            Mind offlineRootBeforeRollback = (Mind) offlineU1.getTop();
            assertEquals(1, offlineRootBeforeRollback.pendingTransactionCount(),
                    "rebased offline U0 lost ownership of explicit U1");
            assertTrue(Boolean.TRUE.equals(offlineU1.query("?offline_layer;")),
                    "storage close lost U1 authorial state");
            assertFalse(Boolean.TRUE.equals(offlineU1.query("?persistent_base;")),
                    "detached database U0 leaked into offline U0");

            CanonicalCommandProcessor.Result rollback = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);
            assertTrue(rollback.isSuccess());
            IMind offlineRoot = rollback.getMind();
            assertEquals(0, offlineRoot.getTransactionLevel());
            assertFalse(offlineRoot.isStorageUsed());
            assertFalse(Boolean.TRUE.equals(offlineRoot.query("?offline_layer;")));

            CanonicalCommandProcessor.Result reopened = processor.execute(
                    parser.parse("storage use " + storage), fixture.user);
            assertEquals(0, reopened.getMind().getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(reopened.getMind().query("?persistent_base;")),
                    "close did not persist database U0 before detach");
            assertFalse(Boolean.TRUE.equals(reopened.getMind().query("?offline_layer;")),
                    "rolled-back U1 leaked into persistent database U0");
        } finally {
            fixture.close();
        }
    }

    private Fixture fixture() throws Exception {
        String identity = "storage-close-rebase-" + UUID.randomUUID();
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
