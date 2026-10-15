/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextProductionFamilyAssembly.Artifact;

/** A common-input round certificate is weaker than closure, but stronger than sound observations. */
public final class ContextWitnessRoundCoverageRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static ContextProductionNativeBinding.Snapshot snapshot(int id,boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,id),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"full native binding"); return bound.snapshot;
    }
    private static ContextWitnessSeedReplay round(Packet input,List<Declaration> rules) throws Exception {
        ContextWitnessSeedReplay r = ContextWitnessSeedReplay.open(input,WORK,100000);
        for (Declaration d : rules) r.rule(d.source,d.statement); return r;
    }
    private static boolean has(Packet packet,String predicate) { return packet.items.stream().anyMatch(i -> i.atom.predicate.equals(predicate)); }
    private static void refused(Result result,String why) { require(!result.exported && result.packet == null,why+": "+result.reason); }
    private static void missing(Packet input,Packet output,Cut cut,List<Declaration> rules,String why) throws Exception {
        List<Input> definitions = ContextWitnessDeclarationCut.capture(input,cut,rules,() -> {},WORK,100000);
        boolean missing = false;
        try { ContextWitnessHornClosure.verifyRound(input,output,definitions,16,() -> {}); }
        catch (IllegalStateException expected) { missing = true; }
        require(missing,why);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("round-coverage-").toString());
            ContextProductionNativeBinding.Snapshot x = snapshot(0,true,"!$w p(w,1); !scalar(1); !@w p(w,1)->warm(w,1);");
            ContextProductionNativeBinding.Snapshot a = snapshot(1,false,"!@x scalar(x)->gate(x); !@w p(w,1)&&gate(1)->bridged(w,1);");
            Artifact family = ContextProductionFamilyAssembly.assemble(x,Arrays.asList(a),WORK).artifact;
            require(family != null,"whole family assembled");
            ContextWitnessFamilyFixedPoint.Branch branch = family.plan.branches.get(1);
            Packet first;
            try (ContextWitnessSeedReplay r = round(family.input,branch.declarations)) {
                Result strict = ContextWitnessResultTransport.exportCoveredNext(r,branch.cut,branch.declarations,16,WORK,100000);
                refused(strict,"first native output is not a closed proof pool");
                require(strict.reason.contains("Missing Horn conclusion instance"),"concrete output-enabled bridge is absent");
                Result step = ContextWitnessResultTransport.exportRoundNext(r,branch.cut,branch.declarations,16,WORK,100000);
                require(step.exported,"all common-input obligations have actual native proofs: "+step.reason); first = step.packet;
                require(has(first,"gate") && !has(first,"bridged"),"round checker never synthesizes the missing native bridge");
                require(first.observedStep && first.coveredCut == null && branch.cut.equals(first.roundCut) && first.emptyCut == null,
                        "round coverage has a distinct tag and never claims closure or emptiness");
                require(first.basis.equals(family.input.identity),"round coverage binds exact common input");
                require(ContextWitnessResultTransport.retains(family.input,first,() -> {}),"all imported proof groups survive");
                refused(ContextWitnessResultTransport.exportRoundNext(r,branch.cut,Collections.singletonList(branch.declarations.get(0)),16,WORK,100000),"omitted owning rows refuse");
                List<Declaration> changed = new ArrayList<>(branch.declarations); Declaration original = changed.get(changed.size()-1);
                changed.set(changed.size()-1,new Declaration(original.source,"!@w p(w,1)->other(w,1);"));
                refused(ContextWitnessResultTransport.exportRoundNext(r,branch.cut,changed,16,WORK,100000),"same source cannot hide another whole rule");
                refused(ContextWitnessResultTransport.exportRoundNext(r,Cut.of(new Source(new UUID(0,2),1,1,false,"other")),branch.declarations,16,WORK,100000),"wrong cut refuses");
                refused(ContextWitnessResultTransport.exportRoundNext(r,branch.cut,branch.declarations,0,WORK,100000),"depth stop publishes no step prefix");
                refused(ContextWitnessResultTransport.exportRoundNext(r,branch.cut,branch.declarations,16,1,100000),"work stop publishes no step prefix");
                refused(ContextWitnessResultTransport.exportRoundNext(r,branch.cut,branch.declarations,16,WORK,1),"event stop publishes no step prefix");
            }
            missing(family.input,family.input,branch.cut,branch.declarations,"sound initial pool cannot hide enabled scalar->gate");
            missing(first,first,branch.cut,branch.declarations,"deferred bridge becomes mandatory at the next barrier");
            missing(first,family.input,branch.cut,branch.declarations,"dropping a carried input proof refuses round coverage");
            Packet second;
            try (ContextWitnessSeedReplay r = round(first,branch.declarations)) {
                Result step = ContextWitnessResultTransport.exportRoundNext(r,branch.cut,branch.declarations,16,WORK,100000);
                require(step.exported && has(step.packet,"bridged"),"next native pass supplies the actual bridge"); second = step.packet;
                Result strict = ContextWitnessResultTransport.exportCoveredNext(r,branch.cut,branch.declarations,16,WORK,100000);
                require(strict.exported && strict.packet.coveredCut != null && strict.packet.roundCut == null,"full closure remains a separate certificate");
            }
            ContextWitnessResultTransport.FamilyResult stale = ContextWitnessResultTransport.family(first,Collections.singletonList(branch.cut),WORK)
                    .finish(Collections.singletonMap(branch.cut,first),WORK);
            require(stale.status == FamilyStatus.INCOMPLETE,"round tag cannot cross input identities");
            ContextWitnessResultTransport.FamilyResult merged = ContextWitnessResultTransport.family(first,Collections.singletonList(branch.cut),WORK)
                    .finish(Collections.singletonMap(branch.cut,second),WORK);
            require(merged.status == FamilyStatus.MERGED && merged.packet.roundCut == null && merged.packet.coveredCut == null,"merge invalidates both branch coverage tags");
            ContextWitnessFamilyFixedPoint.Result fixed = family.plan.run(family.input,new ContextWitnessFamilyFixedPoint.Limits(12,16,WORK,100000));
            require(fixed.status == ContextWitnessFamilyFixedPoint.Status.FIXED_POINT && fixed.rounds.size() == 3,"two productive barriers followed by full unchanged closure");
            require(fixed.rounds.get(0).changed && fixed.rounds.get(1).changed && !fixed.rounds.get(2).changed,"one step is never a fixed point");
            List<ContextWitnessFamilyFixedPoint.Branch> reversed = new ArrayList<>(family.plan.branches); Collections.reverse(reversed);
            ContextWitnessFamilyFixedPoint.Result reverse = ContextWitnessFamilyFixedPoint.plan(reversed).run(family.input,new ContextWitnessFamilyFixedPoint.Limits(12,16,WORK,100000));
            require(reverse.status == fixed.status && reverse.rounds.size() == fixed.rounds.size() && ContextWitnessResultTransport.sameContents(reverse.packet,fixed.packet,WORK),"branch ordering preserves all proofs and round count");
            ContextWitnessSeedReplay closed = round(family.input,branch.declarations); closed.close();
            refused(ContextWitnessResultTransport.exportRoundNext(closed,branch.cut,branch.declarations,16,WORK,100000),"closed owner refuses round coverage");
            System.out.println("CONTEXT_WITNESS_ROUND_COVERAGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
