/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.*;
import org.kanger.ContextUniversalFamilyInspection.Status;

/** Fresh owned universal candidate diagnostics must never publish or reuse stale views. */
public final class ContextUniversalCandidateSnapshotRunner {
    private static int checks;
    private static final int WORK = 64000000;
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
    private static ContextUniversalCandidate.Evaluation qualify(ContextProductionSnapshotSession lease,String statement) throws Exception {
        return lease.qualifyUniversalCandidate(statement,8,16,16,20000,1000000,WORK,500000,WORK);
    }
    private static ContextUniversalCandidate.Evaluation qualify(Fixture x,String statement) throws Exception {
        return x.db.qualifyUniversalProductionCandidate(x.mind,statement,1000,8,16,16,20000,1000000,WORK,500000,WORK);
    }
    public static void main(String[] args) {
        try {
            Path root = Files.createTempDirectory("universal-candidate-snapshots-"); System.setProperty("user.home",root.toString());
            RevisionRef aPin,bPin;
            try (Fixture a = new Fixture(root,"A")) { require(a.mind.compile("!left(1); !p(1);",null,false),"A fixture"); aPin = new RevisionRef(a.db.getContextId(),a.db.getRevision()); }
            try (Fixture b = new Fixture(root,"B")) { require(b.mind.compile("!right(1); !~p(1);",null,false),"B fixture"); bPin = new RevisionRef(b.db.getContextId(),b.db.getRevision()); }
            try (Fixture t = new Fixture(root,"T")) { require(t.mind.compile("!marker(1);",null,false),"TRUST fixture"); }
            long finalRevision;
            try (Fixture x = new Fixture(root,"X")) {
                x.db.connectContext("A"); x.db.connectContext("B");
                long before = x.db.getRevision(); int rows = x.mind.getRules().size();
                int logs = x.mind.getLog().size(),solutions = x.mind.getSolutions().size(),hypotheses = x.mind.getHypothesis().size();
                ContextUniversalCandidate.Evaluation rejected = qualify(x,"!@x left(x)&&right(x)->p(x);");
                require(rejected.status == Status.COLLISION && rejected.qualification.completedBarriers == 2,"fresh real-provider rule-only candidate proves masked collision");
                require(rejected.artifact.base.inventory.context.equals(x.db.getContextId()) && rejected.artifact.base.inventory.revision == before,"actual provider identity/revision bound to candidate");
                require(rejected.artifact.foreign.get(0).inventory.context.equals(aPin.getContextId()) && rejected.artifact.foreign.get(0).inventory.revision == aPin.getRevision(),"A exact physical pin retained");
                require(rejected.artifact.foreign.get(1).inventory.context.equals(bPin.getContextId()) && rejected.artifact.foreign.get(1).inventory.revision == bPin.getRevision(),"B exact physical pin retained");
                ContextUniversalCandidate.Evaluation safe = qualify(x,"!@x unrelated(x);");
                require(safe.status == Status.CONSISTENT && safe.qualification.certificate.input.x == safe.artifact.staged,"safe diagnostic certifies exact private staged assertion");
                require(x.db.getRevision() == before && x.mind.getRules().size() == rows,"neither collision nor successful universal diagnostic writes physical X");
                require(x.mind.getLog().size() == logs && x.mind.getSolutions().size() == solutions && x.mind.getHypothesis().size() == hypotheses,"diagnostics preserve source presentation stores");
                require(qualify(x,"?p(1);").status == Status.INCOMPLETE,"query cannot enter staged authoring");
                require(qualify(x,"!@x isolated(x)->q(x);").status == Status.CONSISTENT,"flat universal candidate has explicit read-only model certificate");
                ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                ContextUniversalCandidate.Evaluation historical = qualify(lease,"!newcandidate(1);");
                require(historical.status == Status.CONSISTENT,"owned lease supports read-only universal diagnostic"); lease.requireCurrent();
                AtomicBoolean otherRejected = new AtomicBoolean();
                Thread other = new Thread(() -> { try { qualify(lease,"!crossThread(1);"); } catch (Exception expected) { otherRejected.set(true); } });
                other.start(); other.join(); require(otherRejected.get(),"another thread cannot use owning snapshot authority");
                try (Fixture aHead = new Fixture(root,"A")) { require(aHead.mind.compile("!headOnly(9);",null,false),"independent foreign HEAD advance"); require(aHead.db.getRevision() > aPin.getRevision(),"HEAD really advanced"); }
                lease.requireCurrent();
                ContextUniversalCandidate.Evaluation pinned = qualify(lease,"!pinnedTrial(1);");
                require(pinned.status == Status.CONSISTENT && pinned.artifact.foreign.get(0).inventory.revision == aPin.getRevision(),"fresh diagnostic retains pinned view instead of new HEAD");
                require(pinned.artifact.foreign.get(0).primary.stream().noneMatch(e -> e.statement.contains("headOnly")),"new HEAD knowledge never leaks into old pin");
                x.db.applyConnectionCommand(x.mind,"A","!private(4);");
                reject(() -> qualify(lease,"!afterPrivate(1);"),"private initialized view change invalidates old authority"); lease.close();
                ContextUniversalCandidate.Evaluation configured = qualify(x,"!configuredTrial(1);");
                require(configured.status == Status.CONSISTENT && configured.artifact.foreign.get(0).primary.stream().anyMatch(e -> e.statement.contains("private")),"new owned snapshot sees exact changed initialized view");
                require(configured.artifact.foreign.get(0).inventory.revision == aPin.getRevision(),"private initialization does not repin physical source");
                require(historical.artifact.foreign.get(0).primary.stream().noneMatch(e -> e.statement.contains("private")),"historical diagnostic never changes retroactively");
                ContextProductionSnapshotSession closed = ContextProductionSnapshotSession.open(x.db,x.mind,1000); closed.close();
                reject(() -> qualify(closed,"!afterClose(1);"),"closed lease cannot qualify universal candidate");
                ContextProductionSnapshotSession stale = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                require(x.mind.compile("!realLocal(1);",null,false),"actual unrelated source change");
                reject(() -> qualify(stale,"!afterLocal(1);"),"changed source rows/revision invalidate universal diagnostic authority"); stale.close();
                ContextUniversalCandidate.Evaluation fresh = qualify(x,"!freshTrial(1);");
                require(fresh.status == Status.CONSISTENT && fresh.artifact.base.primary.stream().anyMatch(e -> e.statement.contains("realLocal")),"new snapshot includes actual source update");
                require(fresh.artifact.rebased.size() == fresh.artifact.base.primary.size(),"every current original has explicit staged rebasing");
                x.db.connectContext("T","own");
                reject(() -> qualify(x,"!trustTrial(1);"),"TRUST cannot be flattened into universal independent cuts"); x.db.disconnectContext("T");
                ContextProductionSnapshotSession topology = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                x.db.disconnectContext("B"); reject(() -> qualify(topology,"!afterTopology(1);"),"source removal invalidates complete-family authority"); topology.close();
                require(x.mind.getRules().size() == rows+1,"only explicit realLocal source change is present physically");
                try (ContextProductionSnapshotSession current = ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.contains("Trial") || r.origin.contains("candidate") || r.origin.contains("unrelated") || r.origin.contains("->p")),"no trial candidate becomes durable primary knowledge");
                }
                finalRevision = x.db.getRevision();
            }
            try (ContextSnapshot stored = ContextSnapshot.open(root.resolve("X"))) { require(stored.getRevision() == finalRevision,"close/reopen retains only explicit source operations"); }
            try (SnapshotMindRuntime oldA = SnapshotMindRuntime.open(root.resolve("A"),aPin,"universal-candidate-pin-check")) {
                ContextProductionInventory inventory = ContextProductionInventory.inspect(oldA.getMind(),aPin.getContextId(),aPin.getRevision(),false,java.util.Collections.emptyList(),1000);
                require(inventory.primary.stream().noneMatch(r -> r.origin.contains("private") || r.origin.contains("headOnly")),"old physical A pin remains untouched");
            }
            System.out.println("CONTEXT_UNIVERSAL_CANDIDATE_SNAPSHOT_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
