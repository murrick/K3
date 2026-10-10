/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detached branch projection of one exact external derived witness.
 * Does not delete native rules, enforce no-goods or authorize automatic branching.
 */
final class DmzStoredRestrictionProbe {
    static final class Node {
        final int index;
        final List<DmzProofWitnesses.Witness> retained;
        final int removed;
        Node(int index, List<DmzProofWitnesses.Witness> retained, int removed) {
            this.index = index; this.retained = Collections.unmodifiableList(retained); this.removed = removed;
        }
        boolean retracted() { return removed > 0 && retained.isEmpty(); }
    }
    final List<Node> nodes;
    final boolean eligible, truncated;
    final boolean complete = false;
    private DmzStoredRestrictionProbe(List<Node> nodes, boolean eligible, boolean truncated) {
        this.nodes = Collections.unmodifiableList(nodes); this.eligible = eligible; this.truncated = truncated;
    }
    static DmzStoredRestrictionProbe project(DmzAcceptanceProofGuard guard, DmzStoredProof proof,
            DmzProofWitnesses.Witness blocked, Mind target, int budget) throws Exception {
        if (budget <= 0) throw new IllegalArgumentException("Positive projection budget required");
        if (proof == null || blocked == null || !proof.witnesses.contains(blocked)
                || blocked.step < 0 || blocked.source.authority != DmzReplayProvenance.Authority.EXTERNAL
                || !guard.auditRoutes(proof, blocked, target).matched)
            return new DmzStoredRestrictionProbe(Collections.<Node>emptyList(), false, false);
        List<Node> nodes = new ArrayList<Node>();
        int remaining = budget;
        for (int index = 0; index < proof.graph.observed.nodes.size(); ++index) {
            if (remaining <= 0) return new DmzStoredRestrictionProbe(Collections.<Node>emptyList(), false, true);
            DmzProofWitnesses.Result result = DmzProofWitnesses.enumerate(proof.graph, index, remaining);
            if (result.truncated) return new DmzStoredRestrictionProbe(Collections.<Node>emptyList(), false, true);
            remaining -= result.created;
            List<DmzProofWitnesses.Witness> retained = new ArrayList<DmzProofWitnesses.Witness>();
            int removed = 0;
            for (DmzProofWitnesses.Witness witness : result.witnesses)
                if (contains(witness, blocked)) ++removed; else retained.add(witness);
            nodes.add(new Node(index, retained, removed));
        }
        return new DmzStoredRestrictionProbe(nodes, true, false);
    }
    private static boolean contains(DmzProofWitnesses.Witness witness, DmzProofWitnesses.Witness blocked) {
        if (same(witness, blocked)) return true;
        for (DmzProofWitnesses.Witness premise : witness.premises) if (contains(premise, blocked)) return true;
        return false;
    }
    private static boolean same(DmzProofWitnesses.Witness left, DmzProofWitnesses.Witness right) {
        if (left.graph != right.graph || left.node != right.node || left.step != right.step
                || left.source != right.source || left.premises.size() != right.premises.size()) return false;
        for (int i = 0; i < left.premises.size(); ++i) if (!same(left.premises.get(i), right.premises.get(i))) return false;
        return true;
    }
}
