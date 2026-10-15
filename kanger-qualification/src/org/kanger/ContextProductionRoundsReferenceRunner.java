/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionRoundsReference.*;
import org.kanger.ContextProductionInputs.Declaration;

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
            maskedAndUnmasked(); pureForeign(); alternateSupports(); delayedChainPermutations(); cyclesAndLimits(); compoundResiduals(); multiClauseSources(); quantifiedResiduals(); universalUnits(); monadicCoverageOracle();
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
        require(typed.status == Status.INCOMPLETE && typedP != null && typedP.fact.arguments.size() == 2,
                "named and numeric arguments survive positional compound transport");
        Result mismatch = ContextProductionRoundsReference.inspect(typedX, Arrays.asList(
                source(UUID.randomUUID(), false, "!left(John,2);"),
                source(UUID.randomUUID(), false, "!right(John,2);")), NORMAL);
        require(mismatch.status == Status.INCOMPLETE && product(mismatch, "p") == null,
                "compound transport preserves argument order rather than matching rendered text");
        Result unbound = ContextProductionRoundsReference.inspect(
                source(X, true, "!@x @y left(x) && right(y) -> p(x,y);"), Collections.singletonList(a), NORMAL);
        require(unbound.status == Status.INCOMPLETE && product(unbound, "p") == null,
                "quantified compound residual remains a clause without asserting its head: " + unbound.status + " " + unbound.reason);
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
        result = ContextProductionRoundsReference.inspect(source(X, true, "!$x p(x);"),
                Collections.singletonList(source(UUID.randomUUID(), false, "!@x p(x) -> q(x);")), NORMAL);
        require(result.status == Status.INCOMPLETE && result.reason.contains("nonordinary-primary-fact"),
                "abstract source input is an explicit coverage stop");
    }
    private static void multiClauseSources() throws Exception {
        List<Declaration> x = source(X, true, "!seed(John) && anchor(2);");
        List<Declaration> a = source(UUID.randomUUID(), false, "!@x seed(x) -> (mid(x) && tail(x));");
        List<Declaration> b = source(UUID.randomUUID(), false, "!@x mid(x) && tail(x) -> end(x);");
        for (boolean reverse : new boolean[] {false, true}) {
            Result result = ContextProductionRoundsReference.inspect(x, reverse ? Arrays.asList(b, a) : Arrays.asList(a, b), NORMAL);
            require(result.status == Status.SATURATED && product(result, "end") != null,
                    "conjoined primary sources and multi-head rules saturate: " + result.status + " " + result.reason);
            Product end = product(result, "end");
            for (Proof proof : end.supports)
                require(proof.sources.contains(x.get(0).source) && proof.sources.contains(a.get(0).source)
                        && proof.sources.contains(b.get(0).source), "each compiled branch retains the original source occurrence");
        }
        Result mixed = ContextProductionRoundsReference.inspect(
                source(X, true, "!(seed(1) || other(1)) && anchor(2);"),
                Arrays.asList(source(UUID.randomUUID(), false, "!~other(1) && (seed(1) -> mid(1));"),
                        source(UUID.randomUUID(), false, "!~mid(1);")), NORMAL);
        require(mixed.status == Status.COLLISION && mixed.rounds == 2,
                "multi-clause ground disjunctions and negative siblings retain their native resolution: "
                        + mixed.status + " round=" + mixed.rounds + " reason=" + mixed.reason);
        Result internalChain = ContextProductionRoundsReference.inspect(source(X, true, "!anchor(1);"),
                Collections.singletonList(source(UUID.randomUUID(), false,
                        "!seed(1) && (seed(1) -> mid(1)) && (mid(1) && anchor(1) -> p(1));")), NORMAL);
        require(internalChain.status == Status.SATURATED && product(internalChain, "p") != null,
                "foreign internal multi-clause chain contributes its X-dependent terminal product: "
                        + internalChain.status + " " + internalChain.reason);
        require(product(internalChain, "seed") == null && product(internalChain, "mid") == null,
                "pure foreign intermediates are retained privately, never exported to X");
        for (Proof proof : product(internalChain, "p").supports)
            require(proof.usesX() && proof.sources.size() == 2,
                    "terminal proof preserves the X fact and the whole foreign source occurrence");
        Result boundedLocal = ContextProductionRoundsReference.inspect(source(X, true, "!anchor(1);"),
                Collections.singletonList(source(UUID.randomUUID(), false,
                        "!seed(1) && (seed(1) -> mid(1)) && (mid(1) && anchor(1) -> p(1));")),
                new Limits(16, internalChain.observations.get(0).eventCount + 1, 100000, 64));
        require(boundedLocal.status == Status.INCOMPLETE && boundedLocal.rounds == 1,
                "observation budget spans private participant rebuilds rather than resetting at each replay");
        Result foreign = ContextProductionRoundsReference.inspect(source(X, true, "!anchor(1);"), Arrays.asList(
                source(UUID.randomUUID(), false, "!p(1) && q(2);"),
                source(UUID.randomUUID(), false, "!~p(1) && ~q(2);")), NORMAL);
        require(foreign.status == Status.SATURATED && product(foreign, "p") == null && product(foreign, "q") == null,
                "multi-clause sibling disagreement stays external");
        Result mixedUnsupported = ContextProductionRoundsReference.inspect(
                source(X, true, "!$x known(1) && abstract(x);"), Collections.singletonList(a), NORMAL);
        require(mixedUnsupported.status == Status.INCOMPLETE && mixedUnsupported.reason.contains("nonordinary-primary-fact"),
                "a supported source branch cannot hide an unsupported abstract unit");
        List<Declaration> c = source(UUID.randomUUID(), false, "!~end(John) && other(3);");
        List<List<Declaration>> targets = Arrays.asList(a, b, c);
        for (int i = 0; i < 3; ++i) for (int j = 0; j < 3; ++j) {
            if (i == j) continue;
            Result result = ContextProductionRoundsReference.inspect(x,
                    Arrays.asList(targets.get(i), targets.get(j), targets.get(3-i-j)), NORMAL);
            require(result.status == Status.COLLISION && result.rounds == 3,
                    "multi-clause source conflict is refused in all six orders");
        }
    }
    private static void quantifiedResiduals() throws Exception {
        List<Declaration> x = source(X, true, "!@x @y left(x) && right(y) -> p(x,y);");
        List<Declaration> a = source(UUID.randomUUID(), false, "!left(1);");
        List<Declaration> b = source(UUID.randomUUID(), false, "!right(2);");
        for (boolean reverse : new boolean[] {false, true}) {
            Result result = ContextProductionRoundsReference.inspect(x, reverse ? Arrays.asList(b,a) : Arrays.asList(a,b), NORMAL);
            Product p = product(result, "p");
            require(result.status == Status.INCOMPLETE && p != null,
                    "remaining quantified variable carries the independent-argument join: " + result.status + " " + result.reason);
            for (Proof proof : p.supports)
                require(proof.sources.contains(x.get(0).source) && proof.sources.contains(a.get(0).source)
                        && proof.sources.contains(b.get(0).source), "quantified join retains its whole source group");
        }
        Result multiple = ContextProductionRoundsReference.inspect(
                source(X,true,"!@x @y @z left(x) && right(y,z) -> p(x,y,z);"), Arrays.asList(
                        source(UUID.randomUUID(),false,"!left(John);"),
                        source(UUID.randomUUID(),false,"!right(2,3);")),NORMAL);
        Product triple = product(multiple,"p");
        require(multiple.status == Status.INCOMPLETE && triple != null && triple.fact.arguments.size() == 3,
                "two remaining variables and a typed constant survive the same clause scope");
        require(proves(triple,"?p(John,2,3);") && !proves(triple,"?p(John,3,2);"),
                "separate remaining variables retain their positional binding");
        Result repeatedMismatch = ContextProductionRoundsReference.inspect(
                source(X,true,"!@x @y left(x) && right(y,y) -> p(x,y);"), Arrays.asList(a,
                        source(UUID.randomUUID(),false,"!right(2,3);")),NORMAL);
        require(repeatedMismatch.status == Status.INCOMPLETE && product(repeatedMismatch,"p") == null,
                "one repeated variable cannot match distinct donor arguments after transport");
        Result repeatedMatch = ContextProductionRoundsReference.inspect(
                source(X,true,"!@x @y left(x) && right(y,y) -> p(x,y);"), Arrays.asList(a,
                        source(UUID.randomUUID(),false,"!right(2,2);")),NORMAL);
        require(repeatedMatch.status == Status.INCOMPLETE && product(repeatedMatch,"p") != null,
                "equal repeated donor arguments retain the native quantified proof");
        Result signed = ContextProductionRoundsReference.inspect(
                source(X,true,"!@x @y left(x) && right(y) -> ~p(x,y);"),Arrays.asList(a,b),NORMAL);
        Product negative = product(signed,"p");
        require(signed.status == Status.INCOMPLETE && negative != null && !negative.fact.positive,
                "negative conclusion polarity survives a quantified residual");
        Result signedConflict = ContextProductionRoundsReference.inspect(
                source(X,true,"!@x @y left(x) && right(y) -> ~p(x,y);"),
                Arrays.asList(a,b,source(UUID.randomUUID(),false,"!p(1,2);")),NORMAL);
        require(signedConflict.status == Status.COLLISION && signedConflict.rounds == 3,
                "opposite-polarity quantified conflict is refused");
        Result masked = ContextProductionRoundsReference.inspect(x, Arrays.asList(
                source(UUID.randomUUID(),false,"!left(1);", "!p(1,2);"),b),NORMAL);
        require(masked.status == Status.INCOMPLETE && product(masked,"p") != null,
                "a new X-dependent quantified proof survives an existing foreign conclusion");
        for (Proof proof : product(masked,"p").supports)
            require(proof.usesX() && proof.sources.contains(b.get(0).source),
                    "stored conclusion suppression cannot erase the independent quantified route");
        List<Declaration> alias = source(UUID.randomUUID(), false, "!left(1);");
        Result alternatives = ContextProductionRoundsReference.inspect(x, Arrays.asList(a,alias,b), NORMAL);
        Product joint = product(alternatives, "p");
        require(alternatives.status == Status.INCOMPLETE && joint != null, "quantified alternatives survive without claiming complete symbolic coverage");
        boolean routeA = false, routeAlias = false;
        for (Proof proof : joint.supports) {
            require(proof.sources.contains(x.get(0).source) && proof.sources.contains(b.get(0).source),
                    "quantified support alternatives retain the whole producer group");
            routeA |= proof.sources.contains(a.get(0).source) && !proof.sources.contains(alias.get(0).source);
            routeAlias |= proof.sources.contains(alias.get(0).source) && !proof.sources.contains(a.get(0).source);
        }
        require(routeA && routeAlias, "independent quantified producer alternatives survive");
        Result shallow = ContextProductionRoundsReference.inspect(x, Arrays.asList(a,b), new Limits(16,10000,100000,1));
        require(shallow.status == Status.INCOMPLETE && shallow.reason.contains("depth limit"),
                "quantified templates carry derivation depth into the bound application");
        Result isolated = ContextProductionRoundsReference.inspect(source(X,true,"!anchor(1);"), Arrays.asList(
                source(UUID.randomUUID(),false,"!@x @y left(x) && right(y) -> p(x,y);", "!left(1);"),b), NORMAL);
        require(isolated.status == Status.INCOMPLETE && product(isolated,"p") == null,
                "pure foreign quantified residuals do not cross the outer barrier");
        Result unsupportedUnit = ContextProductionRoundsReference.inspect(
                source(X,true,"!@x @y left(x) -> p(x,y);"), Collections.singletonList(a),NORMAL);
        require(unsupportedUnit.status == Status.INCOMPLETE && product(unsupportedUnit,"p") == null, "universal unit residual stays a schema rather than inventing a ground fact");
        Result existential = ContextProductionRoundsReference.inspect(
                source(X,true,"!@x $y left(x) && right(y) -> p(x,y);"), Collections.singletonList(a),NORMAL);
        require(existential.status == Status.INCOMPLETE, "existential witness identity is not replaced by an ordinary universal slot");
        Result untouchedWitness = ContextProductionRoundsReference.inspect(
                source(X,true,"!$z (a(z) || b(z));"),
                Collections.singletonList(source(UUID.randomUUID(),false,"!anchor(1);")),NORMAL);
        require(untouchedWitness.status == Status.INCOMPLETE && untouchedWitness.reason.contains("cvariable-input"),
                "lack of a ground existential application cannot masquerade as complete observation: "
                        + untouchedWitness.status + " " + untouchedWitness.reason);
        List<List<Declaration>> targets = Arrays.asList(a,b,source(UUID.randomUUID(), false, "!~p(1,2);"));
        for (int i=0; i<3; ++i) for (int j=0; j<3; ++j) if (i!=j) {
            Result result = ContextProductionRoundsReference.inspect(x,
                    Arrays.asList(targets.get(i),targets.get(j),targets.get(3-i-j)),NORMAL);
            require(result.status == Status.COLLISION && result.rounds == 3,
                    "all six orders refuse a conflict through a quantified residual: " + result.status + " " + result.reason);
        }
    }
    private static void universalUnits() throws Exception {
        List<Declaration> x = source(X,true,"!@x p(x);");
        List<Declaration> a = source(UUID.randomUUID(),false,"!p(1) -> q(1);");
        Result result = ContextProductionRoundsReference.inspect(x,Collections.singletonList(a),NORMAL);
        require(result.status == Status.SATURATED && product(result,"q") != null && proves(product(result,"q"),"?q(1);"),
                "a native universal unit instance supports its ground consequence: " + result.status + " " + result.reason);
        List<Declaration> negative = source(UUID.randomUUID(),false,"!~q(1);");
        for (boolean reverse : new boolean[]{false,true}) {
            Result conflict = ContextProductionRoundsReference.inspect(x,reverse ? Arrays.asList(negative,a) : Arrays.asList(a,negative),NORMAL);
            require(conflict.status == Status.COLLISION && conflict.rounds == 2,
                    "a universal unit ground consequence is qualified in both target orders");
        }
        List<Declaration> generic = source(UUID.randomUUID(),false,"!@y p(y) -> q(y);");
        Result deferred = ContextProductionRoundsReference.inspect(x,Arrays.asList(generic,negative),NORMAL);
        require(deferred.status == Status.COLLISION && deferred.rounds == 2,
                "a proved generic consequence is qualified on the next round: "
                        + deferred.status + " " + deferred.reason);
        Result reverseGeneric = ContextProductionRoundsReference.inspect(x,Arrays.asList(negative,generic),NORMAL);
        require(reverseGeneric.status == Status.COLLISION && reverseGeneric.rounds == 2,
                "generic consequence collision does not depend on target order");
        Result genericOnly = ContextProductionRoundsReference.inspectWithParameters(x,Collections.singletonList(generic),NORMAL);
        Product template = null;
        for (Product candidate : genericOnly.products)
            if (candidate.clause.literals.size() == 1 && candidate.clause.literals.get(0).predicate.equals("q")) template = candidate;
        require(genericOnly.status == Status.SATURATED && genericOnly.reason.contains("monadic-uniform")
                        && template != null && template.fact == null
                        && template.clause.variables.size() == 1,
                "the uniform monadic fragment certifies representative closure and exports a universal template");
        require(template.supports.stream().anyMatch(proof -> proof.sources.size() == 2 && proof.usesX() && proof.depth == 1),
                "generic consequence retains whole X/A support and one rule application");
        User templateUser = new User(); Mind templateMind = new Mind(templateUser); templateUser.setCurrentMind(templateMind);
        templateMind.compileLine(template.clause.assertion(),false,template.clause.project(templateMind));
        require(Boolean.TRUE.equals(templateMind.query("?q(73);",null,false))
                        && Boolean.TRUE.equals(templateMind.query("?q(John);",null,false)),
                "generalized native consequence covers unrelated numeric and named instances");
        List<List<Declaration>> chain = Arrays.asList(generic,
                source(UUID.randomUUID(),false,"!@z q(z) -> r(z);"),
                source(UUID.randomUUID(),false,"!~r(1);"));
        for (int i=0; i<3; ++i) for (int j=0; j<3; ++j) if (i!=j) {
            Result chained = ContextProductionRoundsReference.inspectWithParameters(x,
                    Arrays.asList(chain.get(i),chain.get(j),chain.get(3-i-j)),NORMAL);
            require(chained.status == Status.COLLISION && chained.rounds == 3,
                    "two generic rule applications collide in all six target orders: " + chained.status + " " + chained.reason);
        }
        Result diagonal = ContextProductionRoundsReference.inspectWithParameters(source(X,true,"!@x pair(x,x);"),
                Collections.singletonList(source(UUID.randomUUID(),false,"!@u @v pair(u,v) -> q(u,v);")),NORMAL);
        Product diagonalTemplate = null;
        for (Product candidate : diagonal.products)
            if (candidate.clause.literals.size() == 1 && candidate.clause.literals.get(0).predicate.equals("q")) diagonalTemplate = candidate;
        require(diagonalTemplate != null && diagonalTemplate.fact == null && diagonalTemplate.clause.variables.size() == 1,
                "generalization preserves repeated argument identity instead of asserting independent variables");
        Result independent = ContextProductionRoundsReference.inspectWithParameters(source(X,true,"!@x @y pair(x,y);"),
                Collections.singletonList(source(UUID.randomUUID(),false,"!@u @v pair(u,v) -> q(u,v);")),NORMAL);
        require(independent.products.stream().anyMatch(candidate -> candidate.clause.literals.size() == 1
                        && candidate.clause.literals.get(0).predicate.equals("q") && candidate.clause.variables.size() == 2),
                "distinct fresh parameters preserve independent universally quantified arguments");
        User diagonalUser = new User(); Mind diagonalMind = new Mind(diagonalUser); diagonalUser.setCurrentMind(diagonalMind);
        diagonalMind.compileLine(diagonalTemplate.clause.assertion(),false,diagonalTemplate.clause.project(diagonalMind));
        require(Boolean.TRUE.equals(diagonalMind.query("?q(73,73);",null,false))
                        && diagonalMind.query("?q(73,74);",null,false) == null,
                "diagonal generic proof does not certify a mismatched ordinary instance");
        Result foreignGeneric = ContextProductionRoundsReference.inspectWithParameters(source(X,true,"!anchor(1);"),
                Collections.singletonList(source(UUID.randomUUID(),false,"!@x p(x);","!@y p(y) -> q(y);")),NORMAL);
        require(foreignGeneric.products.stream().noneMatch(candidate -> candidate.clause.literals.stream().anyMatch(atom -> atom.predicate.equals("q"))),
                "generalized foreign-only proofs do not enter X");
        Result rankStop = ContextProductionRoundsReference.inspectWithParameters(source(X,true,"!@a @b @c @d p(a,b,c,d);"),
                Collections.singletonList(source(UUID.randomUUID(),false,"!@a @b @c @d p(a,b,c,d) -> q(a,b,c,d);")),NORMAL);
        require(rankStop.status == Status.INCOMPLETE && rankStop.reason.contains("Parameter rank limit"),
                "a parameter experiment exceeding its bounded rank cannot certify saturation");
        Result specific = ContextProductionRoundsReference.inspectWithParameters(
                source(X,true,"!@x r(x);","!p(1);"),
                Arrays.asList(source(UUID.randomUUID(),false,"!@x r(x) -> s(x);","!@x p(x) -> q(x);"),
                        source(UUID.randomUUID(),false,"!~q(2);")),NORMAL);
        require(specific.status == Status.SATURATED && specific.reason.contains("monadic-uniform") && product(specific,"q") != null
                        && specific.products.stream().noneMatch(candidate -> !candidate.clause.variables.isEmpty()
                        && candidate.clause.literals.size() == 1 && candidate.clause.literals.get(0).predicate.equals("q")),
                "an unrelated generic proof cannot generalize a consequence supported only at p(1)");
        require(diagonal.status == Status.INCOMPLETE && !diagonal.reason.contains("monadic-uniform"),
                "a binary rule cannot inherit the monadic coverage certificate");
        Result missing = ContextProductionRoundsReference.inspectWithParameters(x,
                Collections.singletonList(source(UUID.randomUUID(),false,"!@y p(y) && missing(y) -> q(y);")),NORMAL);
        require(missing.products.stream().noneMatch(candidate -> candidate.clause.literals.size() == 1
                        && candidate.clause.literals.get(0).predicate.equals("q")),
                "a missing parameter premise remains conditional and never becomes an unconditional universal fact");
        Result aliases = ContextProductionRoundsReference.inspectWithParameters(source(X,true,"!@x p(x);","!@y p(y);"),
                Collections.singletonList(generic),NORMAL);
        Product aliasTemplate = null;
        for (Product candidate : aliases.products) if (candidate.clause.literals.size() == 1
                && candidate.clause.literals.get(0).predicate.equals("q") && !candidate.clause.variables.isEmpty()) aliasTemplate = candidate;
        require(aliasTemplate != null && aliasTemplate.supports.stream().filter(proof -> proof.sources.size() == 2
                        && proof.depth == 1 && proof.usesX()).count() == 2,
                "duplicate universal source occurrences retain separate whole alternative proofs");
        User user = new User(); Mind monolithic = new Mind(user); user.setCurrentMind(monolithic);
        require(!monolithic.compile("!@x p(x); !@y p(y) -> q(y); !~q(1);",null,false),
                "native monolithic control proves the deferred generic-composition conflict");
        Result foreign = ContextProductionRoundsReference.inspect(source(X,true,"!anchor(1);"),
                Collections.singletonList(source(UUID.randomUUID(),false,"!@x p(x);", "!p(1) -> q(1);")),NORMAL);
        require(foreign.status == Status.SATURATED && product(foreign,"q") == null,
                "a foreign universal unit and its foreign consequence remain private");
        Result negativeUnit = ContextProductionRoundsReference.inspect(source(X,true,"!@x ~p(x);"),
                Collections.singletonList(source(UUID.randomUUID(),false,"!~p(1) -> q(1);")),NORMAL);
        require(negativeUnit.status == Status.INCOMPLETE && product(negativeUnit,"q") != null,
                "universal unit instantiation preserves negative polarity");
        Result residualUnit = ContextProductionRoundsReference.inspect(source(X,true,"!@x @y left(x) -> p(x,y);"),
                Arrays.asList(source(UUID.randomUUID(),false,"!left(1);"),
                        source(UUID.randomUUID(),false,"!p(1,2) -> q(1);"),negative),NORMAL);
        require(residualUnit.status == Status.COLLISION,
                "a transported universal unit residual supplies an ordinary ground instance");
    }
    /** Independent finite truth-closure oracle, confined to controls. */
    private static void monadicCoverageOracle() throws Exception {
        String[] excluded = {"!@x (p(x)||q(x));", "!@x (~p(x)||q(x));",
                "!@x (p(x)||~q(x));", "!@x (~p(x)||~q(x));"};
        Result uncovered = ContextProductionRoundsReference.inspect(source(X,true,excluded[0]),
                Collections.singletonList(source(UUID.randomUUID(),false,excluded[1],excluded[2],excluded[3])),NORMAL);
        require(uncovered.status == Status.INCOMPLETE && uncovered.coverage == Coverage.NONE
                        && uncovered.reason.contains("unproved-symbolic-coverage"),
                "absence of waiter gaps cannot certify an unsupported inconsistent universal clause family");
        for (boolean p : new boolean[]{false,true}) for (boolean q : new boolean[]{false,true})
            require(!((p||q) && (!p||q) && (p||!q) && (!p||!q)),
                    "independent truth table excludes every valuation of the coverage counterexample");
        for (String assumption : new String[]{"!p(1);","!~p(1);"}) {
            User user = new User(); Mind nativeMind = new Mind(user); user.setCurrentMind(nativeMind);
            for (String rule : excluded) require(nativeMind.compile(rule,null,false),"native control accepts the ungrounded source occurrence");
            require(!nativeMind.compile(assumption,null,false),"native grounding rejects each possible polarity of p(1)");
        }
        List<Declaration> universal = source(X,true,"!@x p(x);");
        List<Declaration> first = source(UUID.randomUUID(),false,"!@x p(x) -> q(x);");
        List<Declaration> second = source(UUID.randomUUID(),false,"!@x q(x) -> r(x);");
        for (boolean reverse : new boolean[]{false,true}) {
            Result chain = ContextProductionRoundsReference.inspect(universal,
                    reverse ? Arrays.asList(second,first) : Arrays.asList(first,second),NORMAL);
            require(chain.status == Status.SATURATED && chain.reason.contains("monadic-uniform"),
                    "consistent generic chain has order-independent representative saturation");
            require(chain.products.stream().filter(candidate -> candidate.clause.variables.size() == 1
                    && candidate.clause.literals.size() == 1 && candidate.clause.literals.get(0).predicate.equals("r"))
                    .anyMatch(candidate -> candidate.supports.stream().anyMatch(proof -> proof.sources.size() == 3 && proof.depth == 2)),
                    "certified generic chain retains whole X/A/B support and carried depth2");
        }
        Result negative = ContextProductionRoundsReference.inspect(source(X,true,"!@x ~q(x);"),Collections.singletonList(first),NORMAL);
        require(negative.status == Status.SATURATED && negative.products.stream().anyMatch(candidate -> candidate.clause.variables.size() == 1
                && candidate.clause.literals.size() == 1 && candidate.clause.literals.get(0).predicate.equals("p")
                && !candidate.clause.literals.get(0).positive),"negative universal units propagate through the same complete representatives");
        Result fixedHead = ContextProductionRoundsReference.inspect(universal,
                Collections.singletonList(source(UUID.randomUUID(),false,"!@x p(x) -> q(1);")),NORMAL);
        require(fixedHead.status == Status.SATURATED && fixedHead.coverage == Coverage.SINGLE_VARIABLE_HORN
                        && !fixedHead.reason.contains("monadic-uniform"),
                "a constant head receives the wider single-variable certificate, not the monadic-uniform one");
        Result typed = ContextProductionRoundsReference.inspect(source(X,true,"!p(1);"),
                Arrays.asList(first,source(UUID.randomUUID(),false,"!~q(John);")),NORMAL);
        require(typed.status == Status.SATURATED && typed.reason.contains("monadic-uniform")
                && product(typed,"q") != null && proves(product(typed,"q"),"?q(1);")
                && typed.products.stream().noneMatch(candidate -> !candidate.clause.variables.isEmpty()
                && candidate.clause.literals.get(0).predicate.equals("q")),
                "numeric and named representatives remain distinct and a ground consequence is not generalized");
        Result siblings = ContextProductionRoundsReference.inspect(source(X,true,"!anchor(1);"),Arrays.asList(
                source(UUID.randomUUID(),false,"!@x p(x);"),source(UUID.randomUUID(),false,"!@x ~p(x);")),NORMAL);
        require(siblings.status == Status.SATURATED && siblings.reason.contains("monadic-uniform")
                && siblings.products.stream().noneMatch(candidate -> candidate.clause.literals.stream().anyMatch(atom -> atom.predicate.equals("p"))),
                "opposite sibling universals remain independent despite shared representative terms");
        Result separateVariables = ContextProductionRoundsReference.inspect(source(X,true,
                "!@x @y (p(x) -> q(x)) && (r(y) -> s(y));"),
                Collections.singletonList(source(UUID.randomUUID(),false,"!anchor(1);")),NORMAL);
        require(separateVariables.status == Status.INCOMPLETE && separateVariables.coverage == Coverage.NONE,
                "separate unary branches cannot hide multi-variable native rotation in one source rule");
        for (int edges=0; edges<4; ++edges) for (int roots=0; roots<4; ++roots) for (int negatives=0; negatives<4; ++negatives) {
            int reachable = roots, previous;
            do {
                previous = reachable;
                if ((edges & 1) != 0 && (reachable & 1) != 0) reachable |= 2;
                if ((edges & 2) != 0 && (reachable & 2) != 0) reachable |= 1;
            } while (reachable != previous);
            List<String> local = new ArrayList<>(); local.add("!anchor(1);");
            if ((roots & 1) != 0) local.add("!@x p(x);");
            if ((roots & 2) != 0) local.add("!@x q(x);");
            List<String> foreign = new ArrayList<>();
            if ((edges & 1) != 0) foreign.add("!@x p(x) -> q(x);");
            if ((edges & 2) != 0) foreign.add("!@x q(x) -> p(x);");
            if ((negatives & 1) != 0) foreign.add("!~p(1);");
            if ((negatives & 2) != 0) foreign.add("!~q(1);");
            Result actual = ContextProductionRoundsReference.inspect(source(X,true,local.toArray(new String[0])),
                    Collections.singletonList(source(UUID.randomUUID(),false,foreign.toArray(new String[0]))),NORMAL);
            boolean conflict = (reachable & negatives) != 0;
            require(actual.status == (conflict ? Status.COLLISION : Status.SATURATED),
                    "independent monadic truth closure edges="+edges+" roots="+roots+" negatives="+negatives
                            +" observed="+actual.status+" "+actual.reason);
            if (!conflict) require(actual.coverage == Coverage.MONADIC_HORN && actual.reason.contains("monadic-uniform"),
                    "consistent fragment has explicit coverage evidence");
        }
    }
    private static boolean proves(Product product, String query) throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        mind.compileLine(product.fact.assertion(), false, product.fact.project(mind));
        return Boolean.TRUE.equals(mind.query(query,null,false));
    }
    private static Product product(Result result, String predicate) {
        for (Product product : result.products) if (product.fact != null && product.fact.predicate.equals(predicate)) return product;
        return null;
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
