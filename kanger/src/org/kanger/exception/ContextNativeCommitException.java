/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.exception;
import org.kanger.ContextNativePendingGuard;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Native pre-settlement refusal, distinct from the older UnitRounds FamilyAudit contract. */
public final class ContextNativeCommitException extends StorageLifecycleException {
    private final ContextNativePendingGuard.Evidence evidence;
    private final String phase;
    private ContextNativeCommitException(StorageLifecycleErrorCode code,String phase,String reason,ContextNativePendingGuard.Evidence evidence){super(code,reason);this.phase=phase;this.evidence=evidence;}
    public String getPhase(){return phase;}
    public ContextNativePendingGuard.Evidence getNativeEvidence(){return evidence;}
    public static ContextNativeCommitException incomplete(String phase,String reason){return new ContextNativeCommitException(StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,phase,reason,null);}
    /** Evidence has no public constructor and is only issued AFTER independent native replay. */
    public static ContextNativeCommitException conflict(ContextNativePendingGuard.Evidence evidence){if(evidence==null||evidence.getOppositions().isEmpty())throw new IllegalArgumentException("Sealed independently verified native refusal required");return new ContextNativeCommitException(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"native-refutation","Proposed Context has a sourced native refutation",evidence);}
}
