/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Version;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** M4.1 durable Revision manifest and generation-integrity qualification. */
public class RevisionManifestStoreTest {

    @TempDir
    Path root;

    @Test
    void sealPersistsLineageAndGenerationDigest() throws Exception {
        Path generation = root.resolve("revision-2");
        Files.createDirectories(generation);
        Files.write(
                generation.resolve("alpha.base"),
                new byte[]{1, 2, 3});
        Files.write(
                generation.resolve("beta.base"),
                new byte[]{4, 5, 6, 7});

        UUID contextId = UUID.randomUUID();
        RevisionManifestStore.Manifest sealed =
                RevisionManifestStore.seal(
                        generation,
                        contextId,
                        2L,
                        1L);

        RevisionManifestStore.Manifest reopened =
                RevisionManifestStore.validate(
                        generation,
                        contextId,
                        2L,
                        1L);

        assertEquals(contextId, reopened.getContextId());
        assertEquals(2L, reopened.getRevision());
        assertEquals(1L, reopened.getParentRevision());
        assertEquals(
                RevisionManifestStore.STORAGE_FORMAT_VERSION,
                reopened.getStorageFormatVersion());
        assertEquals(
                Version.CORE_VERSION_S,
                reopened.getSemanticFingerprint());
        assertEquals(2, reopened.getBaseCount());
        assertArrayEquals(
                sealed.getGenerationDigest(),
                reopened.getGenerationDigest());
    }

    @Test
    void baseMutationAfterSealIsSemanticCorruption() throws Exception {
        Path generation = root.resolve("revision-1");
        Files.createDirectories(generation);
        Path base = generation.resolve("alpha.base");
        Files.write(base, new byte[]{1, 2, 3});

        UUID contextId = UUID.randomUUID();
        RevisionManifestStore.seal(
                generation,
                contextId,
                1L,
                0L);

        Files.write(base, new byte[]{1, 2, 4});

        StorageLifecycleException failure =
                assertThrows(
                        StorageLifecycleException.class,
                        () -> RevisionManifestStore.validate(
                                generation,
                                contextId,
                                1L,
                                0L));
        assertEquals(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void wrongExpectedParentIsSemanticCorruption() throws Exception {
        Path generation = root.resolve("revision-3");
        Files.createDirectories(generation);
        Files.write(
                generation.resolve("alpha.base"),
                new byte[]{9});

        UUID contextId = UUID.randomUUID();
        RevisionManifestStore.seal(
                generation,
                contextId,
                3L,
                2L);

        StorageLifecycleException failure =
                assertThrows(
                        StorageLifecycleException.class,
                        () -> RevisionManifestStore.validate(
                                generation,
                                contextId,
                                3L,
                                1L));
        assertEquals(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }
}
