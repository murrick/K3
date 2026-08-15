/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandHelpRenderer;
import org.kanger.command.CommandIntent;
import org.kanger.command.CommandRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification contract for residual findings from the 3.7.0.7 VPS soak. */
class Soak3708ConvergenceContractTest {

    @Test
    void pluralRulesAliasIsDiscoverableWithoutChangingCanonicalFormatting()
            throws Exception {
        String help = new CommandHelpRenderer().render();
        assertTrue(help.contains("rule/rules family spellings are synonymous"),
                "Help must disclose the executable plural family spelling");
        assertEquals("rule all",
                CommandRegistry.definition(CommandIntent.RULE_ALL).getSyntax(),
                "The help alias must not replace canonical RULE_ALL formatting");
    }

    @Test
    void browserPreservesExactEditorViewAndReturnsHistoryToLatestActivity()
            throws Exception {
        String source = html("bottom-layout.js");

        assertTrue(source.contains("localSourceText = editorText()"));
        assertTrue(source.contains("window.showSourceEditor = function ()"));
        assertTrue(source.contains("window.openEditor(localSourceText)"));
        assertTrue(source.contains("localSourceSignature !== signature"));
        assertTrue(source.contains("history.scrollTop = history.scrollHeight"));
        assertFalse(source.contains("source + '\\n'"),
                "Browser convergence must not normalize compiler EOF");
    }

    @Test
    void commandInputClearControlIsOneCircularLocalPresentationAction()
            throws Exception {
        String source = html("bottom-layout.js");

        assertTrue(source.contains("button.id = 'kanger-query-clear'"));
        assertTrue(source.contains("button.textContent = '×'"));
        assertTrue(source.contains("button.style.borderRadius = '50%'"));
        assertTrue(source.contains("input.value = ''"));
        assertTrue(source.contains("input.focus()"));
    }

    @Test
    void obsoleteResendConfirmationActionIsHiddenForAllBrowserModes()
            throws Exception {
        String css = html("presentation.css");

        assertTrue(css.contains("#user-menu > [onclick=\"resendConfirmation()\"]"));
        assertTrue(css.contains("display: none !important"));
        assertFalse(css.contains("registration_policy")
                        && css.contains("resendConfirmation()"),
                "Menu removal must not be conditional on authentication mode");
    }

    @Test
    void leftSplitterDropsLegacyListenersAndKeepsOneGridAuthority()
            throws Exception {
        String source = html("bottom-layout.js");
        String css = html("presentation.css");

        assertTrue(source.contains("var handle = oldHandle.cloneNode(true)"));
        assertTrue(source.contains("handle.removeAttribute('onmousedown')"));
        assertTrue(source.contains("window.sizeX = null"));
        assertTrue(source.contains("style.setProperty('--kanger-left'"));
        assertTrue(source.contains("left.style.width = width"));
        assertTrue(source.contains("getPropertyValue('--kanger-left')"));
        assertFalse(source.contains("left.style.removeProperty('width')"));
        assertTrue(source.contains("right.style.removeProperty('width')"));
        assertTrue(source.contains("right.style.removeProperty('left')"));
        assertTrue(source.contains("next.stopImmediatePropagation()"));
        assertTrue(css.contains("#container-left.kanger-semantic"));
        assertTrue(css.contains("width: auto !important"),
                "The legacy inline width mirror must remain geometrically inert");
    }

    @Test
    void dialogueTitleCannotShrinkBelowSharedTitleHeight() throws Exception {
        String source = html("bottom-layout.js");

        assertTrue(source.contains("#container-console > div:first-child"));
        assertTrue(source.contains(
                "title.style.flex = '0 0 var(--kanger-title)'"));
        assertTrue(source.contains(
                "title.style.minHeight = 'var(--kanger-title)'"));
    }

    @Test
    void convergenceLayerRemainsInsideQualifiedBrowserInventory() throws Exception {
        String bottom = html("bottom-layout.js");
        assertTrue(bottom.contains("3.7.0.8 residual Browser-soak convergence"));
        assertTrue(bottom.contains("editor-local-file.js"));
        assertFalse(bottom.contains("browser-soak-convergence.js"),
                "Residual correction must not silently widen Browser inventory");
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
