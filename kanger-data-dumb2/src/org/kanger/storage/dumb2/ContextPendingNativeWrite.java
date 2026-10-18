/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import org.kanger.*;
import org.kanger.exception.StorageLifecycleException;

/** One provider-owned native attempt. Always requalifies actual pending X; no proof input. */
abstract class ContextPendingNativeWrite implements AutoCloseable {
    private final DB provider;
    private final Mind source;
    private final Thread owner=Thread.currentThread();
    private final int rows;
    private final String kind;
    private final ContextProductionSnapshotSession.Inventory baseline;
    private final List<ContextConnection> topology;
    private ContextPendingProductionSnapshotSession pending;
    private Mind proposed;
    private boolean closed,qualified,failed;
    ContextPendingNativeWrite(DB db,Mind source,int rows,String kind) throws Exception {
        provider=db;this.source=source;this.rows=rows;this.kind=kind;
        try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(db,source,rows)) {baseline=lease.inventory();}
        topology=Collections.unmodifiableList(new ArrayList<>(db.dmzTopology()));
    }
    final void validate(Mind child) throws Exception {
        if(closed || Thread.currentThread()!=owner || source.getUser().getCurrentMind()!=source || child==null || child.getNext()!=source)
            throw new IllegalStateException("Exact owning native write attempt required");
        try {
            source.requirePendingPhysicalChild(child);
            if(failed) throw new IllegalStateException("Refused native attempt cannot be retried");
            if(pending!=null) {
                if(!qualified) throw new IllegalStateException("Native attempt has no complete qualification");
                if(proposed!=child) throw new IllegalStateException("Native write attempt already bound to another child");
                pending.requireCurrent();return;
            }
            proposed=child;pending=ContextPendingProductionSnapshotSession.open(provider,child,rows);
            pending.requireBaseline(baseline,topology);
            qualify(pending);
            pending.requireCurrent();
            qualified=true;
        } catch(IllegalStateException unsupported) {
            failed=true;
            StorageLifecycleException failure=incomplete("Pending "+kind+" family incomplete: "+unsupported.getMessage());
            failure.initCause(unsupported);throw failure;
        } catch(Exception refusal) {failed=true;throw refusal;
        }
    }
    abstract void qualify(ContextPendingProductionSnapshotSession lease) throws Exception;
    abstract StorageLifecycleException incomplete(String reason);
    @Override public final void close() {closed=true;if(pending!=null) pending.close();}
}
