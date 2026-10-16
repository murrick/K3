/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.*;
import org.kanger.ContextGroundClauseFamily.Status;
import org.kanger.exception.TransactionSettlementException;

/** Ground-only explicit write authority is freshly qualified and consumed before native settlement. */
public final class ContextGroundAuthoringBridgeRunner {
    private static int checks;
    private static final int WORK = 16000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private interface Attempt { void run() throws Exception; }
    private static void reject(Attempt attempt,String why) throws Exception {
        boolean rejected = false; try { attempt.run(); } catch (Exception expected) { rejected = true; } require(rejected,why);
    }
    private static final class FailingUser extends User {
        boolean fail; int failures;
        final IOException injected = new IOException("test post-durable flush failure");
        FailingUser() throws Exception { super(); }
        @Override public void flush() throws Exception { super.flush(); if (fail) { ++failures; throw injected; } }
    }
    private static final class Fixture implements AutoCloseable {
        final User user; final DB db; final Mind mind;
        Fixture(Path root,String name) throws Exception { this(root,name,new User()); }
        Fixture(Path root,String name,User user) throws Exception {
            this.user = user; user.setDatabaseDir(root.toString()+File.separator); db = new DB(); db.init(user);
            Mind m = new Mind(user); user.setCurrentMind(m); mind = (Mind)m.useStorage(name); user.setCurrentMind(mind);
        }
        public void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
    }
    private static ContextProductionSnapshotSession.GroundWriteResult write(ContextProductionSnapshotSession lease,String text) throws Exception {
        return lease.writeGroundCandidate(text,16,16,16,WORK,500000,256,4096,false);
    }
    private static DB.GroundProductionWriteResult write(Fixture x,String text) throws Exception {
        return x.db.writeGroundProductionCandidate(x.mind,text,1000,16,16,16,WORK,500000,256,4096,false);
    }
    public static void main(String[] args) {
        try {
            Path root = Files.createTempDirectory("ground-authoring-bridge-"); System.setProperty("user.home",root.toString());
            try (Fixture a = new Fixture(root,"A")) { require(a.mind.compile("!left(1); !p(1);",null,false),"A fixture"); }
            try (Fixture b = new Fixture(root,"B")) { require(b.mind.compile("!right(1); !~p(1);",null,false),"B fixture"); }
            long publishedRevision;
            try (Fixture x = new Fixture(root,"X")) {
                x.db.connectContext("A"); x.db.connectContext("B");
                long before = x.db.getRevision(); int rows = x.mind.getRules().size();
                int logs = x.mind.getLog().size(),solutions = x.mind.getSolutions().size(),hypotheses = x.mind.getHypothesis().size();
                ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                ContextProductionSnapshotSession.GroundWriteResult collision = write(lease,"!left(1)&&right(1)->p(1);");
                require(collision.status == ContextProductionSnapshotSession.WriteStatus.COLLISION && collision.trial.qualification.rounds == 2,"masked rule-only collision refuses before any native write");
                require(collision.before.equals(collision.after),"declined candidate reports unchanged physical revision"); lease.requireCurrent();
                require(write(lease,"!@x p(x)->q(x);").status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"universal scope cannot fall back to default publication");
                require(write(lease,"?p(1);").status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"query cannot enter write transaction");
                require(lease.writeGroundCandidate("!left(1)&&right(1)->q(1);",1,16,16,WORK,500000,256,4096,false).status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"common round stop cannot publish successful prefix");
                reject(() -> lease.writeGroundCandidate("!left(1)&&right(1)->q(1);",16,16,16,1,500000,256,4096,false),"bounded source binding failure cannot reach publication");
                ContextProductionSnapshotSession.GroundWriteResult modelStop = lease.writeGroundCandidate("!modelTrial(1);",16,16,16,WORK,500000,256,1,false);
                require(modelStop.status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE && modelStop.trial.reason.contains("assignment limit"),"model search limit never falls back to native writing");
                require(x.db.getRevision() == before && x.mind.getRules().size() == rows,"all declines preserve physical source");
                require(x.mind.getLog().size() == logs && x.mind.getSolutions().size() == solutions && x.mind.getHypothesis().size() == hypotheses,"prewrite declines preserve all caller presentation stores");
                AtomicBoolean otherRejected = new AtomicBoolean();
                Thread other = new Thread(() -> { try { write(lease,"!crossThread(1);"); } catch (Exception expected) { otherRejected.set(true); } });
                other.start(); other.join(); require(otherRejected.get(),"ground write authority cannot move threads");
                ContextProductionSnapshotSession.GroundWriteResult accepted = write(lease,"!left(1)&&right(1)->q(1);");
                require(accepted.status == ContextProductionSnapshotSession.WriteStatus.PUBLISHED && accepted.after.getRevision() > before,"fully closed ground rule publishes through ordinary native transaction");
                require(accepted.trial.status == Status.GROUND_FIXED_POINT && accepted.trial.qualification.certificate.frontier.x == accepted.trial.artifact.staged,"publication retains exact fresh staged qualification");
                require(accepted.before.getRevision() == before && accepted.after.getRevision() == x.db.getRevision(),"trial source revision and actual physical publication are distinct");
                reject(lease::requireCurrent,"successful ground transaction consumes old lease");
                reject(() -> write(lease,"!retry(1);"),"consumed ground authority cannot reuse certificate"); lease.close();
                try (ContextProductionSnapshotSession current = ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    require(current.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("->q(1)")),"actual candidate rule is a durable primary");
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.contains("->p(1)") || r.origin.contains("@x")),"rejected/incomplete candidates are absent physically");
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.equals("!q(1);")),"foreign-dependent output is not promoted to a primary assertion");
                }
                long after = x.db.getRevision();
                require(write(x,"!left(1)&&right(1)->q(1);").status == DB.ProductionWriteStatus.INCOMPLETE && x.db.getRevision() == after,"public fresh write rejects duplicate staged occurrence");
                ContextGroundClauseCandidate.Evaluation historical = x.db.qualifyGroundProductionCandidate(x.mind,"!delayed(1);",1000,16,16,16,WORK,500000,256,4096);
                require(historical.status == Status.GROUND_FIXED_POINT,"historical safe diagnostic fixture");
                require(x.mind.compile("!~delayed(1);",null,false),"actual later source knowledge");
                after = x.db.getRevision(); rows = x.mind.getRules().size();
                require(write(x,"!delayed(1);").status != DB.ProductionWriteStatus.PUBLISHED,"historical success cannot bypass fresh qualification against changed X");
                require(x.db.getRevision() == after && x.mind.getRules().size() == rows,"freshly refused conflicting trial leaves later source knowledge intact");
                ContextProductionSnapshotSession stale = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                x.db.applyConnectionCommand(x.mind,"A","!private(4);");
                reject(() -> write(stale,"!afterInitialization(1);"),"changed initialized view invalidates ground write authority"); stale.close();
                ContextProductionSnapshotSession topology = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                x.db.disconnectContext("B"); reject(() -> write(topology,"!afterDisconnect(1);"),"source removal invalidates ground write authority"); topology.close();
                Mind pending = new Mind(x.mind);
                try { reject(() -> write(x,"!whilePending(1);"),"open child reservation cannot be a settled ground write source"); }
                finally { x.mind.release(pending); }
                reject(() -> x.db.writeGroundProductionCandidate(new Mind(new User()),"!unowned(1);",1000,16,16,16,WORK,500000,256,4096,false),"unrelated Mind cannot obtain public ground write authority");
                publishedRevision = x.db.getRevision();
            }
            try (Fixture reopened = new Fixture(root,"X")) {
                require(reopened.db.getRevision() == publishedRevision,"ground publication survives close/reopen");
                try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(reopened.db,reopened.mind,1000)) {
                    require(lease.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("->q(1)")),"published original rule remains durable");
                }
            }
            try (Fixture historical = new Fixture(root,"X@"+publishedRevision)) {
                require(historical.db.isReadOnly(),"historical fixture immutable");
                reject(() -> write(historical,"!forbidden(1);"),"public ground bridge cannot write a historical revision");
            }
            try (Fixture simple = new Fixture(root,"Simple")) {
                require(simple.mind.compile("!seed(1);",null,false),"local production fixture");
                DB.GroundProductionWriteResult result = write(simple,"!seed(1)->ready(1);");
                require(result.status == DB.ProductionWriteStatus.PUBLISHED && result.after.getRevision() > result.before.getRevision(),"public ground bridge publishes local rule through native settlement");
                require(Boolean.TRUE.equals(simple.mind.query("?ready(1);",null,false)),"normal native query observes ordinary local consequence");
            }
            try (Fixture a = new Fixture(root,"CA")) { require(a.mind.compile("!d(1)->a(1);",null,false),"composition A fixture"); }
            try (Fixture b = new Fixture(root,"CB")) { require(b.mind.compile("!d(1)->b(1);",null,false),"composition B fixture"); }
            try (Fixture c = new Fixture(root,"CC")) { require(c.mind.compile("!d(1); !c(1);",null,false),"composition C fixture"); }
            try (Fixture x = new Fixture(root,"Composition")) {
                x.db.connectContext("CA"); x.db.connectContext("CB"); x.db.connectContext("CC");
                long before = x.db.getRevision(); int rows = x.mind.getRules().size();
                DB.GroundProductionWriteResult collision = write(x,"!~a(1)||~b(1)||~c(1);");
                require(collision.status == DB.ProductionWriteStatus.COLLISION && collision.trial.qualification.rounds == 3,"public write catches full composition counterexample before native publication");
                require(collision.trial.qualification.collisions.stream().anyMatch(c -> {
                    java.util.Set<org.kanger.ContextProductionCapture.Source> sources = new java.util.HashSet<>(c.leftProof.sources); sources.addAll(c.rightProof.sources);
                    return sources.size() == 5 && sources.contains(collision.trial.artifact.candidate);
                }),"publication refusal cites candidate and all five actual original causes");
                require(x.db.getRevision() == before && x.mind.getRules().size() == rows,"three-barrier refutation leaves physical source unchanged");
            }
            FailingUser failing = new FailingUser(); long durable;
            try (Fixture x = new Fixture(root,"PostCommit",failing)) {
                ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                long before = x.db.getRevision(); failing.fail = true; TransactionSettlementException observed = null;
                try { write(lease,"!committed(1);"); }
                catch (TransactionSettlementException expected) { observed = expected; }
                finally { failing.fail = false; }
                require(observed != null && observed.getOutcome() == TransactionSettlementException.Outcome.COMMITTED && observed.isReservationConsumed(),"post-durable failure preserves COMMITTED settlement outcome");
                require(observed.getCause() == failing.injected && failing.failures == 1,"native finalization failure propagates unchanged without retry");
                require(x.db.getRevision() > before,"native physical publication occurred before injected finalization error");
                reject(lease::requireCurrent,"post-commit failure cannot reopen consumed ground lease");
                reject(() -> write(lease,"!again(1);"),"failed finalization cannot retry native settlement through old authority"); lease.close();
                require(failing.failures == 1,"no hidden second finalization attempt");
                try (ContextProductionSnapshotSession current = ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    require(current.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("committed(1)")),"semantic delta remains after committed failure");
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.contains("again(1)")),"blocked retry adds no primary");
                }
                durable = x.db.getRevision();
            }
            try (Fixture reopened = new Fixture(root,"PostCommit")) {
                require(reopened.db.getRevision() == durable && Boolean.TRUE.equals(reopened.mind.query("?committed(1);",null,false)),"committed failure delta survives reopening");
            }
            System.out.println("CONTEXT_GROUND_AUTHORING_BRIDGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
