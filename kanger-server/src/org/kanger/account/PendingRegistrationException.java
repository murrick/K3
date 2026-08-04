/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

/**
 * Structured pending-registration failure whose code is safe for public API
 * branching while the message remains diagnostic text.
 */
public final class PendingRegistrationException extends Exception {

    private final AccountErrorCode code;

    public PendingRegistrationException(AccountErrorCode code, String message) {
        super(message);
        if (code == null) {
            throw new IllegalArgumentException("account error code must not be null");
        }
        this.code = code;
    }

    public AccountErrorCode getCode() {
        return code;
    }
}
