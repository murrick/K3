/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import org.kanger.*;
import org.kanger.exception.*;

/** Explicit owning native branch attempt; inherited lease guards persist through settlement. */
final class ContextPendingBranchWrite extends ContextPendingNativeWrite {
    private final ContextNativeCandidateInspection.Limits limits;
    private final int work,audit,models;
    ContextPendingBranchWrite(DB db,Mind source,ContextNativeCandidateInspection.Limits limits,int work,int audit,int models) throws Exception {
        super(db,source,limits.rows,"native branch",true);this.limits=limits;this.work=work;this.audit=audit;this.models=models;
    }
    @Override void qualify(ContextPendingProductionSnapshotSession lease) throws Exception {
        DB.requireNativePendingFamily(lease,limits,work,audit,models);
    }
    @Override StorageLifecycleException incomplete(String reason) {
        return ContextNativeCommitException.incomplete("owning-attempt",reason);
    }
}
