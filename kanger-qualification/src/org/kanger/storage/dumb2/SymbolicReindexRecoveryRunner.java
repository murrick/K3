/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import org.kanger.*;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.units.Rule;
import org.kanger.storage.dumb2.adapter.RuleV1Adapter;
import org.kanger.storage.dumb2.descriptor.*;

/** Real historical Rule-v1 migration, marker uncertainty and living-session recovery. */
public final class SymbolicReindexRecoveryRunner {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind open(DB db,Path d)throws Exception{return open(db,d,"Source");}
    private static Mind open(DB db,Path d,String name)throws Exception{User u=new User();u.setDatabaseDir(d.toString()+java.io.File.separator);db.init(u);Mind r=new Mind(u);u.setCurrentMind(r);Mind m=(Mind)r.useStorage(name);u.setCurrentMind(m);return m;}
    private static ContextStore store(DB db)throws Exception{java.lang.reflect.Field f=DB.class.getDeclaredField("context");f.setAccessible(true);return (ContextStore)f.get(db);}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static String layout(ContextStore s,ContextBase b,long id)throws Exception{return s.resolveType(((ContextStep)b.get(id)).getPersistentRecord().getTypeCode()).getDescriptor().getName();}
    private static void run(SymbolicMarkerFaults.Point point,boolean publicEntry)throws Exception{
        Path d=Files.createTempDirectory("reindex-recovery-");DB targetDB=new DB();Mind targetMind=open(targetDB,d,"Rules");try{need(targetMind.compile("!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);",null,false),"real independent target");}finally{close(targetMind);}
        DB db=new DB();Mind m=open(db,d);Throwable primary=null;long expected;
        try{
            need(m.compile("!marker(9);",null,false),"real source original");((User)m.getUser()).checkpoint(m);
            ContextStore s=store(db);ContextBase b=(ContextBase)db.getBase("rules");Rule rule=null;for(org.kanger.interfaces.IRule r:m.getRules())if(!r.isGenerated())rule=(Rule)r;
            need(rule!=null,"source original available");long id=rule.getId();ContextStep step=(ContextStep)b.get(id);PersistentRecord old=step.getPersistentRecord();
            TypeDefinition type=s.registerType(RuleV1Adapter.TYPE_NAME,RuleV1Adapter.INSTANCE.getDescriptor());
            byte[] payload=StructuralValueCodec.encode(type.getDescriptor(),RuleV1Adapter.INSTANCE.project(rule,m));
            b.update(new ContextStep(b,new PersistentRecord(id,old.getHash(),old.getNextId(),type.getTypeCode(),payload)));db.flush();
            need("Rule-v1".equals(layout(s,b,id)),"actual historical physical layout before migration");db.connectContext("Rules");db.applyConnectionCommand(m,"Rules","!$u rel(1,u,1);");m.saveCheckedSymbolicContextConnections();SymbolicCacheFamilyInspection receipt=db.inspectSymbolicCacheFamily(m);long targetRevision=db.versionHistory("Rules").getCurrentRevision();long before=db.getRevision();Path location=d.resolve("Source");boolean advanced=point!=SymbolicMarkerFaults.Point.MOVE_FAIL_UNREADABLE;byte[] repaired;
            try(SymbolicMarkerFaults fault=new SymbolicMarkerFaults(ContextStore.revisionPath(location),point)){
                boolean failed=false;try{if(publicEntry)m=(Mind)((User)m.getUser()).reindex(null,m,"Source");else db.reindex(null,m);}catch(IOException e){failed=true;if(publicEntry)m=(Mind)((User)m.getUser()).getCurrentMind();}need(failed&&fault.moves==1&&fault.moved==advanced,"real reindex marker attempt became unconfirmed");repaired=fault.validMarker();
                need(db.getRevision()==before,"last-confirmed diagnostic cursor retained");boolean fenced=false;try{db.federationSnapshot();}catch(StorageLifecycleException e){fenced=true;}need(fenced,"reindex uncertainty must fence CURRENT");
                fenced=false;try{receipt.requireCurrentModelled(db,m);}catch(StorageLifecycleException e){fenced=true;}need(fenced,"old receipt is fenced through reindex uncertainty");
                boolean blocked=false;try{((User)m.getUser()).checkpoint(m);}catch(Exception e){blocked=true;}need(blocked,"unavailable or invalid marker cannot be guessed");DmzCandidateSession.requireSettledPhysicalQ(m);need(true,"failed recovery reserves no child");
            }
            if(point==SymbolicMarkerFaults.Point.MOVE_REPORT_BAD_MARKER)Files.write(ContextStore.revisionPath(location),repaired);
            if(advanced&&point==SymbolicMarkerFaults.Point.MOVE_REPORT_UNREADABLE){
                Path manifest=RevisionManifestStore.path(ContextStore.generationPath(location,before+1));byte[] valid=Files.readAllBytes(manifest),bad=valid.clone();bad[bad.length-1]^=1;
                try{Files.write(manifest,bad);boolean rejected=false;try{((User)m.getUser()).checkpoint(m);}catch(StorageLifecycleException e){rejected=true;}need(rejected&&db.getRevision()==before,"corrupt canonical generation cannot authorize adoption");DmzCandidateSession.requireSettledPhysicalQ(m);}finally{Files.write(manifest,valid);}
            }
            ((User)m.getUser()).checkpoint(m);s=store(db);b=(ContextBase)db.getBase("rules");need(db.getRevision()==before+(advanced?1:0),"checkpoint adopts only actually published reindex");
            need((advanced?"Rule-v2":"Rule-v1").equals(layout(s,b,id)),"live physical image matches confirmed marker");
            if(!advanced)db.reindex(null,m);expected=before+1;need(db.getRevision()==expected,"retry or recovery yields exactly one revision");
            need("Rule-v2".equals(layout(s,b,id)),"live base adopts canonical image");db.reindex(null,m);need(db.getRevision()==expected,"reindex no-op does not republish recovered image");
            for(ContextConnection c:db.dmzTopology())need(c.getCertificate().matches(new RevisionRef(db.getContextId(),expected),c.getTarget(),c.getCertificate().getSemanticVersion()),"reindex working certificate follows confirmed source");
            db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"fresh complete family works after reindex recovery");need(db.versionHistory("Rules").getCurrentRevision()==targetRevision,"physical target unchanged");
            need(Boolean.TRUE.equals(m.query("?marker(9);")),"logical original survives migration");
        }catch(Exception|AssertionError e){primary=e;throw e;}finally{try{close(m);}catch(Exception e){if(primary!=null)primary.addSuppressed(e);else throw e;}}
        db=new DB();m=open(db,d);try{need(db.getRevision()==expected,"canonical revision survives cold reopen");need(Boolean.TRUE.equals(m.query("?marker(9);")),"cold original preserved");need(db.dmzTopology().get(0).getInitialization().equals(Collections.singletonList("!$u rel(1,u,1);")),"private recipe survives cold reopen");db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);}finally{close(m);}
    }
    public static void main(String[] args)throws Exception{System.setProperty("user.home",Files.createTempDirectory("reindex-home-").toString());for(SymbolicMarkerFaults.Point p:new SymbolicMarkerFaults.Point[]{SymbolicMarkerFaults.Point.DIRECTORY_BARRIER_FAILURE,SymbolicMarkerFaults.Point.MOVE_REPORT_UNREADABLE,SymbolicMarkerFaults.Point.MOVE_FAIL_UNREADABLE,SymbolicMarkerFaults.Point.MOVE_REPORT_BAD_MARKER})for(boolean publicEntry:new boolean[]{false,true})run(p,publicEntry);System.out.println("SYMBOLIC_REINDEX_RECOVERY_PASS checks="+checks);}
}
