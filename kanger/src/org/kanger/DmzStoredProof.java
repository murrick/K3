/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Historical source-labelled witnesses for an observed stored native identity.
 * Availability is neither current revision validity nor observation completeness.
 */
final class DmzStoredProof {
    final TerminalSupportCapture.Materialization stored;
    final TerminalSupportCapture.Outcome outcome;
    final DmzSourcedProofGraph graph;
    final int root;
    final List<DmzProofWitnesses.Witness> witnesses;
    final List<String> gaps;
    final boolean truncated;
    final boolean complete = false;

    private DmzStoredProof(TerminalSupportCapture.Materialization stored,
            TerminalSupportCapture.Outcome outcome, DmzSourcedProofGraph graph, int root,
            List<DmzProofWitnesses.Witness> witnesses, List<String> gaps, boolean truncated) {
        this.stored = stored; this.outcome = outcome; this.graph = graph; this.root = root;
        this.witnesses = Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.Witness>(witnesses));
        this.gaps = Collections.unmodifiableList(new ArrayList<String>(gaps)); this.truncated = truncated;
    }

    static DmzStoredProof build(TerminalSupportCapture capture, DmzReplayProvenance journal,
            TerminalSupportCapture.Materialization stored, int witnessBudget) {
        if (witnessBudget <= 0) throw new IllegalArgumentException("Positive witness budget required");
        TerminalSupportCapture.Outcome outcome = capture.storedOutcome(stored);
        DmzSourcedProofGraph graph = DmzSourcedProofGraph.build(capture.storedSources(stored, journal));
        List<String> gaps = new ArrayList<String>();
        List<DmzProofWitnesses.Witness> witnesses = Collections.emptyList();
        int root = -1;
        boolean truncated = false;
        TerminalSupportCapture.Ground ground = stored.causes.nodes.get(stored.causes.root).ground;
        if (outcome != TerminalSupportCapture.Outcome.COMMITTED) gaps.add("stored-not-committed:" + outcome);
        if (stored.operation < 0) gaps.add("unknown-stored-operation");
        if (ground == null) gaps.add("unsupported-stored-ground");
        for (int i = 0; i < graph.observed.nodes.size(); ++i) {
            DmzObservedProofGraph.Node node = graph.observed.nodes.get(i);
            if (node.mind == stored.mind && node.ground.equivalent(ground)) { root = i; break; }
        }
        if (root < 0) gaps.add("missing-observed-stored-root");
        else {
            DmzProofWitnesses.Result result = DmzProofWitnesses.enumerate(graph, root, witnessBudget);
            witnesses = result.witnesses; truncated = result.truncated;
            if (witnesses.isEmpty()) gaps.add("missing-compatible-source-witness");
        }
        return new DmzStoredProof(stored, outcome, graph, root, witnesses, gaps, truncated);
    }
}
