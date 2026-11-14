/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.IOException;import java.nio.file.*;

/** Test-only real atomic move with controlled return/cleanup failures for one exact marker. */
final class SymbolicMarkerFaults implements AutoCloseable {
    enum Point {CLEANUP_AFTER_SUCCESS,MOVE_REPORT_ERROR,MOVE_FAIL,MOVE_AND_CLEANUP_FAIL,MOVE_REPORT_BAD_MARKER,MOVE_REPORT_UNREADABLE}
    private static final ThreadLocal<SymbolicMarkerFaults> ACTIVE=new ThreadLocal<>();
    private final Path marker;private final Point point;int moves,cleanups;boolean moved;
    SymbolicMarkerFaults(Path marker,Point point){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested marker fault");this.marker=marker.toAbsolutePath().normalize();this.point=point;ACTIVE.set(this);}
    static Path move(Path source,Path target,CopyOption... options)throws IOException{
        SymbolicMarkerFaults f=ACTIVE.get();if(f==null||!f.marker.equals(target.toAbsolutePath().normalize()))return Files.move(source,target,options);
        f.moves++;if(f.point==Point.MOVE_FAIL||f.point==Point.MOVE_AND_CLEANUP_FAIL)throw new IOException("Injected marker move failure");
        Path result=Files.move(source,target,options);f.moved=true;if(f.point==Point.MOVE_REPORT_BAD_MARKER){byte[] bytes=Files.readAllBytes(target);bytes[8]^=1;Files.write(target,bytes);}if(f.point==Point.MOVE_REPORT_ERROR||f.point==Point.MOVE_REPORT_BAD_MARKER||f.point==Point.MOVE_REPORT_UNREADABLE)throw new IOException("Injected marker move returned an error after replacement");return result;
    }
    static long verifyRead(Path path)throws IOException,org.kanger.exception.StorageLifecycleException{SymbolicMarkerFaults f=ACTIVE.get();if(f!=null&&f.moved&&f.point==Point.MOVE_REPORT_UNREADABLE&&f.marker.equals(path.toAbsolutePath().normalize()))throw new IOException("Injected marker verification read failure");return RevisionStore.read(path);}
    static boolean cleanup(Path path)throws IOException{
        SymbolicMarkerFaults f=ACTIVE.get();if(f!=null&&path.toAbsolutePath().normalize().getParent().equals(f.marker.getParent())&&path.getFileName().toString().startsWith(f.marker.getFileName()+".next-")){
            f.cleanups++;if(f.point==Point.CLEANUP_AFTER_SUCCESS&&f.moved||f.point==Point.MOVE_AND_CLEANUP_FAIL)throw new IOException("Injected marker cleanup failure");
        }return Files.deleteIfExists(path);
    }
    @Override public void close(){if(ACTIVE.get()!=this)throw new IllegalStateException("Wrong marker scope");ACTIVE.remove();}
}
