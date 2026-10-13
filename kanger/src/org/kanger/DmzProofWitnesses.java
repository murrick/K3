/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded acyclic witnesses of the observed graph, never a completeness certificate. */
final class DmzProofWitnesses {
    /** Detached exact unit-chain no-good, portable across graph indices in the same source scope. */
    static final class NoGood {
        private final Witness selected;
        private final boolean typed;
        final int depth;
        final int workCost;
        private NoGood(Witness selected, int depth) { this(selected,depth,false); }
        private NoGood(Witness selected,int depth,boolean typed) {
            this.selected=selected;this.depth=depth;this.typed=typed;
            int cost=depth;
            if (typed) for (Witness current=selected;current.step>=0;current=current.premises.get(0)) {
                int size=current.graph.observed.steps.get(current.step).application.bindings.size();cost+=size*size;
            }
            workCost=cost;
        }
        static NoGood from(Witness witness) {
            if (witness == null || witness.step < 0 || witness.source.authority != DmzReplayProvenance.Authority.EXTERNAL)
                throw new IllegalArgumentException("External derived unit witness required");
            Witness current = witness;
            int depth = 0;
            while (current.step >= 0) {
                if (++depth >= 256 || current.premises.size() != 1)
                    throw new IllegalArgumentException("Bounded unit witness chain required");
                current = current.premises.get(0);
            }
            if (!current.premises.isEmpty() || !compatible(witness, new HashMap<UUID, Long>()))
                throw new IllegalArgumentException("Coherent primary-terminated witness required");
            return new NoGood(witness, depth + 1);
        }
        /** Exact typed substitution pattern; does not authorize native enforcement. */
        static NoGood fromTyped(Witness witness) {
            NoGood shape=from(witness);
            for (Witness current=witness;current.step>=0;current=current.premises.get(0)) {
                List<TerminalSupportCapture.Binding> bindings=current.graph.observed.steps.get(current.step).application.bindings;
                if (bindings.isEmpty() || bindings.size()>16) throw new IllegalArgumentException("Bounded typed substitution required");
                Set<Long> ids=new HashSet<Long>();
                for (TerminalSupportCapture.Binding binding:bindings)
                    if (binding.nativeVariable<0 || binding.value==null || !ids.add(binding.nativeVariable))
                        throw new IllegalArgumentException("Exact distinct native variable IDs required");
            }
            return new NoGood(witness,shape.depth,true);
        }
        /** Shape bound only; this is not native coverage or continuation permission. */
        boolean recursiveWithin(int maximumDepth) {
            return typed && depth>=3 && depth<=maximumDepth;
        }
        boolean matches(DmzSourcedProofGraph graph, int node, int step,
                DmzReplayProvenance.Binding source, List<Witness> premises) {
            return matches(selected, graph, node, step, source, premises,typed);
        }
        private static boolean matches(Witness expected, DmzSourcedProofGraph graph, int node, int step,
                DmzReplayProvenance.Binding source, List<Witness> premises,boolean typed) {
            if (expected.source != source || (expected.step < 0) != (step < 0)
                    || expected.premises.size() != premises.size()
                    || !expected.graph.observed.nodes.get(expected.node).ground.equivalent(graph.observed.nodes.get(node).ground))
                return false;
            if (typed && step>=0 && !sameBindings(expected.graph.observed.steps.get(expected.step).application.bindings,
                    graph.observed.steps.get(step).application.bindings)) return false;
            for (int i = 0; i < premises.size(); ++i) {
                Witness actual = premises.get(i);
                if (!matches(expected.premises.get(i), actual.graph, actual.node, actual.step, actual.source, actual.premises,typed))
                    return false;
            }
            return true;
        }
        static boolean sameBindings(List<TerminalSupportCapture.Binding> expected,List<TerminalSupportCapture.Binding> actual) {
            if (expected.size()!=actual.size() || actual.size()>16) return false;
            Set<Long> seen=new HashSet<Long>();
            for (TerminalSupportCapture.Binding binding:actual)
                if (binding.nativeVariable<0 || binding.value==null || !seen.add(binding.nativeVariable)) return false;
            for (TerminalSupportCapture.Binding wanted:expected) {
                boolean found=false;
                for (TerminalSupportCapture.Binding binding:actual)
                    found |= wanted.nativeVariable==binding.nativeVariable && wanted.value.semanticallyEquals(binding.value);
                if (!found) return false;
            }
            return true;
        }
    }
    static final class Witness {
        final DmzSourcedProofGraph graph;
        final int node, step; // step -1 denotes a primary source occurrence
        final DmzReplayProvenance.Binding source;
        final List<Witness> premises;
        Witness(DmzSourcedProofGraph graph, int node, int step, DmzReplayProvenance.Binding source, List<Witness> premises) {
            this.graph = graph; this.node = node; this.step = step; this.source = source;
            this.premises = Collections.unmodifiableList(new ArrayList<Witness>(premises));
        }
    }
    static final class Result {
        final List<Witness> witnesses;
        final boolean truncated;
        final int created;
        Result(List<Witness> witnesses, Budget budget) {
            this.witnesses = Collections.unmodifiableList(witnesses);
            truncated = budget.truncated; created = budget.created;
        }
    }
    private static final class Budget {
        final int limit;
        int created;
        long work;
        boolean truncated;
        Budget(int limit) { this.limit = limit; }
        boolean advance() {
            return advance(1);
        }
        boolean advance(int amount) {
            if ((work += amount) > 32L * limit) { truncated = true; return false; }
            return true;
        }
        boolean take() {
            if (created >= limit) { truncated = true; return false; }
            ++created; return true;
        }
    }
    static Result enumerate(DmzSourcedProofGraph graph, int node, int budget) {
        if (node < 0 || node >= graph.observed.nodes.size() || budget <= 0)
            throw new IllegalArgumentException("Valid target and positive witness budget required");
        Budget counter = new Budget(budget);
        return new Result(visit(graph, node, new HashSet<Integer>(), counter), counter);
    }
    private static List<Witness> visit(DmzSourcedProofGraph graph, int node, Set<Integer> path, Budget budget) {
        List<Witness> result = new ArrayList<Witness>();
        if (!budget.advance()) return result;
        if (path.contains(node)) return result;
        if (path.size() >= 256) { budget.truncated = true; return result; }
        path.add(node);
        try {
            Set<DmzReplayProvenance.Binding> seen = Collections.newSetFromMap(
                    new java.util.IdentityHashMap<DmzReplayProvenance.Binding, Boolean>());
            for (int i = 0; i < graph.observed.steps.size(); ++i) {
                DmzObservedProofGraph.Step step = graph.observed.steps.get(i);
                for (int p = 0; p < step.premises.size(); ++p)
                    if (step.premises.get(p) == node && step.application.supports.get(p).primary)
                        for (DmzReplayProvenance.Binding source : graph.steps.get(i).primarySources.get(p))
                            if (seen.add(source)) {
                                if (!budget.take()) return result;
                                result.add(new Witness(graph, node, -1, source, Collections.<Witness>emptyList()));
                            }
            }
            for (int index : graph.observed.nodes.get(node).alternatives) {
                DmzSourcedProofGraph.Step sourced = graph.steps.get(index);
                if (!sourced.available) continue;
                DmzObservedProofGraph.Step step = graph.observed.steps.get(index);
                List<List<Witness>> choices = new ArrayList<List<Witness>>();
                boolean ready = true;
                for (int premise : step.premises) {
                    List<Witness> alternatives = visit(graph, premise, path, budget);
                    if (alternatives.isEmpty()) { ready = false; break; }
                    choices.add(alternatives);
                }
                if (ready) for (DmzReplayProvenance.Binding source : sourced.ruleSources) {
                    combine(graph, node, index, source, choices, 0, new ArrayList<Witness>(), result, budget);
                    if (budget.truncated) return result;
                }
            }
            return result;
        } finally { path.remove(node); }
    }
    private static void combine(DmzSourcedProofGraph graph, int node, int step, DmzReplayProvenance.Binding source,
            List<List<Witness>> choices, int slot, List<Witness> selected, List<Witness> result, Budget budget) {
        if (!budget.advance()) return;
        if (slot == choices.size()) {
            if (selected.size() == 1 && selected.get(0).step < 0
                    && TerminalSupportCapture.SourcePair.excludes(graph.steps.get(step).excludedPairs,
                            source, selected.get(0).source)) return;
            for (NoGood noGood : graph.noGoods) {
                if (!budget.advance(noGood.workCost)) return;
                if (noGood.matches(graph, node, step, source, selected)) return;
            }
            Map<UUID, Long> revisions = new HashMap<UUID, Long>();
            revisions.put(source.context, source.revision);
            for (Witness premise : selected) if (!compatible(premise, revisions)) return;
            if (budget.take()) result.add(new Witness(graph, node, step, source, selected));
            return;
        }
        for (Witness candidate : choices.get(slot)) {
            selected.add(candidate);
            combine(graph, node, step, source, choices, slot + 1, selected, result, budget);
            selected.remove(selected.size() - 1);
            if (budget.truncated) return;
        }
    }
    static boolean compatible(Witness witness, Map<UUID, Long> revisions) {
        Long previous = revisions.put(witness.source.context, witness.source.revision);
        if (previous != null && previous.longValue() != witness.source.revision) return false;
        for (Witness premise : witness.premises) if (!compatible(premise, revisions)) return false;
        return true;
    }
}
