/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.RegistrationPolicy;
import org.kanger.interfaces.IReactor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryProcessorAuthenticationBoundaryTest {

    @Test
    void missingSessionTokenReachesCanonicalBoundary() throws Exception {
        String token = "missing-session-" + UUID.randomUUID();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new SessionSerializingReactor(
                        RegistrationPolicy.TRUSTED,
                        new QueryProcessor()));
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()
                        .put("token", token)));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.getString("result"), response.toString());
        assertEquals("authentication_error", response.getString("code"),
                response.toString());
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("session", diagnostic.getString("domain"));
        assertEquals("authentication_error", diagnostic.getString("code"));
        assertEquals("verify", diagnostic.getString("session_action"));
        assertEquals("unknown", diagnostic.getString("operation_outcome"));
    }

    @Test
    void queryProcessorAuthenticationFailureReachesCanonicalBoundary() throws Exception {
        String token = "missing-query-processor-" + UUID.randomUUID();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new QueryProcessor());
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()
                        .put("token", token)));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.getString("result"), response.toString());
        assertEquals("authentication_error", response.getString("code"),
                response.toString());
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("session", diagnostic.getString("domain"));
        assertEquals("authentication_error", diagnostic.getString("code"));
        assertEquals("verify", diagnostic.getString("session_action"));
        assertEquals("unknown", diagnostic.getString("operation_outcome"));
    }

    @Test
    void absentSessionTokenReachesCanonicalBoundary() throws Exception {
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new SessionSerializingReactor(
                        RegistrationPolicy.TRUSTED,
                        new QueryProcessor()));
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.getString("result"), response.toString());
        assertEquals("authentication_error", response.getString("code"),
                response.toString());
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("session", diagnostic.getString("domain"));
        assertEquals("authentication_error", diagnostic.getString("code"));
        assertEquals("verify", diagnostic.getString("session_action"));
        assertEquals("unknown", diagnostic.getString("operation_outcome"));
    }

    @Test
    void invalidLoginCredentialsReachCanonicalBoundary() throws Exception {
        String login = "missing-login-" + UUID.randomUUID();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new SessionSerializingReactor(
                        RegistrationPolicy.TRUSTED,
                        new QueryProcessor()));
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "login")
                .put("parameters", new JSONObject()
                        .put("login", login)
                        .put("password", "invalid-password")));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.getString("result"), response.toString());
        assertEquals("authentication_error", response.getString("code"),
                response.toString());
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("session", diagnostic.getString("domain"));
        assertEquals("authentication_error", diagnostic.getString("code"));
        assertEquals("verify", diagnostic.getString("session_action"));
        assertEquals("unknown", diagnostic.getString("operation_outcome"));
    }

    @Test
    void queryProcessorProfileInfoAuthenticationFailureReachesCanonicalBoundary()
            throws Exception {
        String token = "missing-profile-info-" + UUID.randomUUID();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new QueryProcessor());
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "login")
                .put("parameters", new JSONObject()
                        .put("info", true)
                        .put("token", token)));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.getString("result"), response.toString());
        assertEquals("authentication_error", response.getString("code"),
                response.toString());
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("session", diagnostic.getString("domain"));
        assertEquals("authentication_error", diagnostic.getString("code"));
        assertEquals("verify", diagnostic.getString("session_action"));
        assertEquals("unknown", diagnostic.getString("operation_outcome"));
    }

    @Test
    void queryProcessorResendAuthenticationFailureReachesCanonicalBoundary()
            throws Exception {
        String token = "missing-resend-" + UUID.randomUUID();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new QueryProcessor());
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "login")
                .put("parameters", new JSONObject()
                        .put("resend", true)
                        .put("token", token)));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals("error", response.getString("result"), response.toString());
        assertEquals("authentication_error", response.getString("code"),
                response.toString());
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("session", diagnostic.getString("domain"));
        assertEquals("authentication_error", diagnostic.getString("code"));
        assertEquals("verify", diagnostic.getString("session_action"));
        assertEquals("unknown", diagnostic.getString("operation_outcome"));
    }
}
