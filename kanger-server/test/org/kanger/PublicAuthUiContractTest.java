/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicAuthUiContractTest {

    @Test
    void authenticationGatewayProjectsServerPolicyAndOwnsBrowserSession()
            throws Exception {
        String index = read(Paths.get("..", "html", "index.html"));
        String gateway = read(Paths.get("..", "html", "gateway.js"));
        String config = read(Paths.get("..", "html", "config.js"));
        String console = read(Paths.get("..", "html", "console.html"));

        assertTrue(index.contains("gateway.js"));
        assertTrue(index.contains("console-frame"));
        assertFalse(index.contains("document.cookie"));
        assertFalse(index.contains("innerHTML"));
        assertFalse(index.contains("tokenMonitor"));

        assertTrue(gateway.contains("registration_policy"));
        assertTrue(gateway.contains("public_registration"));
        assertTrue(gateway.contains("sign-in remains a separate step"));
        assertTrue(gateway.contains("EMAIL_CONFIRMATION_REQUIRED"));
        assertTrue(gateway.contains("pending_action_token"));
        assertTrue(gateway.contains("change_pending_email"));
        assertTrue(gateway.contains("cancel_pending"));
        assertTrue(gateway.contains("E-mail confirmed. Sign in to create a session."));
        assertFalse(gateway.contains("?confirm="));
        assertFalse(gateway.contains("innerHTML"));

        assertTrue(gateway.contains("kanger.applicationSession.v1"));
        assertTrue(gateway.contains("sessionStorage"));
        assertTrue(gateway.contains("Max-Age=0"));
        assertTrue(gateway.contains("KANGER_SESSION_BOOTSTRAP"));
        assertTrue(gateway.contains("Object.freeze"));
        assertTrue(gateway.contains("kanger.session.v1"));
        assertTrue(gateway.contains("event.source !== consoleFrame.contentWindow"));
        assertTrue(gateway.contains("data.generation !== state.session.generation"));
        assertTrue(gateway.contains("session.logout"));
        assertTrue(gateway.contains("session.credentials.change"));
        assertTrue(gateway.contains("state.loginInFlight"));
        assertTrue(gateway.contains("preloadConsoleTemplate"));
        assertTrue(gateway.contains("localStorage.getItem(layoutPrefix + name)"));
        assertTrue(gateway.contains("if (name === 'token' || name === 'login') { return; }"));
        assertFalse(gateway.contains("setCookie('token'"));
        assertFalse(gateway.contains("setCookie(\"token\""));
        assertFalse(gateway.contains("getCookie('token'"));
        assertFalse(gateway.contains("getCookie(\"token\""));
        assertFalse(gateway.contains("tokenMonitor"));

        String consoleMarker = "window.apihost = \"http://localhost:1964\";\n"
                + "        window.token = \"\";";
        assertTrue(console.contains(consoleMarker));
        assertTrue(gateway.contains("window.apihost = \"http://localhost:1964\";\\n"
                + "        window.token = \"\";"));

        assertTrue(config.contains("http://localhost:1964"));
        assertTrue(config.contains("https://api.kanger.org"));

        // The historical console remains the semantic UI implementation. The
        // gateway injects its session adapter before jQuery.ready executes.
        assertTrue(console.contains("function registerForm"));
        assertTrue(console.contains("function command("));
        assertTrue(console.contains("function commandQuit"));
        assertTrue(console.contains("function loginCheck"));
    }

    private static String read(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "Missing UI file: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
