package org.kanger.admin;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kanger.ServerOperations;
import org.kanger.account.AccountDeletion;
import org.kanger.account.ActiveAccount;
import org.kanger.account.ActiveAccountRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOperationsTest {

    private static final String TOKEN =
            "0123456789abcdefghijklmnopqrstuvwxyz-ADMIN-TOKEN";

    private AdminServer server;

    @AfterEach
    void tearDown() {
        ServerOperations.clearMaintenance();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void authenticatedStatusReturnsOperatorOnlySnapshot() throws Exception {
        start();

        Response response = request("/status", TOKEN, "{}");
        JSONObject body = new JSONObject(response.body);

        assertEquals(200, response.status);
        assertEquals("OK", body.getString("result"));
        assertEquals("UP", body.getString("status"));
        assertTrue(body.has("active_sessions"));
        assertTrue(body.has("uptime_millis"));
        assertTrue(body.has("maintenance"));
    }

    @Test
    void maintenanceCanBeScheduledAndClearedThroughAdminPlane()
            throws Exception {
        start();
        long deadline = System.currentTimeMillis() + 60_000L;

        Response scheduled = request("/maintenance", TOKEN,
                new JSONObject()
                        .put("deadline_epoch_millis", deadline)
                        .toString());
        JSONObject publicSnapshot = ServerOperations.publicMaintenance()
                .getJSONObject("maintenance");

        assertEquals(200, scheduled.status);
        assertTrue(publicSnapshot.getBoolean("active"));
        assertEquals(deadline,
                publicSnapshot.getLong("deadline_epoch_millis"));

        Response cleared = request("/maintenance", TOKEN,
                new JSONObject().put("clear", true).toString());
        assertEquals(200, cleared.status);
        assertFalse(ServerOperations.publicMaintenance()
                .getJSONObject("maintenance").getBoolean("active"));
    }

    @Test
    void statusStillRequiresAdminBearer() throws Exception {
        start();

        Response response = request("/status", null, "{}");

        assertEquals(401, response.status);
    }

    private void start() throws Exception {
        server = new AdminServer(
                InetAddress.getLoopbackAddress(),
                0,
                4096,
                TOKEN,
                new NoopLifecycle());
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

    private static final class NoopLifecycle implements AdminServer.Lifecycle {
        @Override
        public ActiveAccount create(ActiveAccountRequest request) {
            throw new AssertionError("account lifecycle must not be invoked");
        }

        @Override
        public AccountDeletion deleteByLogin(String login) {
            throw new AssertionError("account lifecycle must not be invoked");
        }

        @Override
        public AccountDeletion deleteByUserId(long userId) {
            throw new AssertionError("account lifecycle must not be invoked");
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
