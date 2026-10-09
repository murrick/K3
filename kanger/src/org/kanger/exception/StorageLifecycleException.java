/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.exception;

import org.kanger.ContextQualification;
import org.kanger.enums.StorageLifecycleErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final List<ContextQualification.CollisionWitness> collisions;

    public StorageLifecycleException(StorageLifecycleErrorCode code,
                                     String message) {
        this(code, message,
                Collections.<ContextQualification.CollisionWitness>emptyList());
    }

    public StorageLifecycleException(
            StorageLifecycleErrorCode code,
            String message,
            List<ContextQualification.CollisionWitness> collisions) {
        super(message);
        if (code == null) {
            throw new IllegalArgumentException("Storage lifecycle code is required");
        }
        if (collisions == null) {
            throw new NullPointerException("collisions");
        }
        this.code = code;
        this.collisions = Collections.unmodifiableList(
                new ArrayList<ContextQualification.CollisionWitness>(
                        collisions));
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

    public List<ContextQualification.CollisionWitness> getCollisions() {
        return collisions;
    }
}
