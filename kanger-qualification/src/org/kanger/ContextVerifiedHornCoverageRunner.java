/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionRoundsReference.*;

/** Independent native multi-premise certificate controls. */
public final class ContextVerifiedHornCoverageRunner {
    private static int checks;
    private static final Limits NORMAL = new Limits(16,50000,4000000,64);
    private static List<Declaration> source(boolean local,String... text) {
        UUID id = UUID.randomUUID(); List<Declaration> out = new ArrayList<>();
        for (int i=0;i<text.length;++i) out.add(new Declaration(new Source(id,1,i,local,"base"),text[i]));
        return out;
    }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Result run(List<Declaration> x,List<List<Declaration>> targets) throws Exception {
        return ContextProductionRoundsReference.inspectWithVerifiedHornCoverage(x,targets,NORMAL);
    }
    private static void certified(Result r,String why) {
        require(r.status == Status.SATURATED && r.coverage == Coverage.AUDITED_HORN,why+": "+r.status+" "+r.reason);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("verified-horn-").toString());
            List<Declaration> x = source(true,"!@x @y left(x) && right(y) -> out(x,y);");
            List<Declaration> a = source(false,"!left(1);"), b = source(false,"!right(John);");
            Result partial = run(x,Collections.singletonList(a));
            System.out.println("HORN_PARTIAL status="+partial.status+" reason="+partial.reason);
            certified(partial,"quantified partial residual");
            for (boolean reverse : new boolean[]{false,true}) {
                Result joined = run(x,reverse ? Arrays.asList(b,a) : Arrays.asList(a,b));
                System.out.println("HORN_JOIN status="+joined.status+" reason="+joined.reason);
                certified(joined,"cross-sibling residual join");
                require(joined.products.stream().anyMatch(p -> p.fact != null && p.fact.predicate.equals("out")
                        && p.supports.stream().anyMatch(proof -> proof.sources.contains(x.get(0).source)
                        && proof.sources.contains(a.get(0).source) && proof.sources.contains(b.get(0).source) && proof.depth == 2)),
                        "joined result retains all three sources and carried depth2");
            }
            require(partial.products.stream().noneMatch(p -> p.fact != null && p.fact.predicate.equals("out")),
                    "partial residual does not assert its head");
            Result old = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(x,Collections.singletonList(a),NORMAL);
            require(old.status == Status.INCOMPLETE && old.coverage == Coverage.NONE && old.rounds == 0,
                    "old explicit linear certificate retains its scope");
            List<Declaration> negative = source(false,"!~out(1,John);");
            List<List<Declaration>> family = Arrays.asList(a,b,negative);
            for (int i=0;i<3;++i) for (int j=0;j<3;++j) if (i != j) {
                Result refused = run(x,Arrays.asList(family.get(i),family.get(j),family.get(3-i-j)));
                require(refused.status == Status.COLLISION && refused.collision != null,
                        "native conflict after residual transport in all six participant orders: "+refused.reason);
            }
            Result shallow = ContextProductionRoundsReference.inspectWithVerifiedHornCoverage(x,Arrays.asList(a,b),new Limits(16,50000,4000000,1));
            require(shallow.status == Status.INCOMPLETE && shallow.coverage == Coverage.NONE && shallow.reason.contains("depth limit"),
                    "carried depth is not reset at participant boundary");
            certified(run(source(true,"!anchor(1);"),Arrays.asList(source(false,"!@x @y p(x,y);"),source(false,"!@x @y ~p(x,y);"))),
                    "opposing independent siblings remain compatible with X");
            for (String excluded : new String[]{"!@x @y @z p(x,y,z);","!@x a(x) && b(x) && c(x) -> d(x);",
                    "!@x (p(x) || q(x));","!@x p(x+1);","!$x p(x);"}) {
                Result boundary = run(source(true,excluded),Collections.singletonList(a));
                require(boundary.status == Status.INCOMPLETE && boundary.coverage == Coverage.NONE && boundary.rounds == 0,
                        "unsupported family is rejected before inference: "+excluded);
            }
            List<Declaration> aliases = source(false,"!left(1);","!left(1);");
            Result aliasResult = run(x,Collections.singletonList(aliases));
            certified(aliasResult,"duplicate source occurrences");
            require(aliasResult.products.stream().filter(p -> p.clause.literals.size() == 2).anyMatch(p ->
                    p.supports.stream().anyMatch(proof -> proof.sources.equals(new LinkedHashSet<>(Arrays.asList(x.get(0).source,aliases.get(0).source))))
                    && p.supports.stream().anyMatch(proof -> proof.sources.equals(new LinkedHashSet<>(Arrays.asList(x.get(0).source,aliases.get(1).source))))),
                    "duplicate native clauses retain separate whole source alternatives");
            Result small = ContextProductionRoundsReference.inspectWithVerifiedHornCoverage(x,Arrays.asList(a,b),new Limits(16,50000,1,64));
            require(small.status == Status.INCOMPLETE && small.coverage == Coverage.NONE,"work stop cannot certify");
            Result bounded = ContextProductionRoundsReference.inspectWithVerifiedHornCoverage(x,Arrays.asList(a,b),new Limits(1,50000,4000000,64));
            require(bounded.status == Status.INCOMPLETE && bounded.coverage == Coverage.NONE,"round stop cannot become saturation");
            finiteOracle();
            System.out.println("CONTEXT_VERIFIED_HORN_COVERAGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void finiteOracle() throws Exception {
        for (int mask=0;mask<8;++mask) {
            List<String> facts = new ArrayList<>();
            if ((mask & 1) != 0) facts.add("!a(1);");
            if ((mask & 2) != 0) facts.add("!b(1);");
            if ((mask & 4) != 0) facts.add("!~r(1);");
            List<Declaration> rule = source(true,"!a(1) && b(1) -> r(1);");
            List<Declaration> inputs = source(false,facts.toArray(new String[0]));
            Result r = run(rule,Collections.singletonList(inputs));
            if (mask == 3) {
                require(r.products.stream().anyMatch(p -> p.fact != null && p.fact.predicate.equals("r")
                        && p.supports.stream().anyMatch(proof -> proof.depth == 1 && proof.sources.equals(
                        new LinkedHashSet<>(Arrays.asList(rule.get(0).source,inputs.get(0).source,inputs.get(1).source))))),
                        "fully matched native rule has its valid joint unit proof");
                for (Declaration donor : inputs) require(r.products.stream().filter(p -> p.clause.literals.size() == 2)
                        .anyMatch(p -> p.supports.stream().anyMatch(proof -> proof.depth == 1 && proof.sources.equals(
                        new LinkedHashSet<>(Arrays.asList(rule.get(0).source,donor.source))))),
                        "each native exclusion retains its partial whole-source residual");
            }
            if (mask == 7) require(r.status == Status.COLLISION && r.collision != null,"Boolean contradiction has a native witness");
            else {
                System.out.println("HORN_BOOLEAN mask="+mask+" status="+r.status+" reason="+r.reason);
                certified(r,"independent ground Boolean oracle mask="+mask);
            }
        }
    }
}
