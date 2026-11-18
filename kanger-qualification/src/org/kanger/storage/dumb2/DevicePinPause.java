/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.io.IOException;
/** Producer-only qualification pause; never added to the provider JAR. */
final class DevicePinPause {
    private static Path marker;
    static void arm(Path path){marker=path.toAbsolutePath().normalize();}
    static void visible(Path path)throws IOException{
        if(marker==null||!marker.equals(path.toAbsolutePath().normalize()))return;
        Files.write(Paths.get("/tmp/pin-visible"),new byte[]{1},StandardOpenOption.CREATE_NEW);
        long end=System.nanoTime()+1_800_000_000_000L;
        while(System.nanoTime()<end)try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
        throw new IOException("Qualification pause deadline exceeded");
    }
}
