/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.exception;
import org.kanger.ContextGroundClauseFamily;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Full pending ground-family diagnostic, never permission for a later write. */
public final class ContextGroundProductionCommitException extends StorageLifecycleException {
    private final ContextGroundClauseFamily.Evaluation evaluation;
    public ContextGroundProductionCommitException(String reason,ContextGroundClauseFamily.Evaluation evaluation) {
        super(evaluation!=null && evaluation.status==ContextGroundClauseFamily.Status.COLLISION
                ?StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT:StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,reason);
        this.evaluation=evaluation;
    }
    public ContextGroundClauseFamily.Evaluation getFamilyEvaluation() {return evaluation;}
}
