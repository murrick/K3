/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/** An unresolved child query attached to its native parent query pass. */
public final class FrontierDemand {
    private final FrontierDomain parent;
    private final FrontierDomain query;
    private final FrontierProjection parentProjection;

    FrontierDemand(FrontierDomain parent, FrontierDomain query,
                   FrontierProjection parentProjection) {
        this.parent = parent;
        this.query = query;
        this.parentProjection = parentProjection;
    }

    public FrontierDomain getParent() {
        return parent;
    }

    public FrontierDomain getQuery() {
        return query;
    }

    public FrontierProjection getParentProjection() {
        return parentProjection;
    }

    boolean semanticallyEquivalent(FrontierDemand other) {
        return parent.semanticallyEquivalent(other.parent)
                && query.semanticallyEquivalent(other.query)
                && parentProjection.semanticallyEquivalent(other.parentProjection);
    }
}
