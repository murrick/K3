/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Shared-vocabulary dormant Horn cuts, proof alternatives, and activation after a barrier. */
public final class ContextWitnessDormantCoverageRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static Source s(int c,int n) { return new Source(new UUID(0,c),1,n,c == 0,"base"); }
    private static Declaration d(Source source,String text) { return new Declaration(source,text); }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Packet base(boolean independent) throws Exception {
        List<Bundle> bundles = new ArrayList<>();
        bundles.add(ContextWitnessBundle.capture(d(s(0,0),independent ? "!$w p(w,1);" : "!$w p(w,1)&&flag(w,1);"),10000));
        if (independent) bundles.add(ContextWitnessBundle.capture(d(s(0,20),"!$v flag(v,1);"),10000));
        try (Session r = ContextWitnessBundle.restoreObserved(bundles,10000,100000)) {
            r.capture.replay(r.mind,s(0,10),"!@w p(w,1)->warm(w,1);"); r.mind.setQueryPass(QueryPass.ACCEPT); r.mind.link(null,false);
            Result result = ContextWitnessResultTransport.export(r,16,WORK); require(result.exported,"base exports"); return result.packet;
        }
    }
    private static Packet step(Packet input,Source source,String rule) throws Exception {
        try (ContextWitnessSeedReplay r = ContextWitnessSeedReplay.open(input,10000,100000)) {
            r.rule(source,rule); Result result = ContextWitnessResultTransport.exportNext(r,16,WORK);
            require(result.exported,"native productive pass: "+result.reason); return result.packet;
        }
    }
    private static Result empty(Packet input,Declaration... rules) {
        return ContextWitnessResultTransport.exportDormantEmpty(input,Cut.of(s(2,2)),Arrays.asList(rules),16,WORK,100000);
    }
    private static void refused(Result result,String why) { require(!result.exported && result.packet == null,why+": "+result.reason); }
    private static Item item(Packet input,String predicate) { return input.items.stream().filter(i -> i.atom.predicate.equals(predicate)).findFirst().orElseThrow(() -> new AssertionError(predicate)); }
    private static boolean proof(Item item,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return item.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    private static FamilyResult merge(Packet input,Packet a,Packet b) {
        Map<Cut,Packet> outputs = new LinkedHashMap<>(); outputs.put(Cut.of(s(1,1)),a); outputs.put(Cut.of(s(2,2)),b);
        return ContextWitnessResultTransport.family(input,new ArrayList<>(outputs.keySet()),WORK).finish(outputs,WORK);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-dormant-").toString());
            Packet base = base(false); Declaration waiting = d(s(2,2),"!@w q(w,1)&&flag(w,1)->out(w,1);");
            Result dormant = empty(base,waiting);
            require(dormant.exported,"shared-vocabulary join is proved dormant before q exists: "+dormant.reason);
            refused(ContextWitnessResultTransport.exportDisjointEmpty(base,Cut.of(s(2,2)),Arrays.asList(waiting),16,WORK,100000),"this extension was outside the disjoint certificate");
            require(ContextWitnessResultTransport.sameContents(base,dormant.packet,WORK),"dormant step preserves atoms and all source/depth alternatives");
            require(!dormant.packet.observedStep && dormant.packet.emptyCut.equals(Cut.of(s(2,2))) && dormant.packet.basis.equals(base.identity),"empty kind/cut/barrier are explicit");
            require(dormant.packet.producers.equals(Collections.singleton(s(2,2))),"actual captured producer source is sealed");
            try (ContextWitnessSeedReplay r = ContextWitnessSeedReplay.open(base,10000,100000)) {
                r.rule(waiting.source,waiting.statement); refused(ContextWitnessResultTransport.exportNext(r,16,WORK),"absence of native applications alone remains incomplete");
            }
            Packet a = step(base,s(1,1),"!@w p(w,1)->q(w,1);");
            FamilyResult round1 = merge(base,a,dormant.packet);
            require(round1.status == FamilyStatus.MERGED && round1.changed,"productive A and dormant B complete the same immutable round");
            require(round1.packet.items.stream().noneMatch(i -> i.atom.predicate.equals("out")),"B cannot see A's same-round q early");
            refused(empty(round1.packet,waiting),"fresh next-round coverage sees q and refuses a false empty certificate");
            FamilyResult stale = ContextWitnessResultTransport.family(round1.packet,Collections.singletonList(Cut.of(s(2,2))),WORK)
                    .finish(Collections.singletonMap(Cut.of(s(2,2)),dormant.packet),WORK);
            require(stale.status == FamilyStatus.INCOMPLETE && stale.packet == null,"old dormant certificate cannot cross the barrier");
            Packet round2 = step(round1.packet,s(2,2),waiting.statement);
            require(proof(item(round2,"out"),2,s(0,0),s(1,1),s(2,2)),"next actual native pass derives out with depth2 and full cross-round sources");
            // Same atoms do not mean the same proof pool.
            Packet secondProof = step(a,s(2,3),"!@w p(w,1)->q(w,1);");
            require(secondProof.items.size() == a.items.size() && !ContextWitnessResultTransport.sameContents(a,secondProof,WORK),"native counterexample: unchanged atom pool gains a proof alternative");
            require(item(secondProof,"q").proofs.size() == 2 && proof(item(secondProof,"q"),1,s(0,0),s(1,1)) && proof(item(secondProof,"q"),1,s(0,0),s(2,3)),"both complete independent authorities survive");
            refused(empty(a,d(s(2,3),"!@w p(w,1)->q(w,1);")),"enabled body refuses emptiness even when its head is already known");
            require(empty(base,d(s(2,2),"!@w p(w,1)&&missing(w,1)->q(w,1);"),d(s(2,3),"!@w q(w,1)->missing(w,1);")).exported,"mutually interacting rules stay dormant when their cycle has no trigger");
            require(empty(base,d(s(2,2),"!@w p(w,1)&&flag(w,2)->q(w,1);")).exported,"fixed-slot mismatch proves a shared-predicate join disabled");
            refused(empty(base,d(s(2,2),"!@w p(w,1.0)&&flag(w,1)->q(w,1);")),"semantic numeric equality cannot invent a fixed-slot mismatch");
            require(empty(base,d(s(2,2),"!@w p(w,w)->q(w,1);")).exported,"repeated variable cannot bind both a witness and a fixed value");
            Packet separate = base(true);
            require(empty(separate,d(s(2,2),"!@w p(w,1)&&flag(w,1)->q(w,1);")).exported,"independent origins do not satisfy one shared-variable body");
            refused(empty(separate,d(s(2,2),"!@x @y p(x,1)&&flag(y,1)->q(x,1);")),"independent universal variables can legitimately join different witnesses");
            refused(empty(base,d(s(2,2),"!@x @y p(x,1)->q(y,1);")),"enabled body with a head-only variable is never certified empty");
            // A negative carried premise can enable contraposition with no positive antecedent.
            Packet signed = step(base,s(1,4),"!@w p(w,1)->~n(w,1);");
            Declaration contraposition = d(s(2,2),"!@w missing(w,1)->n(w,1);");
            refused(empty(signed,contraposition),"negative packet is outside dormant positive Horn theorem");
            Packet negativeOut = step(signed,s(2,2),contraposition.statement);
            require(!item(negativeOut,"missing").atom.positive && proof(item(negativeOut,"missing"),2,s(0,0),s(1,4),s(2,2)),"native counterexample confirms the excluded contraposition");
            require(ContextWitnessResultTransport.exportDisjointEmpty(signed,Cut.of(s(2,2)),Arrays.asList(d(s(2,2),"!@x aa(x,1)->bb(x,1);")),16,WORK,100000).exported,"existing disjoint certificate retains its signed-packet scope");
            refused(empty(base,d(s(2,2),"!@w p(w,1)->~q(w,1);")),"negative head refused");
            refused(empty(base,d(s(2,2),"!@w ~p(w,1)->q(w,1);")),"negative antecedent refused");
            refused(empty(base,d(s(2,2),"!@w q(w,1);")),"universal unit refused");
            refused(empty(base,d(s(2,2),"!q(17,1);")),"ground unit refused");
            refused(empty(base,waiting,d(s(2,3),"!$w seed(w,1);")),"unsupported later declaration suppresses any empty prefix");
            refused(empty(base,d(s(2,2),"!@w missing(w,1)->q(w+1,1);")),"function term refused");
            refused(empty(base,waiting,d(s(3,3),"!@w missing(w,1)->p(w,1);")),"raw sibling declaration refused");
            refused(empty(base,d(new Source(new UUID(0,2),2,2,false,"base"),waiting.statement)),"wrong target revision refused");
            refused(empty(base,d(new Source(new UUID(0,2),1,2,false,"changed"),waiting.statement)),"wrong target view refused");
            refused(empty(base,waiting,waiting),"duplicate declaration source refused"); refused(empty(base),"missing declaration snapshot refused");
            refused(empty(base,waiting,d(s(0,30),"!@w p(w,1)->q(w,1);")),"all supplied X producers participate in the enabled-body check");
            refused(ContextWitnessResultTransport.exportDormantEmpty(base,Cut.of(s(2,2)),Arrays.asList(waiting),0,WORK,100000),"carried derived depth is checked");
            refused(ContextWitnessResultTransport.exportDormantEmpty(base,Cut.of(s(2,2)),Arrays.asList(waiting),16,1,100000),"work stop yields no packet");
            refused(ContextWitnessResultTransport.exportDormantEmpty(base,Cut.of(s(2,2)),Arrays.asList(waiting,d(s(2,3),"!@w missing(w,1)->q(w,1);")),16,WORK,1),"event stop yields no packet");
            Result self = ContextWitnessResultTransport.exportDormantEmpty(base,Cut.of(s(0,0)),Arrays.asList(d(s(0,30),waiting.statement)),16,WORK,100000);
            require(self.exported,"explicit self-X rule cut can also be proved dormant");
            Result ea = ContextWitnessResultTransport.exportDormantEmpty(base,Cut.of(s(1,1)),Arrays.asList(d(s(1,1),"!@w p(w,1)&&missing(w,1)->q(w,1);")),16,WORK,100000);
            require(ea.exported,"second empty branch exports");
            FamilyResult allEmpty = merge(base,ea.packet,dormant.packet);
            require(allEmpty.status == FamilyStatus.MERGED && !allEmpty.changed,"all dormant branches leave the entire proof pool unchanged");
            System.out.println("CONTEXT_WITNESS_DORMANT_COVERAGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
