/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.*;
import org.kanger.ContextUniversalFamilyInspection.Status;
import org.kanger.exception.TransactionSettlementException;

/** Flat universal explicit write authority is freshly qualified and consumed before native settlement. */
public final class ContextUniversalAuthoringBridgeRunner {
    private static int checks;
    private static final int WORK = 64000000;
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
    private static ContextProductionSnapshotSession.UniversalWriteResult write(ContextProductionSnapshotSession lease,String text) throws Exception {
        return lease.writeUniversalCandidate(text,16,16,16,20000,1000000,WORK,500000,WORK,WORK,false);
    }
    private static DB.UniversalProductionWriteResult write(Fixture x,String text) throws Exception {
        return x.db.writeUniversalProductionCandidate(x.mind,text,1000,16,16,16,20000,1000000,WORK,500000,WORK,WORK,false);
    }
    public static void main(String[] args) {
        try {
            Path root = Files.createTempDirectory("universal-authoring-bridge-"); System.setProperty("user.home",root.toString());
            try (Fixture a = new Fixture(root,"A")) { require(a.mind.compile("!left(1); !p(1);",null,false),"A fixture"); }
            try (Fixture b = new Fixture(root,"B")) { require(b.mind.compile("!right(1); !~p(1);",null,false),"B fixture"); }
            long publishedRevision;
            try (Fixture x = new Fixture(root,"X")) {
                x.db.connectContext("A"); x.db.connectContext("B");
                long before = x.db.getRevision(); int rows = x.mind.getRules().size();
                int logs = x.mind.getLog().size(),solutions = x.mind.getSolutions().size(),hypotheses = x.mind.getHypothesis().size();
                ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                ContextProductionSnapshotSession.UniversalWriteResult collision = write(lease,"!@x left(x)&&right(x)->p(x);");
                require(collision.status == ContextProductionSnapshotSession.WriteStatus.COLLISION && collision.trial.qualification.completedBarriers == 2,"masked rule-only collision refuses before any native write");
                require(collision.before.equals(collision.after),"declined candidate reports unchanged physical revision"); lease.requireCurrent();
                require(write(lease,"!@x p(x)->q(x+1);").status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"variable function scope cannot fall back to default publication");
                require(write(lease,"?p(1);").status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"query cannot enter write transaction");
                require(lease.writeUniversalCandidate("!@x left(x)&&right(x)->q(x);",1,16,16,20000,1000000,WORK,500000,WORK,WORK,false).status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE,"common round stop cannot publish successful prefix");
                reject(() -> lease.writeUniversalCandidate("!@x left(x)&&right(x)->q(x);",16,16,16,20000,1000000,1,500000,WORK,WORK,false),"bounded source binding failure cannot reach publication");
                ContextProductionSnapshotSession.UniversalWriteResult modelStop = lease.writeUniversalCandidate("!@x modelTrial(x);",16,16,16,20000,1,WORK,500000,WORK,WORK,false);
                require(modelStop.status == ContextProductionSnapshotSession.WriteStatus.INCOMPLETE && modelStop.trial.reason.contains("assignment limit"),"model search limit never falls back to native writing");
                ContextProductionSnapshotSession.UniversalWriteResult auditStop=lease.writeUniversalCandidate("!@x auditTrial(x);",16,16,16,20000,1000000,WORK,500000,WORK,1,false);
                require(auditStop.status==ContextProductionSnapshotSession.WriteStatus.INCOMPLETE && auditStop.trial.status==Status.CONSISTENT && auditStop.audit!=null && !auditStop.audit.verified,"successful inference cannot bypass incomplete final candidate audit");
                ContextProductionSnapshotSession.UniversalWriteResult collisionAuditStop=lease.writeUniversalCandidate("!@x left(x)&&right(x)->p(x);",16,16,16,20000,1000000,WORK,500000,WORK,1,false);
                require(collisionAuditStop.status==ContextProductionSnapshotSession.WriteStatus.INCOMPLETE && collisionAuditStop.trial.status==Status.COLLISION && !collisionAuditStop.audit.verified,"unverified collision cannot gain an audited writer verdict");
                require(x.db.getRevision() == before && x.mind.getRules().size() == rows,"all declines preserve physical source");
                require(x.mind.getLog().size() == logs && x.mind.getSolutions().size() == solutions && x.mind.getHypothesis().size() == hypotheses,"prewrite declines preserve all caller presentation stores");
                AtomicBoolean otherRejected = new AtomicBoolean();
                Thread other = new Thread(() -> { try { write(lease,"!crossThread(1);"); } catch (Exception expected) { otherRejected.set(true); } });
                other.start(); other.join(); require(otherRejected.get(),"universal write authority cannot move threads");
                ContextProductionSnapshotSession.UniversalWriteResult accepted = write(lease,"!@x left(x)&&right(x)->q(x);");
                require(accepted.status == ContextProductionSnapshotSession.WriteStatus.PUBLISHED && accepted.after.getRevision() > before,"fully closed universal rule publishes through ordinary native transaction");
                require(accepted.trial.status == Status.CONSISTENT && accepted.trial.qualification.certificate.input.x == accepted.trial.artifact.staged,"publication retains exact fresh staged qualification");
                require(accepted.before.getRevision() == before && accepted.after.getRevision() == x.db.getRevision(),"trial source revision and actual physical publication are distinct");
                require(accepted.audit.verified && accepted.audit.evaluation==accepted.trial && accepted.audit.oppositions.isEmpty(),"only the exact newly audited consistent trial reaches native settlement");
                reject(lease::requireCurrent,"successful universal transaction consumes old lease");
                reject(() -> write(lease,"!retry(1);"),"consumed universal authority cannot reuse certificate"); lease.close();
                try (ContextProductionSnapshotSession current = ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    require(current.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("->q(x)")),"actual candidate rule is a durable primary");
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.contains("->p(x)") || r.origin.contains("x+1")),"rejected/incomplete candidates are absent physically");
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.equals("!q(1);")),"foreign-dependent output is not promoted to a primary assertion");
                }
                long after = x.db.getRevision();
                require(write(x,"!@x left(x)&&right(x)->q(x);").status == DB.ProductionWriteStatus.INCOMPLETE && x.db.getRevision() == after,"public fresh write rejects duplicate staged occurrence");
                ContextUniversalCandidate.Evaluation historical = x.db.qualifyUniversalProductionCandidate(x.mind,"!@x delayed(x);",1000,16,16,16,20000,1000000,WORK,500000,WORK);
                require(historical.status == Status.CONSISTENT,"historical safe diagnostic fixture");
                require(x.mind.compile("!~delayed(1);",null,false),"actual later source knowledge");
                after = x.db.getRevision(); rows = x.mind.getRules().size();
                require(write(x,"!@x delayed(x);").status != DB.ProductionWriteStatus.PUBLISHED,"historical success cannot bypass fresh qualification against changed X");
                require(x.db.getRevision() == after && x.mind.getRules().size() == rows,"freshly refused conflicting trial leaves later source knowledge intact");
                ContextProductionSnapshotSession stale = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                x.db.applyConnectionCommand(x.mind,"A","!private(4);");
                reject(() -> write(stale,"!afterInitialization(1);"),"changed initialized view invalidates universal write authority"); stale.close();
                ContextProductionSnapshotSession topology = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                x.db.disconnectContext("B"); reject(() -> write(topology,"!afterDisconnect(1);"),"source removal invalidates universal write authority"); topology.close();
                Mind pending = new Mind(x.mind);
                try { reject(() -> write(x,"!whilePending(1);"),"open child reservation cannot be a settled universal write source"); }
                finally { x.mind.release(pending); }
                reject(() -> x.db.writeUniversalProductionCandidate(new Mind(new User()),"!unowned(1);",1000,16,16,16,20000,1000000,WORK,500000,WORK,WORK,false),"unrelated Mind cannot obtain public universal write authority");
                publishedRevision = x.db.getRevision();
            }
            try (Fixture reopened = new Fixture(root,"X")) {
                require(reopened.db.getRevision() == publishedRevision,"universal publication survives close/reopen");
                try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(reopened.db,reopened.mind,1000)) {
                    require(lease.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("->q(x)")),"published original rule remains durable");
                }
            }
            try (Fixture historical = new Fixture(root,"X@"+publishedRevision)) {
                require(historical.db.isReadOnly(),"historical fixture immutable");
                reject(() -> write(historical,"!forbidden(1);"),"public universal bridge cannot write a historical revision");
            }
            try (Fixture simple = new Fixture(root,"Simple")) {
                require(simple.mind.compile("!seed(1);",null,false),"local production fixture");
                DB.UniversalProductionWriteResult result = write(simple,"!@x seed(x)->ready(x);");
                require(result.status == DB.ProductionWriteStatus.PUBLISHED && result.after.getRevision() > result.before.getRevision(),"public universal bridge publishes local rule through native settlement");
                require(Boolean.TRUE.equals(simple.mind.query("?ready(1);",null,false)),"normal native query observes ordinary local consequence");
            }
            try (Fixture a = new Fixture(root,"CA")) { require(a.mind.compile("!@x d(x)->a(x);",null,false),"composition A fixture"); }
            try (Fixture b = new Fixture(root,"CB")) { require(b.mind.compile("!@x d(x)->b(x);",null,false),"composition B fixture"); }
            try (Fixture c = new Fixture(root,"CC")) { require(c.mind.compile("!d(1); !c(1);",null,false),"composition C fixture"); }
            try (Fixture x = new Fixture(root,"Composition")) {
                x.db.connectContext("CA"); x.db.connectContext("CB"); x.db.connectContext("CC");
                long before = x.db.getRevision(); int rows = x.mind.getRules().size();
                DB.UniversalProductionWriteResult collision = write(x,"!@x ~a(x)||~b(x)||~c(x);");
                require(collision.status == DB.ProductionWriteStatus.COLLISION && collision.trial.qualification.completedBarriers == 3,"public write catches full composition counterexample before native publication");
                require(collision.audit.verified && collision.audit.oppositions.stream().anyMatch(c -> c.usesCandidate && c.originalSources.size()==5),"publication refusal cites exact candidate and all five actual original causes");
                require(x.db.getRevision() == before && x.mind.getRules().size() == rows,"three-barrier refutation leaves physical source unchanged");
            }
            try(Fixture a=new Fixture(root,"OldA")) { require(a.mind.compile("!@x oldp(x);",null,false),"old A fixture"); }
            try(Fixture b=new Fixture(root,"OldB")) { require(b.mind.compile("!~oldq(9);",null,false),"old B fixture"); }
            try(Fixture x=new Fixture(root,"OldX")) {
                require(x.mind.compile("!@x oldp(x)->oldq(x);",null,false),"old X fixture");
                x.db.connectContext("OldA");
                long before=x.db.getRevision(); int rows=x.mind.getRules().size();
                reject(() -> x.db.connectContext("OldB"),"existing provider topology gate already refuses this candidate-independent base-family contradiction");
                require(x.db.getRevision()==before && x.mind.getRules().size()==rows,"refused inconsistent connection preserves physical X");
                try(ContextProductionSnapshotSession current=ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    require(current.inventory().foreign.size()==1,"rejected old-family connection never enters owning write family");
                }
            }
            FailingUser failing = new FailingUser(); long durable;
            try (Fixture x = new Fixture(root,"PostCommit",failing)) {
                ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(x.db,x.mind,1000);
                long before = x.db.getRevision(); failing.fail = true; TransactionSettlementException observed = null;
                try { write(lease,"!@x committed(x);"); }
                catch (TransactionSettlementException expected) { observed = expected; }
                finally { failing.fail = false; }
                require(observed != null && observed.getOutcome() == TransactionSettlementException.Outcome.COMMITTED && observed.isReservationConsumed(),"post-durable failure preserves COMMITTED settlement outcome");
                require(observed.getCause() == failing.injected && failing.failures == 1,"native finalization failure propagates unchanged without retry");
                require(x.db.getRevision() > before,"native physical publication occurred before injected finalization error");
                reject(lease::requireCurrent,"post-commit failure cannot reopen consumed universal lease");
                reject(() -> write(lease,"!again(1);"),"failed finalization cannot retry native settlement through old authority"); lease.close();
                require(failing.failures == 1,"no hidden second finalization attempt");
                try (ContextProductionSnapshotSession current = ContextProductionSnapshotSession.open(x.db,x.mind,1000)) {
                    require(current.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("committed(x)")),"semantic delta remains after committed failure");
                    require(current.inventory().x.primary.stream().noneMatch(r -> r.origin.contains("again(1)")),"blocked retry adds no primary");
                }
                durable = x.db.getRevision();
            }
            try (Fixture reopened = new Fixture(root,"PostCommit")) {
                require(reopened.db.getRevision() == durable && Boolean.TRUE.equals(reopened.mind.query("?committed(1);",null,false)),"committed failure delta survives reopening");
            }
            System.out.println("CONTEXT_UNIVERSAL_AUTHORING_BRIDGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
