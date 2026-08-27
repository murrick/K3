/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.StorageLifecycleException;
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

/** Qualification for storage lifecycle failures crossing the Mind boundary. */
class MindLifecycleStorageErrorBoundaryTest {

    @Test
    void storageLifecycleFailureReachesCanonicalBoundaryWithWorkspace()
            throws Exception {
        String identity = "mind-storage-boundary-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);

        try {
            MindLifecycleReactor lifecycle = new MindLifecycleReactor(
                    rejectingDelegate(),
                    new MindLifecycleReactor.ChildFactory() {
                        @Override
                        public Mind create(IMind parent) throws Exception {
                            return new FailingStorageMind(parent);
                        }
                    });
            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new WorkspaceStateReactor(lifecycle));

            JSONObject response = invoke(reactor, token, "?storage_failure;");

            assertEquals("error", response.getString("result"), response.toString());
            assertEquals("ACTIVE_TRANSACTION", response.getString("code"));
            assertEquals("TRANSACTION_RESOLUTION_REQUIRED",
                    response.getString("required_action"));
            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals(1, diagnostic.getInt("schema"));
            assertEquals("application", diagnostic.getString("domain"));
            assertEquals("ACTIVE_TRANSACTION", diagnostic.getString("code"));
            assertEquals("retain", diagnostic.getString("session_action"));
            assertEquals("confirmed", diagnostic.getString("operation_outcome"));

            JSONObject workspace = response.getJSONObject("workspace");
            assertEquals(2, workspace.getInt("schema"));
            assertFalse(workspace.getJSONObject("storage").getBoolean("active"));
            assertEquals(0, workspace.getJSONObject("transaction").getInt("level"));
            assertTrue(workspace.getJSONObject("transaction").getBoolean("empty"));
            assertFalse(response.has("transaction"));
            assertFalse(response.has("empty"));

            assertSame(root, user.getCurrentMind(),
                    "Storage failure displaced the authoritative root");
            assertEquals(0, counter(root),
                    "Storage failure leaked a hidden child reservation");
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
                "Lifecycle response is not JSON: " + response);
        return (JSONObject) response;
    }

    private IReactor<JSONObject> rejectingDelegate() {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                throw new AssertionError("Lifecycle request escaped to legacy delegate");
            }
        };
    }

    private int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private static final class FailingStorageMind extends Mind {
        private FailingStorageMind(IMind parent) throws Exception {
            super(parent);
        }

        @Override
        public Boolean query(String query) throws Exception {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.ACTIVE_TRANSACTION,
                    "Storage operation rejected");
        }
    }
}
