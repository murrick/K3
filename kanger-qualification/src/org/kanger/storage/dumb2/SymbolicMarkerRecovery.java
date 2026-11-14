/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;import java.util.*;import java.io.IOException;
import org.kanger.*;import org.kanger.exception.*;
import org.kanger.storage.dumb2.SymbolicMarkerFaults.Point;

/** Real sealed generations and native settlement under exact marker move/cleanup failures. */
final class SymbolicMarkerRecovery {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind open(DB db,Path d,String name)throws Exception{User u=new User();u.setDatabaseDir(d.toString()+java.io.File.separator);db.init(u);Mind r=new Mind(u);u.setCurrentMind(r);Mind m=(Mind)r.useStorage(name);u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static void seed(Path d,String name,String text)throws Exception{DB db=new DB();Mind m=open(db,d,name);try{need(m.compile(text,null,false),"marker recovery fixture");}finally{close(m);}}
    private static Throwable cause(Throwable error,String message){for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof IOException&&message.equals(t.getMessage()))return t;return null;}
    private static void run(Point point,boolean writer)throws Exception{
        Path d=Files.createTempDirectory("marker-recovery-");seed(d,"Source","!marker(9);");seed(d,"Rules","!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);");DB db=new DB();Mind m=open(db,d,"Source");long expected;Throwable primary=null;
        try{db.connectContext("Rules");db.applyConnectionCommand(m,"Rules","!$u rel(1,u,1);");if(writer)m.saveCheckedSymbolicContextConnections();SymbolicCacheFamilyInspection before=db.inspectSymbolicCacheFamily(m);Path location=before.local.location,marker=ContextStore.revisionPath(location);long revision=db.getRevision(),target=db.versionHistory("Rules").getCurrentRevision();boolean shouldFail=point==Point.MOVE_FAIL||point==Point.MOVE_AND_CLEANUP_FAIL;Throwable failure=null;
            try(SymbolicMarkerFaults fault=new SymbolicMarkerFaults(marker,point)){
                try{if(writer)need(m.writeCheckedSymbolicContextAssertion("!safe(8);",false),"successful native assertion result");else need(m.saveCheckedSymbolicContextConnections()==revision+1,"successful save result");}
                catch(TransactionSettlementException error){need(writer&&error.getOutcome()==TransactionSettlementException.Outcome.COMMITTED&&error.isReservationConsumed(),"native failure remains consumed COMMITTED");failure=error;}
                catch(IOException error){need(!writer,"save error does not invent native settlement");failure=error;}
                long disk=RevisionStore.read(marker);need(db.getRevision()==disk,"provider cursor must follow the confirmed marker, even after cleanup or ambiguous move return");
                need((failure!=null)==shouldFail&&disk==revision+(shouldFail?0:1),"reported result distinguishes unchanged and proven published markers");need(fault.moves==1&&fault.moved==!shouldFail,"fault reached the actual source marker move exactly once");
                if(!shouldFail)need(fault.cleanups==0,"successful replacement must not perform another fallible temporary deletion");
                else{Throwable move=cause(failure,"Injected marker move failure");need(move!=null,"original move failure remains primary");need(fault.cleanups==1,"failed move attempts temporary cleanup");if(point==Point.MOVE_AND_CLEANUP_FAIL){boolean suppressed=false;for(Throwable extra:move.getSuppressed())suppressed|="Injected marker cleanup failure".equals(extra.getMessage());need(suppressed,"cleanup failure supplements rather than replaces the move failure");}}
            }
            DmzCandidateSession.requireSettledPhysicalQ(m);need(true,"operation leaves no child reservation");
            if(shouldFail){if(writer)((User)m.getUser()).checkpoint(m);else m.saveCheckedSymbolicContextConnections();}
            expected=revision+1;need(db.getRevision()==expected,"recovery never submits the accepted delta twice");
            for(ContextConnection c:db.dmzTopology())need(c.getCertificate().matches(new RevisionRef(db.getContextId(),expected),c.getTarget(),c.getCertificate().getSemanticVersion()),"certificate matches confirmed published source");
            db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"fresh family evidence works after marker recovery");need(m.saveCheckedSymbolicContextConnections()==expected,"checked no-op does not duplicate publication");need(db.versionHistory("Rules").getCurrentRevision()==target,"physical target remains unchanged");
        }catch(Exception|AssertionError error){primary=error;throw error;}finally{try{close(m);}catch(Exception cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}}
        db=new DB();m=open(db,d,"Source");try{need(db.getRevision()==expected,"confirmed revision reopens cold");Set<String> originals=new HashSet<>();for(ContextProductionInventory.Row row:ContextProductionInventory.inspect(m,db.getContextId(),expected,true,Collections.emptyList(),1000).primary)originals.add(row.origin);Set<String> wanted=new HashSet<>(Collections.singletonList("!marker(9);"));if(writer)wanted.add("!safe(8);");need(originals.equals(wanted),"exact original manifest survives without duplicated accepted rule");need(db.dmzTopology().size()==1&&db.dmzTopology().get(0).getInitialization().equals(Collections.singletonList("!$u rel(1,u,1);")),"complete private recipe survives cold reopening");db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"cold family remains qualified");}finally{close(m);}
    }
    static void runAll(boolean writer)throws Exception{System.setProperty("user.home",Files.createTempDirectory("marker-recovery-home-").toString());for(Point point:new Point[]{Point.CLEANUP_AFTER_SUCCESS,Point.MOVE_REPORT_ERROR,Point.MOVE_FAIL,Point.MOVE_AND_CLEANUP_FAIL})run(point,writer);System.out.println((writer?"SYMBOLIC_MARKER_NATIVE_PASS":"SYMBOLIC_MARKER_SAVE_PASS")+" checks="+checks);}
}
