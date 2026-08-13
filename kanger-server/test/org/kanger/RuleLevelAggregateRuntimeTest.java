/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuleLevelAggregateRuntimeTest {

    @Test
    void bareRuleLevelAggregatesPublishedUserLevelsThroughQualifiedSelector()
            throws Exception {
        String identity = "rule-level-aggregate-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        Mind u1 = new Mind(root);
        Mind u2 = new Mind(u1);
        user.setCurrentMind(u2);
        String token = UserFactory.addUser(user);

        try {
            AtomicInteger calls = new AtomicInteger();
            List<Long> requestedLevels = new ArrayList<Long>();
            IReactor<JSONObject> qualifiedSelector = new IReactor<JSONObject>() {
                @Override
                public Object run(JSONObject packet) {
                    JSONObject body = packet.getJSONObject("body");
                    assertEquals("query", body.getString("context"));
                    JSONObject parameters = body.getJSONObject("parameters");
                    assertEquals("", parameters.getString("rules"));
                    long level = parameters.getLong("level");
                    requestedLevels.add(level);
                    calls.incrementAndGet();
                    return new JSONObject()
                            .put("result", "OK")
                            .put("size", 1)
                            .put("list", new JSONArray().put(new JSONObject()
                                    .put("id", 100L + level)
                                    .put("origin", "U" + level)));
                }
            };

            CanonicalCommandRuntimeReactor reactor =
                    new CanonicalCommandRuntimeReactor(qualifiedSelector);
            JSONObject packet = new JSONObject()
                    .put("body", new JSONObject()
                            .put("context", CanonicalCommandIngressReactor.CANONICAL_CONTEXT)
                            .put("parameters", new JSONObject().put("token", token)))
                    .put(CanonicalCommandIngressReactor.INVOCATION_MARKER,
                            new CommandParser().parse("rule level"));

            JSONObject response = (JSONObject) reactor.run(packet);

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals(1, response.getInt("schema"));
            assertEquals(3, response.getInt("size"));
            assertFalse(response.getBoolean("empty"));
            assertEquals(2L, response.getLong("transaction"));
            assertEquals(3, calls.get());
            assertEquals(java.util.Arrays.asList(0L, 1L, 2L), requestedLevels);

            JSONArray levels = response.getJSONArray("levels");
            assertEquals(3, levels.length());
            for (int i = 0; i < levels.length(); ++i) {
                JSONObject group = levels.getJSONObject(i);
                assertEquals(i, group.getInt("level"));
                assertEquals(1, group.getInt("size"));
                assertEquals("U" + i,
                        group.getJSONArray("list").getJSONObject(0).getString("origin"));
            }
        } finally {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Isolated test token is already clean.
            }
        }
    }
}
