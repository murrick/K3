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
