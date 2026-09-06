/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpServerSensitiveQueryTest {

    @Test
    void authenticationCredentialsAreRejectedFromUrlQuery() {
        assertThrows(HttpServer.MalformedRequestException.class,
                () -> reject("login=user@example.test"));
        assertThrows(HttpServer.MalformedRequestException.class,
                () -> reject("currentlogin=user@example.test"));
        assertThrows(HttpServer.MalformedRequestException.class,
                () -> reject("password=secret"));
        assertThrows(HttpServer.MalformedRequestException.class,
                () -> reject("currentpassword=secret"));
    }

    @Test
    void confirmationAndSessionTokensRemainAllowedInUrlQuery() {
        assertDoesNotThrow(() -> reject("confirm=opaque-confirmation-token"));
        assertDoesNotThrow(() -> reject("token=opaque-session-token"));
    }

    private static void reject(String rawQuery) throws Exception {
        HttpServer.rejectSensitiveQueryParameters(
                HttpServer.parseQueryParameters(rawQuery));
    }
}
