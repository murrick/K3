/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParser;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of canonical storage-reindex convergence. */
class CanonicalStorageReindexConvergenceTest {

    @Test
    void sharedProcessorOwnsReindexAndExposesAdapterProgress() throws Exception {
        Fixture fixture = fixture("processor-progress");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String logical = "canonical.reindex." + UUID.randomUUID();

            CanonicalCommandProcessor.Result opened = processor.execute(
                    parser.parse("storage use " + logical), fixture.user);
            assertTrue(Boolean.TRUE.equals(
                    opened.getMind().query("!canonical_reindex_fact;")));

            final List<String> phases = new ArrayList<String>();
            CanonicalCommandProcessor.Result reindexed = executeWithProgress(
                    processor,
                    parser.parse("storage reindex " + logical),
                    fixture.user,
                    new IReactor<String>() {
                        @Override
                        public Object run(String item) {
                            phases.add(item);
                            return null;
                        }
                    });

            assertTrue(reindexed.isHandled(),
                    "Storage reindex is still outside the shared command processor");
            assertTrue(reindexed.isSuccess());
            assertSame(reindexed.getMind(), fixture.user.getCurrentMind());
            assertEquals(logical.replace(".", org.kanger.enums.Enums.FILE_SEPARATOR),
                    reindexed.getMind().getStorageName());
            assertTrue(Boolean.TRUE.equals(
                    reindexed.getMind().query("?canonical_reindex_fact;")));
            assertFalse(phases.isEmpty(), "Reindex progress was not exposed to the adapter");
            assertTrue(reindexed.getStorageStatus().isUsed());
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserReindexUsesSharedCoreWithoutLegacyEscape() throws Exception {
        Fixture fixture = fixture("browser-reindex");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            String logical = "browser.reindex." + UUID.randomUUID();
            processor.execute(new CommandParser().parse("storage use " + logical),
                    fixture.user);
            assertTrue(Boolean.TRUE.equals(
                    fixture.user.getCurrentMind().query("!browser_reindex_fact;")));

            AtomicInteger escaped = new AtomicInteger();
            JSONObject response = invoke(
                    canonicalReactor(escaped), fixture.token,
                    "storage reindex " + logical);

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals("STORAGE_REINDEX", response.optString("canonical_intent"));
            assertEquals(0, escaped.get(),
                    "Canonical storage reindex escaped into legacy reindex protocol");
            assertTrue(Boolean.TRUE.equals(
                    fixture.user.getCurrentMind().query("?browser_reindex_fact;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserMissingReindexExposesTypedCoreLifecycleError() throws Exception {
        Fixture fixture = fixture("browser-missing-reindex");
        try {
            AtomicInteger escaped = new AtomicInteger();
            String missing = "definitely.missing." + UUID.randomUUID();

            JSONObject response = invoke(
                    canonicalReactor(escaped), fixture.token,
                    "storage reindex " + missing);

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals("STORAGE_NOT_FOUND", response.optString("code"));
            assertEquals(0, escaped.get(),
                    "Missing canonical reindex escaped into legacy reindex protocol");
            assertFalse(fixture.user.getCurrentMind().isStorageUsed());
            assertFalse(fixture.user.getCurrentMind().getStoragesList().contains(missing),
                    "Missing reindex created a new storage");
        } finally {
            fixture.close();
        }
    }

    @Test
    void browserActiveTransactionReindexRejectsAndPreservesState()
            throws Exception {
        Fixture fixture = fixture("browser-active-reindex");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String logical = "active.reindex." + UUID.randomUUID();
            processor.execute(parser.parse("storage use " + logical), fixture.user);

            IMind child = new Mind(fixture.user.getCurrentMind());
            fixture.user.setCurrentMind(child);
            assertTrue(Boolean.TRUE.equals(child.query("!active_reindex_transient;")));

            AtomicInteger escaped = new AtomicInteger();
            JSONObject response = invoke(
                    canonicalReactor(escaped), fixture.token,
                    "storage reindex " + logical);

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals("ACTIVE_TRANSACTION", response.optString("code"));
            assertEquals("TRANSACTION_RESOLUTION_REQUIRED",
                    response.optString("required_action"));
            assertEquals(0, escaped.get(),
                    "Rejected canonical reindex escaped into legacy reindex protocol");
            assertSame(child, fixture.user.getCurrentMind());
            assertEquals(1, child.getTransactionLevel());
            assertTrue(child.isStorageUsed());
            assertTrue(Boolean.TRUE.equals(child.query("?active_reindex_transient;")));

            processor.execute(parser.parse("transaction rollback"), fixture.user);
            processor.execute(parser.parse("storage drop " + logical), fixture.user);
        } finally {
            fixture.close();
        }
    }

    @Test
    void progressFailureRestoresPreviouslySelectedStorage() throws Exception {
        Fixture fixture = fixture("progress-failure");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String first = "reindex.origin." + UUID.randomUUID();
            String target = "reindex.target." + UUID.randomUUID();

            processor.execute(parser.parse("storage use " + first), fixture.user);
            assertTrue(Boolean.TRUE.equals(
                    fixture.user.getCurrentMind().query("!origin_reindex_fact;")));
            processor.execute(parser.parse("storage close"), fixture.user);
            processor.execute(parser.parse("storage use " + target), fixture.user);
            assertTrue(Boolean.TRUE.equals(
                    fixture.user.getCurrentMind().query("!target_reindex_fact;")));
            processor.execute(parser.parse("storage close"), fixture.user);
            processor.execute(parser.parse("storage use " + first), fixture.user);
            IMind before = fixture.user.getCurrentMind();

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> executeWithProgress(
                            processor,
                            parser.parse("storage reindex " + target),
                            fixture.user,
                            new IReactor<String>() {
                                @Override
                                public Object run(String item) {
                                    throw new IllegalStateException(
                                            "injected reindex progress failure");
                                }
                            }));

            assertTrue(failure.getMessage().contains("injected"));
            assertSame(before, fixture.user.getCurrentMind());
            assertEquals(first.replace(".", org.kanger.enums.Enums.FILE_SEPARATOR),
                    fixture.user.getCurrentMind().getStorageName());
            assertTrue(Boolean.TRUE.equals(
                    fixture.user.getCurrentMind().query("?origin_reindex_fact;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void closedWorkspaceSurvivesReindexWithoutStaleGenerationReferences()
            throws Exception {
        Fixture fixture = fixture("offline-workspace");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String logical = "reindex.offline." + UUID.randomUUID();

            processor.execute(parser.parse("storage use " + logical), fixture.user);
            processor.execute(parser.parse("storage close"), fixture.user);
            assertTrue(Boolean.TRUE.equals(
                    fixture.user.getCurrentMind().query("!offline_reindex_fact;")));

            CanonicalCommandProcessor.Result reindexed = executeWithProgress(
                    processor,
                    parser.parse("storage reindex " + logical),
                    fixture.user,
                    null);

            assertTrue(reindexed.isSuccess());
            assertFalse(reindexed.getMind().isStorageUsed());
            assertSame(reindexed.getMind(), fixture.user.getCurrentMind());
            assertTrue(Boolean.TRUE.equals(
                    reindexed.getMind().query("?offline_reindex_fact;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void adaptersContainNoIndependentCanonicalReindexSemanticPath()
            throws Exception {
        String console = source("kanger-console/src/org/kanger/CanonicalConsole.java");
        String ingress = source(
                "kanger-server/src/org/kanger/CanonicalCommandIngressReactor.java");

        assertTrue(console.contains("case STORAGE_REINDEX:"));
        assertTrue(console.contains(
                "COMMAND_PROCESSOR.execute(invocation, mind.getUser(),"),
                "Console does not provide progress to the shared processor");
        assertFalse(console.contains("private static IMind reindexStorage("),
                "Console retained an independent storage-reindex semantic helper");
        assertFalse(ingress.contains(
                "case STORAGE_REINDEX:\n                command(envelope, \"reindex\", string(invocation, \"name\"));"),
                "Browser canonical reindex still translates into legacy protocol");
    }

    private CanonicalCommandProcessor.Result executeWithProgress(
            CanonicalCommandProcessor processor,
            CommandInvocation invocation,
            IUser user,
            IReactor<String> progress) throws Exception {
        Method execute = CanonicalCommandProcessor.class.getMethod(
                "execute", CommandInvocation.class, IUser.class, IReactor.class);
        try {
            return (CanonicalCommandProcessor.Result) execute.invoke(
                    processor, invocation, user, progress);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private IReactor<JSONObject> canonicalReactor(final AtomicInteger escaped) {
        IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                escaped.incrementAndGet();
                return new JSONObject()
                        .put("result", "error")
                        .put("code", "legacy_reindex_escape");
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
        String identity = "canonical-storage-reindex-" + purpose + "-"
                + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, token);
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

        private Fixture(IUser user, String token) {
            this.user = user;
            this.token = token;
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
