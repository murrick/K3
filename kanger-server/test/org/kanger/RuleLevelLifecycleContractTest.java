/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end qualification of aggregate rule-level observability against the
 * real qualified rule selector and explicit transaction lifecycle.
 */
class RuleLevelLifecycleContractTest {

    @Test
    void aggregateTracksRollbackCommitAliasesAndPointCompatibility() throws Exception {
        Fixture fixture = fixture("lifecycle");
        try {
            Mind root = fixture.root;
            assertTrue(Boolean.TRUE.equals(root.query("!base_rule;")));

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("!u1_rule;")));

            Mind u2 = new Mind(u1);
            fixture.user.setCurrentMind(u2);
            assertTrue(Boolean.TRUE.equals(u2.query("!u2_rule;")));

            JSONObject aggregate = command(fixture, "rule level");
            assertEquals(Arrays.asList(2, 1, 0), levels(aggregate));
            assertContains(aggregate, "base_rule", "u1_rule", "u2_rule");

            JSONObject abbreviated = command(fixture, "r l");
            assertEquals(aggregate.getJSONArray("levels").toString(),
                    abbreviated.getJSONArray("levels").toString(),
                    "r l diverged from rule level");

            JSONObject point = command(fixture, "rule level 1");
            JSONObject shortPoint = command(fixture, "r l 1");
            JSONObject legacyPoint = legacyPoint(fixture, 1);
            assertFalse(point.has("levels"), "point rule level acquired aggregate shape");
            assertEquals(point.getJSONArray("list").toString(),
                    shortPoint.getJSONArray("list").toString(),
                    "r l N diverged from rule level N");
            assertEquals(legacyPoint.getJSONArray("list").toString(),
                    point.getJSONArray("list").toString(),
                    "rule level N diverged from the qualified legacy selector");

            u1.release(u2);
            fixture.user.setCurrentMind(u1);
            JSONObject rolledBack = command(fixture, "rule level");
            assertEquals(Arrays.asList(1, 0), levels(rolledBack));
            assertFalse(rolledBack.toString().contains("u2_rule"),
                    "rolled-back U2 remained observable");

            Mind committedU2 = new Mind(u1);
            fixture.user.setCurrentMind(committedU2);
            assertTrue(Boolean.TRUE.equals(committedU2.query("!u2_committed_rule;")));
            assertTrue(u1.commit(committedU2), "U2 -> U1 commit failed");
            fixture.user.setCurrentMind(u1);

            JSONObject committed = command(fixture, "rule level");
            assertEquals(Arrays.asList(1, 0), levels(committed));
            assertTrue(committed.toString().contains("u2_committed_rule"),
                    "committed U2 delta was not observable at the surviving stack");
        } finally {
            fixture.close();
        }
    }

    @Test
    void aggregateShowsRebasedExplicitStackAndTargetStorageBase() throws Exception {
        Fixture fixture = fixture("storage-rebase");
        try {
            IMind root = createStorage(fixture, "rule-level-a", "a_base_rule");
            createStorage(fixture, "rule-level-b", "b_base_rule");
            root = open(fixture, root, "rule-level-a");

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("!u1_rebase_rule;")));

            Mind u2 = new Mind(u1);
            fixture.user.setCurrentMind(u2);
            assertTrue(Boolean.TRUE.equals(u2.query("!u2_rebase_rule;")));

            IMind rebased = u2.useStorage("rule-level-b");
            fixture.user.setCurrentMind(rebased);

            JSONObject response = command(fixture, "rule level");
            assertEquals(Arrays.asList(2, 1, 0), levels(response));
            assertContains(response, "b_base_rule", "u1_rebase_rule", "u2_rebase_rule");
            assertFalse(response.toString().contains("a_base_rule"),
                    "old storage U0 leaked through rule-level rebase observability");
        } finally {
            fixture.close();
        }
    }

    @Test
    void operationLocalTechnicalTransactionIsNeverObservable() throws Exception {
        Fixture fixture = fixture("technical-invisible");
        try {
            assertTrue(Boolean.TRUE.equals(fixture.root.query("!base_visible_rule;")));
            Mind u1 = new Mind(fixture.root);
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("!u1_visible_rule;")));

            try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(u1)) {
                assertSame(u1, fixture.user.getCurrentMind(),
                        "technical transaction became the published user currentMind");
                assertTrue(Boolean.TRUE.equals(tx.mind().query("!technical_hidden_rule;")));

                JSONObject response = command(fixture, "rule level");
                assertEquals(Arrays.asList(1, 0), levels(response));
                assertContains(response, "base_visible_rule", "u1_visible_rule");
                assertFalse(response.toString().contains("technical_hidden_rule"),
                        "operation-local technical transaction leaked into rule level");
            }

            JSONObject afterClose = command(fixture, "rule level");
            assertEquals(Arrays.asList(1, 0), levels(afterClose));
            assertFalse(afterClose.toString().contains("technical_hidden_rule"));
        } finally {
            fixture.close();
        }
    }

    private JSONObject command(Fixture fixture, String line) throws Exception {
        CanonicalCommandIngressReactor ingress = new CanonicalCommandIngressReactor(
                new CanonicalCommandRuntimeReactor(new QueryProcessor()));
        Object response = ingress.run(new JSONObject().put("body", new JSONObject()
                .put("context", "dialogue")
                .put("parameters", new JSONObject()
                        .put("token", fixture.token)
                        .put("line", line))));
        assertTrue(response instanceof JSONObject, "command response is not JSON");
        JSONObject json = (JSONObject) response;
        assertEquals("OK", json.optString("result"), json.toString());
        return json;
    }

    private JSONObject legacyPoint(Fixture fixture, long level) throws Exception {
        Object response = new QueryProcessor().run(new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()
                        .put("token", fixture.token)
                        .put("rules", "")
                        .put("level", level))));
        assertTrue(response instanceof JSONObject, "legacy point response is not JSON");
        JSONObject json = (JSONObject) response;
        assertEquals("OK", json.optString("result"), json.toString());
        return json;
    }

    private List<Integer> levels(JSONObject response) {
        JSONArray groups = response.getJSONArray("levels");
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < groups.length(); ++i) {
            result.add(groups.getJSONObject(i).getInt("level"));
        }
        return result;
    }

    private void assertContains(JSONObject response, String... probes) {
        String text = response.toString();
        for (String probe : probes) {
            assertTrue(text.contains(probe),
                    "rule-level response does not contain " + probe + ": " + text);
        }
    }

    private IMind createStorage(Fixture fixture, String name, String fact) throws Exception {
        IMind mind = fixture.user.getCurrentMind();
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(mind.query("!" + fact + ";")));
        fixture.user.checkpoint(mind);
        mind = mind.closeStorage();
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private IMind open(Fixture fixture, IMind mind, String name) throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "rule-level-lifecycle-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, root, token);
    }

    private static final class Fixture {
        private final IUser user;
        private final Mind root;
        private final String token;

        private Fixture(IUser user, Mind root, String token) {
            this.user = user;
            this.root = root;
            this.token = token;
        }

        private void close() throws Exception {
            UserFactory.dropUser(user);
        }
    }
}
