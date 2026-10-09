package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.ITerm;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.11 default-query federation through the ordinary Mind.query surface. */
public class FederatedDefaultQueryTest {

    @TempDir
    Path root;

    @Test
    void foreignOnlyValueIsReturnedByXButDoesNotBecomeDurableKnowledge()
            throws Exception {
        context("A", "!age(Tom,42);");

        Fixture x = open("X");
        try {
            assertTrue(Boolean.TRUE.equals(
                    x.mind.query(
                            "!resident(Rick,Vienna);",
                            null,
                            false)));
            x.data.connectContext("A");

            long before = x.data.getRevision();
            assertTrue(Boolean.TRUE.equals(
                    x.mind.query(
                            "?$x age(Tom,x);",
                            null,
                            false)));

            assertEquals(1, x.mind.getValues().size());
            Map<String, ITerm> row =
                    x.mind.getValues().iterator().next();
            assertEquals("42.0",
                    row.get("x").toString());
            assertEquals(
                    before,
                    x.data.getRevision(),
                    "read-only federated query advanced X revision");

            x.data.disconnectContext("A");
            assertNull(
                    x.mind.query(
                            "?$x age(Tom,x);",
                            null,
                            false),
                    "foreign substitution escaped into local X knowledge");
        } finally {
            x.close();
        }

        Fixture reopened = open("X");
        try {
            assertNull(
                    reopened.mind.query(
                            "?$x age(Tom,x);",
                            null,
                            false),
                    "foreign substitution survived close/reopen as X knowledge");
        } finally {
            reopened.close();
        }
    }

    @Test
    void foreignNegativeEvidenceParticipatesInHistoricalFalsePass()
            throws Exception {
        context("Aneg", "!~male(Tom);");

        Fixture x = open("Xneg");
        try {
            assertTrue(Boolean.TRUE.equals(
                    x.mind.query(
                            "!resident(Rick,Vienna);",
                            null,
                            false)));
            x.data.connectContext("Aneg");

            Boolean answer =
                    x.mind.query(
                            "?male(Tom);",
                            null,
                            false);
            assertFalse(Boolean.TRUE.equals(answer));
            assertEquals(Boolean.FALSE, answer);
        } finally {
            x.close();
        }
    }

    @Test
    void compoundQueryUsesForeignAgeAndKeepsCalculatedConditionLocal()
            throws Exception {
        context("Age", "!age(Tom,18);");

        Fixture x = open("Xcompound");
        try {
            assertTrue(Boolean.TRUE.equals(
                    x.mind.query(
                            "!son(Tom,John);",
                            null,
                            false)));
            x.data.connectContext("Age");

            assertTrue(Boolean.TRUE.equals(
                    x.mind.query(
                            "?$x $y son(x,John) && age(x,y) && y > 10;",
                            null,
                            false)));

            assertEquals(1, x.mind.getValues().size());
            Map<String, ITerm> row =
                    x.mind.getValues().iterator().next();
            assertEquals("Tom", row.get("x").toString());
            assertEquals("18.0", row.get("y").toString());
        } finally {
            x.close();
        }
    }

    @Test
    void liveTransactionOverlayCanConsumeForeignEvidence()
            throws Exception {
        context("Alive", "!age(Tom,42);");

        Fixture x = open("Xlive");
        try {
            assertTrue(Boolean.TRUE.equals(
                    x.mind.query(
                            "!resident(Rick,Vienna);",
                            null,
                            false)));
            x.data.connectContext("Alive");
            long durableRevision = x.data.getRevision();

            Mind tx = new Mind(x.mind);
            x.user.setCurrentMind(tx);
            try {
                assertTrue(Boolean.TRUE.equals(
                        tx.query(
                                "!@x age(x,42) -> special(x);",
                                null,
                                false)));
                assertTrue(Boolean.TRUE.equals(
                        tx.query(
                                "?special(Tom);",
                                null,
                                false)));
                assertEquals(
                        durableRevision,
                        x.data.getRevision(),
                        "live federated query published the transaction overlay");
            } finally {
                x.mind.release(tx);
                x.user.setCurrentMind(x.mind);
            }

            assertNull(
                    x.mind.query(
                            "?special(Tom);",
                            null,
                            false),
                    "rolled-back live rule leaked into durable X");
        } finally {
            x.close();
        }
    }

    @Test
    void localTrueDoesNotTruncateForeignEnumeration() throws Exception {
        Fixture donor = open("ages");
        try {
            assertTrue(donor.mind.compile("!age(John,37); !age(Tom,12); !age(Sarah,4);"));
        } finally { donor.close(); }
        Fixture x = open("ages-X");
        try {
            x.data.connectContext("ages");
            assertTrue(x.mind.query("?$x $y age(x,y);", null, false));
            assertEquals(3, x.mind.getValues().size());
            assertTrue(x.mind.query("!age(Mary,30);", null, false));
            long revision = x.data.getRevision();
            for (int i = 0; i < 2; i++) {
                assertTrue(x.mind.query("?$x $y age(x,y);", null, false));
                java.util.Set<String> rows = new java.util.HashSet<>();
                for (Map<String, ITerm> row : x.mind.getValues())
                    rows.add(row.get("x") + ":" + row.get("y"));
                assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(
                        "John:37.0", "Tom:12.0", "Sarah:4.0", "Mary:30.0")), rows);
                assertEquals(4, x.mind.getSolutions().size());
                assertEquals(revision, x.data.getRevision());
                assertEquals(1, x.data.federationSnapshot().getConnections().size());
            }
            assertTrue(x.mind.query("?$z $a age(z,a);", null, false));
            assertEquals(4, x.mind.getValues().size());
            assertEquals(4, x.mind.getSolutions().size());
            assertTrue(x.mind.query("?$y age(Mary,y);", null, false));
            assertEquals(1, x.mind.getValues().size());
            x.data.disconnectContext("ages");
            assertTrue(x.mind.query("?$x $y age(x,y);", null, false));
            assertEquals(1, x.mind.getValues().size());
        } finally { x.close(); }
    }

    @Test
    void localEnumerationIncludesDelayedForeignRowsAndDeduplicates() throws Exception {
        Fixture a = open("enum-A");
        try { assertTrue(a.mind.compile("!@x seed(x) -> p(x); !p(Mary);")); }
        finally { a.close(); }
        context("enum-B", "!seed(Tom);");
        Fixture x = open("enum-X");
        try {
            assertTrue(x.mind.query("!p(Mary);", null, false));
            x.data.connectContext("enum-A"); x.data.connectContext("enum-B");
            assertTrue(x.mind.query("?$x p(x);", null, false));
            java.util.Set<String> rows = new java.util.HashSet<>();
            for (Map<String, ITerm> row : x.mind.getValues()) rows.add(row.get("x").toString());
            assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("Mary", "Tom")), rows);
            assertEquals(2, x.mind.getSolutions().size());
        } finally { x.close(); }
    }

    @Test
    void conflictedLocalWorkingRowIsExcludedBesideSafeRows() throws Exception {
        context("enum-negative", "!~p(John);");
        context("enum-donor", "!p(Tom);");
        Fixture x = open("enum-conflict-X");
        try {
            assertTrue(x.mind.query("!p(Mary);", null, false));
            x.data.connectContext("enum-negative"); x.data.connectContext("enum-donor");
            long revision = x.data.getRevision();
            Mind tx = new Mind(x.mind); x.user.setCurrentMind(tx);
            try {
                assertTrue(tx.query("!p(John);", null, false));
                assertTrue(tx.query("?$x p(x);", null, false));
                java.util.Set<String> rows = new java.util.HashSet<>();
                for (Map<String, ITerm> row : tx.getValues()) rows.add(row.get("x").toString());
                assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("Mary", "Tom")), rows);
                assertEquals(2, tx.getSolutions().size());
                assertFalse(tx.getQueryConflicts().isEmpty());
                assertEquals(revision, x.data.getRevision());
            } finally { x.mind.release(tx); x.user.setCurrentMind(x.mind); }
            x.data.disconnectContext("enum-negative"); x.data.disconnectContext("enum-donor");
            assertTrue(x.mind.query("?$x p(x);", null, false));
            assertEquals(1, x.mind.getValues().size());
        } finally { x.close(); }
    }

    @Test
    void entirelyConflictedLocalWorkingRowsDoNotRestoreStaleLocalTrue() throws Exception {
        context("enum-all-negative", "!~p(John);");
        Fixture x = open("enum-all-X");
        try {
            x.data.connectContext("enum-all-negative");
            Mind tx = new Mind(x.mind); x.user.setCurrentMind(tx);
            try {
                assertTrue(tx.query("!p(John);", null, false));
                assertNull(tx.query("?$x p(x);", null, false));
                assertTrue(tx.getValues().isEmpty());
                assertTrue(tx.getSolutions().isEmpty());
                assertFalse(tx.getQueryConflicts().isEmpty());
            } finally { x.mind.release(tx); x.user.setCurrentMind(x.mind); }
        } finally { x.close(); }
    }

    @Test
    void federatedFallbackLogsOneFinalPresentationForLocallyProvenCompoundQuery() throws Exception {
        context("log-donor", "!age(Tom,12);");
        Fixture x = open("log-X");
        try {
            assertTrue(x.mind.compile("!p(Mary); !age(Mary,30);"));
            x.data.connectContext("log-donor");
            long revision = x.data.getRevision();
            for (int i = 0; i < 2; i++) {
                assertTrue(x.mind.query("?$x $y p(x), age(x,y), y > 10;", null, true));
                int solutions = 0, values = 0;
                for (org.kanger.interfaces.ILogEntry entry : x.mind.getLog()) {
                    if (entry.getType() == org.kanger.enums.LogMode.SOLVES
                            && entry.getRecord().startsWith("Solutions (")) solutions++;
                    if (entry.getType() == org.kanger.enums.LogMode.VALUES
                            && entry.getRecord().startsWith("Values (")) values++;
                }
                assertEquals(1, solutions, "one final Solutions block per query");
                assertEquals(1, values, "one final Values block per query");
                assertEquals(2, x.mind.getSolutions().size());
                assertEquals(1, x.mind.getValues().size());
                assertEquals(revision, x.data.getRevision());
            }
        } finally { x.close(); }
    }

    private void context(
            String name,
            String assertion) throws Exception {
        Fixture fixture = open(name);
        try {
            assertTrue(Boolean.TRUE.equals(
                    fixture.mind.query(
                            assertion,
                            null,
                            false)));
        } finally {
            fixture.close();
        }
    }

    private Fixture open(String name) throws Exception {
        User user = new User();
        user.setDatabaseDir(
                root.toString() + File.separator);

        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name);
        user.setCurrentMind(mind);
        return new Fixture(user, data, mind);
    }

    private static final class Fixture {
        private final User user;
        private final DB data;
        private Mind mind;

        private Fixture(
                User user,
                DB data,
                Mind mind) {
            this.user = user;
            this.data = data;
            this.mind = mind;
        }

        private void close() throws Exception {
            if (mind != null
                    && mind.isStorageUsed()) {
                mind = (Mind) mind.closeStorage();
                user.setCurrentMind(mind);
            }
        }
    }
}
