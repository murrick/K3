/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionRoundsReference.*;

/** Synchronous reference controls; runtime publication gate remains unchanged. */
public final class ContextProductionRoundsReferenceRunner {
    private static int checks;
    private static final UUID X = UUID.randomUUID();
    private static final Limits NORMAL = new Limits(16, 10000, 100000, 64);
    private static List<Declaration> source(UUID id, boolean x, String... statements) {
        List<Declaration> result = new ArrayList<>();
        for (int i = 0; i < statements.length; ++i)
            result.add(new Declaration(new Source(id, 1, i, x, "base"), statements[i]));
        return result;
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home", java.nio.file.Files.createTempDirectory("production-round-reference-").toString());
            maskedAndUnmasked(); pureForeign(); alternateSupports(); delayedChainPermutations(); cyclesAndLimits(); compoundResiduals();
            System.out.println("CONTEXT_PRODUCTION_ROUNDS_REFERENCE_PASS checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void maskedAndUnmasked() throws Exception {
        List<Declaration> x = source(X, true, "!@x left(x) && right(x) -> p(x);");
        for (boolean masked : new boolean[] {false, true}) {
            List<Declaration> a = masked ? source(UUID.randomUUID(), false, "!left(1);", "!p(1);")
                    : source(UUID.randomUUID(), false, "!left(1);");
            List<Declaration> b = source(UUID.randomUUID(), false, "!right(1);", "!~p(1);");
            for (boolean reverse : new boolean[] {false, true}) {
                Result result = ContextProductionRoundsReference.inspect(x,
                        reverse ? Arrays.asList(b, a) : Arrays.asList(a, b), NORMAL);
                require(result.status == Status.COLLISION && result.rounds == 2,
                        "counterexample refused on second synchronous round: " + result.status + " " + result.reason);
                require(result.collision != null && !result.collision.getCollisions().isEmpty(), "native witnesses retained");
            }
        }
    }
    private static void pureForeign() throws Exception {
        List<Declaration> x = source(X, true, "!anchor(1);");
        List<Declaration> a = source(UUID.randomUUID(), false, "!a(1);", "!@x a(x) -> p(x);");
        List<Declaration> b = source(UUID.randomUUID(), false, "!~p(1);");
        for (boolean reverse : new boolean[] {false, true}) {
            Result result = ContextProductionRoundsReference.inspect(x, reverse ? Arrays.asList(b, a) : Arrays.asList(a, b), NORMAL);
            require(result.status == Status.SATURATED, "foreign-only disagreement is permitted");
            for (Product product : result.products) require(product.fact == null || !product.fact.predicate.equals("p"), "pure foreign p is not promoted to X");
        }
    }
    private static void alternateSupports() throws Exception {
        List<Declaration> x = source(X, true, "!@x a(x) -> p(x);", "!@x c(x) -> p(x);");
        List<Declaration> a = source(UUID.randomUUID(), false, "!a(1);", "!p(1);");
        List<Declaration> b = source(UUID.randomUUID(), false, "!c(1);");
        Result result = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, b), NORMAL);
        require(result.status == Status.SATURATED, "consistent alternative-support fixture saturates");
        Product p = product(result, "p");
        require(p != null && p.supports.size() == 2, "both X-dependent routes survive existing foreign p");
        for (Proof proof : p.supports) require(proof.usesX(), "transported supports use X");
        List<Declaration> negative = source(UUID.randomUUID(), false, "!~p(1);");
        result = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, negative), NORMAL);
        require(result.status == Status.COLLISION, "new X route is not masked by old p support");
    }
    private static void delayedChainPermutations() throws Exception {
        List<Declaration> x = source(X, true, "!seed(John);");
        List<Declaration> a = source(UUID.randomUUID(), false, "!@x seed(x) -> mid(x);");
        List<Declaration> b = source(UUID.randomUUID(), false, "!@x mid(x) -> tail(x);");
        List<Declaration> c = source(UUID.randomUUID(), false, "!~tail(John);");
        List<List<Declaration>> targets = Arrays.asList(a, b, c);
        for (int i = 0; i < 3; ++i) for (int j = 0; j < 3; ++j) {
            if (i == j) continue;
            int k = 3 - i - j;
            Result result = ContextProductionRoundsReference.inspect(x,
                    Arrays.asList(targets.get(i), targets.get(j), targets.get(k)), NORMAL);
            require(result.status == Status.COLLISION && result.rounds == 3, "all six orders keep the same third-round refusal");
        }
    }
    private static void cyclesAndLimits() throws Exception {
        List<Declaration> x = source(X, true, "!seed(1);");
        List<Declaration> a = source(UUID.randomUUID(), false, "!@x seed(x) -> mid(x);");
        List<Declaration> b = source(UUID.randomUUID(), false, "!@x mid(x) -> seed(x);");
        Result result = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, b), NORMAL);
        require(result.status == Status.SATURATED, "ground support cycle reaches source-set closure");
        result = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, b), new Limits(1, 10000, 100000, 64));
        require(result.status == Status.INCOMPLETE && result.reason.contains("Round limit"), "round stop cannot certify saturation");
        List<Declaration> tail = source(UUID.randomUUID(), false, "!@x mid(x) -> tail(x);");
        result = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, tail), new Limits(16, 10000, 100000, 1));
        require(result.status == Status.INCOMPLETE && result.reason.contains("depth limit"), "derivation depth survives round transport");
        result = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, b), new Limits(16, 10000, 1, 64));
        require(result.status == Status.INCOMPLETE, "proof work budget cannot certify saturation");
        result = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, b), new Limits(16, 1, 100000, 64));
        require(result.status == Status.INCOMPLETE, "capture budget cannot certify saturation");
        List<Declaration> number = source(X, true, "!num(1);");
        List<Declaration> recursive = source(UUID.randomUUID(), false, "!@x num(x) -> num(x+1);");
        result = ContextProductionRoundsReference.inspect(number, Collections.singletonList(recursive), new Limits(16, 10000, 100000, 4));
        require(result.status == Status.INCOMPLETE && result.reason.contains("Flood limit"), "native recursive flood stop remains explicit");
    }
    private static void compoundResiduals() throws Exception {
        List<Declaration> x = source(X, true, "!@x left(x) && right(x) -> p(x);");
        List<Declaration> a = source(UUID.randomUUID(), false, "!left(1);");
        List<Declaration> b = source(UUID.randomUUID(), false, "!right(1);");
        Result result = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, b), NORMAL);
        require(result.status == Status.SATURATED && product(result, "p") != null,
                "proved compound residuals carry the two-source join to saturation");
        Product joined = product(result, "p");
        for (Proof proof : joined.supports)
            require(proof.sources.contains(x.get(0).source) && proof.sources.contains(a.get(0).source)
                    && proof.sources.contains(b.get(0).source), "compound producer retains its whole joint support");
        Result partial = ContextProductionRoundsReference.inspect(x, Collections.singletonList(a), NORMAL);
        require(partial.status == Status.SATURATED && product(partial, "p") == null
                && product(partial, "right") == null, "a disjunction does not assert either literal separately");
        boolean compound = false;
        for (Product item : partial.products) compound |= item.clause.literals.size() == 2;
        require(compound, "proved ground residual is retained as a clause");
        List<Declaration> alias = source(UUID.randomUUID(), false, "!left(1);");
        Result alternatives = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, alias, b), NORMAL);
        Product alternativesP = product(alternatives, "p");
        require(alternatives.status == Status.SATURATED && alternativesP != null,
                "alternative compound producers saturate");
        boolean routeA = false, routeAlias = false;
        for (Proof proof : alternativesP.supports) {
            routeA |= proof.sources.contains(a.get(0).source) && !proof.sources.contains(alias.get(0).source);
            routeAlias |= proof.sources.contains(alias.get(0).source) && !proof.sources.contains(a.get(0).source);
            require(proof.sources.contains(x.get(0).source) && proof.sources.contains(b.get(0).source),
                    "alternative clauses never split the producer proof into source aliases");
        }
        require(routeA && routeAlias, "both independent compound support alternatives survive");
        Result foreign = ContextProductionRoundsReference.inspect(source(X, true, "!anchor(1);"),
                Arrays.asList(source(UUID.randomUUID(), false, "!@x left(x) && right(x) -> p(x);", "!left(1);"), b), NORMAL);
        require(foreign.status == Status.SATURATED && product(foreign, "p") == null,
                "foreign-only compound residual is not promoted into X");
        List<Declaration> typedX = source(X, true, "!@x @y left(x,y) && right(y,x) -> p(x,y);");
        Result typed = ContextProductionRoundsReference.inspect(typedX, Arrays.asList(
                source(UUID.randomUUID(), false, "!left(John,2);"),
                source(UUID.randomUUID(), false, "!right(2,John);")), NORMAL);
        Product typedP = product(typed, "p");
        require(typed.status == Status.SATURATED && typedP != null && typedP.fact.arguments.size() == 2,
                "named and numeric arguments survive positional compound transport");
        Result mismatch = ContextProductionRoundsReference.inspect(typedX, Arrays.asList(
                source(UUID.randomUUID(), false, "!left(John,2);"),
                source(UUID.randomUUID(), false, "!right(John,2);")), NORMAL);
        require(mismatch.status == Status.SATURATED && product(mismatch, "p") == null,
                "compound transport preserves argument order rather than matching rendered text");
        Result unbound = ContextProductionRoundsReference.inspect(
                source(X, true, "!@x @y left(x) && right(y) -> p(x,y);"), Collections.singletonList(a), NORMAL);
        require(unbound.status == Status.INCOMPLETE && unbound.reason.contains("compound-residual"),
                "unbound compound residual cannot certify saturation: " + unbound.status + " " + unbound.reason);
        Result shallow = ContextProductionRoundsReference.inspect(x, Arrays.asList(a, b), new Limits(16, 10000, 100000, 1));
        require(shallow.status == Status.INCOMPLETE && shallow.reason.contains("depth limit"),
                "compound replay preserves derivation depth");
        List<Declaration> negative = source(UUID.randomUUID(), false, "!~p(1);");
        List<List<Declaration>> targets = Arrays.asList(a, b, negative);
        for (int i = 0; i < 3; ++i) for (int j = 0; j < 3; ++j) {
            if (i == j) continue;
            Result conflict = ContextProductionRoundsReference.inspect(x,
                    Arrays.asList(targets.get(i), targets.get(j), targets.get(3-i-j)), NORMAL);
            require(conflict.status == Status.COLLISION && conflict.rounds == 3,
                    "compound three-source conflict is refused in all six orders");
        }
        result = ContextProductionRoundsReference.inspect(source(X, true, "!@x p(x);"),
                Collections.singletonList(source(UUID.randomUUID(), false, "!@x p(x) -> q(x);")), NORMAL);
        require(result.status == Status.INCOMPLETE && result.reason.contains("nonordinary-primary-fact"),
                "abstract source input is an explicit coverage stop");
    }
    private static Product product(Result result, String predicate) {
        for (Product product : result.products) if (product.fact != null && product.fact.predicate.equals(predicate)) return product;
        return null;
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
