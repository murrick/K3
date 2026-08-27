/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

/**
 * Stable machine-readable account and registration failure codes.
 *
 * <p>Human-readable descriptions may evolve. Public clients must branch on
 * these codes rather than parse descriptions.</p>
 */
public enum AccountErrorCode {
    REGISTRATION_DISABLED,
    PRIVACY_CONSENT_REQUIRED,
    AUTHENTICATION_FAILED,
    EMAIL_CONFIRMATION_REQUIRED,
    CONFIRMATION_TOKEN_INVALID,
    CONFIRMATION_TOKEN_EXPIRED,
    EMAIL_ALREADY_USED,
    VERIFIED_EMAIL_IMMUTABLE,
    ACCOUNT_LOGIN_IMMUTABLE,
    LOGIN_ALREADY_USED,
    RESEND_RATE_LIMITED,
    MAIL_DELIVERY_UNAVAILABLE;

    public String code() {
        return name();
    }
}
