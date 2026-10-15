/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessResultTransport.Slot;

/** No enabled instance in a positive packet and a fact-free definite Horn declaration cut. */
final class ContextWitnessDormantCoverage {
    static final class Result {
        final boolean covered;
        final String reason;
        final Set<Source> sources;
        private Result(boolean covered,String reason,Set<Source> sources) {
            this.covered = covered; this.reason = reason;
            this.sources = Collections.unmodifiableSet(new LinkedHashSet<>(sources));
        }
    }
    private static boolean enabled(List<ContextProductionCapture.Atom> body,Packet input,int index,
            Map<Integer,Slot> assignment,Runnable work) {
        work.run(); if (index == body.size()) return true;
        for (Item item : input.items) {
            Map<Integer,Slot> next = new LinkedHashMap<>(assignment);
            if (ContextWitnessClauseCollision.match(body.get(index),item,next,work)
                    && enabled(body,input,index+1,next,work)) return true;
        }
        return false;
    }
    static Result inspect(Packet input,Cut cut,List<Declaration> declarations,int maximumDepth,int maximumWork,int maximumEvents) {
        if (maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1) throw new IllegalArgumentException("Bounded dormant coverage required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Dormant coverage work limit"); };
        try {
            List<Input> inputs = ContextWitnessDeclarationCut.capture(input,cut,declarations,work,maximumWork,maximumEvents);
            for (Item item : input.items) {
                work.run(); if (!item.atom.positive) throw new IllegalStateException("Positive carried packet required for dormant Horn coverage");
                for (Support proof : item.proofs) {
                    work.run(); if (proof.depth > maximumDepth) throw new IllegalStateException("Carried proof exceeds dormant depth limit");
                }
            }
            List<List<ContextProductionCapture.Atom>> bodies = new ArrayList<>(); Set<Source> sources = new LinkedHashSet<>();
            for (Input declaration : inputs) {
                work.run(); sources.add(declaration.source);
                for (Clause clause : declaration.shapes) {
                    int positives = 0; List<ContextProductionCapture.Atom> body = new ArrayList<>();
                    for (ContextProductionCapture.Atom literal : clause.literals) {
                        work.run(); if (literal.positive) ++positives; else body.add(literal);
                    }
                    if (positives != 1 || body.isEmpty()) throw new IllegalStateException("Fact-free definite Horn clause required");
                    bodies.add(body);
                }
            }
            for (List<ContextProductionCapture.Atom> body : bodies)
                if (enabled(body,input,0,new LinkedHashMap<>(),work))
                    throw new IllegalStateException("Enabled Horn body: products or proof alternatives need qualification");
            return new Result(true,"Positive packet and fact-free Horn cut: all rule bodies proved disabled",sources);
        } catch (Exception failure) { return new Result(false,failure.getMessage(),Collections.emptySet()); }
    }
}
