/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextLinearProofVerifier.Claim;
import org.kanger.units.Rule;

/** Independent subset oracle and adversarial whole-proof controls. */
public final class ContextHornResidualVerifierRunner {
    private static int checks;
    private static final Source X = source(0), A = source(1), B = source(2), C = source(3);
    private static Source source(int id) { return new Source(new UUID(0,id),1,0,id == 0,"base"); }
    private static Clause clause(String text) throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        Rule rule = (Rule)mind.compileLine(text,false,new LinkedList<org.kanger.interfaces.ITerm>());
        return Clause.shapeOf(rule.getTree().get(0),mind);
    }
    private static Claim claim(Clause c,int depth,Source... sources) {
        return new Claim(c,Collections.singletonList(new Support(new LinkedHashSet<>(Arrays.asList(sources)),depth)));
    }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static String verify(Claim rule,List<Claim> pool,List<SemanticTermSnapshot> basis) {
        int[] remaining = {100000};
        return ContextHornResidualVerifier.verify(Collections.singletonList(rule),pool,basis,() -> {
            if (--remaining[0] < 0) throw new IllegalStateException("work limit");
        });
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("horn-residual-").toString());
            Clause rule = clause("!a(1) && b(1) -> r(1);");
            String[] literals = {"~a(1)","~b(1)","r(1)"};
            Claim[] donors = {claim(clause("!a(1);"),0,A),claim(clause("!b(1);"),0,B),claim(clause("!~r(1);"),0,C)};
            List<SemanticTermSnapshot> basis = donors[0].clause.literals.get(0).fact().arguments;
            Claim producer = claim(rule,0,X);
            for (int mask=0;mask<7;++mask) {
                List<Claim> pool = new ArrayList<>();
                for (int i=0;i<3;++i) if ((mask & (1<<i)) != 0) pool.add(donors[i]);
                int roots = pool.size();
                for (int subset=1;subset<8;++subset) if ((subset & mask) == subset) {
                    List<String> residual = new ArrayList<>(); List<Source> sources = new ArrayList<>(); sources.add(X);
                    for (int i=0;i<3;++i) {
                        if ((subset & (1<<i)) == 0) residual.add(literals[i]);
                        else sources.addAll(donors[i].supports.get(0).sources);
                    }
                    pool.add(claim(clause("!("+String.join(" || ",residual)+");"),1,sources.toArray(new Source[0])));
                }
                require(verify(producer,pool,basis) == null,"all signed subset residuals, mask="+mask);
                for (int i=roots;i<pool.size();++i) {
                    List<Claim> broken = new ArrayList<>(pool); broken.remove(i);
                    require("horn-residual-proof-missing".equals(verify(producer,broken,basis)),"each residual is independently mandatory");
                    broken = new ArrayList<>(pool); broken.set(i,claim(pool.get(i).clause,2,pool.get(i).supports.get(0).sources.toArray(new Source[0])));
                    require("horn-residual-proof-missing".equals(verify(producer,broken,basis)),"direct multi-donor depth is max+1");
                    broken = new ArrayList<>(pool); broken.set(i,claim(pool.get(i).clause,1,A));
                    require("horn-residual-proof-missing".equals(verify(producer,broken,basis)),"foreign proof cannot mask joint residual");
                }
            }
            require(verify(producer,Arrays.asList(donors),basis) != null,"all falsifying donors cannot certify a consistent cut");
            Claim partial = claim(clause("!b(1) -> r(1);"),1,X,A);
            require(verify(partial,Arrays.asList(donors[1],claim(clause("!r(1);"),2,X,A,B)),basis) == null,
                    "carried residual retains earlier producer depth");
            require("horn-residual-proof-missing".equals(verify(partial,Arrays.asList(donors[1],claim(clause("!r(1);"),2,X,B)),basis)),
                    "joint producer cannot split into an alias");
            Claim alternatives = new Claim(donors[0].clause,Arrays.asList(new Support(Collections.singleton(A),0),new Support(Collections.singleton(B),0)));
            Clause residual = clause("!b(1) -> r(1);");
            require("horn-residual-proof-missing".equals(verify(producer,Arrays.asList(alternatives,claim(residual,1,X,A)),basis)),
                    "every donor support alternative is mandatory");
            require(verify(producer,Arrays.asList(alternatives,new Claim(residual,Arrays.asList(
                    new Support(new LinkedHashSet<>(Arrays.asList(X,A)),1),new Support(new LinkedHashSet<>(Arrays.asList(X,B)),1)))),basis) == null,
                    "separate whole-source alternatives discharge their obligations");
            require("horn-shape-mismatch".equals(verify(claim(clause("!a(1) && b(1) && c(1) -> r(1);"),0,X),Collections.emptyList(),basis)),
                    "larger clauses remain outside this bounded checker");
            int[] work = {0}; boolean stopped = false;
            try { ContextHornResidualVerifier.verify(Collections.singletonList(producer),Collections.emptyList(),basis,() -> {
                if (++work[0] > 1) throw new IllegalStateException("bounded");
            }); } catch (IllegalStateException expected) { stopped = true; }
            require(stopped,"work exhaustion never becomes success");
            System.out.println("CONTEXT_HORN_RESIDUAL_VERIFIER_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
