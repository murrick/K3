package org.kanger.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationTokenStoreTest {

    private Path directory;
    private Path file;
    private ConfirmationTokenStore store;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-confirmation-");
        file = directory.resolve("confirmation-tokens.conf");
        store = new ConfirmationTokenStore(file);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (directory != null) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    void issuedTokenIsOneTimeAndPersistent() throws Exception {
        String token = store.issue(7L, 60_000L);
        ConfirmationTokenStore restarted = new ConfirmationTokenStore(file);

        assertEquals(7L, restarted.consume(token));
        assertThrows(AuthenticationErrorException.class,
                () -> restarted.consume(token));
    }

    @Test
    void bindRotatesPreviousTokenForUser() throws Exception {
        store.bind("first", 7L, 60_000L);
        store.bind("second", 7L, 60_000L);

        assertThrows(AuthenticationErrorException.class,
                () -> store.consume("first"));
        assertEquals(7L, store.consume("second"));
    }

    @Test
    void revokeRemovesEveryTokenForExactUser() throws Exception {
        store.bind("first", 7L, 60_000L);
        store.bind("other", 8L, 60_000L);

        assertTrue(store.revoke(7L));
        assertFalse(store.revoke(7L));
        assertThrows(AuthenticationErrorException.class,
                () -> store.consume("first"));
        assertEquals(8L, store.consume("other"));
    }

    @Test
    void separateFacadesPreserveBothConcurrentBindings() throws Exception {
        ConfirmationTokenStore first = new ConfirmationTokenStore(file);
        ConfirmationTokenStore second = new ConfirmationTokenStore(file);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstWrite = executor.submit(() -> {
                start.await();
                first.bind("first", 7L, 60_000L);
                return null;
            });
            Future<?> secondWrite = executor.submit(() -> {
                start.await();
                second.bind("second", 8L, 60_000L);
                return null;
            });

            start.countDown();
            firstWrite.get();
            secondWrite.get();

            assertEquals(7L, first.consume("first"));
            assertEquals(8L, second.consume("second"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = store.issue(7L, 1L);
        Thread.sleep(5L);

        assertThrows(AuthenticationErrorException.class,
                () -> store.consume(token));
    }
}
