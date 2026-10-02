package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

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
    void localExpansionReducesGateToSeedBeforeForeignFanOut()
            throws Exception {
        ContextFixture x = context(
                "X-expand",
                "!seed(Rick,Tom) -> gate(Tom);",
                "!gate(Tom), remote(Tom) -> target(Tom);");

        OperationSnapshot operation =
                OperationSnapshot.open(x.location);
        SnapshotMindRuntime runtime = null;
        Mind work = null;
        Mind rootMind = null;
        try {
            runtime = SnapshotMindRuntime.open(
                    operation.getSourceLocation(),
                    operation.getSourceRef(),
                    "frontier-expand-characterization");
            rootMind = runtime.getMind();
            work = Mind.ephemeralChild(rootMind);
            work.setQueryPass(QueryPass.CHECKTRUE);

            Rule query = (Rule) work.compileLine(
                    "?target(Tom);",
                    true,
                    new LinkedList<ITerm>());
            assertFalse(work.analyze(query, false));
            work.link(query, false);
            assertFalse(work.analyze(query, false));

            List<FrontierDomain> initial =
                    work.getFrontierDomains();
            assertTrue(
                    initial.stream().anyMatch(
                            d -> "gate".equals(
                                    d.getPredicateName())),
                    "initial frontier=" + predicates(initial));
            assertTrue(
                    initial.stream().anyMatch(
                            d -> "remote".equals(
                                    d.getPredicateName())),
                    "initial frontier=" + predicates(initial));

            List<FrontierDomain> expanded =
                    LocalFrontierExpansion.expand(
                            work, initial);
            assertTrue(
                    expanded.stream().anyMatch(
                            d -> "seed".equals(
                                    d.getPredicateName())),
                    "expanded frontier=" + predicates(expanded));
            assertTrue(
                    expanded.stream().anyMatch(
                            d -> "remote".equals(
                                    d.getPredicateName())),
                    "expanded frontier=" + predicates(expanded));
            assertFalse(
                    expanded.stream().anyMatch(
                            d -> "gate".equals(
                                    d.getPredicateName())),
                    "gate survived local expansion: "
                            + predicates(expanded));
        } finally {
            if (rootMind != null && work != null) {
                work.getSolutions().clear();
                work.getValues().clear();
                rootMind.release(work);
            }
            if (runtime != null) {
                runtime.close();
            }
            operation.close();
        }
    }

    @Test
    void causalFixtureIsProvableByOrdinaryLocalKanger()
            throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);

        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        "!seed(Rick,Tom) -> gate(Tom);",
                        null,
                        false)));
        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        "!gate(Tom), remote(Tom) -> target(Tom);",
                        null,
                        false)));
        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        "!seed(Rick,Tom);",
                        null,
                        false)));
        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        "!remote(Tom);",
                        null,
                        false)));
        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        "?target(Tom);",
                        null,
                        false)),
                "ordinary local KANGER did not prove the causal fixture");
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

    private static String predicates(
            List<FrontierDomain> frontier) {
        java.util.List<String> names =
                new java.util.ArrayList<String>();
        for (FrontierDomain domain : frontier) {
            names.add(domain.getPredicateName());
        }
        return names.toString();
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
