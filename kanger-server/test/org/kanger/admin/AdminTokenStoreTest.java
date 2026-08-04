package org.kanger.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminTokenStoreTest {

    private Path directory;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-admin-token-");
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
    void createsOnePersistentOpaqueToken() throws Exception {
        Path file = directory.resolve("KANGER/admin.token");
        AdminTokenStore first = new AdminTokenStore(file);
        String created = first.loadOrCreate();
        String loaded = new AdminTokenStore(file).loadOrCreate();

        assertEquals(created, loaded);
        assertTrue(created.length() >= 32);
        assertEquals(created,
                new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim());
        assertFalse(created.contains("\n"));

        try {
            Set<PosixFilePermission> permissions =
                    Files.getPosixFilePermissions(file);
            assertEquals(2, permissions.size());
            assertTrue(permissions.contains(PosixFilePermission.OWNER_READ));
            assertTrue(permissions.contains(PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Protected service-home fallback is used on non-POSIX filesystems.
        }
    }

    @Test
    void loadDoesNotCreateMissingAuthority() {
        Path file = directory.resolve("missing.token");
        assertThrows(Exception.class, () -> new AdminTokenStore(file).load());
        assertFalse(Files.exists(file));
    }

    @Test
    void rejectsMalformedTokenFile() throws Exception {
        Path file = directory.resolve("bad.token");
        Files.write(file, "short\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> new AdminTokenStore(file).load());
    }
}
