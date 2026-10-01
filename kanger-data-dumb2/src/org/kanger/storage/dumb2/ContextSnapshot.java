/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable read handle for one published DUMB 2.0 Context revision.
 *
 * <p>The snapshot does not acquire the mutable Context writer lock. It selects
 * the revision marker once, resolves all records through a frozen manifest
 * registry and reads only from the corresponding immutable revision directory.
 * A later publication may advance the Context to R+1 without changing this
 * handle's view of R.</p>
 *
 * <p>This is an M2 storage-side primitive, not yet the public runtime
 * attachment contract. It deliberately exposes only the identity and
 * schema-base capabilities required to prove fixed-revision semantics.</p>
 */
final class ContextSnapshot implements AutoCloseable, PersistentTypeResolver {

    private final Path location;
    private final Path generation;
    private final UUID contextId;
    private final long revision;
    private final ContextManifestStore.Origin origin;
    private final TypeRegistry typeRegistry;
    private final Map<String, ContextBase> bases =
            new LinkedHashMap<String, ContextBase>();

    private boolean closed;

    private ContextSnapshot(Path location,
                            UUID contextId,
                            long revision,
                            ContextManifestStore.Origin origin,
                            TypeRegistry typeRegistry) {
        this.location = location;
        this.contextId = contextId;
        this.revision = revision;
        this.origin = origin;
        this.typeRegistry = typeRegistry;
        this.generation = ContextStore.generationPath(location, revision);
    }

    /**
     * Selects the currently published revision without taking the writer lock.
     *
     * <p>The revision marker is read before the manifest. Manifest publication
     * is append-only, so a concurrent writer may make additional type
     * definitions visible but cannot invalidate definitions used by the
     * selected revision. If the writer later advances the revision marker, the
     * immutable generation chosen here remains authoritative for this handle.</p>
     */
    static ContextSnapshot open(Path location)
            throws IOException, StorageLifecycleException {
        Path revisionPath = requireContextMetadata(location);
        long revision = RevisionStore.read(revisionPath);
        return open(location, revision);
    }

    /**
     * Opens one exact already-published Context revision.
     *
     * <p>This is the storage primitive used by pinned M3 connections. A later
     * publication may advance CURRENT, but an operation that selected revision
     * {@code R} continues to read the immutable generation for {@code R}.</p>
     */
    static ContextSnapshot open(Path location, long revision)
            throws IOException, StorageLifecycleException {
        if (revision < RevisionStore.INITIAL_REVISION) {
            throw new IllegalArgumentException(
                    "DUMB2 Context revision must be non-negative");
        }

        Path revisionPath = requireContextMetadata(location);
        long currentRevision = RevisionStore.read(revisionPath);
        if (revision > currentRevision) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                    "DUMB2 Context revision " + revision
                            + " is not published at " + location
                            + " (current " + currentRevision + ")");
        }

        Path contextPath = ContextStore.contextPath(location);
        ContextManifestStore.Manifest manifest =
                ContextManifestStore.read(contextPath);

        if (revision > RevisionStore.INITIAL_REVISION
                && !Files.isDirectory(
                ContextStore.generationPath(location, revision))) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                    "DUMB2 Context revision " + revision
                            + " is not retained at " + location);
        }

        ContextSnapshot snapshot = new ContextSnapshot(
                location,
                manifest.getContextId(),
                revision,
                manifest.getOrigin(),
                copyRegistry(manifest.getTypeRegistry()));
        try {
            snapshot.validatePublishedGeneration();
            return snapshot;
        } catch (IOException | StorageLifecycleException
                 | RuntimeException | Error failure) {
            snapshot.closeQuietly();
            throw failure;
        }
    }

    private static Path requireContextMetadata(Path location)
            throws StorageLifecycleException {
        if (location == null || location.getFileName() == null) {
            throw new IllegalArgumentException(
                    "DUMB2 Context location must have a final path component");
        }

        Path contextPath = ContextStore.contextPath(location);
        Path revisionPath = ContextStore.revisionPath(location);
        boolean hasContext = Files.exists(contextPath);
        boolean hasRevision = Files.exists(revisionPath);

        if (!hasContext && !hasRevision) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                    "DUMB2 Context was not found at " + location);
        }
        if (hasContext != hasRevision) {
            throw corruption("Incomplete DUMB2 Context metadata at " + location
                    + ": both " + ContextStore.CONTEXT_SUFFIX + " and "
                    + ContextStore.REVISION_SUFFIX + " sidecars are required");
        }
        return revisionPath;
    }

    UUID getContextId() {
        return contextId;
    }

    long getRevision() {
        return revision;
    }

    ContextManifestStore.Origin getOrigin() {
        return origin;
    }

    TypeRegistry snapshotTypeRegistry() {
        requireOpen();
        return copyRegistry(typeRegistry);
    }

    ContextStore fork(Path target) throws Exception {
        requireOpen();
        return ContextStore.fork(this, target);
    }

    Path getLocation() {
        return location;
    }

    Path getGeneration() {
        return generation;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized IBase getBase(String schema) throws Exception {
        requireOpen();
        ContextBase base = bases.get(schema);
        if (base == null) {
            Path path = ContextStore.schemaPathForGeneration(
                    generation, schema);
            base = new ContextBase(this, schema, path);
            bases.put(schema, base);
        }
        return base;
    }

    @Override
    public synchronized TypeDefinition resolveType(int typeCode) {
        requireOpen();
        return typeRegistry.resolve(typeCode);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        for (ContextBase base : bases.values()) {
            base.closeFromOwner();
        }
        bases.clear();
        closed = true;
    }

    private void validatePublishedGeneration()
            throws IOException, StorageLifecycleException {
        if (revision == RevisionStore.INITIAL_REVISION) {
            return;
        }

        int baseCount = 0;
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(generation)) {
            for (Path child : stream) {
                if (!Files.isRegularFile(
                        child, LinkOption.NOFOLLOW_LINKS)
                        || !child.getFileName().toString().endsWith(".base")) {
                    throw corruption(
                            "Unexpected entry in published DUMB2 generation "
                                    + child);
                }

                ++baseCount;
                String schema = ContextBase.readStoredSchema(child);
                Path canonical = ContextStore.schemaPathForGeneration(
                        generation, schema);
                if (!canonical.equals(child)) {
                    throw corruption(
                            "DUMB2 schema snapshot path does not match "
                                    + "its stored descriptor: " + child);
                }

                ContextBase probe = null;
                try {
                    probe = new ContextBase(this, schema, child);
                } catch (StorageLifecycleException failure) {
                    throw failure;
                } catch (IOException failure) {
                    throw failure;
                } catch (Exception failure) {
                    StorageLifecycleException invalid = corruption(
                            "Cannot validate published DUMB2 schema " + child);
                    invalid.addSuppressed(failure);
                    throw invalid;
                } finally {
                    if (probe != null) {
                        probe.closeFromOwner();
                    }
                }
            }
        }

        if (baseCount == 0) {
            throw corruption(
                    "Published DUMB2 revision " + revision
                            + " contains no schema snapshots at " + generation);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "DUMB2 Context snapshot is closed: " + location
                            + "@" + revision);
        }
    }

    private void closeQuietly() {
        try {
            close();
        } catch (RuntimeException ignored) {
            // Best-effort cleanup after failed acquisition.
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

    private static StorageLifecycleException corruption(String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                message);
    }
}
