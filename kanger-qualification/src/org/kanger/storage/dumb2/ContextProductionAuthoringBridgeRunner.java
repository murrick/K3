/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.*;
import org.kanger.*;

/** An explicit live bridge gates the ordinary native transaction, never replaying a receipt. */
public final class ContextProductionAuthoringBridgeRunner {
    private static final int WORK = 4000000;
    private static int checks;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private interface Attempt { void run() throws Exception; }
    private static void reject(Attempt attempt,String why) throws Exception {
        boolean rejected = false; try { attempt.run(); } catch (Exception expected) { rejected = true; } require(rejected,why);
    }
    private static final class Fixture implements AutoCloseable {
        final User user; final DB db; final Mind mind;
        Fixture(Path root,String name) throws Exception {
            user = new User(); user.setDatabaseDir(root.toString()+File.separator); db = new DB(); db.init(user);
            Mind m = new Mind(user); user.setCurrentMind(m); mind = (Mind)m.useStorage(name); user.setCurrentMind(mind);
        }
        public void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
    }
    private static ContextProductionSnapshotSession.WriteResult write(ContextProductionSnapshotSession lease,String statement) throws Exception {
        return lease.writeCandidate(statement,12,16,WORK,100000,false);
    }
    public static void main(String[] args) {
        try {
            Path root = Files.createTempDirectory("production-authoring-bridge-"); System.setProperty("user.home",root.toString());
            RevisionRef aPin;
            try (Fixture a = new Fixture(root,"A")) { require(a.mind.compile("!@w p(w,1)->q(w,1);",null,false),"foreign fixture"); aPin = new RevisionRef(a.db.getContextId(),a.db.getRevision()); }
            long finalRevision;
            try (Fixture x = new Fixture(root,"X")) {
                require(x.mind.compile("!$w p(w,1)&&flag(w,1); !@w p(w,1)->warm(w,1);",null,false),"X fixture"); x.db.connectContext("A");
                long before = x.db.getRevision(); int rows = x.mind.getRules().size();
                int logs = x.mind.getLog().size(),solutions = x.mind.getSolutions().size(),hypotheses = x.mind.getHypothesis().size();
                ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                ContextProductionSnapshotSession.WriteResult collision = write(lease,"!@w q(w,1)->~flag(w,1);");
                require(collision.status == ContextProductionSnapshotSession.WriteStatus.COLLISION,"proved delayed collision never invokes native write");
                require(collision.before.equals(collision.after) && x.db.getRevision() == before && x.mind.getRules().size() == rows,"collision preserves revision and native rows"); lease.requireCurrent();
                ContextProductionSnapshotSession.WriteResult incomplete = write(lease,"!@w universal(w,1);");
                require(incomplete.status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"unsupported coverage is never permission to write");
                require(x.db.getRevision() == before && x.mind.getRules().size() == rows,"incomplete trial leaves physical X unchanged");
                require(lease.writeCandidate("!@w q(w,1)->out(w,1);",1,16,WORK,100000,false).status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"round bound never writes a productive prefix");
                lease.requireCurrent();
                require(x.mind.getLog().size() == logs && x.mind.getSolutions().size() == solutions && x.mind.getHypothesis().size() == hypotheses,"prepublication declines preserve caller presentation stores");
                require(write(lease,"?p(1);").status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"non-authoring input is never passed to ordinary query");
                java.util.concurrent.atomic.AtomicBoolean otherRejected = new java.util.concurrent.atomic.AtomicBoolean();
                Thread other = new Thread(() -> { try { write(lease,"!other(1);"); } catch (Exception expected) { otherRejected.set(true); } });
                other.start(); other.join(); require(otherRejected.get() && x.db.getRevision() == before,"publication authority cannot move to another thread");
                ContextProductionSnapshotSession.WriteResult accepted = write(lease,"!@w q(w,1)->out(w,1);");
                require(accepted.status == ContextProductionSnapshotSession.WriteStatus.PUBLISHED && accepted.after.getRevision() > before,"fully covered trial publishes through normal native transaction");
                require(accepted.trial.status == ContextProductionFamilyAssembly.Status.FIXED_POINT && accepted.before.getRevision() == before,"publication carries exact freshly qualified base evidence");
                reject(lease::requireCurrent,"published write consumes old authority");
                reject(() -> write(lease,"!other(1);"),"consumed lease cannot reuse an old candidate certificate"); lease.close();
                try (ContextProductionSnapshotSession current = ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    require(current.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("q(w,1)->out")),"only authoritative candidate rule becomes a primary");
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.contains("universal") || r.origin.contains("~flag")),"rejected and incomplete candidates do not leak into durable X");
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.equals("!out(1);")),"transient foreign-dependent result is not a primary assertion");
                }
                ContextProductionSnapshotSession stale = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                x.db.applyConnectionCommand(x.mind,"A","!private(4);");
                reject(() -> write(stale,"!newcandidate(1);"),"changed initialized view invalidates publication authority"); stale.close();
                finalRevision = x.db.getRevision();
                ContextProductionSnapshotSession topology = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                x.db.disconnectContext("A"); reject(() -> write(topology,"!afterdisconnect(1);"),"source removal invalidates publication authority"); topology.close();
            }
            try (ContextSnapshot stored = ContextSnapshot.open(root.resolve("X"))) {
                require(stored.getRevision() == finalRevision,"close/reopen retains exactly the native published revision");
            }
            try (ContextSnapshot stored = ContextSnapshot.open(root.resolve("A"))) {
                require(stored.getContextId().equals(aPin.getContextId()) && stored.getRevision() == aPin.getRevision(),"foreign physical pin remains unchanged");
            }
            System.out.println("CONTEXT_PRODUCTION_AUTHORING_BRIDGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
