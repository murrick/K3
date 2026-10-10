/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Event-owned diagnostic witness graph; pending sources remain provisional. */
final class DmzProvisionalCollisionProof {
    final CollisionProofCapture.Conflict event;
    final DmzSourcedProofGraph graph;
    final List<DmzWitnessConflicts.Combination> combinations;
    final boolean truncated;
    final boolean rootsAvailable;
    final boolean complete = false; // observation coverage is not certified
    private DmzProvisionalCollisionProof(CollisionProofCapture.Conflict event, DmzSourcedProofGraph graph,
            List<DmzWitnessConflicts.Combination> combinations, boolean truncated) {
        this.event = event; this.graph = graph;
        this.combinations = Collections.unmodifiableList(combinations); this.truncated = truncated;
        TerminalSupportCapture.Ground left = event.left.nodes.get(event.left.root).ground;
        TerminalSupportCapture.Ground right = event.right.nodes.get(event.right.root).ground;
        boolean l = false, r = false;
        for (int i = 0; i < graph.observed.nodes.size(); ++i) if (graph.available.get(i)) {
            l |= graph.observed.nodes.get(i).ground.equivalent(left);
            r |= graph.observed.nodes.get(i).ground.equivalent(right);
        }
        rootsAvailable = l && r;
    }
    static DmzProvisionalCollisionProof build(CollisionProofCapture.Conflict event, int witnessBudget, int pairBudget) {
        if (!event.observations.captureActive || event.observations.operation < 0)
            throw new IllegalArgumentException("Observed collision operation required");
        List<TerminalSupportCapture.ApplicationSources> associations = new ArrayList<>();
        for (TerminalSupportCapture.ProvisionalApplication observed : event.observations.applications) {
            TerminalSupportCapture.Application a = observed.application;
            // Only observed frames in the same operation may share semantic nodes.
            TerminalSupportCapture.Application copy = new TerminalSupportCapture.Application(event.observations.operation,
                    a.rule, a.ruleOrigin, a.conclusion, a.ground, a.bindings, a.supports, a.sourceCandidates);
            List<List<DmzReplayProvenance.Binding>> supports = new ArrayList<>();
            for (List<DmzReplayProvenance.SourceObservation> sources : observed.supportSources) supports.add(bindings(sources));
            associations.add(new TerminalSupportCapture.ApplicationSources(copy, bindings(observed.ruleSources), supports,
                    observed.excludedPairs, observed.noGoods));
        }
        DmzSourcedProofGraph graph = DmzSourcedProofGraph.build(associations);
        DmzWitnessConflicts.Result pairs = DmzWitnessConflicts.collect(graph, witnessBudget, pairBudget);
        TerminalSupportCapture.Ground left = event.left.nodes.get(event.left.root).ground;
        TerminalSupportCapture.Ground right = event.right.nodes.get(event.right.root).ground;
        List<DmzWitnessConflicts.Combination> matching = new ArrayList<>();
        for (DmzWitnessConflicts.Combination pair : pairs.combinations) {
            TerminalSupportCapture.Ground a = graph.observed.nodes.get(pair.left.node).ground;
            TerminalSupportCapture.Ground b = graph.observed.nodes.get(pair.right.node).ground;
            if (a.equivalent(left) && b.equivalent(right) || a.equivalent(right) && b.equivalent(left)) matching.add(pair);
        }
        return new DmzProvisionalCollisionProof(event, graph, matching, pairs.truncated);
    }
    private static List<DmzReplayProvenance.Binding> bindings(List<DmzReplayProvenance.SourceObservation> observations) {
        List<DmzReplayProvenance.Binding> result = new ArrayList<>();
        for (DmzReplayProvenance.SourceObservation source : observations)
            if (source.outcome == DmzReplayProvenance.Outcome.ACCEPTED || source.outcome == DmzReplayProvenance.Outcome.PENDING)
                result.add(source.binding);
        return Collections.unmodifiableList(result);
    }
}
