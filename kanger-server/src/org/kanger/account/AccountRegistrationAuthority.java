/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

/**
 * Shared JVM authority for operations that change registration/account
 * identity across pending state, credentials and account workspaces.
 *
 * <p>All callers acquire this authority before the credential authority and
 * before a user runtime lock. Keeping one explicit order prevents confirmation,
 * operator deletion and registration from deadlocking or observing split
 * identity state.</p>
 */
final class AccountRegistrationAuthority {

    interface Work<T> {
        T run() throws Exception;
    }

    private static final Object MONITOR = new Object();

    private AccountRegistrationAuthority() {
    }

    static <T> T execute(Work<T> work) throws Exception {
        if (work == null) {
            throw new IllegalArgumentException("registration authority work must not be null");
        }
        synchronized (MONITOR) {
            return work.run();
        }
    }
}
