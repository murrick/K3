/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.DescriptorBinaryCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Self-describing DUMB 2.0 Context manifest.
 *
 * <p>The manifest owns stable ContextId plus the Context-local immutable type
 * registry. Numeric typeCode values have meaning only through this file.</p>
 *
 * <pre>
 * K3CM | version | UUID-msb | UUID-lsb | type-count
 * repeated:
 *   typeCode | typeName-utf8 | descriptor-bytes
 * CRC32(all previous bytes)
 * </pre>
 *
 * <p>All integer framing in this manifest is little-endian and explicit.
 * Descriptor payloads are independently versioned by DescriptorBinaryCodec.</p>
 */
final class ContextManifestStore {

    static final int MAGIC = 0x4B33434D; // K3CM
    static final int VERSION = 1;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_DESCRIPTOR_BYTES = 16 * 1024 * 1024;

    private ContextManifestStore() {
    }

    static Manifest create(Path path) throws IOException {
        UUID contextId;
        do {
            contextId = UUID.randomUUID();
        } while (isZero(contextId));

        TypeRegistry registry = new TypeRegistry();
        byte[] bytes = encode(contextId, registry);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        return new Manifest(contextId, registry);
    }

    static Manifest read(Path path) throws IOException, StorageLifecycleException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 4 + 4 + 8 + 8 + 4 + 4) {
            throw corruption("Invalid DUMB2 Context manifest length "
                    + bytes.length + " at " + path);
        }

        int payloadLength = bytes.length - 4;
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, payloadLength);
        long expectedCrc = ((long) bytes[payloadLength] & 0xffL)
                | (((long) bytes[payloadLength + 1] & 0xffL) << 8)
                | (((long) bytes[payloadLength + 2] & 0xffL) << 16)
                | (((long) bytes[payloadLength + 3] & 0xffL) << 24);
        if (crc.getValue() != expectedCrc) {
            throw corruption("DUMB2 Context manifest checksum mismatch at " + path);
        }

        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes, 0, payloadLength));
        try {
            int magic = readInt(input);
            int version = readInt(input);
            if (magic != MAGIC || version != VERSION) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                        "Unsupported DUMB2 Context manifest format at " + path);
            }

            UUID contextId = new UUID(readLong(input), readLong(input));
            if (isZero(contextId)) {
                throw corruption("DUMB2 Context identity is zero at " + path);
            }

            int count = readInt(input);
            if (count < 0) {
                throw corruption("Negative DUMB2 Context type count at " + path);
            }

            TypeRegistry registry = new TypeRegistry();
            for (int i = 0; i < count; ++i) {
                int typeCode = readInt(input);
                String typeName = readString(input, MAX_STRING_BYTES, "type name");
                byte[] descriptorBytes = readBytes(
                        input, MAX_DESCRIPTOR_BYTES, "descriptor");
                Descriptor descriptor;
                try {
                    descriptor = DescriptorBinaryCodec.decode(descriptorBytes);
                } catch (IOException failure) {
                    StorageLifecycleException incompatible =
                            new StorageLifecycleException(
                                    StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                                    "Unsupported DUMB2 descriptor in Context manifest at "
                                            + path);
                    incompatible.addSuppressed(failure);
                    throw incompatible;
                }
                try {
                    registry.install(typeCode, typeName, descriptor);
                } catch (IllegalArgumentException | IllegalStateException failure) {
                    throw corruption("Invalid DUMB2 type registry at " + path, failure);
                }
            }
            if (input.available() != 0) {
                throw corruption("Trailing bytes in DUMB2 Context manifest at " + path);
            }
            return new Manifest(contextId, registry);
        } catch (EOFException failure) {
            throw corruption("Truncated DUMB2 Context manifest at " + path, failure);
        }
    }

    static void publish(Path path, UUID contextId, TypeRegistry registry)
            throws IOException, StorageLifecycleException {
        Manifest published = read(path);
        if (!published.getContextId().equals(contextId)) {
            throw corruption("DUMB2 Context manifest identity replacement rejected at "
                    + path);
        }
        for (TypeDefinition oldDefinition
                : published.getTypeRegistry().definitions()) {
            TypeDefinition candidate;
            try {
                candidate = registry.resolve(oldDefinition.getTypeCode());
            } catch (IllegalArgumentException failure) {
                throw corruption("DUMB2 Context manifest descriptor removal rejected at "
                        + path, failure);
            }
            if (!oldDefinition.equals(candidate)) {
                throw corruption("DUMB2 Context manifest descriptor redefinition rejected at "
                        + path);
            }
        }

        byte[] bytes = encode(contextId, registry);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(path.getFileName().toString()
                + ".tmp-" + UUID.randomUUID().toString());
        try {
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
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
                        "DUMB2 requires atomic same-filesystem manifest publication: "
                                + path, failure);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] encode(UUID contextId, TypeRegistry registry)
            throws IOException {
        if (contextId == null || isZero(contextId)) {
            throw new IllegalArgumentException("ContextId must be non-zero");
        }
        if (registry == null) {
            throw new NullPointerException("registry");
        }

        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        writeInt(output, MAGIC);
        writeInt(output, VERSION);
        writeLong(output, contextId.getMostSignificantBits());
        writeLong(output, contextId.getLeastSignificantBits());
        writeInt(output, registry.size());

        for (TypeDefinition definition : registry.definitions()) {
            writeInt(output, definition.getTypeCode());
            writeString(output, definition.getTypeName());
            writeBytes(output, DescriptorBinaryCodec.encode(
                    definition.getDescriptor()));
        }
        output.flush();

        byte[] payload = payloadBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);

        ByteArrayOutputStream resultBytes =
                new ByteArrayOutputStream(payload.length + 4);
        resultBytes.write(payload);
        DataOutputStream result = new DataOutputStream(resultBytes);
        writeInt(result, (int) crc.getValue());
        result.flush();
        return resultBytes.toByteArray();
    }

    private static void writeString(DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeBytes(output, bytes);
    }

    private static String readString(DataInputStream input, int max, String label)
            throws IOException, StorageLifecycleException {
        return new String(readBytes(input, max, label), StandardCharsets.UTF_8);
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes)
            throws IOException {
        writeInt(output, bytes.length);
        output.write(bytes);
    }

    private static byte[] readBytes(DataInputStream input, int max, String label)
            throws IOException, StorageLifecycleException {
        int length = readInt(input);
        if (length < 0 || length > max || length > input.available()) {
            throw corruption("Invalid DUMB2 Context " + label
                    + " length " + length);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    private static void writeInt(DataOutputStream output, int value)
            throws IOException {
        output.writeByte(value);
        output.writeByte(value >>> 8);
        output.writeByte(value >>> 16);
        output.writeByte(value >>> 24);
    }

    private static int readInt(DataInputStream input) throws IOException {
        return input.readUnsignedByte()
                | (input.readUnsignedByte() << 8)
                | (input.readUnsignedByte() << 16)
                | (input.readUnsignedByte() << 24);
    }

    private static void writeLong(DataOutputStream output, long value)
            throws IOException {
        for (int i = 0; i < 8; ++i) {
            output.writeByte((int) (value >>> (8 * i)));
        }
    }

    private static long readLong(DataInputStream input) throws IOException {
        long value = 0L;
        for (int i = 0; i < 8; ++i) {
            value |= ((long) input.readUnsignedByte()) << (8 * i);
        }
        return value;
    }

    private static boolean isZero(UUID id) {
        return id.getMostSignificantBits() == 0L
                && id.getLeastSignificantBits() == 0L;
    }

    private static StorageLifecycleException corruption(String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION, message);
    }

    private static StorageLifecycleException corruption(
            String message, Throwable cause) {
        StorageLifecycleException failure = corruption(message);
        failure.addSuppressed(cause);
        return failure;
    }

    static final class Manifest {
        private final UUID contextId;
        private final TypeRegistry typeRegistry;

        private Manifest(UUID contextId, TypeRegistry typeRegistry) {
            this.contextId = contextId;
            this.typeRegistry = typeRegistry;
        }

        UUID getContextId() {
            return contextId;
        }

        TypeRegistry getTypeRegistry() {
            return typeRegistry;
        }
    }
}
