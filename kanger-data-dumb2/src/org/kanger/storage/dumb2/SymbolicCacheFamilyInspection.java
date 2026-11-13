/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import org.kanger.*;

/** Owning read-only receiving-round/model result. No topology, query, publication or branch completeness authority. */
public final class SymbolicCacheFamilyInspection {
    final DB owner;
    public final SymbolicCacheStorageInspection local;
    public final List<SymbolicCacheConnectionInspection> members;
    public final ContextDependentModelSearchInspection.Result model;
    SymbolicCacheFamilyInspection(DB owner,SymbolicCacheStorageInspection local,List<SymbolicCacheConnectionInspection> members,ContextDependentModelSearchInspection.Result model){this.owner=owner;this.local=local;this.members=Collections.unmodifiableList(new ArrayList<>(members));this.model=model;}
    /** Current model precondition only; explicit checked publication still acquires a fresh family result. */
    public void requireCurrentModelled(DB provider,Mind source)throws Exception{if(provider!=owner)throw org.kanger.ContextDependentFamilyOutcome.incomplete("Family result belongs to another provider",java.util.Collections.emptyMap());provider.requireCurrentSymbolicFamilyModel(source,this);}
    public void requireCurrent(DB provider,Mind source)throws Exception{if(provider!=owner)throw new IllegalStateException("Symbolic family inspection belongs to another provider");provider.requireSymbolicFamilyInspectionCurrent(source,this);}
}
