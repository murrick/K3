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
                ? new Snapshot(0L, 0L)
                : new Snapshot(session.causes.size(), session.solveKeys.size());
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

    public static final class Snapshot {
        private final long newCauses;
        private final long newTSolves;

        private Snapshot(long newCauses, long newTSolves) {
            this.newCauses = newCauses;
            this.newTSolves = newTSolves;
        }

        public long getNewCauses() {
            return newCauses;
        }

        public long getNewTSolves() {
            return newTSolves;
        }
    }

    private static final class Session {
        private final Set<Object> causes = new HashSet<>();
        private final Set<List<Long>> solveKeys = new HashSet<>();
    }
}
