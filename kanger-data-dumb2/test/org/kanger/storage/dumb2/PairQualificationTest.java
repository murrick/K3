package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.Version;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.2 qualification for exact-revision symmetric PairQualification. */
public class PairQualificationTest {

    @TempDir
    Path root;

    @Test
    void compatiblePairQualifiesSymmetricallyWithoutMutation()
            throws Exception {
        ContextFixture left =
                context("left", "!parent(John,Tom);");
        ContextFixture right =
                context("right", "!male(Tom);");

        long leftRevision = left.revision;
        long rightRevision = right.revision;

        PairQualification.Result result =
                PairQualification.qualify(
                        left.location,
                        leftRevision,
                        right.location,
                        rightRevision);

        assertTrue(result.isCompatible());
        assertTrue(result.isLeftOverRightValid());
        assertTrue(result.isRightOverLeftValid());
        assertNotNull(result.getCertificate());
        assertTrue(result.getCertificate().matches(
                result.getLeft(),
                result.getRight(),
                Version.CORE_VERSION_S));

        assertRevision(left.location, leftRevision);
        assertRevision(right.location, rightRevision);
    }

    @Test
    void contradictoryPairIsRejectedInBothDirections()
            throws Exception {
        ContextFixture left =
                context("positive", "!male(Tom);");
        ContextFixture right =
                context("negative", "!~male(Tom);");

        PairQualification.Result result =
                PairQualification.qualify(
                        left.location,
                        left.revision,
                        right.location,
                        right.revision);

        assertFalse(result.isCompatible());
        assertFalse(result.isLeftOverRightValid());
        assertFalse(result.isRightOverLeftValid());
        assertNull(result.getCertificate());

        assertRevision(left.location, left.revision);
        assertRevision(right.location, right.revision);
    }

    private ContextFixture context(
            String name, String assertion) throws Exception {
        Path databaseDir = root.resolve(name + "-db");
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
        user.setCurrentMind(mind.closeStorage());
        return new ContextFixture(location, revision);
    }

    private void assertRevision(
            Path location, long expected) throws Exception {
        ContextSnapshot snapshot =
                ContextSnapshot.open(location);
        try {
            org.junit.jupiter.api.Assertions.assertEquals(
                    expected, snapshot.getRevision());
        } finally {
            snapshot.close();
        }
    }

    private static final class ContextFixture {
        final Path location;
        final long revision;

        ContextFixture(Path location, long revision) {
            this.location = location;
            this.revision = revision;
        }
    }
}
