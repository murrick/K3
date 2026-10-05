/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Version;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Immutable descriptor sealed together with one physical DUMB2 revision
 * generation.
 *
 * <p>Codec v2 binds the immutable semantic dependency identities of the
 * revision. Only exact target ContextId + RevisionId pairs participate in that
 * digest; physical locators and compatibility-certificate bytes are routing /
 * derived proof and deliberately do not define revision identity.</p>
 *
 * <pre>
 * K3RM | codec-version | storage-format-version
 * ContextId-msb | ContextId-lsb
 * RevisionId | parent-RevisionId
 * semantic-fingerprint-utf8
 * base-count | generation-sha256
 * description-utf8
 * dependency-identity-sha256
 * CRC32(all previous bytes)
 * </pre>
 *
 * <p>Codec v1 remains readable for pre-binding M4 revisions. Every newly sealed
 * manifest is v2.</p>
 *
 * <p>The generation digest covers only canonical {@code *.base} images in
 * lexical filename order. The manifest itself is excluded so the digest is
 * stable before and after sealing.</p>
 */
final class RevisionManifestStore {

    static final String FILE_NAME = "revision.manifest";

    static final int MAGIC = 0x4B33524D; // K3RM
    static final int LEGACY_VERSION = 1;
    static final int VERSION = 2;

    /**
     * Version of the revision-level physical contract. Individual codecs remain
     * independently versioned.
     */
    static final int STORAGE_FORMAT_VERSION = 1;

    private static final int DIGEST_LENGTH = 32;
    private static final int MAX_STRING_BYTES = 64 * 1024;
    private static final int MAX_DESCRIPTION_CODE_POINTS = 512;

    private RevisionManifestStore() {
    }

    static Manifest seal(Path generation,
                         UUID contextId,
                         long revision,
                         long parentRevision)
            throws IOException, StorageLifecycleException {
        return seal(
                generation,
                contextId,
                revision,
                parentRevision,
                ConnectionVector.empty(),
                "");
    }

    static Manifest seal(Path generation,
                         UUID contextId,
                         long revision,
                         long parentRevision,
                         ConnectionVector dependencies,
                         String description)
            throws IOException, StorageLifecycleException {
        if (generation == null
                || contextId == null
                || dependencies == null
                || description == null) {
            throw new NullPointerException();
        }
        if (revision <= RevisionStore.INITIAL_REVISION) {
            throw new IllegalArgumentException(
                    "sealed revision must be positive");
        }
        if (parentRevision < RevisionStore.INITIAL_REVISION
                || parentRevision >= revision) {
            throw new IllegalArgumentException(
                    "invalid parent revision "
                            + parentRevision + " for " + revision);
        }

        String normalizedDescription =
                validateDescription(description);
        GenerationDigest generationDigest =
                digestGeneration(generation);
        Manifest manifest = new Manifest(
                VERSION,
                contextId,
                revision,
                parentRevision,
                STORAGE_FORMAT_VERSION,
                Version.CORE_VERSION_S,
                generationDigest.baseCount,
                generationDigest.digest,
                normalizedDescription,
                dependencyDigest(dependencies));

        byte[] bytes = encode(manifest);
        Files.write(
                path(generation),
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return manifest;
    }

    static Manifest read(Path generation)
            throws IOException, StorageLifecycleException {
        Path path = path(generation);
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 4 * 6 + 8 * 4 + DIGEST_LENGTH) {
            throw corruption(
                    "Invalid DUMB2 revision manifest length "
                            + bytes.length + " at " + path);
        }

        int payloadLength = bytes.length - 4;
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, payloadLength);
        int storedCrc =
                java.nio.ByteBuffer.wrap(
                        bytes, payloadLength, 4).getInt();
        if ((int) crc.getValue() != storedCrc) {
            throw corruption(
                    "DUMB2 revision manifest checksum mismatch at "
                            + path);
        }

        try (DataInputStream input =
                     new DataInputStream(
                             new ByteArrayInputStream(
                                     bytes, 0, payloadLength))) {
            int magic = input.readInt();
            int version = input.readInt();
            if (magic != MAGIC
                    || (version != LEGACY_VERSION
                    && version != VERSION)) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                        "Unsupported DUMB2 revision manifest format at "
                                + path);
            }

            int storageFormatVersion = input.readInt();
            if (storageFormatVersion <= 0) {
                throw corruption(
                        "Invalid DUMB2 storage format version at "
                                + path);
            }

            UUID contextId =
                    new UUID(input.readLong(), input.readLong());
            if (isZero(contextId)) {
                throw corruption(
                        "Invalid zero ContextId in DUMB2 revision manifest at "
                                + path);
            }

            long revision = input.readLong();
            long parentRevision = input.readLong();
            if (revision <= RevisionStore.INITIAL_REVISION
                    || parentRevision < RevisionStore.INITIAL_REVISION
                    || parentRevision >= revision) {
                throw corruption(
                        "Invalid DUMB2 revision lineage at " + path);
            }

            String semanticFingerprint =
                    readString(input, path);
            if (semanticFingerprint.trim().isEmpty()) {
                throw corruption(
                        "Blank DUMB2 semantic fingerprint at " + path);
            }

            int baseCount = input.readInt();
            if (baseCount <= 0) {
                throw corruption(
                        "Invalid DUMB2 revision base count at " + path);
            }

            int digestLength = input.readInt();
            if (digestLength != DIGEST_LENGTH) {
                throw corruption(
                        "Invalid DUMB2 generation digest length "
                                + digestLength + " at " + path);
            }
            byte[] digest = new byte[digestLength];
            input.readFully(digest);

            String description = "";
            byte[] dependencyDigest = null;
            if (version >= VERSION) {
                try {
                    description = validateDescription(
                            readString(input, path));
                } catch (IllegalArgumentException failure) {
                    throw corruption(
                            "Invalid DUMB2 revision description at "
                                    + path,
                            failure);
                }
                int dependencyDigestLength =
                        input.readInt();
                if (dependencyDigestLength
                        != DIGEST_LENGTH) {
                    throw corruption(
                            "Invalid DUMB2 dependency digest length "
                                    + dependencyDigestLength
                                    + " at " + path);
                }
                dependencyDigest =
                        new byte[dependencyDigestLength];
                input.readFully(dependencyDigest);
            }

            if (input.available() != 0) {
                throw corruption(
                        "Trailing bytes in DUMB2 revision manifest at "
                                + path);
            }

            return new Manifest(
                    version,
                    contextId,
                    revision,
                    parentRevision,
                    storageFormatVersion,
                    semanticFingerprint,
                    baseCount,
                    digest,
                    description,
                    dependencyDigest);
        } catch (EOFException failure) {
            throw corruption(
                    "Truncated DUMB2 revision manifest at " + path,
                    failure);
        }
    }

    static Manifest validate(Path generation,
                             UUID expectedContextId,
                             long expectedRevision,
                             long expectedParentRevision)
            throws IOException, StorageLifecycleException {
        Manifest manifest = read(generation);
        Path path = path(generation);

        if (!expectedContextId.equals(manifest.getContextId())) {
            throw corruption(
                    "DUMB2 revision manifest ContextId mismatch at "
                            + path + ": expected="
                            + expectedContextId + " actual="
                            + manifest.getContextId());
        }
        if (manifest.getRevision() != expectedRevision) {
            throw corruption(
                    "DUMB2 revision manifest RevisionId mismatch at "
                            + path + ": expected="
                            + expectedRevision + " actual="
                            + manifest.getRevision());
        }
        if (manifest.getParentRevision()
                != expectedParentRevision) {
            throw corruption(
                    "DUMB2 revision manifest parent mismatch at "
                            + path + ": expected="
                            + expectedParentRevision + " actual="
                            + manifest.getParentRevision());
        }
        if (manifest.getStorageFormatVersion()
                != STORAGE_FORMAT_VERSION) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                    "Unsupported DUMB2 revision storage format at "
                            + path + ": "
                            + manifest.getStorageFormatVersion());
        }
        GenerationDigest actual =
                digestGeneration(generation);
        if (actual.baseCount != manifest.getBaseCount()
                || !Arrays.equals(
                        actual.digest,
                        manifest.getGenerationDigest())) {
            throw corruption(
                    "DUMB2 revision generation digest mismatch at "
                            + path);
        }

        return manifest;
    }

    static void validateDependencyVector(
            Path generation,
            ConnectionVector dependencies)
            throws IOException, StorageLifecycleException {
        Manifest manifest = read(generation);
        validateDependencyVector(
                manifest, dependencies, generation);
    }

    static void validateDependencyVector(
            Manifest manifest,
            ConnectionVector dependencies,
            Path generation)
            throws StorageLifecycleException {
        if (manifest == null
                || dependencies == null
                || generation == null) {
            throw new NullPointerException();
        }
        if (!manifest.hasDependencyDigest()) {
            return;
        }
        byte[] actual =
                dependencyDigest(dependencies);
        if (!Arrays.equals(
                manifest.getDependencyDigest(),
                actual)) {
            throw corruption(
                    "DUMB2 revision dependency identity digest mismatch at "
                            + generation);
        }
    }

    static boolean exists(Path generation) {
        return Files.isRegularFile(
                path(generation),
                LinkOption.NOFOLLOW_LINKS);
    }

    static Path path(Path generation) {
        return generation.resolve(FILE_NAME);
    }

    private static byte[] encode(Manifest manifest)
            throws IOException {
        ByteArrayOutputStream payloadBytes =
                new ByteArrayOutputStream();
        try (DataOutputStream output =
                     new DataOutputStream(payloadBytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(
                    manifest.getStorageFormatVersion());
            output.writeLong(
                    manifest.getContextId()
                            .getMostSignificantBits());
            output.writeLong(
                    manifest.getContextId()
                            .getLeastSignificantBits());
            output.writeLong(manifest.getRevision());
            output.writeLong(
                    manifest.getParentRevision());
            writeString(
                    output,
                    manifest.getSemanticFingerprint());
            output.writeInt(manifest.getBaseCount());
            byte[] digest =
                    manifest.getGenerationDigest();
            output.writeInt(digest.length);
            output.write(digest);
            writeString(
                    output,
                    manifest.getDescription());
            byte[] dependencyDigest =
                    manifest.getDependencyDigest();
            output.writeInt(dependencyDigest.length);
            output.write(dependencyDigest);
        }

        byte[] payload = payloadBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);

        ByteArrayOutputStream result =
                new ByteArrayOutputStream();
        result.write(payload);
        try (DataOutputStream output =
                     new DataOutputStream(result)) {
            output.writeInt((int) crc.getValue());
        }
        return result.toByteArray();
    }

    private static GenerationDigest digestGeneration(
            Path generation)
            throws IOException, StorageLifecycleException {
        if (!Files.isDirectory(generation)) {
            throw corruption(
                    "DUMB2 revision generation is not a directory: "
                            + generation);
        }

        List<Path> bases = new ArrayList<Path>();
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(generation)) {
            for (Path child : stream) {
                String name =
                        child.getFileName().toString();
                if (FILE_NAME.equals(name)) {
                    continue;
                }
                if (!Files.isRegularFile(
                        child, LinkOption.NOFOLLOW_LINKS)
                        || !name.endsWith(".base")) {
                    throw corruption(
                            "Unexpected entry in DUMB2 revision generation "
                                    + child);
                }
                bases.add(child);
            }
        }

        if (bases.isEmpty()) {
            throw corruption(
                    "Published DUMB2 revision contains no schema snapshots at "
                            + generation);
        }

        Collections.sort(
                bases,
                new Comparator<Path>() {
                    @Override
                    public int compare(Path left, Path right) {
                        return left.getFileName().toString()
                                .compareTo(
                                        right.getFileName().toString());
                    }
                });

        MessageDigest digest = sha256();
        for (Path base : bases) {
            byte[] name =
                    base.getFileName().toString()
                            .getBytes(StandardCharsets.UTF_8);
            byte[] data = Files.readAllBytes(base);
            updateInt(digest, name.length);
            digest.update(name);
            updateLong(digest, data.length);
            digest.update(data);
        }

        return new GenerationDigest(
                bases.size(),
                digest.digest());
    }

    static byte[] dependencyDigest(
            ConnectionVector dependencies) {
        if (dependencies == null) {
            throw new NullPointerException("dependencies");
        }

        ArrayList<RevisionRef> refs =
                new ArrayList<RevisionRef>();
        for (ContextConnection connection
                : dependencies.getConnections()) {
            refs.add(connection.getTarget());
        }
        Collections.sort(
                refs,
                new Comparator<RevisionRef>() {
                    @Override
                    public int compare(
                            RevisionRef left,
                            RevisionRef right) {
                        UUID leftId = left.getContextId();
                        UUID rightId = right.getContextId();
                        int most = Long.compare(
                                leftId.getMostSignificantBits(),
                                rightId.getMostSignificantBits());
                        if (most != 0) {
                            return most;
                        }
                        int least = Long.compare(
                                leftId.getLeastSignificantBits(),
                                rightId.getLeastSignificantBits());
                        if (least != 0) {
                            return least;
                        }
                        return Long.compare(
                                left.getRevision(),
                                right.getRevision());
                    }
                });

        MessageDigest digest = sha256();
        updateInt(digest, refs.size());
        for (RevisionRef ref : refs) {
            updateLong(
                    digest,
                    ref.getContextId()
                            .getMostSignificantBits());
            updateLong(
                    digest,
                    ref.getContextId()
                            .getLeastSignificantBits());
            updateLong(digest, ref.getRevision());
        }
        return digest.digest();
    }

    private static String validateDescription(
            String description) {
        if (description == null) {
            throw new NullPointerException("description");
        }
        if (description.indexOf('\n') >= 0
                || description.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "revision description must be one line");
        }
        if (description.codePointCount(
                0, description.length())
                > MAX_DESCRIPTION_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "revision description exceeds "
                            + MAX_DESCRIPTION_CODE_POINTS
                            + " Unicode code points");
        }
        return description;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", failure);
        }
    }

    private static void updateInt(
            MessageDigest digest,
            int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(
            MessageDigest digest,
            long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static void writeString(
            DataOutputStream output,
            String value) throws IOException {
        byte[] bytes =
                value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(
            DataInputStream input,
            Path path)
            throws IOException, StorageLifecycleException {
        int length = input.readInt();
        if (length < 0
                || length > MAX_STRING_BYTES
                || length > input.available()) {
            throw corruption(
                    "Invalid DUMB2 revision manifest string length at "
                            + path);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(
                bytes, StandardCharsets.UTF_8);
    }

    private static boolean isZero(UUID value) {
        return value.getMostSignificantBits() == 0L
                && value.getLeastSignificantBits() == 0L;
    }

    private static StorageLifecycleException corruption(
            String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                message);
    }

    private static StorageLifecycleException corruption(
            String message,
            Throwable cause) {
        StorageLifecycleException failure =
                corruption(message);
        failure.addSuppressed(cause);
        return failure;
    }

    static final class Manifest {

        private final int codecVersion;
        private final UUID contextId;
        private final long revision;
        private final long parentRevision;
        private final int storageFormatVersion;
        private final String semanticFingerprint;
        private final int baseCount;
        private final byte[] generationDigest;
        private final String description;
        private final byte[] dependencyDigest;

        private Manifest(int codecVersion,
                         UUID contextId,
                         long revision,
                         long parentRevision,
                         int storageFormatVersion,
                         String semanticFingerprint,
                         int baseCount,
                         byte[] generationDigest,
                         String description,
                         byte[] dependencyDigest) {
            this.codecVersion = codecVersion;
            this.contextId = contextId;
            this.revision = revision;
            this.parentRevision = parentRevision;
            this.storageFormatVersion =
                    storageFormatVersion;
            this.semanticFingerprint =
                    semanticFingerprint;
            this.baseCount = baseCount;
            this.generationDigest =
                    generationDigest.clone();
            this.description = description;
            this.dependencyDigest =
                    dependencyDigest == null
                            ? null
                            : dependencyDigest.clone();
        }

        int getCodecVersion() {
            return codecVersion;
        }

        UUID getContextId() {
            return contextId;
        }

        long getRevision() {
            return revision;
        }

        long getParentRevision() {
            return parentRevision;
        }

        int getStorageFormatVersion() {
            return storageFormatVersion;
        }

        String getSemanticFingerprint() {
            return semanticFingerprint;
        }

        int getBaseCount() {
            return baseCount;
        }

        byte[] getGenerationDigest() {
            return generationDigest.clone();
        }

        String getDescription() {
            return description;
        }

        boolean hasDependencyDigest() {
            return dependencyDigest != null;
        }

        byte[] getDependencyDigest() {
            return dependencyDigest == null
                    ? null
                    : dependencyDigest.clone();
        }
    }

    private static final class GenerationDigest {

        private final int baseCount;
        private final byte[] digest;

        private GenerationDigest(
                int baseCount,
                byte[] digest) {
            this.baseCount = baseCount;
            this.digest = digest;
        }
    }
}
