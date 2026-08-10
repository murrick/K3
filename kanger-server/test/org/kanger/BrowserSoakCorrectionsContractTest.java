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

/**
 * Regression contract for Browser defects discovered during the 3.7.0.5 VPS
 * development soak. Runtime semantic correctness is covered by the command and
 * Server tests; this class pins the Browser integration boundaries that must
 * remain present in the deployable HTML artifact.
 */
class BrowserSoakCorrectionsContractTest {

    @Test
    void canonicalDialogueRemainsRawWhilePresentationUsesServerIntent() throws Exception {
        String source = html("dialogue.js");

        assertTrue(source.contains("canonical_intent"));
        assertTrue(source.contains("dialogue_help"));
        assertTrue(source.contains("data-kanger-compose"));
        assertTrue(source.contains("confirmation_required"));
        assertTrue(source.contains("parameters.confirmed = true"));
        assertTrue(source.contains("window.confirm(prompt)"));
        assertTrue(source.contains("window.command = dispatch"));
        assertTrue(source.contains("window.query = dispatch"));

        assertFalse(source.contains(".split("),
                "Browser dialogue must not regain a command parser");
        assertFalse(source.contains(".toLowerCase("),
                "Browser dialogue must not normalize command keywords");
    }

    @Test
    void committedProjectionOwnsPanelVisibilityAndRejectedRowsArePruned() throws Exception {
        String source = html("editor-local-file.js");

        assertTrue(source.contains("pruneDeletedStatements"));
        assertTrue(source.contains(
                "setVisible('container-results', hasContent('query-results'))"));
        assertTrue(source.contains(
                "setVisible('container-solutions', hasContent('query-solutions'))"));
        assertTrue(source.contains(
                "setVisible('container-hypothesis', hasContent('query-hypothesis'))"));
        assertTrue(source.contains("lastCommittedSnapshotId"));
        assertTrue(source.contains("actions[i].textContent = '○ tree'"));
    }

    @Test
    void editorCompileNormalizesOnlyTransportCopyAtEof() throws Exception {
        String source = html("editor-local-file.js");

        assertTrue(source.contains("function normalizeCompilePacket(packet)"));
        assertTrue(source.contains("!/[\\r\\n]$/.test(source)"));
        assertTrue(source.contains("encodeURIComponent(source + '\\n')"));
        assertTrue(source.contains(
                "window.KANGER_SOAK_CORRECTIONS = Object.freeze"));
    }

    @Test
    void narrowSemanticRowsAndPromptUseStableFlexGeometry() throws Exception {
        String css = html("presentation.css");

        assertTrue(css.contains(".kanger-semantic-live"));
        assertTrue(css.contains("display: flex !important"));
        assertTrue(css.contains("float: none !important"));
        assertTrue(css.contains("margin-left: auto !important"));
        assertTrue(css.contains("#console-input > span:first-child"));
        assertTrue(css.contains("position: static !important"));
        assertTrue(css.contains("align-items: center"));
    }

    @Test
    void correctionAdapterRemainsInCanonicalBrowserLoadChain() throws Exception {
        String bottom = html("bottom-layout.js");
        assertTrue(bottom.contains("editor-local-file.js"));
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
