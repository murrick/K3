/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production-shaped regression for the erase -> workspace projection seam.
 */
class EraseWorkspaceProjectionBoundaryTest {

    @Test
    void eraseKeepsOpenStorageProjectableAfterSettlement() throws Exception {
        String identity = "erase-workspace-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            IReactor<JSONObject> reactor = new WorkspaceStateReactor(
                    new ExplicitStorageLifecycleReactor(
                            new DestructiveStopLossReactor(
                                    new MindLifecycleReactor(new QueryProcessor()))));

            JSONObject use = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "erase-projection-" + UUID.randomUUID()));
            assertEquals("OK", use.optString("result"), use.toString());

            JSONObject compile = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("compile", URLEncoder.encode("!eraseprobe(one);", "UTF-8")));
            assertEquals("OK", compile.optString("result"), compile.toString());
            assertFalse(SourceContextMaterializer.materializeCurrentLevel(
                    user.getCurrentMind()).isEmpty(),
                    "Erase fixture did not publish source into U0");

            JSONObject erase = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("erase", ""));

            assertEquals("OK", erase.optString("result"), erase.toString());
            JSONObject workspace = erase.getJSONObject("workspace");
            assertEquals(2, workspace.getInt("schema"));
            assertTrue(workspace.getJSONObject("storage").getBoolean("active"),
                    "Erase detached the open storage");
            assertEquals(0, workspace.getJSONObject("transaction").getInt("level"));
            assertTrue(workspace.getJSONObject("transaction").getBoolean("empty"),
                    "Erase response could not project an empty U0");
            assertEquals("", SourceContextMaterializer.materializeCurrentLevel(
                    user.getCurrentMind()),
                    "Erase left source-representable state behind");
        } finally {
            try {
                IMind mind = user.getCurrentMind();
                if (mind != null && mind.isStorageUsed()) {
                    user.setCurrentMind(mind.closeStorage());
                }
            } catch (Exception ignored) {
                // best-effort fixture cleanup
            }
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    private static JSONObject invoke(IReactor<JSONObject> reactor,
                                     String context,
                                     JSONObject parameters) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not JSON: " + response);
        return (JSONObject) response;
    }
}
