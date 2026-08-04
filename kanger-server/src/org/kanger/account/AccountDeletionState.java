/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

/**
 * Forward-only safe account deletion states.
 */
public enum AccountDeletionState {
    PREPARED,
    CREDENTIAL_REMOVED,
    HOME_QUARANTINED,
    COMPLETE;

    boolean canAdvanceTo(AccountDeletionState next) {
        return next != null && next.ordinal() >= ordinal();
    }
}
