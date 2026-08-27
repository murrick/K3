/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageDropCanonicalBoundaryTest {

    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    @org.junit.jupiter.api.Test
    void physicalIncompleteRemovalPreservesTypedLifecycleFailure() throws Exception {
        FailingLogUser user = createUser("physical");
        String token = null;
        String storageName = "stuckdrop";
        Path fakeStore = databaseBase(user, storageName + ".store");
        Path blocker = fakeStore.resolve("blocker");
        try {
            token = UserFactory.addUser(user);
            Files.createDirectories(fakeStore);
            Files.write(blocker, "keep".getBytes(StandardCharsets.UTF_8));

            JSONObject response = invoke(canonicalBoundary(), token, storageName);

            assertEquals("error", response.getString("result"));
            assertEquals("STORAGE_DELETE_INCOMPLETE", response.getString("code"));
            assertTrue(response.getString("description").contains(
                    "Database deletion was incomplete " + storageName),
                    response.toString());
            assertEquals("VERIFY_CURRENT_STATE",
                    response.getString("required_action"));

            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals(1, diagnostic.getInt("schema"));
            assertEquals("operation", diagnostic.getString("domain"));
            assertEquals("STORAGE_DELETE_INCOMPLETE",
                    diagnostic.getString("code"));
            assertFalse(diagnostic.getBoolean("retryable"));
            assertEquals("retain", diagnostic.getString("session_action"));
            assertEquals("unknown", diagnostic.getString("operation_outcome"));

            assertTrue(response.has("workspace"), response.toString());
            String rendered = response.toString();
            assertFalse(rendered.contains("IOException"), rendered);
            assertFalse(rendered.contains("Cannot delete storage artifact"), rendered);
            assertFalse(rendered.contains(user.databaseDirectory().toString()), rendered);
            assertTrue(Files.isDirectory(fakeStore));
            assertTrue(Files.isRegularFile(blocker));
            assertFalse(user.getCurrentMind().isStorageUsed());
        } finally {
            Files.deleteIfExists(blocker);
            Files.deleteIfExists(fakeStore);
            cleanup(user, token);
        }
    }

    @org.junit.jupiter.api.Test
    void postDeleteLoggingFailureDoesNotChangeSuccessfulDrop() throws Exception {
        FailingLogUser user = createUser("logging");
        String token = null;
        String storageName = "loggeddrop";
        try {
            IMind mind = user.getCurrentMind().useStorage(storageName);
            user.setCurrentMind(mind);
            assertTrue(mind.compile("!drop_logging;"));
            assertTrue(generationExists(user, storageName),
                    "Qualification storage generation was not created");

            token = UserFactory.addUser(user);
            user.failRegistrationLoginReads(true);

            JSONObject response = invoke(canonicalBoundary(), token, storageName);

            assertEquals("OK", response.getString("result"), response.toString());
            assertFalse(response.has("error"), response.toString());
            assertFalse(user.getCurrentMind().isStorageUsed(),
                    "Successful drop left storage attached after logging failure");
            assertFalse(generationExists(user, storageName),
                    "Successful drop left physical generation after logging failure");
        } finally {
            user.failRegistrationLoginReads(false);
            cleanup(user, token);
        }
    }

    private FailingLogUser createUser(String purpose) throws Exception {
        Path databaseDirectory = Files.createTempDirectory(
                "kanger-storage-drop-" + purpose + "-");
        FailingLogUser user = new FailingLogUser(databaseDirectory);
        user.setId(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        user.setDatabaseDir(databaseDirectory.toString() + Enums.FILE_SEPARATOR);
        new UDF().init(user);
        new DB().init(user);
        user.setCurrentMind(new Mind(user));
        return user;
    }

    private IReactor<JSONObject> canonicalBoundary() {
        return new CanonicalErrorBoundaryReactor(
                new WorkspaceStateReactor(
                        new DestructiveStopLossReactor(new QueryProcessor())));
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String storageName) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("drop", storageName)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }

    private Path databaseBase(FailingLogUser user, String name) {
        return user.databaseDirectory().resolve(name);
    }

    private boolean generationExists(FailingLogUser user, String storageName)
            throws Exception {
        Path base = databaseBase(user, storageName);
        for (String suffix : GENERATION_SUFFIXES) {
            if (Files.exists(Paths.get(base.toString() + suffix))) {
                return true;
            }
        }
        String prefix = base.getFileName().toString() + ".wal.";
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(user.databaseDirectory())) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)
                        && entry.getFileName().toString().startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void cleanup(FailingLogUser user, String token) throws Exception {
        user.failRegistrationLoginReads(false);
        if (token != null) {
            UserFactory.dropUser(user);
        }
        deleteTree(user.databaseDirectory());
    }

    private void deleteTree(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteTree(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static final class FailingLogUser extends User {
        private final Path databaseDirectory;
        private boolean failRegistrationLoginReads;

        private FailingLogUser(Path databaseDirectory) {
            this.databaseDirectory = databaseDirectory;
        }

        @Override
        public String getProperty(String key, String defaultValue) throws Exception {
            if (failRegistrationLoginReads && "reg.login".equals(key)) {
                throw new IllegalStateException("synthetic logging failure");
            }
            return super.getProperty(key, defaultValue);
        }

        private Path databaseDirectory() {
            return databaseDirectory;
        }

        private void failRegistrationLoginReads(boolean enabled) {
            failRegistrationLoginReads = enabled;
        }
    }
}
