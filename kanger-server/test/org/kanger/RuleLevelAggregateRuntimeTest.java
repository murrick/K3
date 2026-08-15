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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleLevelAggregateRuntimeTest {

    @Test
    void bareRuleLevelAggregatesPublishedUserLevelsCurrentToRoot()
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

            JSONObject response = runAggregate(user, token, qualifiedSelector);

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals(1, response.getInt("schema"));
            assertEquals(3, response.getInt("size"));
            assertFalse(response.getBoolean("empty"));
            assertEquals(2L, response.getLong("transaction"));
            assertEquals(3, calls.get());
            assertEquals(Arrays.asList(2L, 1L, 0L), requestedLevels);

            JSONArray levels = response.getJSONArray("levels");
            assertEquals(3, levels.length());
            for (int i = 0; i < levels.length(); ++i) {
                int expectedLevel = 2 - i;
                JSONObject group = levels.getJSONObject(i);
                assertEquals(expectedLevel, group.getInt("level"));
                assertEquals(1, group.getInt("size"));
                assertEquals("U" + expectedLevel,
                        group.getJSONArray("list").getJSONObject(0).getString("origin"));
            }
        } finally {
            logout(token);
        }
    }

    @Test
    void emptyCurrentLevelRemainsVisibleInAggregate() throws Exception {
        String identity = "rule-level-empty-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        Mind u1 = new Mind(root);
        Mind u2 = new Mind(u1);
        user.setCurrentMind(u2);
        String token = UserFactory.addUser(user);

        try {
            IReactor<JSONObject> qualifiedSelector = new IReactor<JSONObject>() {
                @Override
                public Object run(JSONObject packet) {
                    long level = packet.getJSONObject("body")
                            .getJSONObject("parameters").getLong("level");
                    if (level == 2L) {
                        return new JSONObject()
                                .put("result", "OK")
                                .put("size", 0)
                                .put("list", new JSONArray());
                    }
                    return new JSONObject()
                            .put("result", "OK")
                            .put("size", 1)
                            .put("list", new JSONArray().put(new JSONObject()
                                    .put("id", 100L + level)
                                    .put("origin", "U" + level)));
                }
            };

            JSONObject response = runAggregate(user, token, qualifiedSelector);
            JSONArray levels = response.getJSONArray("levels");

            assertEquals(3, levels.length());
            JSONObject current = levels.getJSONObject(0);
            assertEquals(2, current.getInt("level"));
            assertEquals(0, current.getInt("size"));
            assertEquals(0, current.getJSONArray("list").length());
            assertEquals(2, response.getInt("size"));
            assertFalse(response.getBoolean("empty"));
        } finally {
            logout(token);
        }
    }

    @Test
    void emptyRootIsStillOnePublishedLevel() throws Exception {
        String identity = "rule-level-root-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        user.setCurrentMind(new Mind(user));
        String token = UserFactory.addUser(user);

        try {
            List<Long> requestedLevels = new ArrayList<Long>();
            IReactor<JSONObject> qualifiedSelector = new IReactor<JSONObject>() {
                @Override
                public Object run(JSONObject packet) {
                    long level = packet.getJSONObject("body")
                            .getJSONObject("parameters").getLong("level");
                    requestedLevels.add(level);
                    return new JSONObject()
                            .put("result", "OK")
                            .put("size", 0)
                            .put("list", new JSONArray());
                }
            };

            JSONObject response = runAggregate(user, token, qualifiedSelector);
            JSONArray levels = response.getJSONArray("levels");

            assertEquals(Arrays.asList(0L), requestedLevels);
            assertEquals(1, levels.length());
            assertEquals(0, levels.getJSONObject(0).getInt("level"));
            assertEquals(0, levels.getJSONObject(0).getInt("size"));
            assertTrue(response.getBoolean("empty"));
        } finally {
            logout(token);
        }
    }

    private JSONObject runAggregate(IUser user,
                                    String token,
                                    IReactor<JSONObject> qualifiedSelector)
            throws Exception {
        CanonicalCommandRuntimeReactor reactor =
                new CanonicalCommandRuntimeReactor(qualifiedSelector);
        JSONObject packet = new JSONObject()
                .put("body", new JSONObject()
                        .put("context", CanonicalCommandIngressReactor.CANONICAL_CONTEXT)
                        .put("parameters", new JSONObject().put("token", token)))
                .put(CanonicalCommandIngressReactor.INVOCATION_MARKER,
                        new CommandParser().parse("rule level"));
        return (JSONObject) reactor.run(packet);
    }

    private void logout(String token) throws Exception {
        try {
            UserFactory.logout(token);
        } catch (AuthenticationErrorException alreadyClosed) {
            // Isolated test token is already clean.
        }
    }
}
