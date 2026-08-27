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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for source import failures after transaction settlement. */
class GetSourceSettlementBoundaryTest {

    @Test
    void sourceImportPreservesCommittedOutcomeWhenRootFlushFailsAfterSettlement()
            throws Exception {
        Fixture fixture = fixture();
        Path source = null;
        try {
            IMind root = fixture.root.useStorage("source-get-settlement");
            fixture.user.setCurrentMind(root);

            source = Paths.get(fixture.user.getSourceDir()).resolve("settled-get.k");
            Files.createDirectories(source.getParent());
            Files.write(source,
                    "!source_get_settled;".getBytes(StandardCharsets.UTF_8));

            fixture.db.failFlush = true;
            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new GetSourceBoundaryReactor(rejectingDelegate()));
            JSONObject response = invokeGet(reactor, fixture.token, "settled-get.k");

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals("transaction_settlement_failed",
                    response.optString("code"), response.toString());
            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals("operation", diagnostic.getString("domain"));
            assertEquals("confirmed", diagnostic.getString("operation_outcome"));
            assertEquals("retain", diagnostic.getString("session_action"));

            JSONObject settlement = response.getJSONObject("settlement");
            assertEquals("COMMITTED", settlement.getString("outcome"));
            assertTrue(settlement.getBoolean("semantic_applied"));
            assertTrue(settlement.getBoolean("reservation_consumed"));
            assertFalse(response.has("source_recovery"),
                    "Committed source import was falsely presented as retryable repair input");
            assertSame(root, fixture.user.getCurrentMind(),
                    "Source import published a technical child after settlement failure");
            assertEquals(0, counter((Mind) root),
                    "Source import leaked or double-consumed the technical reservation");

            fixture.db.failFlush = false;
            assertTrue(Boolean.TRUE.equals(root.query("?source_get_settled;")),
                    "Committed source semantic delta disappeared after finalization failure");
        } finally {
            fixture.db.failFlush = false;
            if (source != null) {
                Files.deleteIfExists(source);
            }
            fixture.close();
        }
    }

    private JSONObject invokeGet(IReactor<JSONObject> reactor,
                                 String token,
                                 String fileName) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("get", fileName)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "Source import response is not JSON: " + response);
        return (JSONObject) response;
    }

    private IReactor<JSONObject> rejectingDelegate() {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                throw new AssertionError("Explicit get escaped source import boundary");
            }
        };
    }

    private int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private Fixture fixture() throws Exception {
        String identity = "source-get-settlement-" + UUID.randomUUID();
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
                // Test-owned token may already be closed by cleanup.
            }
        }
    }
}
