package org.kanger.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialStoreTest {

    private Path directory;
    private Path file;
    private CredentialStore store;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-credentials-");
        file = directory.resolve("users.conf");
        store = new CredentialStore(file, 1000, new SecureRandom());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (directory != null) {
            Files.walk(directory)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    void createsAndAuthenticatesVersionedCredential() throws Exception {
        long userId = store.create("rick", "correct horse battery staple");

        assertEquals(1L, userId);
        assertEquals(userId,
                store.authenticate("rick", "correct horse battery staple"));

        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(content.contains("v2\t"));
        assertFalse(content.contains("correct horse battery staple"));
        assertFalse(content.contains(CredentialStore.legacyToken(
                "rick", "correct horse battery staple") + "="));
    }

    @Test
    void preparedCreationPublishesCredentialAfterPreparation() throws Exception {
        AtomicLong preparedUserId = new AtomicLong(-1L);

        long userId = store.createPrepared(
                "rick",
                "correct horse battery staple",
                preparedUserId::set);

        assertEquals(userId, preparedUserId.get());
        assertEquals(Long.valueOf(userId), store.findUserId("rick"));
        assertEquals(userId,
                store.authenticate("rick", "correct horse battery staple"));
    }

    @Test
    void exactPublicationUsesRequestedRecoveryUserId() throws Exception {
        CredentialMaterial material = store.preparePassword("pending password");

        long userId = store.publishPrepared(17L, "rick", material);

        assertEquals(17L, userId);
        assertEquals(Long.valueOf(17L), store.findUserId("rick"));
        assertEquals(17L, store.authenticate("rick", "pending password"));
    }

    @Test
    void exactPublicationRejectsExistingLoginOrUserId() throws Exception {
        CredentialMaterial first = store.preparePassword("first password");
        CredentialMaterial second = store.preparePassword("second password");
        store.publishPrepared(17L, "rick", first);

        assertThrows(AuthenticationErrorException.class,
                () -> store.publishPrepared(18L, "rick", second));
        assertThrows(AuthenticationErrorException.class,
                () -> store.publishPrepared(17L, "other", second));
        assertEquals(17L, store.authenticate("rick", "first password"));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("other", "second password"));
    }

    @Test
    void failedPreparationPublishesNoCredentialAndAllowsRetry() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> store.createPrepared(
                        "rick",
                        "correct horse battery staple",
                        userId -> {
                            throw new IllegalStateException("synthetic preparation failure");
                        }));

        assertNull(store.findUserId("rick"));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "correct horse battery staple"));

        assertEquals(2L,
                store.createPrepared(
                        "rick",
                        "correct horse battery staple",
                        userId -> {
                            // retry succeeds without manual cleanup; id 1 remains retired
                        }));
    }

    @Test
    void deletedUserIdIsNeverReused() throws Exception {
        long first = store.create("first", "first password");
        assertEquals(1L, first);
        assertTrue(store.delete(first));

        long second = store.create("second", "second password");

        assertEquals(2L, second);
        assertEquals(2L, store.authenticate("second", "second password"));
    }

    @Test
    void failedDeletionPreparationLeavesCredentialUsable() throws Exception {
        long userId = store.create("rick", "correct password");

        assertThrows(IllegalStateException.class,
                () -> store.deletePrepared(userId, preparedUserId -> {
                    throw new IllegalStateException("synthetic runtime close failure");
                }));

        assertEquals(userId, store.authenticate("rick", "correct password"));
        assertTrue(store.containsUserId(userId));
    }

    @Test
    void successfulPreparedDeletionRemovesCredentialAfterCallback()
            throws Exception {
        long userId = store.create("rick", "correct password");
        AtomicLong prepared = new AtomicLong(-1L);

        assertTrue(store.deletePrepared(userId, prepared::set));

        assertEquals(userId, prepared.get());
        assertFalse(store.containsUserId(userId));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "correct password"));
    }

    @Test
    void separateFacadesCoordinateOneCredentialAuthority() throws Exception {
        final CredentialStore first = new CredentialStore(
                file, 1000, new SecureRandom());
        final CredentialStore second = new CredentialStore(
                file, 1000, new SecureRandom());
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> firstId = executor.submit(() -> {
                start.await();
                return first.createPrepared("first", "first password", userId -> {
                    // prepared
                });
            });
            Future<Long> secondId = executor.submit(() -> {
                start.await();
                return second.createPrepared("second", "second password", userId -> {
                    // prepared
                });
            });

            start.countDown();
            Set<Long> allocated = new HashSet<Long>(Arrays.asList(
                    firstId.get(), secondId.get()));

            assertEquals(new HashSet<Long>(Arrays.asList(1L, 2L)), allocated);
            assertEquals(first.findUserId("first").longValue(),
                    first.authenticate("first", "first password"));
            assertEquals(second.findUserId("second").longValue(),
                    second.authenticate("second", "second password"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deleteRemovesVersionedCredential() throws Exception {
        long userId = store.create("rick", "correct horse battery staple");

        assertTrue(store.delete(userId));
        assertFalse(store.delete(userId));
        assertNull(store.findUserId("rick"));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "correct horse battery staple"));
    }

    @Test
    void failedAuthenticationDoesNotRewriteCredentialFile() throws Exception {
        store.create("rick", "correct password");
        byte[] before = Files.readAllBytes(file);

        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "wrong password"));

        byte[] after = Files.readAllBytes(file);
        assertTrue(java.util.Arrays.equals(before, after));
    }

    @Test
    void migratesLegacyCredentialAfterSuccessfulAuthentication() throws Exception {
        String login = "legacy-user";
        String password = "legacy-password";
        String legacy = CredentialStore.legacyToken(login, password);
        Files.write(file,
                java.util.Collections.singletonList(legacy + "=7"),
                StandardCharsets.UTF_8);

        assertEquals(7L, store.authenticate(login, password));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String content = join(lines);
        assertTrue(content.contains("v2\t"));
        assertFalse(content.contains(legacy + "=7"));
        assertEquals(7L, store.authenticate(login, password));
    }

    @Test
    void deleteRemovesLegacyCredentialByUserId() throws Exception {
        String login = "legacy-user";
        String password = "legacy-password";
        String legacy = CredentialStore.legacyToken(login, password);
        Files.write(file,
                java.util.Collections.singletonList(legacy + "=7"),
                StandardCharsets.UTF_8);

        assertTrue(store.delete(7L));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate(login, password));
    }

    @Test
    void updateReplacesCredentialForSameUser() throws Exception {
        long userId = store.create("old-login", "old-password");
        store.update(userId, "new-login", "new-password");

        assertEquals(userId, store.authenticate("new-login", "new-password"));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("old-login", "old-password"));
    }

    private static String join(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            result.append(line).append('\n');
        }
        return result.toString();
    }
}
