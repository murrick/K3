/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detached AND/OR graph of bounded observations, not a final transaction certificate. */
final class DmzObservedProofGraph {
    static final class Node {
        final int mind;
        final String atom;
        final boolean primary;
        final boolean grounded;
        final List<Integer> alternatives;
        Node(MutableNode node) {
            mind = node.mind; atom = node.atom; primary = node.primary; grounded = node.grounded;
            alternatives = immutable(node.alternatives);
        }
    }
    static final class Step {
        final TerminalSupportCapture.Application application;
        final int conclusion;
        final List<Integer> premises;
        Step(TerminalSupportCapture.Application application, int conclusion, List<Integer> premises) {
            this.application = application; this.conclusion = conclusion; this.premises = immutable(premises);
        }
    }
    final List<Node> nodes;
    final List<Step> steps;

    private DmzObservedProofGraph(List<MutableNode> source, List<Step> steps) {
        List<Node> nodes = new ArrayList<Node>();
        for (MutableNode node : source) nodes.add(new Node(node));
        this.nodes = immutable(nodes); this.steps = immutable(steps);
    }

    static DmzObservedProofGraph build(List<TerminalSupportCapture.Application> applications) {
        List<MutableNode> nodes = new ArrayList<MutableNode>();
        List<Step> steps = new ArrayList<Step>();
        for (TerminalSupportCapture.Application application : applications) {
            int conclusion = node(nodes, application.mind, application.ground, application.conclusion);
            List<Integer> premises = new ArrayList<Integer>();
            for (TerminalSupportCapture.Support support : application.supports) {
                int premise = node(nodes, application.mind, support.ground, support.donor);
                nodes.get(premise).primary |= support.primary;
                premises.add(premise);
            }
            Step step = new Step(application, conclusion, premises);
            boolean duplicate = false;
            for (Step previous : steps) {
                if (sameApplication(previous, step)) { duplicate = true; break; }
            }
            if (!duplicate) {
                nodes.get(conclusion).alternatives.add(steps.size());
                steps.add(step);
            }
        }
        // Least fixed point: a cycle alone never supplies a proof.
        for (MutableNode node : nodes) node.grounded = node.primary;
        boolean changed;
        do {
            changed = false;
            for (Step step : steps) {
                if (nodes.get(step.conclusion).grounded) continue;
                boolean ready = !step.premises.isEmpty();
                for (int premise : step.premises) ready &= nodes.get(premise).grounded;
                if (ready) { nodes.get(step.conclusion).grounded = true; changed = true; }
            }
        } while (changed);
        return new DmzObservedProofGraph(nodes, steps);
    }

    private static boolean sameApplication(Step left, Step right) {
        if (left.conclusion != right.conclusion || left.application.rule != right.application.rule
                || !left.premises.equals(right.premises)
                || left.application.bindings.size() != right.application.bindings.size()) return false;
        for (int i = 0; i < left.application.bindings.size(); ++i) {
            TerminalSupportCapture.Binding a = left.application.bindings.get(i);
            TerminalSupportCapture.Binding b = right.application.bindings.get(i);
            if (a.variable != b.variable || !a.value.semanticallyEquals(b.value)) return false;
        }
        return true;
    }

    private static int node(List<MutableNode> nodes, int mind,
            TerminalSupportCapture.Ground ground, String atom) {
        if (ground == null) throw new IllegalArgumentException("Ground observation required");
        for (int i = 0; i < nodes.size(); ++i) {
            MutableNode node = nodes.get(i);
            if (node.mind == mind && node.ground.equivalent(ground)) return i;
        }
        nodes.add(new MutableNode(mind, ground, atom));
        return nodes.size() - 1;
    }
    private static <T> List<T> immutable(List<T> list) {
        return Collections.unmodifiableList(new ArrayList<T>(list));
    }
    private static final class MutableNode {
        final int mind;
        final TerminalSupportCapture.Ground ground;
        final String atom;
        boolean primary, grounded;
        final List<Integer> alternatives = new ArrayList<Integer>();
        MutableNode(int mind, TerminalSupportCapture.Ground ground, String atom) {
            this.mind = mind; this.ground = ground; this.atom = atom;
        }
    }
}
