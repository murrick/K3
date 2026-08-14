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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification gates for the canonical runtime workspace projection. */
public class WorkspaceStateReactorTest {

    @Test
    public void workspaceV2PreservesRuntimeAndSourceTransportTruth()
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
            assertWorkspaceV2(initial);
            assertFalse(initial.getJSONObject("workspace")
                    .getJSONObject("storage").getBoolean("active"));
            assertEquals(0, initial.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));

            JSONObject compile = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("compile", URLEncoder.encode("!alpha;", "UTF-8")));
            assertEquals("OK", compile.getString("result"), compile.toString());
            assertWorkspaceV2(compile);

            JSONObject save = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("put", "alpha"));
            assertEquals("OK", save.getString("result"), save.toString());
            assertWorkspaceV2(save);
            Path alpha = Paths.get(user.getSourceDir()).resolve("alpha.k");
            assertTrue(Files.isRegularFile(alpha));

            JSONObject modified = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("request", URLEncoder.encode("!beta;", "UTF-8")));
            assertEquals("OK", modified.getString("result"), modified.toString());
            assertWorkspaceV2(modified);
            assertEquals(0, modified.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));

            JSONObject sources = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("get", ""));
            assertEquals("alpha.k", sources.getJSONArray("list").getString(0));
            assertFalse(sources.has("sources"),
                    "Source list reintroduced active/current-file metadata");
            assertWorkspaceV2(sources);

            JSONObject delete = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("delete", "alpha"));
            assertEquals("OK", delete.getString("result"), delete.toString());
            assertFalse(Files.exists(alpha));
            assertWorkspaceV2(delete);

            // Source transport and storage are independent concerns. Start
            // storage qualification from a clean root so source rules do not
            // become an intentional transaction overlay on storage attachment.
            user.setCurrentMind(new Mind(user));

            JSONObject nested = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "nested/one"));
            assertEquals("OK", nested.getString("result"), nested.toString());
            assertWorkspaceV2(nested);
            assertStorage(nested, "nested.one",
                    "nested" + Enums.FILE_SEPARATOR + "one");

            JSONObject other = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "other"));
            assertEquals("OK", other.getString("result"), other.toString());
            assertWorkspaceV2(other);
            assertStorage(other, "other", "other");
            assertEquals(0, other.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));

            JSONObject dropNonActive = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("drop", "nested.one"));
            assertEquals("OK", dropNonActive.getString("result"),
                    dropNonActive.toString());
            assertWorkspaceV2(dropNonActive);
            assertStorage(dropNonActive, "other", "other");

            JSONObject storageList = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", ""));
            assertWorkspaceV2(storageList);
            JSONArray list = storageList.getJSONArray("list");
            assertTrue(contains(list, "other"));
            JSONArray structured = storageList.getJSONArray("storages");
            assertTrue(findStorage(structured, "other").getBoolean("active"));

            Path corruptBase = Paths.get(user.getDatabaseDir())
                    .resolve("corrupt-target");
            Files.createDirectories(corruptBase.getParent());
            corruptStore = Paths.get(corruptBase.toString() + ".store");
            Files.write(corruptStore, new byte[]{0x00, 0x01, 0x02});

            JSONObject rejectedCorrupt = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "corrupt-target"));
            assertEquals("error", rejectedCorrupt.getString("result"),
                    rejectedCorrupt.toString());
            assertEquals("storage_switch_failed",
                    rejectedCorrupt.getString("code"));
            assertFalse(rejectedCorrupt.has("required_action"));
            assertWorkspaceV2(rejectedCorrupt);
            assertStorage(rejectedCorrupt, "other", "other");

            JSONObject close = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("close", ""));
            assertEquals("OK", close.getString("result"), close.toString());
            assertWorkspaceV2(close);
            assertFalse(close.getJSONObject("workspace")
                    .getJSONObject("storage").getBoolean("active"));

            JSONObject used = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("used", ""));
            assertEquals("error", used.getString("result"));
            assertEquals("storage_not_used", used.getString("code"));
            assertWorkspaceV2(used);
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
    public void sourceWithoutFinalEolCompilesAndExportsSemanticProjection()
            throws Exception {
        String identity = "source-eof-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        String sourceName = "eof-semantic-" + UUID.randomUUID().toString() + ".k";
        Path sourcePath = Paths.get(user.getSourceDir()).resolve(sourceName);
        String exact = "!alpha(one);\n!omega(last);";
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
            assertWorkspaceV2(compile);

            JSONObject source = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("source", ""));
            assertEquals("OK", source.getString("result"), source.toString());
            String semantic = source.getString("source");
            assertTrue(semantic.contains("!alpha(one);"));
            assertTrue(semantic.contains("!omega(last);"));
            assertWorkspaceV2(source);

            JSONObject query = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("request", URLEncoder.encode("?omega(last);", "UTF-8")));
            assertEquals("OK", query.getString("result"), query.toString());
            assertEquals("yes", query.getString("response"),
                    "Last no-EOL statement did not participate in inference");
            assertWorkspaceV2(query);

            JSONObject save = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("put", sourceName));
            assertEquals("OK", save.getString("result"), save.toString());
            String persisted = new String(Files.readAllBytes(sourcePath),
                    StandardCharsets.UTF_8);
            assertTrue(persisted.contains("!alpha(one);"));
            assertTrue(persisted.contains("!omega(last);"));
            assertWorkspaceV2(save);
        } finally {
            if (token != null) {
                UserFactory.dropUser(user);
            }
            Files.deleteIfExists(sourcePath);
        }
    }

    private void assertWorkspaceV2(JSONObject response) {
        JSONObject workspace = response.getJSONObject("workspace");
        assertEquals(2, workspace.getInt("schema"));
        assertFalse(workspace.has("source"),
                "Workspace v2 reintroduced current-file source authority");
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
