/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.enums;

/**
 * Stable machine-readable reasons for rejected physical-storage lifecycle
 * operations.
 *
 * <p>The code identifies the violated invariant. {@link #getRequiredAction()}
 * optionally names the explicit caller action required before retrying the
 * operation.</p>
 */
public enum StorageLifecycleErrorCode {

    STORAGE_ALREADY_OPEN("EXPLICIT_CLOSE_REQUIRED"),
    ACTIVE_TRANSACTION("TRANSACTION_RESOLUTION_REQUIRED"),
    NO_STORAGE_OPEN(null);

    private final String requiredAction;

    StorageLifecycleErrorCode(String requiredAction) {
        this.requiredAction = requiredAction;
    }

    /**
     * @return stable action identifier, or {@code null} when no recovery action
     * is defined by the lifecycle contract
     */
    public String getRequiredAction() {
        return requiredAction;
    }
}
