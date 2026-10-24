/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Required bounded actual private-witness admission; no expected-refusal waiver. */
public final class ContextPrivateWitnessAdmissionContractRunner {
    static int checks;static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static void one(boolean reverse,boolean conflict)throws Exception{
        Path dir=Files.createTempDirectory("private-witness-admission-");System.setProperty("user.home",dir.toString());ContextSevenPremiseNativeWriteRunner.create(dir,"A","!$w seed(w,1)&&flag(w,1);");ContextSevenPremiseNativeWriteRunner.create(dir,"B","!@w mid(w,1)->done(w,1);");
        try(ContextAutomaticPendingCommitRunner.Fixture f=new ContextAutomaticPendingCommitRunner.Fixture(dir,"X")){
            require(f.root.compile("!anchor(1); !@w seed(w,1)->mid(w,1);",null,false),"X has no private witness primary");for(String name:reverse?new String[]{"B","A"}:new String[]{"A","B"})ContextTrustedProductionCommitRunner.command(f,"ctx connect "+name+" trust own");f.db.saveConnections(f.root);long revision=f.db.getRevision();String before=ContextTrustedProductionCommitRunner.inventory(f).fingerprint;String candidate=conflict?"!@w done(w,1)->~flag(w,1);":"!@w done(w,1)->out(w,1);";
            f.guard.install();StorageLifecycleException failure=null;try{require(Boolean.TRUE.equals(f.root.query(candidate,null,false)),"safe private-witness candidate must publish");}catch(StorageLifecycleException e){failure=e;}finally{f.guard.restore();}
            if(failure!=null)System.out.println("PRIVATE_ADMISSION_REFUSAL code="+failure.getErrorCode()+" reason="+failure);
            if(conflict){require(failure instanceof ContextWitnessProductionCommitException&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT&&!((ContextWitnessProductionCommitException)failure).getFamilyEvaluation().refutations.isEmpty(),"negative flag must give complete original-source witness refutation");require(f.db.getRevision()==revision&&ContextTrustedProductionCommitRunner.inventory(f).fingerprint.equals(before),"refusal exact source purity");}else require(failure==null&&f.db.getRevision()>revision,"safe private-witness admission cannot be replaced with INCOMPLETE");
        }
    }
    public static void main(String[] args){try{for(boolean r:new boolean[]{false,true})for(boolean c:new boolean[]{false,true})one(r,c);System.out.println("CONTEXT_PRIVATE_WITNESS_ADMISSION_CONTRACT_PASS checks="+checks);System.exit(0);}catch(Throwable e){System.out.println("CONTEXT_PRIVATE_WITNESS_ADMISSION_CONTRACT_FAIL checks="+checks);e.printStackTrace();System.exit(1);}}
}
