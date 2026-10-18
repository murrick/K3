/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.interfaces.IMind;
import org.kanger.exception.StorageLifecycleException;

/** Read-only observations at the actual pre-settlement hook. Never enables a default guard. */
public final class ContextUniversalCommitSeamRunner {
    private static int checks;
    private static final int WORK=64000000;
    private static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    private static final class ProbeStop extends Exception { private static final long serialVersionUID=1L; }
    private static final class Probe {
        final User owner;final DB db;
        final ProbeStop stop=new ProbeStop();
        boolean armed,legacyRefused;int calls,rounds=16,primary;
        ContextUniversalFamilyInspection.Result result;ContextUniversalFamilyAudit.Audit audit;String incomplete;
        Probe(User owner,DB db) {this.owner=owner;this.db=db;}
        void install() {
            owner.setData((org.kanger.interfaces.internal.IData)java.lang.reflect.Proxy.newProxyInstance(DB.class.getClassLoader(),DB.class.getInterfaces(),(proxy,method,args) -> {
                if(armed && method.getName().equals("validateCommit")) {observe((IMind)args[0]);return null;}
                try {return method.invoke(db,args);} catch(java.lang.reflect.InvocationTargetException failure) {throw failure.getCause();}
            }));
        }
        void restore() {armed=false;owner.setData(db);}
        void observe(IMind proposed) throws Exception {
            ++calls;Mind child=(Mind)proposed,parent=(Mind)owner.getCurrentMind();
            require(child!=parent && child.getNext()==parent,"hook sees actual direct child before parent mutation");
            ContextProductionInventory before=inventory(parent,true,Collections.emptyList(),1000);
            List<ContextConnection> topology=new ArrayList<>(db.dmzTopology());
            ContextProductionInventory pending=inventory(child,true,Collections.emptyList(),1000);primary=pending.primary.size();
            org.kanger.interfaces.internal.IData routed=owner.getData();
            owner.setData(db);
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(db,child,1000)) {
                try {
                    ContextPendingProductionSnapshotSession.Result qualified=lease.inspect(rounds,16,16,20000,1000000,WORK,500000,WORK,WORK);
                    result=qualified.inspection;audit=qualified.audit;
                } catch(IllegalStateException unsupported) {incomplete=unsupported.getMessage();}
                lease.requireCurrent();
            } finally {owner.setData(routed);}
            require(before.fingerprint.equals(inventory(parent,true,Collections.emptyList(),1000).fingerprint),"inspection leaves live physical parent unchanged");
            require(pending.fingerprint.equals(inventory(child,true,Collections.emptyList(),1000).fingerprint),"inspection leaves actual proposed child unchanged");
            require(topology.equals(db.dmzTopology()),"inspection retains exact topology");
            try {db.validateCommit(proposed);} catch(StorageLifecycleException conflict) {legacyRefused=true;}
            // Stop even a consistent proposal: this stage proves the seam without publishing it.
            throw stop;
        }
        ContextProductionInventory inventory(Mind mind,boolean x,List<String> initialization,int rows) throws Exception {
            return ContextProductionInventory.inspect(mind,db.getContextId(),db.getRevision(),x,initialization,rows);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final User user=new User();final DB db=new DB();final Probe probe=new Probe(user,db);final Mind mind;
        Fixture(Path dir,String name) throws Exception {
            user.setDatabaseDir(dir.toString()+File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());db.init(user);
            Mind root=new Mind(user);user.setCurrentMind(root);mind=(Mind)root.useStorage(name);user.setCurrentMind(mind);
        }
        public void close() throws Exception {probe.restore();if(user.getCurrentMind().isStorageUsed()) user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static void probe(Path dir,String name,boolean reverse,String command,ContextUniversalFamilyInspection.Status status,int rounds,boolean batch) throws Exception {
        long revision;String fingerprint;
        try(Fixture x=new Fixture(dir,name)) {
            require(x.mind.compile("!anchor(1);",null,false),"actual parent anchor");
            x.db.connectContext(reverse?"B":"A");x.db.connectContext(reverse?"A":"B");
            revision=x.db.getRevision();fingerprint=x.probe.inventory(x.mind,true,Collections.emptyList(),1000).fingerprint;
            x.probe.rounds=rounds;x.probe.armed=true;x.probe.install();
            Exception seen=null;
            try {
                if(batch) {Mind child=new Mind(x.mind);require(child.compile(command,null,false),"multiple native assertions prepared within one child");x.mind.commit(child);}
                else x.mind.query(command,null,false);
            } catch(Exception expected) {seen=expected;}
            require(seen==x.probe.stop && x.probe.calls==1,"exact hook exception and one commit attempt, no recursive writer/retry");
            if(status==null) require(x.probe.result==null && x.probe.incomplete!=null && x.probe.incomplete.contains("binding incomplete"),"whole proposed unsupported scope remains incomplete");
            else {
                require(x.probe.incomplete==null && x.probe.result.status==status,"actual proposed family status: "+(x.probe.result==null?x.probe.incomplete:x.probe.result.reason));
                require(x.probe.audit!=null && x.probe.audit.verdict==x.probe.result && x.probe.audit.verified==(status!=ContextUniversalFamilyInspection.Status.INCOMPLETE),"whole proposed verdict independently audited, incomplete never verified");
                if(status==ContextUniversalFamilyInspection.Status.COLLISION) {
                    require(x.probe.result.completedBarriers==2 && !x.probe.result.collision.oppositions.isEmpty(),"actual child detects masked second-barrier collision");
                    require(!x.probe.legacyRefused,"legacy hook misses this exact masked proposal, independent of order");
                }
                if(status==ContextUniversalFamilyInspection.Status.CONSISTENT) {
                    ContextUniversalFamilyModel.Certificate c=x.probe.result.certificate;
                    List<ContextUniversalUnitRounds.Receipt> receipts=new ArrayList<>();List<ContextUniversalQuotientModel.Model> models=new ArrayList<>();
                    for(ContextUniversalFamilyModel.Cut cut:c.cuts) {receipts.add(cut.receipt);models.add(cut.model);}
                    require(ContextUniversalFamilyModel.certify(c.input,receipts,models,16,20000,WORK).certified,"complete proposed family models independently rechecked");
                }
            }
            require(x.probe.primary==(batch?3:2),"whole proposed primary inventory includes parent plus actual edits");
            require(x.db.getRevision()==revision && fingerprint.equals(x.probe.inventory(x.mind,true,Collections.emptyList(),1000).fingerprint),"probe rejection restores exact physical parent");
            x.probe.restore();
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {require(lease.inventory().x.primary.size()==1,"native child reservation released, settled source can reopen an owning lease");}
        }
        try(Fixture x=new Fixture(dir,name)) {require(x.db.getRevision()==revision && fingerprint.equals(x.probe.inventory(x.mind,true,Collections.emptyList(),1000).fingerprint),"no proposal became durable after reopen");}
    }
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("universal-commit-seam-");System.setProperty("user.home",dir.toString());
            try(Fixture a=new Fixture(dir,"A")) {require(a.mind.compile("!left(1); !p(1);",null,false),"masking A fixture");}
            try(Fixture b=new Fixture(dir,"B")) {require(b.mind.compile("!right(1); !~p(1);",null,false),"opposite B fixture");}
            for(boolean reverse:new boolean[]{false,true}) {
                probe(dir,"Collision"+reverse,reverse,"!@x left(x)&&right(x)->p(x);",ContextUniversalFamilyInspection.Status.COLLISION,16,false);
                probe(dir,"Safe"+reverse,reverse,"!unrelated(1);",ContextUniversalFamilyInspection.Status.CONSISTENT,16,false);
            }
            probe(dir,"RoundStop",false,"!@x left(x)&&right(x)->p(x);",ContextUniversalFamilyInspection.Status.INCOMPLETE,1,false);
            probe(dir,"Unsupported",false,"!@x f(x)->g(x+1);",null,16,false);
            probe(dir,"Batch",false,"!newone(1); !newtwo(2);",ContextUniversalFamilyInspection.Status.CONSISTENT,16,true);
            System.out.println("CONTEXT_UNIVERSAL_COMMIT_SEAM_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
