/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import org.kanger.*;
import org.kanger.exception.*;

/** Ground and universal engines keep their own exact limits and evidence types. */
final class ContextPendingClauseWrite extends ContextPendingNativeWrite {
    private final boolean universal;
    private final org.kanger.ContextOrdinaryResidualRounds.LedgerLimits ledgerLimits;
    private final int rounds,local,depth,instances,models,preparation,events,work,audit,probes;
    private ContextPendingClauseWrite(DB db,Mind source,boolean universal,int rows,int rounds,int local,int depth,
            int instances,int models,int preparation,int events,int work,int audit,int probes,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits limits) throws Exception {
        super(db,source,rows,universal?"universal":"ground");
        if(rounds<1 || local<1 || depth<0 || instances<1 || models<1 || preparation<1 || events<1 || work<1 || audit<1 || (!universal && probes<1))
            throw new IllegalArgumentException("Bounded native clause write required");
        if(limits==null) throw new IllegalArgumentException("Ledger limits required");
        this.ledgerLimits=limits;
        this.universal=universal;this.rounds=rounds;this.local=local;this.depth=depth;this.instances=instances;this.models=models;
        this.preparation=preparation;this.events=events;this.work=work;this.audit=audit;this.probes=probes;
    }
    static ContextPendingClauseWrite ground(DB db,Mind source,int rows,int rounds,int local,int depth,int work,int events,int probes,int models) throws Exception {
        return ground(db,source,rows,rounds,local,depth,work,events,probes,models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    static ContextPendingClauseWrite ground(DB db,Mind source,int rows,int rounds,int local,int depth,int work,int events,int probes,int models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits limits) throws Exception {
        return new ContextPendingClauseWrite(db,source,false,rows,rounds,local,depth,1,models,work,events,work,1,probes,limits);
    }
    static ContextPendingClauseWrite universal(DB db,Mind source,int rows,int rounds,int local,int depth,int instances,int models,int preparation,int events,int work,int audit) throws Exception {
        return new ContextPendingClauseWrite(db,source,true,rows,rounds,local,depth,instances,models,preparation,events,work,audit,0,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    @Override void qualify(ContextPendingProductionSnapshotSession lease) throws Exception {
        if(universal) DB.requirePendingUniversalResult(lease.inspect(rounds,local,depth,instances,models,preparation,events,work,audit));
        else {
            ContextGroundClauseFamily.Evaluation result=lease.inspectGround(rounds,local,depth,work,events,probes,models,ledgerLimits);
            if(result.status!=ContextGroundClauseFamily.Status.GROUND_FIXED_POINT)
                throw new ContextGroundProductionCommitException("Pending ground family: "+result.reason,result);
        }
    }
    @Override StorageLifecycleException incomplete(String reason) {
        return universal?ContextProductionCommitException.incomplete(reason,null):new ContextGroundProductionCommitException(reason,null);
    }
}
