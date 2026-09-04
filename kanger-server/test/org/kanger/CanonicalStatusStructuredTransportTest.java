/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalStatusStructuredTransportTest {

    @Test
    void browserStatusCarriesStructuredSnapshotWithoutLegacyEmptyTraversal()
            throws Exception {
        String identity = "canonical-status-structured-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);

        AtomicInteger escaped = new AtomicInteger();
        IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                escaped.incrementAndGet();
                throw new AssertionError(
                        "Canonical STATUS escaped into legacy runtime");
            }
        };
        IReactor<JSONObject> reactor = new CanonicalCommandIngressReactor(
                new WorkspaceStateReactor(
                        new CanonicalCommandRuntimeReactor(legacy)));

        try {
            JSONObject first = invoke(reactor, token, "status core transaction");
            assertEquals("OK", first.optString("result"), first.toString());
            assertFalse(first.has("empty"), first.toString());

            JSONObject status = first.getJSONObject("status");
            assertEquals(1, status.getInt("schema"));
            JSONObject transaction = status.getJSONObject("core")
                    .getJSONObject("transaction");
            assertEquals(0, transaction.getInt("level"));
            assertTrue(transaction.getBoolean("quiescent"));
            assertEquals(0, transaction.getInt("current_pending_children"));
            assertEquals(0, transaction.getInt("root_pending_children"));

            JSONObject levels = status.getJSONObject("core")
                    .getJSONObject("levels");
            assertEquals(root.getId(), levels.getLong("mind"));
            assertEquals(root.getId(), levels.getLong("root_mind"));

            JSONObject storage = status.getJSONObject("storage");
            assertEquals("closed", storage.getString("state"));
            assertEquals("DUMB data model", storage.getString("backend"));
            assertEquals(0L, storage.getLong("bases"));
            assertTrue(storage.isNull("records"));

            JSONObject session = status.getJSONObject("session");
            assertEquals(user.getId(), session.getLong("user"));
            assertEquals(root.getId(), session.getLong("mind"));

            JSONObject runtime = status.getJSONObject("runtime");
            assertEquals(Version.PRODUCT_VERSION_S, runtime.getString("version"));
            assertTrue(runtime.getLong("uptime_ms") >= 0L);
            assertTrue(runtime.getJSONObject("heap").getLong("used_bytes") >= 0L);

            JSONObject started = invoke(reactor, token, "transaction start");
            assertEquals("OK", started.optString("result"), started.toString());

            JSONObject nested = invoke(reactor, token, "status core transaction");
            assertEquals("OK", nested.optString("result"), nested.toString());
            assertFalse(nested.has("empty"), nested.toString());
            JSONObject nestedStatus = nested.getJSONObject("status");
            JSONObject nestedTransaction = nestedStatus.getJSONObject("core")
                    .getJSONObject("transaction");
            assertEquals(1, nestedTransaction.getInt("level"));
            assertFalse(nestedTransaction.getBoolean("quiescent"));
            assertEquals(0,
                    nestedTransaction.getInt("current_pending_children"));
            assertEquals(1, nestedTransaction.getInt("root_pending_children"));
            JSONObject nestedLevels = nestedStatus.getJSONObject("core")
                    .getJSONObject("levels");
            assertEquals(root.getId(), nestedLevels.getLong("root_mind"));
            assertTrue(nestedLevels.getLong("mind") != root.getId());

            JSONObject rolledBack = invoke(reactor, token, "transaction rollback");
            assertEquals("OK", rolledBack.optString("result"),
                    rolledBack.toString());
            assertEquals(0, escaped.get());
        } finally {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Test owns this isolated session.
            }
        }
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String line) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "dialogue")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("line", line)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "Response is not JSON: " + response);
        return (JSONObject) response;
    }
}
