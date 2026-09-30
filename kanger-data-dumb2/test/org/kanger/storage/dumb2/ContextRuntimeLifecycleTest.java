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
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        /*
         * Inspect the runtime-produced Context below Mind/User before semantic
         * reopen. The manifest must already describe every record type, and a
         * persistent semantic node must expose its physical envelope without
         * hydrating the Rule graph.
         */
        Path runtimeLocation = databaseDir.resolve("runtime");
        ContextManifestStore.Manifest manifest = ContextManifestStore.read(
                ContextStore.contextPath(runtimeLocation));
        Set<String> typeNames = new HashSet<String>();
        for (TypeDefinition definition :
                manifest.getTypeRegistry().definitions()) {
            typeNames.add(definition.getTypeName());
        }
        assertTrue(typeNames.contains("TERM"));
        assertTrue(typeNames.contains("PREDICATE"));
        assertTrue(typeNames.contains("DOMAIN"));
        assertTrue(typeNames.contains("RULE"));

        ContextStore physical = ContextStore.open(runtimeLocation);
        try {
            IBase ruleBase = physical.getBase("rules");
            IStep rootRule = ruleBase.getRoot();
            assertTrue(rootRule instanceof ContextStep);
            ContextStep storedRule = (ContextStep) rootRule;
            assertNull(storedRule.getData(),
                    "Rule must remain unhydrated at physical reopen");
            TypeDefinition storedRuleType = physical.resolveType(
                    storedRule.getPersistentRecord().getTypeCode());
            assertEquals("RULE", storedRuleType.getTypeName());
            assertEquals("Rule-v2", storedRuleType.getDescriptor().getName());
            assertTrue(storedRule.getId() >= 0L);
            assertEquals(storedRule.getPersistentRecord().getHash(),
                    storedRule.getHash(),
                    "record envelope must expose hash without semantic hydration");
        } finally {
            physical.close();
        }

        Mind reopened = (Mind) offline.useStorage("runtime");
        user.setCurrentMind(reopened);

        assertEquals(contextId, db.getContextId());
        assertEquals(2L, db.getRevision());
        assertTrue(Boolean.TRUE.equals(reopened.query("?baseline;")));
        assertTrue(Boolean.TRUE.equals(reopened.query("?committed;")));
        assertFalse(Boolean.TRUE.equals(reopened.query("?rolled_back;")));

        user.setCurrentMind(reopened.closeStorage());
    }

    @Test
    void publicReindexKeepsCanonicalRuntimeStateAndContextIdentity()
            throws Exception {
        Path databaseDir = root.resolve("reindex-database");
        Files.createDirectories(databaseDir);

        User user = new User();
        user.setDatabaseDir(databaseDir.toString() + File.separator);

        DB db = new DB();
        db.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("runtime");
        user.setCurrentMind(mind);

        assertTrue(Boolean.TRUE.equals(mind.query("!baseline;")));
        UUID contextId = db.getContextId();
        long revision = db.getRevision();

        Mind reindexed =
                (Mind) user.reindex(null, mind, "runtime");
        user.setCurrentMind(reindexed);

        assertEquals(contextId, db.getContextId(),
                "public reindex must preserve Context identity");
        assertEquals(revision, db.getRevision(),
                "already-canonical Context must not invent a revision");
        assertTrue(Boolean.TRUE.equals(reindexed.query("?baseline;")),
                "semantic state must survive public reindex lifecycle");

        user.setCurrentMind(reindexed.closeStorage());
    }
}
