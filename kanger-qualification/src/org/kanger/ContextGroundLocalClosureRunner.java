/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;

/** Private ground composition closure must preserve independent cut semantics. */
public final class ContextGroundLocalClosureRunner {
    private static int checks,ids;
    private static final int WORK = 16000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"native binding"); return bound.snapshot;
    }
    private static Receipt close(Frontier input,Snapshot target) {
        Receipt r = ContextOrdinaryResidualRounds.inspectWithLocalCompositions(input,target,16,WORK,500000,256,16);
        require(r.verified,"private cut closure: "+r.reason); return r;
    }
    private static List<Receipt> pass(Frontier input) {
        List<Receipt> output = new ArrayList<>(); for (Snapshot target : input.cuts) output.add(close(input,target)); return output;
    }
    private static void audits(Frontier input,List<Receipt> output) {
        ContextGroundCompositionCoverage.Audit c = ContextGroundCompositionCoverage.audit(input,output,ContextGroundCompositionCoverage.Mode.LOCAL_CLOSURE,16,WORK);
        require(c.covered,"independent terminal composition audit: "+c.reason);
        ContextOrdinaryResidualCoverage.Audit r = ContextOrdinaryResidualCoverage.audit(input,output,ContextOrdinaryResidualCoverage.Mode.LOCAL_CLOSURE,16,WORK);
        require(r.covered,"independent terminal reduction audit: "+r.reason);
    }
    private static boolean value(Clause clause,Map<String,Boolean> model) {
        for (Atom atom : clause.literals) if (model.get(atom.predicate) == atom.positive) return true; return false;
    }
    private static void oracle(Receipt receipt) {
        List<String> names = new ArrayList<>();
        for (Product p : receipt.localProofs) for (Atom a : p.clause.literals) if (!names.contains(a.predicate)) names.add(a.predicate);
        require(names.size() <= 6,"bounded independent propositional oracle");
        for (Product product : receipt.localProofs) {
            List<Clause> causes = new ArrayList<>();
            for (Snapshot cut : receipt.input.cuts) for (ContextProductionNativeBinding.Entry entry : cut.primary)
                if (product.proof.sources.contains(entry.source)) causes.addAll(entry.clauses);
            require(!causes.isEmpty(),"actual original causes registered for every local recipe");
            for (int bits=0;bits<(1<<names.size());++bits) {
                Map<String,Boolean> model = new HashMap<>();
                for (int i=0;i<names.size();++i) model.put(names.get(i),(bits&(1<<i)) != 0);
                boolean all = true; for (Clause clause : causes) all &= value(clause,model);
                require(!all || value(product.clause,model),"whole original source theories independently entail every local proof");
            }
        }
    }
    private static void stop(Receipt r,String why) { require(!r.verified && r.products.isEmpty() && r.collisions.isEmpty(),why+": "+r.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ground-local-closure-").toString());
            String chain = "!a(1)||b(1); !~a(1)||c(1); !~c(1)||d(1);";
            Snapshot x = snapshot(true,"!anchor(1);"),a = snapshot(false,chain);
            Frontier f = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(a),WORK);
            List<Receipt> closed = pass(f); audits(f,closed);
            Clause bd = snapshot(false,"!b(1)||d(1);").primary.get(0).clauses.get(0);
            Receipt local = closed.stream().filter(r -> r.target == a).findFirst().get();
            require(local.localProofs.stream().anyMatch(p -> p.clause.equivalent(bd) && p.proof.depth == 2 && p.proof.sources.size() == 3 && p.proof.sources.stream().noneMatch(s -> s.localX)),"previously missing private consequence has exact three foreign causes/depth2");
            require(closed.stream().allMatch(r -> r.products.isEmpty()),"private closure does not export foreign knowledge");
            Barrier unchanged = ContextOrdinaryResidualRounds.merge(f,closed,WORK);
            require(unchanged.status == Status.MERGED && !unchanged.changed,"private saturation need not change the shared X pool");
            oracle(local);
            stop(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,a,16,WORK,500000,256,1),"one local round cannot publish partial private closure");
            stop(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,a,16,WORK,500000,256,2),"producing the missing theorem is insufficient without unchanged local proof recipes");
            boolean sharedStop = false;
            for (int limit : new int[]{32,64,128,256}) {
                Receipt one = ContextOrdinaryResidualRounds.inspectWithCompositions(f,a,16,WORK,limit,256);
                Receipt many = ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,a,16,WORK,limit,256,16);
                if (one.verified && !many.verified) { stop(many,"later local sweep shares the original event allowance"); sharedStop = true; break; }
            }
            require(sharedStop,"one-pass proof succeeds within an allowance that unfinished local closure exhausts");
            stop(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,a,1,WORK,500000,256,16),"cumulative local depth limit");
            stop(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,a,16,1,500000,256,16),"shared work limit");
            stop(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,a,16,WORK,1,256,16),"shared event limit");
            Snapshot joinedX = snapshot(true,"!~b(1)||out(1);"),negative = snapshot(false,"!~d(1); !~out(1);");
            Frontier joined = ContextOrdinaryResidualRounds.start(joinedX,Arrays.asList(a,negative),WORK);
            List<Receipt> first = pass(joined); audits(joined,first);
            Clause result = snapshot(false,"!d(1)||out(1);").primary.get(0).clauses.get(0);
            require(first.stream().filter(r -> r.target == a).flatMap(r -> r.products.stream()).anyMatch(p -> p.clause.equivalent(result) && p.proof.depth == 2 && p.proof.sources.size() == 4),"private compositions join X through whole original recipes before export");
            oracle(first.stream().filter(r -> r.target == a).findFirst().get());
            Barrier shared = ContextOrdinaryResidualRounds.merge(joined,first,WORK);
            require(shared.status == Status.MERGED && shared.changed,"X-dependent closed consequences cross only the common barrier");
            List<Receipt> second = pass(shared.frontier);
            require(ContextOrdinaryResidualRounds.merge(shared.frontier,second,WORK).status == Status.COLLISION,"opposing sibling sees exported X-dependent consequence only on next common round");
            Frontier reverse = ContextOrdinaryResidualRounds.start(joinedX,Arrays.asList(negative,a),WORK);
            Barrier reverseFirst = ContextOrdinaryResidualRounds.merge(reverse,pass(reverse),WORK);
            require(reverseFirst.status == Status.MERGED && reverseFirst.changed,"reverse first common barrier");
            require(ContextOrdinaryResidualRounds.merge(reverseFirst.frontier,pass(reverseFirst.frontier),WORK).status == Status.COLLISION,"reverse order collides on same second common round");
            Snapshot reduced = snapshot(false,chain+" !~b(1);");
            Frontier rf = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(reduced),WORK);
            List<Receipt> reducedClosed = pass(rf); audits(rf,reducedClosed);
            require(reducedClosed.stream().filter(r -> r.target == reduced).flatMap(r -> r.localProofs.stream()).anyMatch(p -> p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("d") && p.proof.depth == 2 && p.proof.sources.size() == 4),"balanced local composition/reduction retains four causes at depth2");
            require(reducedClosed.stream().allMatch(r -> r.products.isEmpty()),"local units from foreign-only proofs stay private too");
            Snapshot mx = snapshot(true,"!~a(1)||~b(1)||~c(1);"),ma = snapshot(false,"!d(1)->a(1);"),mb = snapshot(false,"!d(1)->b(1);"),mc = snapshot(false,"!d(1); !c(1);");
            for (List<Snapshot> order : Arrays.asList(Arrays.asList(ma,mb,mc),Arrays.asList(mc,mb,ma))) {
                Frontier masked = ContextOrdinaryResidualRounds.start(mx,order,WORK); boolean collided = false;
                Set<Source> expected = new HashSet<>();
                for (Snapshot cut : masked.cuts) for (ContextProductionNativeBinding.Entry entry : cut.primary) expected.add(entry.source);
                for (int round=1;round<=3;++round) {
                    Barrier barrier = ContextOrdinaryResidualRounds.merge(masked,pass(masked),WORK);
                    if (barrier.status == Status.COLLISION) {
                        require(round == 3,"original X/A/B/C still collides on third immutable common barrier");
                        require(barrier.collisions.stream().anyMatch(collision -> { Set<Source> sources = new HashSet<>(collision.leftProof.sources); sources.addAll(collision.rightProof.sources); return sources.equals(expected); }),"original collision retains all five source occurrences after private closure");
                        collided = true; break;
                    }
                    require(barrier.status == Status.MERGED && barrier.changed,"original family advances with closed private cuts"); masked = barrier.frontier;
                }
                require(collided,"original family collision is retained in either sibling order");
            }
            Snapshot ux = snapshot(true,"!@x a(x)->b(x);");
            Frontier uf = ContextOrdinaryResidualRounds.start(ux,Collections.singletonList(a),WORK);
            stop(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(uf,a,16,WORK,500000,256,16),"universal clauses stay outside ground closure scope");
            System.out.println("CONTEXT_GROUND_LOCAL_CLOSURE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
