/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.command.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;

/** Real shared-cut admission, immutable members, exact original-source collision proofs. */
public final class ContextTrustedProductionCommitRunner {
    static int checks;static final int WORK=64000000;
    static final Map<UUID,ContextProductionInventory> expectedMembers=new HashMap<>();
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static void command(ContextAutomaticPendingCommitRunner.Fixture f,String text)throws Exception{new CanonicalCommandProcessor().execute(new CommandParser().parse(text),f.user);}
    static ContextProductionInventory inventory(ContextAutomaticPendingCommitRunner.Fixture f)throws Exception{return ContextProductionInventory.inspect(f.root,f.db.getContextId(),f.db.getRevision(),true,Collections.emptyList(),1000);}
    static Map<UUID,String> foreign(ContextAutomaticPendingCommitRunner.Fixture f)throws Exception{Map<UUID,String> r=new HashMap<>();for(ContextConnection c:f.db.dmzTopology())r.put(c.getTarget().getContextId(),ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),1000).fingerprint);return r;}
    static Set<String> names(Set<Source> support,Map<UUID,String> ids){Set<String> r=new HashSet<>();for(Source s:support){require(s.localX==ids.get(s.context).equals("X"),"exact local/foreign role");require(r.add(ids.get(s.context)),"one whole primary per required member in fixture");if(!s.localX){ContextProductionInventory inv=expectedMembers.get(s.context);require(inv!=null&&s.revision==inv.revision&&s.view.equals("inventory:"+inv.fingerprint)&&inv.primary.stream().anyMatch(row->row.nativeRule==s.rule),"exact original member pin/view/whole primary ID");}}return r;}
    static void proof(ContextPendingProductionSnapshotSession.Decision d,Map<UUID,String> ids,Set<String> expected,boolean collision,boolean ground,int cuts){
        require(d.engine==(ground?ContextPendingProductionSnapshotSession.Engine.GROUND:ContextPendingProductionSnapshotSession.Engine.UNIVERSAL),"complete grouped family engine selection");
        if(ground){require(d.ground.status==(collision?ContextGroundClauseFamily.Status.COLLISION:ContextGroundClauseFamily.Status.GROUND_FIXED_POINT),"ground shared verdict: "+d.ground.reason);if(collision)for(ContextGroundProductionRounds.Collision p:d.ground.collisions){Set<Source> all=new HashSet<>(p.leftProof.sources);all.addAll(p.rightProof.sources);require(names(all,ids).equals(expected)&&all.size()==expected.size(),"all and only whole original ground Sources, including every trusted member");}else require(d.ground.certificate.models.size()==cuts,"all grouped ground cuts modeled");}
        else{require(d.universal.inspection.status==(collision?ContextUniversalFamilyInspection.Status.COLLISION:ContextUniversalFamilyInspection.Status.CONSISTENT)&&d.universal.audit.verified,"independently audited universal shared verdict: "+d.universal.inspection.reason);if(collision)for(ContextUniversalUnitProof.Proof p:d.universal.audit.oppositions)require(names(p.support.sources,ids).equals(expected)&&p.support.sources.size()==expected.size(),"all and only original universal Sources, no routing-member relabel");else require(d.universal.inspection.certificate.cuts.size()==cuts,"all grouped universal cuts modeled");}
    }
    static void one(boolean ground,boolean reverse,int mode)throws Exception{
        Path dir=Files.createTempDirectory("trusted-production-write-");System.setProperty("user.home",dir.toString());String prefix=ground?"!":"!@x ",arg=ground?"1":"x",rule=prefix+"q("+arg+")->r("+arg+");";boolean collision=mode==1||mode==2;String tr=prefix+"p("+arg+")->q("+arg+");",hr=prefix+"r("+arg+")->s("+arg+");";
        ContextSevenPremiseNativeWriteRunner.create(dir,"TR",tr);ContextSevenPremiseNativeWriteRunner.create(dir,"TF","!p(1);");ContextSevenPremiseNativeWriteRunner.create(dir,"Other",mode==0?"!~q(1);":"!~r(1);");if(mode==2||mode==3){ContextSevenPremiseNativeWriteRunner.create(dir,"HR",hr);ContextSevenPremiseNativeWriteRunner.create(dir,"HF",mode==2?"!~s(1);":"!~s(2);");}
        Set<String> settled;List<RevisionRef> pins;long after;
        try(ContextAutomaticPendingCommitRunner.Fixture f=new ContextAutomaticPendingCommitRunner.Fixture(dir,"X")){
            require(f.root.compile("!anchor(1);",null,false),"original X only");List<String> links=new ArrayList<>(Arrays.asList("TR trust own","TF trust own"));if(mode==2||mode==3)links.addAll(Arrays.asList("HR trust second","HF trust second"));else links.add("Other");if(reverse)Collections.reverse(links);for(String name:links)command(f,"ctx connect "+name);
            if(mode==4){command(f,"ctx ask TF -p(1);");command(f,"ctx ask TF !p(2);");}
            f.db.saveConnections(f.root);pins=new ArrayList<>();Map<UUID,String> ids=new HashMap<>();ids.put(f.db.getContextId(),"X");for(ContextConnection c:f.db.dmzTopology()){pins.add(c.getTarget());ids.put(c.getTarget().getContextId(),Paths.get(c.getTargetLocation().toString()).getFileName().toString());}
            expectedMembers.clear();for(ContextConnection c:f.db.dmzTopology())expectedMembers.put(c.getTarget().getContextId(),ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),1000));
            Map<UUID,String> beforeForeign=foreign(f);ContextProductionInventory before=inventory(f);long revision=f.db.getRevision();Set<String> expected=new HashSet<>(Arrays.asList("X","TR","TF"));if(mode==2)expected.addAll(Arrays.asList("HR","HF"));else expected.add("Other");
            ContextPendingProductionSnapshotSession.Decision diagnostic;
            Mind child=new Mind(f.root);require(child.compile(rule,null,false),"actual whole candidate child");try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)){ContextPendingProductionSnapshotSession.Decision d=lease.inspectAutomatic(16,16,16,65536,1000000,WORK,500000,WORK,WORK,100000);diagnostic=d;proof(d,ids,expected,collision,ground,3);lease.requireCurrent();}finally{f.root.release(child);}
            f.guard.install();StorageLifecycleException failure=null;try{require(Boolean.TRUE.equals(f.root.query(rule,null,false)),"actual safe shared native publication");}catch(StorageLifecycleException e){failure=e;}finally{f.guard.restore();}
            require((failure!=null)==collision,"actual fresh admission matches detached trial");if(collision){require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"typed actual shared conflict");if(ground){require(failure instanceof ContextGroundProductionCommitException&&((ContextGroundProductionCommitException)failure).getFamilyEvaluation()!=diagnostic.ground,"actual fresh ground family evidence");for(ContextGroundProductionRounds.Collision p:((ContextGroundProductionCommitException)failure).getFamilyEvaluation().collisions){Set<Source> all=new HashSet<>(p.leftProof.sources);all.addAll(p.rightProof.sources);require(names(all,ids).equals(expected),"fresh actual ground proof member attribution");}}else{require(failure instanceof ContextProductionCommitException&&((ContextProductionCommitException)failure).getFamilyAudit().verified&&((ContextProductionCommitException)failure).getFamilyAudit()!=diagnostic.universal.audit,"actual fresh universal audit");for(ContextUniversalUnitProof.Proof p:((ContextProductionCommitException)failure).getFamilyAudit().oppositions)require(names(p.support.sources,ids).equals(expected),"fresh actual universal proof member attribution");}require(f.db.getRevision()==revision&&inventory(f).fingerprint.equals(before.fingerprint),"shared refusal exact source/revision purity");}
            else require(f.db.getRevision()>revision,"safe shared publication advances revision");settled=ContextSevenPremiseNativeWriteRunner.origins(inventory(f));require(settled.equals(new HashSet<>(collision?Arrays.asList("!anchor(1);"):Arrays.asList("!anchor(1);",rule))),"no raw/derived shared primary import");require(beforeForeign.equals(foreign(f)),"all initialized member inventories unchanged");after=f.db.getRevision();
        }
        try(ContextAutomaticPendingCommitRunner.Fixture f=new ContextAutomaticPendingCommitRunner.Fixture(dir,"X")){require(f.db.getRevision()>=after&&ContextSevenPremiseNativeWriteRunner.origins(inventory(f)).equals(settled),"ordinary durable reopen exact origins");List<RevisionRef> actual=new ArrayList<>();for(ContextConnection c:f.db.dmzTopology())actual.add(c.getTarget());require(actual.equals(pins),"ordinary durable reopen exact ordered member pins");}
    }
    static void boundary(boolean witness)throws Exception{
        Path dir=Files.createTempDirectory("trusted-production-boundary-");System.setProperty("user.home",dir.toString());ContextSevenPremiseNativeWriteRunner.create(dir,"TR","!@x p(x)->q(x);");ContextSevenPremiseNativeWriteRunner.create(dir,"TF","!p(1);");if(!witness)ContextSevenPremiseNativeWriteRunner.create(dir,"Bad","!@x fun(x)->next(x+1);");
        try(ContextAutomaticPendingCommitRunner.Fixture f=new ContextAutomaticPendingCommitRunner.Fixture(dir,"X")){
            require(f.root.compile(witness?"!anchor(1); !$w seed(w,1);":"!anchor(1);",null,false),"boundary original X");command(f,"ctx connect TR trust own");command(f,"ctx connect TF trust own");if(!witness)command(f,"ctx connect Bad trust own");f.db.saveConnections(f.root);long rev=f.db.getRevision();String before=inventory(f).fingerprint;
            f.guard.install();StorageLifecycleException failure=null;try{f.root.query("!unrelated(1);",null,false);}catch(StorageLifecycleException e){failure=e;}finally{f.guard.restore();}
            require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&failure.getCollisions().isEmpty(),"unsupported whole member/witness group remains incomplete, never prefix permission or fake conflict");require(f.db.getRevision()==rev&&inventory(f).fingerprint.equals(before),"bounded group refusal keeps exact physical source");
        }
    }
    static void staleInitialization()throws Exception{
        Path dir=Files.createTempDirectory("trusted-pending-init-");System.setProperty("user.home",dir.toString());ContextSevenPremiseNativeWriteRunner.create(dir,"TR","!@x p(x)->q(x);");ContextSevenPremiseNativeWriteRunner.create(dir,"TF","!p(1);");
        try(ContextAutomaticPendingCommitRunner.Fixture f=new ContextAutomaticPendingCommitRunner.Fixture(dir,"X")){
            require(f.root.compile("!anchor(1);",null,false),"lease baseline");command(f,"ctx connect TR trust own");command(f,"ctx connect TF trust own");f.db.saveConnections(f.root);long revision=f.db.getRevision();command(f,"transaction start");Mind child=(Mind)f.user.getCurrentMind();require(child.query("!safe(1);",null,false),"pending actual user child");
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)){
                command(f,"ctx ask TF !extra(1);");boolean stale=false;try{lease.requireCurrent();}catch(IllegalStateException expected){stale=expected.getMessage().contains("topology");}require(stale,"changed initialized member invalidates exact grouped pending lease");
            }
            command(f,"transaction rollback");require(f.user.getCurrentMind()==f.root&&f.db.getRevision()==revision&&ContextSevenPremiseNativeWriteRunner.origins(inventory(f)).equals(new HashSet<>(Arrays.asList("!anchor(1);"))),"rollback restores source without proposed primary or private initialization leak");require(f.db.dmzTopology().stream().allMatch(c->c.getInitialization().isEmpty()),"exact original member initialization restored");
        }
    }
    public static void main(String[] args){try{for(boolean g:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})for(int m=0;m<5;m++)one(g,r,m);boundary(false);boundary(true);staleInitialization();System.out.println("CONTEXT_TRUSTED_PRODUCTION_COMMIT_PASS checks="+(checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
