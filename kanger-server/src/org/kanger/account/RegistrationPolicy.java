/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import java.util.Locale;

/**
 * Account-registration topology derived from the configured e-mail transport
 * mode.
 *
 * <p>The transport value is interpreted once at the configuration boundary.
 * Account lifecycle code must depend on this policy rather than scatter string
 * comparisons for {@code disabled}, {@code starttls} and {@code smtps}.</p>
 */
public enum RegistrationPolicy {

    /**
     * Public self-registration is disabled. Complete ACTIVE accounts are
     * provisioned through the local operator plane.
     */
    TRUSTED(false, false),

    /**
     * Public self-registration creates a pending registration and requires
     * successful e-mail confirmation before an ACTIVE account exists.
     */
    EMAIL_VERIFIED(true, true);

    private final boolean publicSelfRegistration;
    private final boolean emailConfirmationRequired;

    RegistrationPolicy(boolean publicSelfRegistration,
                       boolean emailConfirmationRequired) {
        this.publicSelfRegistration = publicSelfRegistration;
        this.emailConfirmationRequired = emailConfirmationRequired;
    }

    /**
     * Resolves the configured {@code server.email.mode} value.
     *
     * @param emailMode configured transport mode
     * @return the account registration policy
     * @throws IllegalArgumentException when the value is absent or unsupported
     */
    public static RegistrationPolicy fromEmailMode(String emailMode) {
        if (emailMode == null) {
            throw new IllegalArgumentException("server.email.mode must not be null");
        }

        String normalized = emailMode.trim().toLowerCase(Locale.ROOT);
        if ("disabled".equals(normalized)) {
            return TRUSTED;
        }
        if ("starttls".equals(normalized) || "smtps".equals(normalized)) {
            return EMAIL_VERIFIED;
        }
        throw new IllegalArgumentException(
                "Unsupported server.email.mode: " + emailMode);
    }

    public boolean allowsPublicSelfRegistration() {
        return publicSelfRegistration;
    }

    public boolean requiresEmailConfirmation() {
        return emailConfirmationRequired;
    }
}
