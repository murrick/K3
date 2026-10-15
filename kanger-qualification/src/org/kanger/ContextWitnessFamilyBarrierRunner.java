/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** A sealed common-input family, proof alternatives, and cross-branch direct opposition. */
public final class ContextWitnessFamilyBarrierRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static Source source(int context,int id,boolean x) { return new Source(new UUID(0,context),1,id,x,"base"); }
    private static Source x(int id) { return source(0,id,true); }
    private static Source a(int id) { return source(1,id,false); }
    private static Source b(int id) { return source(2,id,false); }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Bundle bundle(Source source,String text) throws Exception { return ContextWitnessBundle.capture(new Declaration(source,text),10000); }
    private static Packet base(boolean independent) throws Exception {
        List<Bundle> bundles = new ArrayList<>(); bundles.add(bundle(x(0),"!$x p(x,1) && flag(x,1);"));
        if (independent) bundles.add(bundle(x(20),"!$y seed(y,1);"));
        try (Session s = ContextWitnessBundle.restoreObserved(bundles,10000,100000)) {
            s.capture.replay(s.mind,x(10),"!@x p(x,1)->warm(x,1);");
            s.mind.setQueryPass(QueryPass.ACCEPT); s.mind.link(null,false);
            Result result = ContextWitnessResultTransport.export(s,16,WORK);
            require(result.exported,"base exports: "+result.reason); return result.packet;
        }
    }
    private static Packet step(Packet input,Source source,String rule) throws Exception {
        try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(input,10000,100000)) {
            round.rule(source,rule); Result result = ContextWitnessResultTransport.exportNext(round,16,WORK);
            require(result.exported,"branch exports: "+result.reason); return result.packet;
        }
    }
    private static Map<Cut,Packet> outputs(Packet left,Packet right) {
        Map<Cut,Packet> map = new LinkedHashMap<>(); map.put(Cut.of(a(1)),left); map.put(Cut.of(b(2)),right); return map;
    }
    private static Family family(Packet input) { return ContextWitnessResultTransport.family(input,Arrays.asList(Cut.of(a(1)),Cut.of(b(2))),WORK); }
    private static Item item(Packet packet,String predicate) { return packet.items.stream().filter(i -> i.atom.predicate.equals(predicate)).findFirst().orElseThrow(() -> new AssertionError("Missing "+predicate)); }
    private static boolean proof(Item item,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return item.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    private static void incomplete(FamilyResult result,String why) {
        require(result.status == FamilyStatus.INCOMPLETE && result.packet == null && result.conflicts.isEmpty() && !result.changed,why+": "+result.reason);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-family-").toString());
            Packet base = base(false);
            Packet positive = step(base,a(1),"!@x p(x,1)->q(x,1);");
            Packet negative = step(base,b(2),"!@x p(x,1)->~q(x,1);");
            require(positive.items.stream().noneMatch(i -> !i.atom.positive),"positive branch alone has no negative literal");
            require(negative.items.stream().noneMatch(i -> i.atom.predicate.equals("q") && i.atom.positive),"negative branch alone has no positive q");
            List<Cut> mutable = new ArrayList<>(Arrays.asList(Cut.of(a(1)),Cut.of(b(2))));
            Family sealed = ContextWitnessResultTransport.family(base,mutable,WORK); mutable.clear();
            require(sealed.expected.size() == 2,"expected family is copied before settlement");
            FamilyResult conflict = sealed.finish(outputs(positive,negative),WORK);
            require(conflict.status == FamilyStatus.CONFLICT && conflict.packet == null && conflict.conflicts.size() == 1,"individually consistent branches prove a cross-branch contradiction");
            Opposition opposition = conflict.conflicts.get(0);
            Item pos = opposition.left.atom.positive ? opposition.left : opposition.right;
            Item neg = opposition.left.atom.positive ? opposition.right : opposition.left;
            require(proof(pos,1,x(0),a(1)) && proof(neg,1,x(0),b(2)),"opposition retains both whole source proofs");
            require(pos.atom.arguments.get(0).origin.equals(neg.atom.arguments.get(0).origin),"opposition refers to the same original witness");
            Map<Cut,Packet> reversed = new LinkedHashMap<>(); reversed.put(Cut.of(b(2)),negative); reversed.put(Cut.of(a(1)),positive);
            Family reverseFamily = ContextWitnessResultTransport.family(base,Arrays.asList(Cut.of(b(2)),Cut.of(a(1))),WORK);
            require(reverseFamily.finish(reversed,WORK).status == FamilyStatus.CONFLICT,"branch order does not hide contradiction");
            Packet r = step(base,b(2),"!@x p(x,1)->r(x,1);");
            FamilyResult merged = family(base).finish(outputs(positive,r),WORK);
            require(merged.status == FamilyStatus.MERGED && merged.packet != null && merged.changed && merged.conflicts.isEmpty(),"complete positive family yields a changed quarantined pool");
            require(proof(item(merged.packet,"q"),1,x(0),a(1)) && proof(item(merged.packet,"r"),1,x(0),b(2)),"merge preserves independent branch proofs");
            require(item(merged.packet,"q").atom.arguments.get(0).origin.equals(item(merged.packet,"r").atom.arguments.get(0).origin),"merge preserves common X binder");
            Packet joined = step(merged.packet,source(3,3,false),"!@x q(x,1)&&r(x,1)->out(x,1);");
            require(proof(item(joined,"out"),2,x(0),a(1),b(2),source(3,3,false)),"next real native pass joins the transported results with full depth2 evidence");
            reversed.put(Cut.of(b(2)),r);
            FamilyResult reordered = reverseFamily.finish(reversed,WORK);
            require(reordered.status == FamilyStatus.MERGED && ContextWitnessResultTransport.sameContents(merged.packet,reordered.packet,WORK),"semantic/proof result ignores branch order and packet allocation");
            require(ContextWitnessResultTransport.sameContents(base,base,WORK),"content equality is reflexive");
            Packet same = step(base,b(2),"!@x p(x,1)->q(x,1);");
            FamilyResult alternatives = family(base).finish(outputs(positive,same),WORK);
            require(alternatives.status == FamilyStatus.MERGED && item(alternatives.packet,"q").proofs.size() == 2,"one atom retains two alternative whole proofs");
            require(!proof(item(alternatives.packet,"q"),1,x(0),a(1),b(2)),"alternative proofs are never unioned into a fictitious joint proof");
            require(!ContextWitnessResultTransport.sameContents(positive,alternatives.packet,WORK),"a new proof alternative counts as changed content even without a new atom");
            Packet self = step(base,x(11),"!@x p(x,1)->q(x,1);");
            Map<Cut,Packet> selfOutput = Collections.singletonMap(Cut.of(x(11)),self);
            require(ContextWitnessResultTransport.family(base,Collections.singletonList(Cut.of(x(0))),WORK).finish(selfOutput,WORK).status == FamilyStatus.MERGED,
                    "self-X branch matches context/revision/view independently of declaration id");
            incomplete(family(base).finish(outputs(positive,self),WORK),"X-only producer cannot masquerade as foreign branch");
            Packet[] shared = new Packet[2];
            for (int i=0;i<2;++i) try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(base,10000,100000)) {
                round.rule(x(11),"!@x p(x,1)->q(x,1);");
                round.rule(i == 0 ? a(9) : b(9),"!@x unused(x,1)->extra(x,1);");
                Result result = ContextWitnessResultTransport.exportNext(round,16,WORK);
                require(result.exported,"shared X producer branch exports: "+result.reason); shared[i] = result.packet;
            }
            FamilyResult dedup = family(base).finish(outputs(shared[0],shared[1]),WORK);
            require(dedup.status == FamilyStatus.MERGED && item(dedup.packet,"q").proofs.size() == 1 && proof(item(dedup.packet,"q"),1,x(0),x(11)),"identical proofs deduplicate without unused foreign sources");
            Packet separate = base(true);
            Packet sq = step(separate,a(1),"!@x p(x,1)->q(x,1);");
            Packet sn = step(separate,b(2),"!@x seed(x,1)->~q(x,1);");
            FamilyResult independent = family(separate).finish(outputs(sq,sn),WORK);
            require(independent.status == FamilyStatus.MERGED && independent.conflicts.isEmpty(),"opposite predicates at independent witnesses are compatible");
            require(independent.packet.items.stream().filter(i -> i.atom.predicate.equals("q")).count() == 2,"independent q atoms remain separate");
            // No prefix/evidence can escape an incomplete barrier, even when another branch proves opposition.
            Map<Cut,Packet> missing = outputs(positive,negative); missing.remove(Cut.of(b(2)));
            incomplete(family(base).finish(missing,WORK),"missing branch");
            incomplete(family(base).finish(outputs(negative,positive),WORK),"branch labels swapped");
            incomplete(family(base).finish(outputs(positive,positive),WORK),"one branch reused for another cut");
            incomplete(family(base).finish(outputs(positive,base),WORK),"ancestor input cannot masquerade as observed step");
            incomplete(family(base).finish(outputs(positive,merged.packet),WORK),"family merge cannot masquerade as observed step");
            Packet equivalent = base(false);
            require(ContextWitnessResultTransport.sameContents(base,equivalent,WORK),"separately allocated base has identical content");
            Packet wrongBarrier = step(equivalent,b(2),"!@x p(x,1)->r(x,1);");
            incomplete(family(base).finish(outputs(positive,wrongBarrier),WORK),"equivalent content does not authenticate the exact input barrier");
            Packet wrongRevision = step(base,new Source(new UUID(0,2),2,2,false,"base"),"!@x p(x,1)->r(x,1);");
            incomplete(family(base).finish(outputs(positive,wrongRevision),WORK),"foreign revision must match expected cut");
            Packet wrongView = step(base,new Source(new UUID(0,2),1,2,false,"changed"),"!@x p(x,1)->r(x,1);");
            incomplete(family(base).finish(outputs(positive,wrongView),WORK),"foreign effective view must match expected cut");
            boolean duplicate = false;
            try { ContextWitnessResultTransport.family(base,Arrays.asList(Cut.of(a(1)),Cut.of(new Source(new UUID(0,1),2,2,false,"changed"))),WORK); }
            catch (IllegalArgumentException expected) { duplicate = true; }
            require(duplicate,"one context cannot enter family at two revisions/views");
            incomplete(family(base).finish(outputs(positive,negative),1),"work stop exposes neither candidate nor conflict prefix");
            Packet[] large = new Packet[2];
            for (int i=0;i<2;++i) try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(base,10000,100000)) {
                for (int n=0;n<32;++n) round.rule(i == 0 ? a(100+n) : b(100+n),"!@x p(x,1)->"+(i == 0 ? "aa" : "bb")+n+"(x,1);");
                Result result = ContextWitnessResultTransport.exportNext(round,16,WORK);
                require(result.exported,"bounded individual large branch: "+result.reason); large[i] = result.packet;
            }
            FamilyResult overflow = family(base).finish(outputs(large[0],large[1]),WORK);
            incomplete(overflow,"family union exceeds atom bound");
            require(overflow.reason.contains("atom limit"),"union stop reports precise bound");
            System.out.println("CONTEXT_WITNESS_FAMILY_BARRIER_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
