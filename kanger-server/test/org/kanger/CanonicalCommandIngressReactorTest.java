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
import org.kanger.interfaces.IUser;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
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

        JSONObject response = (JSONObject) reactor.run(dialogue("token-1", "ru 17"));

        assertEquals("OK", response.optString("result"));
        assertEquals("RULE_SHOW", response.optString(
                CanonicalCommandIngressReactor.CANONICAL_INTENT_FIELD));
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
    void bareDeleteIsReadOnlySourceDiscovery() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject response = (JSONObject) reactor.run(dialogue("token-1", "delete"));

        assertEquals(1, capture.calls.get());
        assertEquals("command", context(capture.packet.get()));
        JSONObject parameters = parameters(capture.packet.get());
        assertTrue(parameters.has("get"));
        assertFalse(parameters.has("delete"));
        assertEquals("SOURCE_DELETE", response.optString(
                CanonicalCommandIngressReactor.CANONICAL_INTENT_FIELD));
        JSONObject choices = response.getJSONObject(
                CanonicalCommandIngressReactor.DIALOGUE_CHOICES_FIELD);
        assertEquals("delete", choices.getString("compose"));
    }

    @Test
    void namedDeleteRequiresExplicitConfirmationBeforeRuntime() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject first = (JSONObject) reactor.run(
                dialogue("token-1", "delete victim.k"));
        assertEquals("confirmation_required", first.optString("result"));
        assertEquals(0, capture.calls.get());
        assertTrue(first.getJSONObject(
                CanonicalCommandIngressReactor.CONFIRMATION_FIELD)
                .getString("prompt").contains("victim.k"));

        JSONObject second = (JSONObject) reactor.run(
                confirmedDialogue("token-1", "delete victim.k"));
        assertEquals("OK", second.optString("result"));
        assertEquals(1, capture.calls.get());
        assertEquals("victim.k", parameters(capture.packet.get())
                .optString("delete"));
    }

    @Test
    void existingLogicalSourcePutRequiresConfirmationWithoutExplicitExtension()
            throws Exception {
        String identity = "put-confirm-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        Path source = null;
        try {
            token = UserFactory.addUser(user);
            source = Paths.get(user.getSourceDir()).resolve("victim.k");
            Files.createDirectories(source.getParent());
            Files.write(source, "!victim(one);\n".getBytes(StandardCharsets.UTF_8));

            Capture capture = new Capture();
            CanonicalCommandIngressReactor reactor =
                    new CanonicalCommandIngressReactor(capture);

            JSONObject first = (JSONObject) reactor.run(
                    dialogue(token, "put victim"));

            assertEquals("confirmation_required", first.optString("result"));
            assertEquals(0, capture.calls.get());
            assertTrue(first.getJSONObject(
                    CanonicalCommandIngressReactor.CONFIRMATION_FIELD)
                    .getString("prompt").contains("victim.k"));

            JSONObject second = (JSONObject) reactor.run(
                    confirmedDialogue(token, "put victim"));
            assertEquals("OK", second.optString("result"));
            assertEquals(1, capture.calls.get());
            assertEquals("victim.k", parameters(capture.packet.get())
                    .optString("put"));
        } finally {
            if (source != null) {
                Files.deleteIfExists(source);
            }
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    @Test
    void eraseRequiresConfirmationButRollbackStaysCanonical() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject erase = (JSONObject) reactor.run(dialogue("token-1", "erase"));
        assertEquals("confirmation_required", erase.optString("result"));
        assertEquals(0, capture.calls.get());

        JSONObject rollback = (JSONObject) reactor.run(
                dialogue("token-1", "transaction rollback"));
        assertEquals("OK", rollback.optString("result"));
        assertEquals(1, capture.calls.get());
        assertEquals("canonical", context(capture.packet.get()));
        assertFalse(parameters(capture.packet.get()).has("transaction"));
        CommandInvocation invocation = CanonicalCommandIngressReactor.invocation(
                capture.packet.get());
        assertNotNull(invocation);
        assertEquals(CommandIntent.TX_ROLLBACK, invocation.getIntent());
        assertEquals("TX_ROLLBACK", rollback.optString(
                CanonicalCommandIngressReactor.CANONICAL_INTENT_FIELD));
    }

    @Test
    void storageKeywordLookingNameRemainsData() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        reactor.run(dialogue("token-1", "sto u close"));

        assertEquals("canonical", context(capture.packet.get()));
        JSONObject parameters = parameters(capture.packet.get());
        assertFalse(parameters.has("use"));
        assertFalse(parameters.has("close"));
        CommandInvocation invocation = CanonicalCommandIngressReactor.invocation(
                capture.packet.get());
        assertNotNull(invocation);
        assertEquals(CommandIntent.STORAGE_USE, invocation.getIntent());
        assertEquals("close", invocation.getArgument("name"));
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
    void doubleStatementPrefixIsRejectedBeforeCoreBypass() throws Exception {
        Capture capture = new Capture();
        CanonicalErrorBoundaryReactor reactor = boundary(capture);

        JSONObject response = (JSONObject) reactor.run(
                dialogue("token-1", "!!eating(Cat, Mouse);"));

        assertEquals("error", response.optString("result"));
        assertEquals("command_parse_error", response.optString("code"));
        assertEquals("INVALID_GRAMMAR", response.optString("reason"));
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals("application", diagnostic.getString("domain"));
        assertEquals("command_parse_error", diagnostic.getString("code"));
        assertEquals(0, capture.calls.get());
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
    void successfulCanonicalQuitExposesTransportLevelSessionClosure() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);

        JSONObject response = (JSONObject) reactor.run(dialogue("token-1", "q"));

        assertEquals("command", context(capture.packet.get()));
        assertTrue(parameters(capture.packet.get()).has("quit"));
        assertEquals("OK", response.optString("result"));
        JSONObject session = response.getJSONObject(
                CanonicalCommandIngressReactor.SESSION_STATE_FIELD);
        assertEquals(1, session.getInt("schema"));
        assertEquals(CanonicalCommandIngressReactor.SESSION_CLOSED_STATE,
                session.getString("state"));
    }

    @Test
    void parserRejectionDoesNotReachRuntimeDelegate() throws Exception {
        Capture capture = new Capture();
        CanonicalErrorBoundaryReactor reactor = boundary(capture);

        JSONObject response = (JSONObject) reactor.run(dialogue("token-1", "s"));

        assertEquals("error", response.optString("result"));
        assertEquals("command_parse_error", response.optString("code"));
        assertEquals("AMBIGUOUS_PREFIX", response.optString("reason"));
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals("application", diagnostic.getString("domain"));
        assertEquals("command_parse_error", diagnostic.getString("code"));
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

    @Test
    void rawDialogueConfirmationBitMustBeBoolean() throws Exception {
        Capture capture = new Capture();
        CanonicalCommandIngressReactor reactor = new CanonicalCommandIngressReactor(capture);
        JSONObject packet = dialogue("token-1", "erase");
        parameters(packet).put("confirmed", "yes");

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.optString("result"));
        assertEquals("dialogue_envelope_invalid", response.optString("code"));
        assertEquals(0, capture.calls.get());
    }

    private static CanonicalErrorBoundaryReactor boundary(Capture capture) {
        return new CanonicalErrorBoundaryReactor(
                new CanonicalCommandIngressReactor(capture));
    }

    private static JSONObject dialogue(String token, String line) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "dialogue")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("line", line)));
    }

    private static JSONObject confirmedDialogue(String token, String line) {
        JSONObject packet = dialogue(token, line);
        parameters(packet).put("confirmed", true);
        return packet;
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
