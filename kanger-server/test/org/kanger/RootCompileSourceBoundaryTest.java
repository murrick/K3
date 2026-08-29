package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IRule;
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

    @Test
    public void parseFailedRootCompileDoesNotPoisonCanonicalBaseTree()
            throws Exception {
        String id = "root-compile-settlement-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(id, id);
        String token = null;
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            assertTrue(Boolean.TRUE.equals(root.query("!seed;")));
            user.setCurrentMind(root);
            token = UserFactory.addUser(user);

            long statementId = firstStoredRuleId(root);
            String before = SourceContextMaterializer.materializeCurrentLevel(root);

            IReactor<JSONObject> compile = new CanonicalErrorBoundaryReactor(
                    new WorkspaceStateReactor(
                            new CompileSourceBoundaryReactor(new QueryProcessor())));
            JSONObject failed = invoke(compile, token,
                    "!broken(\"unterminated);");

            assertEquals("error", failed.getString("result"), failed.toString());
            assertSame(root, user.getCurrentMind());
            assertEquals(before,
                    SourceContextMaterializer.materializeCurrentLevel(root));
            assertEquals(0, failed.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));

            assertSnapshotReadsStillWork(token);
            assertBaseTreeStillWorks(root, token, statementId);
        } finally {
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    @Test
    public void rejectedRootCompileDoesNotPoisonCanonicalBaseTree()
            throws Exception {
        String id = "root-compile-rejection-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(id, id);
        String token = null;
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            assertTrue(Boolean.TRUE.equals(root.query("!anchor;")));
            user.setCurrentMind(root);
            token = UserFactory.addUser(user);

            long statementId = firstStoredRuleId(root);
            String before = SourceContextMaterializer.materializeCurrentLevel(root);

            IReactor<JSONObject> compile = new CanonicalErrorBoundaryReactor(
                    new WorkspaceStateReactor(
                            new CompileSourceBoundaryReactor(new QueryProcessor())));
            JSONObject failed = invoke(compile, token,
                    "!collision;!~collision;");

            assertEquals("error", failed.getString("result"), failed.toString());
            assertSame(root, user.getCurrentMind());
            assertEquals(before,
                    SourceContextMaterializer.materializeCurrentLevel(root));
            assertEquals(0, failed.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));

            assertSnapshotReadsStillWork(token);
            assertBaseTreeStillWorks(root, token, statementId);
        } finally {
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    private void assertSnapshotReadsStillWork(String token) throws Exception {
        IReactor<JSONObject> reads = new CanonicalErrorBoundaryReactor(
                new WorkspaceStateReactor(new QueryProcessor()));

        JSONObject statements = queryRead(reads, token,
                new JSONObject().put("predicates", "").put("statements", true));
        assertOkList(statements, "statements");
        assertOkList(queryRead(reads, token,
                new JSONObject().put("functions", "")), "functions");
        assertOkList(queryRead(reads, token,
                new JSONObject().put("results", "")), "results");
        assertOkList(queryRead(reads, token,
                new JSONObject().put("solutions", "")), "solutions");
        assertOkList(queryRead(reads, token,
                new JSONObject().put("hypothesis", "")), "hypothesis");
        assertOkList(queryRead(reads, token,
                new JSONObject().put("log", "")), "log");
    }

    private void assertOkList(JSONObject data, String projection) {
        assertEquals("OK", data.getString("result"),
                projection + ": " + data.toString());
        assertTrue(data.has("size"), projection + " missing size: " + data);
        assertTrue(data.has("list"), projection + " missing list: " + data);
    }

    private JSONObject queryRead(IReactor<JSONObject> reactor, String token,
            JSONObject parameters) throws Exception {
        parameters.put("token", token);
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "query")
                .put("parameters", parameters));
        return (JSONObject) reactor.run(packet);
    }

    private void assertBaseTreeStillWorks(Mind root, String token, long statementId)
            throws Exception {
        CanonicalCommandRuntimeReactor runtime =
                new CanonicalCommandRuntimeReactor(new QueryProcessor());
        JSONObject tree = (JSONObject) runtime.run(canonicalCommand(
                token, "base tree " + statementId));

        assertEquals("OK", tree.getString("result"), tree.toString());
        assertEquals(1, tree.getInt("size"));
        assertEquals(statementId,
                tree.getJSONArray("list").getJSONObject(0).getLong("id"));
        assertSame(root, UserFactory.getUser(token).getCurrentMind());
    }

    private long firstStoredRuleId(Mind mind) throws Exception {
        for (IRule rule : mind.getRules()) {
            if (rule.isStored() && !rule.isDeleted(mind)) {
                return rule.getId();
            }
        }
        throw new AssertionError("No stored rule available for base tree qualification");
    }

    private JSONObject canonicalCommand(String token, String command) throws Exception {
        return new JSONObject()
                .put("body", new JSONObject()
                        .put("context", CanonicalCommandIngressReactor.CANONICAL_CONTEXT)
                        .put("parameters", new JSONObject().put("token", token)))
                .put(CanonicalCommandIngressReactor.INVOCATION_MARKER,
                        new CommandParser().parse(command));
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
