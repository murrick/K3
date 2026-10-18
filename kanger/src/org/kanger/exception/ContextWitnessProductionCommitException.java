/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.exception;
import org.kanger.ContextProductionFamilyAssembly;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Native witness-family refusal; evidence is diagnostic, not a future write permit. */
public final class ContextWitnessProductionCommitException extends StorageLifecycleException {
    private final ContextProductionFamilyAssembly.Evaluation evaluation;
    public ContextWitnessProductionCommitException(String reason,ContextProductionFamilyAssembly.Evaluation evaluation) {
        super(evaluation!=null && evaluation.status==ContextProductionFamilyAssembly.Status.COLLISION
                ?StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT:StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,reason);
        this.evaluation=evaluation;
    }
    public ContextProductionFamilyAssembly.Evaluation getFamilyEvaluation() {return evaluation;}
}
