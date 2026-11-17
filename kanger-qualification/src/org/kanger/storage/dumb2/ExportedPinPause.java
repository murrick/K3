/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.IOException;
import java.nio.file.*;

/** Qualification-only pause after one real marker replacement in the producer process. */
final class ExportedPinPause {
    private static Path marker, signal;
    static void arm(Path selected, Path ready) { marker=selected.toAbsolutePath().normalize(); signal=ready; }
    static void afterMove(Path target) throws IOException {
        if(marker==null || !marker.equals(target.toAbsolutePath().normalize()))return;
        Files.write(signal,new byte[]{1},StandardOpenOption.CREATE_NEW);
        long deadline=System.nanoTime()+120_000_000_000L;
        while(System.nanoTime()<deadline) {
            try { Thread.sleep(20); } catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();throw new IOException("Producer pause interrupted",interrupted);
            }
        }
        throw new IOException("Qualification producer was not terminated before pause timeout");
    }
}
