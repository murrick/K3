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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void compatibleConnectPublishesNewRevisionAndDisconnectPreservesHistory()
            throws Exception {
        ContextFixture x =
                context("X", "!parent(John,Tom);");
        ContextFixture a =
                context("A", "!male(Tom);");

        ContextConnection connection =
                ConnectionManager.connect(
                        x.location, a.location);

        RevisionRef connectedSource =
                new RevisionRef(
                        x.contextId,
                        x.revision + 1L);
        assertEquals(
                a.contextId,
                connection.getTarget().getContextId());
        assertTrue(
                connection.getCertificate().matches(
                        connectedSource,
                        new RevisionRef(
                                a.contextId,
                                a.revision),
                        Version.CORE_VERSION_S));

        OperationSnapshot operation =
                OperationSnapshot.open(x.location);
        try {
            assertEquals(
                    connectedSource,
                    operation.getSourceRef());
            assertEquals(
                    a.revision,
                    operation.getTarget(
                            a.contextId).getRevision());
        } finally {
            operation.close();
        }

        ConnectionManager.disconnect(
                x.location, a.contextId);

        OperationSnapshot disconnected =
                OperationSnapshot.open(x.location);
        try {
            assertEquals(
                    x.revision + 2L,
                    disconnected.getSourceRef()
                            .getRevision());
            assertTrue(
                    disconnected.getConnections()
                            .isEmpty());
        } finally {
            disconnected.close();
        }

        /*
         * KEEP_ALL: the vector bound to the previous published revision remains
         * addressable after CURRENT moves to the disconnected revision.
         */
        ConnectionVector historical =
                ConnectionStore.read(
                        x.location,
                        connectedSource);
        assertNotNull(
                historical.find(a.contextId));
        assertTrue(
                Files.exists(
                        ConnectionStore.path(
                                x.location)));
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
                        x.revision + 2L);
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
    void combinedTargetConflictBlocksTopologyPublication()
            throws Exception {
        ContextFixture x =
                context("XC", "!anchor(X);");
        ContextFixture a =
                context("AC", "!male(Tom);");
        ContextFixture b =
                context("BC", "!~male(Tom);");

        ConnectionManager.connect(
                x.location, a.location);

        assertThrows(
                StorageLifecycleException.class,
                () -> ConnectionManager.connect(
                        x.location, b.location));

        OperationSnapshot stillPublished =
                OperationSnapshot.open(x.location);
        try {
            assertEquals(
                    x.revision + 1L,
                    stillPublished.getSourceRef()
                            .getRevision(),
                    "failed topology qualification must not publish R+2");
            assertNotNull(
                    stillPublished.getConnections()
                            .find(a.contextId));
            assertNull(
                    stillPublished.getConnections()
                            .find(b.contextId));
        } finally {
            stillPublished.close();
        }
    }

    @Test
    void deliberateRevisionSwitchAffectsOnlyFutureOperations()
            throws Exception {
        ContextFixture x =
                context("XS", "!anchor(X);");
        ContextFixture a =
                context("AS", "!male(Tom);");

        ConnectionManager.connect(
                x.location, a.location);

        OperationSnapshot running =
                OperationSnapshot.open(x.location);
        try {
            assertEquals(
                    x.revision + 1L,
                    running.getSourceRef()
                            .getRevision());
            assertEquals(
                    a.revision,
                    running.getTarget(
                            a.contextId).getRevision());

            advance(
                    a,
                    "!female(Jane);");

            OperationSnapshot stillPinned =
                    OperationSnapshot.open(x.location);
            try {
                assertEquals(
                        a.revision,
                        stillPinned.getTarget(
                                a.contextId).getRevision(),
                        "target CURRENT advance must not imply FOLLOW_HEAD");
            } finally {
                stillPinned.close();
            }

            ContextConnection switched =
                    ConnectionManager.switchRevision(
                            x.location,
                            a.contextId,
                            a.revision + 1L);
            assertEquals(
                    a.revision + 1L,
                    switched.getTarget().getRevision());

            OperationSnapshot next =
                    OperationSnapshot.open(x.location);
            try {
                assertEquals(
                        a.revision + 1L,
                        next.getTarget(
                                a.contextId).getRevision());
                assertEquals(
                        x.revision + 2L,
                        next.getSourceRef().getRevision(),
                        "durable target repin must publish a new source revision");
            } finally {
                next.close();
            }

            assertEquals(
                    a.revision,
                    running.getTarget(
                            a.contextId).getRevision(),
                    "running operation must retain old target snapshot");
        } finally {
            running.close();
        }
    }

    @Test
    void incompatibleRevisionSwitchLeavesOriginalPinUntouched()
            throws Exception {
        ContextFixture x =
                context("XSI", "!male(Tom);");
        ContextFixture a =
                context("ASI", "!friend(A,B);");

        ConnectionManager.connect(
                x.location, a.location);
        advance(
                a,
                "!~male(Tom);");

        assertThrows(
                StorageLifecycleException.class,
                () -> ConnectionManager.switchRevision(
                        x.location,
                        a.contextId,
                        a.revision + 1L));

        OperationSnapshot unchanged =
                OperationSnapshot.open(x.location);
        try {
            assertEquals(
                    a.revision,
                    unchanged.getTarget(
                            a.contextId).getRevision());
        } finally {
            unchanged.close();
        }
    }

    @Test
    void revisionSwitchRejectsPairwiseCompatibleButCombinedConflict()
            throws Exception {
        ContextFixture x =
                context("XSC", "!anchor(X);");
        ContextFixture a =
                context("ASC", "!friend(A,B);");
        ContextFixture b =
                context("BSC", "!male(Tom);");

        ConnectionManager.connect(
                x.location, a.location);
        ConnectionManager.connect(
                x.location, b.location);

        advance(
                a,
                "!~male(Tom);");

        assertThrows(
                StorageLifecycleException.class,
                () -> ConnectionManager.switchRevision(
                        x.location,
                        a.contextId,
                        a.revision + 1L));

        OperationSnapshot unchanged =
                OperationSnapshot.open(x.location);
        try {
            assertEquals(
                    a.revision,
                    unchanged.getTarget(
                            a.contextId).getRevision());
            assertEquals(
                    b.revision,
                    unchanged.getTarget(
                            b.contextId).getRevision());
        } finally {
            unchanged.close();
        }
    }

    @Test
    void revisionSwitchRejectsAdditionalConflictWhenWorkingCompositionAlreadyConflicted()
            throws Exception {
        ContextFixture x =
                context("XSN", "!anchor(X);");
        ContextFixture a =
                context("ASN", "!male(Tom);");
        ContextFixture b =
                context("BSN", "!~male(Tom);");

        advance(
                b,
                "!~female(Jane);");

        ContextConnection toA =
                ConnectionManager.qualifyConnect(
                        x.location,
                        a.location);
        ContextConnection toB =
                ConnectionManager.qualifyConnect(
                        x.location,
                        b.location);
        ConnectionVector working =
                ConnectionVector.empty()
                        .with(toA)
                        .with(toB);

        advance(
                a,
                "!female(Jane);");

        assertThrows(
                StorageLifecycleException.class,
                () -> ConnectionManager.qualifySwitchRevision(
                        x.location,
                        working,
                        a.contextId,
                        a.revision + 1L));

        ContextSnapshot unchanged =
                ContextSnapshot.open(x.location);
        try {
            assertEquals(
                    x.revision,
                    unchanged.getRevision(),
                    "working topology qualification must not publish X");
        } finally {
            unchanged.close();
        }
    }

    @Test
    void revisionSwitchDoesNotFreezeOnPreExistingWorkingConflict()
            throws Exception {
        ContextFixture x =
                context("XSP", "!anchor(X);");
        ContextFixture a =
                context("ASP", "!male(Tom);");
        ContextFixture b =
                context("BSP", "!~male(Tom);");

        ContextConnection toA =
                ConnectionManager.qualifyConnect(
                        x.location,
                        a.location);
        ContextConnection toB =
                ConnectionManager.qualifyConnect(
                        x.location,
                        b.location);
        ConnectionVector working =
                ConnectionVector.empty()
                        .with(toA)
                        .with(toB);

        advance(
                a,
                "!female(Jane);");

        ContextConnection switched =
                ConnectionManager.qualifySwitchRevision(
                        x.location,
                        working,
                        a.contextId,
                        a.revision + 1L);
        assertEquals(
                a.revision + 1L,
                switched.getTarget().getRevision());

        ContextSnapshot unchanged =
                ContextSnapshot.open(x.location);
        try {
            assertEquals(
                    x.revision,
                    unchanged.getRevision(),
                    "working repin must not publish source revision");
        } finally {
            unchanged.close();
        }
    }

    private void advance(
            ContextFixture fixture,
            String assertion) throws Exception {
        User user = new User();
        user.setDatabaseDir(
                fixture.location.getParent().toString()
                        + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(
                fixture.location.getFileName().toString());
        user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        assertion,
                        null,
                        false)));
        user.setCurrentMind(
                mind.closeStorage());
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
