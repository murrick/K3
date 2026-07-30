/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.units.Rule;

/**
 * Opt-in query-local capture of pre-execution semantic estimates.
 *
 * Normal execution pays only a ThreadLocal null check. The final calibrated
 * estimate is retained because Analyzer may expose the target rule before and
 * after Linker. Target identity is selected by Analyzer, not by event order.
 */
public final class SemanticPlanningTelemetry {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private SemanticPlanningTelemetry() {
    }

    public static void begin() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException(
                    "Semantic planning telemetry is already active");
        }
        CURRENT.set(new Session());
    }

    public static void record(Rule query,
                              Mind mind,
                              SemanticPlanEstimate estimate) throws Exception {
        Session session = CURRENT.get();
        if (session == null || estimate == null) {
            return;
        }
        ++session.events;
        session.queryShape = SemanticYieldPlanner.describeQueryShape(query, mind);
        if (estimate.isCalibrated()) {
            ++session.calibratedEvents;
            session.estimate = estimate;
        } else if (session.estimate == null) {
            session.fallback = estimate;
        }
    }

    public static Snapshot end() {
        Session session = CURRENT.get();
        CURRENT.remove();
        if (session == null) {
            return new Snapshot(null, 0L, 0L, null);
        }
        SemanticPlanEstimate estimate = session.estimate == null
                ? session.fallback : session.estimate;
        return new Snapshot(estimate, session.events,
                session.calibratedEvents, session.queryShape);
    }

    public static final class Snapshot {
        private final SemanticPlanEstimate estimate;
        private final long events;
        private final long calibratedEvents;
        private final String queryShape;

        private Snapshot(SemanticPlanEstimate estimate,
                         long events,
                         long calibratedEvents,
                         String queryShape) {
            this.estimate = estimate;
            this.events = events;
            this.calibratedEvents = calibratedEvents;
            this.queryShape = queryShape;
        }

        public SemanticPlanEstimate getEstimate() { return estimate; }
        public long getEvents() { return events; }
        public long getCalibratedEvents() { return calibratedEvents; }
        public String getQueryShape() { return queryShape; }
    }

    private static final class Session {
        private SemanticPlanEstimate estimate;
        private SemanticPlanEstimate fallback;
        private String queryShape;
        private long events;
        private long calibratedEvents;
    }
}
