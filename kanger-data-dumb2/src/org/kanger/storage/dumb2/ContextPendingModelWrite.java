/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import org.kanger.*;
import org.kanger.exception.*;

/** Explicit fixed-basis unary-model writer over unchanged native baseline/owning child guards. */
final class ContextPendingModelWrite extends ContextPendingNativeWrite {
 private final List<SemanticTermSnapshot> basis;private final ContextSignedModelInspection.Limits limits;
 ContextPendingModelWrite(DB db,Mind source,List<SemanticTermSnapshot> basis,ContextSignedModelInspection.Limits limits)throws Exception{super(db,source,limits.nativeLimits.rows,"signed unary model",true);this.basis=basis;this.limits=limits;}
 @Override void qualify(ContextPendingProductionSnapshotSession lease)throws Exception{
  ContextSignedModelGuard.Result r=lease.qualifySignedModel(basis,limits);lease.requireCurrent();
  switch(r.status){
   case SCOPED_MODELLED_UNARY:return;
   case REFUTED:throw ContextSignedCommitException.conflict(r.evidence);
   default:throw ContextSignedCommitException.incomplete("unary-model-"+r.phase,r.reason);
  }
 }
 @Override StorageLifecycleException incomplete(String reason){return ContextSignedCommitException.incomplete("model-owning-attempt",reason);}
}
