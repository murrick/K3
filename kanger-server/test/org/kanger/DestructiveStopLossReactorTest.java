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
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification gates for the 3.5.2.2 destructive-operation stop-loss boundary. */
public class DestructiveStopLossReactorTest {

    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    @Test
    public void destructiveBoundaryPreservesAuthoritativeState() throws Exception {
        String identity = "stop-loss-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            new DB().init(user);

            IMind mind = new Mind(user);
            String storageName = "stop-loss" + Enums.FILE_SEPARATOR + "compile";
            mind = mind.useStorage(storageName);
            user.setCurrentMind(mind);
            assertTrue(mind.compile("!a;"), "Initial source was rejected");

            String token = UserFactory.addUser(user);
            DestructiveStopLossReactor reactor = new DestructiveStopLossReactor(
                    new QueryProcessor());

            rejectedCompilePreservesGeneration(
                    reactor, user, token, storageName);
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private void rejectedCompilePreservesGeneration(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token,
            String storageName) throws Exception {
        String sourceBefore = user.getCurrentMind().getSourceCode();
        Map<String, String> generationBefore = hashGeneration(user, storageName);

        JSONObject response = invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put("compile", URLEncoder.encode("!conflict; ?conflict;", "UTF-8")));

        assertEquals("error", response.optString("result"), response.toString());
        assertEquals(sourceBefore, user.getCurrentMind().getSourceCode(),
                "Rejected Compile changed the logical workspace");
        assertEquals(generationBefore, hashGeneration(user, storageName),
                "Rejected Compile changed the persistent generation");
    }

    private JSONObject invoke(
            DestructiveStopLossReactor reactor,
            String context,
            JSONObject parameters) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }

    private Map<String, String> hashGeneration(
            IUser user, String storageName) throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        Path base = Paths.get(user.getDatabaseDir()).resolve(storageName);
        for (String suffix : GENERATION_SUFFIXES) {
            Path file = Paths.get(base.toString() + suffix);
            if (Files.exists(file)) {
                result.put(suffix, sha256(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    private String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte one : digest) {
            result.append(String.format("%02x", one & 0xff));
        }
        return result.toString();
    }
}
