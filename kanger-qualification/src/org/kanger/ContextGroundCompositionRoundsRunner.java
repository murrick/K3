/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;

/** Common barriers and an independent missing-composition coverage control. */
public final class ContextGroundCompositionRoundsRunner {
    private static int checks,ids;
    private static final int WORK = 8000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"native binding"); return bound.snapshot;
    }
    private static List<Receipt> pass(Frontier f,boolean compositions) {
        List<Receipt> receipts = new ArrayList<>();
        for (Snapshot cut : f.cuts) receipts.add(compositions
                ? ContextOrdinaryResidualRounds.inspectWithCompositions(f,cut,16,WORK,200000,256)
                : ContextOrdinaryResidualRounds.inspectWithProbes(f,cut,16,WORK,200000,256));
        for (Receipt receipt : receipts) require(receipt.verified,"verified cut: "+receipt.reason);
        return receipts;
    }
    private static int refute(Snapshot x,List<Snapshot> foreign,Set<Source> expected) {
        Frontier f = ContextOrdinaryResidualRounds.start(x,foreign,WORK);
        for (int round=1;round<=8;++round) {
            List<Receipt> receipts = pass(f,true);
            Barrier b = ContextOrdinaryResidualRounds.merge(f,receipts,WORK);
            if (b.status == Status.COLLISION) {
                require(b.collisions.stream().anyMatch(c -> { Set<Source> s = new HashSet<>(c.leftProof.sources); s.addAll(c.rightProof.sources); return s.equals(expected); }),"complete collision retains exactly X/A/B/C causal sources");
                require(b.collisions.stream().allMatch(c -> Math.max(c.leftProof.depth,c.rightProof.depth) >= 2),"collision does not reset transported proof depths");
                System.out.println("GROUND_COMPOSITION_COLLISION round="+round); return round;
            }
            require(b.status == Status.MERGED && b.changed,"counterexample cannot stop as consistent: "+b.reason);
            ContextGroundCompositionCoverage.Audit audit = ContextGroundCompositionCoverage.audit(f,receipts,ContextGroundCompositionCoverage.Mode.INPUT_STEP,16,WORK);
            require(audit.covered,"every original/carried ground pair covered before advancing: "+audit.reason);
            f = b.frontier;
        }
        throw new AssertionError("missing final collision");
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ground-composition-rounds-").toString());
            Snapshot x = snapshot(true,"!~a(1)||~b(1)||~c(1);"),a = snapshot(false,"!d(1)->a(1);"),b = snapshot(false,"!d(1)->b(1);"),c = snapshot(false,"!d(1); !c(1);");
            Frontier f = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b,c),WORK);
            List<Receipt> old = pass(f,false);
            require(!ContextGroundCompositionCoverage.audit(f,old,ContextGroundCompositionCoverage.Mode.INPUT_STEP,16,WORK).covered,"old reduction receipts cannot hide missing composition");
            List<Receipt> composed = pass(f,true);
            require(ContextGroundCompositionCoverage.audit(f,composed,ContextGroundCompositionCoverage.Mode.INPUT_STEP,16,WORK).covered,"complete first composition step covered");
            Barrier first = ContextOrdinaryResidualRounds.merge(f,composed,WORK);
            require(first.status == Status.MERGED && first.changed,"first common barrier accepts only authenticated results");
            require(first.frontier.products.stream().anyMatch(p -> p.clause.literals.size() == 3 && p.clause.literals.stream().anyMatch(at -> at.predicate.equals("d")) && p.proof.depth == 1 && p.proof.sources.size() == 2),"first missing composed clause crosses common barrier with exact sources/depth");
            require(first.frontier.products.stream().allMatch(p -> p.proof.depth == 1),"new cross-cut compositions cannot feed another cut inside the first common round");
            Set<Source> expected = new HashSet<>(); for (Snapshot cut : Arrays.asList(x,a,b,c)) for (ContextProductionNativeBinding.Entry entry : cut.primary) expected.add(entry.source);
            int forward = refute(x,Arrays.asList(a,b,c),expected),reverse = refute(x,Arrays.asList(c,b,a),expected);
            require(forward == reverse,"immutable barriers make the collision round independent of sibling order");
            require(forward == 3,"one composition barrier, one consequence barrier and collision at third barrier");
            Snapshot sx = snapshot(true,"!a(1)||b(1);"),sa = snapshot(false,"!~a(1)||c(1);");
            Frontier stable = ContextOrdinaryResidualRounds.start(sx,Collections.singletonList(sa),WORK);
            List<Receipt> stableReceipts = pass(stable,true);
            Barrier sb = ContextOrdinaryResidualRounds.merge(stable,stableReceipts,WORK);
            require(sb.status == Status.MERGED && sb.changed,"ground example first learns composed clause");
            stable = sb.frontier; stableReceipts = pass(stable,true);
            require(ContextOrdinaryResidualRounds.merge(stable,stableReceipts,WORK).status == Status.MERGED
                    && !ContextOrdinaryResidualRounds.merge(stable,stableReceipts,WORK).changed,"second barrier has unchanged recipes");
            require(ContextGroundCompositionCoverage.audit(stable,stableReceipts,ContextGroundCompositionCoverage.Mode.LOCAL_CLOSURE,16,WORK).covered,"stable local ground compound closure is independently covered");
            require(ContextOrdinaryResidualCoverage.audit(stable,stableReceipts,ContextOrdinaryResidualCoverage.Mode.LOCAL_CLOSURE,16,WORK).covered,"unit-donor local audit remains separately required and passes");
            require(!ContextGroundCompositionCoverage.audit(stable,stableReceipts,ContextGroundCompositionCoverage.Mode.LOCAL_CLOSURE,16,1).covered,"coverage work stop does not grant authority");
            require(!ContextGroundCompositionCoverage.audit(stable,stableReceipts,ContextGroundCompositionCoverage.Mode.LOCAL_CLOSURE,0,WORK).covered,"coverage rejects carried proof beyond depth allowance");
            Snapshot unrelated = snapshot(true,"!anchor(1);"),left = snapshot(false,"!a(1)||b(1); !~a(1)||c(1);"),right = snapshot(false,"!~b(1); !~c(1);");
            Frontier independent = ContextOrdinaryResidualRounds.start(unrelated,Arrays.asList(left,right),WORK);
            List<Receipt> separated = pass(independent,true);
            require(separated.stream().allMatch(r -> r.products.isEmpty()),"foreign-only local compositions do not become X products");
            require(ContextOrdinaryResidualRounds.merge(independent,separated,WORK).status == Status.MERGED,"independent siblings remain compatible with X separately");
            require(separated.stream().anyMatch(r -> r.localProofs.stream().anyMatch(p -> p.proof.depth == 1 && p.clause.literals.size() == 2)),"foreign-only local composition is retained for local reasoning");
            Snapshot chain = snapshot(false,"!a(1)||b(1); !~a(1)||c(1); !~c(1)||d(1);");
            Frontier privateChain = ContextOrdinaryResidualRounds.start(unrelated,Collections.singletonList(chain),WORK);
            List<Receipt> chainReceipts = pass(privateChain,true);
            Barrier chainBarrier = ContextOrdinaryResidualRounds.merge(privateChain,chainReceipts,WORK);
            require(chainBarrier.status == Status.MERGED && !chainBarrier.changed,"private foreign compositions do not alter common X pool");
            ContextGroundCompositionCoverage.Audit privateAudit = ContextGroundCompositionCoverage.audit(privateChain,chainReceipts,ContextGroundCompositionCoverage.Mode.LOCAL_CLOSURE,16,WORK);
            require(!privateAudit.covered && privateAudit.reason.contains("lacks a checked whole-source proof"),"unchanged common pool cannot conceal unfinished private composition chain");
            Clause missingPrivate = snapshot(false,"!b(1)||d(1);").primary.get(0).clauses.get(0);
            require(chainReceipts.stream().allMatch(r -> r.localProofs.stream().noneMatch(p -> p.clause.equivalent(missingPrivate))),"concrete private second-step b-or-d is absent, rather than a budget artifact");
            require(!ContextGroundCompositionCoverage.audit(f,Collections.singletonList(composed.get(0)),ContextGroundCompositionCoverage.Mode.INPUT_STEP,16,WORK).covered,"missing cut blocks coverage");
            List<Receipt> duplicate = new ArrayList<>(composed); duplicate.set(1,duplicate.get(0));
            require(!ContextGroundCompositionCoverage.audit(f,duplicate,ContextGroundCompositionCoverage.Mode.INPUT_STEP,16,WORK).covered,"duplicate cut blocks coverage");
            require(!ContextGroundCompositionCoverage.audit(first.frontier,composed,ContextGroundCompositionCoverage.Mode.INPUT_STEP,16,WORK).covered,"stale barrier receipts block coverage");
            for (int type=0;type<3;++type) {
                Receipt limited = ContextOrdinaryResidualRounds.inspectWithCompositions(f,a,type==0?0:16,type==1?1:WORK,type==2?1:200000,256);
                require(!limited.verified && limited.products.isEmpty() && limited.collisions.isEmpty(),"composition limit returns no earlier successful prefix");
            }
            Snapshot px = snapshot(true,"!left(1)&&right(1)->p(1);"),pa = snapshot(false,"!left(1); !right(1);");
            Frontier pf = ContextOrdinaryResidualRounds.start(px,Collections.singletonList(pa),WORK);
            Receipt probeStop = ContextOrdinaryResidualRounds.inspectWithCompositions(pf,pa,16,WORK,200000,1);
            require(!probeStop.verified && probeStop.products.isEmpty(),"composition phase cannot reset prior probe allowance");
            Snapshot ux = snapshot(true,"!@x a(x)->b(x);"),ua = snapshot(false,"!anchor(1);");
            Frontier uf = ContextOrdinaryResidualRounds.start(ux,Collections.singletonList(ua),WORK);
            List<Receipt> universal = pass(uf,false);
            require(!ContextGroundCompositionCoverage.audit(uf,universal,ContextGroundCompositionCoverage.Mode.INPUT_STEP,16,WORK).covered,"universal composition remains explicitly outside ground coverage");
            System.out.println("CONTEXT_GROUND_COMPOSITION_ROUNDS_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
