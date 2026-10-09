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
        final boolean available;
        Step(int index, TerminalSupportCapture.ApplicationSources sources, boolean available) {
            observedStep = index; ruleSources = sources.ruleSources;
            primarySources = sources.supportSources; this.available = available;
        }
    }
    final DmzObservedProofGraph observed;
    final List<Step> steps;
    final List<Boolean> available;
    private DmzSourcedProofGraph(DmzObservedProofGraph observed, List<Step> steps, List<Boolean> available) {
        this.observed = observed;
        this.steps = Collections.unmodifiableList(steps);
        this.available = Collections.unmodifiableList(available);
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
        return new DmzSourcedProofGraph(graph, steps, available);
    }
    private static boolean ready(DmzObservedProofGraph.Step step,
            TerminalSupportCapture.ApplicationSources sources, List<Boolean> available) {
        if (sources.ruleSources.isEmpty() || step.premises.isEmpty()) return false;
        for (int premise : step.premises) if (!available.get(premise)) return false;
        return true;
    }
}
