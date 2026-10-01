/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Durable directed connection configuration for one DUMB2 Context.
 *
 * <p>This metadata is operational state, not authoritative knowledge and not a
 * Context revision generation. The source ContextId is persisted so moving a
 * sidecar onto another Context is rejected rather than silently re-bound.</p>
 */
final class ConnectionStore {

    static final String CONNECTION_SUFFIX = ".connections";
    private static final int MAGIC = 0x4B33434E; // K3CN
    private static final int VERSION = 1;
    private static final int MAX_CONNECTIONS = 10000;
    private static final int MAX_LOCATOR_BYTES = 1024 * 1024;

    private ConnectionStore() {
    }

    static Path path(Path location) {
        return location.resolveSibling(
                location.getFileName().toString() + CONNECTION_SUFFIX);
    }

    static ConnectionVector read(Path location, UUID sourceContextId)
            throws IOException, StorageLifecycleException {
        Path path = path(location);
        if (!Files.exists(path)) {
            return ConnectionVector.empty();
        }

        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 4 + 4 + 8 + 8 + 4 + 4) {
            throw corruption("Invalid DUMB2 connection metadata length at "
                    + path);
        }

        int payloadLength = bytes.length - 4;
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, payloadLength);
        long expected = ((long) bytes[payloadLength] & 0xffL) << 24
                | ((long) bytes[payloadLength + 1] & 0xffL) << 16
                | ((long) bytes[payloadLength + 2] & 0xffL) << 8
                | ((long) bytes[payloadLength + 3] & 0xffL);
        if (crc.getValue() != expected) {
            throw corruption("DUMB2 connection metadata checksum mismatch at "
                    + path);
        }

        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes, 0, payloadLength));
        try {
            int magic = input.readInt();
            int version = input.readInt();
            if (magic != MAGIC || version != VERSION) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                        "Unsupported DUMB2 connection metadata format at "
                                + path);
            }

            UUID storedSource = new UUID(
                    input.readLong(), input.readLong());
            if (!storedSource.equals(sourceContextId)) {
                throw corruption(
                        "DUMB2 connection metadata ContextId mismatch at "
                                + path);
            }

            int count = input.readInt();
            if (count < 0 || count > MAX_CONNECTIONS) {
                throw corruption(
                        "Invalid DUMB2 connection count " + count
                                + " at " + path);
            }

            ArrayList<ContextConnection> result =
                    new ArrayList<ContextConnection>(count);
            for (int i = 0; i < count; ++i) {
                UUID targetId = new UUID(
                        input.readLong(), input.readLong());
                long revision = input.readLong();
                if (revision < RevisionStore.INITIAL_REVISION) {
                    throw corruption(
                            "Negative pinned Context revision at " + path);
                }
                String locator = readString(input, path);
                result.add(new ContextConnection(
                        Paths.get(locator),
                        new RevisionRef(targetId, revision)));
            }

            if (input.available() != 0) {
                throw corruption(
                        "Trailing bytes in DUMB2 connection metadata at "
                                + path);
            }
            return new ConnectionVector(result);
        } catch (EOFException failure) {
            throw corruption(
                    "Truncated DUMB2 connection metadata at " + path,
                    failure);
        } catch (IllegalArgumentException failure) {
            throw corruption(
                    "Invalid DUMB2 connection metadata at " + path,
                    failure);
        }
    }

    static void write(Path location,
                      UUID sourceContextId,
                      ConnectionVector vector)
            throws IOException {
        if (sourceContextId == null) {
            throw new NullPointerException("sourceContextId");
        }
        if (vector == null) {
            throw new NullPointerException("vector");
        }

        Path path = path(location);
        if (vector.isEmpty()) {
            Files.deleteIfExists(path);
            return;
        }

        ByteArrayOutputStream payloadBytes =
                new ByteArrayOutputStream();
        DataOutputStream payload =
                new DataOutputStream(payloadBytes);
        payload.writeInt(MAGIC);
        payload.writeInt(VERSION);
        payload.writeLong(sourceContextId.getMostSignificantBits());
        payload.writeLong(sourceContextId.getLeastSignificantBits());
        payload.writeInt(vector.size());
        for (ContextConnection connection : vector.getConnections()) {
            RevisionRef target = connection.getTarget();
            payload.writeLong(
                    target.getContextId().getMostSignificantBits());
            payload.writeLong(
                    target.getContextId().getLeastSignificantBits());
            payload.writeLong(target.getRevision());
            writeString(payload,
                    connection.getTargetLocation().toString());
        }
        payload.flush();

        byte[] body = payloadBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(body);

        ByteArrayOutputStream encodedBytes =
                new ByteArrayOutputStream(body.length + 4);
        encodedBytes.write(body);
        DataOutputStream encoded =
                new DataOutputStream(encodedBytes);
        encoded.writeInt((int) crc.getValue());
        encoded.flush();

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(
                path.getFileName().toString()
                        + ".tmp-" + UUID.randomUUID().toString());
        try {
            try (FileChannel channel = FileChannel.open(
                    temp,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(
                        encodedBytes.toByteArray());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temp, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                throw new IOException(
                        "DUMB2 requires atomic same-filesystem connection publication: "
                                + path,
                        failure);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static void delete(Path location) throws IOException {
        Files.deleteIfExists(path(location));
    }

    private static void writeString(
            DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_LOCATOR_BYTES) {
            throw new IOException("DUMB2 Context locator is too large");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(
            DataInputStream input, Path path)
            throws IOException, StorageLifecycleException {
        int length = input.readInt();
        if (length < 0 || length > MAX_LOCATOR_BYTES) {
            throw corruption(
                    "Invalid DUMB2 Context locator length at " + path);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static StorageLifecycleException corruption(
            String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                message);
    }

    private static StorageLifecycleException corruption(
            String message, Throwable cause) {
        StorageLifecycleException result = corruption(message);
        result.addSuppressed(cause);
        return result;
    }
}
