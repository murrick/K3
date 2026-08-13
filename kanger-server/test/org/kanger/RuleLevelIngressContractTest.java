/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
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

class RuleLevelIngressContractTest {

    @Test
    void bareRuleLevelRemainsCanonicalForAggregateRuntime() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject response = (JSONObject) reactor.run(dialogue("token-1", "r l"));

        assertEquals("OK", response.optString("result"));
        assertEquals("RULE_LEVEL", response.optString(
                CanonicalCommandIngressReactor.CANONICAL_INTENT_FIELD));
        assertEquals(1, capture.calls.get());
        assertEquals("canonical", context(capture.packet.get()));
        assertFalse(parameters(capture.packet.get()).has("level"));
        CommandInvocation invocation = CanonicalCommandIngressReactor.invocation(
                capture.packet.get());
        assertNotNull(invocation);
        assertEquals(CommandIntent.RULE_LEVEL, invocation.getIntent());
        assertFalse(invocation.getArguments().containsKey("level"));
    }

    @Test
    void numberedRuleLevelKeepsQualifiedLegacyReadPath() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject response = (JSONObject) reactor.run(dialogue("token-1", "rule level 2"));

        assertEquals("OK", response.optString("result"));
        assertEquals(1, capture.calls.get());
        assertEquals("query", context(capture.packet.get()));
        JSONObject parameters = parameters(capture.packet.get());
        assertEquals("", parameters.optString("rules"));
        assertEquals(2L, parameters.getLong("level"));
    }

    private static JSONObject dialogue(String token, String line) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "dialogue")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("line", line)));
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
