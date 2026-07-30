/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.units;

import org.kanger.Mind;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.ICause;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Cause;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

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

    private final transient Object causeMemoLock = new Object();
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
        synchronized (causeMemoLock) {
            cachedCauseMind = null;
            cachedCauseArguments = null;
            cachedCauses = null;
        }
    }

    @Override
    public Set<ICause> getCauses(Mind mind) throws Exception {
        ArgumentsList current = getArguments().convertBase(mind);
        synchronized (causeMemoLock) {
            if (cachedCauseMind == mind
                    && cachedCauseArguments != null
                    && cachedCauses != null
                    && current.equalsBase(mind, cachedCauseArguments)) {
                return new HashSet<>(cachedCauses);
            }
        }

        Set<ICause> selected = new HashSet<>();
        Map<ArgumentsList, Set<ICause>> byArguments =
                mind.getDomainCauses().get(this);
        Set<ICause> source = byArguments == null
                ? null : byArguments.get(current);
        if (source != null) {
            selected.addAll(source);
            SortedMap<Integer, Set<ICause>> byWeight = new TreeMap<>();
            for (ICause cause : selected) {
                int weight = 0;
                for (IArgument own : getArguments()) {
                    for (IArgument donor :
                            ((Cause) cause).getDonor().getArguments()) {
                        if (!own.isEmpty(mind)
                                && !donor.isEmpty(mind)
                                && own.getValue(mind).getId()
                                == donor.getValue(mind).getId()) {
                            ++weight;
                            break;
                        }
                    }
                }
                Set<ICause> weighted = byWeight.get(weight);
                if (weighted == null) {
                    weighted = new HashSet<>();
                    byWeight.put(weight, weighted);
                }
                weighted.add(cause);
            }
            if (byWeight.size() > 1) {
                selected.removeAll(byWeight.get(byWeight.firstKey()));
            }
        }

        synchronized (causeMemoLock) {
            cachedCauseMind = mind;
            cachedCauseArguments = current;
            cachedCauses = new HashSet<>(selected);
        }
        return selected;
    }

    @Override
    public boolean setCauses(Collection<Cause> causes, Mind mind) throws Exception {
        invalidateCauseMemo();
        return super.setCauses(causes, mind);
    }

    @Override
    public CachedDomain setMind(Mind mind) throws Exception {
        synchronized (causeMemoLock) {
            if (cachedCauseMind != mind) {
                cachedCauseMind = null;
                cachedCauseArguments = null;
                cachedCauses = null;
            }
        }
        super.setMind(mind);
        return this;
    }
}
