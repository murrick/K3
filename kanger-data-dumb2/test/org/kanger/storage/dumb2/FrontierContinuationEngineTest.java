package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.6 ephemeral evidence, same-query continuation and fixed-point proof. */
public class FrontierContinuationEngineTest {

    @TempDir
    Path root;

    @Test
    void foreignEvidenceContinuesSameQueryAndDiesWithOperation()
            throws Exception {
        ContextFixture x = context(
                "X",
                "!@x source(x) -> target(x);");
        ContextFixture a = context(
                "A",
                "!source(Tom);");

        ConnectionVector working =
                ConnectionVector.empty()
                        .with(ConnectionManager.qualifyConnect(
                                x.location, a.location));

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
                        working,
                        "?target(Tom);");

        assertTrue(result.isResolved());
        assertEquals(1, result.getWaves());
        assertEquals(1, result.getEvidenceCount());
        assertTrue(result.getQueryRuleId() >= 0L);
        assertEquals("source",
                result.getFrontierTrace()
                        .get(0).get(0));

        ContextSnapshot after =
                ContextSnapshot.open(x.location);
        try {
            assertEquals(x.ref.getRevision(),
                    after.getRevision(),
                    "ephemeral continuation changed durable X revision");
        } finally {
            after.close();
        }

        SnapshotMindRuntime verify =
                SnapshotMindRuntime.open(
                        x.location,
                        x.ref,
                        "verify-ephemeral");
        Mind probe = null;
        try {
            probe = Mind.ephemeralChild(
                    verify.getMind());
            assertNull(
                    probe.query(
                            "?target(Tom);",
                            null,
                            false),
                    "foreign evidence escaped the operation-local Mind");
        } finally {
            if (probe != null) {
                probe.getSolutions().clear();
                probe.getValues().clear();
                verify.getMind().release(probe);
            }
            verify.close();
        }
    }

    @Test
    void falseGroundAggregateRemainsObservationOnly()
            throws Exception {
        ContextFixture x = context(
                "X-false",
                "!anchor(X);");
        ContextFixture a = context(
                "A-false",
                "!~source(Tom);");

        ConnectionVector working =
                ConnectionVector.empty()
                        .with(ConnectionManager.qualifyConnect(
                                x.location, a.location));

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
                        working,
                        "?source(Tom);");

        assertFalse(result.isResolved());
        assertEquals(0, result.getEvidenceCount());
        assertEquals(1, result.getObservations().size());
        assertEquals(
                FrontierAggregate.Truth.FALSE,
                result.getObservations().get(0)
                        .getAggregate().getTruth());
        assertFalse(result.hasConflict());
    }

    @Test
    void negativeFrontierUsesTrueNegativeFactAsFactualDonor()
            throws Exception {
        ContextFixture x = context(
                "X-negative-frontier",
                "!anchor(X);");
        ContextFixture a = context(
                "A-negative-frontier",
                "!~source(Tom);");

        ConnectionVector working =
                ConnectionVector.empty()
                        .with(ConnectionManager.qualifyConnect(
                                x.location, a.location));

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
                        working,
                        "?~source(Tom);");

        assertTrue(result.isResolved());
        assertEquals(1, result.getEvidenceCount());
        assertEquals(1, result.getObservations().size());
        assertEquals(
                FrontierAggregate.Truth.TRUE,
                result.getObservations().get(0)
                        .getAggregate().getTruth());
        assertFalse(result.hasConflict());
    }

    @Test
    void conflictingGroundAggregateNeverInjectsEitherSide()
            throws Exception {
        ContextFixture x = context(
                "X-conflict",
                "!anchor(X);");
        ContextFixture a = context(
                "A-conflict",
                "!source(Tom);");
        ContextFixture b = context(
                "B-conflict",
                "!~source(Tom);");

        ConnectionVector working =
                ConnectionVector.empty()
                        .with(ConnectionManager.qualifyConnect(
                                x.location, a.location))
                        .with(ConnectionManager.qualifyConnect(
                                x.location, b.location));

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
                        working,
                        "?source(Tom);");

        assertFalse(result.isResolved());
        assertEquals(0, result.getEvidenceCount());
        assertTrue(result.hasConflict());
        assertEquals(
                FrontierAggregate.Truth.CONFLICT,
                result.getObservations().get(0)
                        .getAggregate().getTruth());
        assertEquals(1,
                result.getObservations().get(0)
                        .getAggregate().getTrueSources().size());
        assertEquals(1,
                result.getObservations().get(0)
                        .getAggregate().getFalseSources().size());
    }

    @Test
    void unknownGroundAggregateRemainsObservationOnly()
            throws Exception {
        ContextFixture x = context(
                "X-unknown",
                "!anchor(X);");
        ContextFixture a = context(
                "A-unknown",
                "!other(Tom);");

        ConnectionVector working =
                ConnectionVector.empty()
                        .with(ConnectionManager.qualifyConnect(
                                x.location, a.location));

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
                        working,
                        "?source(Tom);");

        assertFalse(result.isResolved());
        assertEquals(0, result.getEvidenceCount());
        assertEquals(
                FrontierAggregate.Truth.UNKNOWN,
                result.getObservations().get(0)
                        .getAggregate().getTruth());
        assertTrue(
                result.getProvisionalHypotheses().isEmpty());
    }

    @Test
    void noNewEvidenceTerminatesAtFixedPoint()
            throws Exception {
        ContextFixture x = context(
                "X-null",
                "!@x missing(x) -> target(x);");
        ContextFixture a = context(
                "A-null",
                "!other(Tom);");

        ConnectionVector working =
                ConnectionVector.empty()
                        .with(ConnectionManager.qualifyConnect(
                                x.location, a.location));

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
                        working,
                        "?target(Tom);");

        assertFalse(result.isResolved());
        assertEquals(1, result.getWaves());
        assertEquals(0, result.getEvidenceCount());
        assertEquals("missing",
                result.getFrontierTrace()
                        .get(0).get(0));
    }

    private ContextFixture context(
            String name,
            String... statements) throws Exception {
        Path databaseDir =
                root.resolve(name + "-db");
        Files.createDirectories(databaseDir);
        Path location =
                databaseDir.resolve(name);

        User user = new User();
        user.setDatabaseDir(
                databaseDir.toString()
                        + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name);
        user.setCurrentMind(mind);

        for (String statement : statements) {
            assertTrue(Boolean.TRUE.equals(
                    mind.query(
                            statement,
                            null,
                            false)),
                    "fixture rejected: " + statement);
        }

        RevisionRef ref =
                new RevisionRef(
                        data.getContextId(),
                        data.getRevision());
        user.setCurrentMind(
                mind.closeStorage());
        return new ContextFixture(
                location, ref);
    }

    private static final class ContextFixture {
        final Path location;
        final RevisionRef ref;

        ContextFixture(
                Path location,
                RevisionRef ref) {
            this.location = location;
            this.ref = ref;
        }
    }
}
