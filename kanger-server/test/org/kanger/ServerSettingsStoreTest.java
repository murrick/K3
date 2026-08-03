package org.kanger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsStoreTest {

    private Path directory;
    private ExecutorService executor;

    @AfterEach
    void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (directory != null) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder())
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
    void readingMissingDefaultDoesNotCreateOrRewriteFile() throws Exception {
        ServerSettingsStore store = store();

        assertEquals("1964", store.get("server.port", "1964"));
        assertFalse(Files.exists(store.getFile()));
        assertEquals(0, store.size());
    }

    @Test
    void explicitMutationPersistsAndReloadsUtf8Values() throws Exception {
        ServerSettingsStore store = store();
        store.set("server.name", "КАНГЕР локальный");
        store.set("server.port", "1964");

        ServerSettingsStore reloaded = new ServerSettingsStore(store.getFile());
        reloaded.reload();

        assertEquals("КАНГЕР локальный",
                reloaded.get("server.name", "missing"));
        assertEquals("1964", reloaded.get("server.port", "missing"));
        assertFalse(new String(Files.readAllBytes(store.getFile()),
                StandardCharsets.UTF_8).contains("missing"));
    }

    @Test
    void nullMutationRemovesProperty() throws Exception {
        ServerSettingsStore store = store();
        store.set("server.port", "1964");
        store.set("server.port", null);

        ServerSettingsStore reloaded = new ServerSettingsStore(store.getFile());
        reloaded.reload();

        assertEquals("default", reloaded.get("server.port", "default"));
        assertEquals(0, reloaded.size());
    }

    @Test
    void prefixValuesAreReturnedInKeyOrder() throws Exception {
        ServerSettingsStore store = store();
        store.set("server.wrapper.option.20", "-Xmx2g");
        store.set("server.wrapper.option.10", "-Xms256m");
        store.set("server.port", "1964");

        assertEquals(Arrays.asList("-Xms256m", "-Xmx2g"),
                store.getByPrefix("server.wrapper.option."));
    }

    @Test
    void concurrentMutationsDoNotLoseKeysOrCorruptFile() throws Exception {
        final ServerSettingsStore store = store();
        executor = Executors.newFixedThreadPool(8);
        java.util.ArrayList<Future<?>> futures = new java.util.ArrayList<Future<?>>();

        for (int index = 0; index < 40; index++) {
            final int current = index;
            futures.add(executor.submit(() -> {
                store.set("test.key." + current, "value-" + current);
                return null;
            }));
        }
        for (Future<?> future : futures) {
            future.get(20L, TimeUnit.SECONDS);
        }

        ServerSettingsStore reloaded = new ServerSettingsStore(store.getFile());
        reloaded.reload();
        assertEquals(40, reloaded.size());
        for (int index = 0; index < 40; index++) {
            assertEquals("value-" + index,
                    reloaded.get("test.key." + index, "missing"));
        }

        try (Stream<Path> files = Files.list(directory)) {
            List<Path> remaining = files.collect(java.util.stream.Collectors.toList());
            assertEquals(1, remaining.size());
            assertEquals(store.getFile(), remaining.get(0));
        }
    }

    @Test
    void emptyKeysAndNullPrefixesAreRejected() throws Exception {
        ServerSettingsStore store = store();

        assertThrows(IllegalArgumentException.class,
                () -> store.get(" ", "default"));
        assertThrows(IllegalArgumentException.class,
                () -> store.set("", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> store.getByPrefix(null));
    }

    private ServerSettingsStore store() throws Exception {
        directory = Files.createTempDirectory("kanger-settings-");
        return new ServerSettingsStore(directory.resolve("kanger.conf"));
    }
}
