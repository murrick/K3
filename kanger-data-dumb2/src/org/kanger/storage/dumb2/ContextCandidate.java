/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.storage.dumb2.descriptor.TypeRegistry;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Immutable descriptor of one fully-serialized unpublished Context revision.
 */
final class ContextCandidate {

    private final Path location;
    private final UUID contextId;
    private final long revision;
    private final ContextManifestStore.Origin origin;
    private final TypeRegistry typeRegistry;
    private final Path generation;

    ContextCandidate(Path location,
                     UUID contextId,
                     long revision,
                     ContextManifestStore.Origin origin,
                     TypeRegistry typeRegistry,
                     Path generation) {
        if (location == null || contextId == null
                || typeRegistry == null || generation == null) {
            throw new NullPointerException(
                    "candidate location, identity, registry and generation are required");
        }
        this.location = location;
        this.contextId = contextId;
        this.revision = revision;
        this.origin = origin;
        this.typeRegistry = typeRegistry;
        this.generation = generation;
    }

    Path getLocation() {
        return location;
    }

    UUID getContextId() {
        return contextId;
    }

    long getRevision() {
        return revision;
    }

    RevisionRef getRef() {
        return new RevisionRef(contextId, revision);
    }

    Path getGeneration() {
        return generation;
    }

    ContextSnapshot openSnapshot() throws Exception {
        return ContextSnapshot.openCandidate(
                location,
                contextId,
                revision,
                origin,
                typeRegistry,
                generation);
    }
}
