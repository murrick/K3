/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualification for failures that occur after semantic commit has already
 * consumed the child reservation.
 *
 * <p>A storage flush failure is injected only at root finalization. The server
 * must report an operational error while truthfully exposing that semantic
 * settlement was COMMITTED, currentMind has moved to the parent and retrying
 * the same child is neither necessary nor legal.</p>
 */
class TransactionSettlementFinalizationTest {

    @Test
    void explicitCommitReportsCommittedOutcomeWhenRootFlushFailsAfterSettlement()
            throws Exception {
        Fixture fixture = fixture("explicit-commit");
        try {
            IMind root = fixture.root.useStorage("settlement-finalization");
            fixture.user.setCurrentMind(root);

            Mind child = new Mind(root);
            fixture.user.setCurrentMind(child);
            assertTrue(Boolean.TRUE.equals(child.query("!settled_fact;")));
            assertEquals(1, child.getTransactionLevel());

            fixture.db.failFlush = true;
            MindLifecycleReactor reactor = new MindLifecycleReactor(rejectingDelegate());
            JSONObject response = transaction(reactor, fixture.token, "commit");

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals("transaction_settlement_finalization_failed",
                    response.optString("code"), response.toString());
            assertEquals("COMMITTED", response.optString("settlement"));
            assertTrue(response.optBoolean("semantic_applied", false));
            assertTrue(response.optBoolean("reservation_consumed", false));
            assertEquals("VERIFY_CURRENT_STATE",
                    response.optString("required_action"));
            assertSame(root, fixture.user.getCurrentMind(),
                    "server left currentMind on a child whose reservation was consumed");
            assertEquals(0, counter((Mind) root),
                    "post-settlement failure leaked or double-consumed the reservation");
            assertEquals(0, response.optInt("transaction", -1));

            fixture.db.failFlush = false;
            assertTrue(Boolean.TRUE.equals(root.query("?settled_fact;")),
                    "server reported a commit failure even though semantic delta was not applied");
        } finally {
            fixture.db.failFlush = false;
            fixture.close();
        }
    }

    @Test
    void technicalScopeNeverRetriesCommitAfterPostSettlementFailure()
            throws Exception {
        Fixture fixture = fixture("technical-scope");
        try {
            IMind root = fixture.root.useStorage("settlement-technical");
            fixture.user.setCurrentMind(root);

            TechnicalMindTransaction tx = TechnicalMindTransaction.begin((Mind) root);
            assertTrue(Boolean.TRUE.equals(tx.mind().query("!technical_settled_fact;")));
            fixture.db.failFlush = true;

            Exception failure;
            try {
                tx.commit();
                throw new AssertionError("Injected flush failure did not escape commit");
            } catch (Exception expected) {
                failure = expected;
            }

            assertEquals("TransactionSettlementException",
                    failure.getClass().getSimpleName(), failure.toString());
            assertEquals(0, counter((Mind) root),
                    "commit failure did not consume exactly one reservation");

            tx.close();
            assertEquals(0, counter((Mind) root),
                    "AutoCloseable retried settlement after commit had already started");

            fixture.db.failFlush = false;
            assertTrue(Boolean.TRUE.equals(root.query("?technical_settled_fact;")));
        } finally {
            fixture.db.failFlush = false;
            fixture.close();
        }
    }

    private JSONObject transaction(MindLifecycleReactor reactor,
                                   String token,
                                   String action) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("transaction", action)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "Transaction response is not JSON: " + response);
        return (JSONObject) response;
    }

    private IReactor<JSONObject> rejectingDelegate() {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                throw new AssertionError("Transaction escaped lifecycle boundary");
            }
        };
    }

    private int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "settlement-finalization-" + purpose + "-"
                + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        FailingFlushDB db = new FailingFlushDB();
        db.init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, root, token, db);
    }

    private static final class FailingFlushDB extends DB {
        private boolean failFlush;

        @Override
        public void flush() throws Exception {
            if (failFlush) {
                throw new InjectedFinalizationFailure();
            }
            super.flush();
        }
    }

    private static final class InjectedFinalizationFailure extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static final class Fixture {
        private final IUser user;
        private final Mind root;
        private final String token;
        private final FailingFlushDB db;

        private Fixture(IUser user, Mind root, String token, FailingFlushDB db) {
            this.user = user;
            this.root = root;
            this.token = token;
            this.db = db;
        }

        private void close() throws Exception {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Test-owned token may already be closed by failure cleanup.
            }
        }
    }
}
