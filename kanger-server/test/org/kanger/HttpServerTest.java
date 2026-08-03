/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

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
        assertThrows(IOException.class,
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
}
