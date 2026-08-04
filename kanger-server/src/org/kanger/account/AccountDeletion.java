/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import java.nio.file.Path;

/**
 * Immutable persistent account deletion state.
 */
public final class AccountDeletion {

    private final String id;
    private final long userId;
    private final String login;
    private final String email;
    private final Path canonicalHome;
    private final Path quarantineHome;
    private final AccountDeletionState state;
    private final long createdAt;
    private final long updatedAt;
    private final String diagnostic;

    AccountDeletion(String id,
                    long userId,
                    String login,
                    String email,
                    Path canonicalHome,
                    Path quarantineHome,
                    AccountDeletionState state,
                    long createdAt,
                    long updatedAt,
                    String diagnostic) {
        this.id = id;
        this.userId = userId;
        this.login = login;
        this.email = email;
        this.canonicalHome = canonicalHome.toAbsolutePath().normalize();
        this.quarantineHome = quarantineHome.toAbsolutePath().normalize();
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public String getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getLogin() {
        return login;
    }

    public String getEmail() {
        return email;
    }

    public Path getCanonicalHome() {
        return canonicalHome;
    }

    public Path getQuarantineHome() {
        return quarantineHome;
    }

    public AccountDeletionState getState() {
        return state;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public boolean isComplete() {
        return state == AccountDeletionState.COMPLETE;
    }
}
