/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.TransactionSettlementException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for settlement failures crossing the Mind lifecycle boundary. */
class MindLifecycleSettlementErrorBoundaryTest {

    @Test
    void committedSettlementFailureUsesCanonicalRecoveryAndWorkspace()
            throws Exception {
        String identity = "mind-settlement-boundary-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        SettlementFailingMind root = new SettlementFailingMind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);

        try {
            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new WorkspaceStateReactor(
                            new MindLifecycleReactor(rejectingDelegate())));

            JSONObject response = invoke(
                    reactor, token, "!settlement_applied;");

            assertEquals("error", response.getString("result"), response.toString());
            assertEquals("transaction_settlement_failed",
                    response.getString("code"));
            assertEquals("VERIFY_CURRENT_STATE",
                    response.getString("required_action"));

            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals(1, diagnostic.getInt("schema"));
            assertEquals("operation", diagnostic.getString("domain"));
            assertEquals("transaction_settlement_failed",
                    diagnostic.getString("code"));
            assertEquals("retain", diagnostic.getString("session_action"));
            assertEquals("confirmed",
                    diagnostic.getString("operation_outcome"));

            JSONObject settlement = response.getJSONObject("settlement");
            assertEquals(1, settlement.getInt("schema"));
            assertEquals("COMMITTED", settlement.getString("outcome"));
            assertTrue(settlement.getBoolean("semantic_applied"));
            assertTrue(settlement.getBoolean("reservation_consumed"));

            JSONObject workspace = response.getJSONObject("workspace");
            assertEquals(2, workspace.getInt("schema"));
            assertFalse(workspace.getJSONObject("storage").getBoolean("active"));
            assertEquals(0, workspace.getJSONObject("transaction").getInt("level"));
            assertFalse(response.has("transaction"));
            assertFalse(response.has("empty"));

            assertSame(root, user.getCurrentMind(),
                    "Settlement failure displaced the authoritative root");
            assertEquals(0, counter(root),
                    "Consumed settlement left a hidden child reservation");
            assertTrue(Boolean.TRUE.equals(root.query("?settlement_applied;")),
                    "COMMITTED settlement lost the already-applied semantic state");
        } finally {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Isolated test token may already be closed by cleanup.
            }
        }
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String source) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("request", URLEncoder.encode(source, "UTF-8"))));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "Settlement response is not JSON: " + response);
        return (JSONObject) response;
    }

    private IReactor<JSONObject> rejectingDelegate() {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                throw new AssertionError(
                        "Settlement lifecycle request escaped to legacy delegate");
            }
        };
    }

    private int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private static final class SettlementFailingMind extends Mind {
        private SettlementFailingMind(IUser user) throws Exception {
            super(user);
        }

        @Override
        public boolean commit(IMind child) throws Exception {
            boolean applied = super.commit(child);
            if (!applied) {
                throw new AssertionError(
                        "Synthetic committed settlement was semantically rejected");
            }
            throw new TransactionSettlementException(
                    TransactionSettlementException.Outcome.COMMITTED,
                    new IllegalStateException("root finalization failed"));
        }
    }
}
