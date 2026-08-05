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
        assertTrue(gateway.contains("assertConsoleTemplate"));
        assertTrue(gateway.contains("localStorage.getItem(layoutPrefix + name)"));
        assertTrue(gateway.contains("if (name === 'token' || name === 'login') { return; }"));
        assertFalse(gateway.contains("setCookie('token'"));
        assertFalse(gateway.contains("setCookie(\"token\""));
        assertFalse(gateway.contains("getCookie('token'"));
        assertFalse(gateway.contains("getCookie(\"token\""));
        assertFalse(gateway.contains("tokenMonitor"));

        assertTrue(gateway.contains("var PROBE_VALID = 'valid';"));
        assertTrue(gateway.contains("var PROBE_INVALID = 'invalid';"));
        assertTrue(gateway.contains("var PROBE_UNAVAILABLE = 'unavailable';"));
        assertTrue(gateway.contains("return PROBE_UNAVAILABLE;"));
        assertTrue(gateway.contains("probe === PROBE_INVALID"));
        assertTrue(gateway.contains("probe === PROBE_UNAVAILABLE"));
        assertTrue(gateway.contains("It was retained for a later retry."));
        assertTrue(gateway.contains("revocation was not confirmed"));
        assertFalse(gateway.contains("var valid = await probeSession"));
        assertFalse(gateway.contains("var stillValid = await probeSession"));

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

    @Test
    void supportedConsoleInstallsTrustedRenderingBeforeHistoricalReady()
            throws Exception {
        String loader = read(Paths.get("..", "html", "javascript.js"));
        String mode = read(Paths.get("..", "html", "javascript-mode.js"));

        assertTrue(loader.contains("javascript-mode.js"));
        assertTrue(loader.contains("wrapJQueryReady"));
        assertTrue(loader.contains("KANGER_TRUSTED_RENDERING"));
        assertTrue(loader.contains("Object.freeze({version: 1, installed: true})"));

        assertTrue(loader.contains("HISTORY_PREFIX = '@K2@'"));
        assertTrue(loader.contains("encodeHistoryText"));
        assertTrue(loader.contains("decodeHistoryText"));
        assertTrue(loader.contains("legacyDescriptionText"));
        assertTrue(loader.contains("tokenizeLegacyMarkup"));
        assertTrue(loader.contains("protectTextOnlyElement"));

        assertTrue(loader.contains("document.createTextNode"));
        assertTrue(loader.contains("document.createElement('strong')"));
        assertTrue(loader.contains("span.addEventListener('click'"));
        assertTrue(loader.contains("row.textContent = stringValue(entry.record)"));
        assertTrue(loader.contains("cell.textContent = stringValue(value)"));

        assertFalse(loader.contains("insertAdjacentHTML"));
        assertFalse(loader.contains("outerHTML"));
        assertFalse(loader.contains("onclick="));
        assertFalse(loader.contains("div.innerHTML"));
        assertFalse(loader.contains("row.innerHTML"));
        assertFalse(loader.contains("cell.innerHTML"));

        // The vendored CodeMirror mode is preserved byte-for-byte under an
        // explicit name; javascript.js is now the parser-time KANGER loader.
        assertTrue(mode.contains("CodeMirror.defineMode(\"javascript\""));
        assertTrue(mode.contains("CodeMirror.defineMIME(\"text/javascript\""));
    }

    private static String read(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "Missing UI file: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
