/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.ContextProductionCommitException;
import org.kanger.ContextUniversalFamilyInspection.Status;

/** Unsupported negative quantifier stays INCOMPLETE; its prenex neighbour has a real conflict. */
public final class ContextAntecedentQuantifierWriteRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static void one(boolean negative,boolean reverse,boolean nativePath,boolean nested)throws Exception{
        Path dir=Files.createTempDirectory("antecedent-quantifier-write-");System.setProperty("user.home",dir.toString());String s=negative?"~":"",op=negative?"":"~";
        ContextSevenPremiseNativeWriteRunner.create(dir,"Deny","!"+op+"q();");
        ContextSevenPremiseNativeWriteRunner.create(dir,"Other","!other(2);");
        String text=nested?"!(@x "+s+"p(x))->"+s+"q();":"!@x "+s+"p(x)->"+s+"q();";
        Set<String> origins;List<RevisionRef> pins;long revision;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile("!"+s+"p(1); !"+op+"p(2);",null,false),"one true and one false signed-p instance");
            f.db.connectContext(reverse?"Other":"Deny");f.db.connectContext(reverse?"Deny":"Other");f.db.saveConnections(f.root);
            ContextProductionInventory before=ContextSevenPremiseNativeWriteRunner.inventory(f);origins=ContextSevenPremiseNativeWriteRunner.origins(before);pins=ContextSevenPremiseNativeWriteRunner.pins(f);revision=f.db.getRevision();int rules=f.root.getRules().size();
            ContextUniversalCandidate.Evaluation e=f.db.qualifyUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK);
            require(e.status==(nested?Status.INCOMPLETE:Status.COLLISION),"scope-sensitive diagnostic: "+e.status+" / "+e.reason);
            if(nested)require(e.qualification==null&&e.reason.contains("conditional witness"),"unsupported staged declaration never yields proof or model");
            else require(e.qualification.collision!=null&&e.qualification.certificate==null&&!e.qualification.collision.oppositions.isEmpty(),"prenex has complete concrete opposition");
            if(nativePath){ContextPendingNativeWrite attempt=f.db.beginUniversalWrite(f.root,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK);try{
                ContextProductionCommitException failure=null;try{f.root.query(text,null,false);}catch(ContextProductionCommitException caught){failure=caught;}
                require(failure!=null&&failure.getErrorCode()==(nested?StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE:StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT),"actual native settlement retains scope-sensitive typed refusal");
                if(!nested)require(failure.getFamilyAudit()!=null&&failure.getFamilyAudit().verified&&failure.getFamilyAudit().verdict!=e.qualification,"native conflict freshly audited");
            }finally{f.db.endNativeWrite(attempt);}}
            else{DB.UniversalProductionWriteResult result=f.db.writeUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK,false);
                require(result.status==(nested?DB.ProductionWriteStatus.INCOMPLETE:DB.ProductionWriteStatus.COLLISION),"public writer preserves scope verdict");
                require(result.before.equals(result.after)&&result.trial!=e,"fresh public pretrial has unchanged physical references");
                if(!nested)require(result.audit!=null&&result.audit.verified&&result.audit.evaluation==result.trial,"public conflict freshly audited");
            }
            ContextProductionInventory after=ContextSevenPremiseNativeWriteRunner.inventory(f);
            require(f.db.getRevision()==revision&&f.root.getRules().size()==rules&&after.fingerprint.equals(before.fingerprint),"refusal preserves revision, rules and exact source fingerprint");
            require(ContextSevenPremiseNativeWriteRunner.origins(after).equals(origins)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"refusal preserves all whole origins and ordered pins");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.db.getRevision()>=revision&&ContextSevenPremiseNativeWriteRunner.origins(ContextSevenPremiseNativeWriteRunner.inventory(f)).equals(origins)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"ordinary durable reopen contains exactly unchanged local sources and pins");
        }
    }
    public static void main(String[] args){try{for(boolean n:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})for(boolean p:new boolean[]{false,true})for(boolean q:new boolean[]{false,true})one(n,r,p,q);System.out.println("CONTEXT_ANTECEDENT_QUANTIFIER_WRITE_PASS checks="+(checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
