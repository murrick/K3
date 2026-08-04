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
    void authenticationGatewayProjectsServerPolicyWithoutLegacySessionAssumptions()
            throws Exception {
        String index = read(Paths.get("..", "html", "index.html"));
        String config = read(Paths.get("..", "html", "config.js"));
        String console = read(Paths.get("..", "html", "console.html"));

        assertTrue(index.contains("registration_policy"));
        assertTrue(index.contains("public_registration"));
        assertTrue(index.contains("confirmation_creates_session"));
        assertTrue(index.contains("EMAIL_CONFIRMATION_REQUIRED"));
        assertTrue(index.contains("pending_action_token"));
        assertTrue(index.contains("change_pending_email"));
        assertTrue(index.contains("cancel_pending"));
        assertTrue(index.contains("sessionStorage"));
        assertTrue(index.contains("textContent"));
        assertTrue(index.contains("console.html"));
        assertTrue(index.contains("E-mail confirmed. Sign in to create a session."));
        assertFalse(index.contains("?confirm="));
        assertFalse(index.contains("innerHTML"));

        assertTrue(config.contains("http://localhost:1964"));
        assertTrue(config.contains("https://api.kanger.org"));

        assertTrue(console.contains("function registerForm"));
        assertTrue(console.contains("function command("));
        assertTrue(console.contains("window.apihost = \"http://localhost:1964\";"));
    }

    private static String read(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "Missing UI file: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
