/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextLinearProofVerifier.Claim;
import org.kanger.units.Rule;

/** Native audited certificates plus independent finite proof-obligation controls. */
public final class ContextVerifiedLinearCoverageRunner {
    private static int checks;
    private static final Limits NORMAL = new Limits(16,50000,3000000,64);
    private static final Source X = new Source(new UUID(0,1),1,0,true,"base");
    private static final Source A = new Source(new UUID(0,2),1,0,false,"base");
    private static Mind memory() throws Exception { User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); return m; }
    private static List<Declaration> source(boolean local,String... statements) {
        UUID id = UUID.randomUUID(); List<Declaration> out = new ArrayList<>();
        for (int i=0;i<statements.length;++i) out.add(new Declaration(new Source(id,1,i,local,"base"),statements[i]));
        return out;
    }
    private static Clause clause(String text) throws Exception {
        Mind mind = memory(); Rule rule = (Rule)mind.compileLine(text,false,new LinkedList<org.kanger.interfaces.ITerm>());
        return Clause.shapeOf(rule.getTree().get(0),mind);
    }
    private static Claim claim(Clause clause,int depth,Source... sources) {
        return new Claim(clause,Collections.singletonList(new Support(new LinkedHashSet<>(Arrays.asList(sources)),depth)));
    }
    private static String verify(List<Claim> rules,List<Claim> pool,List<SemanticTermSnapshot> basis) {
        int[] work = {100000}; return ContextLinearProofVerifier.verify(rules,pool,basis,() -> {
            if (--work[0] < 0) throw new IllegalStateException("control work limit");
        });
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("verified-linear-").toString());
            independentObligations(); templatesAndSources(); independentContexts(); boundaries(); finiteOracle();
            System.out.println("CONTEXT_VERIFIED_LINEAR_COVERAGE_PASS checks="+checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void independentObligations() throws Exception {
        Clause rule = clause("!@x @y p(x,y) -> q(y,x);");
        Clause p = clause("!p(1,John);"), q = clause("!q(John,1);"), wrong = clause("!q(1,John);");
        List<SemanticTermSnapshot> basis = p.literals.get(0).fact().arguments;
        Claim premise = claim(p,0,A), produced = claim(q,1,X,A);
        List<Claim> rules = Arrays.asList(claim(rule,0,X),premise);
        require(verify(rules,Arrays.asList(premise,produced),basis) == null,"independent finite ground closure is certified");
        require("linear-reduction-proof-missing".equals(verify(rules,Collections.singletonList(premise),basis)),
                "truth of the premise cannot manufacture the missing native proof");
        require("linear-reduction-proof-missing".equals(verify(rules,Arrays.asList(premise,claim(wrong,1,X,A)),basis)),
                "wrong argument permutation cannot discharge the obligation");
        require("linear-reduction-proof-missing".equals(verify(rules,Arrays.asList(premise,claim(q,1,A)),basis)),
                "an existing foreign conclusion cannot mask the missing joint X/A proof");
        require("linear-reduction-proof-missing".equals(verify(rules,Arrays.asList(premise,claim(q,2,X,A)),basis)),
                "carried depth cannot be silently increased");
        require("linear-pool-proof-missing".equals(verify(rules,Arrays.asList(premise,new Claim(q,Collections.emptyList())),basis)),
                "an unsupported pool entry cannot count as a proved truth");
        require("linear-pool-duplicate".equals(verify(rules,Arrays.asList(premise,produced,produced),basis)),
                "ambiguous duplicate pool entries cannot hide support alternatives");
        Source alias = new Source(new UUID(0,3),1,0,false,"base");
        List<Claim> alternatives = Arrays.asList(claim(rule,0,X),new Claim(p,Arrays.asList(
                new Support(Collections.singleton(A),0),new Support(Collections.singleton(alias),0))));
        require("linear-reduction-proof-missing".equals(verify(alternatives,
                Arrays.asList(alternatives.get(1),produced),basis)),"every alternative whole support has its own obligation");
        Claim both = new Claim(q,Arrays.asList(new Support(new LinkedHashSet<>(Arrays.asList(X,A)),1),
                new Support(new LinkedHashSet<>(Arrays.asList(X,alias)),1)));
        require(verify(alternatives,Arrays.asList(alternatives.get(1),both),basis) == null,"distinct whole proof alternatives remain separate");
        Clause notQ = new Clause(Collections.singletonList(q.literals.get(0).fact().opposite()));
        Clause notP = new Clause(Collections.singletonList(p.literals.get(0).fact().opposite()));
        require(verify(Arrays.asList(claim(rule,0,X),claim(notQ,0,A)),
                Arrays.asList(claim(notQ,0,A),claim(notP,1,X,A)),basis) == null,"negative signed reduction uses the opposite head");
        require("unreported-linear-collision".equals(verify(rules,
                Arrays.asList(premise,produced,claim(notQ,0,A)),basis)),"an unwitnessed opposite pair cannot certify consistency");
        require("linear-unit-proof-missing".equals(verify(Collections.singletonList(claim(clause("!@x @y p(x,y);"),0,A)),
                Collections.singletonList(premise),basis)),"one observed instance cannot stand for a universal unit");
        boolean refused = false;
        try { rule.instantiate(Collections.<Integer,SemanticTermSnapshot>emptyMap()); } catch (IllegalArgumentException expected) { refused = true; }
        require(refused,"structural instantiation requires the entire declared variable assignment");
    }
    private static void templatesAndSources() throws Exception {
        for (String root : new String[]{"!@x @y p(x,y);","!@x p(x,x);","!p(1,John);"}) {
            List<Declaration> x = source(true,root), a = source(false,"!@x @y p(x,y) -> q(y,x);");
            Result result = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(x,Collections.singletonList(a),NORMAL);
            certified(result,"ordinary, diagonal and independent tuple cases");
            Mind mind = units(result);
            if (root.equals("!@x @y p(x,y);")) {
                require(Boolean.TRUE.equals(mind.query("?q(John,73);",null,false)),"two arbitrary independent positions survive generalization");
                require(result.products.stream().anyMatch(p -> p.clause.literals.size() == 1
                        && p.clause.literals.get(0).predicate.equals("q") && p.clause.variables.size() == 2
                        && p.supports.stream().anyMatch(proof -> proof.sources.contains(x.get(0).source)
                        && proof.sources.contains(a.get(0).source) && proof.depth == 1)),"certified template keeps whole X/A proof at depth1");
            } else if (root.contains("p(x,x)")) {
                require(Boolean.TRUE.equals(mind.query("?q(73,73);",null,false)) && mind.query("?q(73,74);",null,false) == null,
                        "diagonal proof cannot split a repeated variable");
            } else require(Boolean.TRUE.equals(mind.query("?q(John,1);",null,false)) && mind.query("?q(1,John);",null,false) == null,
                    "fixed typed tuple retains its permutation");
            require(result.observations.stream().anyMatch(s -> s.rotationsAudited && s.allRotationsAdmitted() && s.allRotationsVisited()),
                    "certificate has actual finite rotation evidence");
        }
        Result masked = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(source(true,"!@x @y p(x,y);"),
                Collections.singletonList(source(false,"!@x @y q(x,y);","!@x @y p(x,y) -> q(y,x);")),NORMAL);
        certified(masked,"an already present foreign conclusion");
        require(masked.products.stream().anyMatch(p -> p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("q")
                && p.supports.stream().anyMatch(proof -> proof.usesX() && proof.depth == 1)),"foreign unit does not erase the X-dependent route");
        Result measured = ContextProductionRoundsReference.inspectWithRotationAudit(source(true,"!@x @y p(x,y);"),
                Collections.singletonList(source(false,"!@x @y p(x,y) -> q(y,x);")),NORMAL);
        require(measured.status == Status.INCOMPLETE && measured.coverage == Coverage.NONE,"measurement-only API retains its old boundary");
    }
    private static void independentContexts() throws Exception {
        List<Declaration> x = source(true,"!@x @y p(x,y) -> q(y,x);");
        List<Declaration> a = source(false,"!@x @y p(x,y);"), b = source(false,"!@x @y ~q(x,y);");
        for (boolean reverse : new boolean[]{false,true}) {
            Result collision = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(x,reverse ? Arrays.asList(b,a) : Arrays.asList(a,b),NORMAL);
            require(collision.status == Status.COLLISION && collision.rounds == 2 && collision.collision != null,
                    "opposing derived units collide at the next immutable barrier in either order");
        }
        Result siblings = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(source(true,"!anchor(1);"),Arrays.asList(
                source(false,"!@x @y p(x,y);"),source(false,"!@x @y ~p(x,y);")),NORMAL);
        certified(siblings,"opposite independent siblings");
        require(siblings.products.stream().noneMatch(p -> p.clause.literals.stream().anyMatch(t -> t.predicate.equals("p"))),
                "foreign unit proofs never become X knowledge");
        List<Declaration> root = source(true,"!@x @y p(x,y);");
        List<List<Declaration>> chain = Arrays.asList(source(false,"!@x @y p(x,y) -> q(y,x);"),
                source(false,"!@x @y q(x,y) -> r(y,x);"),source(false,"!~r(1,John);"));
        for (int i=0;i<3;++i) for (int j=0;j<3;++j) if (i != j) {
            Result collision = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(root,
                    Arrays.asList(chain.get(i),chain.get(j),chain.get(3-i-j)),NORMAL);
            require(collision.status == Status.COLLISION && collision.rounds == 3,
                    "two applications preserve the third-round refusal in all six participant orders");
        }
        Result shallow = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(root,chain.subList(0,2),
                new Limits(16,50000,3000000,1));
        require(shallow.status == Status.INCOMPLETE && shallow.coverage == Coverage.NONE && shallow.reason.contains("depth limit"),
                "carried derivation depth cannot be reset by the new certificate");
        List<Declaration> aliasRules = source(false,"!@x @y p(x,y) -> q(y,x);","!@u @v p(u,v) -> q(v,u);");
        Result aliases = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(root,Collections.singletonList(aliasRules),NORMAL);
        certified(aliases,"duplicate rule source occurrences");
        require(aliases.products.stream().filter(p -> p.clause.literals.size() == 1 && p.clause.variables.size() == 2
                && p.clause.literals.get(0).predicate.equals("q")).anyMatch(p ->
                    p.supports.stream().anyMatch(proof -> proof.sources.contains(root.get(0).source)
                            && proof.sources.contains(aliasRules.get(0).source) && !proof.sources.contains(aliasRules.get(1).source))
                    && p.supports.stream().anyMatch(proof -> proof.sources.contains(root.get(0).source)
                            && proof.sources.contains(aliasRules.get(1).source) && !proof.sources.contains(aliasRules.get(0).source))),
                "both whole source alternatives survive canonical duplicate rules");
    }
    private static void boundaries() throws Exception {
        for (String excluded : new String[]{"!@x @y @z p(x,y,z);","!@x @y p(x) && r(y) -> q(x,y);",
                "!@x @y (p(x,y)||q(y,x));","!@x p(x+1);","!$x p(x);",
                "!@x @y @z (p(x)->q(x)) && (r(y)->s(y)) && (t(z)->u(z));"}) {
            Result result = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(source(true,excluded),Collections.emptyList(),NORMAL);
            require(result.status == Status.INCOMPLETE && result.coverage == Coverage.NONE
                    && result.reason.contains("outside-audited-linear-horn"),"unsupported shape cannot borrow the certificate: "+excluded);
        }
        List<Declaration> x = source(true,"!@x @y p(x,y);"), a = source(false,"!@x @y p(x,y) -> q(y,x);");
        for (Limits limit : new Limits[]{new Limits(1,50000,3000000,64),new Limits(16,8,3000000,64),new Limits(16,50000,20,64)}) {
            Result result = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(x,Collections.singletonList(a),limit);
            require(result.status == Status.INCOMPLETE && result.coverage == Coverage.NONE,"a finite stop cannot acquire a certificate");
        }
        Result ambiguous = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(Arrays.asList(
                new Declaration(X,"!@x p(x);"),new Declaration(X,"!@x q(x);")),Collections.emptyList(),NORMAL);
        require(ambiguous.status == Status.INCOMPLETE && ambiguous.coverage == Coverage.NONE,"one source occurrence cannot name two different declarations");
    }
    /** Independent reachability oracle over all64 two-rule/root/negative configurations. */
    private static void finiteOracle() throws Exception {
        for (int edges=0;edges<4;++edges) for (int roots=0;roots<4;++roots) for (int negatives=0;negatives<4;++negatives) {
            int reachable = roots, previous;
            do { previous = reachable;
                if ((edges&1) != 0 && (reachable&1) != 0) reachable |= 2;
                if ((edges&2) != 0 && (reachable&2) != 0) reachable |= 1;
            } while (reachable != previous);
            List<String> local = new ArrayList<>(); local.add("!anchor(1);");
            if ((roots&1) != 0) local.add("!@x @y p(x,y);");
            if ((roots&2) != 0) local.add("!@x @y q(x,y);");
            List<String> foreign = new ArrayList<>();
            if ((edges&1) != 0) foreign.add("!@x @y p(x,y) -> q(y,x);");
            if ((edges&2) != 0) foreign.add("!@x @y q(x,y) -> p(y,x);");
            if ((negatives&1) != 0) foreign.add("!~p(1,John);");
            if ((negatives&2) != 0) foreign.add("!~q(John,1);");
            Result result = ContextProductionRoundsReference.inspectWithVerifiedLinearCoverage(source(true,local.toArray(new String[0])),
                    Collections.singletonList(source(false,foreign.toArray(new String[0]))),NORMAL);
            boolean conflict = (reachable&negatives) != 0;
            require(result.status == (conflict ? Status.COLLISION : Status.SATURATED),
                    "independent finite oracle edges="+edges+" roots="+roots+" negatives="+negatives+" actual="+result.status+" "+result.reason);
            if (!conflict) require(result.coverage == Coverage.AUDITED_LINEAR_HORN,"consistent oracle has the explicit certificate");
        }
    }
    private static Mind units(Result result) throws Exception {
        Mind mind = memory(); for (Product product : result.products) if (product.clause.literals.size() == 1)
            mind.compileLine(product.clause.assertion(),false,product.clause.project(mind)); return mind;
    }
    private static void certified(Result result,String label) {
        require(result.status == Status.SATURATED && result.coverage == Coverage.AUDITED_LINEAR_HORN,label+": "+result.status+" "+result.reason);
    }
    private static void require(boolean value,String message) { ++checks; if (!value) throw new AssertionError(message); }
}
