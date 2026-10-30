/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.exception;
import org.kanger.ContextSignedPendingGuard;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Signed pre-settlement refusal, never a relabeled old native/UnitRounds family verdict. */
public final class ContextSignedCommitException extends StorageLifecycleException {
 private final ContextSignedPendingGuard.Evidence evidence;private final String phase;
 private ContextSignedCommitException(StorageLifecycleErrorCode code,String phase,String reason,ContextSignedPendingGuard.Evidence evidence){super(code,reason);this.phase=phase;this.evidence=evidence;}
 public String getPhase(){return phase;}
 public ContextSignedPendingGuard.Evidence getSignedEvidence(){return evidence;}
 public static ContextSignedCommitException incomplete(String phase,String reason){return new ContextSignedCommitException(StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,phase,reason,null);}
 /** Sealed immutable historical proof only; the owning DB must obtain it in a fresh pending lease. */
 public static ContextSignedCommitException conflict(ContextSignedPendingGuard.Evidence evidence){if(evidence==null||evidence.getOppositions().isEmpty())throw new IllegalArgumentException("Sealed independently audited signed refusal required");return new ContextSignedCommitException(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"signed-refutation","Proposed Context has an independently audited signed refutation",evidence);}
}
