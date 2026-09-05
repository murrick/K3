/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandIntent;
import org.kanger.command.CommandInvocation;
import org.kanger.interfaces.IReactor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StructuredStatusIngressReactorTest {

    @Test
    void structuredStatusReachesCanonicalServerParser() throws Exception {
        Capture capture = new Capture();
        StructuredStatusIngressReactor reactor = new StructuredStatusIngressReactor(
                new CanonicalCommandIngressReactor(capture));

        JSONObject response = (JSONObject) reactor.run(status("token-1"));

        assertEquals("OK", response.optString("result"));
        assertEquals("STATUS", response.optString(
                CanonicalCommandIngressReactor.CANONICAL_INTENT_FIELD));
        assertEquals(1, capture.calls.get());
        assertEquals(CanonicalCommandIngressReactor.CANONICAL_CONTEXT,
                context(capture.packet.get()));
        JSONObject parameters = parameters(capture.packet.get());
        assertEquals("token-1", parameters.optString("token"));
        assertFalse(parameters.has(StructuredStatusIngressReactor.STATUS_PARAMETER));
        assertFalse(parameters.has(CanonicalCommandIngressReactor.LINE_PARAMETER));

        CommandInvocation invocation = CanonicalCommandIngressReactor.invocation(
                capture.packet.get());
        assertNotNull(invocation);
        assertEquals(CommandIntent.STATUS, invocation.getIntent());
    }

    @Test
    void structuredStatusRejectsEnvelopeInjection() throws Exception {
        Capture capture = new Capture();
        StructuredStatusIngressReactor reactor = new StructuredStatusIngressReactor(capture);
        JSONObject packet = status("token-1");
        parameters(packet).put("erase", "");

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.optString("result"));
        assertEquals("structured_status_invalid", response.optString("code"));
        assertEquals(0, capture.calls.get());
    }

    @Test
    void structuredStatusRequiresEmptyMarker() throws Exception {
        Capture capture = new Capture();
        StructuredStatusIngressReactor reactor = new StructuredStatusIngressReactor(capture);
        JSONObject packet = status("token-1");
        parameters(packet).put(StructuredStatusIngressReactor.STATUS_PARAMETER, "verbose");

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.optString("result"));
        assertEquals("structured_status_invalid", response.optString("code"));
        assertEquals(0, capture.calls.get());
    }

    private static JSONObject status(String token) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", StructuredStatusIngressReactor.COMMAND_CONTEXT)
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put(StructuredStatusIngressReactor.STATUS_PARAMETER, "")));
    }

    private static String context(JSONObject packet) {
        return packet.getJSONObject("body").getString("context");
    }

    private static JSONObject parameters(JSONObject packet) {
        return packet.getJSONObject("body").getJSONObject("parameters");
    }

    private static final class Capture implements IReactor<JSONObject> {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<JSONObject> packet = new AtomicReference<JSONObject>();

        @Override
        public Object run(JSONObject request) {
            calls.incrementAndGet();
            packet.set(request);
            return new JSONObject().put("result", "OK");
        }
    }
}
