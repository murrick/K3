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
    static final class Witness {
        final int node, step; // step -1 denotes a primary source occurrence
        final DmzReplayProvenance.Binding source;
        final List<Witness> premises;
        Witness(int node, int step, DmzReplayProvenance.Binding source, List<Witness> premises) {
            this.node = node; this.step = step; this.source = source;
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
            if (++work > 32L * limit) { truncated = true; return false; }
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
                                result.add(new Witness(node, -1, source, Collections.<Witness>emptyList()));
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
                    combine(node, index, source, choices, 0, new ArrayList<Witness>(), result, budget);
                    if (budget.truncated) return result;
                }
            }
            return result;
        } finally { path.remove(node); }
    }
    private static void combine(int node, int step, DmzReplayProvenance.Binding source,
            List<List<Witness>> choices, int slot, List<Witness> selected, List<Witness> result, Budget budget) {
        if (!budget.advance()) return;
        if (slot == choices.size()) {
            Map<UUID, Long> revisions = new HashMap<UUID, Long>();
            revisions.put(source.context, source.revision);
            for (Witness premise : selected) if (!compatible(premise, revisions)) return;
            if (budget.take()) result.add(new Witness(node, step, source, selected));
            return;
        }
        for (Witness candidate : choices.get(slot)) {
            selected.add(candidate);
            combine(node, step, source, choices, slot + 1, selected, result, budget);
            selected.remove(selected.size() - 1);
            if (budget.truncated) return;
        }
    }
    private static boolean compatible(Witness witness, Map<UUID, Long> revisions) {
        Long previous = revisions.put(witness.source.context, witness.source.revision);
        if (previous != null && previous.longValue() != witness.source.revision) return false;
        for (Witness premise : witness.premises) if (!compatible(premise, revisions)) return false;
        return true;
    }
}
