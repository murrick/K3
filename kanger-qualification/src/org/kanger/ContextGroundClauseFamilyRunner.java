/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextGroundClauseFamily.*;
import org.kanger.ContextGroundClauseFamily.Status;

/** Independent direct clause evaluation and exhaustive small-theory controls. */
public final class ContextGroundClauseFamilyRunner {
    private static int checks,ids;
    private static final int WORK = 16000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        if (!text.isEmpty()) require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"native binding"); return bound.snapshot;
    }
    private static Evaluation evaluate(Snapshot x,Snapshot... cuts) { return ContextGroundClauseFamily.evaluate(x,Arrays.asList(cuts),16,16,16,WORK,500000,256,4096); }
    private static void fixed(Evaluation e,String why) {
        require(e.status == Status.GROUND_FIXED_POINT && e.certificate != null && e.collisions.isEmpty(),why+": "+e.reason);
        require(e.certificate.scope == Scope.ORDINARY_GROUND_UNIT_AND_COMPOUND_RESOLUTION && e.reason.contains("admission unknown"),"ground scope and no publication lease");
    }
    private static void incomplete(Evaluation e,String why) { require(e.status == Status.INCOMPLETE && e.certificate == null && e.collisions.isEmpty(),why+": "+e.reason); }
    private static List<Receipt> pass(Frontier f,boolean local) {
        List<Receipt> outputs = new ArrayList<>();
        for (Snapshot cut : f.cuts) outputs.add(local
                ? ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,cut,16,WORK,500000,256,16)
                : ContextOrdinaryResidualRounds.inspectWithCompositions(f,cut,16,WORK,500000,256));
        return outputs;
    }
    private static boolean truth(Model model,Clause clause) {
        for (Atom atom : clause.literals) {
            Fact fact = atom.fact(),positive = fact.positive ? fact : fact.opposite();
            boolean present = model.positiveAtoms.stream().anyMatch(p -> p.equivalent(positive));
            if (fact.positive == present) return true;
        }
        return false;
    }
    private static void models(Certificate certificate) {
        require(certificate.models.size() == certificate.frontier.cuts.size(),"self and every cut have separate models");
        int index = 0;
        for (Model model : certificate.models) {
            require(model.target == certificate.frontier.cuts.get(index++),"canonical cut order independent of receipt arrival");
            List<Clause> theory = new ArrayList<>();
            for (ContextProductionNativeBinding.Entry entry : certificate.frontier.x.primary) theory.addAll(entry.clauses);
            if (model.target != certificate.frontier.x) for (ContextProductionNativeBinding.Entry entry : model.target.primary) theory.addAll(entry.clauses);
            for (Product product : certificate.frontier.products) theory.add(product.clause);
            Receipt replayed = ContextOrdinaryResidualRounds.inspectWithLocalCompositions(certificate.frontier,model.target,16,WORK,500000,256,16);
            require(replayed.verified && replayed.collisions.isEmpty(),"independently replay local theory for model oracle");
            for (Product product : replayed.localProofs) theory.add(product.clause);
            for (Clause clause : theory) require(truth(model,clause),"direct semantic membership satisfies every whole original, shared and local clause");
            require(model.positiveAtoms.stream().allMatch(a -> a.positive),"chosen witness lists only positive atoms, not inferred negative knowledge");
        }
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ground-clause-family-").toString());
            Snapshot x = snapshot(true,"!anchor(1);"),a = snapshot(false,"!a(1)||b(1); !~a(1)||c(1); !~c(1)||d(1);");
            Evaluation privateChain = evaluate(x,a); fixed(privateChain,"private non-Horn chain completes without communal facts");
            require(privateChain.rounds == 1 && privateChain.certificate.frontier.products.isEmpty(),"closed private chain and unchanged shared pool certify on first common barrier"); models(privateChain.certificate);
            Frontier f = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(a),WORK);
            incomplete(ContextGroundClauseFamily.certify(f,pass(f,false),16,WORK,4096),"old one-sweep unchanged frontier cannot hide missing private composition");
            List<Receipt> receipts = pass(f,true); fixed(ContextGroundClauseFamily.certify(f,receipts,16,WORK,4096),"direct exact-input certificate");
            List<Receipt> reversed = new ArrayList<>(receipts); Collections.reverse(reversed);
            Evaluation reversedCertificate = ContextGroundClauseFamily.certify(f,reversed,16,WORK,4096); fixed(reversedCertificate,"reversed receipt arrival"); models(reversedCertificate.certificate);
            incomplete(ContextGroundClauseFamily.certify(f,Collections.singletonList(receipts.get(0)),16,WORK,4096),"missing cut");
            List<Receipt> duplicates = new ArrayList<>(receipts); duplicates.set(1,duplicates.get(0));
            incomplete(ContextGroundClauseFamily.certify(f,duplicates,16,WORK,4096),"duplicate cut");
            Barrier same = ContextOrdinaryResidualRounds.merge(f,receipts,WORK);
            incomplete(ContextGroundClauseFamily.certify(same.frontier,receipts,16,WORK,4096),"another sealed frontier identity cannot reuse receipts");
            Snapshot positive = snapshot(false,"!p(1);"),negative = snapshot(false,"!~p(1);");
            Evaluation independent = evaluate(x,positive,negative); fixed(independent,"raw opposite sibling facts remain separate"); models(independent.certificate);
            require(independent.certificate.frontier.products.isEmpty(),"model choices do not create common knowledge");
            require(independent.certificate.models.stream().filter(m -> m.target == positive).findFirst().get().positiveAtoms.stream().anyMatch(p -> p.predicate.equals("p"))
                    && independent.certificate.models.stream().filter(m -> m.target == negative).findFirst().get().positiveAtoms.stream().noneMatch(p -> p.predicate.equals("p")),"independent witnesses may disagree on p");
            Snapshot values = snapshot(false,"!p(1); !~p(2);"); Evaluation semantic = evaluate(x,values); fixed(semantic,"model indexes distinguish semantic argument values"); models(semantic.certificate);
            Snapshot mx = snapshot(true,"!~a(1)||~b(1)||~c(1);"),ma = snapshot(false,"!d(1)->a(1);"),mb = snapshot(false,"!d(1)->b(1);"),mc = snapshot(false,"!d(1); !c(1);");
            Evaluation masked = evaluate(mx,ma,mb,mc),maskedReverse = evaluate(mx,mc,mb,ma);
            require(masked.status == Status.COLLISION && maskedReverse.status == Status.COLLISION && masked.rounds == 3 && maskedReverse.rounds == 3 && masked.certificate == null,"original composition counterexample rejects at third common barrier in both orders");
            Set<Source> expected = new HashSet<>(); for (Snapshot cut : Arrays.asList(mx,ma,mb,mc)) for (ContextProductionNativeBinding.Entry entry : cut.primary) expected.add(entry.source);
            require(masked.collisions.stream().anyMatch(c -> { Set<Source> s = new HashSet<>(c.leftProof.sources); s.addAll(c.rightProof.sources); return s.equals(expected); }),"exact five causal sources in typed collision");
            Snapshot splitX = snapshot(true,"!left(1)&&right(1)->p(1);"),splitA = snapshot(false,"!left(1);"),splitB = snapshot(false,"!right(1);");
            Evaluation split = evaluate(splitX,splitA,splitB); fixed(split,"ground split rule completes and has separate actual models"); models(split.certificate);
            require(split.rounds == 3,"split rule needs two changing barriers then stable confirmation");
            Frontier changed = ContextOrdinaryResidualRounds.start(splitX,Arrays.asList(splitA,splitB),WORK);
            incomplete(ContextGroundClauseFamily.certify(changed,pass(changed,true),16,WORK,4096),"changed merge cannot certify");
            incomplete(ContextGroundClauseFamily.evaluate(splitX,Arrays.asList(splitA,splitB),1,16,16,WORK,500000,256,4096),"common round limit");
            incomplete(ContextGroundClauseFamily.evaluate(x,Collections.singletonList(a),16,1,16,WORK,500000,256,4096),"private local round limit");
            incomplete(ContextGroundClauseFamily.evaluate(x,Collections.singletonList(a),16,16,1,WORK,500000,256,4096),"cumulative depth limit");
            incomplete(ContextGroundClauseFamily.evaluate(x,Collections.singletonList(a),16,16,16,1,500000,256,4096),"work limit");
            incomplete(ContextGroundClauseFamily.evaluate(x,Collections.singletonList(a),16,16,16,WORK,1,256,4096),"event limit");
            incomplete(ContextGroundClauseFamily.evaluate(x,Collections.singletonList(a),16,16,16,WORK,500000,256,1),"model assignment limit gives unknown, never a refutation");
            Snapshot ux = snapshot(true,"!@x a(x)->b(x);");
            Evaluation unsupported = evaluate(ux,a); incomplete(unsupported,"universal domain cannot be truncated to mentioned values"); require(unsupported.rounds == 0,"whole family ground preflight before replay");
            boolean immutable = false; try { independent.certificate.models.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; } require(immutable,"model list immutable");
            immutable = false; try { independent.certificate.models.get(0).positiveAtoms.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; } require(immutable,"model atoms immutable");
            // All sign choices for X (a∨b) and A (¬a∨c); direct truth tables are independent of the resolution code.
            int cases = 0;
            for (int bits=0;bits<8;++bits) {
                String p = (bits&1)==0 ? "a(1)" : "~a(1)",opposite = (bits&1)==0 ? "~a(1)" : "a(1)";
                String l = (bits&2)==0 ? "b(1)" : "~b(1)",r = (bits&4)==0 ? "c(1)" : "~c(1)";
                Snapshot ox = snapshot(true,"!"+p+"||"+l+";"),oa = snapshot(false,"!"+opposite+"||"+r+";");
                Evaluation e = evaluate(ox,oa); fixed(e,"signed ground clause pair"); models(e.certificate); ++cases;
            }
            require(cases == 8,"all sign orientations certified with independent model checks");
            Snapshot lateUniversal = snapshot(false,"!@x a(x)->b(x);");
            Evaluation late = evaluate(x,a,lateUniversal); incomplete(late,"unsupported later cut cannot inherit earlier successful prefixes"); require(late.rounds == 0,"all cuts preflight before any replay");
            Snapshot negativeX = snapshot(true,"!~anchor(1);");
            Evaluation lateModelStop = ContextGroundClauseFamily.evaluate(negativeX,Collections.singletonList(positive),16,16,16,WORK,500000,256,1);
            incomplete(lateModelStop,"later cut model exhaustion cannot expose successful self model"); require(lateModelStop.reason.contains("assignment limit"),"failure is specifically bounded model search");
            Snapshot both = snapshot(false,"!left(1); !right(1);");
            incomplete(ContextGroundClauseFamily.evaluate(splitX,Collections.singletonList(both),16,16,16,WORK,500000,1,4096),"probe allowance is not reset by whole-family evaluator");
            Evaluation empty = evaluate(snapshot(true,"")); fixed(empty,"empty ground theory has an empty witness"); models(empty.certificate);
            String[] texts = {"!p(1);","!~p(1);","!p(1)->q(1);","!q(1)->p(1);","!~p(1)||~q(1);","!p(1)&&q(1)->r(1);","!~r(1);","!r(1);","!p(1)||q(1);","!q(1)||r(1);"};
            int[][] truthClauses = {{1},{-1},{-1,2},{-2,1},{-1,-2},{-1,-2,3},{-3},{3},{1,2},{2,3}};
            Snapshot[] xs = new Snapshot[texts.length],fs = new Snapshot[texts.length];
            for (int i=0;i<texts.length;++i) { xs[i] = snapshot(true,texts[i]); fs[i] = snapshot(false,texts[i]); }
            int certified = 0,refuted = 0;
            for (int i=0;i<texts.length;++i) for (int j=0;j<texts.length;++j) {
                boolean satisfiable = false;
                for (int assignment=0;assignment<8;++assignment) {
                    boolean all = true;
                    for (int[] clause : Arrays.asList(truthClauses[i],truthClauses[j])) {
                        boolean any = false;
                        for (int literal : clause) any |= (((assignment >> (Math.abs(literal)-1)) & 1) == 1) == (literal > 0);
                        all &= any;
                    }
                    satisfiable |= all;
                }
                Evaluation observed = evaluate(xs[i],fs[j]);
                require(observed.status != Status.GROUND_FIXED_POINT || satisfiable,"certificate matches independent exhaustive SAT oracle "+i+"/"+j);
                require(observed.status != Status.COLLISION || !satisfiable,"sourced collision matches independent exhaustive SAT oracle "+i+"/"+j);
                if (observed.status == Status.GROUND_FIXED_POINT) { ++certified; models(observed.certificate); }
                if (observed.status == Status.COLLISION) ++refuted;
            }
            require(certified == 96 && refuted == 4,"all100 small ground Horn/non-Horn pairs settle with correct verdicts");
            System.out.println("GROUND_FAMILY_EXHAUSTIVE_ORACLE cases=100 certified="+certified+" refuted="+refuted);

            System.out.println("GROUND_FAMILY_MODEL_ORACLE cases="+cases);
            System.out.println("CONTEXT_GROUND_CLAUSE_FAMILY_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
