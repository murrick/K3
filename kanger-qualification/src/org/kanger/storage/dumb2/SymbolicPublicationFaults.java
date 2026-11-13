/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.IOException;import java.nio.file.Path;

/** Qualification-only, thread/owner-bound, one-shot I/O faults. Never linked by production sources. */
final class SymbolicPublicationFaults implements AutoCloseable {
    enum Point {BEFORE_MARKER,ACTIVATION}
    private static final ThreadLocal<SymbolicPublicationFaults> ACTIVE=new ThreadLocal<>();
    private final DB owner;private final Path location;private final Point point;int hits;
    SymbolicPublicationFaults(DB owner,Path location,Point point){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested publication fault");this.owner=owner;this.location=location.toAbsolutePath().normalize();this.point=point;ACTIVE.set(this);}
    static void beforeMarker(Path path)throws IOException{SymbolicPublicationFaults f=ACTIVE.get();if(f!=null&&f.point==Point.BEFORE_MARKER&&f.location.equals(path.toAbsolutePath().normalize()))f.trip();}
    static void activation(DB owner)throws IOException{SymbolicPublicationFaults f=ACTIVE.get();if(f!=null&&f.point==Point.ACTIVATION&&f.owner==owner)f.trip();}
    private void trip()throws IOException{if(hits++==0)throw new IOException("Injected symbolic publication failure: "+point);}
    @Override public void close(){if(ACTIVE.get()!=this)throw new IllegalStateException("Wrong publication fault owner");ACTIVE.remove();}
}
