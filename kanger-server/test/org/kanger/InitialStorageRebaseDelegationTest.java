/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for legacy first-storage attachment delegation to Core. */
class InitialStorageRebaseDelegationTest {

    @Test
    void legacyUseKeepsNonemptyOfflineRootAtU0AcrossReopen() throws Exception {
        String identity = "initial-storage-rebase-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            new DB().init(user);

            Mind offlineRoot = new Mind(user);
            user.setCurrentMind(offlineRoot);
            assertTrue(offlineRoot.compile("!offline_root_fact;"),
                    "Offline root qualification source was rejected");
            assertEquals(0, offlineRoot.getTransactionLevel(),
                    "Qualification did not start at U0");

            String token = UserFactory.addUser(user);
            String storageName = "initial-rebase-" + UUID.randomUUID();
            DestructiveStopLossReactor reactor = new DestructiveStopLossReactor(
                    new QueryProcessor());

            JSONObject response = invoke(reactor, token, storageName);
            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals(0, response.optInt("transaction", -1), response.toString());

            IMind attached = user.getCurrentMind();
            assertEquals(0, attached.getTransactionLevel(),
                    "Legacy use inserted an artificial transaction boundary");
            assertTrue(attached.isStorageUsed(),
                    "Legacy use did not attach storage");
            assertEquals(storageName, attached.getStorageName(),
                    "Legacy use attached the wrong storage");
            assertTrue(Boolean.TRUE.equals(attached.query("?offline_root_fact;")),
                    "Offline U0 authorial state was not assimilated into storage");

            IMind reopened = attached.closeStorage();
            user.setCurrentMind(reopened);
            reopened = reopened.useStorage(storageName);
            user.setCurrentMind(reopened);

            assertEquals(0, reopened.getTransactionLevel(),
                    "Reopen changed the assimilated U0 transaction depth");
            assertTrue(Boolean.TRUE.equals(reopened.query("?offline_root_fact;")),
                    "Assimilated offline U0 state did not persist across reopen");
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private JSONObject invoke(DestructiveStopLossReactor reactor,
                              String token,
                              String storageName) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("use", storageName)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }
}
