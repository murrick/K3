/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification for the standalone DUMB 2.0 revision marker. */
public class RevisionStoreTest {

    @TempDir
    Path root;

    @Test
    void createPersistsInitialRevision() throws Exception {
        Path sidecar = root.resolve("initial.revision");

        assertEquals(RevisionStore.INITIAL_REVISION, RevisionStore.create(sidecar));
        assertEquals(RevisionStore.FILE_SIZE, Files.size(sidecar));
        assertEquals(RevisionStore.INITIAL_REVISION, RevisionStore.read(sidecar));
    }

    @Test
    void advanceIsMonotonicAndReopenable() throws Exception {
        Path sidecar = root.resolve("advance.revision");
        RevisionStore.create(sidecar);

        long r1 = RevisionStore.advance(sidecar, 0L);
        long r2 = RevisionStore.advance(sidecar, r1);

        assertEquals(1L, r1);
        assertEquals(2L, r2);
        assertEquals(r2, RevisionStore.read(sidecar));
    }

    @Test
    void staleAdvanceIsRejectedWithoutMutation() throws Exception {
        Path sidecar = root.resolve("stale.revision");
        RevisionStore.create(sidecar);
        assertEquals(1L, RevisionStore.advance(sidecar, 0L));

        assertThrows(IllegalStateException.class,
                () -> RevisionStore.advance(sidecar, 0L));
        assertEquals(1L, RevisionStore.read(sidecar));
    }

    @Test
    void createNeverSilentlyReplacesExistingRevision() throws Exception {
        Path sidecar = root.resolve("existing.revision");
        RevisionStore.create(sidecar);
        RevisionStore.advance(sidecar, 0L);

        assertThrows(FileAlreadyExistsException.class,
                () -> RevisionStore.create(sidecar));
        assertEquals(1L, RevisionStore.read(sidecar));
    }

    @Test
    void movingSidecarPreservesRevision() throws Exception {
        Path source = root.resolve("source.revision");
        Path target = root.resolve("target.revision");
        RevisionStore.create(source);
        long before = RevisionStore.advance(source, 0L);

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

        assertEquals(before, RevisionStore.read(target));
    }

    @Test
    void damagedPayloadIsSemanticCorruption() throws Exception {
        Path sidecar = root.resolve("damaged.revision");
        RevisionStore.create(sidecar);
        byte[] bytes = Files.readAllBytes(sidecar);
        bytes[8] ^= 0x01;
        Files.write(sidecar, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> RevisionStore.read(sidecar));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void unsupportedVersionWithValidChecksumIsFormatIncompatible() throws Exception {
        Path sidecar = root.resolve("future.revision");
        RevisionStore.create(sidecar);
        byte[] bytes = Files.readAllBytes(sidecar);
        ByteBuffer.wrap(bytes).putInt(4, RevisionStore.VERSION + 1);
        rewriteChecksum(bytes);
        Files.write(sidecar, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> RevisionStore.read(sidecar));

        assertEquals(StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                failure.getErrorCode());
    }

    @Test
    void negativeRevisionWithValidChecksumIsSemanticCorruption() throws Exception {
        Path sidecar = root.resolve("negative.revision");
        RevisionStore.create(sidecar);
        byte[] bytes = Files.readAllBytes(sidecar);
        ByteBuffer.wrap(bytes).putLong(8, -1L);
        rewriteChecksum(bytes);
        Files.write(sidecar, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> RevisionStore.read(sidecar));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void malformedLengthIsSemanticCorruption() throws Exception {
        Path sidecar = root.resolve("short.revision");
        Files.write(sidecar, new byte[]{1, 2, 3});

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> RevisionStore.read(sidecar));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    private static void rewriteChecksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, RevisionStore.PAYLOAD_SIZE);
        ByteBuffer.wrap(bytes).putInt(RevisionStore.PAYLOAD_SIZE, (int) crc.getValue());
    }
}
