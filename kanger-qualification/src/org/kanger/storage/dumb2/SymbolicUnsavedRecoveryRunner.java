/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;import java.util.*;
import org.kanger.*;import org.kanger.exception.*;
import org.kanger.storage.dumb2.SymbolicPublicationFaults.Point;

/** Source recovery preserves working recipes without implicitly publishing session topology. */
public final class SymbolicUnsavedRecoveryRunner {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind open(DB db,Path d,String name)throws Exception{User u=new User();u.setDatabaseDir(d.toString()+java.io.File.separator);db.init(u);Mind r=new Mind(u);u.setCurrentMind(r);Mind m=(Mind)r.useStorage(name);u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static void seed(Path d,String name,String text)throws Exception{DB db=new DB();Mind m=open(db,d,name);try{need(m.compile(text,null,false),"unsaved recovery fixture");}finally{close(m);}}
    private static void run(Point point)throws Exception{
        Path d=Files.createTempDirectory("unsaved-recovery-");seed(d,"Source","!marker(9);");seed(d,"Rules","!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);");DB db=new DB();Mind m=open(db,d,"Source");boolean save=point==Point.ACTIVATION;long expected;
        try{db.connectContext("Rules");db.applyConnectionCommand(m,"Rules","!$u rel(1,u,1);");SymbolicCacheFamilyInspection before=db.inspectSymbolicCacheFamily(m);Path location=before.local.location;long revision=db.getRevision(),target=db.versionHistory("Rules").getCurrentRevision();need(db.federationSnapshot().hasWorkingChanges()&&ConnectionStore.read(location,new RevisionRef(db.getContextId(),revision)).isEmpty(),"working private recipe really is absent from published topology");
            boolean failed=false;try(SymbolicPublicationFaults fault=new SymbolicPublicationFaults(db,location,point)){try{m.writeCheckedSymbolicContextAssertion("!safe(8);",false);}catch(TransactionSettlementException error){failed=error.getOutcome()==TransactionSettlementException.Outcome.COMMITTED&&error.isReservationConsumed();}need(failed&&fault.hits==1,"actual native accepted delta encountered one late failure");}
            ((User)m.getUser()).checkpoint(m);expected=revision+1;need(db.getRevision()==expected,"checkpoint recovers only the accepted source revision");DmzCandidateSession.requireSettledPhysicalQ(m);
            need(ConnectionStore.read(location,new RevisionRef(db.getContextId(),expected)).isEmpty(),"source publication and recovery never persist unsaved connections");need(db.federationSnapshot().hasWorkingChanges()&&db.dmzTopology().size()==1,"working connection intent survives recovery");ContextConnection connection=db.dmzTopology().get(0);need(connection.getInitialization().equals(Collections.singletonList("!$u rel(1,u,1);")),"complete private recipe survives recovery");need(connection.getCertificate().matches(new RevisionRef(db.getContextId(),expected),connection.getTarget(),connection.getCertificate().getSemanticVersion()),"unsaved connection is qualified against recovered source revision");
            db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"full independent family remains usable with unsaved working recipe");need(db.versionHistory("Rules").getCurrentRevision()==target,"private recipe never changes physical target");
            if(save){expected++;need(m.saveCheckedSymbolicContextConnections()==expected,"only explicit save publishes retained working intent");need(!db.federationSnapshot().hasWorkingChanges(),"explicitly saved intent is settled");}
        }finally{close(m);}
        db=new DB();m=open(db,d,"Source");try{need(db.getRevision()==expected&&db.dmzTopology().size()==(save?1:0),"cold reopen follows only the explicitly saved topology");ContextProductionInventory inventory=ContextProductionInventory.inspect(m,db.getContextId(),expected,true,Collections.emptyList(),1000);Set<String> originals=new HashSet<>();for(ContextProductionInventory.Row row:inventory.primary)originals.add(row.origin);need(originals.equals(new HashSet<>(Arrays.asList("!marker(9);","!safe(8);"))),"exact source originals persist without promoting the private root");if(save)need(db.dmzTopology().get(0).getInitialization().equals(Collections.singletonList("!$u rel(1,u,1);")),"explicitly saved complete recipe reopens");db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"cold family qualifies after recovery and selected save policy");}finally{close(m);}
    }
    public static void main(String[] args)throws Exception{System.setProperty("user.home",Files.createTempDirectory("unsaved-recovery-home-").toString());run(Point.BEFORE_MARKER);run(Point.ACTIVATION);System.out.println("SYMBOLIC_UNSAVED_RECOVERY_PASS checks="+checks);}
}
