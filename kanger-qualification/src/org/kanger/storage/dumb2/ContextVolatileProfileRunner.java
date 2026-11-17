/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.Version;
import org.kanger.storage.Step;
import org.kanger.exception.StorageLifecycleException;

/** Actual disposable tmpfs fixture; does not reboot, remount or remove any existing data. */
public final class ContextVolatileProfileRunner {
    private static void legacyChecks(Path ram,Path disk)throws Exception{
        Files.createDirectory(disk);Path original=disk.resolve("Original"),consumer=disk.resolve("Consumer");
        try(ContextStore source=ContextStore.create(original)){
            Step row=new Step();row.setId(0);row.setHash(19);row.setData(1L);source.getBase("index").add(row);source.flush();
        }
        Path imported=ram.resolve("legacy");ContextPublicationDurabilityTraceRunner.copyOwnContext(original,imported);
        Path legacy=imported.resolve("Original");
        byte[] marker=Files.readAllBytes(ContextStore.revisionPath(legacy));
        try(ContextSnapshot snapshot=ContextSnapshot.open(legacy,1)){
            if(!Long.valueOf(1).equals(snapshot.getBase("index").get(0).getData()))throw new AssertionError("Read-only legacy snapshot must remain available");
        }
        boolean refused=false;
        try(ContextStore ignored=ContextStore.open(legacy)){}catch(StorageLifecycleException failure){refused="STORAGE_DURABILITY_UNSUPPORTED".equals(failure.getCode());}
        if(!refused)throw new AssertionError("Mutable legacy open must refuse volatile medium");
        try(ContextStore owner=ContextStore.create(consumer);ContextSnapshot snapshot=ContextSnapshot.open(legacy,1)){
            Step row=new Step();row.setId(0);row.setHash(21);row.setData(2L);owner.getBase("index").add(row);owner.flush();
            RevisionRef source=new RevisionRef(owner.getContextId(),1),target=new RevisionRef(snapshot.getContextId(),1);
            ConnectionVector desired=new ConnectionVector(Arrays.asList(new ContextConnection(legacy,target,new CompatibilityCertificate(source,target,Version.CORE_VERSION_S))));
            refused=false;
            try{owner.publishTopology(desired,"volatile target");}catch(StorageLifecycleException failure){
                refused="STORAGE_DURABILITY_UNSUPPORTED".equals(failure.getCode())&&"SELECT_SUPPORTED_DURABILITY_STORAGE".equals(failure.getRequiredAction());
            }
            if(!refused||owner.getRevision()!=1||RevisionStore.read(ContextStore.revisionPath(consumer))!=1||owner.hasUnconfirmedPublication())
                throw new AssertionError("Unsupported target must refuse before the consumer marker");
            if(!ConnectionStore.read(consumer,source).isEmpty())throw new AssertionError("Refused target leaked into published vector");
            try(ContextStore importedFork=ContextStore.fork(snapshot,disk.resolve("Imported"))){
                if(importedFork.getRevision()!=1||!Long.valueOf(1).equals(importedFork.getBase("index").get(0).getData()))
                    throw new AssertionError("Self-contained explicit import to supported storage must work");
            }
        }
        if(!Arrays.equals(marker,Files.readAllBytes(ContextStore.revisionPath(legacy))))throw new AssertionError("Legacy target marker changed");
        System.out.println("VOLATILE_LEGACY_CHECKS_PASS readonly=true mutable_refused=true pin_refused=true marker_unchanged=true import=true");
    }
    public static void main(String[] args)throws Exception{
        Path parent=Paths.get(args[0]);
        if(!"tmpfs".equals(Files.getFileStore(parent).type()))throw new AssertionError("Actual tmpfs required");
        Path root=Files.createTempDirectory(parent,"kanger-profile-"),location=root.resolve("Volatile");
        boolean acknowledged=false,refused=false;
        try{
            try(ContextStore store=ContextStore.create(location)){
                Step row=new Step();row.setId(0);row.setHash(19);row.setData(1L);store.getBase("index").add(row);
                acknowledged=store.flush()==1;
            }catch(StorageLifecycleException failure){
                if(!"STORAGE_DURABILITY_UNSUPPORTED".equals(failure.getCode()))throw failure;
                refused=true;
                if(Files.exists(ContextStore.contextPath(location))||Files.exists(ContextStore.revisionPath(location)))
                    throw new AssertionError("Unsupported create must refuse before metadata visibility");
            }
            System.out.println("VOLATILE_PROFILE filesystem=tmpfs acknowledged="+acknowledged+" typed_refusal="+refused);
            if(refused) legacyChecks(root,Paths.get(args[1]));
        }finally{ContextStore.deleteRecursively(root);}
        if(!refused||acknowledged)System.exit(1);
    }
}
