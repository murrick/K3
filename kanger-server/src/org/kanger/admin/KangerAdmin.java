/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.admin;

import org.json.JSONObject;
import org.kanger.Settings;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local host-operator CLI for KANGER Server account lifecycle operations.
 *
 * <p>The CLI is an authenticated client of the in-process admin listener. It
 * never mutates credential, pending, journal or account-home files directly.</p>
 */
public final class KangerAdmin {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_INPUT = 2;
    public static final int EXIT_CONNECTION = 3;
    public static final int EXIT_ACCOUNT = 4;
    public static final int EXIT_INCOMPLETE = 5;

    interface Client {
        JSONObject post(String path, JSONObject request) throws Exception;
    }

    interface Terminal {
        boolean isInteractive();

        String readLine(String prompt) throws IOException;

        char[] readPassword(String prompt) throws IOException;

        char[] readPasswordFromStdin() throws IOException;

        void out(String value);

        void err(String value);
    }

    private KangerAdmin() {
    }

    public static void main(String[] args) {
        int status;
        try {
            status = run(args, new SystemTerminal(), new HttpAdminClient());
        } catch (Exception failure) {
            System.err.println("kanger-admin: " + safeMessage(failure));
            status = EXIT_CONNECTION;
        }
        if (status != EXIT_SUCCESS) {
            System.exit(status);
        }
    }

    static int run(String[] args, Terminal terminal, Client client) {
        if (terminal == null || client == null) {
            throw new IllegalArgumentException("terminal and client must not be null");
        }
        try {
            if (args == null || args.length == 0) {
                terminal.err(usage());
                return EXIT_INPUT;
            }
            if ("--help".equals(args[0]) || "-h".equals(args[0])) {
                terminal.out(usage());
                return EXIT_SUCCESS;
            }

            String command = args[0];
            Options options = Options.parse(Arrays.copyOfRange(args, 1, args.length));
            if ("create-user".equals(command)) {
                return createUser(options, terminal, client);
            }
            if ("delete-user".equals(command)) {
                return deleteUser(options, terminal, client);
            }
            terminal.err("kanger-admin: unknown command: " + command);
            return EXIT_INPUT;
        } catch (InputFailure failure) {
            terminal.err("kanger-admin: " + failure.getMessage());
            return EXIT_INPUT;
        } catch (ConnectionFailure failure) {
            terminal.err("kanger-admin: admin service is unavailable");
            return EXIT_CONNECTION;
        } catch (Exception failure) {
            terminal.err("kanger-admin: " + safeMessage(failure));
            return EXIT_CONNECTION;
        }
    }

    private static int createUser(Options options,
                                  Terminal terminal,
                                  Client client) throws Exception {
        options.rejectUnknown(
                "login", "email", "name", "country", "city",
                "privacy-consent", "password-stdin");
        if (options.has("password")) {
            throw new InputFailure(
                    "--password is forbidden; use hidden prompt or --password-stdin");
        }

        String login = valueOrPrompt(options, terminal, "login", "Login: ", true);
        String email = valueOrPrompt(options, terminal, "email", "E-mail (optional): ", false);
        String name = valueOrPrompt(options, terminal, "name", "Name (optional): ", false);
        String country = valueOrPrompt(
                options, terminal, "country", "Country (optional): ", false);
        String city = valueOrPrompt(options, terminal, "city", "City (optional): ", false);
        Boolean privacy = null;
        if (options.has("privacy-consent")) {
            privacy = Boolean.valueOf(parseBoolean(
                    options.value("privacy-consent"), "privacy-consent"));
        } else if (terminal.isInteractive()) {
            String value = terminal.readLine("Privacy consent [y/N]: ");
            privacy = Boolean.valueOf("y".equalsIgnoreCase(value)
                    || "yes".equalsIgnoreCase(value));
        }

        char[] password = options.flag("password-stdin")
                ? terminal.readPasswordFromStdin()
                : hiddenPassword(terminal);
        try {
            if (password == null || password.length == 0) {
                throw new InputFailure("password must not be empty");
            }
            JSONObject request = new JSONObject()
                    .put("login", login)
                    .put("password", new String(password))
                    .put("email", email)
                    .put("name", name)
                    .put("country", country)
                    .put("city", city);
            if (privacy != null) {
                request.put("privacy_consent", privacy.booleanValue());
            }
            JSONObject response = client.post("/create-user", request);
            return emit(response, terminal,
                    "Created ACTIVE account " + response.optString("login", login)
                            + " (userId=" + response.optLong("user_id", -1L) + ")");
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static int deleteUser(Options options,
                                  Terminal terminal,
                                  Client client) throws Exception {
        options.rejectUnknown("login", "user-id", "yes");
        boolean hasLogin = options.has("login");
        boolean hasUserId = options.has("user-id");
        if (!hasLogin && !hasUserId && terminal.isInteractive()) {
            String login = terminal.readLine("Login to delete: ").trim();
            if (!login.isEmpty()) {
                options.put("login", login);
                hasLogin = true;
            }
        }
        if (hasLogin == hasUserId) {
            throw new InputFailure(
                    "delete-user requires exactly one --login or --user-id");
        }

        if (!options.flag("yes")) {
            if (!terminal.isInteractive()) {
                throw new InputFailure(
                        "delete-user requires --yes in non-interactive mode");
            }
            String confirmation = terminal.readLine(
                    "Type DELETE to quarantine this account: ");
            if (!"DELETE".equals(confirmation)) {
                throw new InputFailure("deletion cancelled");
            }
        }

        JSONObject request = new JSONObject().put("confirm", "DELETE");
        if (hasLogin) {
            request.put("login", required(options.value("login"), "login"));
        } else {
            long userId;
            try {
                userId = Long.parseLong(options.value("user-id"));
            } catch (NumberFormatException ex) {
                throw new InputFailure("user-id must be a positive integer");
            }
            if (userId <= 0L) {
                throw new InputFailure("user-id must be a positive integer");
            }
            request.put("user_id", userId);
        }

        JSONObject response = client.post("/delete-user", request);
        return emit(response, terminal,
                "Deletion " + response.optString("deletion_id", "")
                        + " reached " + response.optString("state", "unknown"));
    }

    private static int emit(JSONObject response,
                            Terminal terminal,
                            String successMessage) {
        if (response != null
                && "OK".equalsIgnoreCase(response.optString("result", ""))) {
            terminal.out(successMessage);
            return EXIT_SUCCESS;
        }
        String code = response == null ? "" : response.optString("code", "");
        String description = response == null
                ? "admin service returned no response"
                : response.optString("description", "admin operation failed");
        terminal.err("kanger-admin: " + code + ": " + description);
        if ("ACCOUNT_DELETION_INCOMPLETE".equals(code)) {
            return EXIT_INCOMPLETE;
        }
        if ("ADMIN_AUTHENTICATION_FAILED".equals(code)) {
            return EXIT_CONNECTION;
        }
        return EXIT_ACCOUNT;
    }

    private static char[] hiddenPassword(Terminal terminal) throws Exception {
        if (!terminal.isInteractive()) {
            throw new InputFailure(
                    "password requires an interactive terminal or --password-stdin");
        }
        return terminal.readPassword("Password: ");
    }

    private static String valueOrPrompt(Options options,
                                        Terminal terminal,
                                        String key,
                                        String prompt,
                                        boolean required) throws Exception {
        if (options.has(key)) {
            return required ? required(options.value(key), key) : options.value(key);
        }
        if (terminal.isInteractive()) {
            String value = terminal.readLine(prompt);
            return required ? required(value, key) : value;
        }
        if (required) {
            throw new InputFailure("missing required --" + key);
        }
        return "";
    }

    private static String required(String value, String name) throws InputFailure {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new InputFailure(name + " must not be empty");
        }
        return normalized;
    }

    private static boolean parseBoolean(String value, String name) throws InputFailure {
        if ("true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "y".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value)
                || "n".equalsIgnoreCase(value)) {
            return false;
        }
        throw new InputFailure(name + " must be true or false");
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message;
    }

    private static String usage() {
        return "Usage:\n"
                + "  kanger-admin create-user [--login VALUE] [--email VALUE] "
                + "[--name VALUE] [--country VALUE] [--city VALUE] "
                + "[--privacy-consent true|false] [--password-stdin]\n"
                + "  kanger-admin delete-user (--login VALUE | --user-id VALUE) [--yes]\n"
                + "\nPasswords are accepted only through a hidden prompt or --password-stdin.";
    }

    private static final class HttpAdminClient implements Client {
        private final String token;
        private final String baseUrl;

        private HttpAdminClient() throws Exception {
            InetAddress address = InetAddress.getByName(Settings.getProperty(
                    "server.admin.bind.address", AdminServer.DEFAULT_BIND_ADDRESS));
            if (!address.isLoopbackAddress()) {
                throw new IOException("configured admin address is not loopback");
            }
            int port = Integer.parseInt(Settings.getProperty(
                    "server.admin.port", Integer.toString(AdminServer.DEFAULT_PORT)));
            token = new AdminTokenStore(AdminServer.configuredTokenFile()).load();
            baseUrl = "http://" + address.getHostAddress() + ":" + port;
        }

        @Override
        public JSONObject post(String path, JSONObject request) throws Exception {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 400
                        ? connection.getErrorStream() : connection.getInputStream();
                if (stream == null) {
                    throw new ConnectionFailure();
                }
                String text = readBounded(stream, 1024 * 1024);
                return new JSONObject(text);
            } catch (IOException failure) {
                throw new ConnectionFailure();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private static String readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximum) {
                throw new IOException("admin response exceeds limit");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class SystemTerminal implements Terminal {
        private final Console console = System.console();
        private final BufferedReader stdin = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        private final PrintStream out = System.out;
        private final PrintStream err = System.err;

        @Override
        public boolean isInteractive() {
            return console != null;
        }

        @Override
        public String readLine(String prompt) throws IOException {
            if (console == null) {
                throw new IOException("interactive terminal is unavailable");
            }
            String value = console.readLine("%s", prompt);
            return value == null ? "" : value;
        }

        @Override
        public char[] readPassword(String prompt) throws IOException {
            if (console == null) {
                throw new IOException("interactive terminal is unavailable");
            }
            char[] value = console.readPassword("%s", prompt);
            return value == null ? new char[0] : value;
        }

        @Override
        public char[] readPasswordFromStdin() throws IOException {
            String value = stdin.readLine();
            return value == null ? new char[0] : value.toCharArray();
        }

        @Override
        public void out(String value) {
            out.println(value);
        }

        @Override
        public void err(String value) {
            err.println(value);
        }
    }

    private static final class Options {
        private final Map<String, String> values = new LinkedHashMap<String, String>();

        static Options parse(String[] args) throws InputFailure {
            Options result = new Options();
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if (!argument.startsWith("--") || argument.length() == 2) {
                    throw new InputFailure("invalid option: " + argument);
                }
                String key = argument.substring(2);
                if ("yes".equals(key) || "password-stdin".equals(key)) {
                    result.put(key, "true");
                    continue;
                }
                if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                    throw new InputFailure("missing value for --" + key);
                }
                result.put(key, args[++index]);
            }
            return result;
        }

        void rejectUnknown(String... allowed) throws InputFailure {
            for (String key : values.keySet()) {
                boolean accepted = false;
                for (String candidate : allowed) {
                    if (candidate.equals(key)) {
                        accepted = true;
                        break;
                    }
                }
                if (!accepted) {
                    throw new InputFailure("unknown option --" + key);
                }
            }
        }

        boolean has(String key) {
            return values.containsKey(key);
        }

        boolean flag(String key) {
            return "true".equalsIgnoreCase(values.get(key));
        }

        String value(String key) {
            return values.get(key);
        }

        void put(String key, String value) throws InputFailure {
            if (values.containsKey(key)) {
                throw new InputFailure("duplicate option --" + key);
            }
            values.put(key, value == null ? "" : value);
        }
    }

    private static final class InputFailure extends Exception {
        private InputFailure(String message) {
            super(message);
        }
    }

    private static final class ConnectionFailure extends Exception {
    }
}
