/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionFamilyAssembly.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessResultTransport.*;

/** Native inventory absence is evidence; an absent supplied declaration list is not. */
public final class ContextWitnessEmptyTargetRunner {
    private static final int WORK = 4000000;
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static void reject(Attempt attempt,String why) throws Exception {
        boolean rejected = false; try { attempt.run(); } catch (Exception expected) { rejected = true; } require(rejected,why);
    }
    private static Snapshot snapshot(int id,boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        if (!text.isEmpty()) require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,id),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"complete native binding"); return bound.snapshot;
    }
    private static Artifact assemble(Snapshot x,Snapshot... foreign) {
        ContextProductionFamilyAssembly.Result r = ContextProductionFamilyAssembly.assemble(x,Arrays.asList(foreign),WORK);
        require(r.assembled,"whole family assembly: "+r.reason); return r.artifact;
    }
    private static void refused(ContextWitnessResultTransport.Result r,String why) { require(!r.exported && r.packet == null,why+": "+r.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("empty-target-").toString());
            Snapshot x = snapshot(0,true,"!$w p(w,1);"),empty = snapshot(1,false,"");
            Artifact bare = assemble(x),both = assemble(x,empty);
            require(bare.evaluate(12,16,WORK,100000).status == Status.FIXED_POINT,"seed-only X is vacuously producer-closed");
            Evaluation fixed = both.evaluate(12,16,WORK,100000);
            require(fixed.status == Status.FIXED_POINT && fixed.qualification.fixedPoint.rounds.size() == 1,"both certified empty cuts settle in one unchanged barrier");
            require(both.rows.size() == 1 && both.rows.get(0).use == Use.X_SEEDS,"no fake row or primary rule is invented for empty foreign/self targets");
            ContextWitnessFamilyFixedPoint.Branch branch = both.plan.branches.get(1);
            ContextWitnessResultTransport.Result unchanged = ContextWitnessResultTransport.exportHornUnchanged(both.input,branch.cut,branch.declarations,16,WORK,100000,branch.emptyTarget);
            require(unchanged.exported && unchanged.packet.producers.isEmpty() && unchanged.packet.emptyTarget == branch.emptyTarget,"source-free unchanged packet carries actual inventory absence authority");
            require(ContextWitnessResultTransport.sameContents(both.input,unchanged.packet,WORK),"empty coverage cannot alter literal/source/depth evidence");
            refused(ContextWitnessResultTransport.exportHornUnchanged(both.input,branch.cut,Collections.emptyList(),16,WORK,100000),"plain empty declaration list is not proof of absence");
            Cut wrong = Cut.of(new Source(branch.cut.context,branch.cut.revision+1,0,false,branch.cut.view));
            refused(ContextWitnessResultTransport.exportHornUnchanged(both.input,wrong,Collections.emptyList(),16,WORK,100000,branch.emptyTarget),"different revision cannot reuse absence");
            Cut wrongView = Cut.of(new Source(branch.cut.context,branch.cut.revision,0,false,"another-view"));
            reject(() -> branch.emptyTarget.validate(wrongView,() -> {}),"different initialization/view cannot reuse absence");
            reject(() -> branch.emptyTarget.validate(bare.plan.branches.get(0).cut,() -> {}),"empty foreign cannot become self-X");
            Map<Cut,Packet> outputs = new LinkedHashMap<>();
            for (ContextWitnessFamilyFixedPoint.Branch b : both.plan.branches) {
                ContextWitnessResultTransport.Result out = ContextWitnessResultTransport.exportHornUnchanged(both.input,b.cut,b.declarations,16,WORK,100000,b.emptyTarget);
                require(out.exported,"each expected empty branch certifies"); outputs.put(b.cut,out.packet);
            }
            FamilyResult merged = ContextWitnessResultTransport.family(both.input,new ArrayList<>(outputs.keySet()),WORK).finish(outputs,WORK);
            require(merged.status == FamilyStatus.MERGED && !merged.changed && merged.packet.emptyTarget == null,"family validates exact empty targets then drops their local authority");
            require(ContextWitnessResultTransport.family(merged.packet,new ArrayList<>(outputs.keySet()),WORK).finish(outputs,WORK).status == FamilyStatus.INCOMPLETE,"old empty certificate cannot cross input barriers");
            outputs.remove(branch.cut);
            require(ContextWitnessResultTransport.family(both.input,Arrays.asList(bare.plan.branches.get(0).cut,branch.cut),WORK).finish(outputs,WORK).status == FamilyStatus.INCOMPLETE,"empty target cannot silently disappear from the family");
            Snapshot foreignRule = snapshot(2,false,"!@w p(w,1)->q(w,1);");
            require(assemble(x,foreignRule).evaluate(12,16,WORK,100000).status == Status.FIXED_POINT,"nonempty foreign inference works with empty self producer scope");
            Snapshot producerX = snapshot(3,true,"!$w p(w,1); !@w p(w,1)->warm(w,1);"); Artifact shared = assemble(producerX,empty);
            require(shared.evaluate(12,16,WORK,100000).status == Status.FIXED_POINT,"empty foreign still executes shared X producers when enabled");
            ContextWitnessFamilyFixedPoint.Branch sharedBranch = shared.plan.branches.get(1);
            refused(ContextWitnessResultTransport.exportHornUnchanged(shared.input,sharedBranch.cut,sharedBranch.declarations,16,WORK,100000,sharedBranch.emptyTarget),"absence of foreign rules cannot skip missing X warm proof");
            try (ContextWitnessSeedReplay r = ContextWitnessSeedReplay.open(shared.input,WORK,100000)) {
                for (Declaration d : sharedBranch.declarations) r.rule(d.source,d.statement);
                ContextWitnessResultTransport.Result productive = ContextWitnessResultTransport.exportRoundNext(r,sharedBranch.cut,sharedBranch.declarations,16,WORK,100000,sharedBranch.emptyTarget);
                require(productive.exported && productive.packet.producers.stream().allMatch(s -> s.localX),"shared production uses only real X sources plus inventory target authority");
            }
            reject(() -> ContextWitnessEmptyTarget.of(producerX,() -> {}),"X producer row cannot be certified absent");
            reject(() -> ContextWitnessEmptyTarget.of(foreignRule,() -> {}),"foreign primary declaration cannot be certified absent");
            Snapshot foreignFact = snapshot(4,false,"!private(1);");
            reject(() -> ContextWitnessEmptyTarget.of(foreignFact,() -> {}),"foreign facts are not empty producer coverage");
            Declaration forged = new Declaration(foreignRule.primary.get(0).source,foreignRule.primary.get(0).statement);
            refused(ContextWitnessResultTransport.exportHornUnchanged(both.input,branch.cut,Arrays.asList(forged),16,WORK,100000,branch.emptyTarget),"declared foreign knowledge contradicts empty target authority");
            Declaration xRule = new Declaration(producerX.primary.stream().filter(e -> e.statement.contains("@w")).findFirst().get().source,"!@w p(w,1)->warm(w,1);");
            refused(ContextWitnessResultTransport.exportHornUnchanged(bare.input,bare.plan.branches.get(0).cut,Arrays.asList(xRule),16,WORK,100000,bare.plan.branches.get(0).emptyTarget),"self empty certificate cannot carry a producer declaration");
            refused(ContextWitnessResultTransport.exportHornUnchanged(both.input,branch.cut,branch.declarations,16,1,100000,branch.emptyTarget),"empty verification obeys work bounds");
            require(assemble(snapshot(5,true,"!scalar(1);"),snapshot(6,false,"!@x scalar(x)->ready(x);")).evaluate(12,16,WORK,100000).status == Status.FIXED_POINT,"ordinary seed-only X needs no synthetic witness or self producer");
            require(!ContextProductionFamilyAssembly.assemble(snapshot(7,true,""),Collections.emptyList(),WORK).assembled,"entirely seed-free X remains outside current scope");
            System.out.println("CONTEXT_WITNESS_EMPTY_TARGET_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
