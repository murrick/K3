package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IReactor;

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
    void expectedAuthenticationRejectionUsesNormalErrorEnvelope() {
        JSONObject response = SessionSerializingReactor.authenticationRejected(
                new AuthenticationErrorException());

        assertEquals("error", response.getString("result"));
        assertEquals("Authentication error", response.getString("description"));
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
