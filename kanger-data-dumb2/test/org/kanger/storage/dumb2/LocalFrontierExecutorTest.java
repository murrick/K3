package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.User;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.4 local-only foreign frontier execution qualification. */
public class LocalFrontierExecutorTest {

    @TempDir
    Path root;

    @Test
    void groundFrontierReturnsTrueFromExactForeignContext()
            throws Exception {
        ContextFixture a =
                context("A", "!male(Tom);");
        FrontierDomain frontier =
                frontier("?male(Tom);");

        FrontierAnswer answer =
                LocalFrontierExecutor.execute(
                        a.location,
                        a.ref(),
                        frontier);

        assertEquals(FrontierAnswer.Truth.TRUE,
                answer.getTruth());
        assertTrue(answer.getVariableOrder().isEmpty());
        assertTrue(answer.getValues().isEmpty());
        assertEquals(a.ref(), answer.getSource());
    }

    @Test
    void groundFrontierReturnsFalseForExplicitNegativeEvidence()
            throws Exception {
        ContextFixture a =
                context("A", "!~male(Tom);");
        FrontierDomain frontier =
                frontier("?male(Tom);");

        FrontierAnswer answer =
                LocalFrontierExecutor.execute(
                        a.location,
                        a.ref(),
                        frontier);

        assertEquals(FrontierAnswer.Truth.FALSE,
                answer.getTruth());
    }

    @Test
    void variableFrontierReturnsOrderedDetachedTuple()
            throws Exception {
        ContextFixture a =
                context("A", "!age(Tom,42);");
        FrontierDomain frontier =
                frontier("?$y age(Tom,y);");

        assertEquals("?$y age(Tom,y);",
                frontier.getQuerySource());

        FrontierAnswer answer =
                LocalFrontierExecutor.execute(
                        a.location,
                        a.ref(),
                        frontier);

        assertEquals(FrontierAnswer.Truth.TRUE,
                answer.getTruth());
        assertEquals(1, answer.getVariableOrder().size());
        assertEquals("y", answer.getVariableOrder().get(0));
        assertFalse(answer.getValues().isEmpty());
        assertEquals(1, answer.getValues().get(0).size());
        assertEquals("42.0",
                answer.getValues().get(0).get(0).getRendered());
    }

    @Test
    void targetConnectionsAreNeverFollowedRecursively()
            throws Exception {
        ContextFixture a =
                context("A", "!resident(Alice,Vienna);");
        ContextFixture b =
                context("B", "!male(Tom);");

        ConnectionManager.connect(
                a.location, b.location);
        assertTrue(Files.exists(
                ConnectionStore.path(a.location)));

        FrontierDomain frontier =
                frontier("?male(Tom);");

        FrontierAnswer answer =
                LocalFrontierExecutor.execute(
                        a.location,
                        a.ref(),
                        frontier);

        assertEquals(FrontierAnswer.Truth.NULL,
                answer.getTruth(),
                "local-only A execution must not recurse into connected B");
    }

    private FrontierDomain frontier(String query)
            throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);

        assertEquals(null,
                mind.query(query, null, false));

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
        return new ContextFixture(location, ref);
    }

    private static final class ContextFixture {
        final Path location;
        final RevisionRef ref;

        ContextFixture(Path location, RevisionRef ref) {
            this.location = location;
            this.ref = ref;
        }

        RevisionRef ref() {
            return ref;
        }
    }
}
