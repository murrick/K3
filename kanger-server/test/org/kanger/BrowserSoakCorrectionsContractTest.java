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
 * Regression contract for Browser defects discovered during the 3.7.0.5 and
 * 3.7.0.6 VPS development soaks. Runtime semantic correctness is covered by
 * command and Server tests; this class pins Browser integration boundaries
 * that must remain present in the deployable HTML artifact.
 */
class BrowserSoakCorrectionsContractTest {

    @Test
    void canonicalDialogueRemainsRawWhilePresentationUsesServerIntent() throws Exception {
        String source = html("dialogue.js");

        assertTrue(source.contains("canonical_intent"));
        assertTrue(source.contains("dialogue_help"));
        assertTrue(source.contains("data-kanger-compose"));
        assertTrue(source.contains("function composeSyntax(value)"));
        assertTrue(source.contains("command.compose"));
        assertTrue(source.contains("confirmation_required"));
        assertTrue(source.contains("function requestConfirmation(prompt, callback)"));
        assertTrue(source.contains("parameters.confirmed = true"));
        assertTrue(source.contains("window.showTransactionLevel(data)"));
        assertTrue(source.contains("window.command = dispatch"));
        assertTrue(source.contains("window.query = dispatch"));

        assertFalse(source.contains("window.confirm(prompt)"),
                "Opaque Browser sandbox must not depend on native modal authority");
        assertFalse(source.contains(".split("),
                "Browser dialogue must not regain a command parser");
        assertFalse(source.contains(".toLowerCase("),
                "Browser dialogue must not normalize command keywords");
    }

    @Test
    void workspaceProjectionOwnsTransactionRendering() throws Exception {
        String source = html("workspace.js");

        assertTrue(source.contains("function renderTransaction(transaction)"));
        assertTrue(source.contains("renderTransaction(data.workspace.transaction)"));
        assertTrue(source.contains("document.getElementById('transaction-level')"));
        assertFalse(source.contains("data.transaction"),
                "Workspace authority must not depend on legacy top-level transaction state");
        assertFalse(source.contains("data.empty"),
                "Workspace authority must not depend on legacy top-level empty state");
    }

    @Test
    void committedProjectionOwnsPanelVisibilityAndRejectedRowsArePruned() throws Exception {
        String source = html("editor-local-file.js");

        assertTrue(source.contains("pruneDeletedStatements"));
        assertTrue(source.contains(
                "setVisible('container-results', expectedVisible('container-results'))"));
        assertTrue(source.contains(
                "setVisible('container-solutions', expectedVisible('container-solutions'))"));
        assertTrue(source.contains(
                "setVisible('container-hypothesis', expectedVisible('container-hypothesis'))"));
        assertTrue(source.contains("lastCommittedSnapshotId"));
        assertTrue(source.contains("actions[i].textContent = '○'"));
        assertTrue(source.contains("actions[i].title = 'tree'"));
        assertFalse(source.contains("actions[i].textContent = '○ tree'"));
        assertTrue(source.contains("function projectionMutation(mutations)"));
        assertTrue(source.contains(
                "visible(mutation.target.id)\n                        !== expectedVisible(mutation.target.id)"));
        assertFalse(source.contains("new window.MutationObserver(scheduleSync)"),
                "Layout-only style mutations must not feed projection refresh indefinitely");
    }

    @Test
    void editorCompileTransportPreservesExactSourceBytes() throws Exception {
        String source = html("editor-local-file.js");

        assertFalse(source.contains("normalizeCompilePacket"),
                "Browser must not rewrite source bytes for compiler EOF handling");
        assertFalse(source.contains("encodeURIComponent(source + '\\n')"),
                "Browser must not append a synthetic final EOL");
        assertTrue(source.contains(
                "Compiler-only EOF normalization is owned by the Server source boundary"));
        assertTrue(source.contains(
                "window.KANGER_SOAK_CORRECTIONS = Object.freeze"));
    }

    @Test
    void narrowSemanticRowsPromptAndLeftSplitterUseStableGeometry() throws Exception {
        String source = html("editor-local-file.js");
        String css = html("presentation.css");

        assertTrue(css.contains(".kanger-semantic-live"));
        assertTrue(css.contains("display: flex !important"));
        assertTrue(css.contains("float: none !important"));
        assertTrue(css.contains("margin-left: auto !important"));
        assertTrue(css.contains("#console-input > span:first-child"));
        assertTrue(css.contains("position: static !important"));
        assertTrue(css.contains("align-items: center"));

        assertTrue(source.contains("function installLeftSplitter()"));
        assertTrue(source.contains("style.setProperty('--kanger-left'"));
        assertTrue(source.contains("event.stopImmediatePropagation()"));
        assertTrue(source.contains("setLeftWidth(next.clientX, true)"));
    }

    @Test
    void correctionAdapterRemainsInCanonicalBrowserLoadChain() throws Exception {
        String console = html("console.html");
        int bottom = console.indexOf("<script src=\"bottom-layout.js\"></script>");
        int correction = console.indexOf(
                "<script src=\"editor-local-file.js\"></script>");

        assertTrue(bottom >= 0, "Browser page must load the bottom layout");
        assertTrue(correction > bottom,
                "Browser page must load the correction adapter after the bottom layout");
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
