/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of the first shared semantic command family. */
class CanonicalTransactionConvergenceTest {

    @Test
    void browserTransactionDialogueExecutesSharedCanonicalRuntimeNotLegacyProtocol()
            throws Exception {
        Fixture fixture = fixture("browser");
        try {
            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
                @Override
                public Object run(JSONObject packet) {
                    escaped.incrementAndGet();
                    throw new AssertionError(
                            "Canonical transaction escaped into legacy runtime");
                }
            };
            IReactor<JSONObject> reactor = new CanonicalCommandIngressReactor(
                    new WorkspaceStateReactor(
                            new CanonicalCommandRuntimeReactor(legacy)));

            JSONObject started = invoke(reactor, fixture.token,
                    "transaction start");
            assertEquals("OK", started.optString("result"), started.toString());
            assertEquals("TX_START", started.optString("canonical_intent"));
            assertEquals(1, started.optInt("transaction", -1));
            assertEquals(1, fixture.user.getCurrentMind().getTransactionLevel());

            JSONObject rolledBack = invoke(reactor, fixture.token,
                    "transaction rollback");
            assertEquals("OK", rolledBack.optString("result"), rolledBack.toString());
            assertEquals("TX_ROLLBACK", rolledBack.optString("canonical_intent"));
            assertEquals(0, rolledBack.optInt("transaction", -1));
            assertEquals(0, fixture.user.getCurrentMind().getTransactionLevel());

            JSONObject rootCommit = invoke(reactor, fixture.token,
                    "transaction commit");
            assertEquals("error", rootCommit.optString("result"), rootCommit.toString());
            assertEquals("NO_STORAGE_OPEN", rootCommit.optString("code"),
                    rootCommit.toString());
            assertEquals("TX_COMMIT", rootCommit.optString("canonical_intent"));
            assertEquals(0, escaped.get(),
                    "Canonical transaction touched legacy query/command protocol");
        } finally {
            fixture.close();
        }
    }

    @Test
    void javaConsoleContainsNoIndependentTransactionSemanticImplementation()
            throws Exception {
        String source = source(
                "kanger-console/src/org/kanger/CanonicalConsole.java");

        assertTrue(source.contains("new CanonicalCommandProcessor()"));
        assertTrue(source.contains(
                "COMMAND_PROCESSOR.execute(invocation, mind.getUser())"));
        assertFalse(source.contains("private static IMind commitTransaction"),
                "Console retained a second commit semantic implementation");
        assertFalse(source.contains("private static IMind rollbackTransaction"),
                "Console retained a second rollback semantic implementation");
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
        String identity = "canonical-tx-convergence-" + purpose + "-"
                + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        user.setCurrentMind(new Mind(user));
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
