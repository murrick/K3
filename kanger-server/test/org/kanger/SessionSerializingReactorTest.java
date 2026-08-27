package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.RegistrationPolicy;
import org.kanger.interfaces.IReactor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SessionSerializingReactorTest {

    @Test
    void prefersBodyParametersForPostPacket() {
        JSONObject packet = new JSONObject()
                .put("body", new JSONObject()
                        .put("context", "query")
                        .put("parameters", new JSONObject()
                                .put("token", "body-token")))
                .put("query", new JSONObject()
                        .put("parameters", new JSONObject()
                                .put("token", "query-token")));

        assertEquals("body-token",
                SessionSerializingReactor.parameters(packet)
                        .getString("token"));
    }

    @Test
    void readsQueryParametersForGetPacket() {
        JSONObject packet = new JSONObject()
                .put("body", new JSONObject())
                .put("query", new JSONObject()
                        .put("context", "command")
                        .put("parameters", new JSONObject()
                                .put("token", "query-token")));

        assertEquals("query-token",
                SessionSerializingReactor.parameters(packet)
                        .getString("token"));
    }

    @Test
    void missingParametersProduceEmptyObject() {
        assertFalse(SessionSerializingReactor.parameters(new JSONObject())
                .keys().hasNext());
    }

    @Test
    void trustedAuthenticationRejectionReachesCanonicalBoundary() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject request) {
                calls.incrementAndGet();
                throw new AssertionError("Rejected credentials reached legacy processor");
            }
        };
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new SessionSerializingReactor(RegistrationPolicy.TRUSTED, legacy));
        String login = "missing-auth-" + UUID.randomUUID();
        JSONObject packet = new JSONObject()
                .put("body", new JSONObject()
                        .put("context", "login")
                        .put("parameters", new JSONObject()
                                .put("login", login)
                                .put("password", "invalid-password")));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals(0, calls.get());
        assertEquals("error", response.getString("result"), response.toString());
        assertEquals("authentication_error", response.getString("code"),
                response.toString());
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("session", diagnostic.getString("domain"));
        assertEquals("authentication_error", diagnostic.getString("code"));
        assertEquals("verify", diagnostic.getString("session_action"));
    }

    @Test
    void unsafeFilesystemIdentifierNeverReachesLegacyProcessor() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SessionSerializingReactor reactor = new SessionSerializingReactor(
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject request) {
                        calls.incrementAndGet();
                        return new JSONObject().put("result", "OK");
                    }
                });
        JSONObject packet = new JSONObject()
                .put("body", new JSONObject())
                .put("query", new JSONObject()
                        .put("context", "command")
                        .put("parameters", new JSONObject()
                                .put("put", "../../outside.k")));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals(0, calls.get());
        assertEquals("error", response.getString("result"));
    }

    @Test
    void safeFilesystemIdentifierReachesLegacyProcessor() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SessionSerializingReactor reactor = new SessionSerializingReactor(
                request -> {
                    calls.incrementAndGet();
                    return new JSONObject().put("result", "OK");
                });
        JSONObject packet = new JSONObject()
                .put("body", new JSONObject())
                .put("query", new JSONObject()
                        .put("context", "command")
                        .put("parameters", new JSONObject()
                                .put("put", "mind.k")));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertEquals(1, calls.get());
        assertEquals("OK", response.getString("result"));
    }
}
