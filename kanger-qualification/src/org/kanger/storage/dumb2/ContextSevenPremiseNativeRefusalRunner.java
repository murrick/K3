/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextOrdinaryResidualRounds.LedgerLimits;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;

/** Complete cross-cut refutation at both public pretrial and actual seven-premise native ingress. */
public final class ContextSevenPremiseNativeRefusalRunner {
    static int checks;static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static void collision(ContextGroundClauseFamily.Evaluation e){
        require(e!=null&&e.status==ContextGroundClauseFamily.Status.COLLISION&&e.certificate==null&&e.rounds==2&&!e.collisions.isEmpty(),"complete barrier2 sourced collision, no consistency certificate: "+(e==null?"null":e.reason));
        require(e.collisions.stream().anyMatch(c->{Set<Source> all=new HashSet<>(c.leftProof.sources);all.addAll(c.rightProof.sources);return all.size()==9&&all.stream().filter(s->s.localX).count()==7&&all.stream().filter(s->!s.localX).map(s->s.context).distinct().count()==2&&Math.max(c.leftProof.depth,c.rightProof.depth)>=1;}),"refutation retains every X source and both independent private cuts with nonzero depth");
    }
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("seven-native-refusal-");System.setProperty("user.home",dir.toString());ContextSevenPremiseNativeWriteRunner.create(dir,"A","!q(1);");ContextSevenPremiseNativeWriteRunner.create(dir,"Deny","!~out(1);");
        for(boolean reverse:new boolean[]{false,true}){
            String name="Refuse"+reverse;Set<String> initial;long revision;List<RevisionRef> pinned;
            try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){
                ContextSevenPremiseNativeWriteRunner.prepare(f,reverse,"Deny");revision=f.db.getRevision();pinned=ContextSevenPremiseNativeWriteRunner.pins(f);ContextProductionInventory baseline=ContextSevenPremiseNativeWriteRunner.inventory(f);initial=ContextSevenPremiseNativeWriteRunner.origins(baseline);int rows=f.root.getRules().size();LedgerLimits limits=new LedgerLimits(256,1024);
                DB.GroundProductionWriteResult trial=ContextSevenPremiseNativeWriteRunner.write(f,limits);require(trial.status==DB.ProductionWriteStatus.COLLISION&&trial.before.getRevision()==revision&&trial.after.getRevision()==revision,"public explicit authoring refuses before any settlement");collision(trial.trial.qualification);
                ContextPendingNativeWrite attempt=f.db.beginGroundWrite(f.root,1000,16,16,16,ContextSevenPremiseNativeWriteRunner.WORK,500000,100000,1000000,limits);
                try{
                    ContextGroundProductionCommitException failure=null;try{f.root.query(ContextSevenPremiseNativeWriteRunner.rule(),null,false);}catch(ContextGroundProductionCommitException expected){failure=expected;}
                    require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"fresh actual pending child refuses typed conflict independently of historical trial");collision(failure.getFamilyEvaluation());
                    Mind child=new Mind(f.root);try{require(child.compile(ContextSevenPremiseNativeWriteRunner.rule(),null,false),"new child cannot revive refused attempt");boolean refused=false;try{attempt.validate(child);}catch(ContextGroundProductionCommitException expected){refused=expected.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&expected.getCause() instanceof IllegalStateException&&expected.getCause().getMessage().contains("cannot be retried");}require(refused,"failed native scope cannot be reused with a replacement child");}finally{f.root.release(child);}
                }finally{f.db.endNativeWrite(attempt);}
                require(f.db.getRevision()==revision&&f.root.getRules().size()==rows&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(baseline.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"refusals leave physical revision, complete inventory and parent rules unchanged");
            }
            try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(inv.primary.size()==6&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(initial)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"reopen retains exactly six original premises, no refused candidate or product");}
        }
        System.out.println("CONTEXT_SEVEN_PREMISE_NATIVE_REFUSAL_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
