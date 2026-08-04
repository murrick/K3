/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

/**
 * Complete operator- or confirmation-authorized account creation request.
 * Password material is retained only for the duration of the synchronous
 * lifecycle call and is never written to the user profile.
 */
public final class ActiveAccountRequest {

    private final String login;
    private final String password;
    private final String email;
    private final String name;
    private final String country;
    private final String city;
    private final Boolean privacyConsent;

    public ActiveAccountRequest(String login, String password) {
        this(login, password, "", "", "", "", null);
    }

    public ActiveAccountRequest(String login,
                                String password,
                                String email,
                                String name,
                                String country,
                                String city,
                                Boolean privacyConsent) {
        this.login = required(login, "login").trim();
        this.password = required(password, "password");
        this.email = optional(email);
        this.name = optional(name);
        this.country = optional(country);
        this.city = optional(city);
        this.privacyConsent = privacyConsent;
        if (this.login.isEmpty()) {
            throw new IllegalArgumentException("login must not be empty");
        }
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
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

    private static String optional(String value) {
        return value == null ? "" : value;
    }
}
