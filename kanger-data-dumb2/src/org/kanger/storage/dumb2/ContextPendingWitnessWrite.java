/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import org.kanger.*;
import org.kanger.exception.ContextWitnessProductionCommitException;

/** One provider-owned native attempt. Always requalifies actual pending X; no proof input. */
final class ContextPendingWitnessWrite implements AutoCloseable {
    private final DB provider;
    private final Mind source;
    private final Thread owner=Thread.currentThread();
    private final int rows,rounds,depth,work,events;
    private final ContextProductionSnapshotSession.Inventory baseline;
    private final List<ContextConnection> topology;
    private ContextPendingProductionSnapshotSession pending;
    private Mind proposed;
    private boolean closed,qualified,failed;
    ContextPendingWitnessWrite(DB db,Mind source,int rows,int rounds,int depth,int work,int events) throws Exception {
        if(rounds<1 || depth<0 || work<1 || events<1) throw new IllegalArgumentException("Bounded witness write required");
        provider=db;this.source=source;this.rows=rows;this.rounds=rounds;this.depth=depth;this.work=work;this.events=events;
        try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(db,source,rows)) {baseline=lease.inventory();}
        topology=Collections.unmodifiableList(new ArrayList<>(db.dmzTopology()));
    }
    void validate(Mind child) throws Exception {
        if(closed || Thread.currentThread()!=owner || source.getUser().getCurrentMind()!=source || child==null || child.getNext()!=source)
            throw new IllegalStateException("Exact owning witness write attempt required");
        try {
            source.requirePendingPhysicalChild(child);
            if(failed) throw new IllegalStateException("Refused witness attempt cannot be retried");
            if(pending!=null) {
                if(!qualified) throw new IllegalStateException("Witness attempt has no complete qualification");
                if(proposed!=child) throw new IllegalStateException("Witness write attempt already bound to another child");
                pending.requireCurrent();return;
            }
            proposed=child;pending=ContextPendingProductionSnapshotSession.open(provider,child,rows);
            pending.requireBaseline(baseline,topology);
            ContextProductionFamilyAssembly.Evaluation result=pending.inspectWitness(rounds,depth,work,events);
            pending.requireCurrent();
            if(result.status!=ContextProductionFamilyAssembly.Status.FIXED_POINT)
                throw new ContextWitnessProductionCommitException("Pending witness family: "+result.reason,result);
            qualified=true;
        } catch(IllegalStateException unsupported) {
            failed=true;
            ContextWitnessProductionCommitException failure=new ContextWitnessProductionCommitException("Pending witness family incomplete: "+unsupported.getMessage(),null);
            failure.initCause(unsupported);throw failure;
        } catch(Exception refusal) {failed=true;throw refusal;
        }
    }
    @Override public void close() {closed=true;if(pending!=null) pending.close();}
}
