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
import org.kanger.ContextGroundProductionRounds.Frontier;
import org.kanger.ContextGroundProductionRounds.Collision;

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
        public final List<Collision> collisions;
        final Frontier input;
        final Snapshot target;
        final List<Route> localRoutes;
        private Result(boolean verified,String reason,Snapshot x,Snapshot foreign,List<Production> productions,Frontier input,Snapshot target,List<Collision> collisions) {
            this(verified,reason,x,foreign,productions,input,target,collisions,Collections.emptyList());
        }
        private Result(boolean verified,String reason,Snapshot x,Snapshot foreign,List<Production> productions,Frontier input,Snapshot target,List<Collision> collisions,List<Route> routes) {
            this.localRoutes = Collections.unmodifiableList(new ArrayList<>(routes));
            this.input = input; this.target = target; this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
            this.verified = verified; this.reason = reason; this.x = x; this.foreign = foreign;
            this.productions = Collections.unmodifiableList(new ArrayList<>(productions));
        }
    }
    private static Pattern pattern(Fact fact,Runnable work) {
        List<Argument> arguments = new ArrayList<>();
        for (SemanticTermSnapshot value : fact.arguments) { work.run(); arguments.add(new Argument(null,value)); }
        return new Pattern(fact.predicate,fact.positive,arguments,true);
    }
    static void sameShapes(Entry expected,Input actual,Runnable work) {
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
    static List<Entry> declarations(Snapshot x,Snapshot foreign,Runnable work) {
        ContextProductionFamilyAssembly.identity(x,true,work);
        List<Entry> declarations = new ArrayList<>(x.primary);
        if (foreign != null) {
            ContextProductionFamilyAssembly.identity(foreign,false,work);
            ContextProductionFamilyAssembly.disjoint(x,foreign,work);
            declarations.addAll(foreign.primary);
        }
        if (declarations.size() > 64) throw new IllegalStateException("Ground cut declaration limit");
        for (Entry entry : declarations) {
            work.run(); if (entry.bundle != null) throw new IllegalStateException("Ordinary ground cut cannot rebind witnesses");
            for (Clause clause : entry.clauses) if (clause.literals.size() == 1 && !clause.variables.isEmpty())
                throw new IllegalStateException("Universal unit needs separate ground cut coverage");
        }
        return declarations;
    }
    public static Result inspect(Snapshot x,Snapshot foreign,int maximumDepth,int maximumWork,int maximumEvents) {
        if (maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1) throw new IllegalArgumentException("Bounded ground cut evidence required");
        try {
            Frontier input = ContextGroundProductionRounds.start(x,foreign == null ? Collections.emptyList() : Collections.singletonList(foreign),maximumWork);
            return inspect(input,foreign == null ? x : foreign,maximumDepth,maximumWork,maximumEvents);
        } catch (Exception failure) { return new Result(false,failure.getMessage(),x,foreign,Collections.emptyList(),null,null,Collections.emptyList()); }
    }
    public static Result inspect(Frontier input,Snapshot target,int maximumDepth,int maximumWork,int maximumEvents) {
        if (input == null) throw new IllegalArgumentException("Sealed ground frontier required");
        Snapshot x = input.x, foreign = target == x ? null : target;

        if (maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1) throw new IllegalArgumentException("Bounded ground cut evidence required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Ground cut evidence work limit"); };
        try {
            if (!input.cuts.contains(target)) throw new IllegalStateException("Ground cut outside sealed family");
            List<Entry> declarations = declarations(x,foreign,work);
            for (Production production : input.productions) {
                work.run(); if (production.proof.depth > maximumDepth) throw new IllegalStateException("Carried ground depth limit");
            }
            List<Collision> collisions = new ArrayList<>();
            for (Entry entry : declarations) for (Clause clause : entry.clauses) {
                work.run(); if (!clause.variables.isEmpty() || clause.literals.size() != 1) continue;
                Fact fact = clause.literals.get(0).fact(); Support proof = new Support(Collections.singleton(entry.source),0);
                for (Production carried : input.productions) {
                    Collision collision = ContextGroundProductionRounds.opposition(carried.conclusion,carried.proof,fact,proof,work);
                    if (collision != null) collisions.add(collision);
                }
            }
            if (!collisions.isEmpty()) return new Result(true,"Proved carried product opposed by actual cut unit",x,foreign,Collections.emptyList(),input,target,collisions);
            User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind); mind.setQueryPass(QueryPass.ACCEPT);
            List<Fact> restored = new ArrayList<>();
            for (Production carried : input.productions) {
                work.run(); boolean duplicate = false;
                for (Fact old : restored) { work.run(); if (old.equivalent(carried.conclusion)) { duplicate = true; break; } }
                if (!duplicate) {
                    Object carrier = mind.compileLine(carried.conclusion.assertion(),false,carried.conclusion.project(mind));
                    if (!(carrier instanceof org.kanger.units.Rule) || !carried.conclusion.equivalent(
                            Fact.of(((org.kanger.units.Rule)carrier).getDomain(),mind,false)))
                        throw new IllegalStateException("Ground carrier semantic roundtrip mismatch");
                    restored.add(carried.conclusion);
                }
            }
            UUID replay = UUID.randomUUID();
            try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,maximumEvents)) {
                for (Entry entry : declarations) { work.run(); capture.replay(mind,entry.source,entry.statement); }
                mind.link(null,false);
                ContextProductionCapture.Snapshot observed = capture.snapshot();
                if (observed.inputs.size() != declarations.size()) throw new IllegalStateException("Ground cut input snapshot incomplete");
                for (int i=0;i<declarations.size();++i) sameShapes(declarations.get(i),observed.inputs.get(i),work);
                ContextWitnessPrimaryStepVerifier.Scope scope = ContextWitnessPrimaryStepVerifier.scope(Collections.emptyList(),observed,work,true);
                List<Route> seeds = new ArrayList<>();
                for (Production carried : input.productions) {
                    work.run(); seeds.add(new Route(replay,pattern(carried.conclusion,work),null,carried.proof,null,null,null,null));
                }
                // Only this cut's actual asserted ordinary units become local proof donors.
                for (Input primary : observed.inputs) for (Clause clause : primary.shapes) {
                    work.run(); if (!clause.variables.isEmpty() || clause.literals.size() != 1) continue;
                    Fact fact = clause.literals.get(0).fact(); Pattern atom = pattern(fact,work);
                    seeds.add(new Route(replay,atom,null,new Support(Collections.singleton(primary.source),0),null,null,null,null));
                }
                if (observed.applications.isEmpty()) return new Result(true,"No ordinary applications observed; no closure claim",x,foreign,input.productions,input,target,Collections.emptyList(),seeds);
                ContextWitnessUnaryChainVerifier.Result checked = ContextWitnessUnaryChainVerifier.close(replay,observed,scope,seeds,maximumDepth,2,work,true);
                if (!checked.reconstructed) throw new IllegalStateException(checked.reason);
                List<Production> products = new ArrayList<>();
                for (Route route : checked.routes) {
                    work.run(); if (route.proof.depth == 0 || route.proof.sources.stream().noneMatch(s -> s.localX)) continue;
                    if (route.physical.root != null || route.origin != null) throw new IllegalStateException("Nonordinary ground cut product");
                    Fact conclusion = null;
                    for (Production carried : input.productions)
                        if (route.physical.equivalent(pattern(carried.conclusion,work),work)) { conclusion = carried.conclusion; break; }
                    for (Application application : observed.applications)
                        if (route.physical.equivalent(pattern(application.conclusion,work),work)) { conclusion = application.conclusion; break; }
                    if (conclusion == null) throw new IllegalStateException("Ground cut product lacks native observation");
                    products.add(new Production(conclusion,route.proof));
                }
                return new Result(true,"Observed signed X-dependent ground proofs reconstructed; coverage/admission unknown",x,foreign,products,input,target,Collections.emptyList(),checked.routes);
            }
        } catch (Exception failure) { return new Result(false,failure.getMessage(),x,foreign,Collections.emptyList(),input,target,Collections.emptyList()); }
    }
}
