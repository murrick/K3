/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.kanger.security.CredentialMaterial;

/**
 * Complete operator- or confirmation-authorized account creation request.
 *
 * <p>An operator request may carry plaintext only for the duration of one
 * synchronous call. A confirmed PendingRegistration carries already-derived
 * {@link CredentialMaterial}; the original password is not retained or
 * recoverable. The activation source independently records whether an optional
 * e-mail address has actually been verified.</p>
 */
public final class ActiveAccountRequest {

    private final String login;
    private final String password;
    private final CredentialMaterial credentialMaterial;
    private final AccountActivationSource activationSource;
    private final String email;
    private final String name;
    private final String country;
    private final String city;
    private final Boolean privacyConsent;

    public ActiveAccountRequest(String login, String password) {
        this(login, password, null, AccountActivationSource.LOCAL_OPERATOR,
                "", "", "", "", null);
    }

    public ActiveAccountRequest(String login,
                                String password,
                                String email,
                                String name,
                                String country,
                                String city,
                                Boolean privacyConsent) {
        this(login, password, null, AccountActivationSource.LOCAL_OPERATOR,
                email, name, country, city, privacyConsent);
    }

    public ActiveAccountRequest(String login,
                                String password,
                                AccountActivationSource activationSource,
                                String email,
                                String name,
                                String country,
                                String city,
                                Boolean privacyConsent) {
        this(login, password, null, activationSource,
                email, name, country, city, privacyConsent);
    }

    public ActiveAccountRequest(String login,
                                CredentialMaterial credentialMaterial) {
        this(login, null, credentialMaterial,
                AccountActivationSource.EMAIL_CONFIRMATION,
                "", "", "", "", null);
    }

    public ActiveAccountRequest(String login,
                                CredentialMaterial credentialMaterial,
                                String email,
                                String name,
                                String country,
                                String city,
                                Boolean privacyConsent) {
        this(login, null, credentialMaterial,
                AccountActivationSource.EMAIL_CONFIRMATION,
                email, name, country, city, privacyConsent);
    }

    public ActiveAccountRequest(String login,
                                CredentialMaterial credentialMaterial,
                                AccountActivationSource activationSource,
                                String email,
                                String name,
                                String country,
                                String city,
                                Boolean privacyConsent) {
        this(login, null, credentialMaterial, activationSource,
                email, name, country, city, privacyConsent);
    }

    private ActiveAccountRequest(String login,
                                 String password,
                                 CredentialMaterial credentialMaterial,
                                 AccountActivationSource activationSource,
                                 String email,
                                 String name,
                                 String country,
                                 String city,
                                 Boolean privacyConsent) {
        this.login = required(login, "login").trim();
        this.password = password;
        this.credentialMaterial = credentialMaterial;
        this.activationSource = required(activationSource, "activation source");
        this.email = optional(email);
        this.name = optional(name);
        this.country = optional(country);
        this.city = optional(city);
        this.privacyConsent = privacyConsent;
        if (this.login.isEmpty()) {
            throw new IllegalArgumentException("login must not be empty");
        }
        if ((password == null || password.isEmpty()) == (credentialMaterial == null)) {
            throw new IllegalArgumentException(
                    "exactly one password source must be supplied");
        }
    }

    public String getLogin() {
        return login;
    }

    String getPassword() {
        return password;
    }

    CredentialMaterial getCredentialMaterial() {
        return credentialMaterial;
    }

    public boolean hasPreparedCredential() {
        return credentialMaterial != null;
    }

    public AccountActivationSource getActivationSource() {
        return activationSource;
    }

    public boolean isEmailVerified() {
        return activationSource.isEmailVerified();
    }

    public String getEmail() {
        return email;
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

    private static String required(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null ? "" : value;
    }
}
