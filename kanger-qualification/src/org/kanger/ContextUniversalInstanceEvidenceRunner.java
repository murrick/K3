/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextLinearProofVerifier.Claim;
import org.kanger.ContextProductionRoundsReference.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.units.Rule;

public final class ContextUniversalInstanceEvidenceRunner {
    private static int checks;
    private static final Source X = new Source(new UUID(0,1),1,0,true,"base");
    private static final Source A = new Source(new UUID(0,2),1,0,false,"base");
    private static Clause clause(String text) throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        Rule rule = (Rule)mind.compileLine(text,false,new LinkedList<org.kanger.interfaces.ITerm>());
        return Clause.shapeOf(rule.getTree().get(0),mind);
    }
    private static Claim claim(Clause c,int depth,Source... sources) {
        return new Claim(c,Collections.singletonList(new Support(new LinkedHashSet<>(Arrays.asList(sources)),depth)));
    }
    private static Runnable budget() {
        int[] left = {1000000}; return () -> { if (--left[0] < 0) throw new IllegalStateException("work limit"); };
    }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-evidence-").toString());
            List<SemanticTermSnapshot> basis = clause("!seed(1,John);").literals.get(0).fact().arguments;
            Claim schema = claim(clause("!@x @y p(x,y) -> q(y,x);"),3,X,A);
            List<Claim> instances = ContextUniversalInstanceEvidence.expand(Collections.singletonList(schema),basis,budget());
            require(instances.size() == 4,"independent variables have four assignments");
            for (String a : new String[]{"1","John"}) for (String b : new String[]{"1","John"}) {
                Claim found = ContextLinearProofVerifier.find(instances,clause("!p("+a+","+b+") -> q("+b+","+a+");"),budget());
                require(found != null && found.supports.size() == 1 && found.supports.get(0).depth == 3
                        && found.supports.get(0).sources.equals(schema.supports.get(0).sources),"substitution retains permutation, whole sources and depth");
            }
            List<Claim> diagonal = ContextUniversalInstanceEvidence.expand(Collections.singletonList(claim(clause("!@x p(x,x);"),0,A)),basis,budget());
            require(diagonal.size() == 2 && ContextLinearProofVerifier.find(diagonal,clause("!p(1,John);"),budget()) == null,
                    "repeated variable cannot become independent");
            Claim universal = claim(clause("!@x p(x);"),2,X,A), ground = claim(clause("!p(1);"),0,A);
            List<Claim> merged = ContextUniversalInstanceEvidence.expand(Arrays.asList(universal,ground),basis,budget());
            Claim p1 = ContextLinearProofVerifier.find(merged,clause("!p(1);"),budget());
            require(merged.size() == 2 && p1.supports.size() == 2,"overlapping instances retain source alternatives");
            List<Claim> minimized = ContextUniversalInstanceEvidence.expand(Arrays.asList(universal,claim(ground.clause,1,X,A)),basis,budget());
            require(ContextLinearProofVerifier.find(minimized,ground.clause,budget()).supports.get(0).depth == 1,
                    "only same-source depth may be minimized");
            boolean stopped = false;
            try { ContextUniversalInstanceEvidence.expand(Collections.singletonList(schema),basis,() -> { throw new IllegalStateException("stop"); }); }
            catch (IllegalStateException expected) { stopped = true; }
            require(stopped,"resource stop cannot return partial evidence");
            stopped = false;
            try { ContextUniversalInstanceEvidence.expand(Collections.singletonList(new Claim(schema.clause,Collections.emptyList())),basis,budget()); }
            catch (IllegalArgumentException expected) { stopped = true; }
            require(stopped,"shape metadata without proof cannot instantiate");
            for (String excluded : new String[]{"!@x @y @z p(x,y,z);", "!a(1) && b(1) && c(1) -> d(1);"}) {
                stopped = false;
                try { ContextUniversalInstanceEvidence.expand(Collections.singletonList(claim(clause(excluded),0,X)),basis,budget()); }
                catch (IllegalArgumentException expected) { stopped = true; }
                require(stopped,"bounded scope cannot silently widen");
            }
            stopped = false;
            try { ContextUniversalInstanceEvidence.expand(Collections.singletonList(schema),Collections.emptyList(),budget()); }
            catch (IllegalArgumentException expected) { stopped = true; }
            require(stopped,"empty representative basis cannot erase universal evidence");
            stopped = false;
            try { ContextUniversalInstanceEvidence.expand(Arrays.asList(universal,universal),basis,budget()); }
            catch (IllegalArgumentException expected) { stopped = true; }
            require(stopped,"duplicate input proof buckets remain invalid");
            List<Claim> conflict = ContextUniversalInstanceEvidence.expand(Arrays.asList(
                    claim(clause("!@x p(x);"),0,A),claim(clause("!~p(John);"),0,X)),basis,budget());
            require("horn-unreported-collision".equals(ContextHornResidualVerifier.verify(Collections.emptyList(),conflict,basis,budget())),
                    "universal and ground opposing units cannot hide a collision");
            nativeResidual(basis);
            System.out.println("CONTEXT_UNIVERSAL_INSTANCE_EVIDENCE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void nativeResidual(List<SemanticTermSnapshot> basis) throws Exception {
        Result result = ContextProductionRoundsReference.inspectWithParameters(
                Collections.singletonList(new Declaration(X,"!@x @y left(x) && right(y) -> out(x,y);")),
                Collections.singletonList(Collections.singletonList(new Declaration(A,"!left(1);"))),
                new Limits(16,50000,3000000,64));
        Clause expected = clause("!@y right(y) -> out(1,y);");
        Product residual = null;
        for (Product product : result.products) if (product.clause.equivalent(expected)) residual = product;
        require(residual != null,"native capture exports the proved universal residual");
        require(result.products.stream().noneMatch(p -> p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("out")),
                "conditional residual does not assert its head");
        List<Support> supports = new ArrayList<>();
        for (Proof proof : residual.supports) supports.add(new Support(proof.sources,proof.depth));
        List<Claim> evidence = ContextUniversalInstanceEvidence.expand(Collections.singletonList(new Claim(residual.clause,supports)),basis,budget());
        for (String value : new String[]{"1","John"}) {
            Clause wanted = clause("!right("+value+") -> out(1,"+value+");");
            require(ContextLinearProofVerifier.hasProof(evidence,wanted,new LinkedHashSet<>(Arrays.asList(X,A)),1,budget()),
                    "actual native residual proves each representative instance without extra depth");
        }
        Claim producer = claim(clause("!@x @y left(x) && right(y) -> out(x,y);"),0,X);
        List<Claim> raw = Arrays.asList(new Claim(residual.clause,supports),claim(clause("!left(1);"),0,A));
        require("horn-residual-proof-missing".equals(ContextHornResidualVerifier.verify(Collections.singletonList(producer),raw,basis,budget())),
                "exact-ground checker alone cannot use a symbolic proof");
        List<Claim> pool = new ArrayList<>(evidence); pool.add(claim(clause("!left(1);"),0,A));
        require(ContextHornResidualVerifier.verify(Collections.singletonList(producer),pool,basis,budget()) == null,
                "native universal residual discharges finite Horn residual obligations");
        require(ContextHornResidualVerifier.verify(Collections.singletonList(producer),Collections.singletonList(pool.get(pool.size()-1)),basis,budget()) != null,
                "missing native residual cannot be supplied by the bridge");
    }
}
