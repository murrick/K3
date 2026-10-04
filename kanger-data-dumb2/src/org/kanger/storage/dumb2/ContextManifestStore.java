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
 * K3CM | version | UUID-msb | UUID-lsb | origin-flag
 * [origin UUID-msb | origin UUID-lsb | origin revision]
 * revision-manifest-baseline
 * type-count
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
    static final int VERSION = 3;
    static final int LEGACY_VERSION = 2;
    static final long LEGACY_REVISION_MANIFEST_BASELINE = Long.MAX_VALUE;
    static final long NEW_CONTEXT_REVISION_MANIFEST_BASELINE = 1L;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_DESCRIPTOR_BYTES = 16 * 1024 * 1024;

    private ContextManifestStore() {
    }

    static Manifest create(Path path) throws IOException {
        return create(
                path,
                null,
                NEW_CONTEXT_REVISION_MANIFEST_BASELINE,
                new TypeRegistry());
    }

    static Manifest createFork(Path path,
                               UUID originContextId,
                               long originRevision,
                               TypeRegistry registry) throws IOException {
        if (originContextId == null || isZero(originContextId)) {
            throw new IllegalArgumentException("origin ContextId must be non-zero");
        }
        if (originRevision < 0L) {
            throw new IllegalArgumentException("origin revision must be non-negative");
        }
        if (registry == null) {
            throw new NullPointerException("registry");
        }
        return create(
                path,
                new Origin(originContextId, originRevision),
                NEW_CONTEXT_REVISION_MANIFEST_BASELINE,
                copyRegistry(registry));
    }

    private static Manifest create(Path path,
                                   Origin origin,
                                   long revisionManifestBaseline,
                                   TypeRegistry registry) throws IOException {
        UUID contextId;
        do {
            contextId = UUID.randomUUID();
        } while (isZero(contextId));

        byte[] bytes = encode(
                contextId,
                origin,
                revisionManifestBaseline,
                registry);
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
        return new Manifest(
                contextId,
                origin,
                revisionManifestBaseline,
                registry);
    }

    static Manifest read(Path path) throws IOException, StorageLifecycleException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 4 + 4 + 8 + 8 + 4 + 4 + 4) {
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
            if (magic != MAGIC
                    || (version != VERSION
                            && version != LEGACY_VERSION)) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                        "Unsupported DUMB2 Context manifest format at " + path);
            }

            UUID contextId = new UUID(readLong(input), readLong(input));
            if (isZero(contextId)) {
                throw corruption("DUMB2 Context identity is zero at " + path);
            }

            int originFlag = readInt(input);
            Origin origin = null;
            if (originFlag == 1) {
                UUID originContextId =
                        new UUID(readLong(input), readLong(input));
                long originRevision = readLong(input);
                if (isZero(originContextId) || originRevision < 0L) {
                    throw corruption("Invalid DUMB2 Context origin at " + path);
                }
                origin = new Origin(originContextId, originRevision);
            } else if (originFlag != 0) {
                throw corruption("Invalid DUMB2 Context origin flag "
                        + originFlag + " at " + path);
            }

            long revisionManifestBaseline =
                    version >= VERSION
                            ? readLong(input)
                            : LEGACY_REVISION_MANIFEST_BASELINE;
            if (revisionManifestBaseline
                    < NEW_CONTEXT_REVISION_MANIFEST_BASELINE) {
                throw corruption(
                        "Invalid DUMB2 revision manifest baseline "
                                + revisionManifestBaseline
                                + " at " + path);
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
            return new Manifest(
                    contextId,
                    origin,
                    revisionManifestBaseline,
                    registry);
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
        validateRegistryExtension(
                path,
                published.getTypeRegistry(),
                registry);

        publishManifest(
                path,
                contextId,
                published.getOrigin(),
                published.getRevisionManifestBaseline(),
                registry);
    }

    /**
     * Declares the first revision that must carry a sealed Revision manifest.
     *
     * <p>Legacy v2 Contexts are upgraded atomically before the first M4
     * publication. Revisions below the recorded baseline remain readable as
     * legacy immutable generations; the baseline itself never moves later.</p>
     */
    static Manifest requireRevisionManifestsFrom(
            Path path,
            UUID contextId,
            long baseline,
            TypeRegistry registry)
            throws IOException, StorageLifecycleException {
        if (baseline < NEW_CONTEXT_REVISION_MANIFEST_BASELINE) {
            throw new IllegalArgumentException(
                    "revision manifest baseline must be positive");
        }

        Manifest published = read(path);
        if (!published.getContextId().equals(contextId)) {
            throw corruption(
                    "DUMB2 Context manifest identity replacement rejected at "
                            + path);
        }
        validateRegistryExtension(
                path,
                published.getTypeRegistry(),
                registry);

        long existing =
                published.getRevisionManifestBaseline();
        long effective =
                existing == LEGACY_REVISION_MANIFEST_BASELINE
                        ? baseline
                        : existing;
        if (effective > baseline) {
            throw new IllegalStateException(
                    "DUMB2 revision manifest baseline cannot move backward: "
                            + existing + " -> " + baseline);
        }

        if (existing == effective) {
            return published;
        }

        publishManifest(
                path,
                contextId,
                published.getOrigin(),
                effective,
                registry);
        return read(path);
    }

    private static byte[] encode(UUID contextId,
                                 Origin origin,
                                 long revisionManifestBaseline,
                                 TypeRegistry registry)
            throws IOException {
        if (contextId == null || isZero(contextId)) {
            throw new IllegalArgumentException("ContextId must be non-zero");
        }
        if (registry == null) {
            throw new NullPointerException("registry");
        }
        if (revisionManifestBaseline
                < NEW_CONTEXT_REVISION_MANIFEST_BASELINE) {
            throw new IllegalArgumentException(
                    "revision manifest baseline must be positive");
        }

        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        writeInt(output, MAGIC);
        writeInt(output, VERSION);
        writeLong(output, contextId.getMostSignificantBits());
        writeLong(output, contextId.getLeastSignificantBits());
        if (origin == null) {
            writeInt(output, 0);
        } else {
            writeInt(output, 1);
            writeLong(output, origin.getContextId().getMostSignificantBits());
            writeLong(output, origin.getContextId().getLeastSignificantBits());
            writeLong(output, origin.getRevision());
        }
        writeLong(output, revisionManifestBaseline);
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

    private static void validateRegistryExtension(
            Path path,
            TypeRegistry published,
            TypeRegistry candidateRegistry)
            throws StorageLifecycleException {
        for (TypeDefinition oldDefinition
                : published.definitions()) {
            TypeDefinition candidate;
            try {
                candidate =
                        candidateRegistry.resolve(
                                oldDefinition.getTypeCode());
            } catch (IllegalArgumentException failure) {
                throw corruption(
                        "DUMB2 Context manifest descriptor removal rejected at "
                                + path,
                        failure);
            }
            if (!oldDefinition.equals(candidate)) {
                throw corruption(
                        "DUMB2 Context manifest descriptor redefinition rejected at "
                                + path);
            }
        }
    }

    private static void publishManifest(
            Path path,
            UUID contextId,
            Origin origin,
            long revisionManifestBaseline,
            TypeRegistry registry)
            throws IOException {
        byte[] bytes = encode(
                contextId,
                origin,
                revisionManifestBaseline,
                registry);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(
                path.getFileName().toString()
                        + ".tmp-"
                        + UUID.randomUUID().toString());
        try {
            try (FileChannel channel = FileChannel.open(
                    temp,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer =
                        java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                throw new IOException(
                        "DUMB2 requires atomic same-filesystem manifest publication: "
                                + path,
                        failure);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static TypeRegistry copyRegistry(TypeRegistry source) {
        TypeRegistry copy = new TypeRegistry();
        for (TypeDefinition definition : source.definitions()) {
            copy.install(definition.getTypeCode(),
                    definition.getTypeName(),
                    definition.getDescriptor());
        }
        return copy;
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
        private final Origin origin;
        private final long revisionManifestBaseline;
        private final TypeRegistry typeRegistry;

        private Manifest(UUID contextId,
                         Origin origin,
                         long revisionManifestBaseline,
                         TypeRegistry typeRegistry) {
            this.contextId = contextId;
            this.origin = origin;
            this.revisionManifestBaseline =
                    revisionManifestBaseline;
            this.typeRegistry = typeRegistry;
        }

        UUID getContextId() {
            return contextId;
        }

        Origin getOrigin() {
            return origin;
        }

        long getRevisionManifestBaseline() {
            return revisionManifestBaseline;
        }

        boolean requiresRevisionManifest(
                long revision) {
            return revision
                    >= revisionManifestBaseline;
        }

        TypeRegistry getTypeRegistry() {
            return typeRegistry;
        }
    }

    static final class Origin {
        private final UUID contextId;
        private final long revision;

        private Origin(UUID contextId, long revision) {
            this.contextId = contextId;
            this.revision = revision;
        }

        UUID getContextId() {
            return contextId;
        }

        long getRevision() {
            return revision;
        }
    }
}
