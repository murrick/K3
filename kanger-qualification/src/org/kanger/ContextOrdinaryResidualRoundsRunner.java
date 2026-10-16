/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;

/** Signed ordinary residuals with sealed barriers and original source/depth authority. */
public final class ContextOrdinaryResidualRoundsRunner {
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
    private static List<Receipt> pass(Frontier f,int depth) {
        List<Receipt> outputs = new ArrayList<>();
        for (Snapshot cut : f.cuts) outputs.add(ContextOrdinaryResidualRounds.inspect(f,cut,depth,WORK,100000)); return outputs;
    }
    private static Barrier merge(Frontier f) { return ContextOrdinaryResidualRounds.merge(f,pass(f,16),WORK); }
    private static void merged(Barrier b,String why) { require(b.status == Status.MERGED,why+": "+b.reason); }
    private static void incomplete(Barrier b,String why) { require(b.status == Status.INCOMPLETE && b.frontier == null && b.collisions.isEmpty(),why+": "+b.reason); }
    private static boolean proof(Frontier f,String predicate,int literals,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return f.products.stream().anyMatch(p -> p.clause.literals.size() == literals && p.clause.literals.stream().anyMatch(a -> a.predicate.equals(predicate))
                && p.proof.depth == depth && p.proof.sources.equals(expected));
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ordinary-residual-").toString());
            Snapshot x = snapshot(true,"!@x left(x)&&right(x)->p(x);"),a = snapshot(false,"!left(1);"),b = snapshot(false,"!right(1);");
            Frontier initial = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b),WORK); List<Receipt> firstReceipts = pass(initial,16);
            for (Receipt receipt : firstReceipts) require(receipt.verified,"first actual residual proof: "+receipt.reason);
            Barrier first = ContextOrdinaryResidualRounds.merge(initial,firstReceipts,WORK); merged(first,"partial clauses cross first barrier");
            require(first.changed && first.frontier.basis.equals(initial.identity),"new residual pool has exact basis");
            require(proof(first.frontier,"right",2,1,x.primary.get(0).source,a.primary.get(0).source),"right(1)->p(1) has exactly X/A sources at depth1");
            require(proof(first.frontier,"left",2,1,x.primary.get(0).source,b.primary.get(0).source),"symmetric residual has exactly X/B sources at depth1");
            require(first.frontier.products.stream().noneMatch(p -> p.clause.literals.size() == 1),"raw sibling facts never enter shared pool");
            Product actualResidual = first.frontier.products.stream().filter(p -> p.clause.literals.stream().anyMatch(atom -> atom.predicate.equals("right"))).findFirst().get();
            Residual actualEvent = new Residual(actualResidual.clause,Collections.singletonList(a.primary.get(0).clauses.get(0).literals.get(0).fact()),Collections.singletonList(new Support(Collections.singleton(x.primary.get(0).source),0)));
            require(ContextOrdinaryResidualVerifier.instance(x.primary.get(0).clauses.get(0),actualEvent,()->{}),"whole-clause structural reduction authenticates actual left donor");
            Residual wrongDonor = new Residual(actualResidual.clause,Collections.singletonList(b.primary.get(0).clauses.get(0).literals.get(0).fact()),actualEvent.producerSupports);
            require(!ContextOrdinaryResidualVerifier.instance(x.primary.get(0).clauses.get(0),wrongDonor,()->{}),"wrong excluded donor cannot authenticate the same residual");
            require(!ContextOrdinaryResidualVerifier.instance(a.primary.get(0).clauses.get(0),actualEvent,()->{}),"an unrelated source unit cannot own a compound reduction");
            Barrier second = merge(first.frontier); merged(second,"residual enables later native consequence");
            require(proof(second.frontier,"p",1,2,x.primary.get(0).source,a.primary.get(0).source,b.primary.get(0).source),"p(1) retains both original cuts and cumulative depth2");
            Frontier reversed = ContextOrdinaryResidualRounds.start(x,Arrays.asList(b,a),WORK); Barrier reverseFirst = merge(reversed),reverseSecond = merge(reverseFirst.frontier);
            merged(reverseSecond,"reversed cut order"); require(proof(reverseSecond.frontier,"p",1,2,x.primary.get(0).source,a.primary.get(0).source,b.primary.get(0).source),"reverse order preserves exact proof");
            Snapshot negative = snapshot(false,"!~p(1);");
            Frontier withNegative = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b,negative),WORK);
            Barrier c1 = merge(withNegative),c2 = merge(c1.frontier),c3 = merge(c2.frontier);
            require(c3.status == Status.COLLISION && c3.frontier == null,"separate negative cut refutes transported p at third complete barrier");
            Snapshot maskedA = snapshot(false,"!left(1); !p(1);"),maskedB = snapshot(false,"!right(1); !~p(1);");
            Barrier masked1 = merge(ContextOrdinaryResidualRounds.start(x,Arrays.asList(maskedA,maskedB),WORK)),masked2 = merge(masked1.frontier);
            require(masked2.status == Status.COLLISION,"original masked fixture refutes at second common barrier");
            List<Receipt> missing = new ArrayList<>(firstReceipts); missing.remove(0);
            incomplete(ContextOrdinaryResidualRounds.merge(initial,missing,WORK),"missing self cannot publish residual prefix");
            List<Receipt> duplicate = new ArrayList<>(firstReceipts); duplicate.set(0,duplicate.get(1));
            incomplete(ContextOrdinaryResidualRounds.merge(initial,duplicate,WORK),"duplicate cut cannot replace self");
            incomplete(ContextOrdinaryResidualRounds.merge(first.frontier,firstReceipts,WORK),"previous-barrier receipts are stale");
            incomplete(ContextOrdinaryResidualRounds.merge(initial,firstReceipts,1),"merge work limit cannot publish prefix");
            incomplete(ContextOrdinaryResidualRounds.merge(first.frontier,pass(first.frontier,1),WORK),"derived residual owner depth is not reset to zero");
            Receipt eventStop = ContextOrdinaryResidualRounds.inspect(first.frontier,b,16,WORK,1);
            require(!eventStop.verified && eventStop.products.isEmpty() && eventStop.collisions.isEmpty(),"event stop has no proof prefix");
            Snapshot a2 = snapshot(false,"!left(1);");
            Barrier alt1 = merge(ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,a2,b),WORK)),alt2 = merge(alt1.frontier); merged(alt2,"independent alternatives");
            require(proof(alt2.frontier,"p",1,2,x.primary.get(0).source,a.primary.get(0).source,b.primary.get(0).source)
                    && proof(alt2.frontier,"p",1,2,x.primary.get(0).source,a2.primary.get(0).source,b.primary.get(0).source),"one semantic carrier preserves separate source alternatives");
            Snapshot unrelatedX = snapshot(true,"!anchor(1);"),local = snapshot(false,"!@x left(x)&&right(x)->p(x); !left(1);");
            Barrier foreignOnly = merge(ContextOrdinaryResidualRounds.start(unrelatedX,Arrays.asList(local,b),WORK)); merged(foreignOnly,"foreign-only residual control");
            require(!foreignOnly.changed && foreignOnly.frontier.products.isEmpty(),"pure foreign residual is not lifted into X");
            Snapshot wideX = snapshot(true,"!@x @y left(x)&&right(y)->pair(x,y);");
            Barrier wide1 = merge(ContextOrdinaryResidualRounds.start(wideX,Arrays.asList(a,b),WORK)),wide2 = merge(wide1.frontier); merged(wide2,"still-unbound variable survives transport");
            require(wide1.frontier.products.stream().anyMatch(p -> p.clause.variables.size() == 1 && p.clause.literals.size() == 2),"partial clause preserves an actual scoped universal variable");
            require(proof(wide2.frontier,"pair",1,2,wideX.primary.get(0).source,a.primary.get(0).source,b.primary.get(0).source),"universal residual instantiates only through actual native application");
            List<Receipt> stoppedCollision = pass(masked1.frontier,16);
            stoppedCollision.set(0,ContextOrdinaryResidualRounds.inspect(masked1.frontier,x,16,1,100000));
            incomplete(ContextOrdinaryResidualRounds.merge(masked1.frontier,stoppedCollision,WORK),"proved collision prefix withheld when another cut is stopped");
            Frontier independentInput = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b),WORK);
            incomplete(ContextOrdinaryResidualRounds.merge(independentInput,firstReceipts,WORK),"equal-content independent frontier is not the receipt basis");
            Snapshot negativeHeadX = snapshot(true,"!@x left(x)&&right(x)->~p(x);");
            Barrier neg1 = merge(ContextOrdinaryResidualRounds.start(negativeHeadX,Arrays.asList(a,b),WORK)),neg2 = merge(neg1.frontier); merged(neg2,"signed negative head through partial residual");
            require(neg2.frontier.products.stream().anyMatch(p -> p.clause.literals.size() == 1 && !p.clause.literals.get(0).positive && p.clause.literals.get(0).predicate.equals("p") && p.proof.depth == 2),"negative conclusion keeps cumulative residual depth");
            Snapshot namedA = snapshot(false,"!left(John);"),namedB = snapshot(false,"!right(Mary);");
            Barrier named1 = merge(ContextOrdinaryResidualRounds.start(wideX,Arrays.asList(namedA,namedB),WORK)),named2 = merge(named1.frontier); merged(named2,"named semantic constants survive residual projection");
            require(proof(named2.frontier,"pair",1,2,wideX.primary.get(0).source,namedA.primary.get(0).source,namedB.primary.get(0).source),"named result retains whole original source group");
            Snapshot universalReductionX = snapshot(true,"!@x @y rel(x,y)->p(x);"),negativeP = snapshot(false,"!~p(1);");
            Frontier universalReduction = ContextOrdinaryResidualRounds.start(universalReductionX,Collections.singletonList(negativeP),WORK);
            Receipt unsupportedUniversal = ContextOrdinaryResidualRounds.inspect(universalReduction,negativeP,16,WORK,100000);
            require(!unsupportedUniversal.verified && unsupportedUniversal.products.isEmpty(),"derived universal unit also refuses rather than silently entering unsupported transport");
            boolean immutable = false; try { first.frontier.products.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; }
            require(immutable,"residual frontier immutable");
            System.out.println("CONTEXT_ORDINARY_RESIDUAL_ROUNDS_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
