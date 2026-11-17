/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.io.IOException;
final class InitialPinPause {
    static Path marker,root;
    static void beforeConfirmation(Path path)throws IOException{
        if(marker==null||!marker.equals(path))return;
        Files.write(root.resolve("visible"),new byte[]{1},StandardOpenOption.CREATE_NEW);
        long deadline=System.nanoTime()+120_000_000_000L;
        while(!Files.exists(root.resolve("release"))&&System.nanoTime()<deadline){
            try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
        }
        throw new IOException("Injected initial marker confirmation failure");
    }
}
