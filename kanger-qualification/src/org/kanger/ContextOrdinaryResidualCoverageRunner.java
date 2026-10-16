/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextOrdinaryResidualCoverage.*;

/** Symbolic partial-reduction coverage, omitted native proofs and exact common barriers. */
public final class ContextOrdinaryResidualCoverageRunner {
    private static int checks,ids;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        if (!text.isEmpty()) require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"native binding"); return bound.snapshot;
    }
    private static List<Receipt> pass(Frontier f) {
        List<Receipt> outputs = new ArrayList<>();
        for (Snapshot cut : f.cuts) outputs.add(ContextOrdinaryResidualRounds.inspect(f,cut,16,WORK,100000));
        require(outputs.stream().allMatch(r -> r.verified),"actual residual receipts reconstructed: "+outputs.stream().filter(r -> !r.verified).map(r -> r.reason).findFirst().orElse("")); return outputs;
    }
    private static Audit audit(Frontier f,List<Receipt> outputs,Mode mode) { return ContextOrdinaryResidualCoverage.audit(f,outputs,mode,16,WORK); }
    private static void missing(List<Product> owners,List<Product> donors,List<Product> output,String why) {
        boolean refused = false;
        try { ContextOrdinaryResidualCoverage.check(owners,donors,output,16,ContextGroundProductionRounds.budget(WORK)); }
        catch (IllegalStateException failure) { refused = failure.getMessage().contains("lacks an observed"); }
        require(refused,why);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("residual-coverage-").toString());
            Snapshot x = snapshot(true,"!@x left(x)&&right(x)->p(x);"),a = snapshot(false,"!left(1);"),b = snapshot(false,"!right(1);");
            Frontier input = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b),WORK); List<Receipt> first = pass(input);
            Audit step = audit(input,first,Mode.INPUT_STEP),local = audit(input,first,Mode.LOCAL_CLOSURE);
            require(step.covered && local.covered,"first partial reductions covered: "+step.reason+" / "+local.reason);
            require(step.reason.contains("admission unknown"),"reduction coverage is no general saturation certificate");
            Receipt cut = first.get(1); List<Product> noResidual = new ArrayList<>();
            for (Product product : cut.localProofs) if (product.proof.depth == 0) noResidual.add(product);
            missing(cut.owners,cut.owners,noResidual,"omitting actual right->p residual cannot pass even without any enabled unit conclusion");
            Barrier one = ContextOrdinaryResidualRounds.merge(input,first,WORK); List<Receipt> second = pass(one.frontier);
            require(audit(one.frontier,second,Mode.INPUT_STEP).covered,"carried original depth and source groups satisfy next-step obligations");
            require(audit(one.frontier,second,Mode.LOCAL_CLOSURE).covered,"full local residual closure covered at second barrier");
            List<Receipt> absent = new ArrayList<>(first); absent.remove(0);
            require(!audit(input,absent,Mode.INPUT_STEP).covered,"missing self cannot give coverage prefix");
            List<Receipt> duplicate = new ArrayList<>(first); duplicate.set(0,duplicate.get(1));
            require(!audit(input,duplicate,Mode.INPUT_STEP).covered,"duplicate receipt cannot replace self");
            require(!audit(one.frontier,first,Mode.INPUT_STEP).covered,"stale barrier cannot certify new input");
            Frontier independent = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b),WORK);
            require(!audit(independent,first,Mode.INPUT_STEP).covered,"equal-content independent frontier is not the actual basis");
            require(!ContextOrdinaryResidualCoverage.audit(input,first,Mode.INPUT_STEP,16,1).covered,"audit work exhaustion is uncovered");
            require(!ContextOrdinaryResidualCoverage.audit(input,first,Mode.INPUT_STEP,0,WORK).covered,"observed residual depth cannot be erased");
            Snapshot wideX = snapshot(true,"!@x @y left(x)&&right(y)->pair(x,y);");
            Frontier wide = ContextOrdinaryResidualRounds.start(wideX,Arrays.asList(a,b),WORK); List<Receipt> widePass = pass(wide);
            Audit symbolic = audit(wide,widePass,Mode.INPUT_STEP);
            require(symbolic.covered,"remaining universal slot is covered as a symbolic clause: "+symbolic.reason);
            List<Product> noUniversal = new ArrayList<>();
            for (Product product : widePass.get(1).localProofs) if (product.clause.variables.isEmpty() || product.proof.depth == 0) noUniversal.add(product);
            missing(widePass.get(1).owners,widePass.get(1).owners,noUniversal,"known ground instances cannot replace omitted universal remainder");
            Snapshot both = snapshot(false,"!left(1); !right(2);");
            Frontier fullyBound = ContextOrdinaryResidualRounds.start(wideX,Collections.singletonList(both),WORK); List<Receipt> fullyBoundPass = pass(fullyBound);
            require(fullyBoundPass.get(1).localProofs.stream().anyMatch(p -> p.proof.depth > 0 && p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("pair")),"fully bound native replay actually proves pair(1,2)");
            require(fullyBoundPass.get(1).localProofs.stream().anyMatch(p -> p.proof.depth > 0 && p.clause.literals.size() == 2 && p.clause.variables.isEmpty()),"actual observed partial reductions can be ground specializations");
            Audit insufficientGrounds = audit(fullyBound,fullyBoundPass,Mode.INPUT_STEP);
            require(!insufficientGrounds.covered && insufficientGrounds.reason.contains("symbolic residual"),"ground-specialized native results do not certify a missing universally quantified residual");
            Snapshot localX = snapshot(true,"!@x b(x)&&c(x)->out(x);"),localForeign = snapshot(false,"!a(1); !@x a(x)->b(x);");
            Frontier localInput = ContextOrdinaryResidualRounds.start(localX,Collections.singletonList(localForeign),WORK); List<Receipt> localPass = pass(localInput);
            require(audit(localInput,localPass,Mode.INPUT_STEP).covered,"input obligations cover a->b without yet requiring b-enabled residual");
            require(audit(localInput,localPass,Mode.LOCAL_CLOSURE).covered,"local foreign intermediate b enables covered c->out");
            Receipt localCut = localPass.get(1); List<Product> withoutOutResidual = new ArrayList<>();
            for (Product product : localCut.localProofs) if (!(product.proof.depth > 0 && product.clause.literals.size() == 2 && product.clause.literals.stream().anyMatch(atom -> atom.predicate.equals("out")))) withoutOutResidual.add(product);
            ContextOrdinaryResidualCoverage.check(localCut.owners,localCut.owners,withoutOutResidual,16,ContextGroundProductionRounds.budget(WORK));
            require(true,"input audit alone cannot see the deliberately omitted b-enabled residual");
            missing(withoutOutResidual,withoutOutResidual,withoutOutResidual,"local audit catches missing residual enabled by foreign-only intermediate");
            Snapshot untouchedX = snapshot(true,"!anchor(1);"),positive = snapshot(false,"!p(1);"),negative = snapshot(false,"!~p(1);");
            Frontier independentSiblings = ContextOrdinaryResidualRounds.start(untouchedX,Arrays.asList(positive,negative),WORK);
            require(audit(independentSiblings,pass(independentSiblings),Mode.LOCAL_CLOSURE).covered,"opposite raw siblings stay in different coverage donor pools");
            Snapshot a2 = snapshot(false,"!left(1);");
            Frontier alternatives = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,a2,b),WORK); List<Receipt> alternatives1 = pass(alternatives);
            Barrier alternativesMerged = ContextOrdinaryResidualRounds.merge(alternatives,alternatives1,WORK); List<Receipt> alternatives2 = pass(alternativesMerged.frontier);
            require(audit(alternativesMerged.frontier,alternatives2,Mode.LOCAL_CLOSURE).covered,"independent carried whole-source alternatives remain separately required");
            Receipt alternativeCut = alternatives2.get(1); List<Product> withoutA = new ArrayList<>();
            for (Product product : alternativeCut.localProofs) if (!(product.proof.depth > 0 && product.proof.sources.contains(a.primary.get(0).source))) withoutA.add(product);
            missing(alternativeCut.owners,alternativeCut.owners,withoutA,"another proof of the same residual cannot replace the omitted whole source group");
            Snapshot negativeHeadX = snapshot(true,"!@x left(x)&&right(x)->~p(x);");
            Frontier negativeHead = ContextOrdinaryResidualRounds.start(negativeHeadX,Arrays.asList(a,b),WORK);
            require(audit(negativeHead,pass(negativeHead),Mode.LOCAL_CLOSURE).covered,"negative head partial reductions retain their signs");
            Snapshot maskedA = snapshot(false,"!left(1); !p(1);"),maskedB = snapshot(false,"!right(1); !~p(1);");
            Frontier masked = ContextOrdinaryResidualRounds.start(x,Arrays.asList(maskedA,maskedB),WORK); List<Receipt> maskedFirst = pass(masked);
            Barrier maskedMerge = ContextOrdinaryResidualRounds.merge(masked,maskedFirst,WORK);
            List<Receipt> maskedSecond = pass(maskedMerge.frontier);
            require(maskedSecond.stream().anyMatch(r -> !r.collisions.isEmpty()) && !audit(maskedMerge.frontier,maskedSecond,Mode.LOCAL_CLOSURE).covered,"collision receipts cannot masquerade as coverage");
            Snapshot repeatX = snapshot(true,"!@x edge(x,x)&&tag(x)->done(x);"),different = snapshot(false,"!edge(1,2);");
            Frontier repeated = ContextOrdinaryResidualRounds.start(repeatX,Collections.singletonList(different),WORK);
            require(audit(repeated,pass(repeated),Mode.LOCAL_CLOSURE).covered,"repeated variable cannot match distinct donor arguments");
            Snapshot namedA = snapshot(false,"!left(John);"); Frontier named = ContextOrdinaryResidualRounds.start(wideX,Collections.singletonList(namedA),WORK);
            require(audit(named,pass(named),Mode.LOCAL_CLOSURE).covered,"named semantic constant and unbound slot remain distinct");
            Snapshot dormant = snapshot(true,"!@x @y left(x)&&right(y)->pair(x,y);"); Frontier empty = ContextOrdinaryResidualRounds.start(dormant,Collections.emptyList(),WORK);
            require(audit(empty,pass(empty),Mode.LOCAL_CLOSURE).covered,"dormant universal rules need no invented known-domain instances");
            boolean immutable = false; try { cut.localProofs.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; }
            require(immutable,"local coverage proof ledger is immutable");
            System.out.println("CONTEXT_ORDINARY_RESIDUAL_COVERAGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
