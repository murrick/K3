/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/** Diagnostic opposite-atom witness combinations; does not authorize native exclusions. */
final class DmzWitnessConflicts {
    enum Policy { SYMMETRIC_ALTERNATIVES, KEEP_LEFT_Q, KEEP_RIGHT_Q, Q_CONFLICT }
    static final class Combination {
        final DmzProofWitnesses.Witness left, right;
        final Policy policy;
        Combination(DmzProofWitnesses.Witness left, DmzProofWitnesses.Witness right) {
            this.left = left; this.right = right;
            boolean l = onlyQ(left), r = onlyQ(right);
            policy = l && r ? Policy.Q_CONFLICT : l ? Policy.KEEP_LEFT_Q
                    : r ? Policy.KEEP_RIGHT_Q : Policy.SYMMETRIC_ALTERNATIVES;
        }
        /** Exact symmetric diagnostic no-good within this graph's witness objects. */
        boolean matches(DmzProofWitnesses.Witness a, DmzProofWitnesses.Witness b) {
            return same(left, a) && same(right, b) || same(left, b) && same(right, a);
        }
        private static boolean same(DmzProofWitnesses.Witness a, DmzProofWitnesses.Witness b) {
            if (a.graph != b.graph || a.node != b.node || a.step != b.step || a.source != b.source
                    || a.premises.size() != b.premises.size()) return false;
            for (int i = 0; i < a.premises.size(); ++i)
                if (!same(a.premises.get(i), b.premises.get(i))) return false;
            return true;
        }
    }
    static final class Result {
        final List<Combination> combinations;
        final boolean truncated;
        Result(List<Combination> combinations, boolean truncated) {
            this.combinations = Collections.unmodifiableList(combinations); this.truncated = truncated;
        }
    }
    static Result collect(DmzSourcedProofGraph graph, int witnessBudget, int pairBudget) {
        if (witnessBudget <= 0 || pairBudget <= 0) throw new IllegalArgumentException("Positive budgets required");
        List<Combination> result = new ArrayList<Combination>();
        boolean truncated = false;
        long attempts = 0;
        for (int i = 0; i < graph.observed.nodes.size(); ++i) {
            DmzObservedProofGraph.Node left = graph.observed.nodes.get(i);
            if (!graph.available.get(i)) continue;
            for (int j = i + 1; j < graph.observed.nodes.size(); ++j) {
                DmzObservedProofGraph.Node right = graph.observed.nodes.get(j);
                if (!graph.available.get(j) || left.mind != right.mind || !opposite(left.ground, right.ground)) continue;
                DmzProofWitnesses.Result l = DmzProofWitnesses.enumerate(graph, i, witnessBudget);
                DmzProofWitnesses.Result r = DmzProofWitnesses.enumerate(graph, j, witnessBudget);
                truncated |= l.truncated || r.truncated;
                for (DmzProofWitnesses.Witness a : l.witnesses)
                    for (DmzProofWitnesses.Witness b : r.witnesses) {
                        if (++attempts > 32L * pairBudget || result.size() >= pairBudget)
                            return new Result(result, true);
                        java.util.Map<java.util.UUID, Long> revisions = new HashMap<java.util.UUID, Long>();
                        if (DmzProofWitnesses.compatible(a, revisions) && DmzProofWitnesses.compatible(b, revisions))
                            result.add(new Combination(a, b));
                    }
            }
        }
        return new Result(result, truncated);
    }
    private static boolean opposite(TerminalSupportCapture.Ground a, TerminalSupportCapture.Ground b) {
        if (a.sign == b.sign || !a.predicate.equals(b.predicate) || a.arguments.size() != b.arguments.size()) return false;
        for (int i = 0; i < a.arguments.size(); ++i)
            if (!a.arguments.get(i).semanticallyEquals(b.arguments.get(i))) return false;
        return true;
    }
    private static boolean onlyQ(DmzProofWitnesses.Witness witness) {
        if (witness.source.authority != DmzReplayProvenance.Authority.TARGET_Q) return false;
        for (DmzProofWitnesses.Witness premise : witness.premises) if (!onlyQ(premise)) return false;
        return true;
    }
}
