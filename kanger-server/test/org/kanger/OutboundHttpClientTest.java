package org.kanger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundHttpClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void performsBoundedGetWithoutChangingJvmTlsDefaults() throws Exception {
        SSLContext sslContext = SSLContext.getDefault();
        HostnameVerifier hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        start(new FixedHandler(200, "platform-defaults"));

        String response = OutboundHttpClient.request(
                url("/get"), null, "UTF-8", 1000,
                Collections.singletonMap("X-Test", "yes"));

        assertEquals("platform-defaults", response);
        assertSame(sslContext, SSLContext.getDefault());
        assertSame(hostnameVerifier, HttpsURLConnection.getDefaultHostnameVerifier());
    }

    @Test
    void postsUsingRequestedEncoding() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/post", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] request = readAll(exchange.getRequestBody());
                byte[] response = request;
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            }
        });
        server.start();

        assertEquals("Привет",
                OutboundHttpClient.request(url("/post"), "Привет", "UTF-8",
                        1000, null));
    }

    @Test
    void rejectsNonHttpProtocolsBeforeOpeningConnection() {
        assertThrows(ProtocolException.class,
                () -> OutboundHttpClient.request(
                        "file:/tmp/kanger.conf", null, "UTF-8", 1000, null));
    }

    @Test
    void surfacesHttpErrorStatusAndResponse() throws Exception {
        start(new FixedHandler(503, "maintenance"));

        IOException error = assertThrows(IOException.class,
                () -> OutboundHttpClient.request(
                        url("/failure"), null, "UTF-8", 1000, null));

        assertTrue(error.getMessage().contains("HTTP 503"));
        assertTrue(error.getMessage().contains("maintenance"));
    }

    @Test
    void rejectsResponseAboveConfiguredBoundary() throws Exception {
        final byte[] response = new byte[OutboundHttpClient.MAX_RESPONSE_BYTES + 1];
        start(new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            }
        });

        IOException error = assertThrows(IOException.class,
                () -> OutboundHttpClient.request(
                        url("/large"), null, "UTF-8", 1000, null));

        assertTrue(error.getMessage().contains("exceeds"));
    }

    private void start(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        byte[] buffer = new byte[1024];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class FixedHandler implements HttpHandler {
        private final int status;
        private final byte[] body;

        private FixedHandler(int status, String body) {
            this.status = status;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
