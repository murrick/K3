/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryProcessorTest {

    @Test
    void legacyRootConfirmationBypassesAuthenticatedRequestGuard() {
        JSONObject confirmation = new JSONObject().put("confirm", "opaque-token");

        assertTrue(QueryProcessor.isLegacyRootConfirmation("", confirmation));
        assertFalse(QueryProcessor.isLegacyRootConfirmation("login", confirmation));
        assertFalse(QueryProcessor.isLegacyRootConfirmation("command", confirmation));
        assertFalse(QueryProcessor.isLegacyRootConfirmation(
                "", new JSONObject().put("confirm", "")));
        assertFalse(QueryProcessor.isLegacyRootConfirmation("", new JSONObject()));
    }

    @Test
    void compileParseFailureReachesCanonicalBoundaryWithSourceSpan() throws Exception {
        String id = "query-processor-parse-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(id, id);
        String token = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            Mind root = new Mind(user);
            user.setCurrentMind(root);
            token = UserFactory.addUser(user);

            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new QueryProcessor());
            JSONObject packet = new JSONObject().put("body", new JSONObject()
                    .put("context", "query")
                    .put("parameters", new JSONObject()
                            .put("token", token)
                            .put("compile", URLEncoder.encode(
                                    "!\"unterminated;", "UTF-8"))));

            JSONObject response = (JSONObject) reactor.run(packet);

            assertEquals("error", response.getString("result"), response.toString());
            assertEquals("parse_error", response.getString("code"), response.toString());
            assertFalse(response.has("source"),
                    "Source span must remain inside the canonical error envelope");
            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals(1, diagnostic.getInt("schema"));
            assertEquals("application", diagnostic.getString("domain"));
            JSONObject source = diagnostic.getJSONObject("source");
            assertEquals(1, source.getInt("offset"));
            assertEquals(0, source.getInt("length"));
            assertSame(root, user.getCurrentMind(),
                    "Rejected compile displaced the authoritative root Mind");
        } finally {
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    @Test
    void ruleLevelCommandFailureReachesCanonicalBoundary() throws Exception {
        String id = "query-processor-command-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(id, id);
        String token = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new QueryProcessor());
            JSONObject packet = new JSONObject().put("body", new JSONObject()
                    .put("context", "query")
                    .put("parameters", new JSONObject()
                            .put("token", token)
                            .put("rules", "")
                            .put("level", 1)));

            JSONObject response = (JSONObject) reactor.run(packet);

            assertEquals("error", response.getString("result"), response.toString());
            assertEquals("command_error", response.getString("code"), response.toString());
            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals(1, diagnostic.getInt("schema"));
            assertEquals("application", diagnostic.getString("domain"));
            assertEquals("command_error", diagnostic.getString("code"));
            assertEquals("retain", diagnostic.getString("session_action"));
            assertEquals("confirmed", diagnostic.getString("operation_outcome"));
        } finally {
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    @Test
    void unclassifiedRuleLevelFailureEscapesQueryProcessor() throws Exception {
        String id = "query-processor-unclassified-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(id, id);
        String token = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new QueryProcessor());
            JSONObject packet = new JSONObject().put("body", new JSONObject()
                    .put("context", "query")
                    .put("parameters", new JSONObject()
                            .put("token", token)
                            .put("rules", "")
                            .put("level", "not-a-number")));

            assertThrows(JSONException.class, () -> reactor.run(packet));
        } finally {
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }
}
