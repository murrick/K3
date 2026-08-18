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

class BrowserContainmentContractTest {

    @Test
    void supportedConsoleDeclaresDeterministicOpaqueSandboxScriptTopology()
            throws Exception {
        String console = read(Paths.get("..", "html", "console.html"));
        String rendering = read(Paths.get("..", "html", "javascript.js"));
        String mode = read(Paths.get("..", "html", "javascript-mode.js"));
        String workspace = read(Paths.get("..", "html", "workspace.js"));
        String bottomLayout = read(
                Paths.get("..", "html", "bottom-layout.js"));

        String[] scripts = {
                "jquery-3.6.0.min.js",
                "codemirror.js",
                "javascript.js",
                "javascript-mode.js",
                "javascript-mode-vendor.js",
                "operation.js",
                "workspace.js",
                "editor-state.js",
                "error.js",
                "dialogue.js",
                "presentation.js",
                "bottom-layout.js",
                "editor-local-file.js"
        };
        int previous = -1;
        for (String script : scripts) {
            String token = "src=\"" + script + "\"";
            int position = console.indexOf(token);
            assertTrue(position > previous,
                    script + " is missing or out of canonical order");
            assertTrue(position == console.lastIndexOf(token),
                    script + " must be declared exactly once");
            previous = position;
        }
        assertTrue(console.indexOf("<script>", previous) > previous,
                "historical inline console must load after all authorities");

        assertFalse(rendering.contains("document.write("));
        assertFalse(mode.contains("document.write("));
        assertFalse(workspace.contains("document.write("));
        assertFalse(bottomLayout.contains("document.write("));
    }

    @Test
    void supportedConsoleKeepsBearerInParentOpaqueSandbox() throws Exception {
        String index = read(Paths.get("..", "html", "index.html"));
        String containment = read(Paths.get("..", "html", "containment.js"));
        String console = read(Paths.get("..", "html", "console.html"));
        String error = read(Paths.get("..", "html", "error.js"));

        int containmentPosition = index.indexOf("containment.js");
        int gatewayPosition = index.indexOf("gateway.js");
        assertTrue(containmentPosition >= 0);
        assertTrue(gatewayPosition > containmentPosition);
        assertTrue(index.contains("sandbox=\"allow-scripts\""));
        assertTrue(index.contains("referrerpolicy=\"no-referrer\""));
        assertFalse(index.contains("allow-same-origin"));

        assertTrue(containment.contains("KANGER_CONTAINMENT_BOUNDARY"));
        assertTrue(containment.contains("kanger.containment.v1"));
        assertTrue(containment.contains("event.origin !== 'null'"));
        assertTrue(containment.contains("event.source !== target.contentWindow"));
        assertTrue(containment.contains("source.split(session.token).join('')"));
        assertTrue(containment.contains("Bearer token escaped the parent containment boundary"));
        assertTrue(containment.contains("__KANGER_PARENT_SESSION__"));
        assertTrue(containment.contains("connect-src 'none'"));
        assertTrue(containment.contains("Direct child network access is disabled"));
        assertTrue(containment.contains("containment_context_denied"));
        assertTrue(containment.contains("copy.parameters.token = session.token"));
        assertTrue(containment.contains("}, '*');"));
        assertFalse(containment.contains("allow-same-origin"));
        assertFalse(containment.contains("document.cookie"));
        assertFalse(containment.contains("innerHTML"));
        assertFalse(containment.contains("eval("));
        assertFalse(containment.contains("new Function"));

        int operationPosition = console.indexOf("src=\"operation.js\"");
        int workspacePosition = console.indexOf("src=\"workspace.js\"");
        int errorPosition = console.indexOf("src=\"error.js\"");
        assertTrue(operationPosition >= 0);
        assertTrue(workspacePosition > operationPosition);
        assertTrue(errorPosition > workspacePosition);

        assertTrue(error.contains("KANGER_ERROR_BOUNDARY"));
        assertTrue(error.contains("domain: domain"));
        assertTrue(error.contains("session_action"));
        assertTrue(error.contains("operation_outcome"));
        assertTrue(error.contains("transport_unavailable"));
        assertTrue(error.contains("operation_busy"));
        assertTrue(error.contains("observeWorkspace"));
        assertFalse(error.contains("innerHTML"));
        assertFalse(error.contains("document.cookie"));
        assertFalse(error.contains("eval("));
        assertFalse(error.contains("new Function"));
    }

    private static String read(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "Missing UI file: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
