/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IReactor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawLegacyFilesystemInputBoundaryTest {

    @Test
    void unsafeLegacyFilesystemIdentifiersAreRejectedBeforeDelegate() throws Exception {
        String[] sourceParameters = {"get", "put", "delete"};
        for (String parameter : sourceParameters) {
            assertRejected(parameter, "../../outside.k");
        }

        String[] storageParameters = {"use", "drop", "reindex"};
        for (String parameter : storageParameters) {
            assertRejected(parameter, "../../outside");
        }
    }

    @Test
    void safeLegacyFilesystemIdentifierStillDelegates() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor =
                new CanonicalCommandIngressReactor(capture);

        JSONObject packet = command("get", "mind.k");
        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("OK", response.optString("result"));
        assertEquals(1, capture.calls.get());
        assertEquals("mind.k", parameters(packet).optString("get"));
    }

    private static void assertRejected(String parameter, String attackerValue)
            throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor =
                new CanonicalCommandIngressReactor(capture);

        JSONObject response = (JSONObject) reactor.run(
                command(parameter, attackerValue));

        assertEquals("error", response.optString("result"));
        assertTrue(response.optString("description")
                .contains("Invalid filesystem identifier"));
        assertTrue(response.optString("description").contains(parameter));
        assertFalse(response.toString().contains(attackerValue));
        assertEquals(0, capture.calls.get(),
                "Unsafe raw legacy identifier reached the delegate: " + parameter);
    }

    private static JSONObject command(String name, String value) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", "token-1")
                        .put(name, value)));
    }

    private static JSONObject parameters(JSONObject packet) {
        return packet.getJSONObject("body").getJSONObject("parameters");
    }

    private static final class Capture implements IReactor<JSONObject> {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Object run(JSONObject request) {
            calls.incrementAndGet();
            return new JSONObject().put("result", "OK");
        }
    }
}
