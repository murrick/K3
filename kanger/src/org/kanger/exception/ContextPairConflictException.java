/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.exception;

import java.util.List;
import java.util.Objects;
import org.kanger.ContextQualification.CollisionWitness;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.interfaces.IContextResults.Revision;

/** Identifies the two views checked by pair qualification, including a pending source edit. */
public final class ContextPairConflictException extends StorageLifecycleException {
    private final Revision sourceBase;
    private final Revision targetPin;

    public ContextPairConflictException(String sourceName, Revision sourceBase,
            String targetName, Revision targetPin, List<CollisionWitness> collisions) {
        super(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                "Proposed Context pair is not compatible: " + sourceName + "@" + sourceBase.getRevision()
                        + " / " + targetName + "@" + targetPin.getRevision(), collisions);
        this.sourceBase = Objects.requireNonNull(sourceBase);
        this.targetPin = Objects.requireNonNull(targetPin);
    }

    /** Published base of the proposed Source view. The pending edit is outside that revision. */
    public Revision getSourceBase() {
        return sourceBase;
    }

    public Revision getTargetPin() {
        return targetPin;
    }
}
