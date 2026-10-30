/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import org.kanger.*;
import org.kanger.exception.*;

/** Explicit signed attempt over unchanged complete native baseline/owning child guards. */
final class ContextPendingSignedWrite extends ContextPendingNativeWrite {
 private final ContextSignedCandidateInspection.Limits limits;private final int work,audit,models;
 ContextPendingSignedWrite(DB db,Mind source,ContextSignedCandidateInspection.Limits limits,int work,int audit,int models)throws Exception{super(db,source,limits.rows,"signed branch",true);this.limits=limits;this.work=work;this.audit=audit;this.models=models;}
 @Override void qualify(ContextPendingProductionSnapshotSession lease)throws Exception{DB.requireSignedPendingFamily(lease,limits,work,audit,models);}
 @Override StorageLifecycleException incomplete(String reason){return ContextSignedCommitException.incomplete("owning-attempt",reason);}
}
