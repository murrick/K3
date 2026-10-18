/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Actual pending reservation ownership, frozen whole views, bounds and no publication. */
public final class ContextPendingProductionSnapshotRunner {
    private static int checks;
    private static final int WORK=64000000;
    private interface Attempt {void run() throws Exception;}
    private static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    private static void reject(Attempt f,String why) throws Exception {boolean no=false;try {f.run();} catch(Exception expected) {no=true;}require(no,why);}
    private static final class Fixture implements AutoCloseable {
        final User user;final DB db=new DB();final Mind root;
        Fixture(Path dir,String name) throws Exception {this(dir,name,new User());}
        Fixture(Path dir,String name,User user) throws Exception {this.user=user;user.setDatabaseDir(dir.toString()+File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);}
        public void close() throws Exception {if(user.getCurrentMind().isStorageUsed()) user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static ContextPendingProductionSnapshotSession.Result inspect(ContextPendingProductionSnapshotSession lease) throws Exception {return lease.inspect(16,16,16,20000,1000000,WORK,500000,WORK,WORK);}
    private static String fingerprint(Fixture f) throws Exception {return ContextProductionInventory.inspect(f.root,f.db.getContextId(),f.db.getRevision(),true,Collections.emptyList(),1000).fingerprint;}
    private static final class FailUser extends User {
        boolean fail;final IOException injected=new IOException("pending snapshot post-durable failure");
        FailUser() throws Exception {super();}
        @Override public void flush() throws Exception {super.flush();if(fail) {fail=false;throw injected;}}
    }
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("pending-production-");System.setProperty("user.home",dir.toString());
            try(Fixture a=new Fixture(dir,"A")) {require(a.root.compile("!left(1); !p(1);",null,false),"A fixture");}
            try(Fixture b=new Fixture(dir,"B")) {require(b.root.compile("!right(1); !~p(1);",null,false),"B fixture");}
            for(boolean reverse:new boolean[]{false,true}) {
                String name="Collision"+reverse;long rev;String original;
                try(Fixture f=new Fixture(dir,name)) {
                    require(f.root.compile("!anchor(1);",null,false),"parent anchor");f.db.connectContext(reverse?"B":"A");f.db.connectContext(reverse?"A":"B");
                    rev=f.db.getRevision();original=fingerprint(f);Mind child=new Mind(f.root);
                    require(child.compile("!@x left(x)&&right(x)->p(x);",null,false),"actual pending rule");
                    ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000);
                    ContextPendingProductionSnapshotSession.Result r=inspect(lease);
                    require(r.inspection.status==ContextUniversalFamilyInspection.Status.COLLISION && r.inspection.completedBarriers==2 && r.audit.verified,"owning pending inspection audits masked collision in both orders");
                    require(f.db.getRevision()==rev && original.equals(fingerprint(f)),"inspection does not settle parent");
                    boolean[] off={false};Thread t=new Thread(() -> {try {lease.requireCurrent();} catch(Exception expected) {off[0]=true;}});t.start();t.join();require(off[0],"cross-thread lease use refuses");
                    if(!reverse) {
                        try(Fixture advanced=new Fixture(dir,"A")) {require(advanced.root.compile("!newHead(99);",null,false),"foreign physical HEAD advances");}
                        lease.requireCurrent();require(inspect(lease).audit.verified,"old immutable foreign pin remains valid after HEAD advance");
                    }
                    lease.close();reject(lease::requireCurrent,"closed lease refuses");f.root.release(child);
                    require(original.equals(fingerprint(f)) && f.db.getRevision()==rev,"native release leaves source unchanged");
                    Mind replacement=new Mind(f.root);reject(() -> f.root.requirePendingPhysicalChild(child),"released child cannot impersonate a new live reservation");f.root.release(replacement);
                }
                try(Fixture f=new Fixture(dir,name)) {require(f.db.getRevision()==rev && original.equals(fingerprint(f)),"no candidate durable after reopen");}
            }
            try(Fixture f=new Fixture(dir,"Controls")) {
                require(f.root.compile("!anchor(1);",null,false),"control parent");Mind child=new Mind(f.root);require(child.compile("!newone(1); !newtwo(2);",null,false),"whole batch prepared");
                reject(() -> ContextPendingProductionSnapshotSession.open(f.db,child,1),"whole proposed row bound");
                try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
                    require(inspect(lease).audit.verified,"consistent whole pending batch independently audited");
                    require(!lease.inspect(16,16,16,20000,1000000,WORK,500000,WORK,1).audit.verified,"audit bound cannot approve consistency");
                    require(lease.inspect(16,16,16,20000,1000000,WORK,500000,1,WORK).inspection.status==ContextUniversalFamilyInspection.Status.INCOMPLETE,"inference bound remains incomplete");
                    require(child.compile("!later(3);",null,false),"child changes after lease capture");reject(lease::requireCurrent,"changed child invalidates lease");
                }
                f.root.release(child);
                Mind unsupported=new Mind(f.root);require(unsupported.compile("!@x f(x)->g(x+1);",null,false),"native variable function prepared");
                try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,unsupported,1000)) {reject(() -> inspect(lease),"unsupported whole pending binding refuses without fallback");}
                f.root.release(unsupported);
                Mind first=new Mind(f.root),second=new Mind(f.root);reject(() -> ContextPendingProductionSnapshotSession.open(f.db,first,1000),"multiple root reservations refuse");f.root.release(second);
                Mind nested=new Mind(first);reject(() -> ContextPendingProductionSnapshotSession.open(f.db,first,1000),"pending grandchild refuses");first.release(nested);
                try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,first,1000)) {
                    require(f.root.compile("!parentlater(4);",null,false),"actual parent changes while another child remains pending");reject(lease::requireCurrent,"changed physical parent invalidates pending lease");
                }
                f.root.release(first);
                Mind activeChild=new Mind(f.root);f.user.setCurrentMind(activeChild);
                try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,activeChild,1000)) {require(inspect(lease).audit.verified,"explicit user-level current child can be inspected");}
                f.user.setCurrentMind(f.root);f.root.release(activeChild);
                Mind topology=new Mind(f.root);
                try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,topology,1000)) {f.db.connectContext("A");reject(lease::requireCurrent,"changed topology invalidates pending lease");}
                f.root.release(topology);
                Mind reconnected=new Mind(f.root);
                try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,reconnected,1000)) {
                    ContextConnection old=f.db.dmzTopology().get(0);f.db.connectContext("A");ContextConnection fresh=f.db.dmzTopology().get(0);
                    require(old!=fresh && old.equals(fresh),"same metadata reconnection installs a distinct effective connection");
                    reject(lease::requireCurrent,"equal connection metadata cannot substitute captured runtime instance");
                }
                f.root.release(reconnected);
                Mind initialized=new Mind(f.root);
                try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,initialized,1000)) {f.db.applyConnectionCommand(f.root,"A","!privatePending(4);");reject(lease::requireCurrent,"changed initialized foreign view invalidates pending lease");}
                f.root.release(initialized);
                Mind unownedRoot=new Mind(new User()),unowned=new Mind(unownedRoot);reject(() -> ContextPendingProductionSnapshotSession.open(f.db,unowned,1000),"different provider/user refuses");unownedRoot.release(unowned);
                reject(() -> ContextPendingProductionSnapshotSession.open(f.db,f.root,1000),"settled root is not a pending child");
            }
            FailUser user=new FailUser();
            try(Fixture f=new Fixture(dir,"FailCommitted",user)) {
                Mind child=new Mind(f.root);require(child.compile("!durable(1);",null,false),"post-settlement child prepared");
                ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000);require(inspect(lease).audit.verified,"committed fixture audit");
                user.fail=true;org.kanger.exception.TransactionSettlementException failure=null;
                try {f.root.commit(child);} catch(org.kanger.exception.TransactionSettlementException expected) {failure=expected;}
                require(failure!=null && failure.getOutcome()==org.kanger.exception.TransactionSettlementException.Outcome.COMMITTED && failure.getCause()==user.injected,"native post-durable outcome remains COMMITTED");
                reject(lease::requireCurrent,"settled failed-finalization lease invalidated");lease.close();
                Mind replacement=new Mind(f.root);reject(() -> f.root.requirePendingPhysicalChild(child),"committed child cannot reuse replacement reservation after finalization failure");f.root.release(replacement);
                require(Boolean.TRUE.equals(f.root.query("?durable(1);",null,false)),"actual committed knowledge retained");
            }
            System.out.println("CONTEXT_PENDING_PRODUCTION_SNAPSHOT_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
