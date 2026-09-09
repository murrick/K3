package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerOperationsTest {

    @AfterEach
    void clearNotice() {
        ServerOperations.clearMaintenance();
    }

    @Test
    void maintenanceNoticeIsPureDeadlineState() {
        long deadline = System.currentTimeMillis() + 60_000L;

        JSONObject scheduled = ServerOperations.scheduleMaintenance(deadline);
        JSONObject maintenance = scheduled.getJSONObject("maintenance");

        assertEquals("OK", scheduled.getString("result"));
        assertTrue(maintenance.getBoolean("active"));
        assertEquals(deadline, maintenance.getLong("deadline_epoch_millis"));
        assertTrue(maintenance.getLong("remaining_millis") >= 0L);

        JSONObject cleared = ServerOperations.clearMaintenance()
                .getJSONObject("maintenance");
        assertFalse(cleared.getBoolean("active"));
        assertFalse(cleared.has("deadline_epoch_millis"));
    }

    @Test
    void maintenanceDeadlineMustBeFuture() {
        assertThrows(IllegalArgumentException.class,
                () -> ServerOperations.scheduleMaintenance(
                        System.currentTimeMillis() - 1L));
    }

    @Test
    void publicSnapshotContainsNoOperatorStatus() {
        JSONObject response = ServerOperations.publicMaintenance();

        assertEquals("OK", response.getString("result"));
        assertTrue(response.has("maintenance"));
        assertFalse(response.has("active_sessions"));
        assertFalse(response.has("uptime_millis"));
    }
}
