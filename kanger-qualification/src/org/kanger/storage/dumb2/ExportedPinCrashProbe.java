/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.exception.StorageLifecycleException;

/** Only a marker-name persistence projection on disposable files; not physical power loss. */
public final class ExportedPinCrashProbe {
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]),producer=root.resolve("producer-db/Producer"),consumer=root.resolve("consumer-db/Consumer");
        boolean rollback=Boolean.parseBoolean(args[1]);
        long acknowledged,pinned;
        try(ContextSnapshot snapshot=ContextSnapshot.open(consumer)){
            acknowledged=snapshot.getRevision();ConnectionVector vector=ConnectionStore.read(consumer,new RevisionRef(snapshot.getContextId(),acknowledged));
            if(vector.size()!=1)throw new AssertionError("One exact saved dependency required");
            ContextConnection connection=vector.getConnections().get(0);pinned=connection.getTarget().getRevision();
            if(!producer.equals(connection.getTargetLocation()))throw new AssertionError("Unmodified exact locator required");
            try(ContextSnapshot target=ContextSnapshot.open(producer,pinned)){
                if(!target.getContextId().equals(connection.getTarget().getContextId()))throw new AssertionError("Target identity changed");
            }
        }
        if(rollback)Files.write(ContextStore.revisionPath(producer),Files.readAllBytes(root.resolve("previous-marker.bin")));
        long recovered=RevisionStore.read(ContextStore.revisionPath(producer));boolean available=true;
        try(ContextSnapshot target=ContextSnapshot.open(producer,pinned)){}catch(StorageLifecycleException failure){
            if(!"STORAGE_NOT_FOUND".equals(failure.getCode()))throw failure;available=false;
        }
        try(ContextSnapshot snapshot=ContextSnapshot.open(consumer)){
            if(snapshot.getRevision()!=acknowledged)throw new AssertionError("Consumer publication changed in projection");
        }
        System.out.println("EXPORTED_PIN_MODEL consumer="+acknowledged+" pinned="+pinned+" producer="+recovered+" readable="+available);
    }
}
