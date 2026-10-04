package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M4.2 research qualification: prove one non-trivial materialized local
 * closure can be reconstructed from the authoritative declarative source.
 *
 * <p>This is deliberately a qualification probe, not yet a storage-format
 * declaration. It establishes evidence before Revision manifest representation
 * metadata is designed.</p>
 */
public class RevisionClosureReproducibilityTest {

    @TempDir
    Path root;

    @Test
    void materializedGeneratedClosureRebuildsFromPrimarySource()
            throws Exception {
        String seed =
                "!@x @y father(x,y) -> male(x), issue(y,x);\n"
                        + "!father(John,Tom);\n";

        Path originalDir = root.resolve("original-db");
        Files.createDirectories(originalDir);
        Fixture original =
                open(originalDir, "original");
        String authoritativeSource;
        Set<String> originalGenerated;
        try {
            assertTrue(original.mind.compile(seed));

            authoritativeSource =
                    original.mind.getSourceCode();
            originalGenerated =
                    generatedSemantics(original.mind);

            assertFalse(
                    originalGenerated.isEmpty(),
                    "qualification seed did not produce materialized G");
            assertTrue(Boolean.TRUE.equals(
                    original.mind.query("?male(John);")));
            assertTrue(Boolean.TRUE.equals(
                    original.mind.query("?issue(Tom,John);")));
        } finally {
            original.close();
        }

        Path rebuiltDir = root.resolve("rebuilt-db");
        Files.createDirectories(rebuiltDir);
        Fixture rebuilt =
                open(rebuiltDir, "rebuilt");
        try {
            assertTrue(
                    rebuilt.mind.compile(
                            authoritativeSource));

            assertEquals(
                    authoritativeSource,
                    rebuilt.mind.getSourceCode(),
                    "B projection changed while rebuilding a clean Context");
            assertEquals(
                    originalGenerated,
                    generatedSemantics(rebuilt.mind),
                    "materialized G differs after rebuilding from B");
            assertTrue(Boolean.TRUE.equals(
                    rebuilt.mind.query("?male(John);")));
            assertTrue(Boolean.TRUE.equals(
                    rebuilt.mind.query("?issue(Tom,John);")));
        } finally {
            rebuilt.close();
        }

        Fixture reopened =
                open(rebuiltDir, "rebuilt");
        try {
            assertEquals(
                    authoritativeSource,
                    reopened.mind.getSourceCode(),
                    "reopen changed authoritative B projection");
            assertEquals(
                    originalGenerated,
                    generatedSemantics(reopened.mind),
                    "reopen changed reconstructed materialized G");
            assertTrue(Boolean.TRUE.equals(
                    reopened.mind.query("?male(John);")));
            assertTrue(Boolean.TRUE.equals(
                    reopened.mind.query("?issue(Tom,John);")));
        } finally {
            reopened.close();
        }
    }

    private Set<String> generatedSemantics(
            Mind mind) throws Exception {
        Set<String> result =
                new LinkedHashSet<String>();
        for (IRule rule : mind.getRules()) {
            Rule concrete = (Rule) rule;
            if (concrete.isGenerated()
                    && !concrete.isDeleted(mind)) {
                result.add(
                        concrete.toString(mind));
            }
        }
        return result;
    }

    private Fixture open(
            Path databaseDir,
            String storageName) throws Exception {
        User user = new User();
        user.setDatabaseDir(
                databaseDir.toString()
                        + File.separator);

        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(storageName);
        user.setCurrentMind(mind);
        return new Fixture(user, mind);
    }

    private static final class Fixture {
        private final User user;
        private Mind mind;

        private Fixture(
                User user,
                Mind mind) {
            this.user = user;
            this.mind = mind;
        }

        private void close() throws Exception {
            if (mind != null
                    && mind.isStorageUsed()) {
                mind =
                        (Mind) mind.closeStorage();
                user.setCurrentMind(mind);
            }
        }
    }
}
