/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerTest {

    @Test
    void queryParametersAreDecodedWithoutLosingEmbeddedSeparators() throws Exception {
        JSONObject parameters = HttpServer.parseQueryParameters(
                "Name=John+Doe&token=a%3Db%26c&empty");

        assertEquals("John Doe", parameters.getString("name"));
        assertEquals("a=b&c", parameters.getString("token"));
        assertEquals("", parameters.getString("empty"));
    }

    @Test
    void malformedUrlEncodingIsRejected() {
        assertThrows(HttpServer.MalformedRequestException.class,
                () -> HttpServer.parseQueryParameters("value=%GG"));
    }

    @Test
    void corsOriginMustMatchConfiguredOriginExactly() {
        assertTrue(HttpServer.isOriginAllowed(
                "https://kanger.example",
                Arrays.asList("https://kanger.example", "https://admin.example")));
        assertFalse(HttpServer.isOriginAllowed(
                "https://kanger.example.evil.invalid",
                Collections.singletonList("https://kanger.example")));
        assertFalse(HttpServer.isOriginAllowed(
                "https://kanger.example",
                Collections.<String>emptyList()));
        assertFalse(HttpServer.isOriginAllowed(null,
                Collections.singletonList("https://kanger.example")));
    }

    @Test
    void requestBodyAtConfiguredLimitIsAccepted() throws Exception {
        byte[] body = "12345678".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(body,
                HttpServer.readBody(new ByteArrayInputStream(body), body.length));
    }

    @Test
    void requestBodyAboveConfiguredLimitIsRejected() {
        byte[] body = "123456789".getBytes(StandardCharsets.UTF_8);

        assertThrows(HttpServer.PayloadTooLargeException.class,
                () -> HttpServer.readBody(new ByteArrayInputStream(body), 8));
    }

    @Test
    void safeRequestIdIsPreservedAndUnsafeValueIsReplaced() {
        assertEquals("nginx-0123:abc",
                HttpServer.resolveRequestId("nginx-0123:abc"));

        String generated = HttpServer.resolveRequestId("bad\r\nInjected: value");
        assertTrue(generated.startsWith("kanger-"));
        assertFalse(generated.contains("\r"));
        assertFalse(generated.contains("\n"));
    }

    @Test
    void everyJsonResponseCarriesCoreApiAndServerIdentity() {
        JSONObject response = HttpServer.withVersionIdentity(
                new JSONObject()
                        .put("result", "OK")
                        .put("version", "legacy"));

        assertEquals("3.3", response.getString("version"));
        assertEquals("3.3", response.getString("core_version"));
        assertEquals("1", response.getString("api_version"));
        assertEquals("server-0.13", response.getString("server_version"));
    }

    @Test
    void saturatedQueueRunsOnlyRejectionResponseInline() throws Exception {
        HttpServer.OperationalState state = new HttpServer.OperationalState();
        ThreadPoolExecutor executor = HttpServer.createWorkerPool(1, 1, state);
        state.attach(executor, 1, 1);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean overloadMarker = new AtomicBoolean(false);
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    entered.countDown();
                    try {
                        release.await(5L, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            assertTrue(entered.await(2L, TimeUnit.SECONDS));

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // occupies the only queue slot while the first task blocks
                }
            });
            assertFalse(state.isReady(true));

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    overloadMarker.set(HttpServer.isOverloadDispatch());
                }
            });

            assertTrue(overloadMarker.get());
            assertEquals(1L, state.overloadRejections());
        } finally {
            release.countDown();
            executor.shutdown();
            executor.awaitTermination(5L, TimeUnit.SECONDS);
            state.detach();
        }
    }
}
