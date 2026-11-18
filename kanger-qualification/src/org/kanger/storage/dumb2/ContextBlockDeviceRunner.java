/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.storage.Step;
import org.kanger.exception.StorageLifecycleException;

/** Executed inside the guest against the unmodified packaged provider. */
public final class ContextBlockDeviceRunner {
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home","/root");Path location=Paths.get("/store/Source");
        if("write-interrupt".equals(args[0])){
            ContextStore store=ContextStore.create(location);add(store,0,42L);
            if(store.flush()!=1)throw new AssertionError("R1 ACK required");
            System.out.println("KANGER_BLOCK_PREVIOUS_ACK revision=1 value=42");
            if(System.in.read()<0)throw new AssertionError("Host release required");
            add(store,1,84L);if(store.flush()!=2)throw new AssertionError("R2 ACK");
            System.out.println("KANGER_BLOCK_NEXT_ACK revision=2 value=84");
            for(;;)Thread.sleep(1000);
        }else if("read-interrupt".equals(args[0])){
            // Snapshot reads must precede mutable open or any explicit persistence repair.
            try(ContextSnapshot previous=ContextSnapshot.open(location,1)){check(previous,0,42L);}
            long selected;
            try(ContextSnapshot current=ContextSnapshot.open(location)){
                selected=current.getRevision();
                if(selected!=1&&selected!=2)throw new AssertionError("Invalid recovered CURRENT "+selected);
                check(current,0,42L);if(selected==2)check(current,1,84L);
            }
            System.out.println("KANGER_BLOCK_PREVIOUS_RECOVERED revision=1 selected="+selected);
            for(;;)Thread.sleep(1000);
        }else if(args[0].contains("-")){
            String[] parts=args[0].split("-",2);String operation=parts[1];
            if(!java.util.Arrays.asList("create","append2","fork").contains(operation))
                throw new IllegalArgumentException(operation);
            boolean write="write".equals(parts[0]);
            long revision="create".equals(operation)?0:("append2".equals(operation)?2:1);
            if(write){
                ContextStore store=ContextStore.create(location);
                if(!"create".equals(operation)){
                    add(store,0,42L);if(store.flush()!=1)throw new AssertionError("R1");
                    if("append2".equals(operation)){
                        add(store,1,84L);if(store.flush()!=2)throw new AssertionError("R2");
                    }else{
                        try(ContextSnapshot source=ContextSnapshot.open(location,1)){
                            ContextStore fork=ContextStore.fork(source,Paths.get("/store/Fork"));
                            if(fork.getRevision()!=1)throw new AssertionError("Fork R1");
                        }
                    }
                }
            }else{
                try(ContextSnapshot recovered=ContextSnapshot.open(location,revision)){
                    if(recovered.getRevision()!=revision)throw new AssertionError("Revision");
                    if(revision>0)check(recovered,0,42L);
                    if(revision==2)check(recovered,1,84L);
                }
                if("append2".equals(operation)){
                    try(ContextSnapshot previous=ContextSnapshot.open(location,1)){check(previous,0,42L);}
                }
                if("fork".equals(operation)){
                    try(ContextSnapshot fork=ContextSnapshot.open(Paths.get("/store/Fork"),1)){check(fork,0,42L);}
                }
            }
            System.out.println("KANGER_BLOCK_"+(write?"ACK":"RECOVERED")+" operation="+operation+" revision="+revision);
            for(;;)Thread.sleep(1000);
        }else if("write".equals(args[0])){
            ContextStore store=ContextStore.create(location);
            Step row=new Step();row.setId(0);row.setHash(19);row.setData(42L);store.getBase("index").add(row);
            if(store.flush()!=1)throw new AssertionError("Expected actual R1 acknowledgement");
            System.out.println("KANGER_BLOCK_ACK revision=1 value=42");
            // No orderly shutdown, close or extra sync after the ACK.
            for(;;)Thread.sleep(1000);
        }else{
            DurabilityProfile.requireContext(location);
            try(ContextSnapshot recovered=ContextSnapshot.open(location,1)){
                if(recovered.getRevision()!=1||!Long.valueOf(42).equals(recovered.getBase("index").get(0).getData()))
                    throw new AssertionError("Acknowledged value lost after cold recovery");
            }catch(StorageLifecycleException missing){
                if(!"STORAGE_NOT_FOUND".equals(missing.getCode()))throw missing;
                System.out.println("KANGER_BLOCK_MISSING revision=1 code=STORAGE_NOT_FOUND");
                for(;;)Thread.sleep(1000);
            }
            System.out.println("KANGER_BLOCK_RECOVERED revision=1 value=42");
            for(;;)Thread.sleep(1000);
        }
    }
    private static void add(ContextStore store,int id,long value)throws Exception{
        Step row=new Step();row.setId(id);row.setHash(19+id);row.setData(value);if(id>0)row.setNext(store.getBase("index").get(id-1));store.getBase("index").add(row);
    }
    private static void check(ContextSnapshot snapshot,int id,long value)throws Exception{
        if(!Long.valueOf(value).equals(snapshot.getBase("index").get(id).getData()))throw new AssertionError("Lost row "+id);
    }
}
