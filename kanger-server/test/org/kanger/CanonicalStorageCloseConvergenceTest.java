/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.StorageLifecycleException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of canonical storage-close convergence. */
class CanonicalStorageCloseConvergenceTest {

    @Test
    void sharedProcessorKeepsNoStorageCloseIdempotent() throws Exception {
        Fixture fixture = fixture("processor-empty-close");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CanonicalCommandProcessor.Result closed = processor.execute(
                    new CommandParser().parse("storage close"), fixture.user);

            assertTrue(closed.isHandled());
            assertTrue(closed.isSuccess());
            assertSame(fixture.root, closed.getMind());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertFalse(closed.getStorageStatus().isUsed());
            assertEquals("No database used", closed.getDescription());
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserCloseUsesSharedCoreAuthorityAndPersistsRootBeforeDetach()
            throws Exception {
        Fixture fixture = fixture("browser-close-persist");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String logical = "canonical.close." + UUID.randomUUID();

            CanonicalCommandProcessor.Result opened = processor.execute(
                    parser.parse("storage use " + logical), fixture.user);
            IMind root = opened.getMind();
            assertTrue(Boolean.TRUE.equals(root.query("!canonical_close_persist;")));

            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> reactor = canonicalReactor(escaped);
            JSONObject response = invoke(reactor, fixture.token, "storage close");

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals("STORAGE_CLOSE", response.optString("canonical_intent"));
            assertEquals(0, escaped.get(),
                    "Canonical storage close escaped into legacy close protocol");
            assertFalse(response.getJSONObject("storage").getBoolean("used"));
            assertFalse(fixture.user.getCurrentMind().isStorageUsed());
            assertEquals(0, fixture.user.getCurrentMind().getTransactionLevel());

            CanonicalCommandProcessor.Result reopened = processor.execute(
                    parser.parse("storage use " + logical), fixture.user);
            assertTrue(Boolean.TRUE.equals(
                    reopened.getMind().query("?canonical_close_persist;")),
                    "Canonical close did not checkpoint root state before detach");
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserClosePreservesActiveTransactionWithoutLegacyEscape()
            throws Exception {
        Fixture fixture = fixture("browser-close-active");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            processor.execute(parser.parse("storage use close.active"), fixture.user);
            Mind child = new Mind(fixture.user.getCurrentMind());
            fixture.user.setCurrentMind(child);
            assertTrue(Boolean.TRUE.equals(child.query("!close_active_transient;")));

            AtomicInteger escaped = new AtomicInteger();
            JSONObject closed = invoke(
                    canonicalReactor(escaped), fixture.token, "storage close");

            assertEquals("OK", closed.optString("result"), closed.toString());
            assertEquals("STORAGE_CLOSE", closed.optString("canonical_intent"));
            assertEquals(0, escaped.get(),
                    "Canonical storage close escaped into legacy close protocol");
            assertFalse(closed.getJSONObject("storage").getBoolean("used"));
            assertEquals(1, closed.optInt("transaction", -1));
            IMind offlineChild = fixture.user.getCurrentMind();
            assertEquals(1, offlineChild.getTransactionLevel());
            assertFalse(offlineChild.isStorageUsed());
            assertTrue(Boolean.TRUE.equals(
                    offlineChild.query("?close_active_transient;")));

            CanonicalCommandProcessor.Result rolledBack = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);
            assertTrue(rolledBack.isSuccess());
            assertEquals(0, rolledBack.getMind().getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(
                    rolledBack.getMind().query("?close_active_transient;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void sharedProcessorPreservesActiveTransactionAcrossClose() throws Exception {
        Fixture fixture = fixture("processor-active-close");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            processor.execute(parser.parse("storage use processor.active"), fixture.user);
            Mind child = new Mind(fixture.user.getCurrentMind());
            fixture.user.setCurrentMind(child);
            assertTrue(Boolean.TRUE.equals(
                    child.query("!processor_close_transient;")));

            CanonicalCommandProcessor.Result closed = processor.execute(
                    parser.parse("storage close"), fixture.user);

            assertTrue(closed.isSuccess());
            assertEquals(1, closed.getMind().getTransactionLevel());
            assertFalse(closed.getMind().isStorageUsed());
            assertTrue(Boolean.TRUE.equals(
                    closed.getMind().query("?processor_close_transient;")));

            CanonicalCommandProcessor.Result rolledBack = processor.execute(
                    parser.parse("transaction rollback"), fixture.user);
            assertTrue(rolledBack.isSuccess());
            assertEquals(0, rolledBack.getMind().getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(
                    rolledBack.getMind().query("?processor_close_transient;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void consoleAndBrowserContainNoIndependentCanonicalCloseSemanticPath()
            throws Exception {
        String console = source("kanger-console/src/org/kanger/CanonicalConsole.java");
        String ingress = source(
                "kanger-server/src/org/kanger/CanonicalCommandIngressReactor.java");

        assertTrue(console.contains("case STORAGE_CLOSE:"));
        assertTrue(console.contains(
                "COMMAND_PROCESSOR.execute(invocation, mind.getUser())"));
        assertFalse(console.contains("private static IMind closeStorage("),
                "Console retained an independent storage-close semantic helper");
        assertFalse(ingress.contains(
                "case STORAGE_CLOSE:\n                command(envelope, \"close\", \"\");"),
                "Browser canonical close still translates into legacy close protocol");
    }

    private IReactor<JSONObject> canonicalReactor(final AtomicInteger escaped) {
        IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                escaped.incrementAndGet();
                throw new AssertionError(
                        "Canonical storage close escaped into legacy runtime");
            }
        };
        return new CanonicalCommandIngressReactor(
                new WorkspaceStateReactor(
                        new CanonicalCommandRuntimeReactor(legacy)));
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
        assertTrue(response instanceof JSONObject, "Response is not JSON: " + response);
        return (JSONObject) response;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "canonical-storage-close-" + purpose + "-"
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
