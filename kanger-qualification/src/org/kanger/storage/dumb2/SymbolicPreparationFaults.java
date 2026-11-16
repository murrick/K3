/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.*;

/** Qualification-only exact-source/thread-bound cuts inside real file preparation. */
final class SymbolicPreparationFaults implements AutoCloseable {
    private static final ThreadLocal<SymbolicPreparationFaults> ACTIVE=new ThreadLocal<>();
    private final Path location, evidence;
    private final String point;
    SymbolicPreparationFaults(Path location,Path evidence,String point){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested preparation fault");this.location=location.toAbsolutePath().normalize();this.evidence=evidence;this.point=point;ACTIVE.set(this);}
    private void cut(Path artifact,long fullLength)throws IOException{
        Properties p=new Properties();p.setProperty("artifact",artifact.toAbsolutePath().normalize().toString());p.setProperty("fullLength",Long.toString(fullLength));
        try(OutputStream out=Files.newOutputStream(evidence)){p.store(out,"exact artifact immediately before halt");}
        SymbolicProcessDeathRunner.halt(point);
    }
    static void staged(Path location,Path staging)throws IOException{SymbolicPreparationFaults f=ACTIVE.get();if(f!=null&&f.point.equals("STAGING_COPY")&&f.location.equals(location.toAbsolutePath().normalize()))f.cut(staging,-1);}
    static void schemaBody(Path path,DataOutputStream out,FileOutputStream file,byte[] body)throws IOException{
        SymbolicPreparationFaults f=ACTIVE.get();if(f!=null&&f.point.equals("SCHEMA_PARTIAL")&&path.toAbsolutePath().normalize().startsWith(ContextStore.stateRoot(f.location))){out.write(body,0,Math.max(1,body.length/2));out.flush();file.getFD().sync();f.cut(path,body.length+4L);}
        out.write(body);
    }
    static int connectionWrite(FileChannel channel,ByteBuffer buffer,Path temp,Path path)throws IOException{
        SymbolicPreparationFaults f=ACTIVE.get();if(f!=null&&f.point.equals("SIDECAR_PARTIAL")&&ConnectionStore.path(f.location).equals(path.toAbsolutePath().normalize())){
            int full=buffer.remaining();ByteBuffer part=buffer.duplicate();part.limit(part.position()+Math.max(1,full/2));while(part.hasRemaining())channel.write(part);channel.force(true);f.cut(temp,full);
        }
        return channel.write(buffer);
    }
    static Path connectionMove(Path temp,Path path,CopyOption... options)throws IOException{
        SymbolicPreparationFaults f=ACTIVE.get();if(f!=null&&f.point.equals("SIDECAR_TEMP")&&ConnectionStore.path(f.location).equals(path.toAbsolutePath().normalize()))f.cut(temp,Files.size(temp));
        return Files.move(temp,path,options);
    }
    static Path markerWrite(Path temp,byte[] bytes,OpenOption... options)throws IOException{
        SymbolicPreparationFaults f=ACTIVE.get();if(f!=null&&f.point.equals("MARKER_PARTIAL")&&temp.toAbsolutePath().normalize().getParent().equals(f.location.getParent())&&temp.getFileName().toString().startsWith(ContextStore.revisionPath(f.location).getFileName()+".next-")){
            Files.write(temp,Arrays.copyOf(bytes,Math.max(1,bytes.length/2)),options);try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}f.cut(temp,bytes.length);
        }
        return Files.write(temp,bytes,options);
    }
    static Path markerMove(Path temp,Path path,CopyOption... options)throws IOException{
        SymbolicPreparationFaults f=ACTIVE.get();if(f!=null&&f.point.equals("MARKER_TEMP")&&ContextStore.revisionPath(f.location).equals(path.toAbsolutePath().normalize()))f.cut(temp,Files.size(temp));
        return SymbolicMarkerFaults.move(temp,path,options);
    }
    @Override public void close(){if(ACTIVE.get()!=this)throw new IllegalStateException("Wrong preparation scope");ACTIVE.remove();}
}
