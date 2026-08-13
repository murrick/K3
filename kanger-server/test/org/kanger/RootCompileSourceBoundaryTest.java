package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RootCompileSourceBoundaryTest {
    @Test
    public void rootCompileUsesAtomicReplacement() throws Exception {
        String id = "root-compile-boundary-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(id, id);
        String token = null;
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            assertTrue(Boolean.TRUE.equals(root.query("!old;")));
            user.setCurrentMind(root);
            token = UserFactory.addUser(user);
            IReactor<JSONObject> reactor = new CompileSourceBoundaryReactor(
                    new QueryProcessor());

            JSONObject accepted = invoke(reactor, token, "!new;");
            assertEquals("OK", accepted.getString("result"));
            assertEquals(0, accepted.getLong("transaction"));
            assertSame(root, user.getCurrentMind());
            assertFalse(Boolean.TRUE.equals(root.query("?old;")));
            assertTrue(Boolean.TRUE.equals(root.query("?new;")));

            String before = SourceContextMaterializer.materializeCurrentLevel(root);
            JSONObject rejected = invoke(reactor, token, "?new;");
            assertEquals("error", rejected.getString("result"));
            assertSame(root, user.getCurrentMind());
            assertEquals(before,
                    SourceContextMaterializer.materializeCurrentLevel(root));
        } finally {
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    private JSONObject invoke(IReactor<JSONObject> reactor, String token, String source)
            throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("compile", URLEncoder.encode(source, "UTF-8"))));
        return (JSONObject) reactor.run(packet);
    }
}
