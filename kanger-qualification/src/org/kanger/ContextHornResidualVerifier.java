/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextLinearProofVerifier.Claim;

/** Detached obligations only: never supplies missing native residuals. */
final class ContextHornResidualVerifier {
    private static String reduce(Clause clause,int index,List<Fact> remaining,List<Claim> donors,
            List<Support> producers,List<Claim> pool,Runnable work) {
        work.run();
        if (index == clause.literals.size()) {
            if (donors.isEmpty()) return null;
            if (remaining.isEmpty()) return "horn-unreported-collision";
            return supports(new Clause(remaining),donors,0,producers,pool,work);
        }
        Atom atom = clause.literals.get(index);
        remaining.add(atom.fact());
        String missing = reduce(clause,index+1,remaining,donors,producers,pool,work);
        remaining.remove(remaining.size()-1);
        if (missing != null) return missing;
        Claim donor = ContextLinearProofVerifier.find(pool,
                new Clause(Collections.singletonList(atom.fact().opposite())),work);
        if (donor == null) return null;
        donors.add(donor);
        missing = reduce(clause,index+1,remaining,donors,producers,pool,work);
        donors.remove(donors.size()-1);
        return missing;
    }
    private static String supports(Clause conclusion,List<Claim> donors,int index,List<Support> partial,
            List<Claim> pool,Runnable work) {
        work.run();
        if (index == donors.size()) {
            for (Support proof : partial) {
                if (proof.depth == Integer.MAX_VALUE) return "horn-depth-overflow";
                if (!ContextLinearProofVerifier.hasProof(pool,conclusion,proof.sources,proof.depth+1,work))
                    return "horn-residual-proof-missing";
            }
            return null;
        }
        for (Support previous : partial) for (Support donor : donors.get(index).supports) {
            work.run(); Set<Source> sources = new LinkedHashSet<>(previous.sources); sources.addAll(donor.sources);
            Support next = new Support(sources,Math.max(previous.depth,donor.depth));
            String missing = supports(conclusion,donors,index+1,Collections.singletonList(next),pool,work);
            if (missing != null) return missing;
        }
        return null;
    }
    private static String assignments(Claim rule,List<Integer> variables,int index,
            Map<Integer,SemanticTermSnapshot> assignment,List<SemanticTermSnapshot> basis,List<Claim> pool,Runnable work) {
        work.run();
        if (index == variables.size()) {
            Clause instance = rule.clause.instantiate(assignment);
            if (instance.literals.size() == 1) {
                for (Support proof : rule.supports)
                    if (!ContextLinearProofVerifier.hasProof(pool,instance,proof.sources,proof.depth,work))
                        return "horn-unit-proof-missing";
                return null;
            }
            return reduce(instance,0,new ArrayList<Fact>(),new ArrayList<Claim>(),rule.supports,pool,work);
        }
        for (SemanticTermSnapshot value : basis) {
            assignment.put(variables.get(index),value);
            String missing = assignments(rule,variables,index+1,assignment,basis,pool,work);
            if (missing != null) return missing;
        }
        assignment.remove(variables.get(index)); return null;
    }
    static String verify(List<Claim> rules,List<Claim> pool,List<SemanticTermSnapshot> basis,Runnable work) {
        for (int i=0;i<pool.size();++i) {
            work.run();
            if (pool.get(i).supports.isEmpty()) return "horn-pool-proof-missing";
            for (int j=0;j<i;++j) {
                work.run(); if (pool.get(i).clause.equivalent(pool.get(j).clause)) return "horn-pool-duplicate";
            }
            if (pool.get(i).clause.literals.size() == 1) {
                Fact fact = pool.get(i).clause.literals.get(0).fact();
                if (fact != null && ContextLinearProofVerifier.find(pool,new Clause(Collections.singletonList(fact.opposite())),work) != null)
                    return "horn-unreported-collision";
            }
        }
        for (Claim rule : rules) {
            work.run(); int positive = 0;
            if (rule.supports.isEmpty() || rule.clause.literals.size() > 3 || rule.clause.variables.size() > 2)
                return "horn-shape-mismatch";
            for (Atom atom : rule.clause.literals) {
                work.run(); if (atom.positive) ++positive;
                if (atom.arguments.isEmpty()) return "horn-shape-mismatch";
                for (Slot slot : atom.arguments) if (slot.variable < 0) {
                    boolean present = false;
                    for (SemanticTermSnapshot value : basis) { work.run(); if (value.semanticallyEquals(slot.value)) { present = true; break; } }
                    if (!present) return "horn-vocabulary-mismatch";
                }
            }
            if (rule.clause.literals.size() > 1 && positive != 1) return "horn-shape-mismatch";
            if (!rule.clause.variables.isEmpty() && basis.isEmpty()) return "horn-representatives-missing";
            String missing = assignments(rule,new ArrayList<>(rule.clause.variables),0,
                    new HashMap<Integer,SemanticTermSnapshot>(),basis,pool,work);
            if (missing != null) return missing;
        }
        return null;
    }
}
