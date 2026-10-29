/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.*;
import org.kanger.ContextNativeCandidateInspection.*;

/** Actual owner/currentness-guarded read-only diagnostic; no detached result is write authority. */
final class ContextNativeCandidateLeaseMatrix {
    static int checks;
    interface Attempt {void run()throws Exception;}
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static void reject(Attempt a,String why)throws Exception{boolean failed=false;try{a.run();}catch(Exception expected){failed=true;}require(failed,why);}
    static final class Fixture implements AutoCloseable {
        final User user=new User();final DB db=new DB();final Mind mind;
        Fixture(Path dir,String name)throws Exception{user.setDatabaseDir(dir.toString()+File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator-"+UUID.randomUUID())).toString());db.init(user);Mind root=new Mind(user);user.setCurrentMind(root);mind=(Mind)root.useStorage(name);user.setCurrentMind(mind);}
        public void close()throws Exception{if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    static RevisionRef create(Path dir,String name,String text)throws Exception{try(Fixture f=new Fixture(dir,name)){require(f.mind.compile(text,null,false),"stored fixture "+name);}try(ContextSnapshot s=ContextSnapshot.open(dir.resolve(name))){return new RevisionRef(s.getContextId(),s.getRevision());}}
    static Limits limits(){return new Limits(1000,8,16,64000000,500000,64000000,64000000,4096);}
    static final class State {
        final long revision;final String fingerprint;final int rules,logs,solutions,hypotheses;
        State(Fixture f)throws Exception{revision=f.db.getRevision();fingerprint=ContextProductionInventory.inspect(f.mind,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint;rules=f.mind.getRules().size();logs=f.mind.getLog().size();solutions=f.mind.getSolutions().size();hypotheses=f.mind.getHypothesis().size();}
        void unchanged(Fixture f)throws Exception{require(f.db.getRevision()==revision&&f.mind.getRules().size()==rules&&fingerprint.equals(ContextProductionInventory.inspect(f.mind,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"diagnostic preserves physical X revision and complete inventory");require(f.user.getCurrentMind()==f.mind&&f.mind.getLog().size()==logs&&f.mind.getSolutions().size()==solutions&&f.mind.getHypothesis().size()==hypotheses,"diagnostic preserves active caller and all presentation stores");}
    }
    static void stable(Result r){require(r.status==Status.STABLE_SUPPLIED&&r.candidate!=null&&r.oppositions.isEmpty(),r.phase+": "+r.reason);}
    static void no(Result r,String phase){require(r.status==Status.INCOMPLETE&&r.candidate==null&&r.oppositions.isEmpty()&&r.phase.equals(phase),r.phase+": "+r.reason);}
    static void positive()throws Exception{
        Path dir=Files.createTempDirectory("native-candidate-lease-");System.setProperty("user.home",dir.toString());RevisionRef a=create(dir,"A","!left(1); !p(1);"),b=create(dir,"B","!right(1); !~p(1);");
        try(Fixture x=new Fixture(dir,"X")){require(x.mind.compile("!anchor(1);",null,false),"X anchor");x.db.connectContext("A");x.db.connectContext("B");State before=new State(x);Limits l=limits();
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,l.rows)){ContextProductionSnapshotSession.Inventory inventory=lease.inventory();Result safe=lease.inspectNativeCandidate("!@u left(u)&&right(u)->q(u);",l);stable(safe);require(safe.baseContext.equals(x.db.getContextId())&&safe.baseRevision==before.revision&&safe.baseFingerprint.equals(inventory.x.fingerprint),"public scalar diagnostic binds actual owning DB X inventory");require(safe.foreignFingerprints.equals(Arrays.asList(inventory.foreign.get(0).fingerprint,inventory.foreign.get(1).fingerprint)),"actual initialized physical foreign views retained in order");require(inventory.foreign.get(0).revision==a.getRevision()&&inventory.foreign.get(1).revision==b.getRevision(),"actual immutable target pins, not latest guesses");before.unchanged(x);
                Result conflict=lease.inspectNativeCandidate("!@u left(u)&&right(u)->p(u);",l);require(conflict.status==Status.REFUTED&&conflict.candidate!=null&&!conflict.oppositions.isEmpty(),conflict.phase+": "+conflict.reason);for(Opposition o:conflict.oppositions){require(o.usesCandidate&&o.xRefuted&&o.originalSources.contains(conflict.candidate)&&o.originalSources.size()==4,"live captured candidate refutation retains exact four original physical Sources");require(o.originalSources.stream().filter(s->!s.localX).allMatch(s->s.context.equals(a.getContextId())||s.context.equals(b.getContextId())),"foreign proof attribution uses actual A/B identities");}before.unchanged(x);lease.requireCurrent();
                no(lease.inspectNativeCandidate("!left(1)||right(1);",l),"candidate");no(lease.inspectNativeCandidate("!@u left(u)&&right(u)->q(u);",new Limits(1000,8,16,l.preparation,l.events,l.work,1,l.capacity)),"candidate-audit");reject(()->lease.inspectNativeCandidate("!extra(1);",new Limits(999,8,16,l.preparation,l.events,l.work,l.audit,l.capacity)),"limits cannot replace actual lease row policy");before.unchanged(x);
                boolean immutable=false;try{safe.foreignFingerprints.clear();}catch(UnsupportedOperationException expected){immutable=true;}require(immutable,"returned initialized-view scalar metadata immutable");immutable=false;try{conflict.oppositions.get(0).originalSources.clear();}catch(UnsupportedOperationException expected){immutable=true;}require(immutable,"returned opposition provenance immutable");}
            stable(x.db.inspectNativeProductionCandidate(x.mind,"!@u left(u)&&right(u)->q(u);",l));before.unchanged(x);require(ContextProductionInventory.inspect(x.mind,x.db.getContextId(),x.db.getRevision(),true,Collections.emptyList(),1000).primary.stream().noneMatch(r->r.origin.contains("->q(")),"diagnostic never publishes the candidate primary");}
        create(dir,"PrivateA","!$u seed(u,1);");create(dir,"Consumer","!@u @v b(u,v); !@u @v r(u,v)&&need(v)->s(u,v); !need(2);");
        try(Fixture x=new Fixture(dir,"OriginX")){require(x.mind.compile("!$u seed(u,1);",null,false),"stored primary X Origin");x.db.connectContext("PrivateA");x.db.connectContext("Consumer");State before=new State(x);stable(x.db.inspectNativeProductionCandidate(x.mind,"!@u @v seed(u,1)&&b(u,v)->r(u,v);",limits()));before.unchanged(x);}
        System.out.println("NATIVE_CANDIDATE_LEASE_POSITIVE actualDbIdentity=true pinsAndPrivateViews=true primaryAndForeignOrigins=true currentnessBeforeAfter=true readOnlyStoresUnchanged=true");
    }
    static void freshness()throws Exception{
        Path dir=Files.createTempDirectory("native-candidate-freshness-");System.setProperty("user.home",dir.toString());RevisionRef old=create(dir,"A","!a(1);" );create(dir,"B","!b(1);");
        try(Fixture x=new Fixture(dir,"X")){require(x.mind.compile("!anchor(1);",null,false),"freshness X");x.db.connectContext("A");x.db.connectContext("B");Limits l=limits();String candidate="!@u trial(u);";Result historical;
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)){historical=lease.inspectNativeCandidate(candidate,l);stable(historical);RevisionRef newer=create(dir,"A","!later(8);");require(newer.getRevision()>old.getRevision(),"foreign HEAD really advanced");lease.requireCurrent();Result stillPinned=lease.inspectNativeCandidate(candidate,l);stable(stillPinned);require(stillPinned.foreignFingerprints.equals(historical.foreignFingerprints)&&lease.inventory().foreign.get(0).revision==old.getRevision(),"new foreign HEAD does NOT invalidate or leak into unchanged old pin");}
            ContextProductionSnapshotSession initialized=ContextProductionSnapshotSession.open(x.db,x.mind,1000);x.db.applyConnectionCommand(x.mind,"A","!private(4);");reject(()->initialized.inspectNativeCandidate(candidate,l),"private initialization invalidates diagnostic lease");initialized.close();Result fresh=x.db.inspectNativeProductionCandidate(x.mind,candidate,l);stable(fresh);require(!fresh.foreignFingerprints.get(0).equals(historical.foreignFingerprints.get(0)),"fresh diagnostic uses new initialized view, old scalar remains historical");
            try(ContextProductionSnapshotSession local=ContextProductionSnapshotSession.open(x.db,x.mind,1000)){require(x.mind.compile("!~trial(1);",null,false),"later actual X knowledge");reject(()->local.inspectNativeCandidate(candidate,l),"actual X publication invalidates old candidate lease");}
            require(historical.status==Status.STABLE_SUPPLIED&&x.db.inspectNativeProductionCandidate(x.mind,candidate,l).status!=Status.STABLE_SUPPLIED,"historical success cannot authorize candidate against later X");
            try(ContextProductionSnapshotSession topology=ContextProductionSnapshotSession.open(x.db,x.mind,1000)){x.db.disconnectContext("B");reject(()->topology.inspectNativeCandidate("!extra(2);",l),"source removal invalidates exact diagnostic family");}
            ContextProductionSnapshotSession closed=ContextProductionSnapshotSession.open(x.db,x.mind,1000);closed.close();reject(()->closed.inspectNativeCandidate("!extra(2);",l),"closed lease cannot use historical snapshot for inspection");
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)){AtomicBoolean failed=new AtomicBoolean();Thread other=new Thread(()->{try{lease.inspectNativeCandidate("!extra(2);",l);}catch(Exception expected){failed.set(true);}});other.start();other.join();require(failed.get(),"owning diagnostic lease cannot move threads");Mind offline=new Mind(x.user);x.user.setCurrentMind(offline);try{reject(()->lease.inspectNativeCandidate("!extra(2);",l),"changed active caller Mind rejected");}finally{x.user.setCurrentMind(x.mind);}lease.requireCurrent();}
            Mind pending=new Mind(x.mind);try{reject(()->x.db.inspectNativeProductionCandidate(x.mind,"!extra(2);",l),"pending child cannot borrow settled-root diagnostic lease");}finally{x.mind.release(pending);}
            reject(()->x.db.inspectNativeProductionCandidate(new Mind(new User()),"!extra(2);",l),"unrelated Mind has no owning provider");reject(()->x.db.inspectNativeProductionCandidate(x.mind,"!extra(2);",new Limits(1,8,16,l.preparation,l.events,l.work,l.audit,l.capacity)),"whole family row capture bound, no prefix result");
            x.db.connectContext("B","own");reject(()->x.db.inspectNativeProductionCandidate(x.mind,"!extra(2);",l),"existing TRUST lease refusal retained, never flatten shared runtime");x.db.disconnectContext("B");stable(x.db.inspectNativeProductionCandidate(x.mind,"!extra(2);",l));}
        long revision;try(Fixture x=new Fixture(dir,"Historical")){require(x.mind.compile("!anchor(1);",null,false),"historical fixture");revision=x.db.getRevision();}
        try(Fixture x=new Fixture(dir,"Historical@"+revision)){require(x.db.isReadOnly(),"historical provider is read-only");State before=new State(x);stable(x.db.inspectNativeProductionCandidate(x.mind,"!extra(2);",limits()));before.unchanged(x);}
        System.out.println("NATIVE_CANDIDATE_LEASE_FRESHNESS pinnedHeadIndependence=true privateInitializationAndXChange=true topologyAndOwner=true pendingRejected=true trustNotFlattened=true historicalReadOnly=true");
    }
}
