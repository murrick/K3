/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Path;
import java.util.UUID;
import org.kanger.*;

/** Read-only owning-provider pin and independent cold-cache evidence; not family or write admission. */
public final class SymbolicCacheStorageInspection {
    final DB owner;final Path location;final boolean standalone;
    public final UUID context;public final long revision;
    public final ContextDependentSymbolicCacheSnapshot.Snapshot working;
    public final String coldFingerprint;
    public final ContextDependentProductionReplay.Saved coldProof;
    SymbolicCacheStorageInspection(DB owner,Path location,UUID context,long revision,ContextDependentSymbolicCacheSnapshot.Snapshot working,ContextDependentSymbolicCacheSnapshot.Snapshot cold,boolean standalone){this.standalone=standalone;this.owner=owner;this.location=location;this.context=context;this.revision=revision;this.working=working;coldFingerprint=cold.inventory.fingerprint;coldProof=cold.evidence.proof;}
    public void requireCurrent(DB provider,Mind source)throws Exception{if(provider!=owner)throw new IllegalStateException("Symbolic cache storage inspection belongs to another provider");provider.requireSymbolicCacheInspectionCurrent(source,this);}
}
