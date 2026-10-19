/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Whole width-eight binding does not override narrower ground/universal proof limits. */
public final class ContextNativeClauseWidthRunner {
    static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static class Fixture extends ContextWideWitnessFamilyRunner.Fixture {Fixture(Path d,String n)throws Exception{super(d,n);}}
    static void create(Path dir,String name,String text)throws Exception{try(Fixture f=new Fixture(dir,name)){require(f.root.compile(text,null,false),"fixture "+name);}}
    static String base(int n){StringBuilder s=new StringBuilder();for(int i=1;i<n;i++)s.append("!p").append(i).append("(1); ");return s.toString();}
    static String rule(int n,boolean universal){String arg=universal?"x":"1";StringBuilder s=new StringBuilder(universal?"!@x q(x)":"!q(1)");for(int i=1;i<n;i++)s.append("&&p").append(i).append('(').append(arg).append(')');return s+"->out("+arg+");";}
    static StorageLifecycleException refused(Fixture f,String rule)throws Exception{
        long rev=f.db.getRevision();f.guard.install();StorageLifecycleException failure=null;try{f.root.query(rule,null,false);}catch(StorageLifecycleException expected){failure=expected;}
        require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"selected incomplete engine cannot fall through to native commit");require(f.db.getRevision()==rev,"incomplete engine never publishes");return failure;
    }
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("native-width-");System.setProperty("user.home",dir.toString());create(dir,"A","!q(1);");create(dir,"Safe","!safe(1);");
        for(int n:new int[]{4,7})for(boolean universal:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}){
            try(Fixture f=new Fixture(dir,"Limited"+n+universal+reverse)){
                require(f.root.compile(base(n),null,false),"ordinary parent");f.db.connectContext(reverse?"Safe":"A");f.db.connectContext(reverse?"A":"Safe");
                ContextPendingProductionSnapshotSession.Decision d=ContextWideWitnessFamilyRunner.inspect(f,rule(n,universal));
                if(universal){
                    require(d.engine==ContextPendingProductionSnapshotSession.Engine.UNIVERSAL,"whole bound universal engine selection");
                    if(d.universal.inspection.status==ContextUniversalFamilyInspection.Status.CONSISTENT)require(d.universal.audit.verified,"wide universal success has independent all-value audit");
                    else require(d.universal.inspection.status==ContextUniversalFamilyInspection.Status.INCOMPLETE&&d.universal.inspection.reason.contains("Unit ledger clause/proof limit"),"wide universal refusal retains ledger bound: "+d.universal.inspection.reason);
                    // Inspection is detached evidence; the write must qualify its own fresh lease.
                    long rev=f.db.getRevision();f.guard.install();boolean published=false;StorageLifecycleException failure=null;
                    try{published=Boolean.TRUE.equals(f.root.query(rule(n,true),null,false));}catch(StorageLifecycleException expected){failure=expected;}
                    require(published&&failure==null&&f.db.getRevision()>rev || !published&&failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&f.db.getRevision()==rev,"fresh universal attempt either qualifies or conservatively refuses, never reuses old success");
                    System.out.println("WIDTH_UNIVERSAL n="+n+" reverse="+reverse+" inspection="+d.universal.inspection.status+" published="+published);
                }else if(n==4){
                    require(d.engine==ContextPendingProductionSnapshotSession.Engine.GROUND&&d.ground.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&d.ground.certificate!=null&&d.ground.certificate.models.size()==3,"wide ground capture now closes with all separate models: "+d.ground.reason);
                    long rev=f.db.getRevision();f.guard.install();require(Boolean.TRUE.equals(f.root.query(rule(n,false),null,false)),"fresh wide ground native qualification publishes");require(f.db.getRevision()>rev,"ground publication advances revision");
                }else{
                    require(d.engine==ContextPendingProductionSnapshotSession.Engine.GROUND&&d.ground.status==ContextGroundClauseFamily.Status.INCOMPLETE&&d.ground.certificate==null&&d.ground.reason.contains("Residual clause limit: clauses=64, proofs=64"),"seven-premise ground clause limit is preserved through family qualification: "+d.ground.reason);
                    StorageLifecycleException failure=refused(f,rule(n,false));require(failure instanceof ContextGroundProductionCommitException&&((ContextGroundProductionCommitException)failure).getFamilyEvaluation().reason.contains("Residual clause limit: clauses=64, proofs=64"),"actual native typed refusal retains exact exhausted resource");
                }
            }
        }
        for(boolean universal:new boolean[]{false,true})try(Fixture f=new Fixture(dir,"Outside"+universal)){
            require(f.root.compile(base(8),null,false),"outside width parent");f.db.connectContext("A");boolean stopped=false;
            try{ContextWideWitnessFamilyRunner.inspect(f,rule(8,universal));}catch(IllegalStateException expected){stopped=expected.getMessage().contains("Native clause width limit");}require(stopped,"nine literals never expose bound prefix");refused(f,rule(8,universal));
        }
        System.out.println("CONTEXT_NATIVE_CLAUSE_WIDTH_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
