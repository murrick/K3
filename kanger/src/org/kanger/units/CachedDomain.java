/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.units;

import org.kanger.Mind;
import org.kanger.interfaces.ICause;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Cause;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Hydrated Domain with a one-entry, query-local memo for cause selection.
 *
 * Cause selection depends on both the active Mind and the current argument
 * assignment. The memo therefore never spans a Mind transition and is
 * invalidated whenever causes are replaced. It is intentionally part of the
 * hydrated object: persistent data and the on-disk format remain unchanged,
 * while residency is bounded by the storage object cache.
 */
public class CachedDomain extends Domain {

    private static final long serialVersionUID = 196402070002L;

    private transient Mind cachedCauseMind;
    private transient ArgumentsList cachedCauseArguments;
    private transient Set<ICause> cachedCauses;

    public CachedDomain() {
        super();
    }

    public CachedDomain(Mind mind) {
        super(mind);
    }

    public CachedDomain(Predicate predicate,
                        boolean antc,
                        ArgumentsList arguments) {
        super(predicate, antc, arguments);
    }

    public CachedDomain(Predicate predicate,
                        boolean antc,
                        ArgumentsList arguments,
                        IRule rule) {
        super(predicate, antc, arguments, rule);
    }

    private void invalidateCauseMemo() {
        cachedCauseMind = null;
        cachedCauseArguments = null;
        cachedCauses = null;
    }

    @Override
    public Set<ICause> getCauses(Mind mind) throws Exception {
        ArgumentsList current = getArguments().convertBase(mind);
        if (cachedCauseMind == mind
                && cachedCauseArguments != null
                && current.equalsBase(mind, cachedCauseArguments)) {
            return new HashSet<>(cachedCauses);
        }

        Set<ICause> selected = super.getCauses(mind);
        cachedCauseMind = mind;
        cachedCauseArguments = current;
        cachedCauses = new HashSet<>(selected);
        return selected;
    }

    @Override
    public boolean setCauses(Collection<Cause> causes, Mind mind) throws Exception {
        invalidateCauseMemo();
        return super.setCauses(causes, mind);
    }

    @Override
    public CachedDomain setMind(Mind mind) throws Exception {
        if (cachedCauseMind != mind) {
            invalidateCauseMemo();
        }
        super.setMind(mind);
        return this;
    }
}
