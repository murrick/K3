/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IMind;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end M1 qualification proving that DUMB2 participates in the existing
 * Mind/User transaction lifecycle rather than introducing another transaction
 * model.
 */
public class ContextRuntimeLifecycleTest {

    @TempDir
    Path root;

    @Test
    void rootSettlementOwnsPhysicalPublicationAndRollbackDoesNot() throws Exception {
        Path databaseDir = root.resolve("database");
        Files.createDirectories(databaseDir);

        User user = new User();
        user.setDatabaseDir(databaseDir.toString() + File.separator);

        DB db = new DB();
        db.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);

        mind = (Mind) mind.useStorage("runtime");
        user.setCurrentMind(mind);

        UUID contextId = db.getContextId();
        assertEquals(0L, db.getRevision());

        assertTrue(Boolean.TRUE.equals(mind.query("!baseline;")));
        assertEquals(1L, db.getRevision(),
                "completed U0 query is itself a root durable settlement");

        Mind committed = new Mind(mind);
        assertTrue(Boolean.TRUE.equals(committed.query("!committed;")));
        assertEquals(1L, db.getRevision(),
                "query work inside explicit U1 must remain above the durable root");
        assertTrue(mind.commit(committed));

        assertEquals(2L, db.getRevision(),
                "explicit U1 commit must publish exactly one new Context revision");
        assertTrue(Boolean.TRUE.equals(mind.query("?baseline;")));
        assertTrue(Boolean.TRUE.equals(mind.query("?committed;")));

        Mind rolledBack = new Mind(mind);
        assertTrue(Boolean.TRUE.equals(rolledBack.query("!rolled_back;")));
        assertEquals(2L, db.getRevision(),
                "query work inside rollback candidate U1 must remain transient");
        mind.release(rolledBack);

        assertEquals(2L, db.getRevision(),
                "rollback must not invent a durable Context revision");
        assertFalse(Boolean.TRUE.equals(mind.query("?rolled_back;")));

        IMind offline = mind.closeStorage();
        user.setCurrentMind(offline);
        assertTrue(db.isClosed());
        assertEquals(2L, RevisionStore.read(
                ContextStore.revisionPath(databaseDir.resolve("runtime"))));

        Mind reopened = (Mind) offline.useStorage("runtime");
        user.setCurrentMind(reopened);

        assertEquals(contextId, db.getContextId());
        assertEquals(2L, db.getRevision());
        assertTrue(Boolean.TRUE.equals(reopened.query("?baseline;")));
        assertTrue(Boolean.TRUE.equals(reopened.query("?committed;")));
        assertFalse(Boolean.TRUE.equals(reopened.query("?rolled_back;")));

        user.setCurrentMind(reopened.closeStorage());
    }
}
