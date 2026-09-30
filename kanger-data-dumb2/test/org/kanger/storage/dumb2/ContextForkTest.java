/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.Step;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * M2 qualification for independent Context copy/fork semantics.
 */
public class ContextForkTest {

    @TempDir
    Path root;

    @Test
    void forkUsesExactSnapshotAndStartsIndependentLineage() throws Exception {
        Path sourceLocation = root.resolve("source");
        Path targetLocation = root.resolve("fork");

        ContextStore source = ContextStore.create(sourceLocation);
        ContextSnapshot snapshot = null;
        ContextStore fork = null;
        ContextStore reopened = null;
        try {
            IBase sourceBase = source.getBase("index");
            sourceBase.add(step(0L, 11, Long.valueOf(10L), null));
            assertEquals(1L, source.flush());

            snapshot = ContextSnapshot.open(sourceLocation);
            UUID sourceContextId = snapshot.getContextId();
            assertEquals(1L, snapshot.getRevision());

            Step sourcePrevious = step(
                    0L, 11, Long.valueOf(10L), null);
            sourceBase.add(step(
                    1L, 12, Long.valueOf(20L), sourcePrevious));
            assertEquals(2L, source.flush());

            /*
             * Fork after source HEAD already moved to R2. The result must still
             * be a copy of the selected immutable A@R1 snapshot.
             */
            fork = snapshot.fork(targetLocation);
            UUID forkContextId = fork.getContextId();

            assertNotEquals(sourceContextId, forkContextId);
            assertEquals(1L, fork.getRevision());
            assertOrigin(fork.getOrigin(), sourceContextId, 1L);

            IBase forkBase = fork.getBase("index");
            assertEquals(Long.valueOf(10L), forkBase.get(0L).getData());
            assertEquals(0L, forkBase.get(0L).getId());
            assertNull(forkBase.get(1L),
                    "source R2 content must not leak into a fork of R1");

            assertEquals(1L, snapshot.getRevision());
            assertNull(snapshot.getBase("index").get(1L));

            Step forkPrevious = step(
                    0L, 11, Long.valueOf(10L), null);
            forkBase.add(step(
                    1L, 13, Long.valueOf(99L), forkPrevious));
            assertEquals(2L, fork.flush());

            assertEquals(Long.valueOf(20L),
                    sourceBase.get(1L).getData());
            assertEquals(Long.valueOf(99L),
                    forkBase.get(1L).getData());

            fork.close();
            fork = null;

            reopened = ContextStore.open(targetLocation);
            assertEquals(forkContextId, reopened.getContextId());
            assertEquals(2L, reopened.getRevision());
            assertOrigin(reopened.getOrigin(), sourceContextId, 1L);
            assertEquals(Long.valueOf(99L),
                    reopened.getBase("index").get(1L).getData());
        } finally {
            if (reopened != null) {
                reopened.close();
            }
            if (fork != null) {
                fork.close();
            }
            if (snapshot != null) {
                snapshot.close();
            }
            source.close();
        }
    }

    @Test
    void emptySnapshotForkRemainsRevisionZeroButGetsNewIdentity()
            throws Exception {
        Path sourceLocation = root.resolve("empty-source");
        Path targetLocation = root.resolve("empty-fork");

        ContextStore source = ContextStore.create(sourceLocation);
        ContextSnapshot snapshot = null;
        ContextStore fork = null;
        try {
            snapshot = ContextSnapshot.open(sourceLocation);
            fork = snapshot.fork(targetLocation);

            assertNotEquals(source.getContextId(), fork.getContextId());
            assertEquals(RevisionStore.INITIAL_REVISION,
                    fork.getRevision());
            assertOrigin(fork.getOrigin(),
                    source.getContextId(),
                    RevisionStore.INITIAL_REVISION);
            assertFalse(Files.exists(
                    ContextStore.generationPath(
                            targetLocation,
                            RevisionStore.INITIAL_REVISION)));
        } finally {
            if (fork != null) {
                fork.close();
            }
            if (snapshot != null) {
                snapshot.close();
            }
            source.close();
        }
    }

    @Test
    void forkNeverReplacesExistingTargetContext() throws Exception {
        Path sourceLocation = root.resolve("collision-source");
        Path targetLocation = root.resolve("collision-target");

        ContextStore source = ContextStore.create(sourceLocation);
        ContextSnapshot snapshot = null;
        ContextStore target = ContextStore.create(targetLocation);
        UUID targetId = target.getContextId();
        target.close();

        try {
            snapshot = ContextSnapshot.open(sourceLocation);
            ContextSnapshot selected = snapshot;
            assertThrows(FileAlreadyExistsException.class,
                    () -> selected.fork(targetLocation));

            ContextStore preserved = ContextStore.open(targetLocation);
            try {
                assertEquals(targetId, preserved.getContextId());
            } finally {
                preserved.close();
            }
        } finally {
            if (snapshot != null) {
                snapshot.close();
            }
            source.close();
        }
    }

    private static void assertOrigin(ContextManifestStore.Origin origin,
                                     UUID contextId,
                                     long revision) {
        assertEquals(contextId, origin.getContextId());
        assertEquals(revision, origin.getRevision());
    }

    private static Step step(long id,
                             int hash,
                             Object data,
                             Step next) {
        Step step = new Step();
        step.setId(id);
        step.setHash(hash);
        step.setData(data);
        step.setNext(next);
        return step;
    }
}
