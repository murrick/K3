/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;

import java.util.UUID;

/**
 * Detached operation-local response address for one logical frontier call.
 * Fan-out shares the address; each answer also identifies its exact target.
 * This token is not a semantic cache key or a Context-local inference ID.
 * Evidence-qualified execution-state dedup belongs to causal scheduling.
 */
final class FrontierInvocation {
    private final UUID id;
    private final FrontierDomain frontier;

    private FrontierInvocation(FrontierDomain frontier) {
        if (frontier == null) {
            throw new NullPointerException("frontier");
        }
        this.id = UUID.randomUUID();
        this.frontier = frontier;
    }

    static FrontierInvocation create(FrontierDomain frontier) {
        return new FrontierInvocation(frontier);
    }

    UUID getId() {
        return id;
    }

    FrontierDomain getFrontier() {
        return frontier;
    }

    boolean sameAddress(FrontierInvocation other) {
        return other != null && id.equals(other.id);
    }
}
