/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for semantic source view/export before production wiring. */
public class SourceProjectionBoundaryReactorTest {

    @Test
    public void sourceViewAndPutUseCurrentLevelWithoutFileIdentityRebind()
            throws Exception {
        String identity = "source-projection-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        String fileName = "projection-" + UUID.randomUUID().toString() + ".k";
        Path target = Paths.get(user.getSourceDir()).resolve(fileName);
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            assertTrue(root.compile("!root;"));
            Mind child = new Mind(root);
            assertTrue(child.compile("!child;"));
            user.setCurrentMind(child);
            token = UserFactory.addUser(user);

            IReactor<JSONObject> reactor = new SourceProjectionBoundaryReactor(
                    new IReactor<JSONObject>() {
                        @Override
                        public Object run(JSONObject packet) {
                            throw new AssertionError("Source boundary delegated owned operation");
                        }
                    });

            JSONObject source = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("source", ""));
            assertEquals("OK", source.getString("result"));
            assertEquals(1, source.getLong("transaction"));
            assertTrue(source.getString("source").contains("!child;"));
            assertFalse(source.getString("source").contains("!root;"));

            String identityBefore = child.getSourceFileName();
            JSONObject put = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("put", fileName));
            assertEquals("OK", put.getString("result"), put.toString());
            String stored = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            assertTrue(stored.contains("!child;"));
            assertFalse(stored.contains("!root;"));
            assertEquals(identityBefore, child.getSourceFileName(),
                    "Transport export rebound obsolete current-file identity");
        } finally {
            if (token != null) {
                UserFactory.dropUser(user);
            }
            Files.deleteIfExists(target);
        }
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String context,
                              JSONObject parameters) throws Exception {
        Object response = reactor.run(new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters)));
        assertTrue(response instanceof JSONObject);
        return (JSONObject) response;
    }
}
