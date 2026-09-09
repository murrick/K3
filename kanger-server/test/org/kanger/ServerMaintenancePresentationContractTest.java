package org.kanger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMaintenancePresentationContractTest {

    @Test
    void maintenanceNoticeHandsOffFromLoginAndMovesWholePresentationGrid()
            throws Exception {
        String maintenance = read(Paths.get("..", "html", "maintenance.js"));
        String presentation = read(Paths.get("..", "html", "presentation.css"));

        assertTrue(maintenance.contains("function handoffToConsole()"));
        assertTrue(maintenance.contains("hideParent();\n        return postToConsole"));
        assertTrue(maintenance.contains("MutationObserver"));
        assertTrue(maintenance.contains("attributeFilter: ['class']"));

        assertTrue(presentation.contains("--kanger-header: 32px"));
        assertTrue(presentation.contains("--kanger-maintenance-row: 33px"));
        assertTrue(presentation.contains("#server-maintenance-notice"));
        assertTrue(presentation.contains("top: var(--kanger-header) !important"));
        assertTrue(presentation.contains("#container-right.kanger-center"));
        assertTrue(presentation.contains("top: 0 !important"));
        assertTrue(presentation.contains(":has(#server-maintenance-notice[style*=\"display: block\"])"));
        assertTrue(presentation.contains(
                "top: calc(var(--kanger-header) + var(--kanger-maintenance-row)) !important"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
