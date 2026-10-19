/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.ContextOrdinaryResidualRounds.LedgerLimits;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Read-only owning requests cannot change a later automatic/native write budget. */
public final class ContextGroundRequestLimitsRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static class Fixture extends ContextWideWitnessFamilyRunner.Fixture{Fixture(Path d,String n)throws Exception{super(d,n);}}
    static void create(Path d,String n,String text)throws Exception{try(Fixture f=new Fixture(d,n)){require(f.root.compile(text,null,false),"context fixture "+n);}}
    static ContextGroundClauseFamily.Evaluation inspect(ContextPendingProductionSnapshotSession lease,LedgerLimits limits)throws Exception{return lease.inspectGround(16,16,16,WORK,500000,100000,1000000,limits);}
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("owning-request-limits-");System.setProperty("user.home",dir.toString());create(dir,"A","!q(1);");create(dir,"Safe","!safe(1);");
        for(int n:new int[]{4,7})try(Fixture f=new Fixture(dir,"Request"+n)){
            require(f.root.compile(ContextNativeClauseWidthRunner.base(n),null,false),"owning X source");f.db.connectContext("A");f.db.connectContext("Safe");String rule=ContextNativeClauseWidthRunner.rule(n,false);long revision=f.db.getRevision();Mind child=new Mind(f.root);require(child.compile(rule,null,false),"proposed immutable child");
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)){
                ContextGroundClauseFamily.Evaluation tiny=inspect(lease,new LedgerLimits(1,8));require(tiny.status==ContextGroundClauseFamily.Status.INCOMPLETE&&tiny.certificate==null&&tiny.reason.contains("Residual clause limit: clauses=1, proofs=1"),"explicit owning read-only request capacity");
                if(n==4){LedgerLimits loose=new LedgerLimits(256,1024);ContextGroundClauseFamily.Evaluation large=inspect(lease,loose);require(large.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&large.certificate.frontier.ledgerLimits==loose&&large.certificate.models.size()==3,"read-only qualification carries exact custom policy through complete models");}
                ContextPendingProductionSnapshotSession.Decision automatic=lease.inspectAutomatic(16,16,16,20000,1000000,WORK,500000,WORK,WORK,100000);
                if(n==4)require(automatic.ground.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&automatic.ground.certificate.frontier.ledgerLimits==LedgerLimits.DEFAULT,"automatic qualification retains original default after custom request");
                else require(automatic.ground.status==ContextGroundClauseFamily.Status.INCOMPLETE&&automatic.ground.reason.contains("Residual clause limit: clauses=64, proofs=64"),"custom tiny request cannot contaminate later default64 qualification");
                boolean refused=false;try{inspect(lease,null);}catch(IllegalArgumentException expected){refused=true;}require(refused,"invalid explicit policy cannot silently pick another budget");lease.requireCurrent();require(f.db.getRevision()==revision,"every read-only request leaves physical revision unchanged");
            }finally{f.root.release(child);}
            f.guard.install();
            if(n==4){require(Boolean.TRUE.equals(f.root.query(rule,null,false)),"later actual native write independently qualifies with default budget");require(f.db.getRevision()>revision,"native publication advances revision");}
            else{StorageLifecycleException failure=null;try{f.root.query(rule,null,false);}catch(StorageLifecycleException expected){failure=expected;}require(failure instanceof ContextGroundProductionCommitException&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&((ContextGroundProductionCommitException)failure).getFamilyEvaluation().reason.contains("Residual clause limit: clauses=64, proofs=64"),"native write retains own original default and fresh full refusal");require(f.db.getRevision()==revision,"read-only override never permits wider native write");}
        }
        System.out.println("CONTEXT_GROUND_REQUEST_LIMITS_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
