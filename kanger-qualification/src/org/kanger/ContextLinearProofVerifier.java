/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;

/** Read-only finite certificate checker. Missing conclusions are never inferred. */
final class ContextLinearProofVerifier {
    static final class Claim {
        final Clause clause;
        final List<Support> supports;
        Claim(Clause clause,List<Support> supports) {
            this.clause = Objects.requireNonNull(clause);
            this.supports = Collections.unmodifiableList(new ArrayList<>(supports));
        }
    }
    private static Claim find(List<Claim> pool,Clause clause,Runnable work) {
        for (Claim claim : pool) { work.run(); if (claim.clause.equivalent(clause)) return claim; }
        return null;
    }
    private static boolean hasProof(List<Claim> pool,Clause clause,Set<Source> sources,int depth,Runnable work) {
        Claim claim = find(pool,clause,work);
        if (claim != null) for (Support support : claim.supports) {
            work.run(); if (support.sources.equals(sources) && support.depth <= depth) return true;
        }
        return false;
    }
    private static String instance(Clause instance,List<Support> producers,List<Claim> pool,Runnable work) {
        work.run();
        if (instance.literals.size() == 1) {
            for (Support producer : producers)
                if (!hasProof(pool,instance,producer.sources,producer.depth,work)) return "linear-unit-proof-missing";
            return null;
        }
        for (int removed=0;removed<2;++removed) {
            Fact falseLiteral = instance.literals.get(removed).fact().opposite();
            Claim premise = find(pool,new Clause(Collections.singletonList(falseLiteral)),work);
            if (premise == null) continue;
            Clause conclusion = new Clause(Collections.singletonList(instance.literals.get(1-removed).fact()));
            for (Support producer : producers) for (Support donor : premise.supports) {
                work.run(); Set<Source> sources = new LinkedHashSet<>(producer.sources); sources.addAll(donor.sources);
                int depth = Math.max(producer.depth,donor.depth);
                if (depth == Integer.MAX_VALUE) return "linear-depth-overflow";
                if (!hasProof(pool,conclusion,sources,depth+1,work)) return "linear-reduction-proof-missing";
            }
        }
        return null;
    }
    private static String assignments(Claim rule,List<Integer> variables,int slot,Map<Integer,SemanticTermSnapshot> assignment,
            List<SemanticTermSnapshot> basis,List<Claim> pool,Runnable work) {
        work.run();
        if (slot == variables.size()) return instance(rule.clause.instantiate(assignment),rule.supports,pool,work);
        for (SemanticTermSnapshot value : basis) {
            assignment.put(variables.get(slot),value);
            String missing = assignments(rule,variables,slot+1,assignment,basis,pool,work);
            if (missing != null) return missing;
        }
        assignment.remove(variables.get(slot)); return null;
    }
    static String verify(List<Claim> rules,List<Claim> pool,List<SemanticTermSnapshot> basis,Runnable work) {
        for (int i=0;i<pool.size();++i) {
            work.run();
            if (pool.get(i).supports.isEmpty()) return "linear-pool-proof-missing";
            for (int j=0;j<i;++j) {
                work.run(); if (pool.get(i).clause.equivalent(pool.get(j).clause)) return "linear-pool-duplicate";
            }
        }
        for (Claim claim : rules) {
            int positive = 0;
            for (Atom atom : claim.clause.literals) {
                if (atom.positive) ++positive;
                if (atom.arguments.isEmpty()) return "linear-verifier-shape-mismatch";
                for (Slot slot : atom.arguments) if (slot.variable < 0) {
                    boolean present = false;
                    for (SemanticTermSnapshot value : basis) { work.run(); if (value.semanticallyEquals(slot.value)) { present = true; break; } }
                    if (!present) return "linear-vocabulary-mismatch";
                }
            }
            if (claim.clause.literals.size() == 2 && positive != 1) return "linear-verifier-shape-mismatch";
        }
        for (Claim claim : pool) if (claim.clause.literals.size() == 1) {
            Fact fact = claim.clause.literals.get(0).fact();
            if (fact != null && find(pool,new Clause(Collections.singletonList(fact.opposite())),work) != null)
                return "unreported-linear-collision";
        }
        for (Claim rule : rules) {
            work.run();
            if (rule.supports.isEmpty() || rule.clause.literals.size() > 2 || rule.clause.variables.size() > 2)
                return "linear-verifier-shape-mismatch";
            if (!rule.clause.variables.isEmpty() && basis.isEmpty()) return "linear-representatives-missing";
            String missing = assignments(rule,new ArrayList<>(rule.clause.variables),0,new HashMap<Integer,SemanticTermSnapshot>(),basis,pool,work);
            if (missing != null) return missing;
        }
        return null;
    }
}
