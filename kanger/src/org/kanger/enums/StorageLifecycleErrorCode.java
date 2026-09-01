/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.enums;

/**
 * Stable machine-readable reasons for rejected storage lifecycle and
 * qualification operations.
 *
 * <p>The code identifies the violated invariant. {@link #getRequiredAction()}
 * optionally names the explicit caller action required before retrying the
 * operation.</p>
 */
public enum StorageLifecycleErrorCode {

    STORAGE_ALREADY_OPEN("EXPLICIT_CLOSE_REQUIRED"),
    ACTIVE_TRANSACTION("TRANSACTION_RESOLUTION_REQUIRED"),
    NO_STORAGE_OPEN(null),
    STORAGE_NOT_FOUND(null),
    STORAGE_SEMANTIC_CORRUPTION(null),
    STORAGE_DELETE_INCOMPLETE("VERIFY_CURRENT_STATE");

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
