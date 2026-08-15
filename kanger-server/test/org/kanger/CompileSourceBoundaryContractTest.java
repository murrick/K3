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

public class CompileSourceBoundaryContractTest {
    @Test
    public void nestedCompileUsesReplacementBoundary() throws Exception {
        String id = "compile-boundary-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(id, id);
        String token = null;
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            assertTrue(Boolean.TRUE.equals(root.query("!root;")));
            Mind old = new Mind(root);
            assertTrue(Boolean.TRUE.equals(old.query("!old;")));
            user.setCurrentMind(old);
            token = UserFactory.addUser(user);

            IReactor<JSONObject> fallback = new QueryProcessor();
            IReactor<JSONObject> reactor = new CompileSourceBoundaryReactor(fallback);
            JSONObject accepted = invoke(reactor, token, "!new;");
            assertEquals("OK", accepted.getString("result"));
            assertEquals(1, accepted.getLong("transaction"));

            Mind current = (Mind) user.getCurrentMind();
            assertFalse(Boolean.TRUE.equals(current.query("?old;")));
            assertTrue(Boolean.TRUE.equals(current.query("?new;")));
            Mind beforeReject = current;

            JSONObject rejected = invoke(reactor, token, "?new;");
            assertEquals("error", rejected.getString("result"));
            assertEquals("compile_rejected", rejected.getString("code"));
            assertSame(beforeReject, user.getCurrentMind());
            root.release((Mind) user.getCurrentMind());
            user.setCurrentMind(root);
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
