/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import org.kanger.*;
import org.kanger.exception.*;
final class ContextPendingAutomaticModelWrite extends ContextPendingNativeWrite {
 private final ContextSignedModelInspection.Automatic limits;
 ContextPendingAutomaticModelWrite(DB db,Mind source,ContextSignedModelInspection.Automatic limits)throws Exception{super(db,source,limits.model.nativeLimits.rows,"automatic signed unary model",true);this.limits=limits;}
 @Override void qualify(ContextPendingProductionSnapshotSession lease)throws Exception{ContextSignedModelGuard.Result r=lease.qualifyAutomaticSignedModel(limits);lease.requireCurrent();switch(r.status){case SCOPED_MODELLED_UNARY:return;case REFUTED:throw ContextSignedCommitException.conflict(r.evidence);default:throw ContextSignedCommitException.incomplete("auto-unary-model-"+r.phase,r.reason);}}
 @Override StorageLifecycleException incomplete(String reason){return ContextSignedCommitException.incomplete("auto-model-owning-attempt",reason);}
}
