/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.kanger.command.CommandParseException;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.DatabaseErrorException;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.ParametersIncompleteException;
import org.kanger.exception.ParseErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.exception.TransactionSettlementException;
import org.kanger.interfaces.IReactor;

/**
 * Converts classified KANGER exceptions into the canonical public error
 * envelope at one Server boundary.
 *
 * <p>Semantic/runtime code below this boundary is allowed to fail by throwing
 * its qualified exception. This reactor owns protocol-neutral classification
 * into the Server error payload; Browser and Console code must not need to
 * parse Java exception text to recover machine-readable meaning.</p>
 *
 * <p>Unclassified exceptions deliberately escape this boundary. The HTTP
 * transport must keep treating an unknown server failure as HTTP 500 rather
 * than disguising a programming or infrastructure defect as a successful
 * application response.</p>
 */
final class CanonicalErrorBoundaryReactor implements IReactor<JSONObject> {

    private static final int ERROR_SCHEMA = 1;

    private final IReactor<JSONObject> delegate;

    CanonicalErrorBoundaryReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        try {
            return delegate.run(packet);
        } catch (StorageLifecycleException failure) {
            JSONObject result = error(
                    "application",
                    failure.getCode(),
                    description(failure),
                    false,
                    "retain",
                    "confirmed");
            String action = failure.getRequiredAction();
            if (action != null && !action.isEmpty()) {
                result.put("required_action", action);
            }
            return result;
        } catch (TransactionSettlementException failure) {
            String outcome = failure.getOutcome().name();
            return error(
                    "operation",
                    "transaction_settlement_failed",
                    description(failure),
                    false,
                    "retain",
                    failure.isSemanticApplied() ? "confirmed" : "not_applied")
                    .put("settlement", new JSONObject()
                            .put("schema", 1)
                            .put("outcome", outcome)
                            .put("semantic_applied", failure.isSemanticApplied())
                            .put("reservation_consumed", failure.isReservationConsumed()));
        } catch (AuthenticationErrorException failure) {
            return error(
                    "session",
                    "authentication_error",
                    description(failure),
                    false,
                    "verify",
                    "unknown");
        } catch (CommandParseException failure) {
            return error(
                    "application",
                    "command_parse_error",
                    description(failure),
                    false,
                    "retain",
                    "confirmed")
                    .put("reason", failure.getReason().name());
        } catch (ParseErrorException failure) {
            JSONObject result = error(
                    "application",
                    "parse_error",
                    description(failure),
                    false,
                    "retain",
                    "confirmed");
            if (failure.getCode() != null) {
                result.put("reason", failure.getCode().name());
            }
            return result;
        } catch (CommandErrorException failure) {
            return application("command_error", failure);
        } catch (DatabaseErrorException failure) {
            return application("database_error", failure);
        } catch (OutOfBufferException failure) {
            return application("out_of_buffer", failure);
        } catch (ParametersIncompleteException failure) {
            return application("parameters_incomplete", failure);
        } catch (RuntimeErrorException failure) {
            return application("runtime_error", failure);
        }
    }

    private static JSONObject application(String code, Exception failure) {
        return error(
                "application",
                code,
                description(failure),
                false,
                "retain",
                "confirmed");
    }

    private static JSONObject error(String domain,
                                    String code,
                                    String description,
                                    boolean retryable,
                                    String sessionAction,
                                    String operationOutcome) {
        JSONObject diagnostic = new JSONObject()
                .put("schema", ERROR_SCHEMA)
                .put("domain", domain)
                .put("code", code)
                .put("retryable", retryable)
                .put("session_action", sessionAction)
                .put("operation_outcome", operationOutcome);
        return new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description)
                .put("error", diagnostic);
    }

    private static String description(Throwable failure) {
        String message = failure.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return failure.toString();
    }
}
