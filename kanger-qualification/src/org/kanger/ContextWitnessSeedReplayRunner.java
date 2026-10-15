/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Actual native applications after imported witness proofs, without primary/depth reset. */
public final class ContextWitnessSeedReplayRunner {
    private static int checks;
    private static Source source(int context,int id,boolean x) { return new Source(new UUID(0,context),1,id,x,"base"); }
    private static Source x(int id) { return source(0,id,true); }
    private static Source a(int id) { return source(1,id,false); }
    private static Source b(int id) { return source(2,id,false); }
    private static Source c(int id) { return source(3,id,false); }
    private static Bundle bundle(Source source,String text) throws Exception { return ContextWitnessBundle.capture(new Declaration(source,text),10000); }
    private static Session session(Bundle... bundles) throws Exception { return ContextWitnessBundle.restoreObserved(Arrays.asList(bundles),10000,100000); }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Packet packet(Session session) throws Exception {
        session.mind.setQueryPass(QueryPass.ACCEPT); session.mind.link(null,false);
        Result result = ContextWitnessResultTransport.export(session,16,4000000);
        require(result.exported,"first pass exports: "+result.reason); return result.packet;
    }
    private static Item item(Packet packet,String predicate) { return packet.items.stream().filter(i -> i.atom.predicate.equals(predicate)).findFirst().orElseThrow(() -> new AssertionError("No item "+predicate)); }
    private static boolean proof(Item item,int depth,Source... sources) {
        Set<Source> expected = new LinkedHashSet<>(Arrays.asList(sources));
        return item.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    private static void incomplete(Result result,String why) { require(!result.exported && result.packet == null,why+": "+result.reason); }
    private static Result next(ContextWitnessSeedReplay round) { return ContextWitnessResultTransport.exportNext(round,16,4000000); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-seed-replay-").toString());
            Packet first;
            try (Session s = session(bundle(x(0),"!$x p(x,1) && flag(x,1);"))) {
                s.capture.replay(s.mind,a(1),"!@x p(x,1)->q(x,1);");
                s.capture.replay(s.mind,a(4),"!@x p(x,1)->q(x,1);"); first = packet(s);
            }
            Packet second;
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(b(2),"!@x q(x,1) && flag(x,1)->r(x,1);");
                incomplete(ContextWitnessResultTransport.exportNext(round,1,4000000),"carried q depth1 cannot yield r at depth1");
                boolean sealed = false; try { round.rule(b(9),"!@x r(x,1)->late(x,1);"); } catch (IllegalStateException expected) { sealed = true; }
                require(sealed,"native pass inputs are immutable after settlement, even after a verifier stop");
                Result result = next(round); require(result.exported,"second productive pass reconstructs: "+result.reason); second = result.packet;
                require(proof(item(second,"r"),2,x(0),a(1),b(2)) && proof(item(second,"r"),2,x(0),a(4),b(2)),
                        "native two-premise step carries both old alternatives to depth2");
                require(item(second,"r").proofs.size() == 2,"old proof alternatives do not become extra primary sources");
                require(!proof(item(second,"r"),1,x(0),b(2)) && !proof(item(second,"r"),2,x(0),a(1),a(4),b(2)),
                        "cannot erase A or merge A alternatives into one proof");
                require(item(first,"q").atom.arguments.get(0).origin.equals(item(second,"r").atom.arguments.get(0).origin),
                        "new product keeps canonical source/binder origin after fresh replay");
                require(proof(item(second,"q"),1,x(0),a(1)) && proof(item(second,"flag"),0,x(0)),"imported ledger is unchanged");
                incomplete(ContextWitnessResultTransport.exportNext(round,16,1),"work stop cannot expose a verified prefix");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(second,10000,100000)) {
                round.rule(c(3),"!@x r(x,1)->out(x,1);"); Result result = next(round);
                require(result.exported,"third productive pass reconstructs: "+result.reason);
                require(proof(item(result.packet,"out"),3,x(0),a(1),b(2),c(3))
                        && proof(item(result.packet,"out"),3,x(0),a(4),b(2),c(3)),"depth3 includes the complete cross-pass proof");
                require(item(result.packet,"out").proofs.stream().allMatch(p -> p.depth == 3),"native carrier's primary appearance cannot reset depth");
                require(item(result.packet,"out").atom.arguments.get(0).origin.equals(item(first,"p").atom.arguments.get(0).origin),
                        "origin remains stable over three fresh Minds");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(b(2),"!@x q(x,2)->r(x,1);"); incomplete(next(round),"no observed productive application is not a saturation certificate");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(b(2),"!@x q(x,1)->r(x,1);"); round.rule(c(3),"!@x r(x,1)->out(x,1);");
                incomplete(next(round),"two independent new foreign contexts cannot form a communal pass");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(source(99,2,true),"!@x q(x,1)->r(x,1);"); incomplete(next(round),"new producer cannot silently change the parent X context");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(new Source(new UUID(0,0),2,2,true,"base"),"!@x q(x,1)->r(x,1);"); incomplete(next(round),"X revision must match the carried source cut");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(new Source(new UUID(0,0),1,2,true,"changed"),"!@x q(x,1)->r(x,1);"); incomplete(next(round),"X effective view must match the carried source cut");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(source(0,2,false),"!@x q(x,1)->r(x,1);"); incomplete(next(round),"X cannot be relabelled as a foreign producer");
            }
            Packet foreignOrigin;
            try (Session s = session(bundle(a(5),"!$x seed(x,1);"))) {
                s.capture.replay(s.mind,x(10),"!@x seed(x,1)->q(x,1);"); foreignOrigin = packet(s);
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(foreignOrigin,10000,100000)) {
                round.rule(b(2),"!@x q(x,1)->r(x,1);"); Result result = next(round);
                require(result.exported && proof(item(result.packet,"r"),2,a(5),x(10),b(2)),"foreign-origin X-dependent seed remains a derived full proof");
                require(item(result.packet,"r").atom.arguments.get(0).origin.source.equals(a(5)),"foreign witness does not acquire false X origin");
                require(result.packet.items.stream().noneMatch(i -> i.atom.predicate.equals("seed")),"new pass does not resurrect excluded foreign primary facts");
            }
            Packet independent;
            try (Session s = session(bundle(x(0),"!$x p(x,1);"),bundle(x(20),"!$y seed(y,1);"))) {
                s.capture.replay(s.mind,a(1),"!@x p(x,1)->q(x,1);"); s.capture.replay(s.mind,a(2),"!@x seed(x,1)->r(x,1);"); independent = packet(s);
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(independent,10000,100000)) {
                round.rule(b(3),"!@x q(x,1)&&r(x,1)->out(x,1);"); round.rule(b(4),"!@x q(x,1)->tail(x,1);"); Result result = next(round);
                require(result.exported && proof(item(result.packet,"tail"),2,x(0),a(1),b(4)),"productive pass retains the correct independent origin");
                require(result.packet.items.stream().noneMatch(i -> i.atom.predicate.equals("out")),"independent imported witnesses cannot join through their common raw carrier Rule");
            }
            Packet signed;
            try (Session s = session(bundle(x(0),"!$x p(x,John,1);"))) {
                s.capture.replay(s.mind,a(1),"!@x p(x,John,1)->~q(x,John,2);"); signed = packet(s);
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(signed,10000,100000)) {
                round.rule(b(2),"!@x ~q(x,John,2)->r(x,John,3);");
                round.rule(b(3),"!@x ~q(x,Mary,2)->wrong(x,Mary,3);"); Result result = next(round);
                require(result.exported && proof(item(result.packet,"r"),2,x(0),a(1),b(2)),"signed imported premise carries sources/depth through a real native step");
                require(!item(result.packet,"q").atom.positive && result.packet.items.stream().noneMatch(i -> i.atom.predicate.equals("wrong")),
                        "sign and fixed semantic arguments cannot be changed by ledger import");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(b(2),"!$x unproved(x,1);"); round.rule(b(3),"!@x q(x,1)->r(x,1);");
                Result result = next(round); incomplete(result,"new raw existential seed is not authenticated by the carried ledger");
                require(result.reason.contains("producer-scope-unproved"),"new witness input is refused at producer scope");
            }
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(first,10000,1)) {
                boolean stopped = false;
                try { round.rule(b(2),"!@x q(x,1)->r(x,1);"); } catch (IllegalStateException expected) { stopped = true; }
                if (!stopped) incomplete(next(round),"event budget cannot publish partial next results");
                else require(stopped,"event stop occurs at the owning capture boundary");
                incomplete(next(round),"failed native/input capture cannot be retried into a verified prefix");
            }
            System.out.println("CONTEXT_WITNESS_SEED_REPLAY_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
