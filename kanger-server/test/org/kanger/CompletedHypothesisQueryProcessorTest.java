/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production-path regression for QueryProcessor query overlay settlement followed
 * by the explicitly requested completed-hypothesis projection.
 */
class CompletedHypothesisQueryProcessorTest {

    @Test
    void completedHypothesisSurvivesQueryOverlaySettlement() throws Exception {
        String identity = "completed-hypothesis-http-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);

        try {
            assertTrue(root.compile("!@x consolepremise(x) -> consoletarget(x);"),
                    "Fixture program was rejected");

            QueryProcessor processor = new QueryProcessor();
            JSONObject query = invoke(processor, token, "request",
                    URLEncoder.encode("?consoletarget(item);", "UTF-8"));

            assertEquals("OK", query.optString("result"), query.toString());
            assertEquals("unknown", query.optString("response"), query.toString());
            assertTrue(query.optInt("hypothesis", 0) > 0,
                    "WHO KNOWS response did not publish RAW hypothesis candidates: " + query);

            JSONObject completed = invoke(processor, token, "hypothesis", "");
            assertEquals("OK", completed.optString("result"), completed.toString());
            assertEquals(1, completed.optInt("size", -1), completed.toString());
            JSONArray list = completed.getJSONArray("list");
            assertEquals(1, list.length(), completed.toString());
            assertEquals("!consolepremise(item);",
                    list.getJSONObject(0).optString("origin"), completed.toString());
        } finally {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Test owns the token; tolerate cleanup after an earlier close.
            }
        }
    }

    private static JSONObject invoke(QueryProcessor processor,
                                     String token,
                                     String key,
                                     String value) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put(key, value)));
        Object response = processor.run(packet);
        assertTrue(response instanceof JSONObject,
                "QueryProcessor response is not JSON: " + response);
        return (JSONObject) response;
    }
}
