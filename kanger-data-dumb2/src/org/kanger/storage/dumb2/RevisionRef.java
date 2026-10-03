/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import java.util.Objects;
import java.util.UUID;

/** Immutable semantic identity of one exact Context revision. */
final class RevisionRef {

    private final UUID contextId;
    private final long revision;

    RevisionRef(UUID contextId, long revision) {
        if (contextId == null) {
            throw new NullPointerException("contextId");
        }
        if (revision < RevisionStore.INITIAL_REVISION) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        this.contextId = contextId;
        this.revision = revision;
    }

    UUID getContextId() {
        return contextId;
    }

    long getRevision() {
        return revision;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RevisionRef)) {
            return false;
        }
        RevisionRef ref = (RevisionRef) other;
        return revision == ref.revision && contextId.equals(ref.contextId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextId, Long.valueOf(revision));
    }

    @Override
    public String toString() {
        return contextId.toString() + "@R" + revision;
    }
}
