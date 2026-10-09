/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable descriptor of one fully materialized but not yet published DUMB2
 * Context revision candidate.
 */
final class ContextCandidate {

    private final Path location;
    private final Path generation;
    private final UUID contextId;
    private final long revision;
    private final ContextManifestStore.Origin origin;
    private final TypeRegistry typeRegistry;
    private final ConnectionVector dependencies;

    private ContextCandidate(
            Path location,
            Path generation,
            UUID contextId,
            long revision,
            ContextManifestStore.Origin origin,
            TypeRegistry typeRegistry,
            ConnectionVector dependencies) {
        this.location = Objects.requireNonNull(
                location, "location")
                .toAbsolutePath().normalize();
        this.generation = Objects.requireNonNull(
                generation, "generation")
                .toAbsolutePath().normalize();
        this.contextId = Objects.requireNonNull(
                contextId, "contextId");
        if (revision <= RevisionStore.INITIAL_REVISION) {
            throw new IllegalArgumentException(
                    "candidate revision must be positive");
        }
        this.revision = revision;
        this.origin = origin;
        this.typeRegistry = copyRegistry(
                Objects.requireNonNull(
                        typeRegistry, "typeRegistry"));
        this.dependencies = Objects.requireNonNull(
                dependencies, "dependencies");
    }

    static ContextCandidate of(
            ContextStore owner,
            Path generation,
            long revision) {
        return of(
                owner,
                generation,
                revision,
                ConnectionVector.empty());
    }

    static ContextCandidate of(
            ContextStore owner,
            Path generation,
            long revision,
            ConnectionVector dependencies) {
        if (owner == null) {
            throw new NullPointerException("owner");
        }
        return new ContextCandidate(
                owner.getLocation(),
                generation,
                owner.getContextId(),
                revision,
                owner.getOrigin(),
                owner.snapshotTypeRegistry(),
                dependencies);
    }

    static ContextCandidate of(
            Path location,
            Path generation,
            UUID contextId,
            long revision,
            ContextManifestStore.Origin origin,
            TypeRegistry typeRegistry,
            ConnectionVector dependencies) {
        return new ContextCandidate(
                location,
                generation,
                contextId,
                revision,
                origin,
                typeRegistry,
                dependencies);
    }

    RevisionRef getRef() {
        return new RevisionRef(contextId, revision);
    }

    Path getLocation() {
        return location;
    }

    Path getGeneration() {
        return generation;
    }

    ContextSnapshot openSnapshot() throws Exception {
        return ContextSnapshot.openCandidate(
                location,
                generation,
                contextId,
                revision,
                origin,
                typeRegistry,
                dependencies);
    }

    private static TypeRegistry copyRegistry(
            TypeRegistry source) {
        TypeRegistry copy = new TypeRegistry();
        for (TypeDefinition definition
                : source.definitions()) {
            copy.install(
                    definition.getTypeCode(),
                    definition.getTypeName(),
                    definition.getDescriptor());
        }
        return copy;
    }
}
