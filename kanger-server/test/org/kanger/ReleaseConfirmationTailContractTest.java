/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused 3.7.0 closure contract for destructive confirmation tails shared by
 * Browser, Server and Console.
 */
class ReleaseConfirmationTailContractTest {

    private static final String DATABASE_ERASE_WARNING =
            "WARNING: The contents of the currently open database will also be erased.";

    @Test
    void erasePromptWarnsOnlyWhenDatabaseIsOpen() throws Exception {
        String identity = "erase-warning-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            IReactor<JSONObject> lower = new WorkspaceStateReactor(
                    new CanonicalCommandRuntimeReactor(
                            new ExplicitStorageLifecycleReactor(
                                    new GetSourceBoundaryReactor(
                                            new DestructiveStopLossReactor(
                                                    new MindLifecycleReactor(
                                                            new QueryProcessor()))))));
            IReactor<JSONObject> reactor = new CanonicalCommandIngressReactor(lower);

            JSONObject withoutDatabase = dialogue(reactor, token, "erase");
            assertEquals("confirmation_required", withoutDatabase.optString("result"),
                    withoutDatabase.toString());
            assertEquals("Erase current workspace?",
                    withoutDatabase.getJSONObject("confirmation").getString("prompt"));

            JSONObject use = command(lower, token, "use",
                    "erase-warning-" + UUID.randomUUID());
            assertEquals("OK", use.optString("result"), use.toString());
            assertTrue(user.getCurrentMind().isStorageUsed(),
                    "Erase warning fixture did not open storage");

            JSONObject withDatabase = dialogue(reactor, token, "erase");
            assertEquals("confirmation_required", withDatabase.optString("result"),
                    withDatabase.toString());
            assertEquals("Erase current workspace?\n" + DATABASE_ERASE_WARNING,
                    withDatabase.getJSONObject("confirmation").getString("prompt"));
        } finally {
            try {
                IMind mind = user.getCurrentMind();
                if (mind != null && mind.isStorageUsed()) {
                    user.setCurrentMind(mind.closeStorage());
                }
            } catch (Exception ignored) {
                // best-effort fixture cleanup
            }
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    @Test
    void browserEnterExecutesDefaultConfirmationAction() throws Exception {
        String source = artifact(
                Paths.get("..", "html", "dialogue.js"),
                Paths.get("html", "dialogue.js"));

        assertTrue(source.contains(
                "if (event.key === 'Enter' || event.keyCode === 13)"));
        assertTrue(source.contains(
                "event.preventDefault();\n                finish(true);"));
    }

    @Test
    void consoleEraseWarnsWhenDatabaseIsOpen() throws Exception {
        String source = artifact(
                Paths.get("..", "kanger-console", "src", "org", "kanger",
                        "CanonicalConsole.java"),
                Paths.get("kanger-console", "src", "org", "kanger",
                        "CanonicalConsole.java"));

        assertTrue(source.contains("String prompt = \"Erase workspace?\";"));
        assertTrue(source.contains("if (mind.isStorageUsed())"));
        assertTrue(source.contains(
                "prompt += \"\\nWARNING: The contents of the currently open database \""));
        assertTrue(source.contains("+ \"will also be erased.\";"));
    }

    private static JSONObject dialogue(IReactor<JSONObject> reactor,
                                       String token,
                                       String line) throws Exception {
        return invoke(reactor, "dialogue", new JSONObject()
                .put("token", token)
                .put("line", line));
    }

    private static JSONObject command(IReactor<JSONObject> reactor,
                                      String token,
                                      String name,
                                      Object value) throws Exception {
        return invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put(name, value));
    }

    private static JSONObject invoke(IReactor<JSONObject> reactor,
                                     String context,
                                     JSONObject parameters) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not JSON: " + response);
        return (JSONObject) response;
    }

    private static String artifact(Path... candidates) throws Exception {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Artifact file not found");
    }
}
