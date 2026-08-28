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
 * Regression for the Browser's raw canonical dialogue confirmation path around
 * erase. Confirmed erase follows the Console contract and clears the complete
 * workspace even when an explicit transaction is active.
 */
class ConfirmedEraseDialogueBoundaryTest {

    @Test
    void confirmedDialogueEraseClearsActiveTransactionThroughCanonicalIngress()
            throws Exception {
        String identity = "erase-dialogue-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            IReactor<JSONObject> lower = new WorkspaceStateReactor(
                    new CanonicalCommandRuntimeReactor(
                            new ExplicitStorageLifecycleReactor(
                                    new GetSourceBoundaryReactor(
                                            new DestructiveStopLossReactor(
                                                    new MindLifecycleReactor(
                                                            new QueryProcessor()))))));
            IReactor<JSONObject> reactor = new CanonicalCommandIngressReactor(lower);

            JSONObject use = command(lower, token, "use",
                    "erase-dialogue-" + UUID.randomUUID());
            assertEquals("OK", use.optString("result"), use.toString());

            JSONObject compile = query(lower, token, "compile",
                    URLEncoder.encode("!eraseprobe(one);", "UTF-8"));
            assertEquals("OK", compile.optString("result"), compile.toString());
            assertFalse(SourceContextMaterializer.materializeCurrentLevel(
                    user.getCurrentMind()).isEmpty(),
                    "Erase dialogue fixture did not publish source into U0");

            JSONObject transaction = query(lower, token, "transaction", "create");
            assertEquals("OK", transaction.optString("result"), transaction.toString());
            assertEquals(1, user.getCurrentMind().getTransactionLevel(),
                    "Erase dialogue fixture did not enter U1");

            JSONObject prompt = dialogue(reactor, token, "erase", false);
            assertEquals("confirmation_required", prompt.optString("result"),
                    prompt.toString());
            assertEquals("ERASE", prompt.optString("canonical_intent"),
                    prompt.toString());

            JSONObject erase = dialogue(reactor, token, "erase", true);
            assertEquals("OK", erase.optString("result"), erase.toString());
            assertEquals("ERASE", erase.optString("canonical_intent"),
                    erase.toString());
            assertTrue(erase.has("workspace"), erase.toString());
            assertTrue(erase.getJSONObject("workspace")
                    .getJSONObject("storage").getBoolean("active"),
                    "Confirmed dialogue erase detached storage");
            assertEquals(0, erase.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));
            assertTrue(erase.getJSONObject("workspace")
                    .getJSONObject("transaction").getBoolean("empty"));
            assertEquals(0, user.getCurrentMind().getTransactionLevel(),
                    "Confirmed dialogue erase left an explicit transaction active");
            assertFalse(((Mind) user.getCurrentMind()).hasPendingTransactions(),
                    "Confirmed dialogue erase left a hidden transaction reservation");
            assertEquals("", SourceContextMaterializer.materializeCurrentLevel(
                    user.getCurrentMind()),
                    "Confirmed dialogue erase left source-representable state behind");
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

    private static JSONObject dialogue(IReactor<JSONObject> reactor,
                                       String token,
                                       String line,
                                       boolean confirmed) throws Exception {
        JSONObject parameters = new JSONObject()
                .put("token", token)
                .put("line", line);
        if (confirmed) {
            parameters.put("confirmed", true);
        }
        return invoke(reactor, "dialogue", parameters);
    }

    private static JSONObject command(IReactor<JSONObject> reactor,
                                      String token,
                                      String name,
                                      Object value) throws Exception {
        return invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put(name, value));
    }

    private static JSONObject query(IReactor<JSONObject> reactor,
                                    String token,
                                    String name,
                                    Object value) throws Exception {
        return invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put(name, value));
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
