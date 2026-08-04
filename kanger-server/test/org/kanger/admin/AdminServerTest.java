package org.kanger.admin;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kanger.account.AccountDeletion;
import org.kanger.account.AccountDeletionState;
import org.kanger.account.ActiveAccount;
import org.kanger.account.ActiveAccountRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminServerTest {

    private static final String TOKEN =
            "0123456789abcdefghijklmnopqrstuvwxyz-ADMIN-TOKEN";

    private AdminServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void rejectsNonLoopbackBindingBeforeOpeningListener() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new AdminServer(
                        InetAddress.getByName("0.0.0.0"),
                        0,
                        1024,
                        TOKEN,
                        new RecordingLifecycle()));
    }

    @Test
    void missingOrWrongBearerNeverInvokesLifecycle() throws Exception {
        RecordingLifecycle lifecycle = new RecordingLifecycle();
        start(lifecycle);
        String body = new JSONObject()
                .put("login", "rick")
                .put("password", "top-secret-password")
                .toString();

        Response missing = request("/create-user", null, body);
        Response wrong = request("/create-user", "wrong-token", body);

        assertEquals(401, missing.status);
        assertEquals(401, wrong.status);
        assertEquals(0, lifecycle.createCalls.get());
        assertFalse(missing.body.contains("top-secret-password"));
        assertFalse(wrong.body.contains(TOKEN));
    }

    @Test
    void authenticatedCreateDelegatesExactlyOnceAndReturnsNoSecret()
            throws Exception {
        RecordingLifecycle lifecycle = new RecordingLifecycle();
        start(lifecycle);
        String body = new JSONObject()
                .put("login", "rick")
                .put("password", "top-secret-password")
                .put("email", "rick@example.org")
                .toString();

        Response response = request("/create-user", TOKEN, body);

        assertEquals(200, response.status);
        assertEquals(1, lifecycle.createCalls.get());
        assertEquals("rick", lifecycle.lastCreate.getLogin());
        assertTrue(response.body.contains("\"state\":\"ACTIVE\""));
        assertFalse(response.body.contains("top-secret-password"));
        assertFalse(response.body.contains(TOKEN));
    }

    @Test
    void deletionRequiresExplicitMarkerBeforeLifecycleInvocation()
            throws Exception {
        RecordingLifecycle lifecycle = new RecordingLifecycle();
        start(lifecycle);

        Response missing = request("/delete-user", TOKEN,
                new JSONObject().put("login", "rick").toString());
        assertEquals(400, missing.status);
        assertEquals(0, lifecycle.deleteCalls.get());

        Response confirmed = request("/delete-user", TOKEN,
                new JSONObject()
                        .put("login", "rick")
                        .put("confirm", "DELETE")
                        .toString());
        assertEquals(200, confirmed.status);
        assertEquals(1, lifecycle.deleteCalls.get());
        assertTrue(confirmed.body.contains("\"state\":\"COMPLETE\""));
    }

    @Test
    void unknownAdminPathDoesNotDispatchLifecycle() throws Exception {
        RecordingLifecycle lifecycle = new RecordingLifecycle();
        start(lifecycle);

        Response response = request("/query", TOKEN, "{}");

        assertEquals(404, response.status);
        assertEquals(0, lifecycle.createCalls.get());
        assertEquals(0, lifecycle.deleteCalls.get());
    }

    private void start(RecordingLifecycle lifecycle) throws Exception {
        server = new AdminServer(
                InetAddress.getLoopbackAddress(),
                0,
                4096,
                TOKEN,
                lifecycle);
        server.start();
        assertTrue(server.getBoundPort() > 0);
    }

    private Response request(String path, String token, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + server.getBoundPort() + path).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            byte[] request = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(request.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, read(input));
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static ActiveAccount activeAccount() throws Exception {
        Constructor<ActiveAccount> constructor = ActiveAccount.class
                .getDeclaredConstructor(long.class, String.class, java.nio.file.Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(7L, "rick", Paths.get("/tmp/KANGER/7"));
    }

    private static AccountDeletion deletion() throws Exception {
        Constructor<AccountDeletion> constructor = AccountDeletion.class
                .getDeclaredConstructor(
                        String.class,
                        long.class,
                        String.class,
                        String.class,
                        java.nio.file.Path.class,
                        java.nio.file.Path.class,
                        AccountDeletionState.class,
                        long.class,
                        long.class,
                        String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                "deletion-7",
                7L,
                "rick",
                "rick@example.org",
                Paths.get("/tmp/KANGER/7"),
                Paths.get("/tmp/KANGER/.quarantine/7-deletion-7"),
                AccountDeletionState.COMPLETE,
                1L,
                2L,
                "complete");
    }

    private static final class RecordingLifecycle implements AdminServer.Lifecycle {
        private final AtomicInteger createCalls = new AtomicInteger();
        private final AtomicInteger deleteCalls = new AtomicInteger();
        private ActiveAccountRequest lastCreate;

        @Override
        public ActiveAccount create(ActiveAccountRequest request) throws Exception {
            lastCreate = request;
            createCalls.incrementAndGet();
            return activeAccount();
        }

        @Override
        public AccountDeletion deleteByLogin(String login) throws Exception {
            deleteCalls.incrementAndGet();
            return deletion();
        }

        @Override
        public AccountDeletion deleteByUserId(long userId) throws Exception {
            deleteCalls.incrementAndGet();
            return deletion();
        }
    }

    private static final class Response {
        private final int status;
        private final String body;

        private Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
