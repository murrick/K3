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
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Durable directed connection configuration for one DUMB2 Context.
 *
 * <p>Version 3 stores exact dependency vectors by source revision. Published
 * entries are revision-bound: ordinary runtime topology changes must never
 * rewrite them in place. A writer may stage R+1 before CURRENT advances, while
 * exact operation snapshots keep selecting the vector belonging to their
 * source RevisionId.</p>
 *
 * <p>Version 2 is read for compatibility. A V2 vector contains certificates
 * for exactly one source revision, which is inferred from those certificates.</p>
 */
final class ConnectionStore {

    static final String CONNECTION_SUFFIX = ".connections";
    private static final int MAGIC = 0x4B33434E; // K3CN
    private static final int VERSION = 4;
    private static final int LEGACY_VERSION = 2;
    private static final int MAX_CONNECTIONS = 10000;
    /*
     * Smallest possible V3 revision entry: source RevisionId (long) plus an
     * empty ConnectionVector count (int). Decoder limits are derived from the
     * actual sidecar byte length rather than imposing a lifetime revision cap.
     */
    private static final int MIN_REVISION_ENTRY_BYTES =
            Long.BYTES + Integer.BYTES;
    private static final int MAX_STRING_BYTES = 1024 * 1024;

    private ConnectionStore() {
    }

    static Path path(Path location) {
        return location.resolveSibling(
                location.getFileName().toString() + CONNECTION_SUFFIX);
    }

    /**
     * Reads the vector belonging to one exact source Context revision.
     */
    static ConnectionVector read(Path location, RevisionRef source)
            throws IOException, StorageLifecycleException {
        if (source == null) {
            throw new NullPointerException("source");
        }
        State state = readState(location, source.getContextId());
        if (state == null) {
            return ConnectionVector.empty();
        }
        ConnectionVector vector =
                state.byRevision.get(Long.valueOf(source.getRevision()));
        if (vector == null) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                    "No DUMB2 connection vector is qualified for "
                            + source + " at " + path(location));
        }
        return vector;
    }

    /**
     * Legacy/internal helper for callers that do not yet carry source revision.
     * Runtime operation code must prefer {@link #read(Path, RevisionRef)}.
     *
     * <p>For a V3 transition sidecar this returns the highest staged source
     * revision and is therefore not a publication selector.</p>
     */
    static ConnectionVector read(Path location, UUID sourceContextId)
            throws IOException, StorageLifecycleException {
        State state = readState(location, sourceContextId);
        if (state == null || state.byRevision.isEmpty()) {
            return ConnectionVector.empty();
        }
        return state.byRevision.lastEntry().getValue();
    }

    /**
     * Legacy package-level helper used by pre-revision-bound storage fixtures.
     * Runtime/operator topology mutation must not call this method.
     */
    static void write(Path location,
                      RevisionRef source,
                      ConnectionVector vector)
            throws IOException {
        if (source == null || vector == null) {
            throw new NullPointerException();
        }
        if (vector.isEmpty()) {
            Files.deleteIfExists(path(location));
            return;
        }
        validateVector(source, vector);
        State state = new State(source.getContextId());
        state.byRevision.put(
                Long.valueOf(source.getRevision()), vector);
        writeState(location, state);
    }

    /**
     * Compatibility helper for pre-M3.8 callers/tests. The exact source
     * revision is inferred from every certificate in the non-empty vector.
     */
    static void write(Path location,
                      UUID sourceContextId,
                      ConnectionVector vector)
            throws IOException {
        if (sourceContextId == null || vector == null) {
            throw new NullPointerException();
        }
        if (vector.isEmpty()) {
            Files.deleteIfExists(path(location));
            return;
        }
        RevisionRef source = inferSource(vector, sourceContextId);
        write(location, source, vector);
    }

    /**
     * Stages the exact dependency vector of a brand-new Context revision before
     * that revision becomes visible through its CURRENT marker.
     */
    static void stageInitialRevision(
            Path location,
            RevisionRef source,
            ConnectionVector vector)
            throws IOException {
        if (source == null || vector == null) {
            throw new NullPointerException();
        }
        validateVector(source, vector);
        if (vector.isEmpty()) {
            Files.deleteIfExists(path(location));
            return;
        }
        State state = new State(source.getContextId());
        state.byRevision.put(
                Long.valueOf(source.getRevision()),
                vector);
        writeState(location, state);
    }

    /**
     * Publishes a crash-safe transition sidecar before Context R+1 becomes
     * visible. Both vectors remain selectable by exact source revision.
     */
    static void writeTransition(
            Path location,
            RevisionRef currentSource,
            ConnectionVector currentVector,
            RevisionRef candidateSource,
            ConnectionVector candidateVector)
            throws IOException, StorageLifecycleException {
        if (currentSource == null
                || currentVector == null
                || candidateSource == null
                || candidateVector == null) {
            throw new NullPointerException();
        }
        if (!currentSource.getContextId().equals(
                candidateSource.getContextId())) {
            throw new IllegalArgumentException(
                    "connection transition requires one source Context");
        }
        if (candidateSource.getRevision()
                != currentSource.getRevision() + 1L) {
            throw new IllegalArgumentException(
                    "connection transition must advance exactly one revision: "
                            + currentSource + " -> " + candidateSource);
        }

        validateVector(currentSource, currentVector);
        validateVector(candidateSource, candidateVector);

        State state = readState(
                location, currentSource.getContextId());
        if (state == null) {
            state = new State(currentSource.getContextId());
        }

        Long currentRevision =
                Long.valueOf(currentSource.getRevision());
        ConnectionVector storedCurrent =
                state.byRevision.get(currentRevision);
        if (storedCurrent != null
                && !storedCurrent.equals(currentVector)) {
            throw new IllegalStateException(
                    "published connection vector changed for "
                            + currentSource);
        }

        /*
         * Preserve every historical published vector. R+1 may already be
         * present only as an unpublished crash/retry candidate, so replacing
         * that one exact entry is safe while CURRENT still names R.
         */
        state.byRevision.put(currentRevision, currentVector);
        state.byRevision.put(
                Long.valueOf(candidateSource.getRevision()),
                candidateVector);
        writeState(location, state);
    }

    static void delete(Path location) throws IOException {
        Files.deleteIfExists(path(location));
    }

    private static State readState(
            Path location,
            UUID sourceContextId)
            throws IOException, StorageLifecycleException {
        if (sourceContextId == null) {
            throw new NullPointerException("sourceContextId");
        }
        Path path = path(location);
        if (!Files.exists(path)) {
            return null;
        }

        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 4 + 4 + 8 + 8 + 4 + 4) {
            throw corruption(
                    "Invalid DUMB2 connection metadata length at "
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
            throw corruption(
                    "DUMB2 connection metadata checksum mismatch at "
                            + path);
        }

        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes, 0, payloadLength));
        try {
            int magic = input.readInt();
            int version = input.readInt();
            if (magic != MAGIC
                    || (version != VERSION
                    && version != 3 && version != LEGACY_VERSION)) {
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

            State state = new State(storedSource);
            if (version == LEGACY_VERSION) {
                ConnectionVector vector =
                        readVector(input, path, version);
                RevisionRef source =
                        inferSource(vector, storedSource);
                state.byRevision.put(
                        Long.valueOf(source.getRevision()), vector);
            } else {
                int revisionCount = input.readInt();
                int maximumRepresentable =
                        input.available()
                                / MIN_REVISION_ENTRY_BYTES;
                if (revisionCount < 1
                        || revisionCount > maximumRepresentable) {
                    throw corruption(
                            "Invalid DUMB2 connection revision count "
                                    + revisionCount + " for "
                                    + input.available()
                                    + " remaining bytes at " + path);
                }
                for (int i = 0; i < revisionCount; ++i) {
                    long sourceRevision = input.readLong();
                    if (sourceRevision < RevisionStore.INITIAL_REVISION) {
                        throw corruption(
                                "Negative DUMB2 connection source revision at "
                                        + path);
                    }
                    ConnectionVector vector =
                            readVector(input, path, version);
                    RevisionRef source =
                            new RevisionRef(
                                    storedSource, sourceRevision);
                    validateVector(source, vector);
                    if (state.byRevision.put(
                            Long.valueOf(sourceRevision),
                            vector) != null) {
                        throw corruption(
                                "Duplicate DUMB2 connection source revision "
                                        + sourceRevision + " at " + path);
                    }
                }
            }

            if (input.available() != 0) {
                throw corruption(
                        "Trailing bytes in DUMB2 connection metadata at "
                                + path);
            }
            return state;
        } catch (EOFException failure) {
            throw corruption(
                    "Truncated DUMB2 connection metadata at "
                            + path, failure);
        } catch (IllegalArgumentException failure) {
            throw corruption(
                    "Invalid DUMB2 connection metadata at "
                            + path, failure);
        }
    }

    private static ConnectionVector readVector(
            DataInputStream input,
            Path path, int version)
            throws IOException, StorageLifecycleException {
        int count = input.readInt();
        if (count < 0 || count > MAX_CONNECTIONS) {
            throw corruption(
                    "Invalid DUMB2 connection count "
                            + count + " at " + path);
        }

        ArrayList<ContextConnection> result =
                new ArrayList<ContextConnection>(count);
        for (int i = 0; i < count; ++i) {
            RevisionRef target =
                    readRevisionRef(input, path);
            String locator =
                    readString(input, path);
            RevisionRef left =
                    readRevisionRef(input, path);
            RevisionRef right =
                    readRevisionRef(input, path);
            String semanticVersion =
                    readString(input, path);
            CompatibilityCertificate certificate =
                    new CompatibilityCertificate(
                            left, right, semanticVersion);
            java.util.List<String> initialization = new java.util.ArrayList<String>();
            if (version >= 4) {
                int commands = input.readInt();
                if (commands < 0 || commands > 4096) throw corruption("Invalid initialization command count at " + path);
                for (int j = 0; j < commands; j++) initialization.add(readString(input, path));
            }
            result.add(new ContextConnection(
                    Paths.get(locator),
                    target,
                    certificate, initialization));
        }
        return new ConnectionVector(result);
    }

    private static void writeState(
            Path location,
            State state) throws IOException {
        Path path = path(location);

        ByteArrayOutputStream payloadBytes =
                new ByteArrayOutputStream();
        DataOutputStream payload =
                new DataOutputStream(payloadBytes);
        payload.writeInt(MAGIC);
        payload.writeInt(VERSION);
        payload.writeLong(
                state.sourceContextId.getMostSignificantBits());
        payload.writeLong(
                state.sourceContextId.getLeastSignificantBits());
        payload.writeInt(state.byRevision.size());

        for (Map.Entry<Long, ConnectionVector> entry
                : state.byRevision.entrySet()) {
            payload.writeLong(entry.getKey().longValue());
            writeVector(payload, entry.getValue());
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
                java.nio.ByteBuffer buffer =
                        java.nio.ByteBuffer.wrap(
                                encodedBytes.toByteArray());
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
                        "DUMB2 requires atomic same-filesystem connection publication: "
                                + path,
                        failure);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void writeVector(
            DataOutputStream output,
            ConnectionVector vector) throws IOException {
        output.writeInt(vector.size());
        for (ContextConnection connection
                : vector.getConnections()) {
            writeRevisionRef(
                    output, connection.getTarget());
            writeString(
                    output,
                    connection.getTargetLocation().toString());
            CompatibilityCertificate certificate =
                    connection.getCertificate();
            writeRevisionRef(
                    output, certificate.getLeft());
            writeRevisionRef(
                    output, certificate.getRight());
            writeString(
                    output, certificate.getSemanticVersion());
            output.writeInt(connection.getInitialization().size());
            for (String command : connection.getInitialization()) writeString(output, command);
        }
    }

    private static RevisionRef inferSource(
            ConnectionVector vector,
            UUID sourceContextId) {
        RevisionRef source = null;
        for (ContextConnection connection
                : vector.getConnections()) {
            CompatibilityCertificate certificate =
                    connection.getCertificate();
            RevisionRef candidate =
                    sourceSide(
                            certificate,
                            sourceContextId,
                            connection.getTarget());
            if (source == null) {
                source = candidate;
            } else if (!source.equals(candidate)) {
                throw new IllegalArgumentException(
                        "connection vector mixes source revisions: "
                                + source + " / " + candidate);
            }
        }
        if (source == null) {
            throw new IllegalArgumentException(
                    "cannot infer source revision from empty connection vector");
        }
        return source;
    }

    private static RevisionRef sourceSide(
            CompatibilityCertificate certificate,
            UUID sourceContextId,
            RevisionRef target) {
        RevisionRef left = certificate.getLeft();
        RevisionRef right = certificate.getRight();
        RevisionRef source;
        if (left.getContextId().equals(sourceContextId)) {
            source = left;
        } else if (right.getContextId().equals(sourceContextId)) {
            source = right;
        } else {
            throw new IllegalArgumentException(
                    "compatibility certificate does not include source Context "
                            + sourceContextId);
        }

        RevisionRef other =
                source.equals(left) ? right : left;
        if (!other.equals(target)) {
            throw new IllegalArgumentException(
                    "compatibility certificate target differs from connection target: "
                            + target + " / " + other);
        }
        return source;
    }

    private static void validateVector(
            RevisionRef source,
            ConnectionVector vector) {
        for (ContextConnection connection
                : vector.getConnections()) {
            RevisionRef actual =
                    sourceSide(
                            connection.getCertificate(),
                            source.getContextId(),
                            connection.getTarget());
            if (!source.equals(actual)) {
                throw new IllegalArgumentException(
                        "connection certificate source differs from vector source: "
                                + source + " / " + actual);
            }
        }
    }

    private static void writeRevisionRef(
            DataOutputStream output,
            RevisionRef ref) throws IOException {
        output.writeLong(
                ref.getContextId().getMostSignificantBits());
        output.writeLong(
                ref.getContextId().getLeastSignificantBits());
        output.writeLong(ref.getRevision());
    }

    private static RevisionRef readRevisionRef(
            DataInputStream input,
            Path path)
            throws IOException, StorageLifecycleException {
        UUID contextId = new UUID(
                input.readLong(), input.readLong());
        long revision = input.readLong();
        if (revision < RevisionStore.INITIAL_REVISION) {
            throw corruption(
                    "Negative pinned Context revision at "
                            + path);
        }
        return new RevisionRef(contextId, revision);
    }

    private static void writeString(
            DataOutputStream output,
            String value) throws IOException {
        byte[] bytes =
                value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IOException(
                    "DUMB2 connection string is too large");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(
            DataInputStream input,
            Path path)
            throws IOException, StorageLifecycleException {
        int length = input.readInt();
        if (length < 0
                || length > MAX_STRING_BYTES) {
            throw corruption(
                    "Invalid DUMB2 connection string length at "
                            + path);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(
                bytes, StandardCharsets.UTF_8);
    }

    private static StorageLifecycleException corruption(
            String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                message);
    }

    private static StorageLifecycleException corruption(
            String message, Throwable cause) {
        StorageLifecycleException result =
                corruption(message);
        result.addSuppressed(cause);
        return result;
    }

    private static final class State {

        private final UUID sourceContextId;
        private final TreeMap<Long, ConnectionVector> byRevision =
                new TreeMap<Long, ConnectionVector>();

        private State(UUID sourceContextId) {
            this.sourceContextId = sourceContextId;
        }
    }
}
