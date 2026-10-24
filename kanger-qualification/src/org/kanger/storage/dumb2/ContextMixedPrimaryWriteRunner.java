/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Actual whole fact/rule authoring, single Source proofs, private initialization and reopen. */
public final class ContextMixedPrimaryWriteRunner {
    static int checks;static final String BUNDLE="!$w seed(w,1)&&flag(w,1);",MIXED="!@w gate(1)&&(seed(w,1)&&gate(1)->mid(w,1));",WARM="!@w seed(w,1)->warm(w,1);",DONE="!@w mid(w,1)->done(w,1);",OUT="!@w done(w,1)->out(w,1);",NEG="!@w done(w,1)->~flag(w,1);";
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static void command(ContextAutomaticPendingCommitRunner.Fixture f,String text)throws Exception{ContextTrustedProductionCommitRunner.command(f,text);}
    static ContextProductionInventory inventory(ContextAutomaticPendingCommitRunner.Fixture f)throws Exception{return ContextTrustedProductionCommitRunner.inventory(f);}
    static void one(boolean local,boolean grouped,boolean reverse,boolean negative)throws Exception{
        Path dir=Files.createTempDirectory("mixed-primary-write-");System.setProperty("user.home",dir.toString());boolean initialized=!local&&reverse;ContextSevenPremiseNativeWriteRunner.create(dir,"A",local?WARM:(initialized?"!private(1);":MIXED));ContextSevenPremiseNativeWriteRunner.create(dir,"B",DONE);Set<String> settled;List<RevisionRef> pins=new ArrayList<>();Map<UUID,String> foreign;long after;
        try(ContextAutomaticPendingCommitRunner.Fixture f=new ContextAutomaticPendingCommitRunner.Fixture(dir,"X")){
            require(f.root.compile(BUNDLE+" "+(local?MIXED:WARM),null,false),"whole mixed X baseline or private witness anchor");for(String name:reverse?new String[]{"B","A"}:new String[]{"A","B"})command(f,"ctx connect "+name+(grouped?" trust own":""));if(initialized)command(f,"ctx ask A "+MIXED);f.db.saveConnections(f.root);ContextProductionInventory before=inventory(f);long revision=f.db.getRevision();foreign=ContextTrustedProductionCommitRunner.foreign(f);Map<UUID,ContextProductionInventory> members=new HashMap<>();for(ContextConnection c:f.db.dmzTopology()){pins.add(c.getTarget());members.put(c.getTarget().getContextId(),ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),1000));}String candidate=negative?NEG:OUT;
            f.guard.install();StorageLifecycleException failure=null;try{require(Boolean.TRUE.equals(f.root.query(candidate,null,false)),"safe mixed-source publication");}catch(StorageLifecycleException e){failure=e;}finally{f.guard.restore();}
            require((failure!=null)==negative,"supported whole mixed declaration cannot fall back to INCOMPLETE");
            if(negative){require(failure instanceof ContextWitnessProductionCommitException&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"complete typed mixed-source witness refutation");ContextProductionFamilyAssembly.Evaluation e=((ContextWitnessProductionCommitException)failure).getFamilyEvaluation();require(!e.refutations.isEmpty()&&e.barrier>0,"derived original-source opposition");for(ContextProductionFamilyAssembly.Refutation r:e.refutations){Set<Source> expected=new HashSet<>();expected.add(r.producer);for(ContextProductionInventory.Row row:before.primary)if(row.origin.equals(BUNDLE)||row.origin.equals(MIXED))expected.add(new Source(before.context,revision,row.nativeRule,true,r.producer.view));for(ContextProductionInventory member:members.values())for(ContextProductionInventory.Row row:member.primary)if(row.origin.equals(MIXED)||row.origin.equals(DONE))expected.add(new Source(member.context,member.revision,row.nativeRule,false,"inventory:"+member.fingerprint));for(Support p:r.proofs)require(p.depth==3&&p.sources.size()==4&&p.sources.equals(expected),"mixed fact and implication are one original Source, exact four-source depth3 proof");}require(f.db.getRevision()==revision&&inventory(f).fingerprint.equals(before.fingerprint),"refusal preserves exact source/revision");}
            else require(f.db.getRevision()>revision,"safe whole-source publication advances revision");
            settled=ContextSevenPremiseNativeWriteRunner.origins(inventory(f));Set<String> expected=new HashSet<>(ContextSevenPremiseNativeWriteRunner.origins(before));if(!negative)expected.add(candidate);require(settled.equals(expected),"only whole candidate published; no synthetic gate clause/rule or private derived primary");require(ContextTrustedProductionCommitRunner.foreign(f).equals(foreign),"original initialized member views unchanged");after=f.db.getRevision();
        }
        try(ContextAutomaticPendingCommitRunner.Fixture f=new ContextAutomaticPendingCommitRunner.Fixture(dir,"X")){require(f.db.getRevision()>=after&&ContextSevenPremiseNativeWriteRunner.origins(inventory(f)).equals(settled),"ordinary reopen preserves exact whole primaries");List<RevisionRef> actual=new ArrayList<>();for(ContextConnection c:f.db.dmzTopology())actual.add(c.getTarget());require(actual.equals(pins)&&ContextTrustedProductionCommitRunner.foreign(f).equals(foreign),"reopen preserves pins/order and initialized mixed-source views");}
    }
    public static void main(String[] args){try{for(boolean l:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})one(l,g,r,n);System.out.println("CONTEXT_MIXED_PRIMARY_WRITE_PASS checks="+(checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
