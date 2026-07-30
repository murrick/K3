/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.units.TValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in, thread-local observation of semantic effects that are created below
 * the public query boundary and would otherwise disappear with the transient
 * child Mind. The observer is inactive during normal execution and does not
 * participate in inference, transactions, persistence, or logical equality.
 */
public final class SemanticEffectTelemetry {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private SemanticEffectTelemetry() {
    }

    public static void begin() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Semantic effect telemetry is already active");
        }
        CURRENT.set(new Session());
    }

    public static Snapshot end() {
        Session session = CURRENT.get();
        CURRENT.remove();
        if (session == null) {
            return new Snapshot(0L, 0L, 0L, 0L, 0L,
                    0L, 0L, 0L, 0L, 0L);
        }

        long contributorLinks = 0L;
        long groupsWithNewTSolve = 0L;
        long minimumContributors = Long.MAX_VALUE;
        long maximumContributors = 0L;
        for (DeferredGroup group : session.deferredGroups.values()) {
            long contributors = group.operationIds.size();
            contributorLinks += contributors;
            minimumContributors = Math.min(minimumContributors, contributors);
            maximumContributors = Math.max(maximumContributors, contributors);
            if (group.newTSolve) {
                ++groupsWithNewTSolve;
            }
        }
        if (session.deferredGroups.isEmpty()) {
            minimumContributors = 0L;
        }

        return new Snapshot(
                session.causes.size(),
                session.solveKeys.size(),
                session.generatedRules.size(),
                session.solveCandidates,
                session.duplicateSolveCandidates,
                session.deferredGroups.size(),
                contributorLinks,
                groupsWithNewTSolve,
                minimumContributors,
                maximumContributors);
    }

    /**
     * Internal instrumentation hook. Public only because producers live in
     * org.kanger subpackages.
     */
    public static void recordCause(Object cause) {
        Session session = CURRENT.get();
        if (session != null && cause != null) {
            session.causes.add(cause);
        }
    }

    /**
     * Records the unification that contributed a deferred substitution tuple.
     * Contributors are grouped by the same canonical TValue-ID key later used
     * to deduplicate TSolve objects.
     */
    public static void recordDeferredContribution(Collection<TValue> values,
                                                  long operationId) {
        Session session = CURRENT.get();
        List<Long> key = solveKey(values);
        if (session == null || key.isEmpty()) {
            return;
        }
        DeferredGroup group = session.deferredGroups.get(key);
        if (group == null) {
            group = new DeferredGroup();
            session.deferredGroups.put(key, group);
        }
        group.operationIds.add(operationId);
    }

    /**
     * Records one unique TSolve only after Mind.addTSolve has passed canonical
     * deduplication and inserted the tuple into ruleSolves.
     */
    public static void recordTSolve(Collection<TValue> values) {
        Session session = CURRENT.get();
        List<Long> key = solveKey(values);
        if (session == null || key.isEmpty()) {
            return;
        }
        session.solveKeys.add(key);
        DeferredGroup group = session.deferredGroups.get(key);
        if (group == null) {
            group = new DeferredGroup();
            session.deferredGroups.put(key, group);
        }
        group.newTSolve = true;
    }

    /**
     * Records the result of one deferred solve candidate reaching the canonical
     * Mind.addTSolve boundary. A duplicate candidate is provenance and cost,
     * but must not be counted as a new TSolve.
     */
    public static void recordTSolveCandidate(boolean created) {
        Session session = CURRENT.get();
        if (session != null) {
            ++session.solveCandidates;
            if (!created) {
                ++session.duplicateSolveCandidates;
            }
        }
    }

    /**
     * Internal instrumentation hook for a generated Rule that survived the
     * canonical RuleFactory insertion path. Deduplicated candidates must not
     * call this method.
     */
    public static void recordGeneratedRule(Object rule) {
        Session session = CURRENT.get();
        if (session != null && rule != null) {
            session.generatedRules.add(rule);
        }
    }

    private static List<Long> solveKey(Collection<TValue> values) {
        List<Long> key = new ArrayList<>();
        if (values == null) {
            return key;
        }
        for (TValue value : values) {
            if (value != null) {
                key.add(value.getId());
            }
        }
        Collections.sort(key);
        return key;
    }

    public static final class Snapshot {
        private final long newCauses;
        private final long newTSolves;
        private final long newGeneratedRules;
        private final long solveCandidates;
        private final long duplicateSolveCandidates;
        private final long deferredGroups;
        private final long deferredContributorLinks;
        private final long groupsWithNewTSolve;
        private final long minimumContributorsPerGroup;
        private final long maximumContributorsPerGroup;

        private Snapshot(long newCauses,
                         long newTSolves,
                         long newGeneratedRules,
                         long solveCandidates,
                         long duplicateSolveCandidates,
                         long deferredGroups,
                         long deferredContributorLinks,
                         long groupsWithNewTSolve,
                         long minimumContributorsPerGroup,
                         long maximumContributorsPerGroup) {
            this.newCauses = newCauses;
            this.newTSolves = newTSolves;
            this.newGeneratedRules = newGeneratedRules;
            this.solveCandidates = solveCandidates;
            this.duplicateSolveCandidates = duplicateSolveCandidates;
            this.deferredGroups = deferredGroups;
            this.deferredContributorLinks = deferredContributorLinks;
            this.groupsWithNewTSolve = groupsWithNewTSolve;
            this.minimumContributorsPerGroup = minimumContributorsPerGroup;
            this.maximumContributorsPerGroup = maximumContributorsPerGroup;
        }

        public long getNewCauses() { return newCauses; }
        public long getNewTSolves() { return newTSolves; }
        public long getNewGeneratedRules() { return newGeneratedRules; }
        public long getSolveCandidates() { return solveCandidates; }
        public long getDuplicateSolveCandidates() { return duplicateSolveCandidates; }
        public long getDeferredGroups() { return deferredGroups; }
        public long getDeferredContributorLinks() { return deferredContributorLinks; }
        public long getGroupsWithNewTSolve() { return groupsWithNewTSolve; }
        public long getMinimumContributorsPerGroup() { return minimumContributorsPerGroup; }
        public long getMaximumContributorsPerGroup() { return maximumContributorsPerGroup; }
    }

    private static final class Session {
        private final Set<Object> causes = new HashSet<>();
        private final Set<List<Long>> solveKeys = new HashSet<>();
        private final Set<Object> generatedRules = new HashSet<>();
        private final Map<List<Long>, DeferredGroup> deferredGroups = new HashMap<>();
        private long solveCandidates;
        private long duplicateSolveCandidates;
    }

    private static final class DeferredGroup {
        private final Set<Long> operationIds = new HashSet<>();
        private boolean newTSolve;
    }
}
