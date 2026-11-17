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
        return create(path, INITIAL_REVISION);
    }

    static long create(Path path, long revision) throws IOException {
        if (revision < INITIAL_REVISION) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = temporarySibling(path);
        boolean temporaryCreated = false, linkAttempted = false, visible = false;
        Throwable primaryFailure = null;
        try {
            // Publish only complete, forced bytes. A hard link provides atomic no-replace
            // installation; ATOMIC_MOVE alone does not specify no-replace semantics.
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                temporaryCreated = true;
                ByteBuffer bytes = ByteBuffer.wrap(encode(revision));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            linkAttempted = true;
            Files.createLink(path, temporary);
            visible = true;
            Files.delete(temporary);
            temporaryCreated = false;
            confirmDurable(path);
            return revision;
        } catch (IOException | RuntimeException failure) {
            IOException result = failure instanceof IOException ? (IOException) failure
                    : new IOException("Initial marker installation is unavailable at " + path, failure);
            if (visible || (linkAttempted && !(failure instanceof java.nio.file.FileAlreadyExistsException)
                    && !Files.notExists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)))
                result = new UnconfirmedRevisionException(-1L, revision, result);
            primaryFailure = result;
            // A consumer may have already stabilized and pinned this visible revision.
            // Never retract the final name, even though our own acknowledgement failed.
            throw result;
        } catch (Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (temporaryCreated) {
                try { discardInitialTemporary(temporary); }
                catch (IOException | RuntimeException | Error cleanupFailure) {
                    if (primaryFailure == null) throw cleanupFailure;
                    if (cleanupFailure != primaryFailure) primaryFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    private static void discardInitialTemporary(Path path) throws IOException {
        Files.deleteIfExists(path);
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
     * <p>An I/O error from the move is reconciled only when the ordinary
     * marker reader confirms the exact next revision and persistence barriers complete. If confirmation also
     * fails, the exception does not prove that the marker stayed unchanged.
     * The caller owns exclusive Context publication and subsequent recovery.</p>
     *
     * @throws IOException if replacement fails or its outcome cannot be confirmed
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
        boolean published = false;
        Throwable primaryFailure = null;
        try {
            Files.write(temporary, encode(next),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailure) {
                // The owner holds the Context lock; verify an ambiguous move result.
                if (!confirmsPublication(path, current, next, moveFailure)) throw moveFailure;
            }
            // A visible next marker is not yet a durable acknowledgement.
            try {
                confirmDurable(path);
            } catch (IOException barrierFailure) {
                throw new UnconfirmedRevisionException(current, next, barrierFailure);
            }
            published = true;
            return next;
        } catch (IOException | RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            // A successful move already consumed the temporary pathname.
            if (!published) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException | RuntimeException | Error cleanupFailure) {
                    if (primaryFailure == null) throw cleanupFailure;
                    if (cleanupFailure != primaryFailure) primaryFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    /** Recovery must repeat barriers even when CURRENT already reads as the next revision. */
    static void confirmDurable(Path path) throws IOException {
        DurableFiles.forceFile(path);
        DurableFiles.forceParents(path);
    }

    private static boolean confirmsPublication(Path path, long previous, long next, IOException moveFailure) throws UnconfirmedRevisionException {
        long observed;
        try { observed = read(path); }
        catch (IOException | StorageLifecycleException verificationFailure) {
            if (verificationFailure != moveFailure) moveFailure.addSuppressed(verificationFailure);
            throw new UnconfirmedRevisionException(previous,next,moveFailure);
        }
        if(observed==next)return true;
        if(observed==previous)return false;
        moveFailure.addSuppressed(new IOException("Marker is outside the attempted revision transition"));
        throw new UnconfirmedRevisionException(previous,next,moveFailure);
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
