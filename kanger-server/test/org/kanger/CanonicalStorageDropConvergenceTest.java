/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of canonical storage-drop convergence. */
class CanonicalStorageDropConvergenceTest {

    @Test
    void sharedProcessorOwnsTruthfulActiveStorageDrop() throws Exception {
        Fixture fixture = fixture("processor-active-drop");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String logical = "canonical.drop." + UUID.randomUUID();

            CanonicalCommandProcessor.Result opened = processor.execute(
                    parser.parse("storage use " + logical), fixture.user);
            IMind active = opened.getMind();
            assertTrue(Boolean.TRUE.equals(active.query("!canonical_drop_fact;")));

            CanonicalCommandProcessor.Result dropped = processor.execute(
                    parser.parse("storage drop " + logical), fixture.user);

            assertTrue(dropped.isHandled(),
                    "Storage drop is still outside the shared command processor");
            assertTrue(dropped.isSuccess());
            assertSame(dropped.getMind(), fixture.user.getCurrentMind());
            assertFalse(dropped.getMind().isStorageUsed());
            assertFalse(dropped.getStorageStatus().isUsed());
            assertFalse(dropped.getStorageStatus().getNames().contains(
                    logical.replace(".", org.kanger.enums.Enums.FILE_SEPARATOR)),
                    "Dropped storage is still advertised by canonical status");
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserConfirmedDropUsesSharedCoreWithoutLegacyEscape() throws Exception {
        Fixture fixture = fixture("browser-drop");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            String logical = "browser.drop." + UUID.randomUUID();
            processor.execute(new CommandParser().parse("storage use " + logical),
                    fixture.user);
            assertTrue(Boolean.TRUE.equals(
                    fixture.user.getCurrentMind().query("!browser_drop_fact;")));

            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> reactor = canonicalReactor(escaped);

            JSONObject confirmation = invoke(
                    reactor, fixture.token, "storage drop " + logical, false);
            assertEquals("confirmation_required",
                    confirmation.optString("result"), confirmation.toString());
            assertEquals(0, escaped.get(),
                    "Unconfirmed drop reached runtime");
            assertTrue(fixture.user.getCurrentMind().isStorageUsed());

            JSONObject dropped = invoke(
                    reactor, fixture.token, "storage drop " + logical, true);

            assertEquals("OK", dropped.optString("result"), dropped.toString());
            assertEquals("STORAGE_DROP", dropped.optString("canonical_intent"));
            assertEquals(0, escaped.get(),
                    "Canonical storage drop escaped into legacy drop protocol");
            assertFalse(dropped.getJSONObject("storage").getBoolean("used"));
            assertFalse(fixture.user.getCurrentMind().isStorageUsed());
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserMissingDropExposesTypedCoreLifecycleError() throws Exception {
        Fixture fixture = fixture("browser-missing-drop");
        try {
            AtomicInteger escaped = new AtomicInteger();
            JSONObject response = invoke(
                    canonicalReactor(escaped), fixture.token,
                    "storage drop definitely.missing." + UUID.randomUUID(), true);

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals("STORAGE_NOT_FOUND", response.optString("code"));
            assertEquals(0, escaped.get(),
                    "Missing canonical drop escaped into legacy drop protocol");
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertFalse(fixture.root.isStorageUsed());
        } finally {
            fixture.close();
        }
    }

    @Test
    void adaptersContainNoIndependentCanonicalDropSemanticPath() throws Exception {
        String console = source("kanger-console/src/org/kanger/CanonicalConsole.java");
        String ingress = source(
                "kanger-server/src/org/kanger/CanonicalCommandIngressReactor.java");

        assertTrue(console.contains("case STORAGE_DROP:"));
        assertTrue(console.contains(
                "COMMAND_PROCESSOR.execute(invocation, mind.getUser())"));
        assertFalse(console.contains("private static IMind dropStorage("),
                "Console retained an independent storage-drop semantic helper");
        assertFalse(ingress.contains(
                "case STORAGE_DROP:\n                command(envelope, \"drop\", string(invocation, \"name\"));"),
                "Browser canonical drop still translates into legacy drop protocol");
    }

    private IReactor<JSONObject> canonicalReactor(final AtomicInteger escaped) {
        IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                escaped.incrementAndGet();
                throw new AssertionError(
                        "Canonical storage drop escaped into legacy runtime");
            }
        };
        return new CanonicalCommandIngressReactor(
                new WorkspaceStateReactor(
                        new CanonicalCommandRuntimeReactor(legacy)));
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String line,
                              boolean confirmed) throws Exception {
        JSONObject parameters = new JSONObject()
                .put("token", token)
                .put("line", line);
        if (confirmed) {
            parameters.put("confirmed", true);
        }
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "dialogue")
                .put("parameters", parameters));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject, "Response is not JSON: " + response);
        return (JSONObject) response;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "canonical-storage-drop-" + purpose + "-"
                + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, token, root);
    }

    private String source(String relative) throws Exception {
        Path[] candidates = new Path[] {
                Paths.get("..", relative),
                Paths.get(relative)
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Source file not found: " + relative);
    }

    private static final class Fixture {
        private final IUser user;
        private final String token;
        private final Mind root;

        private Fixture(IUser user, String token, Mind root) {
            this.user = user;
            this.token = token;
            this.root = root;
        }

        private void close() throws Exception {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Isolated test session may already be closed by a failed request path.
            }
        }
    }
}
