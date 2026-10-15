/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextLinearProofVerifier.Claim;

/** Universal elimination on already proved clauses, never native inference. */
final class ContextUniversalInstanceEvidence {
    private static void add(List<Claim> output,Clause clause,List<Support> proofs,Runnable work) {
        Claim old = ContextLinearProofVerifier.find(output,clause,work);
        List<Support> merged = new ArrayList<>();
        if (old != null) merged.addAll(old.supports);
        for (Support proof : proofs) {
            work.run(); int match = -1;
            for (int i=0;i<merged.size();++i) {
                work.run(); if (merged.get(i).sources.equals(proof.sources)) { match = i; break; }
            }
            if (match < 0) merged.add(proof);
            else if (proof.depth < merged.get(match).depth) merged.set(match,proof);
        }
        Claim next = new Claim(clause,merged);
        if (old == null) output.add(next); else output.set(output.indexOf(old),next);
    }
    private static void instances(Claim claim,List<Integer> variables,int index,
            Map<Integer,SemanticTermSnapshot> assignment,List<SemanticTermSnapshot> basis,
            List<Claim> output,Runnable work) {
        work.run();
        if (index == variables.size()) { add(output,claim.clause.instantiate(assignment),claim.supports,work); return; }
        for (SemanticTermSnapshot value : basis) {
            assignment.put(variables.get(index),value);
            instances(claim,variables,index+1,assignment,basis,output,work);
        }
        assignment.remove(variables.get(index));
    }
    /** Input must contain proved universally closed clauses, never shape metadata. */
    static List<Claim> expand(List<Claim> proved,List<SemanticTermSnapshot> basis,Runnable work) {
        List<Claim> output = new ArrayList<>();
        for (int i=0;i<proved.size();++i) {
            work.run(); Claim claim = proved.get(i);
            if (claim.supports.isEmpty() || claim.clause.variables.size() > 2 || claim.clause.literals.size() > 3)
                throw new IllegalArgumentException("Unsupported universal proof evidence");
            if (!claim.clause.variables.isEmpty() && basis.isEmpty())
                throw new IllegalArgumentException("Universal representatives missing");
            for (int j=0;j<i;++j) {
                work.run(); if (claim.clause.equivalent(proved.get(j).clause))
                    throw new IllegalArgumentException("Duplicate universal proof entry");
            }
            instances(claim,new ArrayList<>(claim.clause.variables),0,
                    new HashMap<Integer,SemanticTermSnapshot>(),basis,output,work);
        }
        return Collections.unmodifiableList(output);
    }
}
