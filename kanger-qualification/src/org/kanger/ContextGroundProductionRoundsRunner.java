/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundCutEvidence.Result;
import org.kanger.ContextGroundCutEvidence.Production;
import org.kanger.ContextGroundProductionRounds.*;

/** Exact common barriers and sourced signed refutations; no admission from an unchanged observation. */
public final class ContextGroundProductionRoundsRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private interface Attempt { void run() throws Exception; }
    private static void reject(Attempt attempt,String why) throws Exception {
        boolean rejected = false; try { attempt.run(); } catch (Exception expected) { rejected = true; } require(rejected,why);
    }
    private static Snapshot snapshot(int id,boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        if (!text.isEmpty()) require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,id),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"whole binding"); return bound.snapshot;
    }
    private static Source source(Snapshot s,String fragment) { return s.primary.stream().filter(e -> e.statement.contains(fragment)).findFirst().get().source; }
    private static List<Result> pass(Frontier input,int depth) {
        List<Result> results = new ArrayList<>();
        for (Snapshot cut : input.cuts) results.add(ContextGroundCutEvidence.inspect(input,cut,depth,WORK,100000)); return results;
    }
    private static Barrier merge(Frontier input) { return ContextGroundProductionRounds.merge(input,pass(input,16),WORK); }
    private static Production proof(Frontier f,String name,boolean positive,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return f.productions.stream().filter(p -> p.conclusion.predicate.equals(name) && p.conclusion.positive == positive && p.proof.depth == depth && p.proof.sources.equals(expected)).findFirst().orElse(null);
    }
    private static Set<Source> sources(Collision c) { Set<Source> result = new HashSet<>(c.leftProof.sources); result.addAll(c.rightProof.sources); return result; }
    private static void incomplete(Barrier b,String why) { require(b.status == Status.INCOMPLETE && b.frontier == null && b.collisions.isEmpty(),why+": "+b.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ground-rounds-").toString());
            Snapshot x = snapshot(0,true,"!@x left(x)&&right(x)->p(x);"),a = snapshot(1,false,"!left(1); !p(1);"),b = snapshot(2,false,"!right(1); !~p(1);");
            List<Snapshot> selected = new ArrayList<>(Arrays.asList(a,b)); Frontier initial = ContextGroundProductionRounds.start(x,selected,WORK); selected.clear();
            require(initial.cuts.size() == 3 && initial.productions.isEmpty(),"whole family frozen without lifting any raw knowledge");
            List<Result> firstPass = pass(initial,16);
            require(firstPass.stream().allMatch(r -> r.verified && r.collisions.isEmpty()),"all first-barrier independent pairs have sound observations");
            Barrier first = ContextGroundProductionRounds.merge(initial,firstPass,WORK);
            require(first.status == Status.MERGED && first.changed && first.frontier.basis.equals(initial.identity),"signed product crosses only the complete common barrier");
            require(proof(first.frontier,"left",false,1,source(x,"@x"),source(b,"right"),source(b,"~p")) != null,"transport retains exact signed three-source depth1 recipe");
            require(first.frontier.productions.stream().noneMatch(p -> p.conclusion.predicate.equals("right") || p.conclusion.predicate.equals("p")),"raw sibling facts never enter shared frontier");
            Barrier second = merge(first.frontier);
            require(second.status == Status.COLLISION && second.frontier == null && !second.collisions.isEmpty(),"A rejects B's X-dependent negative left at the next common barrier");
            Set<Source> expected = new HashSet<>(Arrays.asList(source(x,"@x"),source(b,"right"),source(b,"~p"),source(a,"left")));
            require(second.collisions.stream().anyMatch(c -> sources(c).equals(expected) && c.leftProof.depth == 1 && c.rightProof.depth == 0),"refutation keeps both proof sides and all four exact sources without resetting depth");
            Frontier reverse = ContextGroundProductionRounds.start(x,Arrays.asList(b,a),WORK); Barrier reverseFirst = merge(reverse),reverseSecond = merge(reverseFirst.frontier);
            require(reverseSecond.status == Status.COLLISION && reverseSecond.collisions.stream().anyMatch(c -> sources(c).equals(expected)),"reversed traversal yields the same sourced collision after a common barrier");
            List<Result> partial = new ArrayList<>(firstPass); partial.remove(0);
            incomplete(ContextGroundProductionRounds.merge(initial,partial,WORK),"missing self result cannot publish a prefix");
            incomplete(ContextGroundProductionRounds.merge(first.frontier,firstPass,WORK),"previous branch evidence cannot be reused at another input identity");
            List<Result> duplicate = new ArrayList<>(firstPass); duplicate.set(0,duplicate.get(1));
            incomplete(ContextGroundProductionRounds.merge(initial,duplicate,WORK),"duplicate cut cannot replace a missing participant");
            incomplete(ContextGroundProductionRounds.merge(initial,firstPass,1),"merge work stop returns no partial frontier");
            List<Result> stopped = pass(first.frontier,0);
            incomplete(ContextGroundProductionRounds.merge(first.frontier,stopped,WORK),"carried proof depth is not reset even for collision scanning");
            Result unrelated = ContextGroundCutEvidence.inspect(x,a,16,WORK,100000); List<Result> unbound = new ArrayList<>(firstPass); unbound.set(1,unrelated);
            incomplete(ContextGroundProductionRounds.merge(initial,unbound,WORK),"independently created observation has no authority at this barrier");
            Snapshot cx = snapshot(3,true,"!@x a(x)->b(x);"),ca = snapshot(4,false,"!a(1);"),cb = snapshot(5,false,"!@x b(x)->c(x);");
            Frontier chain = ContextGroundProductionRounds.start(cx,Arrays.asList(ca,cb),WORK); Barrier one = merge(chain);
            require(one.status == Status.MERGED && proof(one.frontier,"b",true,1,source(cx,"@x"),source(ca,"!a")) != null,"first cut supplies X-dependent b");
            Barrier two = merge(one.frontier);
            require(two.status == Status.MERGED && proof(two.frontier,"c",true,2,source(cx,"@x"),source(ca,"!a"),source(cb,"@x")) != null,"semantic carrier feeds another cut with complete depth2 authority");
            Barrier three = merge(two.frontier);
            require(three.status == Status.MERGED && !three.changed && three.reason.contains("unknown"),"unchanged observed pool is explicitly not a fixed-point/admission certificate");
            incomplete(ContextGroundProductionRounds.merge(one.frontier,pass(one.frontier,1),WORK),"later native application cannot bypass cumulative depth limit");
            Snapshot alternateA = snapshot(16,false,"!a(1);");
            Frontier alternatives = ContextGroundProductionRounds.start(cx,Arrays.asList(ca,alternateA,cb),WORK);
            Barrier altFirst = merge(alternatives),altSecond = merge(altFirst.frontier);
            require(altSecond.status == Status.MERGED && proof(altSecond.frontier,"c",true,2,source(cx,"@x"),source(ca,"!a"),source(cb,"@x")) != null
                    && proof(altSecond.frontier,"c",true,2,source(cx,"@x"),source(alternateA,"!a"),source(cb,"@x")) != null,"one restored semantic atom retains both independent source alternatives through the next cut");
            require(altSecond.frontier.productions.stream().filter(p -> p.conclusion.predicate.equals("c")).count() == 2,"alternative authorities are not flattened into a combined source set");
            Snapshot cycleCut = snapshot(17,false,"!@x b(x)->a(x);");
            Frontier cycle = ContextGroundProductionRounds.start(cx,Arrays.asList(ca,cycleCut),WORK);
            Barrier cycled = null;
            for (int n=0;n<6;++n) { cycled = merge(cycle); require(cycled.status == Status.MERGED,"cycle pass retains sound observed products"); cycle = cycled.frontier; if (!cycled.changed) break; }
            require(cycled != null && !cycled.changed && proof(cycle,"a",true,2,source(cx,"@x"),source(ca,"!a"),source(cycleCut,"@x")) != null,"cycle keeps the minimum depth for an exact whole source set");
            Snapshot negativeReceiver = snapshot(18,false,"!@x ~left(x)->blocked(x);");
            Barrier signedOne = merge(ContextGroundProductionRounds.start(x,Arrays.asList(b,negativeReceiver),WORK)),signedTwo = merge(signedOne.frontier);
            require(signedTwo.status == Status.MERGED && proof(signedTwo.frontier,"blocked",true,2,source(x,"@x"),source(b,"right"),source(b,"~p"),source(negativeReceiver,"@x")) != null,"negative semantic carrier can feed a signed premise in another cut");
            List<Result> collisionPrefix = pass(first.frontier,16);
            collisionPrefix.set(2,ContextGroundCutEvidence.inspect(first.frontier,b,16,1,100000));
            incomplete(ContextGroundProductionRounds.merge(first.frontier,collisionPrefix,WORK),"a proved collision is not published as a prefix of an incomplete family barrier");
            Result eventStop = ContextGroundCutEvidence.inspect(one.frontier,cb,16,WORK,1);
            require(!eventStop.verified && eventStop.productions.isEmpty() && eventStop.collisions.isEmpty(),"event exhaustion after transport returns no proof prefix");
            Snapshot ux = snapshot(6,true,"!unrelated(1);"),ua = snapshot(7,false,"!p(1);"),ub = snapshot(8,false,"!~p(1);");
            Barrier independent = merge(ContextGroundProductionRounds.start(ux,Arrays.asList(ua,ub),WORK));
            require(independent.status == Status.MERGED && !independent.changed && independent.frontier.productions.isEmpty(),"opposite raw sibling facts do not create an X collision");
            Snapshot ox = snapshot(9,true,"!@x a(x)->p(x); !@x b(x)->~p(x);"),oa = snapshot(10,false,"!a(1);"),ob = snapshot(11,false,"!b(1);");
            Barrier opposite = merge(ContextGroundProductionRounds.start(ox,Arrays.asList(oa,ob),WORK));
            require(opposite.status == Status.COLLISION && opposite.frontier == null && opposite.collisions.stream().allMatch(c -> c.leftProof.sources.stream().anyMatch(s -> s.localX) && c.rightProof.sources.stream().anyMatch(s -> s.localX)),"opposite proved X-dependent products refute the merged barrier");
            Snapshot nx = snapshot(12,true,"!@x a(x)->~p(x);"),na = snapshot(13,false,"!a(1);"),nb = snapshot(14,false,"!p(2);");
            Barrier namedFirst = merge(ContextGroundProductionRounds.start(nx,Arrays.asList(na,nb),WORK));
            require(namedFirst.status == Status.MERGED && merge(namedFirst.frontier).status == Status.MERGED,"different semantic argument values cannot form an opposition");
            reject(() -> ContextGroundProductionRounds.start(x,Arrays.asList(a,a),WORK),"duplicate source scope refuses before any pass");
            reject(() -> ContextGroundProductionRounds.start(x,Arrays.asList(x),WORK),"self cannot be relabelled as foreign");
            Snapshot unsupported = snapshot(15,false,"!$w witness(w,1);");
            reject(() -> ContextGroundProductionRounds.start(x,Arrays.asList(a,unsupported),WORK),"unsupported later source preflight prevents a partial family");
            boolean immutable = false; try { first.frontier.productions.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; }
            require(immutable,"sealed transported proof pool is immutable");
            System.out.println("CONTEXT_GROUND_PRODUCTION_ROUNDS_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
