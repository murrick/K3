/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification gates for the canonical workspace projection. */
public class WorkspaceStateReactorTest {

    @Test
    public void sourceAndStorageProjectionRemainTruthfulAcrossOperations()
            throws Exception {
        String identity = "workspace-state-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        Path corruptStore = null;
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            IReactor<JSONObject> reactor = new WorkspaceStateReactor(
                    new ExplicitStorageLifecycleReactor(
                            new DestructiveStopLossReactor(
                                    new MindLifecycleReactor(new QueryProcessor()))));

            JSONObject initial = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("ping", ""));
            JSONObject initialSource = initial.getJSONObject("workspace")
                    .getJSONObject("source");
            assertEquals("missing",
                    initialSource.getString("repository_state"));
            assertFalse(initialSource.isNull("logical_name"));
            assertFalse(initialSource.getBoolean("has_text"));
            assertTrue(initialSource.getBoolean("dirty"));
            String defaultSourceName = initialSource.getString("logical_name");
            assertFalse(initial.getJSONObject("workspace")
                    .getJSONObject("storage").getBoolean("active"));

            JSONObject compile = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("compile", URLEncoder.encode("!alpha;", "UTF-8")));
            assertEquals("OK", compile.getString("result"), compile.toString());
            assertSource(compile, "missing", defaultSourceName, true);

            JSONObject save = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("put", "alpha"));
            assertEquals("OK", save.getString("result"), save.toString());
            assertSource(save, "saved", "alpha.k", false);
            assertEquals("alpha.k", user.getCurrentMind().getSourceFileName());

            JSONObject modified = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("request", URLEncoder.encode("!beta;", "UTF-8")));
            assertEquals("OK", modified.getString("result"), modified.toString());
            assertSource(modified, "modified", "alpha.k", true);
            assertEquals(0, modified.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));

            JSONObject sources = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("get", ""));
            assertEquals("alpha.k", sources.getJSONArray("list").getString(0));
            assertTrue(sources.getJSONArray("sources")
                    .getJSONObject(0).getBoolean("active"));

            JSONObject delete = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("delete", "alpha"));
            assertEquals("OK", delete.getString("result"), delete.toString());
            assertSource(delete, "missing", "alpha.k", true);

            // Source and storage UX are independent concerns. Start storage
            // qualification from a clean root so source rules do not become
            // an intentional transaction overlay on storage attachment.
            user.setCurrentMind(new Mind(user));

            JSONObject nested = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "nested/one"));
            assertEquals("OK", nested.getString("result"), nested.toString());
            assertStorage(nested, "nested.one",
                    "nested" + Enums.FILE_SEPARATOR + "one");

            // OPEN A -> use B is now an atomic Core semantic rebase. With
            // no explicit overlays in this fixture, workspace projection should
            // simply move from nested.one U0 to other U0 in one response.
            JSONObject other = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "other"));
            assertEquals("OK", other.getString("result"), other.toString());
            assertStorage(other, "other", "other");
            assertEquals(0, other.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));

            JSONObject dropNonActive = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("drop", "nested.one"));
            assertEquals("OK", dropNonActive.getString("result"),
                    dropNonActive.toString());
            assertStorage(dropNonActive, "other", "other");

            JSONObject storageList = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", ""));
            JSONArray list = storageList.getJSONArray("list");
            assertTrue(contains(list, "other"));
            JSONArray structured = storageList.getJSONArray("storages");
            assertTrue(findStorage(structured, "other").getBoolean("active"));

            Path corruptBase = Paths.get(user.getDatabaseDir())
                    .resolve("corrupt-target");
            Files.createDirectories(corruptBase.getParent());
            corruptStore = Paths.get(corruptBase.toString() + ".store");
            Files.write(corruptStore, new byte[]{0x00, 0x01, 0x02});

            // The server may probe a target before Core mutation. A corrupt
            // target therefore fails as a switch, while the active generation
            // and projected workspace remain the original "other" state.
            JSONObject rejectedCorrupt = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "corrupt-target"));
            assertEquals("error", rejectedCorrupt.getString("result"),
                    rejectedCorrupt.toString());
            assertEquals("storage_switch_failed",
                    rejectedCorrupt.getString("code"));
            assertFalse(rejectedCorrupt.has("required_action"));
            assertStorage(rejectedCorrupt, "other", "other");

            JSONObject close = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("close", ""));
            assertEquals("OK", close.getString("result"), close.toString());
            assertFalse(close.getJSONObject("workspace")
                    .getJSONObject("storage").getBoolean("active"));

            JSONObject used = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("used", ""));
            assertEquals("error", used.getString("result"));
            assertEquals("storage_not_used", used.getString("code"));
            assertFalse(used.getJSONObject("workspace")
                    .getJSONObject("storage").getBoolean("active"));
        } finally {
            if (corruptStore != null) {
                Files.deleteIfExists(corruptStore);
            }
            try {
                IMind mind = user.getCurrentMind();
                if (mind != null && mind.isStorageUsed()) {
                    user.setCurrentMind(mind.closeStorage());
                }
            } catch (Exception ignored) {
                // best-effort test cleanup
            }
            if (token != null) {
                UserFactory.dropUser(user);
            }
            Files.deleteIfExists(Paths.get(user.getSourceDir()).resolve("alpha.k"));
        }
    }

    @Test
    public void sourceWithoutFinalEolRemainsExactWhileLastStatementCompiles()
            throws Exception {
        String identity = "source-eof-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        String sourceName = "eof-exact-" + UUID.randomUUID().toString() + ".k";
        Path sourcePath = Paths.get(user.getSourceDir()).resolve(sourceName);
        String exact = "!alpha(one);\n!omega(last);";
        byte[] exactBytes = exact.getBytes(StandardCharsets.UTF_8);
        try {
            new UDF().init(user);
            new DB().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            IReactor<JSONObject> reactor = new WorkspaceStateReactor(
                    new MindLifecycleReactor(new QueryProcessor()));

            JSONObject compile = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("compile", URLEncoder.encode(exact, "UTF-8")));
            assertEquals("OK", compile.getString("result"), compile.toString());
            JSONObject projected = compile.getJSONObject("workspace")
                    .getJSONObject("source");
            assertEquals(exactBytes.length, projected.getInt("bytes_utf8"));

            JSONObject source = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("source", ""));
            assertEquals("OK", source.getString("result"), source.toString());
            assertEquals(exact, source.getString("source"),
                    "Server source response changed exact editor bytes");

            JSONObject query = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("request", URLEncoder.encode("?omega(last);", "UTF-8")));
            assertEquals("OK", query.getString("result"), query.toString());
            assertEquals("yes", query.getString("response"),
                    "Last no-EOL statement did not participate in inference");

            JSONObject save = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("put", sourceName));
            assertEquals("OK", save.getString("result"), save.toString());
            assertArrayEquals(exactBytes, Files.readAllBytes(sourcePath),
                    "Repository save changed exact source bytes");
            assertEquals(exactBytes.length, save.getJSONObject("workspace")
                    .getJSONObject("source").getInt("bytes_utf8"));
            assertEquals("saved", save.getJSONObject("workspace")
                    .getJSONObject("source").getString("repository_state"));
        } finally {
            SourceDocumentState.invalidate(user);
            if (token != null) {
                UserFactory.dropUser(user);
            }
            Files.deleteIfExists(sourcePath);
        }
    }

    private void assertSource(JSONObject response,
                              String state,
                              String name,
                              boolean dirty) {
        JSONObject source = response.getJSONObject("workspace")
                .getJSONObject("source");
        assertEquals(state, source.getString("repository_state"));
        if (name == null) {
            assertTrue(source.isNull("logical_name"));
        } else {
            assertEquals(name, source.getString("logical_name"));
        }
        assertEquals(dirty, source.getBoolean("dirty"));
    }

    private void assertStorage(JSONObject response,
                               String logical,
                               String canonical) {
        JSONObject storage = response.getJSONObject("workspace")
                .getJSONObject("storage");
        assertTrue(storage.getBoolean("active"));
        assertEquals(logical, storage.getString("logical_name"));
        assertEquals(canonical, storage.getString("canonical_name"));
        assertTrue(storage.getJSONObject("physical_generation")
                .getBoolean("present"));
    }

    private boolean contains(JSONArray values, String expected) {
        for (int index = 0; index < values.length(); index++) {
            if (expected.equals(values.optString(index))) {
                return true;
            }
        }
        return false;
    }

    private JSONObject findStorage(JSONArray values, String logical) {
        for (int index = 0; index < values.length(); index++) {
            JSONObject one = values.getJSONObject(index);
            if (logical.equals(one.optString("logical_name"))) {
                return one;
            }
        }
        throw new AssertionError("Storage not found: " + logical);
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
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
}
