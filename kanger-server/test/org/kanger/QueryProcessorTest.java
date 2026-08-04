/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryProcessorTest {

    @Test
    void legacyRootConfirmationBypassesAuthenticatedRequestGuard() {
        JSONObject confirmation = new JSONObject().put("confirm", "opaque-token");

        assertTrue(QueryProcessor.isLegacyRootConfirmation("", confirmation));
        assertFalse(QueryProcessor.isLegacyRootConfirmation("login", confirmation));
        assertFalse(QueryProcessor.isLegacyRootConfirmation("command", confirmation));
        assertFalse(QueryProcessor.isLegacyRootConfirmation(
                "", new JSONObject().put("confirm", "")));
        assertFalse(QueryProcessor.isLegacyRootConfirmation("", new JSONObject()));
    }
}
