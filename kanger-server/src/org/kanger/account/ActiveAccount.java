/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import java.nio.file.Path;

/**
 * Immutable result of successful complete ACTIVE account publication.
 */
public final class ActiveAccount {

    private final long userId;
    private final String login;
    private final Path home;

    ActiveAccount(long userId, String login, Path home) {
        this.userId = userId;
        this.login = login;
        this.home = home.toAbsolutePath().normalize();
    }

    public long getUserId() {
        return userId;
    }

    public String getLogin() {
        return login;
    }

    public Path getHome() {
        return home;
    }
}
