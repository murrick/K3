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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

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

            // OPEN -> use is no longer a storage switch. The active workspace
            // must remain projected unchanged until an explicit close.
            JSONObject rejectedOther = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "other"));
            assertEquals("error", rejectedOther.getString("result"),
                    rejectedOther.toString());
            assertEquals("storage_already_open",
                    rejectedOther.getString("code"));
            assertStorage(rejectedOther, "nested.one",
                    "nested" + Enums.FILE_SEPARATOR + "one");

            JSONObject closeNested = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("close", ""));
            assertEquals("OK", closeNested.getString("result"),
                    closeNested.toString());
            assertFalse(closeNested.getJSONObject("workspace")
                    .getJSONObject("storage").getBoolean("active"));

            JSONObject other = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "other"));
            assertEquals("OK", other.getString("result"), other.toString());
            assertStorage(other, "other", "other");

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

            // Rejection precedes target open and validation; even a deliberately
            // corrupt target cannot replace or damage the active projection.
            JSONObject rejectedCorrupt = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "corrupt-target"));
            assertEquals("error", rejectedCorrupt.getString("result"),
                    rejectedCorrupt.toString());
            assertEquals("storage_already_open",
                    rejectedCorrupt.getString("code"));
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
