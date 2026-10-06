package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IContextFederation;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Declarative .k dependency header qualification.
 */
public class ContextSourceMetadataIntegrationTest {

    @TempDir
    Path root;

    @Test
    void manualCurrentDependencyCanonicalizesToExactPin()
            throws Exception {
        ContextFixture a =
                context("A", "!male(Tom);");
        ContextFixture x =
                context("X", "!anchor(X);");

        Session session = open("X");
        try {
            assertTrue(session.mind.compile(
                    "//! ctx connect A\n"
                            + "!female(Jane);\n"));

            IContextFederation federation =
                    session.data;
            assertEquals(
                    1,
                    federation.federationSnapshot()
                            .getConnections().size());
            assertEquals(
                    a.revision,
                    federation.federationSnapshot()
                            .getConnections().get(0)
                            .getPinnedRevision());

            String canonical =
                    session.mind.getSourceCode();
            assertTrue(
                    canonical.startsWith(
                            "//! ctx connect A@"
                                    + a.revision),
                    canonical);
            assertFalse(
                    canonical.contains(
                            "//! ctx connect A\n"),
                    "canonical source must never depend on target CURRENT");

            /*
             * Advance A after canonicalization. Re-import into a fresh Context
             * must keep the exact old pin written by getSourceCode().
             */
            advance("A", "!female(Alice);");

            Session copy = open("COPY");
            try {
                assertTrue(
                        copy.mind.compile(canonical));
                IContextFederation.Snapshot snapshot =
                        copy.data.federationSnapshot();
                assertEquals(
                        a.revision,
                        snapshot.getConnections()
                                .get(0)
                                .getPinnedRevision());
                assertTrue(
                        snapshot.getConnections()
                                .get(0)
                                .hasNewerRevision());
            } finally {
                copy.close();
            }

            assertEquals(
                    x.revision + 1L,
                    session.data.getRevision(),
                    "Core source compile may publish B, but dependency metadata must not add another revision");
        } finally {
            session.close();
        }
    }

    @Test
    void explicitEmptySetReplacesWorkingTopology()
            throws Exception {
        context("A", "!male(Tom);");
        context("X", "!anchor(X);");

        Session session = open("X");
        try {
            session.data.connectContext("A");
            assertEquals(
                    1,
                    session.data.federationSnapshot()
                            .getConnections().size());

            assertTrue(
                    session.mind.compile(
                            "//! ctx dependencies none\n"
                                    + "!female(Jane);\n"));

            assertTrue(
                    session.data.federationSnapshot()
                            .getConnections().isEmpty());
            assertTrue(
                    session.mind.getSourceCode()
                            .startsWith(
                                    "//! ctx dependencies none"));
        } finally {
            session.close();
        }
    }

    @Test
    void failedCompileDoesNotLeakPreparedTopology()
            throws Exception {
        context("A", "!male(Tom);");
        context("X", "!anchor(X);");

        Session session = open("X");
        try {
            session.data.connectContext("A");
            assertEquals(
                    1,
                    session.data.federationSnapshot()
                            .getConnections().size());

            assertFalse(
                    session.mind.compile(
                            "//! ctx dependencies none\n"
                                    + "!probe(Tom);\n"
                                    + "!~probe(Tom);\n"));
            assertTrue(
                    session.mind.getLastCompileQualification() != null
                            && !session.mind
                                    .getLastCompileQualification()
                                    .isValid());
            assertFalse(
                    session.mind.getLastCompileQualification()
                            .getCollisions().isEmpty(),
                    "failed .k compile must retain collision witnesses before rollback");

            assertEquals(
                    1,
                    session.data.federationSnapshot()
                            .getConnections().size(),
                    "failed source compile must preserve previous working topology");
        } finally {
            session.close();
        }
    }

    @Test
    void closedVocabularyRejectsConsoleCommands()
            throws Exception {
        context("X", "!anchor(X);");

        Session session = open("X");
        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> session.mind.compile(
                            "//! ctx disconnect A\n"
                                    + "!female(Jane);\n"));
        } finally {
            session.close();
        }
    }

    private ContextFixture context(
            String name,
            String assertion) throws Exception {
        Session session = open(name);
        try {
            assertTrue(Boolean.TRUE.equals(
                    session.mind.query(
                            assertion,
                            null,
                            false)));
            return new ContextFixture(
                    session.data.getRevision());
        } finally {
            session.close();
        }
    }

    private void advance(
            String name,
            String assertion) throws Exception {
        Session session = open(name);
        try {
            assertTrue(Boolean.TRUE.equals(
                    session.mind.query(
                            assertion,
                            null,
                            false)));
        } finally {
            session.close();
        }
    }

    private Session open(String name) throws Exception {
        User user = new User();
        user.setDatabaseDir(
                root.toString()
                        + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name);
        user.setCurrentMind(mind);
        return new Session(user, data, mind);
    }

    private static final class ContextFixture {
        final long revision;

        ContextFixture(long revision) {
            this.revision = revision;
        }
    }

    private static final class Session {
        final User user;
        final DB data;
        Mind mind;

        Session(
                User user,
                DB data,
                Mind mind) {
            this.user = user;
            this.data = data;
            this.mind = mind;
        }

        void close() throws Exception {
            user.setCurrentMind(
                    mind.closeStorage());
        }
    }
}
