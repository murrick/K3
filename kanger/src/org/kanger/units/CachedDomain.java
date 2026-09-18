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
import java.util.List;
import java.util.ArrayList;
import org.kanger.interfaces.ITerm;

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
        boolean profile = Boolean.getBoolean("kanger.experiment.profileCauseMemo");
        long[] counts = null;
        if (profile) {
            counts = causeProfile.get();
            if (counts == null) { counts = new long[9]; causeProfile.set(counts); }
            counts[0]++;
        }
        long start = profile ? System.nanoTime() : 0;
        ArgumentsList current = getArguments().convertBase(mind);
        long converted = profile ? System.nanoTime() : 0;
        if (profile) counts[2] += converted - start;
        synchronized (causeMemoLock) {
            if (cachedCauseMind == mind
                    && cachedCauseArguments != null
                    && cachedCauses != null
                    && current.equalsBase(mind, cachedCauseArguments)) {
                Set<ICause> copy = new HashSet<>(cachedCauses);
                if (profile) {
                    counts[1]++;
                    counts[3] += System.nanoTime() - converted;
                    counts[8] += copy.size();
                }
                return copy;
            }
        }

        long checked = profile ? System.nanoTime() : 0;
        if (profile) counts[3] += checked - converted;
        Set<ICause> selected = new HashSet<>();
        Map<ArgumentsList, Set<ICause>> byArguments =
                mind.getDomainCauses().get(this);
        Set<ICause> source = byArguments == null
                ? null : byArguments.get(current);
        if (source != null) {
            if (profile) counts[6] += source.size();
            selected.addAll(source);
            boolean shadow = Boolean.getBoolean("kanger.experiment.shadowCauseWeights");
            boolean fast = Boolean.getBoolean("kanger.experiment.resolvedCauseWeights");
            Map<ICause, Integer> resolved = fast ? guardedWeights(selected, mind) : null;
            if (fast) causeWeightCounts()[resolved == null ? 5 : 4]++;
            // Verification follows the guard too: unsupported resolution is reference-only.
            if (fast && resolved == null) shadow = false;
            List<Long> ownIds = shadow && resolved == null ? resolvedIds(getArguments(), mind) : null;
            SortedMap<Integer, Set<ICause>> predictedGroups = shadow ? new TreeMap<>() : null;
            if (shadow) causeWeightCounts()[0]++;
            SortedMap<Integer, Set<ICause>> byWeight = new TreeMap<>();
            for (ICause cause : selected) {
                int weight = 0;
                if (resolved != null && !shadow) weight = resolved.get(cause);
                else for (IArgument own : getArguments()) {
                    for (IArgument donor :
                            ((Cause) cause).getDonor().getArguments()) {
                        if (profile) counts[7]++;
                        if (!own.isEmpty(mind)
                                && !donor.isEmpty(mind)
                                && own.getValue(mind).getId()
                                == donor.getValue(mind).getId()) {
                            ++weight;
                            break;
                        }
                    }
                }
                if (shadow) {
                    int predicted = resolved != null ? resolved.get(cause)
                            : resolvedWeight(ownIds, ((Cause) cause).getDonor().getArguments(), mind);
                    causeWeightCounts()[1]++;
                    if (predicted != weight) {
                        causeWeightCounts()[3]++;
                        throw new AssertionError("Resolved cause weight differs from reference");
                    }
                    Set<ICause> group = predictedGroups.get(predicted);
                    if (group == null) { group = new HashSet<>(); predictedGroups.put(predicted, group); }
                    group.add(cause);
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
            if (shadow) {
                if (predictedGroups.size() > 1) predictedGroups.remove(predictedGroups.firstKey());
                Set<ICause> predicted = new HashSet<>();
                for (Set<ICause> group : predictedGroups.values()) predicted.addAll(group);
                if (!selected.equals(predicted)) {
                    causeWeightCounts()[3]++;
                    throw new AssertionError("Resolved cause selection differs from reference");
                }
            }
        }

        long selectedAt = profile ? System.nanoTime() : 0;
        if (profile) counts[4] += selectedAt - checked;
        synchronized (causeMemoLock) {
            cachedCauseMind = mind;
            cachedCauseArguments = current;
            cachedCauses = new HashSet<>(selected);
        }
        if (profile) {
            counts[5] += System.nanoTime() - selectedAt;
            counts[8] += selected.size();
        }
        return selected;
    }

    private static final ThreadLocal<long[]> causeProfile = new ThreadLocal<>();
    private static final ThreadLocal<long[]> weightProfile = new ThreadLocal<>();
    private static long[] causeWeightCounts() {
        long[] counts = weightProfile.get();
        if (counts == null) { counts = new long[6]; weightProfile.set(counts); }
        return counts;
    }
    /** Shadow miss selections, compared weights, resolved arguments, mismatches, eligible fast selections, fallback selections. */
    public static long[] experimentalCauseWeightProfile() { return causeWeightCounts().clone(); }

    private static List<Long> resolvedIds(ArgumentsList arguments, Mind mind) throws Exception {
        List<Long> ids = new ArrayList<>();
        boolean profile = Boolean.getBoolean("kanger.experiment.shadowCauseWeights");
        for (IArgument argument : arguments) {
            ITerm value = argument.getValue(mind);
            if (profile) causeWeightCounts()[2]++;
            if (value != null) ids.add(value.getId());
        }
        return ids;
    }

    private Map<ICause, Integer> guardedWeights(Set<ICause> causes, Mind mind) {
        if (mind.getClass() != Mind.class || getClass() != CachedDomain.class) return null;
        try {
            if (!supportedArguments(getArguments(), mind)) return null;
            for (ICause cause : causes)
                if (cause.getClass() != Cause.class
                        || !supportedArguments(((Cause) cause).getDonor().getArguments(), mind)) return null;
            Map<ICause, Integer> weights = new java.util.IdentityHashMap<>();
            if (Boolean.getBoolean("kanger.experiment.compactCauseWeights")) {
                long[] own = new long[getArguments().size()];
                int ownSize = resolvedPrimitiveIds(getArguments(), mind, own);
                long[] donor = new long[0];
                for (ICause cause : causes) {
                    ArgumentsList arguments = ((Cause) cause).getDonor().getArguments();
                    if (donor.length < arguments.size()) donor = new long[arguments.size()];
                    int donorSize = resolvedPrimitiveIds(arguments, mind, donor);
                    weights.put(cause, primitiveWeight(own, ownSize, donor, donorSize));
                }
            } else {
                List<Long> own = resolvedIds(getArguments(), mind);
                for (ICause cause : causes)
                    weights.put(cause, resolvedWeight(own, ((Cause) cause).getDonor().getArguments(), mind));
            }
            return weights;
        } catch (Exception unsupportedResolution) {
            // Keep original resolution/logging/error behavior on the reference path.
            return null;
        }
    }

    private static int resolvedPrimitiveIds(ArgumentsList arguments, Mind mind, long[] ids) throws Exception {
        int size = 0;
        boolean profile = Boolean.getBoolean("kanger.experiment.shadowCauseWeights");
        for (IArgument argument : arguments) {
            ITerm value = argument.getValue(mind);
            if (profile) causeWeightCounts()[2]++;
            if (value != null) ids[size++] = value.getId();
        }
        return size;
    }

    private static int primitiveWeight(long[] own, int ownSize, long[] donor, int donorSize) {
        int weight = 0;
        for (int i = 0; i < ownSize; ++i)
            for (int j = 0; j < donorSize; ++j)
                if (own[i] == donor[j]) { ++weight; break; }
        return weight;
    }

    private static boolean supportedArguments(ArgumentsList arguments, Mind mind) throws Exception {
        for (IArgument argument : arguments) {
            if (argument.getClass() != org.kanger.primitives.Argument.class) return false;
            Class<?> expected;
            switch (argument.getType()) {
                case EMPTY: continue;
                case TERM: expected = Term.class; break;
                case TVARIABLE: expected = TVariable.class; break;
                case TVALUE: expected = TValue.class; break;
                default: return false;
            }
            Object object = argument.getObject(mind);
            if (object == null || object.getClass() != expected) return false;
        }
        return true;
    }

    private static int resolvedWeight(List<Long> ownIds, ArgumentsList donor, Mind mind) throws Exception {
        Set<Long> donorIds = new HashSet<>(resolvedIds(donor, mind));
        int weight = 0;
        for (Long id : ownIds) if (donorIds.contains(id)) ++weight;
        return weight;
    }
    /** Calls, hits, convert ns, check/copy ns, select ns, publish ns, source causes, argument pairs, returned causes. */
    public static long[] experimentalCauseProfile() {
        long[] counts = causeProfile.get();
        return counts == null ? new long[9] : counts.clone();
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
