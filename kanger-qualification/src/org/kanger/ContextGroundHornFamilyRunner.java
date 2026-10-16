/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundCutEvidence.Result;
import org.kanger.ContextGroundProductionRounds.Frontier;
import org.kanger.ContextGroundHornFamily.*;

/** Separate Horn models, source-correct masked refutation and bounded synchronous settlement. */
public final class ContextGroundHornFamilyRunner {
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
    private static Evaluation evaluate(Snapshot x,Snapshot... foreign) { return ContextGroundHornFamily.evaluate(x,Arrays.asList(foreign),16,16,WORK,100000); }
    private static void fixed(Evaluation e,String why) { require(e.status == Status.UNIT_FIXED_POINT && e.certificate != null && e.collisions.isEmpty(),why+": "+e.reason); }
    private static void incomplete(Evaluation e,String why) { require(e.status == Status.INCOMPLETE && e.certificate == null && e.collisions.isEmpty(),why+": "+e.reason); }
    private static List<Result> pass(Frontier f) {
        List<Result> outputs = new ArrayList<>(); for (Snapshot cut : f.cuts) outputs.add(ContextGroundCutEvidence.inspect(f,cut,16,WORK,100000)); return outputs;
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("horn-family-").toString());
            Snapshot x = snapshot(true,"!@x left(x)&&right(x)->p(x);"),a = snapshot(false,"!left(1); !p(1);"),b = snapshot(false,"!right(1); !~p(1);");
            Evaluation forward = evaluate(x,a,b),reverse = evaluate(x,b,a);
            require(forward.status == Status.COLLISION && reverse.status == Status.COLLISION && forward.rounds == 2 && reverse.rounds == 2,"masked original fixture refutes in two synchronous rounds in both orders");
            require(forward.certificate == null && !forward.collisions.isEmpty(),"refutation has evidence and no consistency certificate");
            Set<ContextProductionCapture.Source> expected = new HashSet<>(); expected.add(x.primary.get(0).source);
            for (ContextProductionNativeBinding.Entry entry : b.primary) expected.add(entry.source);
            expected.add(a.primary.stream().filter(e -> e.statement.contains("left")).findFirst().get().source);
            require(forward.collisions.stream().anyMatch(c -> { Set<ContextProductionCapture.Source> sources = new HashSet<>(c.leftProof.sources); sources.addAll(c.rightProof.sources); return sources.equals(expected); }),"collision retains four exact causal sources");
            Snapshot splitA = snapshot(false,"!left(1);"),splitB = snapshot(false,"!right(1);");
            Evaluation split = evaluate(x,splitA,splitB); fixed(split,"split antecedents have separate Horn models and stable unit frontier");
            require(split.certificate.frontier.productions.isEmpty() && split.reason.contains("saturation unknown"),"unit certificate explicitly cannot claim residual-family saturation");
            User residualUser = new User(); Mind residualMind = new Mind(residualUser); residualUser.setCurrentMind(residualMind);
            residualMind.setQueryPass(org.kanger.enums.QueryPass.ACCEPT);
            try (ContextProductionCapture capture = ContextProductionCapture.begin(residualMind,100000)) {
                capture.observeResidualSubsets();
                for (Snapshot part : Arrays.asList(x,splitA)) for (ContextProductionNativeBinding.Entry entry : part.primary)
                    capture.replay(residualMind,entry.source,entry.statement);
                residualMind.link(null,false);
                ContextProductionCapture.Snapshot observed = capture.snapshot();
                require(observed.residuals.stream().anyMatch(r -> r.conclusion.literals.size() == 2
                        && r.conclusion.literals.stream().anyMatch(l -> l.predicate.equals("right") && !l.positive)
                        && r.conclusion.literals.stream().anyMatch(l -> l.predicate.equals("p") && l.positive)),"actual native match observes right(1)->p(1), absent from the unit frontier");
            }
            Snapshot independentX = snapshot(true,"!unrelated(1);"),positive = snapshot(false,"!p(1);"),negative = snapshot(false,"!~p(1);");
            Evaluation siblings = evaluate(independentX,positive,negative); fixed(siblings,"opposite raw siblings have separate valid models");
            require(siblings.rounds == 1 && siblings.certificate.frontier.productions.isEmpty(),"independent raw facts remain outside common pool");
            Snapshot cx = snapshot(true,"!@x a(x)->b(x);"),ca = snapshot(false,"!a(1);"),cb = snapshot(false,"!@x b(x)->c(x);");
            Evaluation chain = evaluate(cx,ca,cb),chainReverse = evaluate(cx,cb,ca);
            fixed(chain,"cross-cut positive chain reaches certified closure"); fixed(chainReverse,"reverse order reaches certified closure");
            require(chain.rounds == 3 && chainReverse.rounds == 3,"discovery rounds precede unchanged full local audit");
            require(chain.certificate.frontier.productions.stream().anyMatch(p -> p.conclusion.predicate.equals("c") && p.proof.depth == 2 && p.proof.sources.size() == 3),"certificate keeps cumulative cross-cut proof");
            Frontier initial = ContextGroundProductionRounds.start(cx,Arrays.asList(ca,cb),WORK); List<Result> initialResults = pass(initial);
            incomplete(ContextGroundHornFamily.certify(initial,initialResults,16,WORK),"local closure cannot certify a changed shared frontier");
            List<Result> missing = new ArrayList<>(initialResults); missing.remove(0);
            incomplete(ContextGroundHornFamily.certify(initial,missing,16,WORK),"missing self receipt cannot certify");
            Frontier stable = chain.certificate.frontier;
            incomplete(ContextGroundHornFamily.certify(stable,initialResults,16,WORK),"stale receipts cannot certify final frontier");
            fixed(ContextGroundHornFamily.certify(stable,pass(stable),16,WORK),"fresh receipts certify exact stable input");
            incomplete(ContextGroundHornFamily.evaluate(cx,Arrays.asList(ca,cb),1,16,WORK,100000),"round exhaustion cannot return a prefix certificate");
            incomplete(ContextGroundHornFamily.evaluate(cx,Arrays.asList(ca,cb),16,1,WORK,100000),"cumulative depth stop cannot certify");
            incomplete(ContextGroundHornFamily.evaluate(cx,Arrays.asList(ca,cb),16,16,1,100000),"work stop cannot certify");
            incomplete(ContextGroundHornFamily.evaluate(cx,Arrays.asList(ca,cb),16,16,WORK,1),"event stop cannot certify");
            Snapshot emptyX = snapshot(true,""); fixed(evaluate(emptyX),"genuinely empty X has an empty model");
            Snapshot dormant = snapshot(true,"!@x a(x)&&b(x)->c(x);"); fixed(evaluate(dormant),"rule-only universal X has a model over all admissible values");
            Snapshot nonHorn = snapshot(false,"!p(1)||q(1);");
            incomplete(evaluate(independentX,positive,nonHorn),"unsupported later non-Horn source refuses whole family");
            Snapshot unbound = snapshot(true,"!@x @y r(x,y)->p(x);"); incomplete(evaluate(unbound),"unbound reverse head is not covered by an empty known domain");
            Snapshot universalUnit = snapshot(true,"!@x p(x);"); incomplete(evaluate(universalUnit),"universal unit requires separate coverage");
            Snapshot localX = snapshot(true,"!@x b(x)->c(x);"),local = snapshot(false,"!a(1); !@x a(x)->b(x);");
            fixed(evaluate(localX,local),"foreign-only intermediate participates only in its cut model");
            Snapshot signX = snapshot(true,"!@x a(x)->~p(x);"),signA = snapshot(false,"!a(1);"),different = snapshot(false,"!p(2);");
            fixed(evaluate(signX,signA,different),"negative X-dependent products respect semantic argument identity");
            Snapshot constraint = snapshot(true,"!@x ~(a(x)&&b(x));");
            fixed(evaluate(constraint,ca),"satisfied pure-negative Horn constraint has a model");
            Snapshot cycle = snapshot(false,"!@x b(x)->a(x);"); fixed(evaluate(cx,ca,cycle),"cyclic whole-source alternatives retain finite minimum proof depths");
            // Independent finite-model oracle: signed integers denote literals p/q/r.
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
                require(observed.status != Status.UNIT_FIXED_POINT || satisfiable,"certificate agrees with exhaustive p/q/r truth table at "+i+"/"+j);
                require(observed.status != Status.COLLISION || !satisfiable,"refutation agrees with exhaustive p/q/r truth table at "+i+"/"+j);
                if (observed.status == Status.UNIT_FIXED_POINT) ++certified;
                if (observed.status == Status.COLLISION) ++refuted;
            }
            require(certified >= 32,"finite oracle exercises actual certificates, not only incomplete refusals");
            System.out.println("HORN_FINITE_MODEL_ORACLE cases=64 certified="+certified+" refuted="+refuted);
            boolean immutable = false; try { chain.certificate.frontier.productions.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; }
            require(immutable,"certificate owns an immutable common frontier");
            System.out.println("CONTEXT_GROUND_HORN_FAMILY_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
