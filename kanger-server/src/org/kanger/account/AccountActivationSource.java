/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

/**
 * Authority that permits publication of a complete ACTIVE account.
 *
 * <p>Account activation and e-mail verification are distinct facts. A local
 * operator may activate an account without asserting anything about an
 * optional e-mail address; a confirmed pending registration carries explicit
 * verification proof.</p>
 */
public enum AccountActivationSource {

    /** Complete account provisioned by the local server operator. */
    LOCAL_OPERATOR(false),

    /** Complete account activated by successful e-mail confirmation. */
    EMAIL_CONFIRMATION(true);

    private final boolean emailVerified;

    AccountActivationSource(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }
}
