package org.kanger.admin;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KangerAdminTest {

    @Test
    void flagModeReadsPasswordOnlyFromExplicitStdinAndPrintsNoSecret() {
        RecordingTerminal terminal = new RecordingTerminal(false);
        terminal.stdinPassword = "top-secret-password".toCharArray();
        RecordingClient client = RecordingClient.successCreate();

        int status = KangerAdmin.run(new String[]{
                        "create-user",
                        "--login", "rick",
                        "--email", "rick@example.org",
                        "--password-stdin"
                }, terminal, client);

        assertEquals(KangerAdmin.EXIT_SUCCESS, status);
        assertEquals(1, client.calls);
        assertEquals("/create-user", client.path);
        assertEquals("rick", client.request.getString("login"));
        assertEquals("top-secret-password", client.request.getString("password"));
        assertFalse(terminal.output().contains("top-secret-password"));
        assertFalse(terminal.output().contains("ADMIN-TOKEN"));
    }

    @Test
    void mixedInteractiveModePromptsOnlyMissingFieldsAndUsesHiddenPassword() {
        RecordingTerminal terminal = new RecordingTerminal(true);
        terminal.lines.add("rick@example.org");
        terminal.lines.add("Rick");
        terminal.lines.add("Austria");
        terminal.lines.add("Vienna");
        terminal.lines.add("yes");
        terminal.hiddenPassword = "hidden-password".toCharArray();
        RecordingClient client = RecordingClient.successCreate();

        int status = KangerAdmin.run(new String[]{
                "create-user", "--login", "rick"
        }, terminal, client);

        assertEquals(KangerAdmin.EXIT_SUCCESS, status);
        assertEquals("rick@example.org", client.request.getString("email"));
        assertEquals("Rick", client.request.getString("name"));
        assertTrue(client.request.getBoolean("privacy_consent"));
        assertEquals(1, terminal.passwordPrompts);
        assertFalse(terminal.output().contains("hidden-password"));
    }

    @Test
    void nonInteractiveDeletionRequiresExplicitYes() {
        RecordingTerminal terminal = new RecordingTerminal(false);
        RecordingClient client = RecordingClient.successDelete();

        int status = KangerAdmin.run(new String[]{
                "delete-user", "--login", "rick"
        }, terminal, client);

        assertEquals(KangerAdmin.EXIT_INPUT, status);
        assertEquals(0, client.calls);
    }

    @Test
    void confirmedDeletionSendsExactMarkerAndOneSelector() {
        RecordingTerminal terminal = new RecordingTerminal(false);
        RecordingClient client = RecordingClient.successDelete();

        int status = KangerAdmin.run(new String[]{
                "delete-user", "--user-id", "7", "--yes"
        }, terminal, client);

        assertEquals(KangerAdmin.EXIT_SUCCESS, status);
        assertEquals(1, client.calls);
        assertEquals("/delete-user", client.path);
        assertEquals("DELETE", client.request.getString("confirm"));
        assertEquals(7L, client.request.getLong("user_id"));
        assertFalse(client.request.has("login"));
    }

    @Test
    void incompleteDeletionHasDedicatedExitClass() {
        RecordingTerminal terminal = new RecordingTerminal(false);
        RecordingClient client = new RecordingClient(new JSONObject()
                .put("result", "error")
                .put("code", "ACCOUNT_DELETION_INCOMPLETE")
                .put("description", "recovery required"));

        int status = KangerAdmin.run(new String[]{
                "delete-user", "--login", "rick", "--yes"
        }, terminal, client);

        assertEquals(KangerAdmin.EXIT_INCOMPLETE, status);
        assertTrue(terminal.errors.toString().contains("recovery required"));
    }

    @Test
    void plaintextPasswordArgumentIsRejectedBeforeNetworkCall() {
        RecordingTerminal terminal = new RecordingTerminal(false);
        RecordingClient client = RecordingClient.successCreate();

        int status = KangerAdmin.run(new String[]{
                "create-user", "--login", "rick",
                "--password", "must-not-appear"
        }, terminal, client);

        assertEquals(KangerAdmin.EXIT_INPUT, status);
        assertEquals(0, client.calls);
        assertFalse(terminal.output().contains("must-not-appear"));
    }

    private static final class RecordingClient implements KangerAdmin.Client {
        private final JSONObject response;
        private int calls;
        private String path;
        private JSONObject request;

        private RecordingClient(JSONObject response) {
            this.response = response;
        }

        static RecordingClient successCreate() {
            return new RecordingClient(new JSONObject()
                    .put("result", "OK")
                    .put("state", "ACTIVE")
                    .put("login", "rick")
                    .put("user_id", 7L));
        }

        static RecordingClient successDelete() {
            return new RecordingClient(new JSONObject()
                    .put("result", "OK")
                    .put("state", "COMPLETE")
                    .put("deletion_id", "deletion-7"));
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
        private final boolean interactive;
        private final Queue<String> lines = new ArrayDeque<String>();
        private final StringBuilder standard = new StringBuilder();
        private final StringBuilder errors = new StringBuilder();
        private char[] hiddenPassword = new char[0];
        private char[] stdinPassword = new char[0];
        private int passwordPrompts;

        private RecordingTerminal(boolean interactive) {
            this.interactive = interactive;
        }

        @Override
        public boolean isInteractive() {
            return interactive;
        }

        @Override
        public String readLine(String prompt) {
            String value = lines.poll();
            return value == null ? "" : value;
        }

        @Override
        public char[] readPassword(String prompt) {
            passwordPrompts++;
            return hiddenPassword.clone();
        }

        @Override
        public char[] readPasswordFromStdin() {
            return stdinPassword.clone();
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
