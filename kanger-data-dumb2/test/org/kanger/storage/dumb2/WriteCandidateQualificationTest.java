package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.Version;
import org.kanger.exception.StorageLifecycleException;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.8 local/pair/composition qualification before publication. */
public class WriteCandidateQualificationTest {

    @TempDir
    Path root;

    @Test
    void compatibleCandidateRefreshesCertificatesForNextSourceRevision()
            throws Exception {
        ContextFixture x =
                context("X-success", "!parent(John,Tom);");
        ContextFixture a =
                context("A-success", "!male(Tom);");

        ConnectionVector working =
                ConnectionVector.empty()
                        .with(ConnectionManager.qualifyConnect(
                                x.location, a.location));

        ContextStore owner =
                ContextStore.open(x.location);
        try {
            RevisionRef current =
                    new RevisionRef(
                            owner.getContextId(),
                            owner.getRevision());
            ConnectionVector currentVector =
                    working;

            ContextCandidate candidate =
                    ContextCandidate.of(
                            owner,
                            sealedCandidate(
                                    owner, x, currentVector),
                            x.revision + 1L,
                            currentVector);

            WriteCandidateQualification.Result result =
                    WriteCandidateQualification.qualify(
                            candidate,
                            currentVector);

            RevisionRef next =
                    new RevisionRef(
                            x.contextId,
                            x.revision + 1L);
            assertEquals(
                    next,
                    result.getCandidate());
            assertEquals(
                    1,
                    result.getConnections().size());
            ContextConnection refreshed =
                    result.getConnections()
                            .getConnections().get(0);
            assertTrue(
                    refreshed.getCertificate().matches(
                            next,
                            new RevisionRef(
                                    a.contextId,
                                    a.revision),
                            Version.CORE_VERSION_S));
        } finally {
            owner.close();
        }

        assertRevision(
                x.location, x.revision);
        assertRevision(
                a.location, a.revision);
    }

    @Test
    void pairwiseCompatibleTargetsCanFailCombinedComposition()
            throws Exception {
        ContextFixture x =
                context("X-composition", "!anchor(X);");
        ContextFixture a =
                context("A-composition", "!male(Tom);");
        ContextFixture b =
                context("B-composition", "!~male(Tom);");

        ConnectionVector working =
                ConnectionVector.empty()
                        .with(ConnectionManager.qualifyConnect(
                                x.location, a.location))
                        .with(ConnectionManager.qualifyConnect(
                                x.location, b.location));

        ContextStore owner =
                ContextStore.open(x.location);
        try {
            RevisionRef current =
                    new RevisionRef(
                            owner.getContextId(),
                            owner.getRevision());
            ConnectionVector currentVector =
                    working;
            assertEquals(2, currentVector.size());

            ContextCandidate candidate =
                    ContextCandidate.of(
                            owner,
                            sealedCandidate(
                                    owner, x, currentVector),
                            x.revision + 1L,
                            currentVector);

            assertThrows(
                    StorageLifecycleException.class,
                    () -> WriteCandidateQualification.qualify(
                            candidate,
                            currentVector));
        } finally {
            owner.close();
        }

        assertRevision(
                x.location, x.revision);
    }

    private Path sealedCandidate(
            ContextStore owner,
            ContextFixture source,
            ConnectionVector dependencies) throws Exception {
        Path sourceGeneration =
                ContextStore.generationPath(
                        source.location,
                        source.revision);
        Path candidate =
                root.resolve(
                        "candidate-"
                                + java.util.UUID.randomUUID().toString());
        Files.createDirectories(candidate);
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(
                             sourceGeneration,
                             "*.base")) {
            for (Path one : stream) {
                Files.copy(
                        one,
                        candidate.resolve(
                                one.getFileName().toString()));
            }
        }

        RevisionManifestStore.seal(
                candidate,
                owner.getContextId(),
                source.revision + 1L,
                source.revision,
                dependencies,
                "");
        return candidate;
    }

    private ContextFixture context(
            String name,
            String assertion) throws Exception {
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
        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        assertion,
                        null,
                        false)));

        long revision = data.getRevision();
        java.util.UUID contextId =
                data.getContextId();
        user.setCurrentMind(
                mind.closeStorage());
        return new ContextFixture(
                location,
                contextId,
                revision);
    }

    private void assertRevision(
            Path location,
            long expected) throws Exception {
        ContextSnapshot snapshot =
                ContextSnapshot.open(location);
        try {
            assertEquals(
                    expected,
                    snapshot.getRevision());
        } finally {
            snapshot.close();
        }
    }

    private static final class ContextFixture {
        final Path location;
        final java.util.UUID contextId;
        final long revision;

        ContextFixture(
                Path location,
                java.util.UUID contextId,
                long revision) {
            this.location = location;
            this.contextId = contextId;
            this.revision = revision;
        }
    }
}
