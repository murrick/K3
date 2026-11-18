/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.storage.Step;
import org.kanger.exception.StorageLifecycleException;

/** Executed inside the guest against the unmodified packaged provider. */
public final class ContextBlockDeviceRunner {
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home","/root");Path location=Paths.get("/store/Source");
        if("write".equals(args[0])){
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
}
