/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountDeletionStoreTest {

    private Path directory;
    private Path file;
    private MutableClock clock;
    private AccountDeletionStore store;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-deletion-store-");
        file = directory.resolve("account-deletions.conf");
        clock = new MutableClock(1_000_000L);
        store = new AccountDeletionStore(file, clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (directory != null) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void preparedDeletionSurvivesRestartWithExactIdentity() throws Exception {
        AccountDeletion prepared = store.prepare(
                7L,
                "rick",
                "rick@example.org",
                directory.resolve("KANGER/7"),
                directory.resolve("KANGER/.quarantine"));

        AccountDeletionStore restarted = new AccountDeletionStore(file, clock);
        AccountDeletion restored = restarted.findById(prepared.getId());

        assertNotNull(restored);
        assertEquals(7L, restored.getUserId());
        assertEquals("rick", restored.getLogin());
        assertEquals(AccountDeletionState.PREPARED, restored.getState());
        assertEquals(prepared.getCanonicalHome(), restored.getCanonicalHome());
        assertEquals(prepared.getQuarantineHome(), restored.getQuarantineHome());
    }

    @Test
    void stateTransitionsAreForwardOnlyAndCompleteIsRetained() throws Exception {
        AccountDeletion prepared = store.prepare(
                7L,
                "rick",
                "",
                directory.resolve("KANGER/7"),
                directory.resolve("KANGER/.quarantine"));
        clock.advance(10L);
        AccountDeletion credentialRemoved = store.advance(
                prepared.getId(),
                AccountDeletionState.CREDENTIAL_REMOVED,
                "credential removed");
        clock.advance(10L);
        AccountDeletion complete = store.advance(
                prepared.getId(),
                AccountDeletionState.COMPLETE,
                "complete");

        assertEquals(AccountDeletionState.CREDENTIAL_REMOVED,
                credentialRemoved.getState());
        assertEquals(AccountDeletionState.COMPLETE, complete.getState());
        assertTrue(complete.isComplete());
        assertEquals(1, store.all().size());
        assertThrows(IllegalStateException.class,
                () -> store.advance(
                        prepared.getId(),
                        AccountDeletionState.PREPARED,
                        "backward"));
    }

    @Test
    void prepareIsIdempotentForSameUserAndRejectsIdentityConflict()
            throws Exception {
        Path canonical = directory.resolve("KANGER/7");
        Path quarantine = directory.resolve("KANGER/.quarantine");
        AccountDeletion first = store.prepare(
                7L, "rick", "rick@example.org", canonical, quarantine);
        AccountDeletion repeated = store.prepare(
                7L, "rick", "rick@example.org", canonical, quarantine);

        assertEquals(first.getId(), repeated.getId());
        assertThrows(IllegalStateException.class,
                () -> store.prepare(
                        7L,
                        "other",
                        "other@example.org",
                        canonical,
                        quarantine));
    }

    @Test
    void diagnosticUpdatePreservesLifecycleState() throws Exception {
        AccountDeletion prepared = store.prepare(
                7L,
                "rick",
                "",
                directory.resolve("KANGER/7"),
                directory.resolve("KANGER/.quarantine"));
        clock.advance(5L);

        AccountDeletion diagnosed = store.diagnose(
                prepared.getId(), "synthetic failure");

        assertEquals(AccountDeletionState.PREPARED, diagnosed.getState());
        assertEquals("synthetic failure", diagnosed.getDiagnostic());
        assertTrue(diagnosed.getUpdatedAt() > prepared.getUpdatedAt());
    }

    private static final class MutableClock
            implements AccountDeletionStore.TimeSource {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long now() {
            return now;
        }

        private void advance(long millis) {
            now += millis;
        }
    }
}
