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
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression gate for the historical bare {@code ?} Core program check. */
class BareCoreCheckLifecycleTest {

    @Test
    void bareQuestionMarkChecksAuthoritativeProgramWithoutOuterQueryChild()
            throws Exception {
        String identity = "bare-core-check-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);

        try {
            assertTrue(Boolean.TRUE.equals(root.query("!bare_core_check_fact;")),
                    "Fixture fact was not accepted");

            AtomicInteger requestLocalChildren = new AtomicInteger();
            MindLifecycleReactor reactor = new MindLifecycleReactor(
                    rejectingDelegate(),
                    new MindLifecycleReactor.ChildFactory() {
                        @Override
                        public Mind create(IMind parent) throws Exception {
                            requestLocalChildren.incrementAndGet();
                            return new Mind(parent);
                        }
                    });

            JSONObject response = invoke(reactor, token, "?");

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals("yes", response.optString("response"), response.toString());
            assertTrue(response.optString("description")
                            .contains("SUCCESS: No Collisions in Program"),
                    response.toString());
            assertSame(root, user.getCurrentMind(),
                    "Bare check displaced the authoritative Mind");
            assertEquals(0, requestLocalChildren.get(),
                    "Bare check was incorrectly wrapped in an outer request child");
            assertEquals(0, transactionCounter(root),
                    "Bare check leaked its Core-owned check transaction");
            assertEquals(0, response.optInt("transaction", -1));
        } finally {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Test owns the token; tolerate cleanup after an earlier close.
            }
        }
    }

    private static JSONObject invoke(MindLifecycleReactor reactor,
                                     String token,
                                     String source) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("request", URLEncoder.encode(source, "UTF-8"))));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "Lifecycle response is not JSON: " + response);
        return (JSONObject) response;
    }

    private static IReactor<JSONObject> rejectingDelegate() {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                throw new AssertionError(
                        "Bare Core check escaped the Mind lifecycle authority");
            }
        };
    }

    private static int transactionCounter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }
}
