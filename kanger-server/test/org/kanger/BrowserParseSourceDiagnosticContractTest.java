/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Browser side of the canonical source-located diagnostic contract.
 * The Server owns UTF-16 offsets; the Browser may only project a trusted span
 * onto the exact Editor source submitted by the matching compile operation.
 */
class BrowserParseSourceDiagnosticContractTest {

    @Test
    void compileDiagnosticIsBoundToExactSubmittedOperationAndSource() throws Exception {
        String source = html("error.js");

        assertTrue(source.contains("KANGER_ERROR_SOURCE_PRESENTATION"));
        assertTrue(source.contains("activeBefore === 0 && activeAfter > 0"));
        assertTrue(source.contains("operationId: activeAfter"));
        assertTrue(source.contains("source: submittedSource"));
        assertTrue(source.contains("Number(data.client_operation_id)"));
        assertTrue(source.contains("=== pendingCompile.operationId"));
        assertTrue(source.contains("editorText() !== submitted.source"));
        assertTrue(source.contains("window.setTimeout(function ()"));
    }

    @Test
    void canonicalSpanIsValidatedAndProjectedWithoutTextParsing() throws Exception {
        String source = html("error.js");

        assertTrue(source.contains("data.error.schema !== 1"));
        assertTrue(source.contains("var source = data.error.source"));
        assertTrue(source.contains("Number.isInteger(offset)"));
        assertTrue(source.contains("Number.isInteger(length)"));
        assertTrue(source.contains("length > submittedSource.length"));
        assertTrue(source.contains("offset > submittedSource.length - length"));
        assertTrue(source.contains("instance.posFromIndex(span.offset)"));
        assertTrue(source.contains("instance.setCursor(start)"));
        assertTrue(source.contains("instance.setSelection(start, end)"));
        assertTrue(source.contains("window.openEditor(null)"));

        assertFalse(source.contains("data.source.offset"),
                "Source location must remain nested in the canonical error envelope");
        assertFalse(source.contains("parseInt(data.description"),
                "Browser must not recover source coordinates from human text");
    }

    private String html(String name) throws Exception {
        Path[] candidates = new Path[] {
                Paths.get("..", "html", name),
                Paths.get("html", name)
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Browser artifact file not found: " + name);
    }
}
