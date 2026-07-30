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
import java.util.HashSet;
import java.util.List;
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
        return session == null
                ? new Snapshot(0L, 0L, 0L, 0L, 0L)
                : new Snapshot(
                        session.causes.size(),
                        session.solveKeys.size(),
                        session.generatedRules.size(),
                        session.solveCandidates,
                        session.duplicateSolveCandidates);
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
     * Records one unique TSolve only after Mind.addTSolve has passed canonical
     * deduplication and inserted the tuple into ruleSolves.
     */
    public static void recordTSolve(Collection<TValue> values) {
        Session session = CURRENT.get();
        if (session == null || values == null || values.isEmpty()) {
            return;
        }
        List<Long> key = new ArrayList<>();
        for (TValue value : values) {
            if (value != null) {
                key.add(value.getId());
            }
        }
        if (!key.isEmpty()) {
            Collections.sort(key);
            session.solveKeys.add(key);
        }
    }

    /**
     * Records the result of one deferred solve candidate reaching the canonical
     * Mind.addTSolve boundary. A duplicate candidate is semantically useful
     * provenance but must not be counted as a new TSolve.
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

    public static final class Snapshot {
        private final long newCauses;
        private final long newTSolves;
        private final long newGeneratedRules;
        private final long solveCandidates;
        private final long duplicateSolveCandidates;

        private Snapshot(long newCauses,
                         long newTSolves,
                         long newGeneratedRules,
                         long solveCandidates,
                         long duplicateSolveCandidates) {
            this.newCauses = newCauses;
            this.newTSolves = newTSolves;
            this.newGeneratedRules = newGeneratedRules;
            this.solveCandidates = solveCandidates;
            this.duplicateSolveCandidates = duplicateSolveCandidates;
        }

        public long getNewCauses() {
            return newCauses;
        }

        public long getNewTSolves() {
            return newTSolves;
        }

        public long getNewGeneratedRules() {
            return newGeneratedRules;
        }

        public long getSolveCandidates() {
            return solveCandidates;
        }

        public long getDuplicateSolveCandidates() {
            return duplicateSolveCandidates;
        }
    }

    private static final class Session {
        private final Set<Object> causes = new HashSet<>();
        private final Set<List<Long>> solveKeys = new HashSet<>();
        private final Set<Object> generatedRules = new HashSet<>();
        private long solveCandidates;
        private long duplicateSolveCandidates;
    }
}
