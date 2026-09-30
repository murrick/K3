package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IBase;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2 qualification proving that a fixed DUMB2 Context snapshot can participate
 * in the existing single-Context Mind runtime without import or ID remapping.
 */
public class ContextSnapshotRuntimeTest {

    @TempDir
    Path root;

    @Test
    void pinnedSnapshotRunsQueriesWhileWriterPublishesNextRevision()
            throws Exception {
        Path databaseDir = root.resolve("database");
        Files.createDirectories(databaseDir);
        Path contextLocation = databaseDir.resolve("shared");

        User writerUser = new User();
        writerUser.setDatabaseDir(
                databaseDir.toString() + File.separator);
        DB writerData = new DB();
        writerData.init(writerUser);

        Mind writer = new Mind(writerUser);
        writerUser.setCurrentMind(writer);
        writer = (Mind) writer.useStorage("shared");
        writerUser.setCurrentMind(writer);

        assertTrue(Boolean.TRUE.equals(writer.query("!baseline;")));
        UUID contextId = writerData.getContextId();
        long revisionOne = writerData.getRevision();
        assertEquals(1L, revisionOne);

        User readerUser = new User();
        ContextSnapshotData readerData =
                new ContextSnapshotData(contextLocation, "shared");
        readerData.init(readerUser);

        Mind reader = new Mind(readerUser);
        readerUser.setCurrentMind(reader);
        reader = (Mind) reader.useStorage("shared");
        readerUser.setCurrentMind(reader);

        assertEquals(contextId, readerData.getContextId());
        assertEquals(revisionOne, readerData.getRevision());
        assertTrue(Boolean.TRUE.equals(reader.query("?baseline;")));

        /*
         * The writer remains live and publishes R2 while the second User/Mind
         * is attached to R1. No second mutable ContextStore open is involved.
         */
        assertTrue(Boolean.TRUE.equals(writer.query("!later;")));
        long revisionTwo = writerData.getRevision();
        assertEquals(revisionOne + 1L, revisionTwo);

        assertEquals(revisionOne, readerData.getRevision(),
                "attached runtime must remain pinned to the selected revision");

        /*
         * "later" does not exist in R1. Compiling this query therefore needs
         * runtime-local IDs, but must neither mutate R1 nor start observing R2.
         */
        assertFalse(Boolean.TRUE.equals(reader.query("?later;")));
        assertEquals(revisionOne, readerData.getRevision());
        assertEquals(revisionTwo, writerData.getRevision());

        /*
         * A later attachment boundary can select the new published revision.
         */
        reader = (Mind) reader.closeStorage();
        readerUser.setCurrentMind(reader);
        reader = (Mind) reader.useStorage("shared");
        readerUser.setCurrentMind(reader);

        assertEquals(contextId, readerData.getContextId());
        assertEquals(revisionTwo, readerData.getRevision());
        assertTrue(Boolean.TRUE.equals(reader.query("?baseline;")));
        assertTrue(Boolean.TRUE.equals(reader.query("?later;")));

        readerUser.setCurrentMind(reader.closeStorage());
        writerUser.setCurrentMind(writer.closeStorage());
    }

    @Test
    void runtimeIdsAreLocalAndPhysicalMutationStillRejected()
            throws Exception {
        Path databaseDir = root.resolve("allocator-database");
        Files.createDirectories(databaseDir);
        Path contextLocation = databaseDir.resolve("source");

        User writerUser = new User();
        writerUser.setDatabaseDir(
                databaseDir.toString() + File.separator);
        DB writerData = new DB();
        writerData.init(writerUser);

        Mind writer = new Mind(writerUser);
        writerUser.setCurrentMind(writer);
        writer = (Mind) writer.useStorage("source");
        writerUser.setCurrentMind(writer);
        assertTrue(Boolean.TRUE.equals(writer.query("!baseline;")));

        long publishedRevision = writerData.getRevision();

        User readerUser = new User();
        ContextSnapshotData readerData =
                new ContextSnapshotData(contextLocation, "source");
        readerData.init(readerUser);
        Mind reader = new Mind(readerUser);
        readerUser.setCurrentMind(reader);
        reader = (Mind) reader.useStorage("source");
        readerUser.setCurrentMind(reader);

        IBase dictionary = readerData.getBase("dictionary");
        long first = dictionary.nextId();
        long second = dictionary.nextId();
        assertEquals(first + 1L, second,
                "snapshot runtime must own an ephemeral allocation domain");
        assertEquals(publishedRevision, readerData.getRevision());
        assertEquals(publishedRevision, writerData.getRevision());

        // Runtime-only IDs may be discarded by Escalera rollback cleanup.
        dictionary.delete(first);

        long persistentId = dictionary.getRoot().getId();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> dictionary.delete(persistentId));
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> dictionary.clear());
        assertEquals(publishedRevision, readerData.getRevision());
        assertEquals(publishedRevision, writerData.getRevision());

        readerUser.setCurrentMind(reader.closeStorage());
        writerUser.setCurrentMind(writer.closeStorage());
    }
}
