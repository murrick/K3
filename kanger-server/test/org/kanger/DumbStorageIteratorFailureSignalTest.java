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
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.Base;
import org.kanger.storage.DB;
import org.kanger.storage.Data;
import org.kanger.udf.UDF;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sequential DUMB traversal must preserve the distinction between a storage
 * read failure and an absent semantic record.
 */
class DumbStorageIteratorFailureSignalTest {

    @Test
    void iteratorReadFailureMustNotBecomeNullStep() throws Exception {
        Path root = Files.createTempDirectory("kanger-dumb-iterator-failure-");
        Path databaseDir = root.resolve("database");
        Files.createDirectories(databaseDir);
        String storageName = "iterator-read-failure";

        createRuleGeneration(root, databaseDir, storageName);

        User readerUser = new User();
        readerUser.setDatabaseDir(withSeparator(databaseDir));
        DB reader = new DB();
        reader.init(readerUser);
        try {
            Base rules = acquireCanonicalBases(reader, storageName);
            assertNotNull(rules.getRoot(), "fixture Rule schema has no persisted record");

            Iterator<IStep> iterator = rules.iterator();
            assertTrue(iterator.hasNext(), "fixture Rule iterator is unexpectedly empty");

            Data physicalStore = physicalStore(rules);
            physicalStore.close();

            assertThrows(IllegalStateException.class,
                    iterator::next,
                    "iterator storage read failure was silently converted to null");
        } finally {
            reader.close();
        }
    }

    private void createRuleGeneration(Path root,
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
            assertTrue(Boolean.TRUE.equals(mind.query("!iterator_read_failure;")));
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

    private Base acquireCanonicalBases(DB db, String storageName)
            throws Exception {
        db.use(storageName);
        db.getBase(DictionaryFactory.SCHEMA);
        db.getBase(DomainFactory.SCHEMA);
        db.getBase(FunctionFactory.SCHEMA);
        db.getBase(PredicateFactory.SCHEMA);
        Base rules = (Base) db.getBase(RuleFactory.SCHEMA);
        db.getBase(TVariableFactory.SCHEMA);
        db.getBase(LibraryFactory.SCHEMA);
        db.getBase(TValueFactory.SCHEMA);
        db.getBase(FValueFactory.SCHEMA);
        db.getBase(CommentFactory.SCHEMA);
        return rules;
    }

    private Data physicalStore(Base base) throws Exception {
        Field data = Base.class.getDeclaredField("data");
        data.setAccessible(true);
        return (Data) data.get(base);
    }

    private static String withSeparator(Path path) throws Exception {
        Files.createDirectories(path);
        return path.toAbsolutePath().toString() + File.separator;
    }
}
