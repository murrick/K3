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
                ? new Snapshot(0L, 0L, 0L)
                : new Snapshot(
                        session.causes.size(),
                        session.solveKeys.size(),
                        session.generatedRules.size());
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
     * Internal instrumentation hook. TSolve construction is used both for
     * lookup and insertion, so tuples are deduplicated by their TValue IDs.
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

        private Snapshot(long newCauses, long newTSolves, long newGeneratedRules) {
            this.newCauses = newCauses;
            this.newTSolves = newTSolves;
            this.newGeneratedRules = newGeneratedRules;
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
    }

    private static final class Session {
        private final Set<Object> causes = new HashSet<>();
        private final Set<List<Long>> solveKeys = new HashSet<>();
        private final Set<Object> generatedRules = new HashSet<>();
    }
}
