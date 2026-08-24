/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.ParseErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for canonical errors that cross the workspace boundary. */
class CanonicalFailureWorkspaceBoundaryTest {

    @Test
    void classifiedFailureKeepsSerializedWorkspaceProjection() throws Exception {
        String identity = "canonical-failure-workspace-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new WorkspaceStateReactor(new IReactor<JSONObject>() {
                        @Override
                        public Object run(JSONObject packet) throws Exception {
                            throw new ParseErrorException(4, 2, "Unexpected term");
                        }
                    }));

            JSONObject packet = new JSONObject().put("body", new JSONObject()
                    .put("context", "query")
                    .put("parameters", new JSONObject()
                            .put("token", token)
                            .put("request", "ignored")));
            JSONObject response = (JSONObject) reactor.run(packet);

            assertEquals("error", response.getString("result"));
            assertEquals("parse_error", response.getString("code"));
            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals("application", diagnostic.getString("domain"));
            assertEquals(4, diagnostic.getJSONObject("source").getInt("offset"));
            assertEquals(2, diagnostic.getJSONObject("source").getInt("length"));

            JSONObject workspace = response.getJSONObject("workspace");
            assertEquals(2, workspace.getInt("schema"));
            assertFalse(workspace.getJSONObject("storage").getBoolean("active"));
            assertEquals(0, workspace.getJSONObject("transaction").getInt("level"));
            assertTrue(workspace.getJSONObject("transaction").getBoolean("empty"));
            assertFalse(response.has("transaction"));
            assertFalse(response.has("empty"));
        } finally {
            if (token != null) {
                try {
                    UserFactory.logout(token);
                } catch (AuthenticationErrorException alreadyClosed) {
                    // Isolated test token may already be closed by cleanup.
                }
            }
        }
    }
}
