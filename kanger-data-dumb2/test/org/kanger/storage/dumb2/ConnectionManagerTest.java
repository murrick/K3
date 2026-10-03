package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.Version;
import org.kanger.exception.StorageLifecycleException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3.1/M3.2 integration: only a qualified exact pair may become an active
 * directed connection.
 */
public class ConnectionManagerTest {

    @TempDir
    Path root;

    @Test
    void compatibleConnectPersistsCertificateAndDisconnectInvalidatesIt()
            throws Exception {
        ContextFixture x =
                context("X", "!parent(John,Tom);");
        ContextFixture a =
                context("A", "!male(Tom);");

        ContextConnection connection =
                ConnectionManager.connect(
                        x.location, a.location);

        assertEquals(a.contextId,
                connection.getTarget().getContextId());
        assertTrue(connection.getCertificate().matches(
                new RevisionRef(x.contextId, x.revision),
                new RevisionRef(a.contextId, a.revision),
                Version.CORE_VERSION_S));

        OperationSnapshot operation =
                OperationSnapshot.open(x.location);
        try {
            assertEquals(a.revision,
                    operation.getTarget(
                            a.contextId).getRevision());
        } finally {
            operation.close();
        }

        ConnectionManager.disconnect(
                x.location, a.contextId);
        assertFalse(Files.exists(
                ConnectionStore.path(x.location)));
    }

    @Test
    void incompatibleConnectDoesNotPublishConnection()
            throws Exception {
        ContextFixture x =
                context("X", "!male(Tom);");
        ContextFixture a =
                context("A", "!~male(Tom);");

        assertThrows(
                StorageLifecycleException.class,
                () -> ConnectionManager.connect(
                        x.location, a.location));

        assertFalse(Files.exists(
                ConnectionStore.path(x.location)));
    }

    @Test
    void sourceRevisionAdvanceRefreshesQualifiedConnection()
            throws Exception {
        ContextFixture x =
                context("X", "!parent(John,Tom);");
        ContextFixture a =
                context("A", "!male(Tom);");

        ConnectionManager.connect(
                x.location, a.location);

        User user = new User();
        user.setDatabaseDir(
                x.location.getParent().toString()
                        + File.separator);
        DB data = new DB();
        data.init(user);
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(
                x.location.getFileName().toString());
        user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(
                mind.query("!female(Jane);", null, false)));
        user.setCurrentMind(mind.closeStorage());

        RevisionRef expectedSource =
                new RevisionRef(
                        x.contextId,
                        x.revision + 1L);
        RevisionRef expectedTarget =
                new RevisionRef(
                        a.contextId,
                        a.revision);

        OperationSnapshot operation =
                OperationSnapshot.open(x.location);
        try {
            assertEquals(
                    expectedSource,
                    operation.getSourceRef());
            ContextConnection refreshed =
                    operation.getConnections()
                            .find(a.contextId);
            assertNotNull(refreshed);
            assertTrue(
                    refreshed.getCertificate().matches(
                            expectedSource,
                            expectedTarget,
                            Version.CORE_VERSION_S));
            assertEquals(
                    a.revision,
                    operation.getTarget(
                            a.contextId).getRevision());
        } finally {
            operation.close();
        }
    }

    @Test
    void combinedTargetConflictBlocksCandidatePublication()
            throws Exception {
        ContextFixture x =
                context("XC", "!anchor(X);");
        ContextFixture a =
                context("AC", "!male(Tom);");
        ContextFixture b =
                context("BC", "!~male(Tom);");

        ConnectionManager.connect(
                x.location, a.location);
        ConnectionManager.connect(
                x.location, b.location);

        User user = new User();
        user.setDatabaseDir(
                x.location.getParent().toString()
                        + File.separator);
        DB data = new DB();
        data.init(user);
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(
                x.location.getFileName().toString());
        user.setCurrentMind(mind);

        assertThrows(
                Exception.class,
                () -> mind.query(
                        "!female(Jane);",
                        null,
                        false));

        ContextSnapshot stillPublished =
                ContextSnapshot.open(x.location);
        try {
            assertEquals(
                    x.revision,
                    stillPublished.getRevision(),
                    "failed composition qualification must not publish R+1");
        } finally {
            stillPublished.close();
        }

        /*
         * Repair only operational topology, then settle the already-staged
         * local change so the test releases the mutable Context cleanly.
         */
        ConnectionManager.disconnect(
                x.location, b.contextId);
        user.setCurrentMind(
                mind.closeStorage());

        ContextSnapshot repaired =
                ContextSnapshot.open(x.location);
        try {
            assertEquals(
                    x.revision + 1L,
                    repaired.getRevision());
        } finally {
            repaired.close();
        }
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

        long revision = data.getRevision();
        java.util.UUID contextId = data.getContextId();
        assertNotNull(contextId);
        user.setCurrentMind(mind.closeStorage());
        return new ContextFixture(
                location, contextId, revision);
    }

    private static final class ContextFixture {
        final Path location;
        final java.util.UUID contextId;
        final long revision;

        ContextFixture(Path location,
                       java.util.UUID contextId,
                       long revision) {
            this.location = location;
            this.contextId = contextId;
            this.revision = revision;
        }
    }
}
