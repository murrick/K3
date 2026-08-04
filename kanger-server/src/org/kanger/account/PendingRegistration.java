/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.kanger.security.CredentialMaterial;

/**
 * Immutable transient registration intent. It has no user id, credential,
 * account home, runtime or session.
 */
public final class PendingRegistration {

    private final String id;
    private final String login;
    private final String email;
    private final CredentialMaterial credentialMaterial;
    private final String name;
    private final String country;
    private final String city;
    private final Boolean privacyConsent;
    private final long createdAt;
    private final long expiresAt;
    private final long confirmationExpiresAt;
    private final int resendCount;
    private final long lastResendAt;

    public PendingRegistration(String id,
                               String login,
                               String email,
                               CredentialMaterial credentialMaterial,
                               String name,
                               String country,
                               String city,
                               Boolean privacyConsent,
                               long createdAt,
                               long expiresAt,
                               long confirmationExpiresAt,
                               int resendCount,
                               long lastResendAt) {
        this.id = id;
        this.login = login;
        this.email = email;
        this.credentialMaterial = credentialMaterial;
        this.name = name;
        this.country = country;
        this.city = city;
        this.privacyConsent = privacyConsent;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.confirmationExpiresAt = confirmationExpiresAt;
        this.resendCount = resendCount;
        this.lastResendAt = lastResendAt;
    }

    public String getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getEmail() {
        return email;
    }

    public CredentialMaterial getCredentialMaterial() {
        return credentialMaterial;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public Boolean getPrivacyConsent() {
        return privacyConsent;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public long getConfirmationExpiresAt() {
        return confirmationExpiresAt;
    }

    public int getResendCount() {
        return resendCount;
    }

    public long getLastResendAt() {
        return lastResendAt;
    }
}
