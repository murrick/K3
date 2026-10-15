/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessUnaryChainVerifier.Argument;
import org.kanger.ContextWitnessUnaryChainVerifier.Pattern;
import org.kanger.ContextWitnessUnaryChainVerifier.Route;
import org.kanger.enums.QueryPass;

/** Actual signed ordinary evidence from exactly X and one independent cut; never a closure/admission verdict. */
public final class ContextGroundCutEvidence {
    public static final class Production {
        public final Fact conclusion;
        public final Support proof;
        private Production(Fact conclusion,Support proof) { this.conclusion = conclusion; this.proof = proof; }
    }
    public static final class Result {
        public final boolean verified;
        public final String reason;
        public final Snapshot x, foreign;
        public final List<Production> productions;
        private Result(boolean verified,String reason,Snapshot x,Snapshot foreign,List<Production> productions) {
            this.verified = verified; this.reason = reason; this.x = x; this.foreign = foreign;
            this.productions = Collections.unmodifiableList(new ArrayList<>(productions));
        }
    }
    private static Pattern pattern(Fact fact,Runnable work) {
        List<Argument> arguments = new ArrayList<>();
        for (SemanticTermSnapshot value : fact.arguments) { work.run(); arguments.add(new Argument(null,value)); }
        return new Pattern(fact.predicate,fact.positive,arguments,true);
    }
    private static void sameShapes(Entry expected,Input actual,Runnable work) {
        if (!expected.source.equals(actual.source) || expected.bundle != null || expected.clauses.size() != actual.shapes.size())
            throw new IllegalStateException("Ground cut whole declaration binding mismatch");
        boolean[] used = new boolean[actual.shapes.size()];
        for (Clause clause : expected.clauses) {
            boolean found = false;
            for (int i=0;i<used.length;++i) {
                work.run(); if (!used[i] && clause.equivalent(actual.shapes.get(i))) { used[i] = true; found = true; break; }
            }
            if (!found) throw new IllegalStateException("Ground cut whole clause instance changed");
        }
    }
    public static Result inspect(Snapshot x,Snapshot foreign,int maximumDepth,int maximumWork,int maximumEvents) {
        if (maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1) throw new IllegalArgumentException("Bounded ground cut evidence required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Ground cut evidence work limit"); };
        try {
            ContextProductionFamilyAssembly.identity(x,true,work);
            List<Entry> declarations = new ArrayList<>(x.primary);
            if (foreign != null) {
                ContextProductionFamilyAssembly.identity(foreign,false,work);
                if (x.inventory.context.equals(foreign.inventory.context)) throw new IllegalStateException("Ground cut cannot alias X");
                declarations.addAll(foreign.primary);
            }
            if (declarations.size() > 64) throw new IllegalStateException("Ground cut declaration limit");
            for (Entry entry : declarations) {
                work.run(); if (entry.bundle != null) throw new IllegalStateException("Ordinary ground cut cannot rebind witnesses");
                for (Clause clause : entry.clauses) if (clause.literals.size() == 1 && !clause.variables.isEmpty())
                    throw new IllegalStateException("Universal unit needs separate ground cut coverage");
            }
            User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind); mind.setQueryPass(QueryPass.ACCEPT);
            UUID replay = UUID.randomUUID();
            try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,maximumEvents)) {
                for (Entry entry : declarations) { work.run(); capture.replay(mind,entry.source,entry.statement); }
                mind.link(null,false);
                ContextProductionCapture.Snapshot observed = capture.snapshot();
                if (observed.inputs.size() != declarations.size()) throw new IllegalStateException("Ground cut input snapshot incomplete");
                for (int i=0;i<declarations.size();++i) sameShapes(declarations.get(i),observed.inputs.get(i),work);
                ContextWitnessPrimaryStepVerifier.Scope scope = ContextWitnessPrimaryStepVerifier.scope(Collections.emptyList(),observed,work,true);
                List<Route> seeds = new ArrayList<>();
                // Only this cut's actual asserted ordinary units become local proof donors.
                for (Input input : observed.inputs) for (Clause clause : input.shapes) {
                    work.run(); if (!clause.variables.isEmpty() || clause.literals.size() != 1) continue;
                    Fact fact = clause.literals.get(0).fact(); Pattern atom = pattern(fact,work);
                    seeds.add(new Route(replay,atom,null,new Support(Collections.singleton(input.source),0),null,null,null,null));
                }
                if (observed.applications.isEmpty()) return new Result(true,"No ordinary applications observed; no closure claim",x,foreign,Collections.emptyList());
                ContextWitnessUnaryChainVerifier.Result checked = ContextWitnessUnaryChainVerifier.close(replay,observed,scope,seeds,maximumDepth,2,work,true);
                if (!checked.reconstructed) throw new IllegalStateException(checked.reason);
                List<Production> products = new ArrayList<>();
                for (Route route : checked.routes) {
                    work.run(); if (route.proof.depth == 0 || route.proof.sources.stream().noneMatch(s -> s.localX)) continue;
                    if (route.physical.root != null || route.origin != null) throw new IllegalStateException("Nonordinary ground cut product");
                    Fact conclusion = null;
                    for (Application application : observed.applications)
                        if (route.physical.equivalent(pattern(application.conclusion,work),work)) { conclusion = application.conclusion; break; }
                    if (conclusion == null) throw new IllegalStateException("Ground cut product lacks native observation");
                    products.add(new Production(conclusion,route.proof));
                }
                return new Result(true,"Observed signed X-dependent ground proofs reconstructed; coverage/admission unknown",x,foreign,products);
            }
        } catch (Exception failure) { return new Result(false,failure.getMessage(),x,foreign,Collections.emptyList()); }
    }
}
