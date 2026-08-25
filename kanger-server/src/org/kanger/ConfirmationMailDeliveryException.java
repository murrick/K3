/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Classified confirmation-mail queue failure after account state has already
 * been persisted. The exception carries only the public recovery fields that
 * must survive canonical error projection; the transport cause remains an
 * internal diagnostic detail.
 */
final class ConfirmationMailDeliveryException extends Exception {

    private final String state;
    private final String emailHint;
    private final String pendingActionToken;

    ConfirmationMailDeliveryException(String state,
                                      String emailHint,
                                      String pendingActionToken,
                                      Throwable cause) {
        super("Confirmation e-mail could not be queued", cause);
        this.state = value(state);
        this.emailHint = value(emailHint);
        this.pendingActionToken = value(pendingActionToken);
    }

    String getState() {
        return state;
    }

    String getEmailHint() {
        return emailHint;
    }

    String getPendingActionToken() {
        return pendingActionToken;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
