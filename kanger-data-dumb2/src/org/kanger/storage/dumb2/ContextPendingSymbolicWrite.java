/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import org.kanger.*;
import org.kanger.exception.StorageLifecycleException;

/** One explicit original append; the owning native attempt retains rollback authority. */
final class ContextPendingSymbolicWrite extends ContextPendingNativeWrite {
    private final DB provider;private final String statement;private int inspectedGeneratedRows=-1;
    ContextPendingSymbolicWrite(DB provider,Mind source,String statement)throws Exception{super(provider,source,1000,"symbolic original append",true);this.provider=provider;this.statement=statement;}
    @Override void qualify(ContextPendingProductionSnapshotSession lease)throws Exception{inspectedGeneratedRows=lease.requireSymbolicFamily(statement);}
    int inspectedGeneratedRows(){return inspectedGeneratedRows;}
    @Override StorageLifecycleException incomplete(String reason){try{return ContextDependentFamilyOutcome.incomplete(reason,provider.symbolicFamilyNames());}catch(Exception unavailable){return ContextDependentFamilyOutcome.incomplete(reason,java.util.Collections.emptyMap());}}
}
