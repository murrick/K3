/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * DUMB 2.0 Context lifecycle and storage-wide publication owner.
 *
 * <p>A Context is identified by one stable ContextId and one currently
 * published durable revision. Schema bases are acquired by stable string
 * descriptor and remain Context-local physical namespaces.</p>
 *
 * <p>Mutations are staged in {@link ContextBase}. A storage-wide flush writes
 * a complete immutable next revision generation first and advances the
 * revision marker only afterwards. Therefore a visible revision can never
 * point at an older or partially written physical generation.</p>
 *
 * <p>The supplied {@code location} is a locator, not Context identity.
 * Metadata sidecars are derived as {@code <location>.context} and
 * {@code <location>.revision}. Physical generations live under
 * {@code <location>.dumb2}. Moving or renaming the complete Context preserves
 * its persisted ContextId and revision.</p>
 */
final class ContextStore implements AutoCloseable {

    static final String CONTEXT_SUFFIX = ".context";
    static final String REVISION_SUFFIX = ".revision";
    static final String STATE_SUFFIX = ".dumb2";

    private final Path location;
    private final UUID contextId;
    private final ContextLock contextLock;
    private final TypeRegistry typeRegistry;
    private final Map<String, ContextBase> bases =
            new LinkedHashMap<String, ContextBase>();

    private long revision;
    private boolean closed;

    private ContextStore(Path location,
                         UUID contextId,
                         long revision,
                         ContextLock contextLock,
                         TypeRegistry typeRegistry) {
        this.location = location;
        this.contextId = contextId;
        this.revision = revision;
        this.contextLock = contextLock;
        this.typeRegistry = typeRegistry;
    }

    /**
     * Creates a new empty Context and acquires its mutable lifecycle lock.
     *
     * <p>If revision creation fails after a fresh identity has been created,
     * that fresh identity is removed again. Existing metadata is never deleted
     * or repaired implicitly.</p>
     */
    static ContextStore create(Path location) throws IOException, StorageLifecycleException {
        Path normalized = requireLocation(location);
        Path contextPath = contextPath(normalized);
        Path revisionPath = revisionPath(normalized);

        ContextManifestStore.Manifest manifest = ContextManifestStore.create(contextPath);
        UUID contextId = manifest.getContextId();
        long revision;
        try {
            revision = RevisionStore.create(revisionPath);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(contextPath);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }

        ContextLock lock = null;
        try {
            lock = acquireLock(normalized);
            return new ContextStore(normalized, contextId, revision, lock, manifest.getTypeRegistry());
        } catch (IOException | StorageLifecycleException
                 | RuntimeException | Error failure) {
            closeQuietly(lock, failure);
            throw failure;
        }
    }

    /**
     * Reopens one complete Context metadata pair and acquires its lifecycle
     * lock. Revision zero denotes the initial empty Context and needs no
     * physical generation directory.
     */
    static ContextStore open(Path location)
            throws IOException, StorageLifecycleException {
        Path normalized = requireLocation(location);
        Path contextPath = contextPath(normalized);
        Path revisionPath = revisionPath(normalized);

        boolean hasContext = Files.exists(contextPath);
        boolean hasRevision = Files.exists(revisionPath);
        if (!hasContext && !hasRevision) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                    "DUMB2 Context was not found at " + normalized);
        }
        if (hasContext != hasRevision) {
            throw incomplete(normalized);
        }

        ContextLock lock = acquireLock(normalized);
        try {
            ContextManifestStore.Manifest manifest = ContextManifestStore.read(contextPath);
            UUID contextId = manifest.getContextId();
            long revision = RevisionStore.read(revisionPath);
            if (revision > RevisionStore.INITIAL_REVISION
                    && !Files.isDirectory(generationPath(normalized, revision))) {
                throw corruption("DUMB2 Context revision " + revision
                        + " has no physical generation at " + normalized);
            }
            return new ContextStore(normalized, contextId, revision, lock, manifest.getTypeRegistry());
        } catch (NoSuchFileException failure) {
            StorageLifecycleException incomplete = incomplete(normalized);
            incomplete.addSuppressed(failure);
            closeQuietly(lock, incomplete);
            throw incomplete;
        } catch (IOException | StorageLifecycleException
                 | RuntimeException | Error failure) {
            closeQuietly(lock, failure);
            throw failure;
        }
    }

    /**
     * Registers a persistent layout in this Context and publishes the manifest
     * before returning its Context-local typeCode.
     *
     * <p>The in-memory registry is append-only. If manifest publication fails,
     * the call fails and no record may use the returned definition; retrying
     * the same registration republishes the same immutable code.</p>
     */
    synchronized TypeDefinition registerType(String typeName, Descriptor descriptor)
            throws Exception {
        requireOpen();
        TypeDefinition definition = typeRegistry.register(typeName, descriptor);
        ContextManifestStore.publish(contextPath(location), contextId, typeRegistry);
        return definition;
    }

    synchronized TypeDefinition resolveType(int typeCode) {
        requireOpen();
        return typeRegistry.resolve(typeCode);
    }

    synchronized TypeRegistry snapshotTypeRegistry() {
        requireOpen();
        TypeRegistry snapshot = new TypeRegistry();
        for (TypeDefinition definition : typeRegistry.definitions()) {
            snapshot.install(definition.getTypeCode(),
                    definition.getTypeName(),
                    definition.getDescriptor());
        }
        return snapshot;
    }

    synchronized IBase getBase(String schema) throws Exception {
        requireOpen();
        String descriptor = requireSchema(schema);
        ContextBase base = bases.get(descriptor);
        if (base == null) {
            base = new ContextBase(this, descriptor);
            bases.put(descriptor, base);
        }
        return base;
    }

    /**
     * Publishes all dirty schema images as one new immutable Context revision.
     *
     * <p>The previous generation is copied forward so unopened schema
     * namespaces are preserved. Dirty acquired bases replace their snapshot in
     * the staging generation. Only after the complete directory has been
     * atomically installed does the revision marker advance.</p>
     *
     * @return currently published revision; unchanged for a clean Context
     */
    synchronized long flush() throws Exception {
        requireOpen();

        boolean dirty = false;
        for (ContextBase base : bases.values()) {
            if (base.isDirty()) {
                base.validateWorkingState();
                dirty = true;
            }
        }
        if (!dirty) {
            return revision;
        }
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("DUMB2 revision space exhausted");
        }

        long persisted = RevisionStore.read(revisionPath(location));
        if (persisted != revision) {
            throw new IllegalStateException(
                    "DUMB2 Context revision changed while open: expected="
                            + revision + " actual=" + persisted);
        }

        long next = revision + 1L;
        Path root = stateRoot(location);
        Path previous = generationPath(location, revision);
        Path target = generationPath(location, next);
        Path staging = root.resolve(
                ".staging-" + next + "-" + UUID.randomUUID().toString());

        Files.createDirectories(root);
        deleteRecursively(staging);

        boolean generationInstalled = false;
        try {
            if (revision > RevisionStore.INITIAL_REVISION) {
                if (!Files.isDirectory(previous)) {
                    throw corruption("DUMB2 Context revision " + revision
                            + " lost its physical generation at " + location);
                }
                copyDirectory(previous, staging);
            } else {
                Files.createDirectories(staging);
            }

            for (ContextBase base : bases.values()) {
                if (base.isDirty()) {
                    base.writeSnapshot(staging);
                }
            }

            /*
             * A target with no matching visible revision is an orphan left by
             * a failed/crashed publication. The Context lock proves that no
             * concurrent writer can own it now, so rebuilding it is safe.
             */
            if (Files.exists(target)) {
                deleteRecursively(target);
            }

            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException failure) {
                throw new IOException(
                        "DUMB2 requires atomic same-filesystem generation publication: "
                                + target, failure);
            }
            generationInstalled = true;

            long published = RevisionStore.advance(revisionPath(location), revision);
            if (published != next) {
                throw new IllegalStateException(
                        "Unexpected DUMB2 published revision " + published
                                + "; expected " + next);
            }

            revision = published;
            for (ContextBase base : bases.values()) {
                if (base.isDirty()) {
                    base.markPublished();
                }
            }
            return revision;
        } finally {
            if (!generationInstalled || Files.exists(staging)) {
                deleteRecursively(staging);
            }
            /*
             * If generation installation succeeded but revision publication
             * failed, target intentionally remains an unpublished orphan. The
             * next exclusive flush rebuilds it while revision still names R.
             */
        }
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }

        /*
         * Failed flush leaves the handle open and lock held so the caller can
         * repair/retry. We only invalidate bases after durable publication.
         */
        flush();

        for (ContextBase base : bases.values()) {
            base.closeFromOwner();
        }
        bases.clear();
        closed = true;
        contextLock.close();
    }

    synchronized boolean isClosed() {
        return closed;
    }

    UUID getContextId() {
        return contextId;
    }

    synchronized long getRevision() {
        return revision;
    }

    Path getLocation() {
        return location;
    }

    Path schemaPath(String schema) {
        return schemaPath(generationPath(location, revision), schema);
    }

    Path schemaPath(Path generation, String schema) {
        return generation.resolve(encodeSchema(requireSchema(schema)) + ".base");
    }

    static Path contextPath(Path location) {
        Path normalized = requireLocation(location);
        return normalized.resolveSibling(
                normalized.getFileName().toString() + CONTEXT_SUFFIX);
    }

    static Path revisionPath(Path location) {
        Path normalized = requireLocation(location);
        return normalized.resolveSibling(
                normalized.getFileName().toString() + REVISION_SUFFIX);
    }

    static Path stateRoot(Path location) {
        Path normalized = requireLocation(location);
        return normalized.resolveSibling(
                normalized.getFileName().toString() + STATE_SUFFIX);
    }

    static Path generationPath(Path location, long revision) {
        return stateRoot(location).resolve("revision-" + revision);
    }

    private static Path lockPath(Path location) {
        return stateRoot(location).resolve(".lock");
    }

    private static ContextLock acquireLock(Path location)
            throws IOException, StorageLifecycleException {
        Path lockPath = lockPath(location);
        Files.createDirectories(lockPath.getParent());
        FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException failure) {
                lock = null;
            }
            if (lock == null) {
                channel.close();
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_ALREADY_OPEN,
                        "DUMB2 Context is already open at " + location);
            }
            return new ContextLock(channel, lock);
        } catch (IOException | StorageLifecycleException
                 | RuntimeException | Error failure) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("DUMB2 Context is closed: " + location);
        }
    }

    private static String requireSchema(String schema) {
        Objects.requireNonNull(schema, "schema");
        if (schema.isEmpty()) {
            throw new IllegalArgumentException("DUMB2 schema descriptor must not be empty");
        }
        return schema;
    }

    private static String encodeSchema(String schema) {
        byte[] bytes = schema.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        final char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte raw : bytes) {
            int value = raw & 0xff;
            if ((value >= 'a' && value <= 'z')
                    || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '-' || value == '_' || value == '.') {
                encoded.append((char) value);
            } else {
                encoded.append('%');
                encoded.append(hex[(value >>> 4) & 0x0f]);
                encoded.append(hex[value & 0x0f]);
            }
        }
        return encoded.toString();
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path child : stream) {
                Path destination = target.resolve(child.getFileName().toString());
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    copyDirectory(child, destination);
                } else {
                    Files.copy(child, destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static Path requireLocation(Path location) {
        Objects.requireNonNull(location, "location");
        if (location.getFileName() == null) {
            throw new IllegalArgumentException(
                    "DUMB2 Context location must have a final path component");
        }
        return location;
    }

    private static StorageLifecycleException incomplete(Path location) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                "Incomplete DUMB2 Context metadata at " + location
                        + ": both " + CONTEXT_SUFFIX + " and " + REVISION_SUFFIX
                        + " sidecars are required");
    }

    private static StorageLifecycleException corruption(String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION, message);
    }

    private static void closeQuietly(ContextLock lock, Throwable primary) {
        if (lock == null) {
            return;
        }
        try {
            lock.close();
        } catch (IOException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private static final class ContextLock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        private ContextLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                if (lock.isValid()) {
                    lock.release();
                }
            } catch (IOException error) {
                failure = error;
            }
            try {
                channel.close();
            } catch (IOException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
