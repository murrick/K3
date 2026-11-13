/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Real native settlement and refusal, never a detached admission receipt. */
public final class SymbolicFamilyCandidateRunner {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static Mind open(DB db,Path directory,String name)throws Exception{User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static String fingerprint(DB db,Mind m)throws Exception{return ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000).fingerprint;}
    private static void pendingProduct()throws Exception{
        Path directory=Files.createTempDirectory("symbolic-pending-product-");DB db=new DB();Mind m=open(db,directory,"Local");try{
            need(m.compile("!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);",null,false),"seed local symbolic prefix");
        }finally{close(m);}
        db=new DB();m=open(db,directory,"Local");try{
            ContextProductionInventory before=ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000);need(before.primary.size()==2&&before.generated.isEmpty(),"cold local baseline binds the published primary-only prefix");
            Path location=db.inspectSymbolicCache(m).location;String statement="!$u rel(1,u,1);";ContextPendingSymbolicWrite attempt=db.beginSymbolicWrite(m,statement);
            try{need(Boolean.TRUE.equals(m.query(statement,null,false)),"candidate existential assertion with native symbolic production settles");}finally{db.endNativeWrite(attempt);}
            need(attempt.inspectedGeneratedRows()>before.generated.size(),"qualification really saw pending native symbolic productions before settlement");
            ContextProductionInventory after=ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000);need(after.primary.size()==before.primary.size()+1,"settlement preserves the new original");
            try(SnapshotMindRuntime physical=SnapshotMindRuntime.open(location,new RevisionRef(db.getContextId(),db.getRevision()),"Local")){
                ContextProductionInventory published=ContextProductionInventory.inspect(physical.getMind(),db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000);int deleted=0;for(ContextProductionInventory.Row row:published.rows)if(row.deleted)deleted++;need(deleted>0,"exact published snapshot really contains cache deletion markers");
            }
            db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"fresh family qualification accepts independently checked cold deletion markers");
            need(m.writeCheckedSymbolicContextAssertion("!safe(7);",false),"another actual append succeeds after symbolic products were retired");
            long stable=db.getRevision();String state=fingerprint(db,m);boolean refused=false;try{refused=!m.writeCheckedSymbolicContextAssertion("!@u ~tail(u);",false);}catch(StorageLifecycleException conflict){refused=conflict.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT;}need(refused&&stable==db.getRevision()&&state.equals(fingerprint(db,m)),"retired cache does not hide consequences of the still-live originals");
        }finally{close(m);}
        db=new DB();m=open(db,directory,"Local");try{
            ContextProductionInventory cold=ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000);boolean root=false;for(ContextProductionInventory.Row row:cold.primary)root|="!$u rel(1,u,1);".equals(row.origin);need(root&&cold.primary.size()==4,"successful existential and following original survive cold reopen");db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"family requalifies again after cold reopen");
        }finally{close(m);}
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("symbolic-candidate-home-").toString());Path directory=Files.createTempDirectory("symbolic-candidate-");DB db=new DB();Mind m=open(db,directory,"Rules");try{need(m.compile("!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);",null,false),"seed rules");}finally{close(m);}
        db=new DB();m=open(db,directory,"Source");try{
            need(m.compile("!marker(9);",null,false),"seed source");db.connectContext("Rules");db.applyConnectionCommand(m,"Rules","!$u rel(1,u,1);");m.saveCheckedSymbolicContextConnections();
            long before=db.getRevision();need(m.writeCheckedSymbolicContextAssertion("!safe(2);",false),"supported append settles through native writer");need(db.getRevision()>before,"successful assertion publishes a revision");db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);
            long revision=db.getRevision();String inventory=fingerprint(db,m);ContextConnection member=db.dmzTopology().get(0);ConnectionRuntime runtime=member.runtime();
            boolean conflict=false;try{m.writeCheckedSymbolicContextAssertion("!@u ~tail(u);",false);}catch(StorageLifecycleException error){conflict=error.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT&&!error.getCollisions().isEmpty();need(!error.toString().contains(db.getContextId().toString())&&!error.toString().contains(member.getTarget().getContextId().toString()),"actual conflict diagnostics use names");}
            need(conflict,"pending denial refuted against generated foreign tail");need(db.getRevision()==revision&&inventory.equals(fingerprint(db,m)),"proved refusal restores revision and raw source inventory");need(db.dmzTopology().get(0)==member&&member.runtime()==runtime,"refusal preserves exact member runtime");DmzCandidateSession.requireSettledPhysicalQ(m);need(true,"no pending child after refusal");
            boolean incomplete=false;try{m.writeCheckedSymbolicContextAssertion("!a(9)||b(9);",false);}catch(StorageLifecycleException error){incomplete=error.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&error.getCollisions().isEmpty();}
            need(incomplete,"unsupported proposed language is incomplete");need(db.getRevision()==revision&&inventory.equals(fingerprint(db,m)),"incomplete append restores source exactly");DmzCandidateSession.requireSettledPhysicalQ(m);
            final Mind restricted=m;final DB owner=db;ContextDependentForeignCacheSnapshotRunner.withNoGood(m,()->{boolean refused=false;try{restricted.writeCheckedSymbolicContextAssertion("!safe(4);",false);}catch(StorageLifecycleException error){refused=error.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&error.getCollisions().isEmpty();}need(refused&&owner.getRevision()==revision,"restricted source cannot start an unrestricted append");});
            boolean batch=false;try{m.writeCheckedSymbolicContextAssertion("!safe(4); !safe(5);",false);}catch(IllegalArgumentException expected){batch=true;}need(batch&&db.getRevision()==revision&&inventory.equals(fingerprint(db,m)),"multiple assertions rejected before opening a native attempt");
            need(m.writeCheckedSymbolicContextAssertion("!safe(3);",false),"fresh operation works after both refusals");
        }finally{close(m);}
        db=new DB();m=open(db,directory,"Source");try{db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(db.dmzTopology().get(0).getInitialization().equals(Collections.singletonList("!$u rel(1,u,1);")),"cold reopen preserves full private recipe");ContextProductionInventory actual=ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000);boolean safe=false,rejected=false;for(ContextProductionInventory.Row row:actual.primary){safe|="!safe(3);".equals(row.origin);rejected|="!@u ~tail(u);".equals(row.origin)||"!a(9)||b(9);".equals(row.origin);}need(safe&&!rejected,"only successful originals persist after cold reopen");}finally{close(m);}
        pendingProduct();System.out.println("SYMBOLIC_FAMILY_CANDIDATE_PASS checks="+checks);
    }
}
