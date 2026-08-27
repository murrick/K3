/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IReactor;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceSaveCanonicalBoundaryTest {

    @Test
    void physicalSaveFailureIsCanonicalAndRequiresStateVerification()
            throws Exception {
        FailingLogUser user = createUser("physical");
        Path target = null;
        Path child = null;
        String token = null;
        try {
            assertTrue(user.getCurrentMind().compile("!save_boundary;"));
            token = UserFactory.addUser(user);

            target = user.sourceDirectory().resolve("stuck.k");
            Files.createDirectories(target);
            child = target.resolve("content.txt");
            Files.write(child, "keep".getBytes(StandardCharsets.UTF_8));

            JSONObject response = invoke(canonicalBoundary(), token, "stuck.k");

            assertEquals("error", response.getString("result"));
            assertEquals("source_save_failed", response.getString("code"));
            assertEquals("Source save failed", response.getString("description"));
            assertEquals("VERIFY_CURRENT_STATE",
                    response.getString("required_action"));

            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals(1, diagnostic.getInt("schema"));
            assertEquals("operation", diagnostic.getString("domain"));
            assertEquals("source_save_failed", diagnostic.getString("code"));
            assertFalse(diagnostic.getBoolean("retryable"));
            assertEquals("retain", diagnostic.getString("session_action"));
            assertEquals("unknown", diagnostic.getString("operation_outcome"));

            String rendered = response.toString();
            assertFalse(rendered.contains("Exception"), rendered);
            assertFalse(rendered.contains(target.toString()), rendered);
            assertTrue(Files.isDirectory(target),
                    "Failed save replaced the existing source directory");
            assertTrue(Files.isRegularFile(child),
                    "Failed save changed existing source content");
        } finally {
            if (child != null) {
                Files.deleteIfExists(child);
            }
            if (target != null) {
                Files.deleteIfExists(target);
            }
            cleanup(user, token);
        }
    }

    @Test
    void postPublishLoggingFailureDoesNotChangeSuccessfulSave() throws Exception {
        FailingLogUser user = createUser("logging");
        String token = null;
        Path target = user.sourceDirectory().resolve("published.k");
        try {
            assertTrue(user.getCurrentMind().compile("!published;"));
            token = UserFactory.addUser(user);
            user.failRegistrationLoginReads(true);

            JSONObject response = invoke(canonicalBoundary(), token, "published.k");

            assertEquals("OK", response.getString("result"), response.toString());
            assertFalse(response.has("error"), response.toString());
            assertTrue(Files.isRegularFile(target),
                    "Successful publication disappeared after logging failure");
            String persisted = new String(Files.readAllBytes(target),
                    StandardCharsets.UTF_8);
            assertTrue(persisted.contains("!published;"), persisted);
        } finally {
            user.failRegistrationLoginReads(false);
            Files.deleteIfExists(target);
            cleanup(user, token);
        }
    }

    private FailingLogUser createUser(String suffix) throws Exception {
        Path sourceDirectory = Files.createTempDirectory(
                "kanger-source-save-" + suffix + "-");
        FailingLogUser user = new FailingLogUser(sourceDirectory);
        user.setId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        user.setSourceDir(sourceDirectory.toString());
        user.setDatabaseDir(sourceDirectory.toString());
        new UDF().init(user);
        new DB().init(user);
        user.setCurrentMind(new Mind(user));
        return user;
    }

    private IReactor<JSONObject> canonicalBoundary() {
        return new CanonicalErrorBoundaryReactor(
                new DestructiveStopLossReactor(new QueryProcessor()));
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String fileName) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("put", fileName)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }

    private void cleanup(FailingLogUser user, String token) throws Exception {
        user.failRegistrationLoginReads(false);
        if (token != null) {
            UserFactory.dropUser(user);
        }
        try (java.nio.file.DirectoryStream<Path> stream =
                     Files.newDirectoryStream(user.sourceDirectory())) {
            for (Path entry : stream) {
                Files.deleteIfExists(entry);
            }
        }
        Files.deleteIfExists(user.sourceDirectory());
    }

    private static final class FailingLogUser extends User {
        private final Path sourceDirectory;
        private boolean failRegistrationLoginReads;

        private FailingLogUser(Path sourceDirectory) {
            this.sourceDirectory = sourceDirectory;
        }

        @Override
        public String getProperty(String key, String defaultValue) throws Exception {
            if (failRegistrationLoginReads && "reg.login".equals(key)) {
                throw new IllegalStateException("synthetic logging failure");
            }
            return super.getProperty(key, defaultValue);
        }

        private Path sourceDirectory() {
            return sourceDirectory;
        }

        private void failRegistrationLoginReads(boolean enabled) {
            failRegistrationLoginReads = enabled;
        }
    }
}
