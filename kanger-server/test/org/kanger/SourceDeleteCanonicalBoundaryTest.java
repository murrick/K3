/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceDeleteCanonicalBoundaryTest {

    @Test
    void physicalDeleteFailureIsCanonicalAndRequiresStateVerification() throws Exception {
        String identity = "source-delete-boundary-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        Path target = null;
        Path child = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            String token = UserFactory.addUser(user);

            target = Paths.get(user.getSourceDir()).resolve("stuck.k");
            Files.createDirectories(target);
            child = target.resolve("content.txt");
            Files.write(child, "keep".getBytes(StandardCharsets.UTF_8));

            IReactor<JSONObject> boundary = new CanonicalErrorBoundaryReactor(
                    new DestructiveStopLossReactor(new QueryProcessor()));
            JSONObject response = invoke(boundary, token, "stuck.k");

            assertEquals("error", response.getString("result"));
            assertEquals("source_delete_failed", response.getString("code"));
            assertEquals("Source delete failed for stuck.k",
                    response.getString("description"));
            assertEquals("VERIFY_CURRENT_STATE",
                    response.getString("required_action"));

            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals(1, diagnostic.getInt("schema"));
            assertEquals("operation", diagnostic.getString("domain"));
            assertEquals("source_delete_failed", diagnostic.getString("code"));
            assertFalse(diagnostic.getBoolean("retryable"));
            assertEquals("retain", diagnostic.getString("session_action"));
            assertEquals("unknown", diagnostic.getString("operation_outcome"));

            String rendered = response.toString();
            assertFalse(rendered.contains("DirectoryNotEmptyException"), rendered);
            assertFalse(rendered.contains(target.toString()), rendered);
            assertTrue(Files.exists(target),
                    "Failed delete changed the source filesystem state");
            assertTrue(Files.exists(child),
                    "Failed delete removed source content unexpectedly");
        } finally {
            if (child != null) {
                Files.deleteIfExists(child);
            }
            if (target != null) {
                Files.deleteIfExists(target);
            }
            UserFactory.dropUser(user);
        }
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String fileName) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("delete", fileName)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }
}
