/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import org.kanger.*;

/** Read-only one-member effective-view evidence; not a family certificate or publication authority. */
public final class SymbolicCacheConnectionInspection {
    final DB owner;final Mind source;final ContextConnection connection;
    final java.nio.file.Path sourceLocation;final ContextProductionInventory sourceInventory;
    public final UUID sourceContext,targetContext;public final long sourceRevision,targetRevision;
    public final List<String> initialization;
    public final ContextDependentSymbolicCacheSnapshot.Snapshot working;
    public final String coldFingerprint;
    public final ContextDependentProductionReplay.Saved coldProof;
    SymbolicCacheConnectionInspection(DB owner,Mind source,java.nio.file.Path sourceLocation,ContextProductionInventory sourceInventory,ContextConnection connection,ContextDependentSymbolicCacheSnapshot.Snapshot working,ContextDependentSymbolicCacheSnapshot.Snapshot cold){this.owner=owner;this.source=source;this.sourceLocation=sourceLocation;this.sourceInventory=sourceInventory;this.connection=connection;sourceContext=sourceInventory.context;sourceRevision=sourceInventory.revision;targetContext=connection.getTarget().getContextId();targetRevision=connection.getTarget().getRevision();initialization=Collections.unmodifiableList(new ArrayList<>(connection.getInitialization()));this.working=working;coldFingerprint=cold.inventory.fingerprint;coldProof=cold.evidence.proof;}
    public void requireCurrent(DB provider,Mind current)throws Exception{if(provider!=owner)throw new IllegalStateException("Symbolic connection inspection belongs to another provider");provider.requireSymbolicConnectionInspectionCurrent(current,this);}
}
