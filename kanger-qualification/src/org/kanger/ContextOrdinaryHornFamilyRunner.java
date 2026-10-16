/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.Frontier;
import org.kanger.ContextOrdinaryResidualRounds.Receipt;
import org.kanger.ContextOrdinaryHornFamily.*;

/** Complete common barriers, independent model witnesses and explicit reduction-only scope. */
public final class ContextOrdinaryHornFamilyRunner {
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
    private static Evaluation evaluate(Snapshot x,Snapshot... foreign) { return ContextOrdinaryHornFamily.evaluate(x,Arrays.asList(foreign),16,16,WORK,100000,64); }
    private static void fixed(Evaluation e,String why) {
        require(e.status == Status.REDUCTION_FIXED_POINT && e.certificate != null && e.collisions.isEmpty(),why+": "+e.reason);
        require(e.certificate.scope == Scope.ORDINARY_HORN_UNIT_DONOR_REDUCTIONS && e.reason.contains("admission unknown"),"certificate is explicitly scoped, not publication permission");
    }
    private static void incomplete(Evaluation e,String why) { require(e.status == Status.INCOMPLETE && e.certificate == null && e.collisions.isEmpty(),why+": "+e.reason); }
    private static List<Receipt> pass(Frontier input) {
        List<Receipt> outputs = new ArrayList<>();
        for (Snapshot cut : input.cuts) outputs.add(ContextOrdinaryResidualRounds.inspectWithProbes(input,cut,16,WORK,100000,64)); return outputs;
    }
    private static boolean atom(Model model,Fact fact) {
        Fact positive = fact.positive ? fact : fact.opposite(); boolean present = model.positiveAtoms.stream().anyMatch(p -> p.equivalent(positive));
        return fact.positive ? present : !present;
    }
    private static void assignments(Model model,Clause clause,List<Integer> variables,int index,Map<Integer,SemanticTermSnapshot> bindings,List<SemanticTermSnapshot> basis) {
        if (index == variables.size()) {
            Clause ground = clause.instantiate(bindings); boolean trueLiteral = false;
            for (Atom literal : ground.literals) trueLiteral |= atom(model,literal.fact());
            require(trueLiteral,"independent model oracle satisfies every sampled original/carried clause instance"); return;
        }
        for (SemanticTermSnapshot value : basis) { bindings.put(variables.get(index),value); assignments(model,clause,variables,index+1,bindings,basis); }
        bindings.remove(variables.get(index));
    }
    private static void verifyModels(ReductionCertificate certificate) throws Exception {
        User u = new User(); Mind fresh = new Mind(u); u.setCurrentMind(fresh);
        SemanticTermSnapshot newValue = SemanticTermSnapshot.capture(fresh.getTerms().add("NeverMentionedModelValue"));
        require(certificate.models.size() == certificate.frontier.cuts.size(),"self and every cut have a separate witness");
        for (Model model : certificate.models) {
            List<Clause> clauses = new ArrayList<>();
            for (ContextProductionNativeBinding.Entry entry : certificate.frontier.x.primary) clauses.addAll(entry.clauses);
            if (model.target != certificate.frontier.x) for (ContextProductionNativeBinding.Entry entry : model.target.primary) clauses.addAll(entry.clauses);
            for (ContextOrdinaryResidualRounds.Product product : certificate.frontier.products) clauses.add(product.clause);
            List<SemanticTermSnapshot> basis = new ArrayList<>(); basis.add(newValue);
            for (Clause clause : clauses) for (Atom atom : clause.literals) for (Slot slot : atom.arguments)
                if (slot.variable < 0 && basis.stream().noneMatch(v -> v.semanticallyEquals(slot.value))) basis.add(slot.value);
            for (Clause clause : clauses) assignments(model,clause,new ArrayList<>(clause.variables),0,new HashMap<>(),basis);
        }
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ordinary-horn-family-").toString());
            Snapshot x = snapshot(true,"!@x left(x)&&right(x)->p(x);"),a = snapshot(false,"!left(1);"),b = snapshot(false,"!right(1);");
            Evaluation split = evaluate(x,a,b),reverse = evaluate(x,b,a); fixed(split,"split antecedents reach complete residual reduction closure"); fixed(reverse,"reverse cut order reaches same scoped result");
            require(split.rounds == 3 && reverse.rounds == 3,"two discovery rounds precede unchanged full local audit");
            require(split.certificate.frontier.products.stream().anyMatch(p -> p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("p") && p.proof.depth == 2 && p.proof.sources.size() == 3),"common consequence retains all original sources and depth2");
            verifyModels(split.certificate);
            Snapshot maskedA = snapshot(false,"!left(1); !p(1);"),maskedB = snapshot(false,"!right(1); !~p(1);");
            Evaluation masked = evaluate(x,maskedA,maskedB),maskedReverse = evaluate(x,maskedB,maskedA);
            require(masked.status == Status.COLLISION && maskedReverse.status == Status.COLLISION && masked.rounds == 2 && maskedReverse.rounds == 2 && masked.certificate == null,"original masked contradiction refutes in two synchronous rounds in either order");
            Set<Source> expected = new HashSet<>(); expected.add(x.primary.get(0).source);
            for (ContextProductionNativeBinding.Entry entry : maskedB.primary) expected.add(entry.source);
            expected.add(maskedA.primary.stream().filter(e -> e.statement.contains("left")).findFirst().get().source);
            require(masked.collisions.stream().anyMatch(c -> { Set<Source> sources = new HashSet<>(c.leftProof.sources); sources.addAll(c.rightProof.sources); return sources.equals(expected); }),"collision retains exact four causal sources");
            Snapshot unrelated = snapshot(true,"!anchor(1);"),positive = snapshot(false,"!p(1);"),negative = snapshot(false,"!~p(1);");
            Evaluation independent = evaluate(unrelated,positive,negative); fixed(independent,"raw sibling opposition gets independent consistent models");
            require(independent.rounds == 1 && independent.certificate.frontier.products.isEmpty(),"no communal raw knowledge or model-false negations enter the shared pool");
            require(independent.certificate.models.stream().filter(m -> m.target == positive).findFirst().get().positiveAtoms.stream().anyMatch(p -> p.predicate.equals("p"))
                    && independent.certificate.models.stream().filter(m -> m.target == negative).findFirst().get().positiveAtoms.stream().noneMatch(p -> p.predicate.equals("p")),"chosen model witnesses may disagree on p"); verifyModels(independent.certificate);
            Snapshot genericX = snapshot(true,"!@x @y anchor(1)&&rel(x,y)->out(x,y);"),genericA = snapshot(false,"!anchor(1); !rel(John,Mary);");
            Evaluation generic = evaluate(genericX,genericA); fixed(generic,"two-free-variable proof views settle through audited residual barriers");
            require(generic.certificate.frontier.products.stream().anyMatch(p -> p.clause.variables.size() == 2 && p.proof.depth == 1 && p.proof.sources.size() == 2),"scoped certificate includes the actual generic theorem"); verifyModels(generic.certificate);
            Frontier legacyInput = ContextOrdinaryResidualRounds.start(genericX,Collections.singletonList(genericA),WORK);
            List<Receipt> legacyOutputs = null; boolean stableLegacy = false;
            for (int i=0;i<4;++i) {
                legacyOutputs = new ArrayList<>();
                for (Snapshot cut : legacyInput.cuts) legacyOutputs.add(ContextOrdinaryResidualRounds.inspectWithParameters(legacyInput,cut,16,WORK,100000));
                ContextOrdinaryResidualRounds.Barrier observed = ContextOrdinaryResidualRounds.merge(legacyInput,legacyOutputs,WORK);
                require(observed.status == ContextOrdinaryResidualRounds.Status.MERGED,"legacy sound observation barrier");
                if (!observed.changed) { stableLegacy = true; break; }
                legacyInput = observed.frontier;
            }
            require(stableLegacy,"legacy two-free-variable pool can stop changing without being covered");
            incomplete(ContextOrdinaryHornFamily.certify(legacyInput,legacyOutputs,16,WORK),"unchanged incomplete symbolic observation must not receive a model/closure certificate");
            Frontier initial = ContextOrdinaryResidualRounds.start(x,Arrays.asList(a,b),WORK); List<Receipt> first = pass(initial);
            incomplete(ContextOrdinaryHornFamily.certify(initial,first,16,WORK),"changed shared pool cannot certify");
            List<Receipt> missing = new ArrayList<>(first); missing.remove(0);
            incomplete(ContextOrdinaryHornFamily.certify(initial,missing,16,WORK),"missing self cannot certify");
            List<Receipt> duplicate = new ArrayList<>(first); duplicate.set(0,duplicate.get(1));
            incomplete(ContextOrdinaryHornFamily.certify(initial,duplicate,16,WORK),"duplicate cut cannot replace self");
            incomplete(ContextOrdinaryHornFamily.certify(split.certificate.frontier,first,16,WORK),"stale receipts cannot certify final input");
            Evaluation direct = ContextOrdinaryHornFamily.certify(split.certificate.frontier,pass(split.certificate.frontier),16,WORK); fixed(direct,"fresh exact-input receipts certify stable frontier");
            require(direct.certificate.frontier == split.certificate.frontier,"certificate binds the exact shared input object");
            List<Receipt> receiptOrder = pass(split.certificate.frontier); Collections.reverse(receiptOrder);
            Evaluation reordered = ContextOrdinaryHornFamily.certify(split.certificate.frontier,receiptOrder,16,WORK); fixed(reordered,"physical receipt order is irrelevant");
            require(reordered.certificate.models.get(0).target == split.certificate.frontier.x,"model witnesses follow sealed cut order, not receipt arrival order");
            incomplete(ContextOrdinaryHornFamily.evaluate(x,Arrays.asList(a,b),1,16,WORK,100000,64),"round limit cannot return discovered prefix");
            incomplete(ContextOrdinaryHornFamily.evaluate(x,Arrays.asList(a,b),16,1,WORK,100000,64),"cumulative depth cannot reset across barriers");
            incomplete(ContextOrdinaryHornFamily.evaluate(x,Arrays.asList(a,b),16,16,1,100000,64),"work stop is incomplete");
            incomplete(ContextOrdinaryHornFamily.evaluate(x,Arrays.asList(a,b),16,16,WORK,1,64),"event stop is incomplete");
            incomplete(ContextOrdinaryHornFamily.evaluate(genericX,Collections.singletonList(genericA),16,16,WORK,100000,1),"isolated probe stop is incomplete");
            Snapshot nonHorn = snapshot(false,"!p(1)||q(1);"); incomplete(evaluate(unrelated,positive,nonHorn),"unsupported later source preflights whole family");
            Snapshot universal = snapshot(true,"!@x p(x);"); incomplete(evaluate(universal),"universal unit remains outside scope");
            Snapshot unbound = snapshot(true,"!@x @y a(x)->b(y);"),unitA = snapshot(false,"!a(1);"); incomplete(evaluate(unbound,unitA),"enabled unbound head needs unsupported universal unit");
            Snapshot emptyX = snapshot(true,""); Evaluation empty = evaluate(emptyX); fixed(empty,"empty family has empty model"); verifyModels(empty.certificate);
            Snapshot dormant = snapshot(true,"!@x @y left(x)&&right(y)->pair(x,y);"); Evaluation dormantResult = evaluate(dormant); fixed(dormantResult,"dormant universal theory is modeled over unmentioned values too"); verifyModels(dormantResult.certificate);
            Snapshot compositionX = snapshot(true,"!~a(1)||~b(1)||~c(1);"),compositionA = snapshot(false,"!d(1)->a(1);"),compositionB = snapshot(false,"!d(1)->b(1);"),compositionC = snapshot(false,"!d(1); !c(1);");
            Evaluation composition = evaluate(compositionX,compositionA,compositionB,compositionC);
            fixed(composition,"unit-donor closure is explicitly narrower than arbitrary clause composition"); verifyModels(composition.certificate);
            require(composition.certificate.frontier.products.stream().noneMatch(p -> p.clause.literals.stream().anyMatch(atom -> atom.predicate.equals("d"))),"no unobserved d-containing clause composition is invented");
            // X+A entails H=(not d or not b or not c); H+C entails not b;
            // B+not b entails not d, which opposes C's actual d. Each step stays X-dependent.
            for (int assignment=0;assignment<16;++assignment) {
                boolean av = (assignment&1) != 0,bv = (assignment&2) != 0,cv = (assignment&4) != 0,dv = (assignment&8) != 0;
                boolean xv = !av || !bv || !cv,arule = !dv || av,brule = !dv || bv,h = !dv || !bv || !cv;
                require(!(xv && arule) || h,"independent truth table verifies the missing X+A resolvent");
                require(!(h && dv && cv) || !bv,"X-dependent resolvent with C forces negative b");
                require(!(brule && !bv) || !dv,"B with X-dependent negative b forces negative d");
            }
            boolean immutable = false; try { split.certificate.models.get(0).positiveAtoms.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; }
            require(immutable,"model witness atoms are immutable");
            String[] texts = {"!p(1);","!~p(1);","!p(1)->q(1);","!q(1)->p(1);","!~p(1)||~q(1);","!p(1)&&q(1)->r(1);","!~r(1);","!r(1);"};
            int[][] clauses = {{1},{-1},{-1,2},{-2,1},{-1,-2},{-1,-2,3},{-3},{3}};
            Snapshot[] xs = new Snapshot[texts.length],fs = new Snapshot[texts.length];
            for (int i=0;i<texts.length;++i) { xs[i] = snapshot(true,texts[i]); fs[i] = snapshot(false,texts[i]); }
            int certified = 0,refuted = 0;
            for (int i=0;i<texts.length;++i) for (int j=0;j<texts.length;++j) {
                boolean satisfiable = false;
                for (int assignment=0;assignment<8;++assignment) {
                    boolean all = true;
                    for (int[] clause : Arrays.asList(clauses[i],clauses[j])) {
                        boolean any = false;
                        for (int literal : clause) any |= (((assignment >> (Math.abs(literal)-1)) & 1) == 1) == (literal > 0);
                        all &= any;
                    }
                    satisfiable |= all;
                }
                Evaluation observed = evaluate(xs[i],fs[j]);
                require(observed.status != Status.REDUCTION_FIXED_POINT || satisfiable,"certificate agrees with independent truth table "+i+"/"+j);
                require(observed.status != Status.COLLISION || !satisfiable,"refutation agrees with independent truth table "+i+"/"+j);
                if (observed.status == Status.REDUCTION_FIXED_POINT) { ++certified; verifyModels(observed.certificate); }
                if (observed.status == Status.COLLISION) ++refuted;
            }
            require(certified == 60 && refuted == 4,"all64 finite oracle cases settled without incomplete or wrong verdicts");
            System.out.println("ORDINARY_HORN_MODEL_ORACLE cases=64 certified="+certified+" refuted="+refuted);
            System.out.println("CONTEXT_ORDINARY_HORN_FAMILY_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
