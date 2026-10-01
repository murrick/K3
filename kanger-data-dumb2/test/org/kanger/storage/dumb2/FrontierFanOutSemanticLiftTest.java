package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.DataType;
import org.kanger.interfaces.ITerm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.5 parallel fan-out, semantic canonicalization and provenance qualification. */
public class FrontierFanOutSemanticLiftTest {

    @TempDir
    Path root;

    @Test
    void duplicateForeignValuesLiftToOneCanonicalXTupleWithTwoSupports()
            throws Exception {
        ContextFixture x =
                context("X", "!resident(Rick,Vienna);");
        ContextFixture a =
                context("A", "!age(Tom,42);");
        ContextFixture b =
                context("B", "!age(Tom,42);");

        ConnectionManager.connect(
                x.location, a.location);
        ConnectionManager.connect(
                x.location, b.location);

        OperationSnapshot operation =
                OperationSnapshot.open(x.location);
        FrontierLiftSession lift = null;
        try {
            FrontierDomain frontier =
                    frontier("?$y age(Tom,y);");

            List<FrontierAnswer> answers =
                    FrontierFanOut.execute(
                            operation, frontier);
            assertEquals(2, answers.size());

            lift = FrontierLiftSession.open(
                    operation, answers);
            assertEquals(1,
                    lift.getVariableOrder().size());
            assertEquals("y",
                    lift.getVariableOrder().get(0));
            assertEquals(1,
                    lift.getTuples().size());

            FrontierLiftSession.LiftedTuple tuple =
                    lift.getTuples().get(0);
            assertEquals(1, tuple.getValues().size());
            assertEquals("42.0",
                    tuple.getValues().get(0).toString());
            assertEquals(2, tuple.getSupports().size());
        } finally {
            if (lift != null) {
                lift.close();
            }
            operation.close();
        }

        ContextSnapshot after =
                ContextSnapshot.open(x.location);
        try {
            assertEquals(x.ref.getRevision(),
                    after.getRevision(),
                    "semantic lift must not publish a new X revision");
        } finally {
            after.close();
        }
    }

    @Test
    void semanticProjectionPreservesAmbiguousStringType()
            throws Exception {
        ContextFixture x =
                context("X2", "!resident(Rick,Vienna);");
        ContextFixture a =
                context("A2", "!code(Tom,'42');");

        ConnectionManager.connect(
                x.location, a.location);

        OperationSnapshot operation =
                OperationSnapshot.open(x.location);
        FrontierLiftSession lift = null;
        try {
            List<FrontierAnswer> answers =
                    FrontierFanOut.execute(
                            operation,
                            frontier("?$y code(Tom,y);"));
            lift = FrontierLiftSession.open(
                    operation, answers);

            assertEquals(1, lift.getTuples().size());
            ITerm value =
                    lift.getTuples().get(0)
                            .getValues().get(0);
            assertEquals(DataType.STRING,
                    value.getType());
            assertEquals("42", value.getValue());
        } finally {
            if (lift != null) {
                lift.close();
            }
            operation.close();
        }
    }

    private FrontierDomain frontier(String query)
            throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);

        mind.query(query, null, false);
        List<FrontierDomain> frontier =
                mind.getFrontierDomains();
        assertEquals(1, frontier.size());
        return frontier.get(0);
    }

    private ContextFixture context(
            String name, String assertion) throws Exception {
        Path databaseDir =
                root.resolve(name + "-db");
        Files.createDirectories(databaseDir);
        Path location = databaseDir.resolve(name);

        User user = new User();
        user.setDatabaseDir(
                databaseDir.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name);
        user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(
                mind.query(assertion, null, false)));

        RevisionRef ref =
                new RevisionRef(
                        data.getContextId(),
                        data.getRevision());
        user.setCurrentMind(mind.closeStorage());
        return new ContextFixture(
                location, ref);
    }

    private static final class ContextFixture {
        final Path location;
        final RevisionRef ref;

        ContextFixture(Path location,
                       RevisionRef ref) {
            this.location = location;
            this.ref = ref;
        }
    }
}
