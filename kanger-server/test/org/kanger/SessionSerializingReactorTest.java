package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

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
}
