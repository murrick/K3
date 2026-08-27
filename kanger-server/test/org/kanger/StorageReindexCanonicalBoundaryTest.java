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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageReindexCanonicalBoundaryTest {

    private static final String CORRUPT_TARGET = "corrupt-reindex-target";

    @Test
    void activeReindexFailureIsCanonicalWithoutDuplicateServerRestore()
            throws Exception {
        IUser user = createUser("active");
        try {
            String currentStorage = "reindex-current";
            ProbeMind probe = new ProbeMind(user);
            IMind mind = probe.useStorage(currentStorage);
            user.setCurrentMind(mind);
            assertSame(probe, mind);
            assertTrue(probe.compile("!current;"),
                    "Current storage source was rejected");
            String sourceBefore = probe.getSourceCode();
            String token = UserFactory.addUser(user);
            createCorruptTarget(user, CORRUPT_TARGET);

            JSONObject response = invoke(canonicalBoundary(), token, CORRUPT_TARGET);

            assertCanonical(response, user);
            assertTrue(user.getCurrentMind().isStorageUsed(),
                    "Failed reindex detached the current storage");
            assertEquals(currentStorage, user.getCurrentMind().getStorageName(),
                    "Failed reindex changed the selected storage");
            assertEquals(sourceBefore, user.getCurrentMind().getSourceCode(),
                    "Failed reindex changed the logical workspace");
            assertEquals(0, probe.closeStorageCalls(),
                    "Server repeated Core reindex restore by closing storage again");
            assertEquals(1, probe.useStorageCalls(),
                    "Server repeated Core reindex restore by reopening storage again");
        } finally {
            deleteGeneration(user, CORRUPT_TARGET);
            UserFactory.dropUser(user);
        }
    }

    @Test
    void legacyReindexFailureIsSanitizedBeforeCanonicalProjection()
            throws Exception {
        IUser user = createUser("legacy");
        try {
            String currentStorage = "legacy-reindex-current";
            IMind mind = new Mind(user).useStorage(currentStorage);
            user.setCurrentMind(mind);
            assertTrue(mind.compile("!legacy;"),
                    "Legacy storage source was rejected");
            String token = UserFactory.addUser(user);
            createCorruptTarget(user, CORRUPT_TARGET);

            JSONObject response = invoke(
                    new DestructiveStopLossReactor(new QueryProcessor()),
                    token,
                    CORRUPT_TARGET);

            assertEquals("error", response.getString("result"));
            assertEquals("storage_reindex_failed", response.getString("code"));
            assertEquals("Storage reindex failed", response.getString("description"));
            assertFalse(response.has("error"), response.toString());
            assertFalse(response.has("required_action"), response.toString());
            String rendered = response.toString();
            assertFalse(rendered.contains("Exception"), rendered);
            assertFalse(rendered.contains(user.getDatabaseDir()), rendered);
        } finally {
            deleteGeneration(user, CORRUPT_TARGET);
            UserFactory.dropUser(user);
        }
    }

    @Test
    void offlineReindexSuccessDoesNotReplayPublishedWorkspace() throws Exception {
        IUser user = createUser("offline");
        String target = "offline-reindex-target";
        try {
            IMind seed = new Mind(user).useStorage(target);
            user.setCurrentMind(seed);
            assertTrue(seed.compile("!stored;"),
                    "Persistent seed source was rejected");
            seed = seed.closeStorage();
            user.setCurrentMind(seed);
            assertFalse(seed.isStorageUsed());

            ProbeMind offline = new ProbeMind(user);
            user.setCurrentMind(offline);
            assertTrue(offline.compile("!offline;"),
                    "Offline source was rejected");
            String sourceBefore = offline.getSourceCode();
            int compileCallsBefore = offline.compileCalls();
            String token = UserFactory.addUser(user);

            JSONObject response = invoke(canonicalBoundary(), token, target);

            assertEquals("OK", response.getString("result"), response.toString());
            assertSame(offline, user.getCurrentMind(),
                    "Offline reindex replaced the published workspace");
            assertFalse(user.getCurrentMind().isStorageUsed(),
                    "Offline reindex attached the target storage");
            assertEquals(sourceBefore, user.getCurrentMind().getSourceCode(),
                    "Offline reindex replayed or changed the workspace");
            assertEquals(compileCallsBefore, offline.compileCalls(),
                    "Server replayed source already preserved by Core reindex");
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private IUser createUser(String suffix) throws Exception {
        String identity = "storage-reindex-boundary-" + suffix + "-"
                + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        return user;
    }

    private IReactor<JSONObject> canonicalBoundary() {
        return new CanonicalErrorBoundaryReactor(
                new DestructiveStopLossReactor(new QueryProcessor()));
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String storageName) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("reindex", storageName)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }

    private void assertCanonical(JSONObject response, IUser user) {
        assertEquals("error", response.getString("result"));
        assertEquals("storage_reindex_failed", response.getString("code"));
        assertEquals("Storage reindex failed", response.getString("description"));
        assertEquals("VERIFY_CURRENT_STATE",
                response.getString("required_action"));

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("operation", diagnostic.getString("domain"));
        assertEquals("storage_reindex_failed", diagnostic.getString("code"));
        assertFalse(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("unknown", diagnostic.getString("operation_outcome"));

        String rendered = response.toString();
        assertFalse(rendered.contains("Exception"), rendered);
        assertFalse(rendered.contains(user.getDatabaseDir()), rendered);
    }

    private void createCorruptTarget(IUser user, String storageName) throws Exception {
        Path base = Paths.get(user.getDatabaseDir()).resolve(storageName);
        Files.createDirectories(base.getParent());
        Files.write(Paths.get(base.toString() + ".store"),
                new byte[]{0x00, 0x01, 0x02, 0x03});
    }

    private void deleteGeneration(IUser user, String storageName) throws Exception {
        Path base = Paths.get(user.getDatabaseDir()).resolve(storageName);
        String[] suffixes = {".index", ".store", ".integrity", ".integrity.delta"};
        for (String suffix : suffixes) {
            Files.deleteIfExists(Paths.get(base.toString() + suffix));
        }
        Path directory = base.getParent();
        if (directory != null && Files.isDirectory(directory)) {
            String prefix = base.getFileName().toString() + ".wal.";
            try (java.nio.file.DirectoryStream<Path> stream =
                         Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)
                            && entry.getFileName().toString().startsWith(prefix)) {
                        Files.deleteIfExists(entry);
                    }
                }
            }
        }
    }

    private static final class ProbeMind extends Mind {
        private int compileCalls;
        private int closeStorageCalls;
        private int useStorageCalls;

        private ProbeMind(IUser user) throws Exception {
            super(user);
        }

        @Override
        public boolean compile(String source) throws Exception {
            compileCalls++;
            return super.compile(source);
        }

        @Override
        public IMind closeStorage() throws Exception {
            closeStorageCalls++;
            return super.closeStorage();
        }

        @Override
        public IMind useStorage(String name) throws Exception {
            useStorageCalls++;
            return super.useStorage(name);
        }

        private int compileCalls() {
            return compileCalls;
        }

        private int closeStorageCalls() {
            return closeStorageCalls;
        }

        private int useStorageCalls() {
            return useStorageCalls;
        }
    }
}
