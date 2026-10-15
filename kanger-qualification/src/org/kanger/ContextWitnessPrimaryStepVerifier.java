/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextWitnessBundle.PrimaryEvidence;
import org.kanger.ContextWitnessBundle.Session;
import org.kanger.ContextWitnessProjection.*;
import org.kanger.ContextWitnessProjection.Slot;

/** Reconstructs observed single native steps from whole primary witness bundles. */
final class ContextWitnessPrimaryStepVerifier {
    static final class Step {
        final UUID replay;
        final ContextWitnessProjection observation;
        final List<Support> supports;
        Step(UUID replay,ContextWitnessProjection observation,List<Support> supports) {
            this.replay = replay; this.observation = observation;
            this.supports = Collections.unmodifiableList(new ArrayList<>(supports));
        }
    }
    static final class Result {
        final boolean reconstructed;
        final String reason;
        final List<Step> steps;
        Result(boolean reconstructed,String reason,List<Step> steps) {
            this.reconstructed = reconstructed; this.reason = reason;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }
    }
    private static Result incomplete(String why) { return new Result(false,why,Collections.emptyList()); }
    private static boolean root(Slot slot,long term,long owner) {
        if (slot.lineage.isEmpty()) return false;
        Node root = slot.lineage.get(slot.lineage.size()-1);
        return root.term == term && root.ownerRule == owner && !root.domini;
    }
    private static boolean matches(ContextWitnessBundle.Atom atom,PrimaryEvidence evidence,Literal literal,Runnable work) {
        work.run();
        if (!atom.predicate.equals(literal.predicate) || atom.positive != literal.positive
                || atom.arguments.size() != literal.arguments.size()) return false;
        for (int i=0;i<atom.arguments.size();++i) {
            work.run(); ContextWitnessBundle.Slot expected = atom.arguments.get(i); Slot actual = literal.arguments.get(i);
            if (expected.witness >= 0) {
                if (!root(actual,evidence.witnessTerms.get(expected.witness),evidence.nativeRule)) return false;
            } else if (!actual.lineage.isEmpty() || actual.value == null || !expected.value.semanticallyEquals(actual.value)) return false;
        }
        return true;
    }
    private static boolean knownRoots(Literal literal,List<PrimaryEvidence> primary,Runnable work) {
        for (Slot slot : literal.arguments) if (!slot.lineage.isEmpty()) {
            boolean known = false;
            for (PrimaryEvidence evidence : primary) for (long term : evidence.witnessTerms) {
                work.run(); if (root(slot,term,evidence.nativeRule)) known = true;
            }
            if (!known) return false;
        }
        return true;
    }
    private static boolean sameSlot(Slot left,Slot right) {
        if (left.lineage.isEmpty() || right.lineage.isEmpty())
            return left.lineage.isEmpty() && right.lineage.isEmpty() && left.value.semanticallyEquals(right.value);
        Node a = left.lineage.get(left.lineage.size()-1), b = right.lineage.get(right.lineage.size()-1);
        return a.term == b.term && a.ownerRule == b.ownerRule && a.domini == b.domini;
    }
    private static boolean instance(Clause shape,ContextWitnessProjection observation,int index,boolean[] used,
            Map<Integer,Slot> bindings,Runnable work) {
        work.run();
        if (index == shape.literals.size()) return true;
        ContextProductionCapture.Atom expected = shape.literals.get(index);
        for (int i=0;i<used.length;++i) if (!used[i]) {
            work.run(); Literal actual = i == 0 ? observation.conclusion : observation.premises.get(i-1);
            boolean positive = i == 0 ? actual.positive : !actual.positive;
            if (!expected.predicate.equals(actual.predicate) || expected.positive != positive
                    || expected.arguments.size() != actual.arguments.size()) continue;
            Map<Integer,Slot> next = new HashMap<>(bindings); boolean matches = true;
            for (int a=0;a<expected.arguments.size();++a) {
                work.run(); ContextProductionCapture.Slot slot = expected.arguments.get(a); Slot value = actual.arguments.get(a);
                if (slot.variable >= 0) {
                    Slot old = next.putIfAbsent(slot.variable,value);
                    if (old != null && !sameSlot(old,value)) { matches = false; break; }
                } else if (!value.lineage.isEmpty() || !slot.value.semanticallyEquals(value.value)) { matches = false; break; }
            }
            if (matches) {
                used[i] = true;
                boolean found = instance(shape,observation,index+1,used,next,work);
                used[i] = false;
                if (found) return true;
            }
        }
        return false;
    }
    static boolean producerInstance(Clause shape,ContextWitnessProjection observation,Runnable work) {
        return shape.literals.size() == observation.premises.size()+1
                && instance(shape,observation,0,new boolean[shape.literals.size()],new HashMap<Integer,Slot>(),work);
    }
    private static void add(List<Support> output,Set<Source> sources,int depth,Runnable work) {
        for (int i=0;i<output.size();++i) {
            work.run(); Support old = output.get(i);
            if (old.sources.equals(sources)) {
                if (depth < old.depth) output.set(i,new Support(sources,depth));
                return;
            }
        }
        output.add(new Support(sources,depth));
    }
    private static void join(List<List<Support>> donors,int index,Set<Source> sources,int depth,
            int maximumDepth,List<Support> output,Runnable work) {
        work.run();
        if (index == donors.size()) {
            if (depth == Integer.MAX_VALUE || depth+1 > maximumDepth) throw new IllegalStateException("Witness proof depth limit");
            add(output,sources,depth+1,work); return;
        }
        for (Support donor : donors.get(index)) {
            work.run(); Set<Source> union = new LinkedHashSet<>(sources); union.addAll(donor.sources);
            join(donors,index+1,union,Math.max(depth,donor.depth),maximumDepth,output,work);
        }
    }
    static Result inspect(Session session,int maximumDepth,int maximumWork) {
        if (maximumDepth < 0 || maximumWork < 1) throw new IllegalArgumentException("Bounded witness proof verification required");
        int[] left = {maximumWork}; Runnable work = () -> { if (--left[0] < 0) throw new IllegalStateException("Witness proof work limit"); };
        try {
            Snapshot snapshot = session.capture.snapshot();
            for (String gap : snapshot.gaps) {
                work.run();
                if (!(gap.startsWith("cvariable-input:") || gap.startsWith("nonordinary-primary-fact:")
                        || gap.startsWith("nonordinary-conclusion:") || gap.startsWith("nonordinary-premise:")
                        || gap.startsWith("nonordinary-materialization:"))) return incomplete("unsupported-capture-gap:"+gap);
            }
            Map<Source,Long> sources = new HashMap<>(); Map<Long,Set<Source>> producers = new HashMap<>();
            Map<Long,List<Clause>> producerShapes = new HashMap<>();
            Map<Boolean,Source> cutLabels = new HashMap<>();
            Set<Source> bundleSources = new HashSet<>();
            for (PrimaryEvidence evidence : session.primary) { work.run(); bundleSources.add(evidence.bundle.source); }
            for (Input input : snapshot.inputs) {
                work.run();
                if (input.source == null) return incomplete("derived-input-outside-primary-step");
                Source label = cutLabels.putIfAbsent(input.source.localX,input.source);
                if (label != null && (!label.context.equals(input.source.context) || label.revision != input.source.revision
                        || !label.view.equals(input.source.view))) return incomplete("incompatible-source-cut");
                Source opposite = cutLabels.get(!input.source.localX);
                if (opposite != null && opposite.context.equals(input.source.context)) return incomplete("ambiguous-source-role");
                Long old = sources.put(input.source,input.nativeRule);
                if (old != null && old != input.nativeRule) return incomplete("conflicting-source-occurrence");
                producers.computeIfAbsent(input.nativeRule,k -> new LinkedHashSet<>()).add(input.source);
                if (!bundleSources.contains(input.source)) {
                    if (input.variableCount > 2 || input.shapes.size() != input.branches) return incomplete("producer-scope-unproved");
                    for (Clause shape : input.shapes) { work.run(); if (shape.literals.size() > 3) return incomplete("producer-scope-unproved"); }
                    producerShapes.put(input.nativeRule,input.shapes);
                }
            }
            for (PrimaryEvidence evidence : session.primary) {
                work.run();
                if (!Objects.equals(sources.get(evidence.bundle.source),evidence.nativeRule)) return incomplete("primary-binding-missing");
            }
            List<Step> steps = new ArrayList<>();
            for (ContextWitnessProjection observation : snapshot.witnessProjections) {
                work.run();
                if (observation.premises.isEmpty() || observation.premises.size() > 2) return incomplete("outside-primary-witness-step");
                boolean instance = false;
                for (Clause shape : producerShapes.getOrDefault(observation.nativeRule,Collections.emptyList()))
                    if (producerInstance(shape,observation,work)) { instance = true; break; }
                if (!instance) return incomplete("witness-producer-instance-mismatch");
                if (!knownRoots(observation.conclusion,session.primary,work)) return incomplete("new-witness-outside-primary-step");
                Set<Source> expected = producers.get(observation.nativeRule), actual = new LinkedHashSet<>();
                for (Support producer : observation.producerSupports) {
                    work.run(); if (producer.depth != 0 || producer.sources.size() != 1) return incomplete("producer-proof-not-primary");
                    actual.addAll(producer.sources);
                }
                if (expected == null || !actual.equals(expected)) return incomplete("producer-binding-mismatch");
                List<List<Support>> donors = new ArrayList<>();
                for (Literal premise : observation.premises) {
                    List<Support> alternatives = new ArrayList<>();
                    for (PrimaryEvidence evidence : session.primary) for (ContextWitnessBundle.Atom atom : evidence.bundle.atoms)
                        if (matches(atom,evidence,premise,work)) add(alternatives,evidence.support.sources,evidence.support.depth,work);
                    if (alternatives.isEmpty()) return incomplete("witness-primary-premise-missing");
                    donors.add(alternatives);
                }
                List<Support> proofs = new ArrayList<>();
                for (Support producer : observation.producerSupports)
                    join(donors,0,producer.sources,producer.depth,maximumDepth,proofs,work);
                steps.add(new Step(session.replay,observation,proofs));
            }
            return steps.isEmpty() ? incomplete("no-primary-witness-applications") : new Result(true,"Observed primary witness steps reconstructed",steps);
        } catch (IllegalStateException incomplete) { return incomplete(incomplete.getMessage()); }
    }
}
