/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextOrdinaryResidualCoverage.*;

/** Measured native tuple restriction and source-bound proof-only observations. */
public final class ContextOrdinaryResidualProbesRunner {
    private static int checks,ids;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        if (!text.isEmpty()) require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"whole native binding"); return bound.snapshot;
    }
    private static List<Receipt> pass(Frontier f,boolean probes) {
        List<Receipt> result = new ArrayList<>();
        for (Snapshot cut : f.cuts) result.add(probes ? ContextOrdinaryResidualRounds.inspectWithProbes(f,cut,16,WORK,100000,64) : ContextOrdinaryResidualRounds.inspectWithParameters(f,cut,16,WORK,100000));
        require(result.stream().allMatch(r -> r.verified),"sound observations: "+result.stream().filter(r -> !r.verified).map(r -> r.reason).findFirst().orElse("")); return result;
    }
    private static Audit audit(Frontier f,List<Receipt> receipts,Mode mode) { return ContextOrdinaryResidualCoverage.audit(f,receipts,mode,16,WORK); }
    private static int[] measure(Snapshot x,Snapshot foreign,boolean relation) throws Exception {
        User u = new User(); Mind mind = new Mind(u); u.setCurrentMind(mind); mind.setQueryPass(org.kanger.enums.QueryPass.ACCEPT);
        List<Clause> full = new ArrayList<>();
        for (Snapshot part : Arrays.asList(x,foreign)) for (ContextProductionNativeBinding.Entry entry : part.primary) full.addAll(entry.clauses);
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,100000)) {
            capture.observeResidualSubsets();
            for (Snapshot part : Arrays.asList(x,foreign)) for (ContextProductionNativeBinding.Entry entry : part.primary)
                if (relation || !entry.statement.contains("rel(John")) capture.replay(mind,entry.source,entry.statement);
            List<SemanticTermSnapshot> parameters = ContextOrdinaryResidualParameters.seed(mind,full,full,ContextGroundProductionRounds.budget(WORK));
            List<SemanticTermSnapshot> basis = new ArrayList<>(parameters);
            for (Clause clause : full) for (Atom atom : clause.literals) for (Slot slot : atom.arguments)
                if (slot.variable < 0 && basis.stream().noneMatch(v -> v.semanticallyEquals(slot.value))) basis.add(slot.value);
            capture.auditRotations(mind,basis); mind.link(null,false);
            int constrainedTuples = mind.getRuleSolves().values().stream().flatMap(List::stream).mapToInt(tuple -> tuple.size() > 1 ? 1 : 0).sum();
            ContextProductionCapture.Snapshot observed = capture.snapshot(); RotationAudit rotation = observed.rotations.get(0);
            int generic = 0;
            for (Residual event : observed.residuals) if (event.conclusion.generalize(parameters).variables.size() == 2) ++generic;
            require(rotation.rank == 2 && rotation.expected == 25,"same five-value Cartesian basis for both controlled observations");
            System.out.println("RESIDUAL_NATIVE_VISITS relation="+relation+" tuples="+constrainedTuples+" expected="+rotation.expected+" admitted="+rotation.admitted+" visited="+rotation.visited+" generic2="+generic);
            return new int[]{rotation.admitted,rotation.visited,generic,constrainedTuples};
        }
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("residual-probes-").toString());
            Snapshot x = snapshot(true,"!@x @y anchor(1)&&rel(x,y)->out(x,y);"),foreign = snapshot(false,"!anchor(1); !rel(John,Mary);");
            int[] fullVisits = measure(x,foreign,true),subsetVisits = measure(x,foreign,false);
            require(fullVisits[0] == 1 && fullVisits[1] == 1 && fullVisits[2] == 0 && fullVisits[3] > 0,"concrete tuple restricts admission before native reduction observations");
            require(subsetVisits[0] == 25 && subsetVisits[1] == 25 && subsetVisits[2] > 0 && subsetVisits[3] == 0,"same candidate basis admits generic reductions when unrelated tuple is absent");
            Frontier input = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(foreign),WORK);
            require(!audit(input,pass(input,false),Mode.LOCAL_CLOSURE).covered,"previous full-context parameter observation gap remains reproducible");
            List<Receipt> probed = pass(input,true); Audit step = audit(input,probed,Mode.INPUT_STEP),closed = audit(input,probed,Mode.LOCAL_CLOSURE);
            require(step.covered && closed.covered,"isolated actual proofs close unchanged symbolic audits: "+step.reason+" / "+closed.reason);
            ContextProductionCapture.Source anchorSource = foreign.primary.stream().filter(e -> e.statement.contains("anchor")).findFirst().get().source;
            Set<Source> expected = new HashSet<>(Arrays.asList(x.primary.get(0).source,anchorSource));
            require(probed.get(1).products.stream().anyMatch(p -> p.clause.variables.size() == 2 && p.clause.literals.size() == 2 && p.proof.depth == 1 && p.proof.sources.equals(expected)),"two-variable generic theorem has exact smaller X/anchor proof at depth1");
            require(probed.get(1).owners.size() == 3 && probed.get(1).localProofs.stream().anyMatch(p -> p.proof.depth == 0 && p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("rel")),"complete original context and concrete relation remain in final receipt");
            require(input.products.isEmpty(),"isolated views never mutate the common input");
            Barrier first = ContextOrdinaryResidualRounds.merge(input,probed,WORK); require(first.status == Status.MERGED && first.changed,"actual probe proofs cross only a complete common barrier");
            List<Receipt> later = pass(first.frontier,true);
            require(audit(first.frontier,later,Mode.LOCAL_CLOSURE).covered,"generic proof preserves source/depth through carrier replay");
            Receipt limited = ContextOrdinaryResidualRounds.inspectWithProbes(input,foreign,16,WORK,100000,1);
            require(!limited.verified && limited.products.isEmpty() && limited.collisions.isEmpty() && limited.reason.contains("probe limit"),"probe stop withholds both baseline and successful earlier isolated proofs");
            Receipt eventStop = ContextOrdinaryResidualRounds.inspectWithProbes(input,foreign,16,WORK,1,64);
            require(!eventStop.verified && eventStop.products.isEmpty(),"shared event stop has no prefix");
            Receipt workStop = ContextOrdinaryResidualRounds.inspectWithProbes(input,foreign,16,1,100000,64);
            require(!workStop.verified && workStop.products.isEmpty(),"shared work stop has no prefix");
            Receipt depthStop = ContextOrdinaryResidualRounds.inspectWithProbes(input,foreign,0,WORK,100000,64);
            require(!depthStop.verified && depthStop.products.isEmpty(),"isolated view cannot reset depth");
            List<Receipt> missing = new ArrayList<>(probed); missing.remove(0);
            require(ContextOrdinaryResidualRounds.merge(input,missing,WORK).status == Status.INCOMPLETE,"missing self cannot publish probe prefix");
            require(!audit(first.frontier,probed,Mode.INPUT_STEP).covered,"probe receipts cannot be reused at a later input");
            Snapshot combinedSource = snapshot(false,"!anchor(1)&&rel(John,Mary);"); Frontier wholeEntry = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(combinedSource),WORK);
            List<Receipt> wholeEntryPass = pass(wholeEntry,true);
            require(audit(wholeEntry,wholeEntryPass,Mode.LOCAL_CLOSURE).covered && wholeEntryPass.get(1).owners.size() == 3,"conjunctive source retains every original branch in final full audit");
            require(wholeEntryPass.get(1).products.stream().anyMatch(p -> p.clause.variables.size() == 2 && p.proof.sources.equals(new HashSet<>(Arrays.asList(x.primary.get(0).source,combinedSource.primary.get(0).source)))),"clause projection preserves whole original source identity, never inventing an anchor-only source");
            Snapshot derivedAnchor = snapshot(false,"!seed(1); !@z seed(z)->anchor(z); !rel(John,Mary);");
            Frontier derived = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(derivedAnchor),WORK); List<Receipt> derivedPass = pass(derived,true);
            Audit derivedAudit = audit(derived,derivedPass,Mode.LOCAL_CLOSURE);
            require(derivedAudit.covered,"derived donor remains covered in a proof-only view: "+derivedAudit.reason);
            Set<Source> derivedSources = new HashSet<>(); derivedSources.add(x.primary.get(0).source);
            for (ContextProductionNativeBinding.Entry entry : derivedAnchor.primary) if (!entry.statement.contains("rel")) derivedSources.add(entry.source);
            require(derivedPass.get(1).products.stream().anyMatch(p -> p.clause.variables.size() == 2 && p.proof.depth == 2 && p.proof.sources.equals(derivedSources)),"replayed derived donor retains both foreign causes and adds one step instead of becoming depth0");
            Snapshot untouchedX = snapshot(true,"!unrelated(1);"),foreignRule = snapshot(false,"!@x @y anchor(1)&&rel(x,y)->out(x,y); !anchor(1); !rel(John,Mary);");
            Frontier pure = ContextOrdinaryResidualRounds.start(untouchedX,Collections.singletonList(foreignRule),WORK); List<Receipt> purePass = pass(pure,true);
            require(audit(pure,purePass,Mode.LOCAL_CLOSURE).covered && purePass.get(1).products.isEmpty(),"foreign-only isolated theorems remain local");
            Snapshot a = snapshot(false,"!anchor(1);"),b = snapshot(false,"!rel(John,Mary);");
            Frontier split = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b),WORK); Barrier split1 = ContextOrdinaryResidualRounds.merge(split,pass(split,true),WORK);
            Barrier split2 = ContextOrdinaryResidualRounds.merge(split1.frontier,pass(split1.frontier,true),WORK);
            require(split2.status == Status.MERGED && split2.frontier.products.stream().anyMatch(p -> p.clause.literals.size() == 1 && p.proof.depth == 2 && p.proof.sources.size() == 3),"independent sibling knowledge still requires a later common barrier");
            Frontier reversed = ContextOrdinaryResidualRounds.start(x,Arrays.asList(b,a),WORK); Barrier reversed1 = ContextOrdinaryResidualRounds.merge(reversed,pass(reversed,true),WORK);
            Barrier reversed2 = ContextOrdinaryResidualRounds.merge(reversed1.frontier,pass(reversed1.frontier,true),WORK);
            require(reversed2.status == Status.MERGED && reversed2.frontier.products.stream().anyMatch(p -> p.clause.literals.size() == 1 && p.proof.depth == 2 && p.proof.sources.size() == 3),"reversed sibling order retains the same cumulative consequence");
            Snapshot opposed = snapshot(false,"!~out(John,Mary);"); Frontier conflict = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b,opposed),WORK);
            Barrier conflict1 = ContextOrdinaryResidualRounds.merge(conflict,pass(conflict,true),WORK);
            Barrier conflict2 = ContextOrdinaryResidualRounds.merge(conflict1.frontier,pass(conflict1.frontier,true),WORK);
            Barrier conflict3 = ContextOrdinaryResidualRounds.merge(conflict2.frontier,pass(conflict2.frontier,true),WORK);
            require(conflict3.status == Status.COLLISION && conflict3.frontier == null && !conflict3.collisions.isEmpty(),"proof-only views never discard the full independent negative context during qualification");
            Snapshot negativeHead = snapshot(true,"!@x @y anchor(1)&&rel(x,y)->~out(x,y);"); Frontier negative = ContextOrdinaryResidualRounds.start(negativeHead,Collections.singletonList(foreign),WORK);
            require(audit(negative,pass(negative,true),Mode.LOCAL_CLOSURE).covered,"probe generalization preserves signed negative heads");
            Snapshot repeatedX = snapshot(true,"!@x edge(x,x)&&tag(1)->same(x);"),repeatedForeign = snapshot(false,"!tag(1); !edge(John,Mary);");
            Frontier repeated = ContextOrdinaryResidualRounds.start(repeatedX,Collections.singletonList(repeatedForeign),WORK); List<Receipt> repeatedPass = pass(repeated,true);
            require(audit(repeated,repeatedPass,Mode.LOCAL_CLOSURE).covered && repeatedPass.get(1).products.stream().noneMatch(p -> p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("same")),"isolated generic proof preserves repeated-variable equality without inventing edge(John,John)");
            Snapshot rawPositive = snapshot(false,"!p(1);"),rawNegative = snapshot(false,"!~p(1);");
            Frontier rawSiblings = ContextOrdinaryResidualRounds.start(untouchedX,Arrays.asList(rawPositive,rawNegative),WORK); List<Receipt> rawPass = pass(rawSiblings,true);
            Barrier rawMerge = ContextOrdinaryResidualRounds.merge(rawSiblings,rawPass,WORK);
            require(audit(rawSiblings,rawPass,Mode.LOCAL_CLOSURE).covered && rawMerge.status == Status.MERGED && rawMerge.frontier.products.isEmpty(),"raw sibling opposition never becomes a shared probe premise or collision");
            System.out.println("CONTEXT_ORDINARY_RESIDUAL_PROBES_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
