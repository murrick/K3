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
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes actionable diagnostics for a rejected historical rollback. */
class RollbackRejectionDiagnosticTest {

    @Test
    void browserRollbackRejectReportsCollisionAndThreeUserControlledExits()
            throws Exception {
        Fixture fixture = fixture();
        try {
            IMind root = createStorage(fixture, "rollback-diag-a", "!a_anchor;");
            createStorage(fixture, "rollback-diag-b", "!ghost;");
            root = open(fixture, root, "rollback-diag-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!~ghost;")));
            Rule historical = findRule(u1, "!~ghost;");

            Mind u2 = new Mind(u1);
            historical.setDeleted(true, u2);
            fixture.user.setCurrentMind(u2);
            Mind rebasedU2 = (Mind) u2.useStorage("rollback-diag-b");
            fixture.user.setCurrentMind(rebasedU2);
            assertEquals(2, rebasedU2.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?")));

            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            int reservationBefore = transactionCounter(rebasedU1);
            AtomicInteger escaped = new AtomicInteger();

            JSONObject response = invoke(
                    canonicalReactor(escaped), fixture.token, "transaction rollback");

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals("ROLLBACK_REBASE_CONFLICT", response.optString("code"),
                    response.toString());
            assertEquals("STORAGE_BASELINE_COLLISION", response.optString("reason"),
                    response.toString());
            assertEquals("TX_ROLLBACK", response.optString("canonical_intent"));
            assertEquals(2, response.optInt("transaction", -1));
            assertEquals(0, escaped.get(),
                    "canonical rollback diagnostic escaped into legacy runtime");

            JSONObject rejection = response.getJSONObject("rejection");
            assertEquals(1, rejection.getInt("schema"));
            assertEquals("ROLLBACK_REBASE_CONFLICT", rejection.getString("kind"));
            assertEquals(1, rejection.getInt("target_level"));
            assertEquals("rollback-diag-b", rejection.getString("storage"));

            JSONArray collisions = rejection.getJSONArray("collisions");
            assertFalse(collisions.isEmpty(), "rollback rejection contained no collision witness");
            boolean ghostWitness = false;
            for (int i = 0; i < collisions.length(); ++i) {
                JSONObject collision = collisions.getJSONObject(i);
                Set<String> rules = new HashSet<String>();
                rules.add(collision.getString("left"));
                rules.add(collision.getString("right"));
                if (rules.contains("!ghost;") && rules.contains("!~ghost;")) {
                    ghostWitness = true;
                }
            }
            assertTrue(ghostWitness,
                    "rollback rejection did not identify the exact ghost collision: " + response);

            JSONArray actions = rejection.getJSONArray("actions");
            assertEquals(3, actions.length());
            assertEquals("USE_COMPATIBLE_STORAGE",
                    actions.getJSONObject(0).getString("id"));
            assertEquals("TRANSACTION_SQUASH",
                    actions.getJSONObject(1).getString("id"));
            assertEquals("transaction squash",
                    actions.getJSONObject(1).getString("command"));
            assertEquals("TRANSACTION_COMMIT",
                    actions.getJSONObject(2).getString("id"));
            assertEquals("transaction commit",
                    actions.getJSONObject(2).getString("command"));

            assertSame(rebasedU2, fixture.user.getCurrentMind(),
                    "diagnostic rejection changed the published U2");
            assertEquals(2, fixture.user.getCurrentMind().getTransactionLevel());
            assertEquals(reservationBefore, transactionCounter(rebasedU1),
                    "diagnostic rejection consumed the live U2 reservation");
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?")),
                    "diagnostic rejection damaged the current valid context");
        } finally {
            fixture.close();
        }
    }

    private IReactor<JSONObject> canonicalReactor(final AtomicInteger escaped) {
        IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                escaped.incrementAndGet();
                throw new AssertionError(
                        "canonical rollback escaped into legacy runtime");
            }
        };
        return new CanonicalCommandIngressReactor(
                new WorkspaceStateReactor(
                        new CanonicalCommandRuntimeReactor(legacy)));
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String line) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "dialogue")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("line", line)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject, "Response is not JSON: " + response);
        return (JSONObject) response;
    }

    private Rule findRule(Mind mind, String origin) throws Exception {
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (origin.equals(rule.getOrigin())) {
                return rule;
            }
        }
        throw new AssertionError("Rule not found: " + origin);
    }

    private int transactionCounter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private IMind createStorage(Fixture fixture, String name, String source)
            throws Exception {
        IMind mind = fixture.user.getCurrentMind();
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(mind.query(source)));
        fixture.user.checkpoint(mind);
        mind = mind.closeStorage();
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private IMind open(Fixture fixture, IMind mind, String name) throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private Fixture fixture() throws Exception {
        String identity = "rollback-diagnostic-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, token);
    }

    private static final class Fixture {
        private final IUser user;
        private final String token;

        private Fixture(IUser user, String token) {
            this.user = user;
            this.token = token;
        }

        private void close() throws Exception {
            UserFactory.logout(token);
        }
    }
}
