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
import java.util.UUID;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification for the standalone DUMB 2.0 Context identity sidecar. */
public class ContextIdStoreTest {

    @TempDir
    Path root;

    @Test
    void createPersistsReopenableContextId() throws Exception {
        Path sidecar = root.resolve("reopen.context");

        UUID created = ContextIdStore.create(sidecar);

        assertFalse(isZero(created));
        assertEquals(ContextIdStore.FILE_SIZE, Files.size(sidecar));
        assertEquals(created, ContextIdStore.read(sidecar));
    }

    @Test
    void independentSidecarsReceiveDistinctIds() throws Exception {
        UUID first = ContextIdStore.create(root.resolve("first.context"));
        UUID second = ContextIdStore.create(root.resolve("second.context"));

        assertNotEquals(first, second);
    }

    @Test
    void movingSidecarPreservesContextId() throws Exception {
        Path source = root.resolve("source.context");
        Path target = root.resolve("target.context");
        UUID before = ContextIdStore.create(source);

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

        assertEquals(before, ContextIdStore.read(target));
    }

    @Test
    void createNeverSilentlyReplacesExistingIdentity() throws Exception {
        Path sidecar = root.resolve("existing.context");
        UUID original = ContextIdStore.create(sidecar);

        assertThrows(FileAlreadyExistsException.class,
                () -> ContextIdStore.create(sidecar));
        assertEquals(original, ContextIdStore.read(sidecar));
    }

    @Test
    void damagedPayloadIsSemanticCorruption() throws Exception {
        Path sidecar = root.resolve("damaged.context");
        ContextIdStore.create(sidecar);
        byte[] bytes = Files.readAllBytes(sidecar);
        bytes[8] ^= 0x01;
        Files.write(sidecar, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextIdStore.read(sidecar));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void unsupportedVersionWithValidChecksumIsFormatIncompatible() throws Exception {
        Path sidecar = root.resolve("future.context");
        ContextIdStore.create(sidecar);
        byte[] bytes = Files.readAllBytes(sidecar);
        ByteBuffer.wrap(bytes).putInt(4, ContextIdStore.VERSION + 1);
        rewriteChecksum(bytes);
        Files.write(sidecar, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextIdStore.read(sidecar));

        assertEquals(StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                failure.getErrorCode());
    }

    @Test
    void zeroIdentityWithValidChecksumIsSemanticCorruption() throws Exception {
        Path sidecar = root.resolve("zero.context");
        ContextIdStore.create(sidecar);
        byte[] bytes = Files.readAllBytes(sidecar);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putLong(8, 0L);
        buffer.putLong(16, 0L);
        rewriteChecksum(bytes);
        Files.write(sidecar, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextIdStore.read(sidecar));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void malformedLengthIsSemanticCorruption() throws Exception {
        Path sidecar = root.resolve("short.context");
        Files.write(sidecar, new byte[]{1, 2, 3});

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextIdStore.read(sidecar));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    private static void rewriteChecksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, ContextIdStore.PAYLOAD_SIZE);
        ByteBuffer.wrap(bytes).putInt(ContextIdStore.PAYLOAD_SIZE, (int) crc.getValue());
    }

    private static boolean isZero(UUID id) {
        return id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L;
    }
}
