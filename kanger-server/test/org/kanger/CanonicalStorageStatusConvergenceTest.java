/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.enums.Enums;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of canonical storage status/use convergence. */
class CanonicalStorageStatusConvergenceTest {

    @Test
    void sharedProcessorProjectsAvailableAndCurrentStorageWithoutMutation()
            throws Exception {
        Fixture fixture = fixture("processor-status");
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
    void sharedProcessorOwnsStorageUseNameMappingAndSameStorageRejection()
            throws Exception {
        Fixture fixture = fixture("processor-use");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String logical = "canonical.use." + UUID.randomUUID();
            String physical = logical.replace(".", Enums.FILE_SEPARATOR);

            CanonicalCommandProcessor.Result opened = processor.execute(
                    parser.parse("storage use " + logical), fixture.user);

            assertTrue(opened.isHandled());
            assertTrue(opened.isSuccess());
            assertSame(opened.getMind(), fixture.user.getCurrentMind());
            assertTrue(opened.getStorageStatus().isUsed());
            assertEquals(physical, opened.getStorageStatus().getCurrent());
            assertEquals(physical, opened.getMind().getStorageName());

            StorageLifecycleException duplicate = assertThrows(
                    StorageLifecycleException.class,
                    () -> processor.execute(
                            parser.parse("storage use " + logical), fixture.user));
            assertEquals(StorageLifecycleErrorCode.STORAGE_ALREADY_OPEN.name(),
                    duplicate.getCode());
            assertEquals("EXPLICIT_CLOSE_REQUIRED", duplicate.getRequiredAction());
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserStorageStatusExecutesCanonicalProcessorAndNeverLegacyUseProtocol()
            throws Exception {
        Fixture fixture = fixture("browser-status");
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
    void browserStorageUseExecutesCanonicalProcessorAndNeverLegacyUseProtocol()
            throws Exception {
        Fixture fixture = fixture("browser-use");
        try {
            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
                @Override
                public Object run(JSONObject packet) {
                    escaped.incrementAndGet();
                    throw new AssertionError(
                            "Canonical storage use escaped into legacy runtime");
                }
            };
            IReactor<JSONObject> reactor = new CanonicalCommandIngressReactor(
                    new WorkspaceStateReactor(
                            new CanonicalCommandRuntimeReactor(legacy)));
            String logical = "browser.use." + UUID.randomUUID();
            String physical = logical.replace(".", Enums.FILE_SEPARATOR);

            JSONObject response = invoke(
                    reactor, fixture.token, "storage use " + logical);

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals("STORAGE_USE", response.optString("canonical_intent"));
            assertEquals(0, escaped.get(),
                    "Canonical storage use touched legacy command use protocol");
            assertEquals(physical, response.optString("name"));
            JSONObject status = response.getJSONObject("storage");
            assertTrue(status.getBoolean("used"));
            assertEquals(physical, status.getString("current"));
            assertEquals(physical, fixture.user.getCurrentMind().getStorageName());

            JSONObject duplicate = invoke(
                    reactor, fixture.token, "storage use " + logical);
            assertEquals("error", duplicate.optString("result"), duplicate.toString());
            assertEquals(StorageLifecycleErrorCode.STORAGE_ALREADY_OPEN.name(),
                    duplicate.optString("code"));
            assertEquals("EXPLICIT_CLOSE_REQUIRED",
                    duplicate.optString("required_action"));
            assertEquals(0, escaped.get(),
                    "Rejected canonical storage use escaped into legacy runtime");
        } finally {
            fixture.close();
        }
    }

    @Test
    void javaConsoleStorageStatusAndUseConsumeSharedProcessor() throws Exception {
        String source = source(
                "kanger-console/src/org/kanger/CanonicalConsole.java");

        assertTrue(source.contains("case STORAGE_STATUS:"));
        assertTrue(source.contains("case STORAGE_USE:"));
        assertTrue(source.contains(
                "CanonicalCommandProcessor.Result storage ="));
        assertTrue(source.contains(
                "COMMAND_PROCESSOR.execute(invocation, mind.getUser())"));
        assertTrue(source.contains(
                "showStorage(storage.getStorageStatus())"));
        assertFalse(source.contains(
                "case STORAGE_STATUS:\n                showStorage(mind);"),
                "Console retained an independent canonical storage-status query path");
        assertFalse(source.contains(
                "mind = useStorage(mind, String.valueOf(invocation.getArgument(\"name\")))"),
                "Console retained an independent canonical storage-use semantic path");
        assertFalse(source.contains("private static IMind useStorage("),
                "Console retained its storage-use semantic helper");
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
