/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.IOException;import java.nio.file.*;

/** Test-only real atomic move with controlled return/cleanup failures for one exact marker. */
final class SymbolicMarkerFaults implements AutoCloseable {
    enum Point {PROFILE_AFTER_MARKER_FAILURE,PIN_BARRIER_FAILURE,PRE_MARKER_BARRIER_FAILURE,DIRECTORY_BARRIER_FAILURE,CLEANUP_AFTER_SUCCESS,MOVE_REPORT_ERROR,MOVE_FAIL,MOVE_AND_CLEANUP_FAIL,MOVE_REPORT_BAD_MARKER,MOVE_REPORT_UNREADABLE,MOVE_FAIL_UNREADABLE,HALT_AFTER_MOVE}
    private static final ThreadLocal<SymbolicMarkerFaults> ACTIVE=new ThreadLocal<>();
    private final Path marker;private final Point point;int moves,cleanups,profileFailures;boolean moved;private byte[] validMarker;
    SymbolicMarkerFaults(Path marker,Point point){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested marker fault");this.marker=marker.toAbsolutePath().normalize();this.point=point;ACTIVE.set(this);}
    static Path move(Path source,Path target,CopyOption... options)throws IOException{
        SymbolicMarkerFaults f=ACTIVE.get();if(f==null||!f.marker.equals(target.toAbsolutePath().normalize()))return Files.move(source,target,options);
        f.moves++;if(f.point==Point.MOVE_FAIL||f.point==Point.MOVE_AND_CLEANUP_FAIL||f.point==Point.MOVE_FAIL_UNREADABLE)throw new IOException("Injected marker move failure");
        Path result=Files.move(source,target,options);if(f.point==Point.HALT_AFTER_MOVE)SymbolicProcessDeathRunner.halt("AFTER_MARKER");f.moved=true;f.validMarker=Files.readAllBytes(target);if(f.point==Point.MOVE_REPORT_BAD_MARKER){byte[] bytes=Files.readAllBytes(target);bytes[8]^=1;Files.write(target,bytes);}if(f.point==Point.MOVE_REPORT_ERROR||f.point==Point.MOVE_REPORT_BAD_MARKER||f.point==Point.MOVE_REPORT_UNREADABLE)throw new IOException("Injected marker move returned an error after replacement");return result;
    }
    static long verifyRead(Path path)throws IOException,org.kanger.exception.StorageLifecycleException{SymbolicMarkerFaults f=ACTIVE.get();if(f!=null&&(f.moved&&f.point==Point.MOVE_REPORT_UNREADABLE||f.point==Point.MOVE_FAIL_UNREADABLE)&&f.marker.equals(path.toAbsolutePath().normalize()))throw new IOException("Injected marker verification read failure");return RevisionStore.read(path);}
    static boolean cleanup(Path path)throws IOException{
        SymbolicMarkerFaults f=ACTIVE.get();if(f!=null&&path.toAbsolutePath().normalize().getParent().equals(f.marker.getParent())&&path.getFileName().toString().startsWith(f.marker.getFileName()+".next-")){
            f.cleanups++;if(f.point==Point.CLEANUP_AFTER_SUCCESS&&f.moved||f.point==Point.MOVE_AND_CLEANUP_FAIL)throw new IOException("Injected marker cleanup failure");
        }return Files.deleteIfExists(path);
    }
    static void profile(Path path)throws org.kanger.exception.StorageLifecycleException{
        SymbolicMarkerFaults f=ACTIVE.get();
        if(f!=null&&f.moved&&f.point==Point.PROFILE_AFTER_MARKER_FAILURE&&f.marker.equals(path.toAbsolutePath().normalize())){
            f.profileFailures++;
            throw new org.kanger.exception.StorageLifecycleException(org.kanger.enums.StorageLifecycleErrorCode.STORAGE_DURABILITY_UNSUPPORTED,"Injected profile confirmation refusal");
        }
    }
    static void directory(Path path)throws IOException{
        SymbolicMarkerFaults f=ACTIVE.get();
        if(f!=null&&(f.moved&&f.point==Point.DIRECTORY_BARRIER_FAILURE
                ||!f.moved&&f.point==Point.PRE_MARKER_BARRIER_FAILURE
                ||f.point==Point.PIN_BARRIER_FAILURE)
                &&f.marker.getParent().equals(path.toAbsolutePath().normalize()))
            throw new IOException("Injected directory persistence barrier failure");
    }
    byte[] validMarker(){return validMarker==null?null:validMarker.clone();}
    @Override public void close(){if(ACTIVE.get()!=this)throw new IllegalStateException("Wrong marker scope");ACTIVE.remove();}
}
