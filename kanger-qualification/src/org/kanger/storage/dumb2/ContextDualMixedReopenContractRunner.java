/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextUniversalFamilyInspection.Status;

/** A certified dual mixed rule with disjoint b/c instances must reopen normally. */
public final class ContextDualMixedReopenContractRunner {
    static int checks;static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("dual-mixed-reopen-contract-");System.setProperty("user.home",dir.toString());System.out.println("FIXTURE "+dir);ContextSevenPremiseNativeWriteRunner.create(dir,"Deny","!~q();");Set<String> origins;List<RevisionRef> pins;long revision;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile("!b(1); !c(2);",null,false),"disjoint original paired instances");f.db.connectContext("Deny");f.db.saveConnections(f.root);DB.UniversalProductionWriteResult result=f.db.writeUniversalProductionCandidate(f.root,"!($x (a(x)||(b(x)&&c(x))))->q();",1000,16,16,16,65536,1000000,64000000,500000,64000000,64000000,false);
            require(result.status==DB.ProductionWriteStatus.PUBLISHED&&result.trial.status==Status.CONSISTENT&&result.audit.verified,"complete consistent qualification and verified actual publication");origins=ContextSevenPremiseNativeWriteRunner.origins(ContextSevenPremiseNativeWriteRunner.inventory(f));pins=ContextSevenPremiseNativeWriteRunner.pins(f);revision=f.db.getRevision();require(origins.size()==3,"only original two facts and whole candidate persisted");System.out.println("PUBLISHED exactSources="+origins+" revision="+revision);
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){require(f.db.getRevision()>=revision&&ContextSevenPremiseNativeWriteRunner.origins(ContextSevenPremiseNativeWriteRunner.inventory(f)).equals(origins)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"ordinary reopen preserves exactly certified compatible source theory");}
        System.out.println("CONTEXT_DUAL_MIXED_REOPEN_CONTRACT_PASS checks="+(checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
