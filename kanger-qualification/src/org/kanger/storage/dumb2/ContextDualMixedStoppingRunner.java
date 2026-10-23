/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextUniversalFamilyInspection.Status;

/** Complete conflict evidence does not promise enumeration of proofs at later local depths. */
public final class ContextDualMixedStoppingRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static void one(boolean negative,int mode)throws Exception{
        Path dir=Files.createTempDirectory("dual-mixed-stopping-");System.setProperty("user.home",dir.toString());String s=negative?"~":"",op=negative?"":"~",rule="!(($x "+s+"a(x))||(($y "+s+"b(y))&&($z "+s+"c(z))))->"+s+"q();";ContextSevenPremiseNativeWriteRunner.create(dir,"Deny","!"+op+"q();");
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            String base="!base(2);";for(String fact:ContextDualMixedExistential.facts(mode,s))base+=" "+fact;require(f.root.compile(base,null,false),"isolated solo/paired fixture");f.db.connectContext("Deny");f.db.saveConnections(f.root);ContextProductionInventory before=ContextSevenPremiseNativeWriteRunner.inventory(f);long revision=f.db.getRevision();ContextUniversalCandidate.Evaluation e=f.db.qualifyUniversalProductionCandidate(f.root,rule,1000,16,16,16,65536,1000000,WORK,500000,WORK);
            require(e.status==Status.COLLISION&&e.qualification.completedBarriers==1&&e.qualification.collision.receipts.size()==2&&e.qualification.certificate==null,"all cuts checked before first terminal collision barrier, no stable model claim");Snapshot deny=e.artifact.foreign.get(0);ContextUniversalUnitLedger.Opened opened=ContextUniversalUnitLedger.openPrimary(e.artifact.staged,deny,WORK);require(opened.opened,"exact primary-only historical ledger");ContextUniversalUnitLedger ledger=opened.ledger;boolean solo=mode>=6;int terminal=solo?2:3;Set<Source> expected=new HashSet<>();expected.add(deny.primary.get(0).source);for(Entry row:e.artifact.staged.primary)if(row.statement.equals(rule)||(solo?row.statement.equals("!"+s+"a(1);"):row.statement.equals("!"+s+"b(1);")||row.statement.equals("!"+s+"c("+(mode==5?2:1)+");")))expected.add(row.source);
            for(int sweep=1;sweep<=terminal;sweep++){
                int rows=ledger.rows.size();ContextUniversalUnitLedger.Step step=ContextUniversalUnitLedger.stepFactored(ledger,16,WORK);require(step.input==ledger&&ledger.rows.size()==rows&&step.includesFactoring,"one immutable-input audited local sweep");
                if(sweep<terminal){require(step.status==ContextUniversalUnitLedger.Status.ADVANCED&&step.next!=null&&step.changed&&step.oppositions.isEmpty(),"no partial opposition before required depth");ledger=step.next;}
                else{require(step.status==ContextUniversalUnitLedger.Status.OPPOSITION&&step.next==null&&step.oppositions.size()==(solo?2:3),"terminal opposition seals exact evidence without a later frontier");for(ContextUniversalUnitProof.Proof proof:step.oppositions)require(proof.contradiction&&proof.dependsOnX&&proof.support.depth==terminal&&proof.support.sources.equals(expected),"solo stops at depth2; isolated pair reaches depth3 with exact necessary whole support");}
            }
            require(e.qualification.collision.oppositions.size()==(solo?2:3),"family result agrees with exact terminal local sweep");for(ContextUniversalUnitProof.Proof proof:e.qualification.collision.oppositions)require(proof.support.depth==terminal&&proof.support.sources.equals(expected),"family retains complete terminal evidence, never an unfinished later route");require(f.db.getRevision()==revision&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(before.fingerprint),"detached trace and diagnostic preserve physical source");
        }
    }
    public static void main(String[] args){try{for(boolean n:new boolean[]{false,true})for(int m=4;m<8;m++)one(n,m);System.out.println("CONTEXT_DUAL_MIXED_STOPPING_PASS checks="+(checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
