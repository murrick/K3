/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import java.nio.file.Path;

/**
 * Exact persisted identity of a canonical account workspace.
 */
public final class ActiveAccountIdentity {

    private final long userId;
    private final String login;
    private final String email;
    private final Path home;

    ActiveAccountIdentity(long userId, String login, String email, Path home) {
        if (userId <= 0L || login == null || login.trim().isEmpty() || home == null) {
            throw new IllegalArgumentException(
                    "user id, login and canonical home are required");
        }
        this.userId = userId;
        this.login = login.trim();
        this.email = email == null ? "" : email.trim();
        this.home = home.toAbsolutePath().normalize();
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

    public Path getHome() {
        return home;
    }
}
