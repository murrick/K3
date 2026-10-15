/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Cross-replay origin sharing, proof-depth retention, and foreign-seed exclusion. */
public final class ContextWitnessResultTransportRunner {
    private static int checks;
    private static Source source(int id,boolean x) { return new Source(new UUID(0,x ? 0 : 1),1,id,x,"base"); }
    private static Source source(int id) { return source(id,id == 0); }
    private static Bundle bundle(Source source,String text) throws Exception { return ContextWitnessBundle.capture(new Declaration(source,text),10000); }
    private static Bundle bundle(int id,String text) throws Exception { return bundle(source(id),text); }
    private static Session session(Bundle... bundles) throws Exception { return ContextWitnessBundle.restoreObserved(Arrays.asList(bundles),10000,100000); }
    private static void rule(Session s,Source source,String text) throws Exception { s.capture.replay(s.mind,source,text); }
    private static void rule(Session s,int id,String text) throws Exception { rule(s,source(id),text); }
    private static Result export(Session s) throws Exception {
        s.mind.setQueryPass(QueryPass.ACCEPT); s.mind.link(null,false);
        return ContextWitnessResultTransport.export(s,16,4000000);
    }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Item item(Packet packet,String predicate) { return packet.items.stream().filter(i -> i.atom.predicate.equals(predicate)).findFirst().orElseThrow(() -> new AssertionError("No item: "+predicate)); }
    private static boolean proof(Item item,int depth,Source... sources) {
        Set<Source> expected = new LinkedHashSet<>(Arrays.asList(sources));
        return item.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    private static void incomplete(Result result,String why) { require(!result.exported && result.packet == null,why+": "+result.reason); }
    private static Mind memory() throws Exception { User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); return m; }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-transport-").toString());
            Packet packet;
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1)->q(x,1);"); rule(s,2,"!@x q(x,1)->r(x,1);"); rule(s,3,"!@x p(x,1)->r(x,1);");
                Result result = export(s); require(result.exported,"verified witness products export: "+result.reason); packet = result.packet;
                Origin origin = item(packet,"p").atom.arguments.get(0).origin;
                require(origin.source.equals(source(0)) && origin.binder == 0,"stable origin is sourced binder ordinal");
                require(origin.equals(item(packet,"q").atom.arguments.get(0).origin) && origin.equals(item(packet,"r").atom.arguments.get(0).origin),
                        "separate products retain their relation to X's primary witness");
                require(proof(item(packet,"p"),0,source(0)),"X seed retains primary depth0");
                require(proof(item(packet,"q"),1,source(0),source(1)),"derived q retains full sources and depth1");
                require(proof(item(packet,"r"),2,source(0),source(1),source(2)) && proof(item(packet,"r"),1,source(0),source(3)),
                        "alternative proofs retain independent source groups and depths");
                require(!proof(item(packet,"r"),0,source(0)) && !proof(item(packet,"r"),2,source(0),source(1),source(2),source(3)),
                        "transport cannot promote or union alternative proofs");
                incomplete(ContextWitnessResultTransport.export(s,0,4000000),"depth stop returns no packet");
                incomplete(ContextWitnessResultTransport.export(s,16,1),"work stop returns no packet");
            }
            Replay first = ContextWitnessResultTransport.restore(packet,10000), second = ContextWitnessResultTransport.restore(packet,10000);
            require(!first.namespace.equals(second.namespace) && first.witnesses.keySet().equals(second.witnesses.keySet()),
                    "fresh replay namespace distinguishes even numerically reused native IDs");
            require(first.witnesses.size() == 1 && first.seeds.equals(packet.items),"restoration retains one shared witness and the immutable proof ledger");
            require(proof(item(packet,"r"),2,source(0),source(1),source(2)),"repeated restore cannot reset depth2");
            require(!first.mind.compile("!@x ~(p(x,1)&&q(x,1));",null,false),"restored X and its product share the same witness");
            Mind naive = memory();
            require(naive.compile("!$x p(x,1); !$y q(y,1); !@x ~(p(x,1)&&q(x,1));",null,false),
                    "concrete countermodel: independent existential export loses the p/q relation");
            boolean immutable = false; try { packet.items.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"packet cannot be edited after verification");
            immutable = false; try { item(packet,"q").proofs.get(0).sources.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"whole proof source set is immutable");
            boolean stopped = false; try { ContextWitnessResultTransport.restore(packet,1); } catch (IllegalStateException expected) { stopped = true; }
            require(stopped,"restore work stop returns no partial replay");
            for (boolean reverse : new boolean[]{false,true}) {
                Bundle x = bundle(0,"!$x p(x,1);"), foreign = bundle(2,"!$y seed(y,1);");
                try (Session s = reverse ? session(foreign,x) : session(x,foreign)) {
                    rule(s,1,"!@x p(x,1)->q(x,1);"); rule(s,3,"!@x seed(x,1)->foreignout(x,1);"); Result result = export(s);
                    require(result.exported,"input order does not affect export: "+result.reason);
                    require(result.packet.items.stream().noneMatch(i -> i.atom.predicate.equals("seed") || i.atom.predicate.equals("foreignout")),
                            "foreign primary seed and foreign-only product stay out of X packet");
                    Origin origin = item(result.packet,"q").atom.arguments.get(0).origin;
                    require(origin.source.equals(source(0)) && origin.binder == 0,"origin ignores changed replay rule/term allocation");
                }
            }
            try (Session s = session(bundle(2,"!$y seed(y,1);"))) {
                rule(s,source(10,true),"!@x seed(x,1)->q(x,1);"); Result result = export(s);
                require(result.exported && result.packet.items.size() == 1,"X-dependent foreign-origin result exports without its foreign seed");
                require(item(result.packet,"q").atom.arguments.get(0).origin.source.equals(source(2)),"foreign origin remains exact, without false X ownership");
                require(proof(item(result.packet,"q"),1,source(2),source(10,true)),"foreign-origin product retains both authorities");
                require(ContextWitnessResultTransport.restore(result.packet,10000).witnesses.size() == 1,"foreign-origin result restores as its own witness");
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"),bundle(source(20,true),"!$y seed(y,1);"))) {
                rule(s,1,"!@x p(x,1)->q(x,1);"); rule(s,2,"!@x seed(x,1)->q(x,1);"); Result result = export(s);
                require(result.exported,"independent X origins export: "+result.reason);
                long qCount = result.packet.items.stream().filter(i -> i.atom.predicate.equals("q")).count();
                require(qCount == 2 && ContextWitnessResultTransport.restore(result.packet,10000).witnesses.size() == 2,
                        "same-shaped outputs from different primary declarations cannot merge");
            }
            try (Session s = session(bundle(0,"!$x p(x,John,1);"))) {
                rule(s,1,"!@x p(x,John,1)->~q(x,John,2);"); Result result = export(s);
                require(result.exported && !item(result.packet,"q").atom.positive,"signed result enters detached packet");
                Replay restored = ContextWitnessResultTransport.restore(result.packet,10000);
                require(restored.mind.compile("!@x q(x,Mary,2);",null,false),"different fixed semantic value remains distinct");
                require(!restored.mind.compile("!@x q(x,John,2);",null,false),"signed fixed values survive placeholder projection");
            }
            try (Session s = session(bundle(2,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1)->q(x,1);"); incomplete(export(s),"foreign-only proof cannot become an X packet");
            }
            List<Bundle> large = new ArrayList<>(); for (int i=0;i<9;++i) large.add(bundle(source(100+i,true),"!$x p"+i+"(x,1);"));
            try (Session s = session(large.toArray(new Bundle[0]))) {
                rule(s,1,"!@x p0(x,1)->q(x,1);"); Result result = export(s);
                incomplete(result,"rank bound cannot leak a packet prefix"); require(result.reason.contains("rank limit"),"rank refusal occurs at the transport boundary");
            }
            System.out.println("CONTEXT_WITNESS_RESULT_TRANSPORT_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
