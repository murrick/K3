/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression boundary for lifecycle failures that legacy diagnostic helpers
 * historically printed to stderr and then swallowed.
 */
public class StorageLifecycleDiagnosticHygieneTest {

    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    @Test
    void probeCloseReopenAndActiveDropDoNotHideLifecycleFailures() throws Exception {
        String identity = "storage-diagnostic-hygiene-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedBytes = new ByteArrayOutputStream();

        try {
            new UDF().init(user);
            new DB().init(user);
            Mind root = new Mind(user);
            user.setCurrentMind(root);
            String token = UserFactory.addUser(user);
            IReactor<JSONObject> reactor = new ExplicitStorageLifecycleReactor(
                    new DestructiveStopLossReactor(
                            new MindLifecycleReactor(new QueryProcessor())));

            String displayName = "diagnostic.one";
            String storageName = "diagnostic" + Enums.FILE_SEPARATOR + "one";

            try (PrintStream captured = new PrintStream(
                    capturedBytes, true, StandardCharsets.UTF_8.name())) {
                System.setErr(captured);

                JSONObject firstUse = command(reactor, token, "use", displayName);
                assertEquals("OK", firstUse.optString("result"), firstUse.toString());
                assertTrue(user.getCurrentMind().isStorageUsed());
                assertEquals(storageName, user.getCurrentMind().getStorageName());

                JSONObject create = transaction(reactor, token, "create");
                assertEquals("OK", create.optString("result"), create.toString());
                JSONObject transientInsert = query(reactor, token,
                        "!transient_diagnostic_predicate;");
                assertEquals("OK", transientInsert.optString("result"),
                        transientInsert.toString());
                JSONObject rollback = transaction(reactor, token, "rollback");
                assertEquals("OK", rollback.optString("result"), rollback.toString());

                JSONObject close = command(reactor, token, "close", "");
                assertEquals("OK", close.optString("result"), close.toString());
                assertFalse(user.getCurrentMind().isStorageUsed());

                /*
                 * Reopening an existing generation goes through probeStorage(),
                 * whose probe close previously triggered Predicate.toString()
                 * after the dependent Term had already been packed away.
                 */
                JSONObject secondUse = command(reactor, token, "use", displayName);
                assertEquals("OK", secondUse.optString("result"), secondUse.toString());
                assertTrue(user.getCurrentMind().isStorageUsed());

                JSONObject durableInsert = query(reactor, token, "!drop_diagnostic;");
                assertEquals("OK", durableInsert.optString("result"),
                        durableInsert.toString());

                JSONObject drop = command(reactor, token, "drop", displayName);
                assertEquals("OK", drop.optString("result"), drop.toString());
                IMind afterDrop = user.getCurrentMind();
                assertFalse(afterDrop.isStorageUsed(),
                        "Dropping the active database left it attached");
                assertEquals("", afterDrop.getStorageName(),
                        "Dropped active database still has a storage identity");
                assertTrue(afterDrop.isEmptyLevel(),
                        "Dropped active database left a semantic level behind");
                assertFalse(generationExists(user, storageName),
                        "Dropped active database left generation files");
            } finally {
                System.setErr(originalErr);
            }

            String diagnostics = capturedBytes.toString(StandardCharsets.UTF_8.name());
            assertFalse(diagnostics.contains("NullPointerException"), diagnostics);
            assertFalse(diagnostics.contains("Cannot invoke"), diagnostics);
        } finally {
            System.setErr(originalErr);
            UserFactory.dropUser(user);
        }
    }

    private JSONObject command(IReactor<JSONObject> reactor,
                               String token,
                               String name,
                               String value) throws Exception {
        return invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put(name, value));
    }

    private JSONObject transaction(IReactor<JSONObject> reactor,
                                   String token,
                                   String action) throws Exception {
        return invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put("transaction", action));
    }

    private JSONObject query(IReactor<JSONObject> reactor,
                            String token,
                            String source) throws Exception {
        return invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put("request", URLEncoder.encode(source, "UTF-8")));
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                             String context,
                             JSONObject parameters) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "Lifecycle response is not JSON: " + response);
        return (JSONObject) response;
    }

    private boolean generationExists(IUser user, String storageName) {
        Path base = Paths.get(user.getDatabaseDir()).resolve(storageName);
        for (String suffix : GENERATION_SUFFIXES) {
            if (Files.exists(Paths.get(base.toString() + suffix))) {
                return true;
            }
        }
        return false;
    }
}
