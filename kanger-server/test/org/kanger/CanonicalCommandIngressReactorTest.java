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

import java.net.URLDecoder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalCommandIngressReactorTest {

    @Test
    void abbreviatedRuleLookupProjectsToQualifiedLegacyQuery() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject response = (JSONObject) reactor.run(dialogue("token-1", "r 17"));

        assertEquals("OK", response.optString("result"));
        assertEquals(1, capture.calls.get());
        assertEquals("query", context(capture.packet.get()));
        JSONObject parameters = parameters(capture.packet.get());
        assertEquals("token-1", parameters.optString("token"));
        assertEquals("", parameters.optString("rules"));
        assertEquals(17L, parameters.getLong("id"));
        assertFalse(parameters.has("line"));
        CommandInvocation invocation = CanonicalCommandIngressReactor.invocation(
                capture.packet.get());
        assertNotNull(invocation);
        assertEquals(CommandIntent.RULE_SHOW, invocation.getIntent());
    }

    @Test
    void sourceOperationIsValidatedAfterCanonicalTranslation() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject response = (JSONObject) reactor.run(
                dialogue("token-1", "get ../secret.k"));

        assertEquals("error", response.optString("result"));
        assertTrue(response.optString("description").contains("Invalid filesystem identifier"));
        assertEquals(0, capture.calls.get(),
                "Unsafe canonical source reached the legacy delegate");
    }

    @Test
    void storageKeywordLookingNameRemainsData() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        reactor.run(dialogue("token-1", "st u close"));

        assertEquals("command", context(capture.packet.get()));
        JSONObject parameters = parameters(capture.packet.get());
        assertEquals("close", parameters.optString("use"));
        assertFalse(parameters.has("close"));
    }

    @Test
    void coreLanguageBypassesCommandDispatchAndUsesExistingQueryPath() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        reactor.run(dialogue("token-1", "?father(John,Tom)"));

        assertEquals("query", context(capture.packet.get()));
        String encoded = parameters(capture.packet.get()).getString("request");
        assertEquals("?father(John,Tom)", URLDecoder.decode(encoded, "UTF-8"));
        assertTrue(CanonicalCommandIngressReactor.invocation(
                capture.packet.get()).isCoreLanguage());
    }

    @Test
    void canonicalOnlyIntentIsNotApproximatedThroughLegacyTransport() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        reactor.run(dialogue("token-1", "solution 42"));

        assertEquals("canonical", context(capture.packet.get()));
        CommandInvocation invocation = CanonicalCommandIngressReactor.invocation(
                capture.packet.get());
        assertEquals(CommandIntent.SOLUTION_SHOW, invocation.getIntent());
        assertFalse(parameters(capture.packet.get()).has("solutions"));
        assertFalse(parameters(capture.packet.get()).has("statements"));
    }

    @Test
    void parserRejectionDoesNotReachRuntimeDelegate() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject response = (JSONObject) reactor.run(dialogue("token-1", "s"));

        assertEquals("error", response.optString("result"));
        assertEquals("command_parse_error", response.optString("code"));
        assertEquals("AMBIGUOUS_PREFIX", response.optString("reason"));
        assertEquals(0, capture.calls.get());
    }

    @Test
    void rawDialogueEnvelopeRejectsLegacyOperationInjection() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);
        JSONObject packet = dialogue("token-1", "help");
        parameters(packet).put("delete", "victim.k");

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.optString("result"));
        assertEquals("dialogue_envelope_invalid", response.optString("code"));
        assertEquals(0, capture.calls.get());
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
