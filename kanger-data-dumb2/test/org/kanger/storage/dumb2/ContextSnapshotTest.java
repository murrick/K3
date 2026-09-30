package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.Step;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * M2 storage-side qualification for fixed-revision Context snapshots.
 */
public class ContextSnapshotTest {

    @TempDir
    Path root;

    @Test
    void snapshotPinsRevisionWhileMutableContextPublishesNextRevision()
            throws Exception {
        Path location = root.resolve("pinned");

        ContextStore writer = ContextStore.create(location);
        ContextSnapshot first = null;
        ContextSnapshot second = null;
        try {
            IBase mutable = writer.getBase("index");
            mutable.add(step(0L, 11, Long.valueOf(10L), null));
            assertEquals(1L, writer.flush());

            /*
             * This acquisition happens while the mutable Context still owns its
             * exclusive writer lock. The snapshot must therefore be a reader of
             * immutable published state, not a second mutable open.
             */
            first = ContextSnapshot.open(location);
            assertEquals(writer.getContextId(), first.getContextId());
            assertEquals(1L, first.getRevision());

            IBase pinned = first.getBase("index");
            assertEquals(Long.valueOf(10L), pinned.get(0L).getData());
            assertNull(pinned.get(1L));

            Step previous = new Step();
            previous.setId(0L);
            previous.setHash(11);
            previous.setData(Long.valueOf(10L));

            mutable.add(step(1L, 12, Long.valueOf(20L), previous));
            assertEquals(2L, writer.flush());

            assertEquals(1L, first.getRevision(),
                    "existing snapshot must remain pinned to R1");
            assertEquals(Long.valueOf(10L), pinned.get(0L).getData());
            assertNull(pinned.get(1L),
                    "R2 record must not leak into a snapshot of R1");
            assertEquals(0L, pinned.getRoot().getId());
            assertEquals(0L, pinned.getTop().getId());

            second = ContextSnapshot.open(location);
            assertEquals(writer.getContextId(), second.getContextId());
            assertEquals(2L, second.getRevision());
            IBase latest = second.getBase("index");
            assertEquals(Long.valueOf(20L), latest.get(1L).getData());
            assertEquals(1L, latest.getRoot().getId());
            assertEquals(0L, latest.getTop().getId());
        } finally {
            if (second != null) {
                second.close();
            }
            if (first != null) {
                first.close();
            }
            writer.close();
        }
    }

    @Test
    void snapshotBaseRejectsPhysicalMutation() throws Exception {
        Path location = root.resolve("read-only");

        ContextStore writer = ContextStore.create(location);
        ContextSnapshot snapshot = null;
        try {
            writer.getBase("index").add(
                    step(0L, 21, Long.valueOf(100L), null));
            assertEquals(1L, writer.flush());

            snapshot = ContextSnapshot.open(location);
            IBase view = snapshot.getBase("index");

            assertThrows(UnsupportedOperationException.class, view::nextId);
            assertThrows(UnsupportedOperationException.class,
                    () -> view.add(
                            step(1L, 22, Long.valueOf(200L), null)));
            assertThrows(UnsupportedOperationException.class,
                    () -> view.delete(0L));
            assertThrows(UnsupportedOperationException.class, view::clear);
            assertThrows(UnsupportedOperationException.class, view::flush);

            assertEquals(Long.valueOf(100L), view.get(0L).getData());
            assertEquals(1L, snapshot.getRevision());
            assertEquals(1L, writer.getRevision(),
                    "rejected snapshot writes must not touch the mutable Context");
        } finally {
            if (snapshot != null) {
                snapshot.close();
            }
            writer.close();
        }
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
