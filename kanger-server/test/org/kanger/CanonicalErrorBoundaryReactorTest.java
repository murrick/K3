/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.exception.TransactionSettlementException;
import org.kanger.interfaces.IReactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalErrorBoundaryReactorTest {

    @Test
    void storageFailureCarriesMachineReadableRecoveryWithoutParsingMessage() throws Exception {
        CanonicalErrorBoundaryReactor boundary = new CanonicalErrorBoundaryReactor(
                throwing(new StorageLifecycleException(
                        StorageLifecycleErrorCode.ACTIVE_TRANSACTION,
                        "Storage switch rejected")));

        JSONObject response = response(boundary);
        assertEquals("error", response.getString("result"));
        assertEquals("ACTIVE_TRANSACTION", response.getString("code"));
        assertEquals("TRANSACTION_RESOLUTION_REQUIRED",
                response.getString("required_action"));

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("application", diagnostic.getString("domain"));
        assertEquals("ACTIVE_TRANSACTION", diagnostic.getString("code"));
        assertFalse(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("confirmed", diagnostic.getString("operation_outcome"));
    }

    @Test
    void authenticationFailureIsClassifiedAsSessionFailure() throws Exception {
        CanonicalErrorBoundaryReactor boundary = new CanonicalErrorBoundaryReactor(
                throwing(new AuthenticationErrorException("Bad credentials")));

        JSONObject response = response(boundary);
        assertEquals("authentication_error", response.getString("code"));
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals("session", diagnostic.getString("domain"));
        assertEquals("verify", diagnostic.getString("session_action"));
        assertEquals("unknown", diagnostic.getString("operation_outcome"));
    }

    @Test
    void committedSettlementFailureDoesNotLieAboutSemanticOutcome() throws Exception {
        CanonicalErrorBoundaryReactor boundary = new CanonicalErrorBoundaryReactor(
                throwing(new TransactionSettlementException(
                        TransactionSettlementException.Outcome.COMMITTED,
                        new IllegalStateException("flush failed"))));

        JSONObject response = response(boundary);
        assertEquals("transaction_settlement_failed", response.getString("code"));
        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals("operation", diagnostic.getString("domain"));
        assertEquals("confirmed", diagnostic.getString("operation_outcome"));

        JSONObject settlement = response.getJSONObject("settlement");
        assertEquals("COMMITTED", settlement.getString("outcome"));
        assertTrue(settlement.getBoolean("semantic_applied"));
        assertTrue(settlement.getBoolean("reservation_consumed"));
    }

    @Test
    void unknownExceptionEscapesForHttp500Handling() {
        IllegalStateException failure = new IllegalStateException("programming defect");
        CanonicalErrorBoundaryReactor boundary = new CanonicalErrorBoundaryReactor(
                throwing(failure));

        IllegalStateException observed = assertThrows(
                IllegalStateException.class,
                () -> boundary.run(new JSONObject()));
        assertTrue(observed == failure,
                "Canonical boundary must not reclassify unknown server defects");
    }

    private JSONObject response(CanonicalErrorBoundaryReactor boundary) throws Exception {
        Object response = boundary.run(new JSONObject());
        assertTrue(response instanceof JSONObject);
        return (JSONObject) response;
    }

    private IReactor<JSONObject> throwing(final Exception failure) {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) throws Exception {
                throw failure;
            }
        };
    }
}
