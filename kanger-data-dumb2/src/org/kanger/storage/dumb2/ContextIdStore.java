/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Persistent identity codec for a DUMB 2.0 Context.
 *
 * <p>The sidecar is intentionally independent of replaceable physical
 * generation files. Its binary contract is:</p>
 *
 * <pre>
 * K3CT | version=1 | UUID-msb | UUID-lsb | CRC32(payload)
 *  4        4           8          8             4
 * </pre>
 *
 * <p>The checksum covers the first 24 bytes. Unsupported magic or version is
 * classified as an incompatible storage format; malformed, checksum-invalid
 * or zero identity is semantic corruption.</p>
 */
final class ContextIdStore {

    static final int MAGIC = 0x4B334354; // K3CT
    static final int VERSION = 1;
    static final int PAYLOAD_SIZE = 24;
    static final int FILE_SIZE = 28;

    private ContextIdStore() {
    }

    /**
     * Creates a new Context identity sidecar without replacing an existing one.
     *
     * @param path exact sidecar path
     * @return newly persisted non-zero UUID
     * @throws IOException if the sidecar cannot be created atomically
     */
    static UUID create(Path path) throws IOException {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (isZero(id));

        byte[] bytes = encode(id);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return id;
    }

    /**
     * Reads and validates a persisted Context identity sidecar.
     *
     * @param path exact sidecar path
     * @return persisted UUID
     * @throws IOException if the sidecar cannot be read
     * @throws StorageLifecycleException when the persisted identity is invalid
     */
    static UUID read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length != FILE_SIZE) {
            throw corruption("Invalid DUMB2 context identity length " + bytes.length
                    + " at " + path);
        }

        CRC32 crc = new CRC32();
        crc.update(bytes, 0, PAYLOAD_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        long mostSignificantBits = buffer.getLong();
        long leastSignificantBits = buffer.getLong();
        long expectedCrc = buffer.getInt() & 0xffffffffL;

        if (crc.getValue() != expectedCrc) {
            throw corruption("DUMB2 context identity checksum mismatch at " + path);
        }
        if (magic != MAGIC || version != VERSION) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                    "Unsupported DUMB2 context identity format at " + path);
        }

        UUID id = new UUID(mostSignificantBits, leastSignificantBits);
        if (isZero(id)) {
            throw corruption("DUMB2 context identity is zero at " + path);
        }
        return id;
    }

    private static byte[] encode(UUID id) {
        ByteBuffer buffer = ByteBuffer.allocate(FILE_SIZE);
        buffer.putInt(MAGIC);
        buffer.putInt(VERSION);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());

        CRC32 crc = new CRC32();
        crc.update(buffer.array(), 0, PAYLOAD_SIZE);
        buffer.putInt((int) crc.getValue());
        return buffer.array();
    }

    private static boolean isZero(UUID id) {
        return id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L;
    }

    private static StorageLifecycleException corruption(String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                message);
    }
}
