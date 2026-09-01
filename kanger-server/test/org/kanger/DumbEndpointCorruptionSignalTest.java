/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.factory.CommentFactory;
import org.kanger.factory.DictionaryFactory;
import org.kanger.factory.DomainFactory;
import org.kanger.factory.FValueFactory;
import org.kanger.factory.FunctionFactory;
import org.kanger.factory.LibraryFactory;
import org.kanger.factory.PredicateFactory;
import org.kanger.factory.RuleFactory;
import org.kanger.factory.TValueFactory;
import org.kanger.factory.TVariableFactory;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DUMB chain corruption is a storage failure, not an empty semantic schema.
 */
class DumbEndpointCorruptionSignalTest {

    @Test
    void liveDanglingChainMustNotBecomeNullEndpoints() throws Exception {
        Path root = Files.createTempDirectory("kanger-dumb-endpoint-corrupt-");
        Path databaseDir = root.resolve("database");
        Files.createDirectories(databaseDir);
        String storageName = "dangling-endpoint";

        createTwoRuleGeneration(root, databaseDir, storageName);

        User readerUser = new User();
        readerUser.setDatabaseDir(withSeparator(databaseDir));
        DB reader = new DB();
        reader.init(readerUser);
        try {
            IBase rules = acquireCanonicalBases(reader, storageName);
            IStep first = rules.getRoot();
            assertNotNull(first, "fixture rule schema has no root");
            IStep second = first.getNext();
            assertNotNull(second,
                    "fixture did not create a two-node persistent Rule chain");
            long victimId = second.getId();
            assertTrue(rules.containsKey(victimId),
                    "fixture successor is not physically present");

            rules.delete(victimId);
            reader.flush();
            assertFalse(rules.containsKey(victimId),
                    "failed to create live dangling Rule chain fixture");

            assertAll(
                    () -> assertThrows(IllegalStateException.class,
                            rules::getRoot,
                            "root endpoint corruption was silently converted to null"),
                    () -> assertThrows(IllegalStateException.class,
                            rules::getTop,
                            "top endpoint corruption was silently converted to null"));
        } finally {
            reader.close();
        }
    }

    private void createTwoRuleGeneration(Path root,
                                         Path databaseDir,
                                         String storageName) throws Exception {
        User user = new User();
        long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user.setId(id == 0L ? 1L : id);
        user.setUserDir(withSeparator(root.resolve("creator-home")));
        user.setDatabaseDir(withSeparator(databaseDir));
        new UDF().init(user);
        DB db = new DB();
        db.init(user);
        user.setCurrentMind(new Mind(user));

        try {
            Mind mind = (Mind) user.getCurrentMind();
            user.setCurrentMind(mind.useStorage(storageName));
            mind = (Mind) user.getCurrentMind();
            assertTrue(Boolean.TRUE.equals(mind.query("!endpoint_chain_a;")));
            assertTrue(Boolean.TRUE.equals(mind.query("!endpoint_chain_b;")));
            user.setCurrentMind(user.checkpoint(mind));
            user.setCurrentMind(user.getCurrentMind().closeStorage());
            assertTrue(db.isClosed(),
                    "fixture creator failed to close clean generation");
        } finally {
            if (!db.isClosed()) {
                db.close();
            }
        }
    }

    private IBase acquireCanonicalBases(DB db, String storageName)
            throws Exception {
        db.use(storageName);
        db.getBase(DictionaryFactory.SCHEMA);
        db.getBase(DomainFactory.SCHEMA);
        db.getBase(FunctionFactory.SCHEMA);
        db.getBase(PredicateFactory.SCHEMA);
        IBase rules = db.getBase(RuleFactory.SCHEMA);
        db.getBase(TVariableFactory.SCHEMA);
        db.getBase(LibraryFactory.SCHEMA);
        db.getBase(TValueFactory.SCHEMA);
        db.getBase(FValueFactory.SCHEMA);
        db.getBase(CommentFactory.SCHEMA);
        return rules;
    }

    private static String withSeparator(Path path) throws Exception {
        Files.createDirectories(path);
        return path.toAbsolutePath().toString() + File.separator;
    }
}
