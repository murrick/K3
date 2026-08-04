/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.admin;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;
import org.kanger.Settings;
import org.kanger.UserFactory;
import org.kanger.Watchdog;
import org.kanger.account.AccountDeletion;
import org.kanger.account.AccountDeletionIncompleteException;
import org.kanger.account.AccountLifecycleService;
import org.kanger.account.ActiveAccount;
import org.kanger.account.ActiveAccountRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loopback-only authenticated transport for KANGER host-operator operations.
 *
 * <p>This listener is deliberately separate from the public application API.
 * It owns no account files and delegates every mutation to the in-process
 * {@link AccountLifecycleService}.</p>
 */
public final class AdminServer {

    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    public static final int DEFAULT_PORT = 1965;
    public static final int DEFAULT_MAX_BODY_BYTES = 65536;

    /** Lifecycle surface exposed to the transport and replaceable in tests. */
    interface Lifecycle {
        ActiveAccount create(ActiveAccountRequest request) throws Exception;

        AccountDeletion deleteByLogin(String login) throws Exception;

        AccountDeletion deleteByUserId(long userId) throws Exception;
    }

    private final Object monitor = new Object();
    private final InetAddress address;
    private final int port;
    private final int maxBodyBytes;
    private final String token;
    private final Lifecycle lifecycle;

    private volatile com.sun.net.httpserver.HttpServer server;
    private volatile ExecutorService executor;

    public static AdminServer fromSettings() throws Exception {
        String bindAddress = Settings.getProperty(
                "server.admin.bind.address", DEFAULT_BIND_ADDRESS);
        int port = integer("server.admin.port", DEFAULT_PORT, 0, 65535);
        int maxBodyBytes = integer(
                "server.admin.request.max.body.bytes",
                DEFAULT_MAX_BODY_BYTES,
                1,
                Integer.MAX_VALUE);
        Path tokenFile = configuredTokenFile();
        String token = new AdminTokenStore(tokenFile).loadOrCreate();
        final AccountLifecycleService accounts = AccountLifecycleService.runtime();
        return new AdminServer(
                InetAddress.getByName(bindAddress),
                port,
                maxBodyBytes,
                token,
                new Lifecycle() {
                    @Override
                    public ActiveAccount create(ActiveAccountRequest request)
                            throws Exception {
                        return accounts.createActiveAccount(request);
                    }

                    @Override
                    public AccountDeletion deleteByLogin(String login)
                            throws Exception {
                        return accounts.deleteActiveAccountByLogin(login);
                    }

                    @Override
                    public AccountDeletion deleteByUserId(long userId)
                            throws Exception {
                        return accounts.deleteActiveAccountByUserId(userId);
                    }
                });
    }

    AdminServer(InetAddress address,
                int port,
                int maxBodyBytes,
                String token,
                Lifecycle lifecycle) {
        if (address == null || !address.isLoopbackAddress()) {
            throw new IllegalArgumentException(
                    "server.admin.bind.address must resolve to loopback");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("admin port is outside valid range");
        }
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("admin body limit must be positive");
        }
        if (token == null || token.length() < 32) {
            throw new IllegalArgumentException("admin bearer token is invalid");
        }
        if (lifecycle == null) {
            throw new IllegalArgumentException("admin lifecycle must not be null");
        }
        this.address = address;
        this.port = port;
        this.maxBodyBytes = maxBodyBytes;
        this.token = token;
        this.lifecycle = lifecycle;
    }

    /** Starts the local listener without blocking the public server bootstrap. */
    public void start() throws IOException {
        synchronized (monitor) {
            if (server != null) {
                throw new IllegalStateException("KANGER admin server is already running");
            }
            final ExecutorService workers = Executors.newFixedThreadPool(
                    2, new AdminThreadFactory());
            final com.sun.net.httpserver.HttpServer created =
                    com.sun.net.httpserver.HttpServer.create(
                            new InetSocketAddress(address, port), 16);
            created.setExecutor(workers);
            created.createContext("/", new Handler());
            created.start();
            server = created;
            executor = workers;
            Watchdog.log("Admin server listening on "
                    + created.getAddress().getAddress().getHostAddress()
                    + ":" + created.getAddress().getPort());
        }
    }

    public void stop() {
        final com.sun.net.httpserver.HttpServer current;
        final ExecutorService workers;
        synchronized (monitor) {
            current = server;
            workers = executor;
            server = null;
            executor = null;
        }
        if (current != null) {
            current.stop(0);
        }
        if (workers != null) {
            workers.shutdown();
            try {
                if (!workers.awaitTermination(5L, TimeUnit.SECONDS)) {
                    workers.shutdownNow();
                }
            } catch (InterruptedException ex) {
                workers.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    int getBoundPort() {
        com.sun.net.httpserver.HttpServer current = server;
        return current == null ? -1 : current.getAddress().getPort();
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(Settings.getProperty(
                "server.admin.enabled", "true"));
    }

    public static Path configuredTokenFile() {
        String configured = Settings.getProperty("server.admin.token.file", "").trim();
        if (configured.isEmpty()) {
            return Paths.get(UserFactory.getDir(UserFactory.rootDir), "admin.token")
                    .toAbsolutePath().normalize();
        }
        Path path = Paths.get(configured);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.home")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private final class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!authorized(exchange.getRequestHeaders())) {
                    send(exchange, 401, error("ADMIN_AUTHENTICATION_FAILED",
                            "Admin authentication failed"));
                    return;
                }
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    send(exchange, 405, error("METHOD_NOT_ALLOWED",
                            "Admin mutations require POST"));
                    return;
                }

                JSONObject request = readJson(exchange.getRequestBody(), maxBodyBytes);
                String path = exchange.getRequestURI().getPath();
                if ("/create-user".equals(path)) {
                    send(exchange, 200, createUser(request));
                } else if ("/delete-user".equals(path)) {
                    send(exchange, 200, deleteUser(request));
                } else {
                    send(exchange, 404, error("ADMIN_OPERATION_NOT_FOUND",
                            "Unknown admin operation"));
                }
            } catch (PayloadTooLargeException ex) {
                send(exchange, 413, error("PAYLOAD_TOO_LARGE",
                        "Admin request body exceeds configured limit"));
            } catch (AccountDeletionIncompleteException ex) {
                AccountDeletion deletion = ex.getDeletion();
                JSONObject response = error("ACCOUNT_DELETION_INCOMPLETE",
                        "Account deletion requires recovery");
                if (deletion != null) {
                    response.put("deletion_id", deletion.getId());
                    response.put("state", deletion.getState().name());
                }
                send(exchange, 409, response);
            } catch (IllegalArgumentException ex) {
                send(exchange, 400, error("INVALID_ADMIN_REQUEST", safeMessage(ex)));
            } catch (Exception ex) {
                Watchdog.err("Admin operation failed: "
                        + ex.getClass().getSimpleName() + ": " + safeMessage(ex));
                send(exchange, 409, error("ADMIN_OPERATION_FAILED", safeMessage(ex)));
            } finally {
                exchange.close();
            }
        }
    }

    private JSONObject createUser(JSONObject request) throws Exception {
        String login = required(request, "login");
        String password = required(request, "password");
        ActiveAccount account = lifecycle.create(new ActiveAccountRequest(
                login,
                password,
                optional(request, "email"),
                optional(request, "name"),
                optional(request, "country"),
                optional(request, "city"),
                request.has("privacy_consent")
                        ? Boolean.valueOf(request.optBoolean("privacy_consent"))
                        : null));
        return new JSONObject()
                .put("result", "OK")
                .put("state", "ACTIVE")
                .put("user_id", account.getUserId())
                .put("login", account.getLogin());
    }

    private JSONObject deleteUser(JSONObject request) throws Exception {
        if (!"DELETE".equals(request.optString("confirm", ""))) {
            throw new IllegalArgumentException(
                    "delete-user requires confirm=DELETE");
        }
        boolean hasLogin = request.has("login")
                && !request.optString("login", "").trim().isEmpty();
        boolean hasUserId = request.has("user_id") && !request.isNull("user_id");
        if (hasLogin == hasUserId) {
            throw new IllegalArgumentException(
                    "delete-user requires exactly one login or user_id selector");
        }

        AccountDeletion deletion = hasLogin
                ? lifecycle.deleteByLogin(request.getString("login").trim())
                : lifecycle.deleteByUserId(request.getLong("user_id"));
        return new JSONObject()
                .put("result", "OK")
                .put("deletion_id", deletion.getId())
                .put("user_id", deletion.getUserId())
                .put("login", deletion.getLogin())
                .put("state", deletion.getState().name())
                .put("quarantine_home", deletion.getQuarantineHome().toString());
    }

    private boolean authorized(Headers headers) {
        String authorization = headers.getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = authorization.substring("Bearer ".length())
                .trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }

    private static JSONObject readJson(InputStream input, int maxBytes)
            throws IOException, PayloadTooLargeException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(maxBytes, 4096));
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new PayloadTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        if (total == 0) {
            return new JSONObject();
        }
        return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, JSONObject response)
            throws IOException {
        byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static JSONObject error(String code, String description) {
        return new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description == null ? "" : description);
    }

    private static String required(JSONObject object, String name) {
        String value = optional(object, name).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    private static String optional(JSONObject object, String name) {
        return object.has(name) && !object.isNull(name)
                ? object.optString(name, "") : "";
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message;
    }

    private static int integer(String key,
                               int fallback,
                               int minimum,
                               int maximum) {
        int value;
        try {
            value = Integer.parseInt(Settings.getProperty(
                    key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer", ex);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " is outside valid range");
        }
        return value;
    }

    private static final class PayloadTooLargeException extends Exception {
    }

    private static final class AdminThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task,
                    "kanger-admin-http-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
