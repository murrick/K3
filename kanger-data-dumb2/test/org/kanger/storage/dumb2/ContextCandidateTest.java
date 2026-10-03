package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.storage.Step;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** M3.8 proof that staging R+1 is readable without becoming CURRENT. */
public class ContextCandidateTest {

    @TempDir
    Path root;

    @Test
    void candidateSnapshotReadsStagingWithoutPublishingRevision()
            throws Exception {
        Path location = root.resolve("candidate");
        ContextStore store = ContextStore.create(location);
        try {
            ContextBase base =
                    (ContextBase) store.getBase("index");
            base.add(step(
                    0L, 11, Long.valueOf(100L), null));
            assertEquals(1L, store.flush());

            Step previous = step(
                    0L, 11, Long.valueOf(100L), null);
            base.add(step(
                    1L, 12, Long.valueOf(200L), previous));

            Path staging = ContextStore.stateRoot(location)
                    .resolve(".candidate-test");
            Files.createDirectories(staging);
            base.writeSnapshot(staging);

            ContextCandidate candidate =
                    ContextCandidate.of(
                            store, staging, 2L);

            ContextSnapshot published =
                    ContextSnapshot.open(location);
            try {
                assertEquals(1L, published.getRevision());
                assertNull(
                        published.getBase("index").get(1L));
            } finally {
                published.close();
            }

            assertThrows(
                    org.kanger.exception.StorageLifecycleException.class,
                    () -> ContextSnapshot.open(location, 2L),
                    "candidate revision must not be openable as published");

            ContextSnapshot staged =
                    candidate.openSnapshot();
            try {
                assertEquals(2L, staged.getRevision());
                assertEquals(
                        store.getContextId(),
                        staged.getContextId());
                assertEquals(
                        Long.valueOf(200L),
                        staged.getBase("index")
                                .get(1L).getData());
            } finally {
                staged.close();
            }

            assertEquals(
                    1L,
                    RevisionStore.read(
                            ContextStore.revisionPath(location)),
                    "reading candidate must not advance CURRENT");
        } finally {
            store.close();
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
