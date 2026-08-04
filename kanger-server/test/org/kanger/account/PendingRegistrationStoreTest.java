/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kanger.security.CredentialMaterial;
import org.kanger.security.CredentialStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingRegistrationStoreTest {

    private Path directory;
    private Path file;
    private MutableClock clock;
    private PendingRegistrationStore.Config config;
    private CredentialStore credentials;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-pending-");
        file = directory.resolve("pending-registrations.conf");
        clock = new MutableClock(1_000_000L);
        config = new PendingRegistrationStore.Config(
                7L * 24L * 60L * 60L * 1000L,
                24L * 60L * 60L * 1000L,
                15L * 60L * 1000L,
                60L * 1000L,
                100);
        credentials = new CredentialStore(directory.resolve("users.conf"));
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
    void pendingRegistrationSurvivesRestartWithoutRawSecrets() throws Exception {
        PendingRegistrationStore first = store();
        PendingRegistrationStore.Created created = first.create(draft(
                "Rick", "Rick@Example.org", "not persisted plaintext"));

        String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertFalse(raw.contains("not persisted plaintext"));
        assertFalse(raw.contains(created.getConfirmationToken()));
        assertFalse(raw.contains("Rick@Example.org"));

        PendingRegistrationStore restarted = store();
        PendingRegistration resolved = restarted.resolveConfirmation(
                created.getConfirmationToken());

        assertEquals(created.getRegistration().getId(), resolved.getId());
        assertEquals("Rick", resolved.getLogin());
        assertEquals("rick@example.org", resolved.getEmail());
        assertEquals(1, restarted.size());
    }

    @Test
    void confirmationExpiryPreservesLivePendingRegistration() throws Exception {
        PendingRegistrationStore store = store();
        PendingRegistrationStore.Created created = store.create(draft(
                "rick", "rick@example.org", "password"));
        clock.advance(config.confirmationTtlMillis + 1L);

        PendingRegistrationException failure = assertThrows(
                PendingRegistrationException.class,
                () -> store.resolveConfirmation(created.getConfirmationToken()));

        assertEquals(AccountErrorCode.CONFIRMATION_TOKEN_EXPIRED,
                failure.getCode());
        assertEquals(1, store.size());
    }

    @Test
    void pendingTtlEvictsRecordAtRestart() throws Exception {
        PendingRegistrationStore first = store();
        first.create(draft("rick", "rick@example.org", "password"));
        clock.advance(config.pendingTtlMillis + 1L);

        PendingRegistrationStore restarted = store();

        assertEquals(0, restarted.size());
        String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertFalse(raw.contains("v1\t"));
    }

    @Test
    void pendingLoginVerifiesPasswordAndIssuesScopedActionToken()
            throws Exception {
        PendingRegistrationStore store = store();
        store.create(draft("rick", "rick@example.org", "password"));

        PendingRegistrationException rejected = assertThrows(
                PendingRegistrationException.class,
                () -> store.authenticate("rick", "wrong"));
        assertEquals(AccountErrorCode.AUTHENTICATION_FAILED,
                rejected.getCode());

        PendingRegistrationStore.Authenticated authenticated =
                store.authenticate("rick", "password");
        assertNotNull(authenticated.getActionToken());
        assertEquals("rick", authenticated.getRegistration().getLogin());
        String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertFalse(raw.contains(authenticated.getActionToken()));
    }

    @Test
    void resendHonorsCooldownAndInvalidatesPreviousConfirmation()
            throws Exception {
        PendingRegistrationStore store = store();
        PendingRegistrationStore.Created created = store.create(draft(
                "rick", "rick@example.org", "password"));
        PendingRegistrationStore.Authenticated authenticated =
                store.authenticate("rick", "password");

        PendingRegistrationException limited = assertThrows(
                PendingRegistrationException.class,
                () -> store.resend(authenticated.getActionToken()));
        assertEquals(AccountErrorCode.RESEND_RATE_LIMITED, limited.getCode());

        clock.advance(config.resendCooldownMillis + 1L);
        PendingRegistrationStore.Rotation rotation =
                store.resend(authenticated.getActionToken());

        PendingRegistrationException oldToken = assertThrows(
                PendingRegistrationException.class,
                () -> store.resolveConfirmation(created.getConfirmationToken()));
        assertEquals(AccountErrorCode.CONFIRMATION_TOKEN_INVALID,
                oldToken.getCode());
        assertEquals(rotation.getRegistration().getId(),
                store.resolveConfirmation(rotation.getConfirmationToken()).getId());
    }

    @Test
    void changingEmailRotatesConfirmationAndActionTokens() throws Exception {
        PendingRegistrationStore store = store();
        PendingRegistrationStore.Created created = store.create(draft(
                "rick", "old@example.org", "password"));
        PendingRegistrationStore.Authenticated authenticated =
                store.authenticate("rick", "password");

        PendingRegistrationStore.Rotation changed = store.changeEmail(
                authenticated.getActionToken(), " NEW@Example.org ");

        assertEquals("new@example.org", changed.getRegistration().getEmail());
        assertFalse(store.containsEmail("old@example.org"));
        assertTrue(store.containsEmail("NEW@example.org"));
        assertThrows(PendingRegistrationException.class,
                () -> store.resolveConfirmation(created.getConfirmationToken()));
        assertThrows(PendingRegistrationException.class,
                () -> store.cancel(authenticated.getActionToken()));
        assertEquals(changed.getRegistration().getId(),
                store.resolveConfirmation(changed.getConfirmationToken()).getId());
        assertNotNull(store.cancel(changed.getActionToken()));
        assertEquals(0, store.size());
    }

    @Test
    void completionRemovesOnlyMatchingPendingAndToken() throws Exception {
        PendingRegistrationStore store = store();
        PendingRegistrationStore.Created first = store.create(draft(
                "first", "first@example.org", "first password"));
        PendingRegistrationStore.Created second = store.create(draft(
                "second", "second@example.org", "second password"));

        assertFalse(store.complete(first.getRegistration().getId(),
                second.getConfirmationToken()));
        assertTrue(store.complete(first.getRegistration().getId(),
                first.getConfirmationToken()));
        assertEquals(1, store.size());
        assertEquals(second.getRegistration().getId(),
                store.resolveConfirmation(second.getConfirmationToken()).getId());
    }

    private PendingRegistrationStore store() throws Exception {
        return new PendingRegistrationStore(file, config, clock);
    }

    private PendingRegistrationStore.Draft draft(String login,
                                                  String email,
                                                  String password)
            throws Exception {
        CredentialMaterial material = credentials.preparePassword(password);
        return new PendingRegistrationStore.Draft(
                login,
                email,
                material,
                "Name",
                "Country",
                "City",
                Boolean.TRUE);
    }

    private static final class MutableClock
            implements PendingRegistrationStore.TimeSource {
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
