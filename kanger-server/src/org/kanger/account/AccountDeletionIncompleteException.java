/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

/**
 * Safe deletion did not reach COMPLETE. The persistent record identifies the
 * exact recovery state and must be resumed rather than guessed or rolled back.
 */
public final class AccountDeletionIncompleteException extends Exception {

    private final AccountDeletion deletion;

    AccountDeletionIncompleteException(AccountDeletion deletion,
                                       Throwable cause) {
        super("Account deletion is incomplete at state "
                + (deletion == null ? "unknown" : deletion.getState()), cause);
        this.deletion = deletion;
    }

    public AccountDeletion getDeletion() {
        return deletion;
    }
}
