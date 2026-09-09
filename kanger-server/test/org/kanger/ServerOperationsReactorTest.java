package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IReactor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerOperationsReactorTest {

    @AfterEach
    void clearNotice() {
        ServerOperations.clearMaintenance();
    }

    @Test
    void maintenanceContextReturnsPublicSnapshotWithoutInvokingDelegate()
            throws Exception {
        AtomicInteger delegateCalls = new AtomicInteger();
        ServerOperationsReactor reactor = new ServerOperationsReactor(
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        delegateCalls.incrementAndGet();
                        return new JSONObject().put("delegate", true);
                    }
                });
        long deadline = System.currentTimeMillis() + 60_000L;
        ServerOperations.scheduleMaintenance(deadline);

        JSONObject packet = new JSONObject().put("query", new JSONObject()
                .put("context", "maintenance")
                .put("parameters", new JSONObject()));
        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals(0, delegateCalls.get());
        JSONObject maintenance = response.getJSONObject("maintenance");
        assertTrue(maintenance.getBoolean("active"));
        assertEquals(deadline, maintenance.getLong("deadline_epoch_millis"));
        assertFalse(response.has("active_sessions"));
    }

    @Test
    void unrelatedContextIsDelegatedUnchanged() throws Exception {
        ServerOperationsReactor reactor = new ServerOperationsReactor(
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        return new JSONObject().put("delegate", true);
                    }
                });

        JSONObject packet = new JSONObject().put("query", new JSONObject()
                .put("context", "health")
                .put("parameters", new JSONObject()));
        JSONObject response = (JSONObject) reactor.run(packet);

        assertTrue(response.getBoolean("delegate"));
    }
}
