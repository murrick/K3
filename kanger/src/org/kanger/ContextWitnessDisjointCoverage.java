/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessResultTransport.*;

/** A conservative-extension certificate for a predicate-disjoint, fact-free definite Horn cut. */
final class ContextWitnessDisjointCoverage {
    static final class Result {
        final boolean covered;
        final String reason;
        final Set<Source> sources;
        private Result(boolean covered,String reason,Set<Source> sources) {
            this.covered = covered; this.reason = reason;
            this.sources = Collections.unmodifiableSet(new LinkedHashSet<>(sources));
        }
    }
    static Result inspect(Packet input,Cut cut,List<Declaration> declarations,int maximumDepth,int maximumWork,int maximumEvents) {
        if (maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1) throw new IllegalArgumentException("Bounded disjoint coverage required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Disjoint coverage work limit"); };
        try {
            List<Input> inputs = ContextWitnessDeclarationCut.capture(input,cut,declarations,work,maximumWork,maximumEvents);
            Set<String> vocabulary = new HashSet<>();
            for (int i=0;i<input.items.size();++i) {
                Item item = input.items.get(i); work.run(); vocabulary.add(item.atom.predicate);
                for (Support proof : item.proofs) {
                    work.run(); if (proof.depth > maximumDepth) throw new IllegalStateException("Carried proof exceeds disjoint depth limit");
                }
                for (int j=0;j<i;++j) if (item.atom.opposite(input.items.get(j).atom,work))
                    throw new IllegalStateException("Opposite literals in disjoint input");
            }
            Set<Source> sources = new LinkedHashSet<>();
            for (Input declaration : inputs) {
                work.run(); sources.add(declaration.source);
                for (Clause clause : declaration.shapes) {
                    int positives = 0;
                    for (ContextProductionCapture.Atom literal : clause.literals) {
                        work.run(); if (vocabulary.contains(literal.predicate)) throw new IllegalStateException("Declaration vocabulary overlaps carried packet");
                        if (literal.positive) ++positives;
                    }
                    if (positives != 1 || clause.literals.size() < 2)
                        throw new IllegalStateException("Fact-free definite Horn clause required");
                }
            }
            return new Result(true,"Disjoint fact-free definite Horn cut: no new connected literal proofs",sources);
        } catch (Exception failure) { return new Result(false,failure.getMessage(),Collections.emptySet()); }
    }
}
