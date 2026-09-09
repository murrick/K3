package org.kanger.admin;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KangerAdminOperationsTest {

    @Test
    void statusPrintsCompactServerSnapshot() {
        RecordingTerminal terminal = new RecordingTerminal();
        RecordingClient client = new RecordingClient(new JSONObject()
                .put("result", "OK")
                .put("status", "UP")
                .put("core_version", "3.7.0")
                .put("server_version", "server-0.18")
                .put("uptime_millis", 3_661_000L)
                .put("active_sessions", 103)
                .put("maintenance", new JSONObject().put("active", false)));

        int status = KangerAdmin.run(
                new String[]{"status"}, terminal, client);

        assertEquals(KangerAdmin.EXIT_SUCCESS, status);
        assertEquals(1, client.calls);
        assertEquals("/status", client.path);
        assertTrue(terminal.output().contains("active sessions: 103"));
        assertTrue(terminal.output().contains("maintenance: none"));
    }

    @Test
    void maintenanceMinutesPostsFutureDeadlineWithoutUserDirective() {
        RecordingTerminal terminal = new RecordingTerminal();
        RecordingClient client = new RecordingClient(new JSONObject()
                .put("result", "OK")
                .put("maintenance", new JSONObject().put("active", true)));
        long before = System.currentTimeMillis();

        int status = KangerAdmin.run(
                new String[]{"maintenance", "--minutes", "10"},
                terminal,
                client);

        assertEquals(KangerAdmin.EXIT_SUCCESS, status);
        assertEquals("/maintenance", client.path);
        long deadline = client.request.getLong("deadline_epoch_millis");
        assertTrue(deadline >= before + 599_000L);
        assertFalse(client.request.has("message"));
        assertFalse(terminal.output().toLowerCase().contains("save"));
        assertFalse(terminal.output().toLowerCase().contains("commit"));
        assertFalse(terminal.output().toLowerCase().contains("disconnect"));
    }

    @Test
    void maintenanceClearPostsExplicitClearMarker() {
        RecordingTerminal terminal = new RecordingTerminal();
        RecordingClient client = new RecordingClient(new JSONObject()
                .put("result", "OK")
                .put("maintenance", new JSONObject().put("active", false)));

        int status = KangerAdmin.run(
                new String[]{"maintenance", "--clear"}, terminal, client);

        assertEquals(KangerAdmin.EXIT_SUCCESS, status);
        assertTrue(client.request.getBoolean("clear"));
        assertFalse(client.request.has("deadline_epoch_millis"));
    }

    @Test
    void maintenanceRequiresExactlyOneModeBeforeNetworkCall() {
        RecordingTerminal terminal = new RecordingTerminal();
        RecordingClient client = new RecordingClient(new JSONObject()
                .put("result", "OK"));

        int status = KangerAdmin.run(
                new String[]{"maintenance"}, terminal, client);

        assertEquals(KangerAdmin.EXIT_INPUT, status);
        assertEquals(0, client.calls);
    }

    private static final class RecordingClient implements KangerAdmin.Client {
        private final JSONObject response;
        private int calls;
        private String path;
        private JSONObject request;

        private RecordingClient(JSONObject response) {
            this.response = response;
        }

        @Override
        public JSONObject post(String path, JSONObject request) {
            calls++;
            this.path = path;
            this.request = new JSONObject(request.toString());
            return new JSONObject(response.toString());
        }
    }

    private static final class RecordingTerminal implements KangerAdmin.Terminal {
        private final Queue<String> lines = new ArrayDeque<String>();
        private final StringBuilder standard = new StringBuilder();
        private final StringBuilder errors = new StringBuilder();

        @Override
        public boolean isInteractive() {
            return false;
        }

        @Override
        public String readLine(String prompt) {
            String value = lines.poll();
            return value == null ? "" : value;
        }

        @Override
        public char[] readPassword(String prompt) {
            return new char[0];
        }

        @Override
        public char[] readPasswordFromStdin() {
            return new char[0];
        }

        @Override
        public void out(String value) {
            standard.append(value).append('\n');
        }

        @Override
        public void err(String value) {
            errors.append(value).append('\n');
        }

        private String output() {
            return standard.toString() + errors.toString();
        }
    }
}
