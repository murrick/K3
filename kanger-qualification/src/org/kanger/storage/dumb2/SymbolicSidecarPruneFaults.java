/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;import java.nio.channels.FileChannel;import java.nio.ByteBuffer;import java.io.*;import java.util.*;
/** Qualification-only failures inside one exact sidecar pruning rewrite. */
final class SymbolicSidecarPruneFaults implements AutoCloseable {
    private static final ThreadLocal<SymbolicSidecarPruneFaults> ACTIVE=new ThreadLocal<>();
    private final Path sidecar,evidence;private final String point;int moves;
    SymbolicSidecarPruneFaults(Path location,Path evidence,String point){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested sidecar fault");sidecar=ConnectionStore.path(location).toAbsolutePath().normalize();this.evidence=evidence;this.point=point;ACTIVE.set(this);}
    private void cut(Path artifact,long full)throws IOException{Properties p=new Properties();p.setProperty("artifact",artifact.toString());p.setProperty("fullLength",Long.toString(full));try(OutputStream out=Files.newOutputStream(evidence)){p.store(out,"real sidecar rewrite boundary");}SymbolicProcessDeathRunner.halt(point);}
    static int write(FileChannel channel,ByteBuffer buffer,Path temp,Path path)throws IOException{
        SymbolicSidecarPruneFaults f=ACTIVE.get();if(f!=null&&f.sidecar.equals(path.toAbsolutePath().normalize())&&f.point.equals("PRUNE_PARTIAL")){int full=buffer.remaining();ByteBuffer part=buffer.duplicate();part.limit(part.position()+Math.max(1,full/2));while(part.hasRemaining())channel.write(part);channel.force(true);f.cut(temp,full);}
        return SymbolicPreparationFaults.connectionWrite(channel,buffer,temp,path);
    }
    static Path move(Path temp,Path path,CopyOption... options)throws IOException{
        SymbolicSidecarPruneFaults f=ACTIVE.get();boolean own=f!=null&&f.sidecar.equals(path.toAbsolutePath().normalize());
        if(own){f.moves++;if(f.point.equals("PRUNE_TEMP"))f.cut(temp,Files.size(temp));}
        Path result=SymbolicPreparationFaults.connectionMove(temp,path,options);
        if(own){if(f.point.equals("PRUNE_AFTER_MOVE"))f.cut(path,Files.size(path));if(f.point.equals("PRUNE_IO_AFTER_MOVE"))throw new IOException("Injected pruning move returned error after replacement");}
        return result;
    }
    @Override public void close(){if(ACTIVE.get()!=this)throw new IllegalStateException("Wrong sidecar fault scope");ACTIVE.remove();}
}
