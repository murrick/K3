/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Opt-in query-local capture of pre-execution semantic estimates.
 *
 * Normal execution pays only a ThreadLocal null check. The first calibrated
 * estimate is retained; repeated observations are counted so experiments can
 * detect unexpected planning re-entry.
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

    public static void record(SemanticPlanEstimate estimate) {
        Session session = CURRENT.get();
        if (session == null || estimate == null) {
            return;
        }
        ++session.events;
        if (estimate.isCalibrated()) {
            ++session.calibratedEvents;
            if (session.estimate == null) {
                session.estimate = estimate;
            }
        } else if (session.estimate == null) {
            session.fallback = estimate;
        }
    }

    public static Snapshot end() {
        Session session = CURRENT.get();
        CURRENT.remove();
        if (session == null) {
            return new Snapshot(null, 0L, 0L);
        }
        SemanticPlanEstimate estimate = session.estimate == null
                ? session.fallback : session.estimate;
        return new Snapshot(estimate, session.events,
                session.calibratedEvents);
    }

    public static final class Snapshot {
        private final SemanticPlanEstimate estimate;
        private final long events;
        private final long calibratedEvents;

        private Snapshot(SemanticPlanEstimate estimate,
                         long events,
                         long calibratedEvents) {
            this.estimate = estimate;
            this.events = events;
            this.calibratedEvents = calibratedEvents;
        }

        public SemanticPlanEstimate getEstimate() { return estimate; }
        public long getEvents() { return events; }
        public long getCalibratedEvents() { return calibratedEvents; }
    }

    private static final class Session {
        private SemanticPlanEstimate estimate;
        private SemanticPlanEstimate fallback;
        private long events;
        private long calibratedEvents;
    }
}
