/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import org.kanger.*;
import org.kanger.exception.*;

/** Witness evaluator in the shared one-attempt native ownership scope. */
final class ContextPendingWitnessWrite extends ContextPendingNativeWrite {
    private final int rounds,depth,work,events;
    ContextPendingWitnessWrite(DB db,Mind source,int rows,int rounds,int depth,int work,int events) throws Exception {
        super(db,source,rows,"witness");
        if(rounds<1 || depth<0 || work<1 || events<1) throw new IllegalArgumentException("Bounded witness write required");
        this.rounds=rounds;this.depth=depth;this.work=work;this.events=events;
    }
    @Override void qualify(ContextPendingProductionSnapshotSession lease) throws Exception {
        ContextProductionFamilyAssembly.Evaluation result=lease.inspectWitness(rounds,depth,work,events);
        if(result.status!=ContextProductionFamilyAssembly.Status.FIXED_POINT)
            throw new ContextWitnessProductionCommitException("Pending witness family: "+result.reason,result);
    }
    @Override StorageLifecycleException incomplete(String reason) {return new ContextWitnessProductionCommitException(reason,null);}
}
