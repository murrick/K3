/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Source-associated observed AND/OR graph; availability is not proof completeness. */
final class DmzSourcedProofGraph {
    static final class Step {
        final int observedStep;
        final List<DmzReplayProvenance.Binding> ruleSources;
        final List<List<DmzReplayProvenance.Binding>> primarySources;
        final List<TerminalSupportCapture.SourcePair> excludedPairs;
        final boolean available;
        Step(int index, TerminalSupportCapture.ApplicationSources sources, boolean available) {
            observedStep = index; ruleSources = sources.ruleSources;
            primarySources = sources.supportSources; this.available = available;
            excludedPairs = sources.excludedPairs;
        }
    }
    final DmzObservedProofGraph observed;
    final List<Step> steps;
    final List<Boolean> available;
    final List<DmzProofWitnesses.NoGood> noGoods;
    private DmzSourcedProofGraph(DmzObservedProofGraph observed, List<Step> steps, List<Boolean> available) {
        this(observed, steps, available, Collections.<DmzProofWitnesses.NoGood>emptyList());
    }
    private DmzSourcedProofGraph(DmzObservedProofGraph observed, List<Step> steps, List<Boolean> available,
            List<DmzProofWitnesses.NoGood> noGoods) {
        this.observed = observed;
        this.steps = Collections.unmodifiableList(steps);
        this.available = Collections.unmodifiableList(available);
        this.noGoods = Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.NoGood>(noGoods));
    }
    /** A detached branch view; observed reachability and accepted source records remain historical. */
    DmzSourcedProofGraph restrict(List<DmzProofWitnesses.NoGood> additional) {
        if (additional == null || additional.contains(null)) throw new IllegalArgumentException("Exact no-goods required");
        List<DmzProofWitnesses.NoGood> combined = new ArrayList<DmzProofWitnesses.NoGood>(noGoods);
        if ((long) combined.size() + additional.size() > 10000L)
            throw new IllegalArgumentException("Detached no-good count exceeds budget");
        combined.addAll(additional);
        return new DmzSourcedProofGraph(observed, steps, available, combined);
    }
    static DmzSourcedProofGraph build(List<TerminalSupportCapture.ApplicationSources> associations) {
        List<TerminalSupportCapture.Application> applications = new ArrayList<TerminalSupportCapture.Application>();
        for (TerminalSupportCapture.ApplicationSources sources : associations) applications.add(sources.application);
        DmzObservedProofGraph graph = DmzObservedProofGraph.build(applications);
        List<TerminalSupportCapture.ApplicationSources> mapped = new ArrayList<TerminalSupportCapture.ApplicationSources>();
        for (DmzObservedProofGraph.Step step : graph.steps) {
            TerminalSupportCapture.ApplicationSources found = null;
            for (TerminalSupportCapture.ApplicationSources sources : associations)
                if (sources.application == step.application) { found = sources; break; }
            if (found == null || found.supportSources.size() != step.premises.size())
                throw new IllegalArgumentException("Source association does not match observed step");
            mapped.add(found);
        }
        List<Boolean> available = new ArrayList<Boolean>();
        for (int i = 0; i < graph.nodes.size(); ++i) available.add(false);
        for (int i = 0; i < graph.steps.size(); ++i) {
            DmzObservedProofGraph.Step step = graph.steps.get(i);
            TerminalSupportCapture.ApplicationSources sources = mapped.get(i);
            for (int p = 0; p < step.premises.size(); ++p)
                if (step.application.supports.get(p).primary && !sources.supportSources.get(p).isEmpty())
                    available.set(step.premises.get(p), true);
        }
        // Least fixed point requires an attributed primary seed and attributed rules.
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < graph.steps.size(); ++i) {
                DmzObservedProofGraph.Step step = graph.steps.get(i);
                if (!available.get(step.conclusion) && ready(step, mapped.get(i), available)) {
                    available.set(step.conclusion, true); changed = true;
                }
            }
        } while (changed);
        List<Step> steps = new ArrayList<Step>();
        for (int i = 0; i < graph.steps.size(); ++i)
            steps.add(new Step(i, mapped.get(i), ready(graph.steps.get(i), mapped.get(i), available)));
        List<DmzProofWitnesses.NoGood> noGoods = new ArrayList<DmzProofWitnesses.NoGood>();
        for (TerminalSupportCapture.ApplicationSources sources : associations)
            for (DmzProofWitnesses.NoGood pattern : sources.noGoods) if (!noGoods.contains(pattern)) noGoods.add(pattern);
        if (noGoods.size() > 10000) throw new IllegalArgumentException("Observed no-good count exceeds budget");
        return new DmzSourcedProofGraph(graph, steps, available, noGoods);
    }
    private static boolean ready(DmzObservedProofGraph.Step step,
            TerminalSupportCapture.ApplicationSources sources, List<Boolean> available) {
        if (sources.ruleSources.isEmpty() || step.premises.isEmpty()) return false;
        for (int premise : step.premises) if (!available.get(premise)) return false;
        return true;
    }
}
