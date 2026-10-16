/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.interfaces.IRule;
import org.kanger.units.*;

/** Fresh native substitution candidates, never primary facts or additional proof sources. */
final class ContextOrdinaryResidualParameters {
    static void fresh(List<Clause> knowledge,List<SemanticTermSnapshot> parameters,Runnable work) {
        for (int i=0;i<parameters.size();++i) {
            for (int j=0;j<i;++j) { work.run(); if (parameters.get(i).semanticallyEquals(parameters.get(j))) throw new IllegalStateException("Repeated residual eigenparameter"); }
            for (Clause clause : knowledge) for (Atom atom : clause.literals) for (Slot slot : atom.arguments) {
                work.run(); if (slot.variable < 0 && slot.value.containsSemanticValue(parameters.get(i)))
                    throw new IllegalStateException("Residual eigenparameter occurs in family knowledge");
            }
        }
    }
    static List<SemanticTermSnapshot> seed(Mind mind,List<Clause> family,List<Clause> owners,Runnable work) throws Exception {
        Set<TVariable> variables = new LinkedHashSet<>(); List<Integer> ranks = new ArrayList<>(); int rank = 0;
        for (IRule entry : mind.getRules()) if (!entry.isDeleted(mind)) {
            Set<TVariable> scoped = new LinkedHashSet<>();
            for (List<Domain> branch : ((Rule)entry).getTree()) for (Domain domain : branch) {
                work.run(); scoped.addAll(domain.getArguments().getTVariables(mind));
            }
            if (scoped.size() > 2) throw new IllegalStateException("Residual eigenparameter rank limit");
            ranks.add(scoped.size()); rank = Math.max(rank,scoped.size()); variables.addAll(scoped);
        }
        List<SemanticTermSnapshot> parameters = new ArrayList<>();
        for (int i=0;i<rank;++i) { work.run(); parameters.add(SemanticTermSnapshot.capture(mind.getTerms().add("ctxresidualparameter_"+UUID.randomUUID().toString().replace("-","")))); }
        fresh(family,parameters,work);
        List<SemanticTermSnapshot> representatives = new ArrayList<>(parameters);
        for (Clause clause : owners) for (Atom atom : clause.literals) for (Slot slot : atom.arguments) if (slot.variable < 0) {
            boolean duplicate = false;
            for (SemanticTermSnapshot old : representatives) { work.run(); if (old.semanticallyEquals(slot.value)) { duplicate = true; break; } }
            if (!duplicate) representatives.add(slot.value);
        }
        // Charge the Cartesian rotation plan before adding TValue candidates to the private runtime.
        for (int scopedRank : ranks) {
            long rotations = 1;
            for (int i=0;i<scopedRank;++i) {
                work.run(); rotations *= Math.max(1,representatives.size());
                if (rotations > Integer.MAX_VALUE) throw new IllegalStateException("Residual representative plan limit");
            }
            for (long i=0;i<rotations;++i) work.run();
        }
        for (TVariable variable : variables) for (SemanticTermSnapshot value : representatives) {
            work.run(); mind.getTValues().add(variable,mind.getTerms().projectSemantic(value.materialize()));
        }
        return Collections.unmodifiableList(parameters);
    }
}
