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
        assertTrue(gateway.contains(
                "E-mail confirmed. Sign in to create a session."));
        assertFalse(gateway.contains("?confirm="));
        assertFalse(gateway.contains("innerHTML"));

        assertTrue(gateway.contains("kanger.applicationSession.v1"));
        assertTrue(gateway.contains("sessionStorage"));
        assertTrue(gateway.contains("Max-Age=0"));
        assertTrue(gateway.contains("KANGER_SESSION_BOOTSTRAP"));
        assertTrue(gateway.contains("Object.freeze"));
        assertTrue(gateway.contains("kanger.session.v1"));
        assertTrue(gateway.contains(
                "event.source !== consoleFrame.contentWindow"));
        assertTrue(gateway.contains(
                "data.generation !== state.session.generation"));
        assertTrue(gateway.contains("session.logout"));
        assertTrue(gateway.contains("session.credentials.change"));
        assertTrue(gateway.contains("state.loginInFlight"));
        assertTrue(gateway.contains("preloadConsoleTemplate"));
        assertTrue(gateway.contains("assertConsoleTemplate"));
        assertTrue(gateway.contains(
                "localStorage.getItem(layoutPrefix + name)"));
        assertTrue(gateway.contains(
                "if (name === 'token' || name === 'login') { return; }"));
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

        String consoleMarker =
                "window.apihost = \"http://localhost:1964\";\n"
                + "        window.token = \"\";";
        assertTrue(console.contains(consoleMarker));
        assertTrue(gateway.contains(
                "window.apihost = \"http://localhost:1964\";\\n"
                + "        window.token = \"\";"));

        assertTrue(config.contains("http://localhost:1964"));
        assertTrue(config.contains("https://api.kanger.org"));

        assertTrue(console.contains("function registerForm"));
        assertTrue(console.contains("function command("));
        assertTrue(console.contains("function commandQuit"));
        assertTrue(console.contains("function loginCheck"));
    }

    @Test
    void supportedConsoleInstallsTrustedRenderingBeforeHistoricalReady()
            throws Exception {
        String rendering = read(Paths.get("..", "html", "javascript.js"));
        String modeLoader = read(
                Paths.get("..", "html", "javascript-mode.js"));
        String modeVendor = read(
                Paths.get("..", "html", "javascript-mode-vendor.js"));

        assertTrue(rendering.contains("javascript-mode.js"));
        assertTrue(rendering.contains("wrapJQueryReady"));
        assertTrue(rendering.contains("KANGER_TRUSTED_RENDERING"));
        assertTrue(rendering.contains(
                "Object.freeze({version: 1, installed: true})"));

        assertTrue(rendering.contains("HISTORY_PREFIX = '@K2@'"));
        assertTrue(rendering.contains("encodeHistoryText"));
        assertTrue(rendering.contains("decodeHistoryText"));
        assertTrue(rendering.contains("legacyDescriptionText"));
        assertTrue(rendering.contains("tokenizeLegacyMarkup"));
        assertTrue(rendering.contains("protectTextOnlyElement"));

        assertTrue(rendering.contains("document.createTextNode"));
        assertTrue(rendering.contains("document.createElement('strong')"));
        assertTrue(rendering.contains("span.addEventListener('click'"));
        assertTrue(rendering.contains(
                "row.textContent = stringValue(entry.record)"));
        assertTrue(rendering.contains(
                "cell.textContent = stringValue(value)"));

        assertFalse(rendering.contains("insertAdjacentHTML"));
        assertFalse(rendering.contains("outerHTML"));
        assertFalse(rendering.contains("onclick="));
        assertFalse(rendering.contains("div.innerHTML"));
        assertFalse(rendering.contains("row.innerHTML"));
        assertFalse(rendering.contains("cell.innerHTML"));

        int vendorPosition = modeLoader.indexOf("javascript-mode-vendor.js");
        int operationPosition = modeLoader.indexOf("operation.js");
        int workspacePosition = modeLoader.indexOf("workspace.js");
        assertTrue(vendorPosition >= 0);
        assertTrue(operationPosition > vendorPosition);
        assertTrue(workspacePosition > operationPosition);
        assertTrue(modeVendor.contains(
                "CodeMirror.defineMode(\"javascript\""));
        assertTrue(modeVendor.contains(
                "CodeMirror.defineMIME(\"text/javascript\""));
    }

    @Test
    void supportedConsoleSerializesMutationsAndCommitsCoherentSnapshots()
            throws Exception {
        String operation = read(Paths.get("..", "html", "operation.js"));

        assertTrue(operation.contains("KANGER_OPERATION_PROTOCOL"));
        assertTrue(operation.contains("observeTrustedRendering"));
        assertTrue(operation.contains("isMutationPacket"));
        assertTrue(operation.contains("activeMutation"));
        assertTrue(operation.contains("operation_busy"));
        assertTrue(operation.contains("operation_timeout"));
        assertTrue(operation.contains("client_operation_id"));
        assertTrue(operation.contains("client_generation"));
        assertTrue(operation.contains("client_snapshot_id"));

        assertTrue(operation.contains("snapshot.staging"));
        assertTrue(operation.contains("snapshot.pending"));
        assertTrue(operation.contains("maybeCommitSnapshot"));
        assertTrue(operation.contains("moveChildren"));
        assertTrue(operation.contains("snapshotIsCurrent"));
        assertTrue(operation.contains("generation !== state.generation"));

        assertTrue(operation.contains("original.logRequest(queryText);"));
        assertTrue(operation.contains("original.logResponse(data, presentation);"));
        assertTrue(operation.contains("callback();"));
        assertTrue(operation.contains("scheduleSnapshot(data);"));

        assertFalse(operation.contains("innerHTML"));
        assertFalse(operation.contains("document.cookie"));
        assertFalse(operation.contains("eval("));
        assertFalse(operation.contains("new Function"));
    }

    @Test
    void supportedConsoleUsesCanonicalWorkspaceProjectionAfterOperations()
            throws Exception {
        String workspace = read(Paths.get("..", "html", "workspace.js"));

        assertTrue(workspace.contains("KANGER_WORKSPACE_STATE"));
        assertTrue(workspace.contains("observeOperationProtocol"));
        assertTrue(workspace.contains("canonicalSourceName"));
        assertTrue(workspace.contains("canonicalStorageName"));
        assertTrue(workspace.contains("repository_state"));
        assertTrue(workspace.contains("physical_generation"));
        assertTrue(workspace.contains("applyProjection"));
        assertTrue(workspace.contains("responseGeneration < state.generation"));
        assertTrue(workspace.contains("finally"));
        assertTrue(workspace.contains("["));
        assertTrue(workspace.contains("data.code"));

        assertFalse(workspace.contains("innerHTML"));
        assertFalse(workspace.contains("document.cookie"));
        assertFalse(workspace.contains("eval("));
        assertFalse(workspace.contains("new Function"));
    }

    private static String read(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "Missing UI file: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
