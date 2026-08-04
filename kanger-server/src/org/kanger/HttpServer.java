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
import org.json.JSONException;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    static final String API_VERSION_S = "1";

    private static final ThreadLocal<Boolean> OVERLOAD_DISPATCH =
            new ThreadLocal<Boolean>();
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

    private final Object lifecycleMonitor = new Object();
    private final OperationalState operationalState = new OperationalState();

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
        final ThreadPoolExecutor workers = createWorkerPool(
                maxThreads, queueCapacity, operationalState);

        final com.sun.net.httpserver.HttpServer created =
                com.sun.net.httpserver.HttpServer.create(socketAddress, backlog);
        created.setExecutor(workers);
        created.createContext("/", new ApiHandler(
                reactor, maxBodyBytes, operationalState, this));

        synchronized (lifecycleMonitor) {
            if (active) {
                created.stop(0);
                workers.shutdownNow();
                throw new IllegalStateException("HTTP server is already running");
            }
            server = created;
            executor = workers;
            operationalState.attach(workers, queueCapacity, maxThreads);
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

        operationalState.markStopping();
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
        operationalState.detach();
    }

    static ThreadPoolExecutor createWorkerPool(int maxThreads,
                                               int queueCapacity,
                                               OperationalState state) {
        if (state == null) {
            throw new IllegalArgumentException("operational state must not be null");
        }
        ThreadPoolExecutor workers = new ThreadPoolExecutor(
                Math.min(2, maxThreads),
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(queueCapacity),
                new ServerThreadFactory(),
                new OverloadRejectedExecutionHandler(state));
        workers.allowCoreThreadTimeOut(true);
        return workers;
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

    static JSONObject parseQueryParameters(String rawQuery)
            throws MalformedRequestException {
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

    static String resolveRequestId(String supplied) {
        if (supplied != null) {
            String candidate = supplied.trim();
            if (!candidate.isEmpty()
                    && candidate.length() <= 64
                    && candidate.matches("[A-Za-z0-9._:-]+")) {
                return candidate;
            }
        }
        return "kanger-"
                + Long.toString(System.currentTimeMillis(), 36)
                + "-"
                + Long.toString(REQUEST_SEQUENCE.incrementAndGet(), 36);
    }

    static boolean isOverloadDispatch() {
        return Boolean.TRUE.equals(OVERLOAD_DISPATCH.get());
    }

    private static String decode(String value) throws MalformedRequestException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (IllegalArgumentException ex) {
            throw new MalformedRequestException("Malformed URL encoding", ex);
        } catch (IOException ex) {
            throw new MalformedRequestException("UTF-8 decoder unavailable", ex);
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
            throws MalformedRequestException {
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

    private static String normalizeContext(String rawPath)
            throws MalformedRequestException {
        String context = rawPath == null ? "" : decode(rawPath);
        while (context.startsWith("/")) {
            context = context.substring(1);
        }
        return context;
    }

    static JSONObject withVersionIdentity(JSONObject response) {
        if (response == null) {
            return null;
        }
        return response
                .put("version", Version.CORE_VERSION_S)
                .put("core_version", Version.CORE_VERSION_S)
                .put("api_version", API_VERSION_S)
                .put("server_version", Version.SERVER_VERSION_S);
    }

    private static void sendJson(HttpExchange exchange,
                                 int status,
                                 JSONObject response,
                                 String allowedOrigin,
                                 String requestId) throws IOException {
        JSONObject versionedResponse = withVersionIdentity(response);
        byte[] body = versionedResponse == null
                ? new byte[0]
                : versionedResponse.toString().getBytes(StandardCharsets.UTF_8);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Request-ID", requestId);
        if (status == 503) {
            headers.set("Retry-After", "1");
        }
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

    private boolean isTransportActive() {
        return active && server != null && executor != null;
    }

    private static String logPath(HttpExchange exchange) {
        URI uri = exchange.getRequestURI();
        String raw = uri == null ? "" : uri.getRawPath();
        if (raw == null || raw.isEmpty()) {
            return "/";
        }
        StringBuilder safe = new StringBuilder(Math.min(raw.length(), 128));
        for (int index = 0; index < raw.length() && safe.length() < 128; index++) {
            char value = raw.charAt(index);
            if ((value >= 'a' && value <= 'z')
                    || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '/' || value == '-' || value == '_'
                    || value == '.' || value == '%') {
                safe.append(value);
            } else {
                safe.append('_');
            }
        }
        return safe.toString();
    }

    private static void logCompletion(String requestId,
                                      String method,
                                      String path,
                                      int status,
                                      long startNanos,
                                      OperationalState state) {
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                Math.max(0L, System.nanoTime() - startNanos));
        String record = "http request_id=" + requestId
                + " method=" + method
                + " path=" + path
                + " status=" + status
                + " duration_ms=" + durationMillis
                + " active=" + state.activeRequests()
                + " queued=" + state.queuedRequests();
        if (status >= 500 && status != 503) {
            Watchdog.err(record);
        } else if (status == 503) {
            Watchdog.warn(record);
        } else {
            Watchdog.log(record);
        }
    }

    private static final class ApiHandler implements HttpHandler {
        private final IReactor<JSONObject> reactor;
        private final int maxBodyBytes;
        private final OperationalState state;
        private final HttpServer owner;

        private ApiHandler(IReactor<JSONObject> reactor,
                           int maxBodyBytes,
                           OperationalState state,
                           HttpServer owner) {
            this.reactor = reactor;
            this.maxBodyBytes = maxBodyBytes;
            this.state = state;
            this.owner = owner;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = resolveRequestId(
                    exchange.getRequestHeaders().getFirst("X-Request-ID"));
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            String path = logPath(exchange);
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            String allowedOrigin = isOriginAllowed(origin, configuredOrigins()) ? origin : null;
            long startNanos = System.nanoTime();
            int status = 500;

            state.requestStarted();
            try {
                if (isOverloadDispatch()) {
                    status = 503;
                    sendJson(exchange, status,
                            error("Server request capacity is exhausted"),
                            allowedOrigin, requestId);
                    return;
                }

                if ("OPTIONS".equals(method)) {
                    status = handlePreflight(exchange, allowedOrigin, requestId);
                    return;
                }
                if (!"GET".equals(method) && !"POST".equals(method)) {
                    exchange.getResponseHeaders().set("Allow", "GET, POST, OPTIONS");
                    status = 405;
                    sendJson(exchange, status, error("Method not allowed"),
                            allowedOrigin, requestId);
                    return;
                }

                String context = normalizeContext(
                        exchange.getRequestURI().getRawPath());
                if ("health".equals(context)) {
                    status = 200;
                    sendJson(exchange, status,
                            new JSONObject()
                                    .put("result", "OK")
                                    .put("status", "UP")
                                    .put("version", Version.VERSION_S),
                            allowedOrigin, requestId);
                    return;
                }
                if ("ready".equals(context)) {
                    boolean ready = state.isReady(owner.isTransportActive());
                    status = ready ? 200 : 503;
                    sendJson(exchange, status,
                            state.readiness(owner.isTransportActive()),
                            allowedOrigin, requestId);
                    return;
                }

                String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
                if (contentLength != null) {
                    long declaredLength = Long.parseLong(contentLength);
                    if (declaredLength < 0L || declaredLength > maxBodyBytes) {
                        status = 413;
                        sendJson(exchange, status,
                                error("Request body exceeds configured limit"),
                                allowedOrigin, requestId);
                        return;
                    }
                }

                byte[] body = readBody(exchange.getRequestBody(), maxBodyBytes);
                JSONObject packet = createPacket(exchange, body);
                Object applicationResponse = reactor.run(packet);
                if (applicationResponse != null
                        && !(applicationResponse instanceof JSONObject)) {
                    throw new IllegalStateException(
                            "Reactor returned unsupported response type");
                }
                JSONObject response = (JSONObject) applicationResponse;
                if (response == null) {
                    status = 404;
                    sendJson(exchange, status, error("Unknown API context"),
                            allowedOrigin, requestId);
                } else {
                    status = 200;
                    sendJson(exchange, status, response, allowedOrigin, requestId);
                }
            } catch (PayloadTooLargeException ex) {
                status = 413;
                sendJson(exchange, status, error(ex.getMessage()),
                        allowedOrigin, requestId);
            } catch (MalformedRequestException ex) {
                status = 400;
                sendJson(exchange, status, error(ex.getMessage()),
                        allowedOrigin, requestId);
            } catch (NumberFormatException ex) {
                status = 400;
                sendJson(exchange, status, error("Invalid Content-Length"),
                        allowedOrigin, requestId);
            } catch (JSONException ex) {
                status = 400;
                sendJson(exchange, status, error("Malformed JSON request"),
                        allowedOrigin, requestId);
            } catch (Exception ex) {
                status = 500;
                Watchdog.err("http request_id=" + requestId
                        + " failure=" + ex.getClass().getName()
                        + " message=" + safeMessage(ex.getMessage()));
                sendJson(exchange, status, error("Internal server error"),
                        allowedOrigin, requestId);
            } finally {
                state.requestFinished(status);
                logCompletion(requestId, method, path, status, startNanos, state);
                exchange.close();
            }
        }

        private int handlePreflight(HttpExchange exchange,
                                    String allowedOrigin,
                                    String requestId) throws IOException {
            if (allowedOrigin == null) {
                sendJson(exchange, 403, error("Origin is not allowed"),
                        null, requestId);
                return 403;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type, X-Request-ID");
            headers.set("Access-Control-Max-Age", "600");
            sendJson(exchange, 204, null, allowedOrigin, requestId);
            return 204;
        }

        private static String safeMessage(String message) {
            if (message == null || message.isEmpty()) {
                return "none";
            }
            StringBuilder safe = new StringBuilder(Math.min(message.length(), 160));
            for (int index = 0; index < message.length() && safe.length() < 160; index++) {
                char value = message.charAt(index);
                safe.append(Character.isISOControl(value) ? ' ' : value);
            }
            return safe.toString();
        }
    }

    static final class OperationalState {
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong failedRequests = new AtomicLong();
        private final AtomicLong overloadRejections = new AtomicLong();
        private final AtomicInteger activeRequests = new AtomicInteger();

        private volatile ThreadPoolExecutor workers;
        private volatile int queueCapacity;
        private volatile int maxWorkers;
        private volatile long startedAtMillis;
        private volatile boolean stopping = true;

        void attach(ThreadPoolExecutor workers, int queueCapacity, int maxWorkers) {
            this.workers = workers;
            this.queueCapacity = queueCapacity;
            this.maxWorkers = maxWorkers;
            this.startedAtMillis = System.currentTimeMillis();
            this.stopping = false;
            totalRequests.set(0L);
            failedRequests.set(0L);
            overloadRejections.set(0L);
            activeRequests.set(0);
        }

        void markStopping() {
            stopping = true;
        }

        void detach() {
            workers = null;
            stopping = true;
        }

        void requestStarted() {
            totalRequests.incrementAndGet();
            activeRequests.incrementAndGet();
        }

        void requestFinished(int status) {
            if (status >= 400) {
                failedRequests.incrementAndGet();
            }
            activeRequests.decrementAndGet();
        }

        void rejected() {
            overloadRejections.incrementAndGet();
        }

        int activeRequests() {
            return activeRequests.get();
        }

        int queuedRequests() {
            ThreadPoolExecutor current = workers;
            return current == null ? 0 : current.getQueue().size();
        }

        long overloadRejections() {
            return overloadRejections.get();
        }

        boolean isReady(boolean transportActive) {
            ThreadPoolExecutor current = workers;
            return transportActive
                    && !stopping
                    && current != null
                    && !current.isShutdown()
                    && current.getQueue().remainingCapacity() > 0;
        }

        JSONObject readiness(boolean transportActive) {
            ThreadPoolExecutor current = workers;
            boolean ready = isReady(transportActive);
            int queued = current == null ? 0 : current.getQueue().size();
            int remaining = current == null ? 0 : current.getQueue().remainingCapacity();
            return new JSONObject()
                    .put("result", ready ? "OK" : "error")
                    .put("status", ready ? "READY" : "NOT_READY")
                    .put("version", Version.VERSION_S)
                    .put("uptime_millis", Math.max(0L,
                            System.currentTimeMillis() - startedAtMillis))
                    .put("active_requests", activeRequests.get())
                    .put("queued_requests", queued)
                    .put("queue_remaining", remaining)
                    .put("queue_capacity", queueCapacity)
                    .put("max_workers", maxWorkers)
                    .put("total_requests", totalRequests.get())
                    .put("failed_requests", failedRequests.get())
                    .put("overload_rejections", overloadRejections.get());
        }
    }

    static final class PayloadTooLargeException extends Exception {
        private PayloadTooLargeException(int maxBodyBytes) {
            super("Request body exceeds " + maxBodyBytes + " bytes");
        }
    }

    static final class MalformedRequestException extends Exception {
        private MalformedRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class OverloadRejectedExecutionHandler
            implements RejectedExecutionHandler {
        private final OperationalState state;

        private OverloadRejectedExecutionHandler(OperationalState state) {
            this.state = state;
        }

        @Override
        public void rejectedExecution(Runnable runnable,
                                      ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("HTTP executor is shutting down");
            }
            state.rejected();
            OVERLOAD_DISPATCH.set(Boolean.TRUE);
            try {
                runnable.run();
            } finally {
                OVERLOAD_DISPATCH.remove();
            }
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
