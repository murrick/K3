/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.QueryPass;
import org.kanger.units.Rule;

/**
 * Opt-in query-local capture of pre-execution semantic estimates.
 *
 * A public query may execute multiple internal passes with transient Rule IDs
 * that overlap. The session binds observations to the intended QueryPass and
 * retains the first target-Rule estimate, which is the candidate state before
 * any Linker transformation in that pass.
 */
public final class SemanticPlanningTelemetry {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private SemanticPlanningTelemetry() {
    }

    public static void begin(QueryPass targetPass) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException(
                    "Semantic planning telemetry is already active");
        }
        CURRENT.set(new Session(targetPass));
    }

    public static void record(Rule query,
                              Mind mind,
                              SemanticPlanEstimate estimate) throws Exception {
        Session session = CURRENT.get();
        if (session == null || estimate == null
                || (session.targetPass != null
                && session.targetPass != mind.getQueryPass())) {
            return;
        }
        ++session.events;
        if (session.queryShape == null) {
            session.queryShape = SemanticYieldPlanner.describeQueryShape(query, mind);
        }
        if (estimate.isCalibrated()) {
            ++session.calibratedEvents;
            if (session.estimate == null) {
                session.estimate = estimate;
            }
        } else if (session.estimate == null && session.fallback == null) {
            session.fallback = estimate;
        }
    }

    public static Snapshot end() {
        Session session = CURRENT.get();
        CURRENT.remove();
        if (session == null) {
            return new Snapshot(null, 0L, 0L, null, null);
        }
        SemanticPlanEstimate estimate = session.estimate == null
                ? session.fallback : session.estimate;
        return new Snapshot(estimate, session.events,
                session.calibratedEvents, session.queryShape,
                session.targetPass);
    }

    public static final class Snapshot {
        private final SemanticPlanEstimate estimate;
        private final long events;
        private final long calibratedEvents;
        private final String queryShape;
        private final QueryPass targetPass;

        private Snapshot(SemanticPlanEstimate estimate,
                         long events,
                         long calibratedEvents,
                         String queryShape,
                         QueryPass targetPass) {
            this.estimate = estimate;
            this.events = events;
            this.calibratedEvents = calibratedEvents;
            this.queryShape = queryShape;
            this.targetPass = targetPass;
        }

        public SemanticPlanEstimate getEstimate() { return estimate; }
        public long getEvents() { return events; }
        public long getCalibratedEvents() { return calibratedEvents; }
        public String getQueryShape() { return queryShape; }
        public QueryPass getTargetPass() { return targetPass; }
    }

    private static final class Session {
        private final QueryPass targetPass;
        private SemanticPlanEstimate estimate;
        private SemanticPlanEstimate fallback;
        private String queryShape;
        private long events;
        private long calibratedEvents;

        private Session(QueryPass targetPass) {
            this.targetPass = targetPass;
        }
    }
}
