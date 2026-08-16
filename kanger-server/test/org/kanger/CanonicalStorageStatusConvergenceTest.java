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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of canonical read-only storage-status convergence. */
class CanonicalStorageStatusConvergenceTest {

    @Test
    void sharedProcessorProjectsAvailableAndCurrentStorageWithoutMutation()
            throws Exception {
        Fixture fixture = fixture("processor");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();

            CanonicalCommandProcessor.Result empty = processor.execute(
                    parser.parse("storage"), fixture.user);
            assertTrue(empty.isHandled());
            assertTrue(empty.isSuccess());
            assertSame(fixture.root, empty.getMind());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertNotNull(empty.getStorageStatus());
            assertFalse(empty.getStorageStatus().isUsed());
            assertEquals(0, fixture.root.getTransactionLevel());

            IMind opened = fixture.root.useStorage(
                    "canonical-storage-status-" + UUID.randomUUID());
            fixture.user.setCurrentMind(opened);

            CanonicalCommandProcessor.Result active = processor.execute(
                    parser.parse("storage"), fixture.user);
            assertTrue(active.isHandled());
            assertTrue(active.isSuccess());
            assertSame(opened, active.getMind());
            assertSame(opened, fixture.user.getCurrentMind());
            assertTrue(active.getStorageStatus().isUsed());
            assertEquals(opened.getStorageName(),
                    active.getStorageStatus().getCurrent());
            assertTrue(active.getStorageStatus().getNames().contains(
                    opened.getStorageName()));
            assertEquals(0, opened.getTransactionLevel());
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserStorageStatusExecutesCanonicalProcessorAndNeverLegacyUseProtocol()
            throws Exception {
        Fixture fixture = fixture("browser");
        try {
            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
                @Override
                public Object run(JSONObject packet) {
                    escaped.incrementAndGet();
                    throw new AssertionError(
                            "Canonical storage status escaped into legacy runtime");
                }
            };
            IReactor<JSONObject> reactor = new CanonicalCommandIngressReactor(
                    new WorkspaceStateReactor(
                            new CanonicalCommandRuntimeReactor(legacy)));

            JSONObject response = invoke(reactor, fixture.token, "storage");

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals("STORAGE_STATUS",
                    response.optString("canonical_intent"));
            assertEquals(0, escaped.get(),
                    "Canonical storage status touched legacy command use protocol");
            assertTrue(response.has("list"));
            assertTrue(response.has("dialogue_choices"));
            JSONObject status = response.getJSONObject("storage");
            assertEquals(1, status.getInt("schema"));
            assertFalse(status.getBoolean("used"));
            assertEquals(0, response.optInt("transaction", -1));
            assertSame(fixture.root, fixture.user.getCurrentMind());
        } finally {
            fixture.close();
        }
    }

    @Test
    void javaConsoleStorageStatusConsumesSharedReadModel() throws Exception {
        String source = source(
                "kanger-console/src/org/kanger/CanonicalConsole.java");

        assertTrue(source.contains("case STORAGE_STATUS:"));
        assertTrue(source.contains(
                "CanonicalCommandProcessor.Result storage ="));
        assertTrue(source.contains(
                "COMMAND_PROCESSOR.execute(invocation, mind.getUser())"));
        assertTrue(source.contains(
                "showStorage(storage.getStorageStatus())"));
        assertFalse(source.contains(
                "case STORAGE_STATUS:\n                showStorage(mind);"),
                "Console retained an independent canonical storage-status query path");
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
        String identity = "canonical-storage-status-" + purpose + "-"
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
