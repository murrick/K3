/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundCutEvidence.Result;
import org.kanger.ContextGroundProductionRounds.*;
import org.kanger.ContextGroundRoundCoverage.*;
import org.kanger.ContextWitnessUnaryChainVerifier.Route;
import org.kanger.ContextProductionCapture.Support;

/** Independent obligations, missing native evidence and universal-domain boundaries. */
public final class ContextGroundRoundCoverageRunner {
    private static int checks,ids;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        if (!text.isEmpty()) require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"native whole-source binding"); return bound.snapshot;
    }
    private static List<Result> pass(Frontier input) {
        List<Result> result = new ArrayList<>();
        for (Snapshot cut : input.cuts) result.add(ContextGroundCutEvidence.inspect(input,cut,16,WORK,100000));
        require(result.stream().allMatch(r -> r.verified),"actual signed observations reconstructed"); return result;
    }
    private static Audit audit(Frontier input,List<Result> receipts,Mode mode) {
        return ContextGroundRoundCoverage.audit(input,receipts,mode,16,WORK);
    }
    private static void missing(Result receipt,List<Route> donors,List<Route> output,String why) {
        boolean refused = false;
        try { ContextGroundRoundCoverage.check(ContextGroundCutEvidence.declarations(receipt.x,receipt.foreign,()->{}),donors,output,16,ContextGroundProductionRounds.budget(WORK)); }
        catch (IllegalStateException failure) { refused = failure.getMessage().contains("lacks an observed"); }
        require(refused,why);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-coverage-").toString());
            Snapshot x = snapshot(true,"!@x a(x)->b(x);"),a = snapshot(false,"!a(1);");
            Frontier input = ContextGroundProductionRounds.start(x,Collections.singletonList(a),WORK); List<Result> receipts = pass(input);
            Audit step = audit(input,receipts,Mode.INPUT_STEP),closed = audit(input,receipts,Mode.LOCAL_CLOSURE);
            require(step.covered && closed.covered,"all enabled signed orientations have observed proof coverage: "+step.reason+" / "+closed.reason);
            require(closed.reason.contains("admission unknown"),"unit coverage carries no consistency or admission verdict");
            Result cut = receipts.get(1); List<Route> roots = new ArrayList<>();
            for (Route route : cut.localRoutes) if (route.proof.depth == 0) roots.add(route);
            missing(cut,roots,roots,"dropping actual native b observation fails rather than synthesizing it");
            List<Route> wrongDepth = new ArrayList<>(),wrongSources = new ArrayList<>();
            for (Route route : cut.localRoutes) {
                if (route.physical.predicate.equals("b")) {
                    wrongDepth.add(new Route(route.replay,route.physical,null,new Support(route.proof.sources,2),null,null,null,null));
                    wrongSources.add(new Route(route.replay,route.physical,null,roots.get(0).proof,null,null,null,null));
                } else { wrongDepth.add(route); wrongSources.add(route); }
            }
            missing(cut,roots,wrongDepth,"a deeper proof cannot discharge a shallower enabled obligation");
            missing(cut,roots,wrongSources,"matching literal with a different source group is insufficient");
            List<Result> absent = new ArrayList<>(receipts); absent.remove(0);
            require(!audit(input,absent,Mode.INPUT_STEP).covered,"missing self receipt has no covered prefix");
            List<Result> duplicate = Arrays.asList(receipts.get(1),receipts.get(1));
            require(!audit(input,duplicate,Mode.INPUT_STEP).covered,"duplicate cannot stand in for self");
            Frontier independent = ContextGroundProductionRounds.start(x,Collections.singletonList(a),WORK);
            require(!audit(independent,receipts,Mode.LOCAL_CLOSURE).covered,"independent equal-content input is not the sealed receipt basis");
            require(!ContextGroundRoundCoverage.audit(input,receipts,Mode.INPUT_STEP,16,1).covered,"work exhaustion is incomplete");
            require(!ContextGroundRoundCoverage.audit(input,receipts,Mode.INPUT_STEP,0,WORK).covered,"observed cumulative depth cannot be erased by audit");
            Snapshot localX = snapshot(true,"!@x b(x)->c(x);"),local = snapshot(false,"!a(1); !@x a(x)->b(x);");
            Frontier localInput = ContextGroundProductionRounds.start(localX,Collections.singletonList(local),WORK); List<Result> localPass = pass(localInput);
            require(audit(localInput,localPass,Mode.LOCAL_CLOSURE).covered,"closure audits actual foreign-only intermediate b as a local donor");
            Result localCut = localPass.get(1); List<Route> withoutC = new ArrayList<>(),localRoots = new ArrayList<>();
            for (Route route : localCut.localRoutes) { if (!route.physical.predicate.equals("c")) withoutC.add(route); if (route.proof.depth == 0) localRoots.add(route); }
            ContextGroundRoundCoverage.check(ContextGroundCutEvidence.declarations(localX,local,()->{}),localRoots,withoutC,16,ContextGroundProductionRounds.budget(WORK));
            require(true,"input-step alone does not yet require c enabled only by a local intermediate");
            missing(localCut,withoutC,withoutC,"final local closure catches omitted c enabled by foreign-only b");
            Snapshot signedX = snapshot(true,"!@x left(x)&&right(x)->p(x);"),signedB = snapshot(false,"!right(1); !~p(1);");
            Frontier signed = ContextGroundProductionRounds.start(signedX,Collections.singletonList(signedB),WORK); List<Result> signedPass = pass(signed);
            require(audit(signed,signedPass,Mode.INPUT_STEP).covered,"negative conclusion obligation is observed with whole three-source support");
            List<Route> signedRoots = new ArrayList<>(); for (Route route : signedPass.get(1).localRoutes) if (route.proof.depth == 0) signedRoots.add(route);
            missing(signedPass.get(1),signedRoots,signedRoots,"omitted negative left cannot be hidden by an unchanged shared pool");
            Snapshot permutationX = snapshot(true,"!@x @y edge(x,y)->out(y,x);"),edge = snapshot(false,"!edge(1,2);");
            Frontier permutation = ContextGroundProductionRounds.start(permutationX,Collections.singletonList(edge),WORK);
            require(audit(permutation,pass(permutation),Mode.LOCAL_CLOSURE).covered,"two-variable binding preserves argument permutation");
            Snapshot repeatedX = snapshot(true,"!@x pair(x,x)->same(x);"),different = snapshot(false,"!pair(1,2);");
            Frontier repeated = ContextGroundProductionRounds.start(repeatedX,Collections.singletonList(different),WORK);
            require(audit(repeated,pass(repeated),Mode.LOCAL_CLOSURE).covered,"repeated variable cannot match two different semantic values");
            Snapshot fixedX = snapshot(true,"!@x a(x)&&tag(2)->b(x);"),fixedCut = snapshot(false,"!a(1); !tag(3);");
            Frontier fixed = ContextGroundProductionRounds.start(fixedX,Collections.singletonList(fixedCut),WORK);
            require(audit(fixed,pass(fixed),Mode.LOCAL_CLOSURE).covered,"fixed semantic clause arguments must match their actual donors");
            Snapshot wideX = snapshot(true,"!@x @y r(x,y)->p(x);");
            Frontier wide = ContextGroundProductionRounds.start(wideX,Collections.emptyList(),WORK); List<Result> widePass = pass(wide);
            require(!audit(wide,widePass,Mode.LOCAL_CLOSURE).covered,"unbound reverse head variable refuses even without currently matching constants");
            Snapshot plainX = snapshot(true,"!unrelated(1);"),positive = snapshot(false,"!p(1);"),negative = snapshot(false,"!~p(1);");
            Frontier siblings = ContextGroundProductionRounds.start(plainX,Arrays.asList(positive,negative),WORK);
            require(audit(siblings,pass(siblings),Mode.LOCAL_CLOSURE).covered,"independent raw sibling opposition is never made a common donor pool");
            Snapshot otherValue = snapshot(false,"!a(2);");
            Frontier alternatives = ContextGroundProductionRounds.start(x,Arrays.asList(a,otherValue),WORK); List<Result> alternatePass = pass(alternatives);
            require(audit(alternatives,alternatePass,Mode.LOCAL_CLOSURE).covered,"semantic values remain distinct in quantified matching");
            Snapshot secondA = snapshot(false,"!a(1);");
            Frontier donors = ContextGroundProductionRounds.start(x,Arrays.asList(a,secondA),WORK);
            Barrier merged = ContextGroundProductionRounds.merge(donors,pass(donors),WORK);
            require(merged.status == Status.MERGED,"two source alternatives share only after the barrier");
            List<Result> carried = pass(merged.frontier);
            require(audit(merged.frontier,carried,Mode.LOCAL_CLOSURE).covered,"carried proof alternatives remain whole and cumulative");
            boolean immutable = false; try { cut.localRoutes.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"observed local ledger cannot be edited by coverage callers");
            System.out.println("CONTEXT_GROUND_ROUND_COVERAGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
