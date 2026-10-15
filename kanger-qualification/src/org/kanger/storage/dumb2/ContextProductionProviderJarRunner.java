/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.*;
import org.kanger.*;

/** Public provider facade only; also run with Core+DUMB2 artifacts without qualification. */
public final class ContextProductionProviderJarRunner {
    private static final int WORK = 4000000;
    private static int checks;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private interface Attempt { void run() throws Exception; }
    private static void reject(Attempt attempt,String why) throws Exception {
        boolean refused = false; try { attempt.run(); } catch (Exception expected) { refused = true; } require(refused,why);
    }
    private static final class Fixture implements AutoCloseable {
        final User user; final DB db; final Mind mind;
        Fixture(Path root,String name) throws Exception {
            user = new User(); user.setDatabaseDir(root.toString()+File.separator); db = new DB(); db.init(user);
            Mind m = new Mind(user); user.setCurrentMind(m); mind = (Mind)m.useStorage(name); user.setCurrentMind(mind);
        }
        public void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
    }
    private static DB.ProductionWriteResult write(Fixture fixture,String statement) throws Exception {
        return fixture.db.writeProductionCandidate(fixture.mind,statement,1000,12,16,WORK,100000,false);
    }
    public static void main(String[] args) {
        try {
            Path root = Files.createTempDirectory("production-provider-jar-"); System.setProperty("user.home",root.toString());
            long before,after;
            try (Fixture x = new Fixture(root,"X")) {
                require(x.mind.compile("!scalar(1);",null,false),"native physical fixture"); before = x.db.getRevision(); int rows = x.mind.getRules().size();
                ContextProductionCandidate.Evaluation trial = x.db.qualifyProductionCandidate(x.mind,"!@x scalar(x)->ready(x);",1000,12,16,WORK,100000);
                require(trial.status == ContextProductionFamilyAssembly.Status.FIXED_POINT,"public provider diagnosis qualifies actual current family");
                require(x.db.getRevision() == before && x.mind.getRules().size() == rows,"public diagnosis never prepublishes candidate");
                require(write(x,"!@x unknown(x);").status == DB.ProductionWriteStatus.INCOMPLETE,"public unsupported result cannot fall back to native authoring");
                require(write(x,"?scalar(1);").status == DB.ProductionWriteStatus.INCOMPLETE,"public writing rejects non-assertion requests");
                require(x.db.getRevision() == before && x.mind.getRules().size() == rows,"public declines preserve physical state");
                DB.ProductionWriteResult published = write(x,"!@x scalar(x)->ready(x);"); after = x.db.getRevision();
                require(published.status == DB.ProductionWriteStatus.PUBLISHED && after > before,"public production API reaches ordinary native publication");
                require(published.before.getRevision() == before && published.after.getRevision() == after && published.trial.artifact != null,"public receipt distinguishes trial from actual publication references");
                require(Boolean.TRUE.equals(x.mind.query("?ready(1);",null,false)),"normal native query observes published local consequence");
                reject(() -> x.db.writeProductionCandidate(new Mind(new User()),"!other(1);",1000,12,16,WORK,100000,false),"unrelated Mind cannot obtain provider publication authority");
                Mind pending = new Mind(x.mind);
                try { reject(() -> write(x,"!pending(1);"),"pending child reservation cannot be qualified as settled physical X"); }
                finally { x.mind.release(pending); }
            }
            try (Fixture reopened = new Fixture(root,"X")) {
                require(reopened.db.getRevision() == after && Boolean.TRUE.equals(reopened.mind.query("?ready(1);",null,false)),"public publication survives close/reopen");
            }
            try (Fixture historical = new Fixture(root,"X@"+after)) {
                require(historical.db.isReadOnly(),"historical fixture is immutable");
                reject(() -> write(historical,"!forbidden(1);"),"public bridge cannot write immutable revision");
            }
            try (Fixture a = new Fixture(root,"A")) { require(a.mind.compile("!@w p(w,1)->q(w,1);",null,false),"foreign source fixture"); }
            try (Fixture x = new Fixture(root,"Family")) {
                require(x.mind.compile("!$w p(w,1)&&flag(w,1); !@w p(w,1)->warm(w,1);",null,false),"family fixture"); x.db.connectContext("A");
                long revision = x.db.getRevision(); int rows = x.mind.getRules().size();
                DB.ProductionWriteResult collision = write(x,"!@w q(w,1)->~flag(w,1);");
                require(collision.status == DB.ProductionWriteStatus.COLLISION && collision.trial.artifact != null,"public bridge proves delayed family collision");
                require(x.db.getRevision() == revision && x.mind.getRules().size() == rows,"public family refutation precedes all physical changes");
            }
            System.out.println("CONTEXT_PRODUCTION_PROVIDER_JAR_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
