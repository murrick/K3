/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for canonical recoverable source-import failures. */
class SourceImportCanonicalBoundaryTest {

    @Test
    void classifiedSourceImportFailureCarriesExactRecoveryWithoutLeakingCause()
            throws Exception {
        String exactSource = "!source_recovery_contract;";
        SourceImportException failure = new SourceImportException(
                "recovery.k",
                exactSource,
                new IllegalStateException("internal-secret-detail"));
        CanonicalErrorBoundaryReactor boundary = new CanonicalErrorBoundaryReactor(
                throwing(failure));

        JSONObject response = response(boundary, new JSONObject());
        assertEquals("error", response.getString("result"));
        assertEquals("source_load_failed", response.getString("code"));
        assertEquals("Source import failed for recovery.k",
                response.getString("description"));
        assertFalse(response.getString("description").contains("internal-secret-detail"));

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("operation", diagnostic.getString("domain"));
        assertEquals("source_load_failed", diagnostic.getString("code"));
        assertFalse(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("not_applied", diagnostic.getString("operation_outcome"));

        JSONObject recovery = response.getJSONObject("source_recovery");
        assertEquals(1, recovery.getInt("schema"));
        assertEquals("recovery.k", recovery.getString("logical_name"));
        assertEquals(exactSource, recovery.getString("text"));
    }

    @Test
    void explicitGetOperationalFailureUsesCanonicalRecoveryBeforeSettlement()
            throws Exception {
        Fixture fixture = fixture();
        Path source = null;
        try {
            String exactSource = "!source_get_recoverable;";
            source = Paths.get(fixture.user.getSourceDir()).resolve("recoverable-get.k");
            Files.createDirectories(source.getParent());
            Files.write(source, exactSource.getBytes(StandardCharsets.UTF_8));

            fixture.user.setProperty("flood.limit", "not-a-number");
            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                    new GetSourceBoundaryReactor(rejectingDelegate()));
            JSONObject response = response(reactor,
                    getPacket(fixture.token, "recoverable-get.k"));

            assertEquals("error", response.getString("result"), response.toString());
            assertEquals("source_load_failed", response.getString("code"), response.toString());
            assertEquals("Source import failed for recoverable-get.k",
                    response.getString("description"));
            assertFalse(response.getString("description").contains("NumberFormatException"));
            assertFalse(response.getString("description").contains("not-a-number"));

            JSONObject diagnostic = response.getJSONObject("error");
            assertEquals("operation", diagnostic.getString("domain"));
            assertEquals("not_applied", diagnostic.getString("operation_outcome"));

            JSONObject recovery = response.getJSONObject("source_recovery");
            assertEquals(1, recovery.getInt("schema"));
            assertEquals("recoverable-get.k", recovery.getString("logical_name"));
            assertEquals(exactSource, recovery.getString("text"));

            assertSame(fixture.root, fixture.user.getCurrentMind(),
                    "Recoverable source failure published a technical child");
            assertEquals(0, counter(fixture.root),
                    "Pre-settlement source failure leaked a technical reservation");
        } finally {
            fixture.user.setProperty("flood.limit", "10000");
            if (source != null) {
                Files.deleteIfExists(source);
            }
            fixture.close();
        }
    }

    private JSONObject getPacket(String token, String fileName) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("get", fileName)));
    }

    private JSONObject response(IReactor<JSONObject> reactor, JSONObject packet)
            throws Exception {
        Object result = reactor.run(packet);
        assertTrue(result instanceof JSONObject,
                "Source import response is not JSON: " + result);
        return (JSONObject) result;
    }

    private IReactor<JSONObject> throwing(final Exception failure) {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) throws Exception {
                throw failure;
            }
        };
    }

    private IReactor<JSONObject> rejectingDelegate() {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                throw new AssertionError("Explicit get escaped source import boundary");
            }
        };
    }

    private int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private Fixture fixture() throws Exception {
        String identity = "source-import-canonical-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        DB db = new DB();
        db.init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, root, token);
    }

    private static final class Fixture {
        private final IUser user;
        private final Mind root;
        private final String token;

        private Fixture(IUser user, Mind root, String token) {
            this.user = user;
            this.root = root;
            this.token = token;
        }

        private void close() throws Exception {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Test-owned token may already be closed by cleanup.
            }
        }
    }
}
