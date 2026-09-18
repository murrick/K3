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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Persistent revision marker for one mutable DUMB 2.0 Context.
 *
 * <p>The revision is deliberately separate from {@link ContextIdStore}:
 * Context identity is stable for the lifetime of the Context, while this
 * marker advances only when a future storage lifecycle publishes a new durable
 * Context state. The lifecycle wiring is intentionally outside this codec.</p>
 *
 * <p>The binary contract is:</p>
 *
 * <pre>
 * K3RV | version=1 | revision | CRC32(payload)
 *  4        4           8             4
 * </pre>
 *
 * <p>Revision zero identifies the newly created initial state. Revisions are
 * non-negative and monotonically increasing. A replacement is published by an
 * atomic same-directory move so a reader observes either the previous complete
 * marker or the next complete marker.</p>
 */
final class RevisionStore {

    static final int MAGIC = 0x4B335256; // K3RV
    static final int VERSION = 1;
    static final int PAYLOAD_SIZE = 16;
    static final int FILE_SIZE = 20;
    static final long INITIAL_REVISION = 0L;

    private RevisionStore() {
    }

    /**
     * Creates the initial revision marker without replacing an existing one.
     *
     * @param path exact revision sidecar path
     * @return {@value #INITIAL_REVISION}
     * @throws IOException if the marker cannot be created
     */
    static long create(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, encode(INITIAL_REVISION),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return INITIAL_REVISION;
    }

    /**
     * Reads and validates a persisted revision marker.
     *
     * @param path exact revision sidecar path
     * @return persisted non-negative revision
     * @throws IOException if the marker cannot be read
     * @throws StorageLifecycleException when the marker is invalid
     */
    static long read(Path path) throws IOException, StorageLifecycleException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length != FILE_SIZE) {
            throw corruption("Invalid DUMB2 revision marker length " + bytes.length
                    + " at " + path);
        }

        CRC32 crc = new CRC32();
        crc.update(bytes, 0, PAYLOAD_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        long revision = buffer.getLong();
        long expectedCrc = buffer.getInt() & 0xffffffffL;

        if (crc.getValue() != expectedCrc) {
            throw corruption("DUMB2 revision marker checksum mismatch at " + path);
        }
        if (magic != MAGIC || version != VERSION) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                    "Unsupported DUMB2 revision marker format at " + path);
        }
        if (revision < INITIAL_REVISION) {
            throw corruption("DUMB2 revision marker is negative at " + path);
        }
        return revision;
    }

    /**
     * Advances the marker exactly once from the caller-observed revision.
     *
     * <p>This method serializes revision replacement inside the current JVM.
     * Cross-process commit arbitration belongs to the later DUMB 2.0 lifecycle
     * contract; this primitive must not be mistaken for that arbitration.</p>
     *
     * @param path exact revision sidecar path
     * @param expectedRevision revision observed by the caller
     * @return newly persisted revision
     * @throws IOException if the replacement cannot be published atomically
     * @throws StorageLifecycleException when the current marker is invalid
     * @throws IllegalStateException when the expected revision is stale or the
     *         revision space is exhausted
     */
    static synchronized long advance(Path path, long expectedRevision)
            throws IOException, StorageLifecycleException {
        long current = read(path);
        if (current != expectedRevision) {
            throw new IllegalStateException(
                    "DUMB2 revision changed: expected=" + expectedRevision
                            + " actual=" + current);
        }
        if (current == Long.MAX_VALUE) {
            throw new IllegalStateException("DUMB2 revision space exhausted");
        }

        long next = current + 1L;
        Path temporary = temporarySibling(path);
        try {
            Files.write(temporary, encode(next),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return next;
    }

    private static Path temporarySibling(Path path) {
        String name = path.getFileName().toString();
        return path.resolveSibling(name + ".next-" + UUID.randomUUID().toString());
    }

    private static byte[] encode(long revision) {
        ByteBuffer buffer = ByteBuffer.allocate(FILE_SIZE);
        buffer.putInt(MAGIC);
        buffer.putInt(VERSION);
        buffer.putLong(revision);

        CRC32 crc = new CRC32();
        crc.update(buffer.array(), 0, PAYLOAD_SIZE);
        buffer.putInt((int) crc.getValue());
        return buffer.array();
    }

    private static StorageLifecycleException corruption(String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                message);
    }
}
