/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Identical trusted-member facts remain independent whole-source proof alternatives. */
public final class ContextTrustedDuplicateSupportRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static void alternatives(boolean ground,ContextGroundClauseFamily.Evaluation g,ContextUniversalFamilyAudit.Audit u,Map<UUID,String> ids){
        List<Set<Source>> proofs=new ArrayList<>();if(ground){require(g.status==ContextGroundClauseFamily.Status.COLLISION&&!g.collisions.isEmpty(),"complete ground collision");for(ContextGroundProductionRounds.Collision p:g.collisions){Set<Source> support=new HashSet<>(p.leftProof.sources);support.addAll(p.rightProof.sources);proofs.add(support);}}
        else{require(u.verified&&u.verdict.status==ContextUniversalFamilyInspection.Status.COLLISION&&!u.oppositions.isEmpty(),"independently audited universal collision");for(ContextUniversalUnitProof.Proof p:u.oppositions)proofs.add(p.support.sources);}
        Set<Set<String>> expected=new HashSet<>();expected.add(new HashSet<>(Arrays.asList("X","TR","TF","Other")));expected.add(new HashSet<>(Arrays.asList("X","TR","TA","Other")));Set<Set<String>> actual=new HashSet<>();for(Set<Source> proof:proofs){Set<String> names=ContextTrustedProductionCommitRunner.names(proof,ids);require(proof.size()==4&&expected.contains(names),"only one of two independent fact Sources per minimal proof, no merged-member label or both-fact superset");actual.add(names);}require(actual.equals(expected),"both same-barrier fact alternatives survive identical native semantics");
    }
    static void one(boolean ground,boolean reverse)throws Exception{
        Path dir=Files.createTempDirectory("trusted-duplicate-support-");System.setProperty("user.home",dir.toString());String prefix=ground?"!":"!@x ",arg=ground?"1":"x",tr=prefix+"p("+arg+")->q("+arg+");",candidate=prefix+"q("+arg+")->r("+arg+");";
        ContextSevenPremiseNativeWriteRunner.create(dir,"TR",tr);ContextSevenPremiseNativeWriteRunner.create(dir,"TF","!p(1);");ContextSevenPremiseNativeWriteRunner.create(dir,"TA","!p(1);");ContextSevenPremiseNativeWriteRunner.create(dir,"Other","!~r(1);");
        try(ContextAutomaticPendingCommitRunner.Fixture f=new ContextAutomaticPendingCommitRunner.Fixture(dir,"X")){
            require(f.root.compile("!anchor(1);",null,false),"original X");List<String> links=new ArrayList<>(Arrays.asList("TR trust own","TF trust own","TA trust own","Other"));if(reverse)Collections.reverse(links);for(String link:links)ContextTrustedProductionCommitRunner.command(f,"ctx connect "+link);f.db.saveConnections(f.root);Map<UUID,String> ids=new HashMap<>();ids.put(f.db.getContextId(),"X");ContextTrustedProductionCommitRunner.expectedMembers.clear();for(ContextConnection c:f.db.dmzTopology()){ids.put(c.getTarget().getContextId(),c.getTargetLocation().getFileName().toString());ContextTrustedProductionCommitRunner.expectedMembers.put(c.getTarget().getContextId(),ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),1000));}
            long before=f.db.getRevision();String fingerprint=ContextTrustedProductionCommitRunner.inventory(f).fingerprint;Map<UUID,String> foreign=ContextTrustedProductionCommitRunner.foreign(f);
            Mind child=new Mind(f.root);require(child.compile(candidate,null,false),"whole candidate child");try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)){ContextPendingProductionSnapshotSession.Decision d=lease.inspectAutomatic(16,16,16,65536,1000000,WORK,500000,WORK,WORK,100000);alternatives(ground,d.ground,ground?null:d.universal.audit,ids);lease.requireCurrent();}finally{f.root.release(child);}
            f.guard.install();StorageLifecycleException failure=null;try{f.root.query(candidate,null,false);}catch(StorageLifecycleException e){failure=e;}finally{f.guard.restore();}require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"actual duplicate-support admission refuses");if(ground){require(failure instanceof ContextGroundProductionCommitException,"actual ground engine");alternatives(true,((ContextGroundProductionCommitException)failure).getFamilyEvaluation(),null,ids);}else{require(failure instanceof ContextProductionCommitException,"actual universal engine");alternatives(false,null,((ContextProductionCommitException)failure).getFamilyAudit(),ids);}
            require(f.db.getRevision()==before&&ContextTrustedProductionCommitRunner.inventory(f).fingerprint.equals(fingerprint)&&ContextTrustedProductionCommitRunner.foreign(f).equals(foreign),"actual refusal preserves exact source and all original members");
        }
    }
    public static void main(String[] args){try{for(boolean g:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})one(g,r);System.out.println("CONTEXT_TRUSTED_DUPLICATE_SUPPORT_PASS checks="+(checks+ContextTrustedProductionCommitRunner.checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
