/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextOrdinaryResidualCoverage.*;

/** Fresh-parameter native observations must close symbolic obligations without supplying facts. */
public final class ContextOrdinaryResidualParametersRunner {
    private static int checks,ids;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        if (!text.isEmpty()) require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"whole binding"); return bound.snapshot;
    }
    private static List<Receipt> pass(Frontier f,boolean parameters) {
        List<Receipt> outputs = new ArrayList<>();
        for (Snapshot cut : f.cuts) outputs.add(parameters ? ContextOrdinaryResidualRounds.inspectWithParameters(f,cut,16,WORK,100000) : ContextOrdinaryResidualRounds.inspect(f,cut,16,WORK,100000));
        require(outputs.stream().allMatch(r -> r.verified),"all actual observations verified: "+outputs.stream().filter(r -> !r.verified).map(r -> r.reason).findFirst().orElse("")); return outputs;
    }
    private static Audit audit(Frontier f,List<Receipt> outputs,Mode mode) { return ContextOrdinaryResidualCoverage.audit(f,outputs,mode,16,WORK); }
    private static void rejectFresh(List<Clause> knowledge,List<SemanticTermSnapshot> parameters,String why) {
        boolean rejected = false;
        try { ContextOrdinaryResidualParameters.fresh(knowledge,parameters,ContextGroundProductionRounds.budget(WORK)); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected,why);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("residual-parameters-").toString());
            Snapshot x = snapshot(true,"!@x @y left(x)&&right(y)->pair(x,y);"),both = snapshot(false,"!left(1); !right(2);");
            Frontier f = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(both),WORK);
            List<Receipt> ordinary = pass(f,false),fresh = pass(f,true);
            require(!audit(f,ordinary,Mode.INPUT_STEP).covered,"stage3x real ordinary observation gap remains reproducible");
            Audit step = audit(f,fresh,Mode.INPUT_STEP),closed = audit(f,fresh,Mode.LOCAL_CLOSURE);
            require(step.covered,"fresh native pass supplies universal remainder: "+step.reason);
            require(closed.covered,"new universal remainders also close local obligations: "+closed.reason);
            Set<Source> leftSources = new HashSet<>(); leftSources.add(x.primary.get(0).source); leftSources.add(both.primary.stream().filter(e -> e.statement.contains("left")).findFirst().get().source);
            Receipt target = fresh.get(1);
            require(target.products.stream().anyMatch(p -> p.clause.variables.size() == 1 && p.clause.literals.size() == 2 && p.proof.depth == 1 && p.proof.sources.equals(leftSources)),"generalized partial proof keeps exact smaller X/left authority and depth1");
            require(target.products.stream().anyMatch(p -> p.clause.variables.isEmpty() && p.clause.literals.size() == 1 && p.proof.depth == 1 && p.proof.sources.size() == 3),"ordinary ground consequence remains alongside generalized proofs");
            require(f.products.isEmpty(),"fresh candidates never mutate the input frontier");
            Barrier merged = ContextOrdinaryResidualRounds.merge(f,fresh,WORK); require(merged.status == Status.MERGED && merged.changed,"generalized actual proofs cross a complete ordinary barrier");
            List<Receipt> later = pass(merged.frontier,true);
            require(audit(merged.frontier,later,Mode.INPUT_STEP).covered,"carried generalized recipes retain next-step source and depth authority");
            require(audit(merged.frontier,later,Mode.LOCAL_CLOSURE).covered,"fresh pass remains covered after carrier projection");
            List<Receipt> stale = new ArrayList<>(fresh);
            require(!audit(merged.frontier,stale,Mode.INPUT_STEP).covered,"parameter receipts still belong to only their exact input");
            List<Receipt> missing = new ArrayList<>(fresh); missing.remove(0);
            require(ContextOrdinaryResidualRounds.merge(f,missing,WORK).status == Status.INCOMPLETE,"parameter proof cannot publish a missing-self prefix");
            Receipt workStop = ContextOrdinaryResidualRounds.inspectWithParameters(f,both,16,1,100000);
            require(!workStop.verified && workStop.products.isEmpty() && workStop.collisions.isEmpty(),"shared work stop publishes no ordinary or parameter prefix");
            Receipt eventStop = ContextOrdinaryResidualRounds.inspectWithParameters(f,both,16,WORK,1);
            require(!eventStop.verified && eventStop.products.isEmpty(),"shared event stop publishes no prefix");
            int upper = 1;
            while (!ContextOrdinaryResidualRounds.inspect(f,both,16,WORK,upper).verified && upper < 1024) upper *= 2;
            require(upper < 1024,"ordinary event threshold is bounded");
            int lower = 1;
            while (lower < upper) {
                int middle = lower+(upper-lower)/2;
                if (ContextOrdinaryResidualRounds.inspect(f,both,16,WORK,middle).verified) upper = middle; else lower = middle+1;
            }
            Receipt ordinaryAtLimit = ContextOrdinaryResidualRounds.inspect(f,both,16,WORK,upper);
            Receipt noSecondBudget = ContextOrdinaryResidualRounds.inspectWithParameters(f,both,16,WORK,upper);
            require(ordinaryAtLimit.verified && !ordinaryAtLimit.products.isEmpty() && !noSecondBudget.verified && noSecondBudget.products.isEmpty() && noSecondBudget.collisions.isEmpty(),"successful ordinary pass cannot leak a proof prefix when its shared event budget leaves no second pass");
            Receipt depthStop = ContextOrdinaryResidualRounds.inspectWithParameters(f,both,0,WORK,100000);
            require(!depthStop.verified && depthStop.products.isEmpty(),"parameter pass cannot bypass proof depth cap");
            Snapshot a = snapshot(false,"!left(1);"),b = snapshot(false,"!right(2);");
            Frontier split = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b),WORK); Barrier split1 = ContextOrdinaryResidualRounds.merge(split,pass(split,true),WORK);
            Barrier split2 = ContextOrdinaryResidualRounds.merge(split1.frontier,pass(split1.frontier,true),WORK);
            require(split2.status == Status.MERGED && split2.frontier.products.stream().anyMatch(p -> p.clause.literals.size() == 1 && p.proof.depth == 2 && p.proof.sources.size() == 3),"independent cuts still use later barrier and cumulative depth2");
            Snapshot untouchedX = snapshot(true,"!anchor(1);"),foreignRule = snapshot(false,"!@x @y left(x)&&right(y)->pair(x,y); !left(1); !right(2);");
            Frontier foreignOnly = ContextOrdinaryResidualRounds.start(untouchedX,Collections.singletonList(foreignRule),WORK);
            List<Receipt> foreignPass = pass(foreignOnly,true); Barrier foreignMerge = ContextOrdinaryResidualRounds.merge(foreignOnly,foreignPass,WORK);
            require(audit(foreignOnly,foreignPass,Mode.LOCAL_CLOSURE).covered && foreignMerge.frontier.products.isEmpty(),"generalized foreign-only proofs stay local");
            Snapshot named = snapshot(false,"!left(John); !right(Mary);"); Frontier namedInput = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(named),WORK);
            require(audit(namedInput,pass(namedInput,true),Mode.LOCAL_CLOSURE).covered,"named constants are not generalized away");
            SemanticTermSnapshot known = named.primary.get(0).clauses.get(0).literals.get(0).arguments.get(0).value;
            rejectFresh(Collections.singletonList(named.primary.get(0).clauses.get(0)),Collections.singletonList(known),"parameter present in primary knowledge is refused");
            rejectFresh(Collections.emptyList(),Arrays.asList(known,known),"two parameter positions cannot alias one semantic value");
            Clause carried = merged.frontier.products.stream().filter(p -> p.clause.literals.size() == 2).findFirst().get().clause;
            SemanticTermSnapshot carriedValue = carried.literals.stream().flatMap(atom -> atom.arguments.stream()).filter(slot -> slot.variable < 0).findFirst().get().value;
            rejectFresh(Collections.singletonList(carried),Collections.singletonList(carriedValue),"parameter present in a carried recipe is refused");
            Snapshot twoFreeX = snapshot(true,"!@x @y anchor(1)&&rel(x,y)->out(x,y);"),twoFreeForeign = snapshot(false,"!anchor(1); !rel(John,Mary);");
            Frontier twoFree = ContextOrdinaryResidualRounds.start(twoFreeX,Collections.singletonList(twoFreeForeign),WORK); List<Receipt> twoFreePass = pass(twoFree,true);
            Audit twoFreeAudit = audit(twoFree,twoFreePass,Mode.LOCAL_CLOSURE);
            require(!twoFreeAudit.covered && twoFreeAudit.reason.contains("symbolic residual"),"two-free-variable native admission gap remains explicitly uncovered");
            require(twoFreePass.get(1).products.stream().noneMatch(p -> p.clause.variables.size() == 2),"seeding two parameter candidates does not fabricate an unvisited native off-diagonal proof");
            Snapshot repeatedX = snapshot(true,"!@x edge(x,x)&&tag(1)->same(x);"),repeatedForeign = snapshot(false,"!tag(1); !edge(John,Mary);");
            Frontier repeated = ContextOrdinaryResidualRounds.start(repeatedX,Collections.singletonList(repeatedForeign),WORK); List<Receipt> repeatedPass = pass(repeated,true);
            require(repeatedPass.get(1).products.stream().noneMatch(p -> p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("same")),"parameter seeding preserves repeated-variable equality and cannot assume an unproved edge");
            Snapshot negativeHeadX = snapshot(true,"!@x @y left(x)&&right(y)->~pair(x,y);"); Frontier negativeHead = ContextOrdinaryResidualRounds.start(negativeHeadX,Collections.singletonList(both),WORK);
            require(audit(negativeHead,pass(negativeHead,true),Mode.LOCAL_CLOSURE).covered,"signed negative conclusions generalize with unchanged polarity");
            Snapshot emptyX = snapshot(true,"!@x @y left(x)&&right(y)->pair(x,y);"); Frontier empty = ContextOrdinaryResidualRounds.start(emptyX,Collections.emptyList(),WORK); List<Receipt> emptyPass = pass(empty,true);
            require(emptyPass.stream().allMatch(r -> r.products.isEmpty()) && audit(empty,emptyPass,Mode.LOCAL_CLOSURE).covered,"parameter candidates alone produce no facts or residual proofs");
            Snapshot universalX = snapshot(true,"!@x @y rel(x,y)->p(x);"),negative = snapshot(false,"!~p(1);");
            Frontier universal = ContextOrdinaryResidualRounds.start(universalX,Collections.singletonList(negative),WORK);
            Receipt unsupported = ContextOrdinaryResidualRounds.inspectWithParameters(universal,negative,16,WORK,100000);
            require(!unsupported.verified && unsupported.products.isEmpty(),"universal unit remains unsupported, not silently widened by parameters");
            System.out.println("CONTEXT_ORDINARY_RESIDUAL_PARAMETERS_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
