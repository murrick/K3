/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IMind;
import org.kanger.storage.DB;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification for persistent DUMB Context identity. */
public class DumbContextIdentityTest {

    @Test
    void reopenPreservesContextId() throws Exception {
        Path root = Files.createTempDirectory("kanger-context-reopen-");
        DB db = null;
        try {
            db = newDb(root);
            db.use("reopen");
            UUID first = db.getContextId();
            assertNotNull(first);

            db.close();
            db.use("reopen");
            assertEquals(first, db.getContextId());
        } finally {
            closeQuietly(db);
            deleteTree(root);
        }
    }

    @Test
    void independentContextsReceiveDistinctIds() throws Exception {
        Path root = Files.createTempDirectory("kanger-context-distinct-");
        DB db = null;
        try {
            db = newDb(root);
            db.use("first");
            UUID first = db.getContextId();
            db.close();

            db.use("second");
            UUID second = db.getContextId();
            assertNotNull(first);
            assertNotNull(second);
            assertNotEquals(first, second);
        } finally {
            closeQuietly(db);
            deleteTree(root);
        }
    }

    @Test
    void movingWholeStoragePreservesContextId() throws Exception {
        Path root = Files.createTempDirectory("kanger-context-move-");
        DB db = null;
        try {
            db = newDb(root);
            db.use("source");
            db.getBase("probe");
            UUID before = db.getContextId();
            db.close();

            moveStorage(root, "source", "target");

            db.use("target");
            db.getBase("probe");
            assertEquals(before, db.getContextId());
        } finally {
            closeQuietly(db);
            deleteTree(root);
        }
    }

    @Test
    void reindexPreservesContextId() throws Exception {
        Path root = Files.createTempDirectory("kanger-context-reindex-");
        User user = new User();
        user.setDatabaseDir(root.toString() + File.separator);
        DB db = new DB();
        db.init(user);
        IMind mind = new Mind(user);
        try {
            mind = mind.useStorage("reindex");
            UUID before = db.getContextId();
            assertNotNull(before);

            mind = user.reindex(null, mind, "reindex");

            assertEquals(before, db.getContextId());
        } finally {
            if (!user.isClosed()) {
                try {
                    mind.closeStorage();
                } catch (Exception ignored) {
                }
            }
            deleteTree(root);
        }
    }

    @Test
    void legacyGenerationWithoutContextIdIsRejected() throws Exception {
        Path root = Files.createTempDirectory("kanger-context-legacy-");
        DB db = null;
        try {
            Files.write(root.resolve("legacy.store"), new byte[]{1});
            db = newDb(root);

            StorageLifecycleException failure = assertThrows(
                    StorageLifecycleException.class,
                    () -> db.use("legacy"));

            assertEquals(StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                    failure.getErrorCode());
            assertFalse(Files.exists(root.resolve("legacy.context")));
        } finally {
            closeQuietly(db);
            deleteTree(root);
        }
    }

    @Test
    void damagedContextSidecarIsRejectedAsCorruption() throws Exception {
        Path root = Files.createTempDirectory("kanger-context-corrupt-");
        DB db = null;
        try {
            db = newDb(root);
            db.use("damaged");
            db.close();

            Path sidecar = root.resolve("damaged.context");
            byte[] bytes = Files.readAllBytes(sidecar);
            bytes[8] ^= 0x01;
            Files.write(sidecar, bytes);

            StorageLifecycleException failure = assertThrows(
                    StorageLifecycleException.class,
                    () -> db.use("damaged"));

            assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                    failure.getErrorCode());
        } finally {
            closeQuietly(db);
            deleteTree(root);
        }
    }

    private static DB newDb(Path root) throws Exception {
        User user = new User();
        user.setDatabaseDir(root.toString() + File.separator);
        DB db = new DB();
        db.init(user);
        return db;
    }

    private static void moveStorage(Path root, String source, String target)
            throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(root)) {
            files = stream
                    .filter(path -> path.getFileName().toString()
                            .startsWith(source + "."))
                    .collect(Collectors.toList());
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            String suffix = name.substring(source.length());
            Files.move(file, root.resolve(target + suffix),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void closeQuietly(DB db) {
        if (db == null) {
            return;
        }
        try {
            db.close();
        } catch (Exception ignored) {
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
