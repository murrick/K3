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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes read-only compatibility observability for the explicit U-stack. */
class TransactionCompatibilityObservabilityTest {

    @Test
    void statusTracksValidUnqualifiedAndRejectedRollbackCompatibility()
            throws Exception {
        Fixture fixture = fixture();
        try {
            IMind root = createStorage(fixture, "tx-observe-a", "!a_anchor;");
            createStorage(fixture, "tx-observe-b", "!ghost;");
            root = open(fixture, root, "tx-observe-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!~ghost;")));
            Rule historical = findRule(u1, "!~ghost;");

            Mind u2 = new Mind(u1);
            historical.setDeleted(true, u2);
            fixture.user.setCurrentMind(u2);

            JSONObject before = invoke(fixture.reactor, fixture.token, "transaction");
            assertCompatibility(before, 0, "VALID");
            assertCompatibility(before, 1, "VALID");
            assertCompatibility(before, 2, "VALID");

            Mind rebasedU2 = (Mind) u2.useStorage("tx-observe-b");
            fixture.user.setCurrentMind(rebasedU2);
            assertEquals(2, rebasedU2.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?")));

            JSONObject afterRebase = invoke(fixture.reactor, fixture.token, "transaction");
            assertCompatibility(afterRebase, 0, "VALID");
            assertCompatibility(afterRebase, 1, "UNQUALIFIED");
            assertCompatibility(afterRebase, 2, "VALID");
            assertEquals("tx-observe-b",
                    afterRebase.getJSONObject("transaction_status")
                            .getString("storage"));

            JSONObject rejected = invoke(
                    fixture.reactor, fixture.token, "transaction rollback");
            assertEquals("error", rejected.optString("result"), rejected.toString());
            assertEquals("ROLLBACK_REBASE_CONFLICT", rejected.optString("code"),
                    rejected.toString());
            assertSame(rebasedU2, fixture.user.getCurrentMind(),
                    "rejected rollback changed the published top");

            JSONObject afterReject = invoke(fixture.reactor, fixture.token, "transaction");
            assertCompatibility(afterReject, 0, "VALID");
            JSONObject incompatible = assertCompatibility(afterReject, 1, "INCOMPATIBLE");
            assertCompatibility(afterReject, 2, "VALID");

            JSONArray collisions = incompatible.getJSONArray("collisions");
            assertFalse(collisions.isEmpty(), "incompatible U1 lost its collision witness");
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
                    "transaction status did not retain the rollback collision witness: "
                            + afterReject);
        } finally {
            fixture.close();
        }
    }

    private JSONObject assertCompatibility(JSONObject response,
                                           int level,
                                           String expected) {
        assertEquals("OK", response.optString("result"), response.toString());
        JSONObject status = response.optJSONObject("transaction_status");
        assertNotNull(status, "transaction status projection is missing: " + response);
        assertEquals(1, status.optInt("schema", -1));
        JSONArray levels = status.optJSONArray("levels");
        assertNotNull(levels, "transaction levels are missing: " + response);
        for (int i = 0; i < levels.length(); ++i) {
            JSONObject item = levels.getJSONObject(i);
            if (item.optInt("level", -1) == level) {
                assertEquals(expected, item.optString("compatibility"), item.toString());
                return item;
            }
        }
        throw new AssertionError("Transaction level U" + level + " is missing: " + response);
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
        String identity = "transaction-compatibility-observability-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        AtomicInteger escaped = new AtomicInteger();
        IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                escaped.incrementAndGet();
                throw new AssertionError("canonical transaction escaped into legacy runtime");
            }
        };
        IReactor<JSONObject> reactor = new CanonicalCommandIngressReactor(
                new WorkspaceStateReactor(
                        new CanonicalCommandRuntimeReactor(legacy)));
        return new Fixture(user, token, reactor, escaped);
    }

    private static final class Fixture {
        private final IUser user;
        private final String token;
        private final IReactor<JSONObject> reactor;
        private final AtomicInteger escaped;

        private Fixture(IUser user,
                        String token,
                        IReactor<JSONObject> reactor,
                        AtomicInteger escaped) {
            this.user = user;
            this.token = token;
            this.reactor = reactor;
            this.escaped = escaped;
        }

        private void close() throws Exception {
            assertEquals(0, escaped.get(),
                    "canonical transaction touched legacy runtime");
            UserFactory.logout(token);
        }
    }
}
