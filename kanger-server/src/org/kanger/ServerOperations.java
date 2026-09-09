/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;

/**
 * Process-local operational state exposed through the authenticated admin plane
 * and, in reduced form, through the public maintenance polling endpoint.
 *
 * <p>A maintenance notice is deliberately informational. Scheduling a notice
 * does not stop, drain, commit or otherwise mutate user runtime state. The host
 * operator remains responsible for the actual server shutdown/update.</p>
 */
public final class ServerOperations {

    private static final Object MONITOR = new Object();
    private static final long STARTED_AT_MILLIS = System.currentTimeMillis();

    private static long maintenanceDeadlineMillis;

    private ServerOperations() {
    }

    /** Returns the authenticated host-operator status snapshot. */
    public static JSONObject status() {
        JSONObject maintenance = maintenanceSnapshot();
        return new JSONObject()
                .put("result", "OK")
                .put("status", "UP")
                .put("core_version", Version.CORE_VERSION_S)
                .put("server_version", Version.SERVER_VERSION_S)
                .put("uptime_millis", Math.max(0L,
                        System.currentTimeMillis() - STARTED_AT_MILLIS))
                .put("active_sessions", UserFactory.activeSessionCount())
                .put("maintenance", maintenance);
    }

    /** Returns only the state intentionally exposed to ordinary browser clients. */
    public static JSONObject publicMaintenance() {
        return new JSONObject()
                .put("result", "OK")
                .put("maintenance", maintenanceSnapshot());
    }

    /**
     * Publishes a maintenance deadline. This does not initiate shutdown.
     *
     * @param deadlineMillis absolute UTC epoch milliseconds, strictly in future
     */
    public static JSONObject scheduleMaintenance(long deadlineMillis) {
        long now = System.currentTimeMillis();
        if (deadlineMillis <= now) {
            throw new IllegalArgumentException(
                    "maintenance deadline must be in the future");
        }
        synchronized (MONITOR) {
            maintenanceDeadlineMillis = deadlineMillis;
        }
        Watchdog.warn("Server maintenance notice scheduled for epoch_ms="
                + deadlineMillis);
        return new JSONObject()
                .put("result", "OK")
                .put("maintenance", maintenanceSnapshot());
    }

    /** Clears the published maintenance notice. */
    public static JSONObject clearMaintenance() {
        synchronized (MONITOR) {
            maintenanceDeadlineMillis = 0L;
        }
        Watchdog.log("Server maintenance notice cleared");
        return new JSONObject()
                .put("result", "OK")
                .put("maintenance", maintenanceSnapshot());
    }

    static JSONObject maintenanceSnapshot() {
        long deadline;
        synchronized (MONITOR) {
            deadline = maintenanceDeadlineMillis;
        }
        JSONObject result = new JSONObject()
                .put("active", deadline > 0L);
        if (deadline > 0L) {
            result.put("deadline_epoch_millis", deadline);
            result.put("remaining_millis",
                    Math.max(0L, deadline - System.currentTimeMillis()));
        }
        return result;
    }
}
