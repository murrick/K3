/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal DUMB 2.0 Context metadata lifecycle boundary.
 *
 * <p>A Context is created and reopened as one coherent pair of stable identity
 * and durable revision. The two binary codecs remain deliberately independent;
 * this owner only prevents the runtime from accepting a half-created metadata
 * pair as a valid Context.</p>
 *
 * <p>This slice does not publish physical unit state and therefore exposes no
 * revision-advance operation. A later storage-wide durable commit owns revision
 * advancement after all schema state has crossed its durability boundary.</p>
 *
 * <p>The supplied {@code location} is a locator, not Context identity. Metadata
 * sidecars are derived as {@code <location>.context} and
 * {@code <location>.revision}; moving or renaming the complete pair preserves
 * the persisted ContextId and revision.</p>
 */
final class ContextStore {

    static final String CONTEXT_SUFFIX = ".context";
    static final String REVISION_SUFFIX = ".revision";

    private final Path location;
    private final UUID contextId;
    private final long revision;

    private ContextStore(Path location, UUID contextId, long revision) {
        this.location = location;
        this.contextId = contextId;
        this.revision = revision;
    }

    /**
     * Creates a new Context metadata pair without replacing existing metadata.
     *
     * <p>If revision creation fails after a fresh identity has been created,
     * that fresh identity is removed again. Existing metadata is never deleted
     * or repaired implicitly.</p>
     */
    static ContextStore create(Path location) throws IOException {
        Path normalized = requireLocation(location);
        Path contextPath = contextPath(normalized);
        Path revisionPath = revisionPath(normalized);

        UUID contextId = ContextIdStore.create(contextPath);
        try {
            long revision = RevisionStore.create(revisionPath);
            return new ContextStore(normalized, contextId, revision);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(contextPath);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Reopens one complete Context metadata pair.
     *
     * <p>No metadata present is a missing Context. Exactly one sidecar present
     * is semantic corruption: DUMB2 never silently adopts or repairs such a
     * partial generation.</p>
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

        try {
            UUID contextId = ContextIdStore.read(contextPath);
            long revision = RevisionStore.read(revisionPath);
            return new ContextStore(normalized, contextId, revision);
        } catch (NoSuchFileException failure) {
            StorageLifecycleException incomplete = incomplete(normalized);
            incomplete.addSuppressed(failure);
            throw incomplete;
        }
    }

    UUID getContextId() {
        return contextId;
    }

    long getRevision() {
        return revision;
    }

    Path getLocation() {
        return location;
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
}
