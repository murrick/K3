/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.PendingRegistrationException;
import org.kanger.command.CommandParseException;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.DatabaseErrorException;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.ParametersIncompleteException;
import org.kanger.exception.ParseErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.exception.SourceSpan;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.exception.TransactionSettlementException;
import org.kanger.interfaces.IReactor;

/**
 * Converts classified KANGER failures into the canonical public error envelope
 * at one Server boundary.
 *
 * <p>Semantic/runtime code below this boundary is allowed to fail by throwing
 * its qualified exception. Legacy compatibility reactors may instead return a
 * classified safe error envelope; this boundary owns its final canonical
 * diagnostic projection as well. Browser and Console code must not need to
 * parse Java exception text to recover machine-readable meaning.</p>
 *
 * <p>For authenticated failures below {@link WorkspaceStateReactor}, the
 * workspace boundary captures the post-failure runtime state while the session
 * is still serialized. This outer boundary attaches that snapshot only after
 * classifying the original exception, preserving both single-point error
 * presentation and truthful workspace observability.</p>
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
            return canonicalizeReturnedError(delegate.run(packet));
        } catch (StorageLifecycleException failure) {
            boolean incompleteRemoval = failure.getErrorCode()
                    == org.kanger.enums.StorageLifecycleErrorCode.STORAGE_DELETE_INCOMPLETE;
            JSONObject result = error(
                    incompleteRemoval ? "operation" : "application",
                    failure.getCode(),
                    description(failure),
                    false,
                    "retain",
                    incompleteRemoval ? "unknown" : "confirmed");
            String action = failure.getRequiredAction();
            if (action != null && !action.isEmpty()) {
                result.put("required_action", action);
            }
            return withFailureWorkspace(packet, result);
        } catch (TransactionSettlementException failure) {
            String outcome = failure.getOutcome().name();
            JSONObject result = error(
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
                            .put("reservation_consumed", failure.isReservationConsumed()))
                    .put("required_action", "VERIFY_CURRENT_STATE");
            return withFailureWorkspace(packet, result);
        } catch (SourceImportException failure) {
            JSONObject result = error(
                    "operation",
                    "source_load_failed",
                    description(failure),
                    false,
                    "retain",
                    "not_applied")
                    .put("source_recovery", new JSONObject()
                            .put("schema", 1)
                            .put("logical_name", failure.getLogicalName())
                            .put("text", failure.getRecoverySource()));
            return withFailureWorkspace(packet, result);
        } catch (SourceDeleteException failure) {
            JSONObject result = error(
                    "operation",
                    "source_delete_failed",
                    description(failure),
                    false,
                    "retain",
                    "unknown")
                    .put("required_action", "VERIFY_CURRENT_STATE");
            return withFailureWorkspace(packet, result);
        } catch (StorageSwitchException failure) {
            JSONObject result = error(
                    "operation",
                    "storage_switch_failed",
                    description(failure),
                    false,
                    "retain",
                    "unknown")
                    .put("required_action", "VERIFY_CURRENT_STATE");
            return withFailureWorkspace(packet, result);
        } catch (AuthenticationErrorException failure) {
            return withFailureWorkspace(packet, error(
                    "session",
                    "authentication_error",
                    description(failure),
                    false,
                    "verify",
                    "unknown"));
        } catch (ConfirmationMailDeliveryException failure) {
            JSONObject result = error(
                    "account",
                    AccountErrorCode.MAIL_DELIVERY_UNAVAILABLE.code(),
                    description(failure),
                    true,
                    "retain",
                    "confirmed");
            if (!failure.getState().isEmpty()) {
                result.put("state", failure.getState());
            }
            if (!failure.getEmailHint().isEmpty()) {
                result.put("email_hint", failure.getEmailHint());
            }
            if (!failure.getPendingActionToken().isEmpty()) {
                result.put("pending_action_token", failure.getPendingActionToken());
            }
            return withFailureWorkspace(packet, result);
        } catch (PendingRegistrationException failure) {
            return withFailureWorkspace(packet, error(
                    "account",
                    failure.getCode().code(),
                    description(failure),
                    failure.getCode() == AccountErrorCode.RESEND_RATE_LIMITED,
                    "retain",
                    "not_applied"));
        } catch (CommandParseException failure) {
            return withFailureWorkspace(packet, error(
                    "application",
                    "command_parse_error",
                    description(failure),
                    false,
                    "retain",
                    "confirmed")
                    .put("reason", failure.getReason().name()));
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
            SourceSpan sourceSpan = failure.getSourceSpan();
            if (sourceSpan != null) {
                result.getJSONObject("error").put("source", new JSONObject()
                        .put("offset", sourceSpan.getOffset())
                        .put("length", sourceSpan.getLength()));
            }
            return withFailureWorkspace(packet, result);
        } catch (CommandErrorException failure) {
            return withFailureWorkspace(packet,
                    application("command_error", failure));
        } catch (DatabaseErrorException failure) {
            return withFailureWorkspace(packet,
                    application("database_error", failure));
        } catch (OutOfBufferException failure) {
            return withFailureWorkspace(packet,
                    application("out_of_buffer", failure));
        } catch (ParametersIncompleteException failure) {
            return withFailureWorkspace(packet,
                    application("parameters_incomplete", failure));
        } catch (RuntimeErrorException failure) {
            return withFailureWorkspace(packet,
                    application("runtime_error", failure));
        }
    }

    private static Object canonicalizeReturnedError(Object response) {
        if (!(response instanceof JSONObject)) {
            return response;
        }
        JSONObject result = (JSONObject) response;
        if (!"error".equalsIgnoreCase(result.optString("result", ""))
                || result.has("error")) {
            return result;
        }
        String code = result.optString("code", "");
        if ("storage_switch_failed".equals(code)
                || "storage_reindex_failed".equals(code)
                || "source_save_failed".equals(code)) {
            if ("source_save_failed".equals(code)) {
                result.put("description", "Source save failed");
            }
            result.put("error", diagnostic(
                    "operation",
                    code,
                    false,
                    "retain",
                    "unknown"));
            result.put("required_action", "VERIFY_CURRENT_STATE");
        }
        return result;
    }

    private static JSONObject withFailureWorkspace(JSONObject packet,
                                                   JSONObject result) {
        JSONObject workspace = WorkspaceStateReactor.takeFailureWorkspace(packet);
        if (workspace != null) {
            result.put("workspace", workspace);
        }
        return result;
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
        return new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description)
                .put("error", diagnostic(
                        domain,
                        code,
                        retryable,
                        sessionAction,
                        operationOutcome));
    }

    private static JSONObject diagnostic(String domain,
                                         String code,
                                         boolean retryable,
                                         String sessionAction,
                                         String operationOutcome) {
        return new JSONObject()
                .put("schema", ERROR_SCHEMA)
                .put("domain", domain)
                .put("code", code)
                .put("retryable", retryable)
                .put("session_action", sessionAction)
                .put("operation_outcome", operationOutcome);
    }

    private static String description(Throwable failure) {
        String message = failure.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return failure.toString();
    }
}
