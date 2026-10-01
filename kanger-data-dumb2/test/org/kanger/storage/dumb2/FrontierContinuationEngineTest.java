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

        ConnectionManager.connect(
                x.location, a.location);

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
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
    void noNewEvidenceTerminatesAtFixedPoint()
            throws Exception {
        ContextFixture x = context(
                "X-null",
                "!@x missing(x) -> target(x);");
        ContextFixture a = context(
                "A-null",
                "!other(Tom);");

        ConnectionManager.connect(
                x.location, a.location);

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
                        "?target(Tom);");

        assertFalse(result.isResolved());
        assertEquals(1, result.getWaves());
        assertEquals(0, result.getEvidenceCount());
        assertEquals("missing",
                result.getFrontierTrace()
                        .get(0).get(0));
    }

    @Test
    void causalFixtureReportsWhetherSecondFrontierNeedsAnotherWave()
            throws Exception {
        ContextFixture x = context(
                "X-wave",
                "!seed(Rick,Tom) -> gate(Tom);",
                "!gate(Tom), remote(Tom) -> target(Tom);");
        ContextFixture a = context(
                "A-wave",
                "!seed(Rick,Tom);");
        ContextFixture b = context(
                "B-wave",
                "!remote(Tom);");

        ConnectionManager.connect(
                x.location, a.location);
        ConnectionManager.connect(
                x.location, b.location);

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        x.location,
                        "?target(Tom);");

        assertTrue(result.isResolved(),
                "causal fixture did not reach target; trace="
                        + result.getFrontierTrace());
        assertTrue(result.getWaves() >= 1);
        assertTrue(result.getEvidenceCount() >= 2,
                "expected seed and remote evidence; trace="
                        + result.getFrontierTrace());
        assertTrue(
                result.getFrontierTrace().get(0).contains("seed"),
                "local frontier expansion did not expose seed; trace="
                        + result.getFrontierTrace());
        assertTrue(
                result.getFrontierTrace().get(0).contains("remote"),
                "remote leaf disappeared before foreign fan-out; trace="
                        + result.getFrontierTrace());
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
