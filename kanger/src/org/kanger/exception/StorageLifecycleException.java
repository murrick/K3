/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.exception;

import org.kanger.enums.StorageLifecycleErrorCode;

/**
 * Rejection of a physical-storage lifecycle operation by the Core contract.
 *
 * <p>The exception carries a stable machine-readable reason independently of
 * any server, JSON or HTTP protocol. Protocol adapters may expose
 * {@link #getCode()} and {@link #getRequiredAction()} without parsing the
 * human-readable message.</p>
 */
public class StorageLifecycleException extends RuntimeErrorException {

    private final StorageLifecycleErrorCode code;

    public StorageLifecycleException(StorageLifecycleErrorCode code,
                                     String message) {
        super(message);
        if (code == null) {
            throw new IllegalArgumentException("Storage lifecycle code is required");
        }
        this.code = code;
    }

    public StorageLifecycleErrorCode getErrorCode() {
        return code;
    }

    public String getCode() {
        return code.name();
    }

    public String getRequiredAction() {
        return code.getRequiredAction();
    }
}
