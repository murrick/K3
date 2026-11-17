/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import org.kanger.exception.StorageLifecycleException;

/** Read a disposable projected image with the real backend; never a physical power-cut claim. */
public final class ContextMarkerCrashImageProbe {
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("ack-image, previous marker, fresh projection, rollback flag required");
        Path source=Paths.get(args[0]).resolve("Source"),previous=Paths.get(args[1]),projection=Paths.get(args[2]);
        long acknowledged=RevisionStore.read(ContextStore.revisionPath(source));
        long before=RevisionStore.read(previous);
        if(before+1!=acknowledged)throw new AssertionError("Exact predecessor witness required");
        ContextPublicationDurabilityTraceRunner.copyOwnContext(source,projection);
        Path selected=projection.resolve("Source");
        if(Boolean.parseBoolean(args[3]))Files.write(ContextStore.revisionPath(selected),Files.readAllBytes(previous));
        long observed;java.util.UUID owner;
        try(ContextSnapshot original=ContextSnapshot.open(source);ContextSnapshot recovered=ContextSnapshot.open(selected)){
            owner=original.getContextId();observed=recovered.getRevision();
            if(!owner.equals(recovered.getContextId()))throw new AssertionError("Projection changed Context identity");
        }
        boolean readable=true;
        try(ContextSnapshot exact=ContextSnapshot.open(selected,acknowledged)){
            if(!owner.equals(exact.getContextId()))throw new AssertionError("Wrong exact Context identity");
        }catch(StorageLifecycleException unavailable){
            if(!"STORAGE_NOT_FOUND".equals(unavailable.getCode()))throw unavailable;
            readable=false;
        }
        if(observed!=(Boolean.parseBoolean(args[3])?before:acknowledged))throw new AssertionError("Unexpected projected marker");
        System.out.println("MARKER_MODEL_PROBE previous="+before+" acknowledged="+acknowledged+" observed="+observed+" acknowledged_readable="+readable);
    }
}
