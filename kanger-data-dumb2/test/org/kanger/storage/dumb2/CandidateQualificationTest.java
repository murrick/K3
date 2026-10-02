package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.8 qualification surface for unpublished Context generations. */
public class CandidateQualificationTest {

    @TempDir
    Path root;

    @Test
    void unpublishedGenerationCanBeQualifiedAsProposedNextRevision()
            throws Exception {
        Path databaseDir = root.resolve("db");
        Files.createDirectories(databaseDir);
        Path location = databaseDir.resolve("X");

        User user = new User();
        user.setDatabaseDir(
                databaseDir.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("X");
        user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        "!resident(Rick,Vienna);",
                        null,
                        false)));
        user.setCurrentMind(
                mind.closeStorage());

        ContextSnapshot published =
                ContextSnapshot.open(location);
        ContextSnapshot candidate = null;
        try {
            assertEquals(1L,
                    published.getRevision());

            /*
             * Reuse the immutable R1 bytes as a synthetic proposed R2. The
             * point of this test is not mutation: it proves candidate
             * attachment/qualification does not consult CURRENT<=revision.
             */
            candidate =
                    ContextSnapshot.openCandidate(
                            location,
                            published.getContextId(),
                            2L,
                            published.getOrigin(),
                            published.snapshotTypeRegistry(),
                            published.getGeneration());

            assertEquals(2L,
                    candidate.getRevision());
            assertEquals(1L,
                    RevisionStore.read(
                            ContextStore.revisionPath(location)),
                    "candidate qualification must not advance CURRENT");

            CandidateQualification.Result result =
                    CandidateQualification.qualifyLocal(
                            candidate);
            candidate = null; // runtime owns/closes supplied candidate

            assertTrue(result.isValid());
            assertEquals(2L,
                    result.getCandidate().getRevision());
            assertEquals(published.getContextId(),
                    result.getCandidate().getContextId());
            assertEquals(1L,
                    RevisionStore.read(
                            ContextStore.revisionPath(location)));
        } finally {
            if (candidate != null) {
                candidate.close();
            }
            published.close();
        }
    }
}
