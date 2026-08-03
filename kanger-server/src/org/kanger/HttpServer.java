/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package org.kanger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;
import org.kanger.interfaces.IReactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP transport adapter for the KANGER server API.
 *
 * <p>The production deployment model is an internal loopback service behind
 * nginx. TLS termination, public request buffering and public connection
 * limits belong to nginx; this class still enforces an application body limit,
 * a bounded worker pool and an explicit CORS allow-list.</p>
 */
public final class HttpServer {

    static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    static final int DEFAULT_PORT = 1964;
    static final int DEFAULT_BACKLOG = 128;
    static final int DEFAULT_MAX_THREADS = 32;
    static final int DEFAULT_QUEUE_CAPACITY = 128;
    static final int DEFAULT_MAX_BODY_BYTES = 1024 * 1024;

    private final Object lifecycleMonitor = new Object();

    private volatile boolean active;
    private volatile com.sun.net.httpserver.HttpServer server;
    private volatile ExecutorService executor;

    /**
     * Starts the HTTP service and blocks until {@link #stop()} is called.
     *
     * @param reactor application request processor
     * @throws Exception if configuration is invalid or the listen socket cannot
     *                   be created
     */
    public void start(final IReactor<JSONObject> reactor) throws Exception {
        if (reactor == null) {
            throw new IllegalArgumentException("reactor must not be null");
        }

        final String bindAddress = Settings.getProperty(
                "server.bind.address", DEFAULT_BIND_ADDRESS);
        final int port = positiveOrZeroInt("server.port", DEFAULT_PORT);
        final int backlog = positiveInt("server.backlog", DEFAULT_BACKLOG);
        final int maxThreads = positiveInt("server.maxthreads", DEFAULT_MAX_THREADS);
        final int queueCapacity = positiveInt(
                "server.queue.capacity", DEFAULT_QUEUE_CAPACITY);
        final int maxBodyBytes = positiveInt(
                "server.request.max.body.bytes", DEFAULT_MAX_BODY_BYTES);

        final InetAddress address = InetAddress.getByName(bindAddress);
        final InetSocketAddress socketAddress = new InetSocketAddress(address, port);
        final ThreadPoolExecutor workers = new ThreadPoolExecutor(
                Math.min(2, maxThreads),
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(queueCapacity),
                new ServerThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        workers.allowCoreThreadTimeOut(true);

        final com.sun.net.httpserver.HttpServer created =
                com.sun.net.httpserver.HttpServer.create(socketAddress, backlog);
        created.setExecutor(workers);
        created.createContext("/", new ApiHandler(reactor, maxBodyBytes));

        synchronized (lifecycleMonitor) {
            if (active) {
                created.stop(0);
                workers.shutdownNow();
                throw new IllegalStateException("HTTP server is already running");
            }
            server = created;
            executor = workers;
            active = true;
        }

        try {
            created.start();
            Watchdog.log("HTTP server listening on "
                    + created.getAddress().getAddress().getHostAddress()
                    + ":" + created.getAddress().getPort());

            synchronized (lifecycleMonitor) {
                while (active) {
                    lifecycleMonitor.wait();
                }
            }
        } finally {
            stopInternal();
        }
    }

    /**
     * Stops accepting requests and releases transport resources.
     */
    public void stop() {
        synchronized (lifecycleMonitor) {
            if (!active && server == null && executor == null) {
                return;
            }
            active = false;
            lifecycleMonitor.notifyAll();
        }
        stopInternal();
    }

    private void stopInternal() {
        final com.sun.net.httpserver.HttpServer currentServer;
        final ExecutorService currentExecutor;

        synchronized (lifecycleMonitor) {
            currentServer = server;
            currentExecutor = executor;
            server = null;
            executor = null;
            active = false;
            lifecycleMonitor.notifyAll();
        }

        if (currentServer != null) {
            currentServer.stop(1);
        }
        if (currentExecutor != null) {
            currentExecutor.shutdown();
            try {
                if (!currentExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    currentExecutor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                currentExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int positiveInt(String key, int defaultValue) throws Exception {
        int value = Integer.parseInt(Settings.getProperty(key, Integer.toString(defaultValue)));
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }
        return value;
    }

    private static int positiveOrZeroInt(String key, int defaultValue) throws Exception {
        int value = Integer.parseInt(Settings.getProperty(key, Integer.toString(defaultValue)));
        if (value < 0 || value > 65535) {
            throw new IllegalArgumentException(key + " must be between 0 and 65535");
        }
        return value;
    }

    static JSONObject parseQueryParameters(String rawQuery) throws IOException {
        JSONObject parameters = new JSONObject();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return parameters;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String name = decode(parts[0]).trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            String value = parts.length == 2 ? decode(parts[1]).trim() : "";
            parameters.put(name, value);
        }
        return parameters;
    }

    static boolean isOriginAllowed(String origin, List<String> allowedOrigins) {
        if (origin == null || allowedOrigins == null || allowedOrigins.isEmpty()) {
            return false;
        }
        for (String allowed : allowedOrigins) {
            if (origin.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    static byte[] readBody(InputStream input, int maxBodyBytes)
            throws IOException, PayloadTooLargeException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(maxBodyBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBodyBytes) {
                throw new PayloadTooLargeException(maxBodyBytes);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String decode(String value) throws IOException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (IllegalArgumentException ex) {
            throw new IOException("Malformed URL encoding", ex);
        }
    }

    private static List<String> configuredOrigins() {
        List<String> origins = new ArrayList<String>();
        for (String value : Settings.getByPrefix("server.cors.allowed.origin.")) {
            String origin = value == null ? "" : value.trim();
            if (!origin.isEmpty() && !origins.contains(origin)) {
                origins.add(origin);
            }
        }
        return Collections.unmodifiableList(origins);
    }

    private static JSONObject createPacket(HttpExchange exchange, byte[] body)
            throws IOException {
        JSONObject packet = new JSONObject();
        List<String> headerLines = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
            for (String value : entry.getValue()) {
                headerLines.add(entry.getKey() + ": " + value);
            }
        }
        packet.put("headers", headerLines);

        URI uri = exchange.getRequestURI();
        JSONObject query = new JSONObject();
        query.put("context", normalizeContext(uri.getRawPath()));
        query.put("parameters", parseQueryParameters(uri.getRawQuery()));
        packet.put("query", query);

        if (body.length == 0) {
            packet.put("body", new JSONObject());
        } else {
            packet.put("body", new JSONObject(new String(body, StandardCharsets.UTF_8)));
        }
        return packet;
    }

    private static String normalizeContext(String rawPath) throws IOException {
        String context = rawPath == null ? "" : decode(rawPath);
        while (context.startsWith("/")) {
            context = context.substring(1);
        }
        return context;
    }

    private static void sendJson(HttpExchange exchange,
                                 int status,
                                 JSONObject response,
                                 String allowedOrigin) throws IOException {
        byte[] body = response == null
                ? new byte[0]
                : response.toString().getBytes(StandardCharsets.UTF_8);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        if (allowedOrigin != null) {
            headers.set("Access-Control-Allow-Origin", allowedOrigin);
            headers.set("Vary", "Origin");
            if (Boolean.parseBoolean(getSetting("server.cors.allow.credentials", "false"))) {
                headers.set("Access-Control-Allow-Credentials", "true");
            }
        }

        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String getSetting(String key, String defaultValue) {
        try {
            return Settings.getProperty(key, defaultValue);
        } catch (Exception ex) {
            Watchdog.err("Unable to read setting " + key + ": " + ex);
            return defaultValue;
        }
    }

    private static JSONObject error(String description) {
        return new JSONObject()
                .put("result", "error")
                .put("description", description);
    }

    private static final class ApiHandler implements HttpHandler {
        private final IReactor<JSONObject> reactor;
        private final int maxBodyBytes;

        private ApiHandler(IReactor<JSONObject> reactor, int maxBodyBytes) {
            this.reactor = reactor;
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            String allowedOrigin = isOriginAllowed(origin, configuredOrigins()) ? origin : null;

            try {
                String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
                if ("OPTIONS".equals(method)) {
                    handlePreflight(exchange, allowedOrigin);
                    return;
                }
                if (!"GET".equals(method) && !"POST".equals(method)) {
                    exchange.getResponseHeaders().set("Allow", "GET, POST, OPTIONS");
                    sendJson(exchange, 405, error("Method not allowed"), allowedOrigin);
                    return;
                }

                if ("health".equals(normalizeContext(exchange.getRequestURI().getRawPath()))) {
                    sendJson(exchange, 200,
                            new JSONObject()
                                    .put("result", "OK")
                                    .put("status", "UP")
                                    .put("version", Version.VERSION_S),
                            allowedOrigin);
                    return;
                }

                String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
                if (contentLength != null) {
                    long declaredLength = Long.parseLong(contentLength);
                    if (declaredLength < 0L || declaredLength > maxBodyBytes) {
                        sendJson(exchange, 413,
                                error("Request body exceeds configured limit"),
                                allowedOrigin);
                        return;
                    }
                }

                byte[] body = readBody(exchange.getRequestBody(), maxBodyBytes);
                JSONObject packet = createPacket(exchange, body);
                Object applicationResponse = reactor.run(packet);
                if (applicationResponse != null && !(applicationResponse instanceof JSONObject)) {
                    throw new IllegalStateException("Reactor returned unsupported response type");
                }
                JSONObject response = (JSONObject) applicationResponse;
                if (response == null) {
                    sendJson(exchange, 404, error("Unknown API context"), allowedOrigin);
                } else {
                    sendJson(exchange, 200, response, allowedOrigin);
                }
            } catch (PayloadTooLargeException ex) {
                sendJson(exchange, 413, error(ex.getMessage()), allowedOrigin);
            } catch (NumberFormatException ex) {
                sendJson(exchange, 400, error("Invalid Content-Length"), allowedOrigin);
            } catch (Exception ex) {
                Watchdog.err("HTTP request failed: " + ex);
                sendJson(exchange, 400, error("Invalid request"), allowedOrigin);
            } finally {
                exchange.close();
            }
        }

        private void handlePreflight(HttpExchange exchange, String allowedOrigin)
                throws IOException {
            if (allowedOrigin == null) {
                sendJson(exchange, 403, error("Origin is not allowed"), null);
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type");
            headers.set("Access-Control-Max-Age", "600");
            sendJson(exchange, 204, null, allowedOrigin);
        }
    }

    static final class PayloadTooLargeException extends Exception {
        private PayloadTooLargeException(int maxBodyBytes) {
            super("Request body exceeds " + maxBodyBytes + " bytes");
        }
    }

    private static final class ServerThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "kanger-http-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }
}
