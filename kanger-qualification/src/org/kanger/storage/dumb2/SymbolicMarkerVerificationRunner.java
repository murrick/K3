/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;import java.io.IOException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.dumb2.SymbolicMarkerFaults.Point;

/** Unknown/unreadable or invalid markers must never be reported as confirmed publication. */
public final class SymbolicMarkerVerificationRunner {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("marker-verification-");for(Point point:new Point[]{Point.MOVE_REPORT_BAD_MARKER,Point.MOVE_REPORT_UNREADABLE}){
            Path marker=directory.resolve(point+".revision");RevisionStore.create(marker);IOException move=null;
            try(SymbolicMarkerFaults fault=new SymbolicMarkerFaults(marker,point)){
                try{RevisionStore.advance(marker,0);}catch(IOException expected){move=expected;}
                need(move!=null&&"Injected marker move returned an error after replacement".equals(move.getMessage()),"unconfirmed replacement preserves the move exception");need(fault.moves==1&&fault.moved,"marker really was replaced before verification failed");boolean evidence=false;for(Throwable extra:move.getSuppressed())evidence|=point==Point.MOVE_REPORT_BAD_MARKER?extra instanceof StorageLifecycleException:"Injected marker verification read failure".equals(extra.getMessage());need(evidence,"failed verification is retained without inventing a successful outcome");need(fault.cleanups==1,"unconfirmed outcome follows failure cleanup contract");
            }
            if(point==Point.MOVE_REPORT_UNREADABLE)need(RevisionStore.read(marker)==1,"later readable marker remains advanced despite the unconfirmed return");else{boolean invalid=false;try{RevisionStore.read(marker);}catch(StorageLifecycleException expected){invalid=true;}need(invalid,"invalid marker is not accepted by the ordinary CRC reader");}
        }System.out.println("SYMBOLIC_MARKER_VERIFICATION_PASS checks="+checks);
    }
}
