/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Explicit opt-in save only, in the native connection profile. */
public final class SymbolicFamilyPublicationRunner {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind open(DB db,Path directory,String name)throws Exception{User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static void seed(Path directory,String name,String text)throws Exception{DB db=new DB();Mind m=open(db,directory,name);try{need(m.compile(text,null,false),"publication fixture "+name);}finally{close(m);}}
    private static void success()throws Exception{
        Path directory=Files.createTempDirectory("symbolic-checked-save-");seed(directory,"Source","!marker(9);");seed(directory,"Target","!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);");DB db=new DB();Mind source=open(db,directory,"Source");long published,pin;
        try{pin=db.versionHistory("Target").getCurrentRevision();db.connectContext("Target");db.applyConnectionCommand(source,"Target","!$u rel(1,u,1);");SymbolicCacheFamilyInspection before=db.inspectSymbolicCacheFamily(source);before.requireCurrentModelled(db,source);long revision=db.getRevision();published=source.saveCheckedSymbolicContextConnections();need(published>revision&&!db.federationSnapshot().hasWorkingChanges(),"explicit checked save publishes candidate connections only after fresh model qualification");need(db.versionHistory("Target").getCurrentRevision()==pin,"checked save never publishes private facts to target");need(source.saveCheckedSymbolicContextConnections()==published,"freshly rechecked no-op does not create another revision");
            boolean stale=false;try{before.requireCurrentModelled(db,source);}catch(StorageLifecycleException error){stale=error.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&error.getCollisions().isEmpty();}need(stale,"old pre-save result becomes typed incomplete after source revision changes");
            SymbolicCacheFamilyInspection old=db.inspectSymbolicCacheFamily(source);db.applyConnectionCommand(source,"Target","!private(2);");stale=false;try{old.requireCurrentModelled(db,source);}catch(StorageLifecycleException error){stale=error.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&error.getCollisions().isEmpty();}need(stale,"changed recipe cannot reuse a formerly checked model");published=source.saveCheckedSymbolicContextConnections();need(db.inspectSymbolicCacheFamily(source).members.get(0).initialization.size()==2,"checked save reacquires the new complete private recipe");
            final DB owner=db;final Mind q=source;final long stable=db.getRevision();ContextDependentForeignCacheSnapshotRunner.withNoGood(source,()->{boolean refused=false;try{q.saveCheckedSymbolicContextConnections();}catch(StorageLifecycleException expected){refused=expected.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&expected.getCollisions().isEmpty();}need(refused&&stable==owner.getRevision(),"source restriction becomes typed incomplete before publication");});
        }finally{close(source);}
        db=new DB();source=open(db,directory,"Source");try{SymbolicCacheFamilyInspection reopened=db.inspectSymbolicCacheFamily(source);reopened.requireCurrentModelled(db,source);need(db.getRevision()==published&&reopened.members.get(0).initialization.equals(Arrays.asList("!$u rel(1,u,1);","!private(2);")),"published checked pin and full recipe reopen with fresh model evidence");}finally{close(source);}
    }
    private static void incomplete()throws Exception{
        Path directory=Files.createTempDirectory("symbolic-checked-incomplete-");seed(directory,"Source","!a(9)||b(9);");seed(directory,"Target","!marker(1);");DB db=new DB();Mind source=open(db,directory,"Source");try{db.connectContext("Target");long revision=db.getRevision();String before=ContextProductionInventory.inspect(source,db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint;boolean pending=db.federationSnapshot().hasWorkingChanges();ContextConnection proposal=db.dmzTopology().get(0);ConnectionRuntime runtime=proposal.runtime();boolean incomplete=false;
            try{source.saveCheckedSymbolicContextConnections();}catch(StorageLifecycleException error){incomplete=error.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&error.getCollisions().isEmpty();need(!error.toString().contains(db.getContextId().toString()),"incomplete operator message does not expose UUIDs");}need(incomplete,"unsupported proof language gets typed incomplete, not a collision or publication");need(db.getRevision()==revision&&pending==db.federationSnapshot().hasWorkingChanges()&&before.equals(ContextProductionInventory.inspect(source,db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"failed save preserves current source and working proposal exactly");need(db.dmzTopology().size()==1&&db.dmzTopology().get(0)==proposal&&proposal.runtime()==runtime,"incomplete preflight retains the exact pending connection object and private runtime");db.disconnectContext("Target");
        }finally{close(source);}
    }
    public static void main(String[] args)throws Exception{System.setProperty("user.home",Files.createTempDirectory("symbolic-publication-driver-").toString());success();incomplete();System.out.println("SYMBOLIC_FAMILY_PUBLICATION_PASS checks="+checks);}
}
