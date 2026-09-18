/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.enums.StorageLifecycleErrorCode;
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
    void schemaSnapshotChecksumFailureIsDetectedOnAcquisition() throws Exception {
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

        ContextStore reopened = ContextStore.open(location);
        try {
            StorageLifecycleException failure = assertThrows(
                    StorageLifecycleException.class,
                    () -> reopened.getBase("terms"));
            assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                    failure.getErrorCode());
        } finally {
            reopened.close();
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
