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
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageSwitchCanonicalBoundaryTest {

    private static final String TARGET = "corrupt-switch-target";

    @Test
    void activeStorageSwitchFailureIsCanonicalAndPreservesCurrentState() throws Exception {
        IUser user = createUser("active");
        try {
            String currentStorage = "switch-current";
            IMind mind = new Mind(user).useStorage(currentStorage);
            user.setCurrentMind(mind);
            assertTrue(mind.compile("!current;"), "Current storage source was rejected");
            String token = UserFactory.addUser(user);
            createCorruptTarget(user, TARGET);

            JSONObject response = invoke(boundary(), token, TARGET);

            assertCanonical(response, user, TARGET);
            assertTrue(user.getCurrentMind().isStorageUsed(),
                    "Failed switch detached the current storage");
            assertEquals(currentStorage, user.getCurrentMind().getStorageName(),
                    "Failed switch changed the selected storage");
            assertTrue(user.getCurrentMind().getSourceCode().contains("current"),
                    "Failed switch lost the current logical workspace");
        } finally {
            deleteGeneration(user, TARGET);
            UserFactory.dropUser(user);
        }
    }

    @Test
    void offlineStorageProbeFailureIsCanonicalAndPreservesWorkspace() throws Exception {
        IUser user = createUser("offline");
        try {
            IMind mind = new Mind(user);
            assertTrue(mind.compile("!offline;"), "Offline source was rejected");
            user.setCurrentMind(mind);
            String token = UserFactory.addUser(user);
            createCorruptTarget(user, TARGET);

            JSONObject response = invoke(boundary(), token, TARGET);

            assertCanonical(response, user, TARGET);
            assertFalse(user.getCurrentMind().isStorageUsed(),
                    "Failed preflight attached corrupt storage");
            assertTrue(user.getCurrentMind().getSourceCode().contains("offline"),
                    "Failed preflight changed the offline workspace");
        } finally {
            deleteGeneration(user, TARGET);
            UserFactory.dropUser(user);
        }
    }

    private IUser createUser(String suffix) throws Exception {
        String identity = "storage-switch-boundary-" + suffix + "-"
                + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        return user;
    }

    private IReactor<JSONObject> boundary() {
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
                        .put("use", storageName)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }

    private void assertCanonical(JSONObject response,
                                 IUser user,
                                 String storageName) {
        assertEquals("error", response.getString("result"));
        assertEquals("storage_switch_failed", response.getString("code"));
        assertEquals("Storage switch failed for " + storageName,
                response.getString("description"));
        assertEquals("VERIFY_CURRENT_STATE",
                response.getString("required_action"));

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("operation", diagnostic.getString("domain"));
        assertEquals("storage_switch_failed", diagnostic.getString("code"));
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
}
