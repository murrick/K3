/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;import java.io.*;import java.util.*;
/** Qualification-only exact-source cuts after retirement and after a real first unlink. */
final class SymbolicReclamationFaults implements AutoCloseable {
    private static final ThreadLocal<SymbolicReclamationFaults> ACTIVE=new ThreadLocal<>();
    private final Path location,evidence;private final String point;private Path retired;private long before;
    SymbolicReclamationFaults(Path location,Path evidence,String point){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested reclamation fault");this.location=location.toAbsolutePath().normalize();this.evidence=evidence;this.point=point;ACTIVE.set(this);}
    private static long count(Path path)throws IOException{try(java.util.stream.Stream<Path> entries=Files.list(path)){return entries.count();}}
    private void cut()throws IOException{Properties p=new Properties();p.setProperty("artifact",retired.toString());p.setProperty("before",Long.toString(before));p.setProperty("remaining",Long.toString(count(retired)));try(OutputStream out=Files.newOutputStream(evidence)){p.store(out,"actual retirement/deletion evidence");}SymbolicProcessDeathRunner.halt(point);}
    static void retired(Path location,Path name)throws IOException{SymbolicReclamationFaults f=ACTIVE.get();if(f!=null&&f.location.equals(location.toAbsolutePath().normalize())){f.retired=ContextStore.stateRoot(f.location).resolve(name);f.before=count(f.retired);if(f.point.equals("RECLAIM_RETIRED"))f.cut();}}
    static void deleted(Path location,Path name)throws IOException{SymbolicReclamationFaults f=ACTIVE.get();if(f!=null&&f.point.equals("RECLAIM_PARTIAL")&&f.location.equals(location.toAbsolutePath().normalize())&&f.retired!=null&&f.retired.getFileName().equals(name))f.cut();}
    @Override public void close(){if(ACTIVE.get()!=this)throw new IllegalStateException("Wrong reclamation scope");ACTIVE.remove();}
}
