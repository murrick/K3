/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessResultTransport.Cut;
import org.kanger.ContextWitnessResultTransport.Item;
import org.kanger.ContextWitnessResultTransport.Packet;
import org.kanger.ContextWitnessResultTransport.Slot;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Positive refutations only: universal elimination and a falsified sourced clause. */
final class ContextWitnessClauseCollision {
    enum Status { COLLISION, INCOMPLETE }
    static final class Evidence {
        final Source producer;
        final Clause clause;
        final Map<Integer,Slot> assignment;
        /** One opposite proved Item per clause literal, in the same order. */
        final List<Item> opponents;
        final List<Support> proofs;
        private Evidence(Source producer,Clause clause,Map<Integer,Slot> assignment,List<Item> opponents,List<Support> proofs) {
            this.producer = producer; this.clause = clause;
            this.assignment = Collections.unmodifiableMap(new LinkedHashMap<>(assignment));
            this.opponents = Collections.unmodifiableList(new ArrayList<>(opponents));
            this.proofs = Collections.unmodifiableList(new ArrayList<>(proofs));
        }
    }
    static final class Result {
        final Status status;
        final String reason;
        final UUID inputIdentity;
        final Cut cut;
        final List<Evidence> evidence;
        private Result(Status status,String reason,Packet input,Cut cut,List<Evidence> evidence) {
            this.status = status; this.reason = reason; inputIdentity = input.identity; this.cut = cut;
            this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
        }
    }
    private static boolean same(Slot left,Slot right) {
        return left.origin != null || right.origin != null ? Objects.equals(left.origin,right.origin)
                : left.value.semanticallyEquals(right.value);
    }
    private static boolean match(ContextProductionCapture.Atom literal,Item opponent,Map<Integer,Slot> assignment,Runnable work) {
        work.run();
        if (!literal.predicate.equals(opponent.atom.predicate) || literal.positive == opponent.atom.positive
                || literal.arguments.size() != opponent.atom.arguments.size()) return false;
        for (int i=0;i<literal.arguments.size();++i) {
            work.run(); ContextProductionCapture.Slot formal = literal.arguments.get(i); Slot actual = opponent.atom.arguments.get(i);
            if (formal.variable >= 0) {
                Slot prior = assignment.putIfAbsent(formal.variable,actual);
                if (prior != null && !same(prior,actual)) return false;
            } else if (actual.origin != null || !formal.value.semanticallyEquals(actual.value)) return false;
        }
        return true;
    }
    private static void supports(List<Item> opponents,int index,Set<Source> sources,int depth,int maximumDepth,
            List<Support> proofs,Runnable work) {
        work.run();
        if (index == opponents.size()) {
            if (depth == Integer.MAX_VALUE || depth+1 > maximumDepth) throw new IllegalStateException("Clause collision depth limit");
            for (int i=0;i<proofs.size();++i) {
                work.run(); Support old = proofs.get(i);
                if (old.sources.equals(sources)) {
                    if (depth+1 < old.depth) proofs.set(i,new Support(sources,depth+1)); return;
                }
            }
            if (proofs.size() == 256) throw new IllegalStateException("Clause collision support limit");
            proofs.add(new Support(sources,depth+1)); return;
        }
        for (Support donor : opponents.get(index).proofs) {
            work.run(); Set<Source> next = new LinkedHashSet<>(sources); next.addAll(donor.sources);
            supports(opponents,index+1,next,Math.max(depth,donor.depth),maximumDepth,proofs,work);
        }
    }
    private static void instances(Source producer,Clause clause,Packet input,int index,Map<Integer,Slot> assignment,
            List<Item> opponents,int maximumDepth,List<Evidence> evidence,Runnable work) {
        work.run();
        if (index == clause.literals.size()) {
            List<Support> proofs = new ArrayList<>();
            supports(opponents,0,new LinkedHashSet<>(Collections.singleton(producer)),0,maximumDepth,proofs,work);
            if (evidence.size() == 256) throw new IllegalStateException("Clause collision evidence limit");
            evidence.add(new Evidence(producer,clause,assignment,opponents,proofs)); return;
        }
        for (Item item : input.items) {
            Map<Integer,Slot> next = new LinkedHashMap<>(assignment);
            if (!match(clause.literals.get(index),item,next,work)) continue;
            List<Item> selected = new ArrayList<>(opponents); selected.add(item);
            instances(producer,clause,input,index+1,next,selected,maximumDepth,evidence,work);
        }
    }
    /** A fresh compiler authenticates universal clause shape; no native link or general inference. */
    static Result inspect(Packet input,Cut cut,List<Declaration> declarations,int maximumDepth,int maximumWork,int maximumEvents) {
        if (maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1) throw new IllegalArgumentException("Bounded collision verification required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Clause collision work limit"); };
        try {
            // Validates all carried X cuts/roles before accepting a target cut.
            ContextWitnessResultTransport.family(input,Collections.singletonList(cut),maximumWork);
            Cut parent = null;
            for (Item item : input.items) for (Support proof : item.proofs) for (Source source : proof.sources) {
                work.run(); if (source.localX) parent = Cut.of(source);
            }
            if (declarations.isEmpty() || declarations.size() > 64) throw new IllegalStateException("Bounded nonempty declaration cut required");
            List<Declaration> snapshot = new ArrayList<>(declarations); Set<Source> seen = new HashSet<>();
            boolean target = false;
            User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
            List<Input> inputs;
            try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,maximumEvents)) {
                for (Declaration declaration : snapshot) {
                    work.run(); Source source = declaration.source; Cut actual = Cut.of(source);
                    if (!seen.add(source)) throw new IllegalStateException("Duplicate collision source occurrence");
                    if (!(source.localX ? actual.equals(parent) : !cut.localX && actual.equals(cut)))
                        throw new IllegalStateException("Collision producer outside expected cut");
                    target |= actual.equals(cut); capture.replay(mind,source,declaration.statement);
                }
                Snapshot captured = capture.snapshot();
                if (!target || captured.inputs.size() != snapshot.size() || !captured.gaps.isEmpty())
                    throw new IllegalStateException("Incomplete collision declaration capture");
                inputs = captured.inputs;
                for (int i=0;i<inputs.size();++i) {
                    work.run(); if (!inputs.get(i).source.equals(snapshot.get(i).source))
                        throw new IllegalStateException("Collision source binding mismatch");
                }
                // Shape metadata is evidence only after checking the entire original sourced Rule.
                // TERM witnesses/functions cannot be promoted to universal variables.
                for (Input declaration : inputs) {
                    work.run(); Rule rule = (Rule)mind.getRules().get(declaration.nativeRule);
                    if (rule.isQuery() || rule.isGenerated() || declaration.branches < 1 || declaration.branches > 8
                            || declaration.shapes.size() != declaration.branches || declaration.variableCount > 2)
                        throw new IllegalStateException("Outside universal collision clause scope");
                    for (List<Domain> branch : rule.getTree()) for (Domain domain : branch) for (IArgument argument : domain.getArguments()) {
                        work.run();
                        if (argument.getType() != ArgumentType.TVARIABLE && (argument.getType() != ArgumentType.TERM
                                || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))))
                            throw new IllegalStateException("Non-universal collision argument");
                    }
                    for (Clause clause : declaration.shapes) {
                        work.run(); if (clause.literals.size() > 3) throw new IllegalStateException("Collision clause size limit");
                    }
                }
            }
            List<Evidence> evidence = new ArrayList<>();
            for (Input declaration : inputs) for (Clause clause : declaration.shapes)
                instances(declaration.source,clause,input,0,new LinkedHashMap<>(),new ArrayList<>(),maximumDepth,evidence,work);
            return new Result(evidence.isEmpty() ? Status.INCOMPLETE : Status.COLLISION,
                    evidence.isEmpty() ? "No falsified clause instance proved; coverage unknown" : "Sourced universal clause refuted by carried proofs",
                    input,cut,evidence);
        } catch (Exception failure) {
            return new Result(Status.INCOMPLETE,failure.getMessage(),input,cut,Collections.emptyList());
        }
    }
}
