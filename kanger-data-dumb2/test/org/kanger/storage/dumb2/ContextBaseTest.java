/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.factory.CommentFactory;
import org.kanger.units.Comment;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.Sapato;
import org.kanger.storage.Step;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Physical lifecycle qualification for one DUMB 2.0 schema inside a Context. */
public class ContextBaseTest {

    @TempDir
    Path root;

    @Test
    void dirtySchemaPublishesAsOneNewContextRevisionAndReopens() throws Exception {
        Path location = root.resolve("base-reopen");
        ContextStore context = ContextStore.create(location);
        IBase base = context.getBase("terms");

        base.add(step(base, 0L, 101, Long.valueOf(42L), null));
        assertEquals(0L, context.getRevision());

        assertEquals(1L, context.flush());
        assertEquals(1L, context.getRevision());
        assertTrue(Files.isDirectory(ContextStore.generationPath(location, 1L)));
        assertEquals(1L, context.flush(), "clean flush must not invent a revision");

        java.util.UUID contextId = context.getContextId();
        context.close();

        ContextStore reopened = ContextStore.open(location);
        try {
            assertEquals(contextId, reopened.getContextId());
            assertEquals(1L, reopened.getRevision());

            IBase reopenedBase = reopened.getBase("terms");
            IStep stored = reopenedBase.get(0L);
            assertNotNull(stored);
            assertEquals(42L, ((Long) stored.getData()).longValue());
            assertEquals(0L, reopenedBase.getRoot().getId());
            assertEquals(0L, reopenedBase.getTop().getId());
        } finally {
            reopened.close();
        }
    }

    @Test
    void closePublishesDirtyStateBeforeReleasingContext() throws Exception {
        Path location = root.resolve("close-publishes");
        ContextStore context = ContextStore.create(location);
        IBase base = context.getBase("rules");
        base.add(step(base, 7L, 707, Long.valueOf(99L), null));

        context.close();

        assertEquals(1L, RevisionStore.read(ContextStore.revisionPath(location)));
        ContextStore reopened = ContextStore.open(location);
        try {
            assertEquals(99L,
                    ((Long) reopened.getBase("rules").get(7L).getData()).longValue());
        } finally {
            reopened.close();
        }
    }

    @Test
    void revisionNeverAdvancesForInvalidPhysicalWorkingState() throws Exception {
        Path location = root.resolve("invalid-chain");
        ContextStore context = ContextStore.create(location);
        IBase base = context.getBase("terms");

        Step missing = volatileStep(99L, 0, Long.valueOf(2L), null);
        base.add(step(base, 0L, 1, Long.valueOf(1L), missing));

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class, context::flush);
        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
        assertEquals(0L, context.getRevision());
        assertEquals(0L, RevisionStore.read(ContextStore.revisionPath(location)));
        assertFalse(Files.exists(ContextStore.generationPath(location, 1L)));

        // Restore a valid working image so lifecycle resources can close cleanly.
        base.clear();
        context.close();
    }

    @Test
    void schemaSnapshotChecksumFailureIsDetectedDuringContextOpen() throws Exception {
        Path location = root.resolve("damaged-base");
        ContextStore context = ContextStore.create(location);
        IBase base = context.getBase("terms");
        base.add(step(base, 0L, 10, Long.valueOf(11L), null));
        context.flush();

        Path snapshot = context.schemaPath("terms");
        context.close();

        byte[] bytes = Files.readAllBytes(snapshot);
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(snapshot, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextStore.open(location));
        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void reservedCommentChainSurvivesSnapshotAndKeepsAllocatorNonNegative() throws Exception {
        Mind mind = new Mind(new User());
        Path location = root.resolve("reserved-comments");
        try (ContextStore context = ContextStore.create(location)) {
            IBase base = context.getBase(CommentFactory.SCHEMA);
            base.add(step(base, CommentFactory.HEADER_ID, 1,
                    new Comment(CommentFactory.HEADER_ID, "header", mind), null));
            assertEquals(1L, context.flush());
            assertEquals(0L, base.lastId());
            IStep header = base.get(CommentFactory.HEADER_ID);
            base.add(step(base, CommentFactory.FOOTER_ID, 2,
                    new Comment(CommentFactory.FOOTER_ID, "footer", mind), header));
            assertEquals(2L, context.flush());
            assertEquals(CommentFactory.FOOTER_ID, base.getRoot().getId());
            assertEquals(CommentFactory.HEADER_ID, base.getRoot().getNext().getId());
            assertEquals(CommentFactory.HEADER_ID, base.getTop().getId());
            assertEquals(0L, base.nextId());
            try (ContextSnapshot snapshot = ContextSnapshot.open(location, 1L)) {
                IBase pinned = snapshot.getBase(CommentFactory.SCHEMA);
                assertEquals(CommentFactory.HEADER_ID, pinned.getRoot().getId());
                assertEquals(CommentFactory.HEADER_ID, pinned.getTop().getId());
                assertEquals("header", ((Comment) pinned.getRoot().getData(mind)).getComment());
            }
        }
        try (ContextStore reopened = ContextStore.open(location)) {
            IBase base = reopened.getBase(CommentFactory.SCHEMA);
            assertEquals(CommentFactory.FOOTER_ID, base.getRoot().getId());
            assertEquals("header", ((Comment) base.getRoot().getNext().getData(mind)).getComment());
            assertEquals(0L, base.nextId());
        }
    }

    @Test
    void reservedCommentAddressesRejectOtherSchemasTypesAndDanglingLinks() throws Exception {
        Mind mind = new Mind(new User());
        try (ContextStore context = ContextStore.create(root.resolve("reserved-guards"))) {
            IBase rules = context.getBase("rules");
            assertThrows(IllegalArgumentException.class, () -> rules.add(step(rules, -2L, 1,
                    new Comment(-2L, "header", mind), null)));
            IBase comments = context.getBase(CommentFactory.SCHEMA);
            assertThrows(StorageLifecycleException.class,
                    () -> comments.add(step(comments, -2L, 1, Long.valueOf(1L), null)));
            assertThrows(IllegalArgumentException.class,
                    () -> comments.add(step(comments, -1L, 1, Long.valueOf(1L), null)));
            assertThrows(IllegalArgumentException.class,
                    () -> comments.add(step(comments, -4L, 1, Long.valueOf(1L), null)));
            comments.add(step(comments, -2L, 1, new Comment(-2L, "header", mind),
                    volatileStep(-3L, 2, new Comment(-3L, "missing", mind), null)));
            assertThrows(StorageLifecycleException.class, context::flush);
            assertEquals(0L, context.getRevision());
            comments.clear();
        }
    }

    private static Sapato step(IBase base,
                               long id,
                               int hash,
                               Object data,
                               IStep next) {
        return new Sapato(base, volatileStep(id, hash, data, next));
    }

    private static Step volatileStep(long id,
                                     int hash,
                                     Object data,
                                     IStep next) {
        Step step = new Step();
        step.setId(id);
        step.setHash(hash);
        step.setData(data);
        step.setNext(next);
        return step;
    }
}
