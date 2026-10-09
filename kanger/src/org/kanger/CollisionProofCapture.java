/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.ICause;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Experimental, opt-in capture for DMZ conflict attribution qualification.
 * Detached graphs preserve each existing ICause pair as a separate edge.
 * An edge is not a complete proof certificate: Core's cause representation
 * may contain partial, unresolved or cyclic supports. No attribution or
 * admissibility decision is made here.
 */
final class CollisionProofCapture implements AutoCloseable {
    private static final ThreadLocal<CollisionProofCapture> ACTIVE =
            new ThreadLocal<CollisionProofCapture>();

    static final class Edge {
        final int rule;
        final int donor;
        Edge(int rule, int donor) { this.rule = rule; this.donor = donor; }
    }

    static final class Node {
        final String origin;
        final String domain;
        final boolean generated;
        final List<Edge> causes;

        Node(String origin, String domain, boolean generated, List<Edge> causes) {
            this.origin = origin;
            this.domain = domain;
            this.generated = generated;
            this.causes = Collections.unmodifiableList(new ArrayList<Edge>(causes));
        }
    }

    static final class Graph {
        final int root;
        final List<Node> nodes;
        Graph(int root, List<Node> nodes) {
            this.root = root;
            this.nodes = Collections.unmodifiableList(new ArrayList<Node>(nodes));
        }
    }

    static final class Conflict {
        final Graph left;
        final Graph right;
        Conflict(Graph left, Graph right) { this.left = left; this.right = right; }
    }

    private final CollisionProofCapture previous;
    private final Thread owner;
    private final List<Conflict> conflicts = new ArrayList<Conflict>();
    private boolean closed;

    private CollisionProofCapture() {
        previous = ACTIVE.get();
        owner = Thread.currentThread();
        ACTIVE.set(this);
    }

    static CollisionProofCapture begin() { return new CollisionProofCapture(); }

    static void record(Mind mind, Rule left, Rule right) throws Exception {
        CollisionProofCapture capture = ACTIVE.get();
        if (capture == null) return;
        capture.conflicts.add(new Conflict(build(mind, left), build(mind, right)));
    }

    List<Conflict> snapshot() {
        return Collections.unmodifiableList(new ArrayList<Conflict>(conflicts));
    }

    private static Graph build(Mind mind, IRule root) throws Exception {
        Builder builder = new Builder(mind);
        return new Graph(builder.visit(root), builder.nodes);
    }

    private static final class Builder {
        final Mind mind;
        final Map<Long, Integer> indexes = new HashMap<Long, Integer>();
        final List<Node> nodes = new ArrayList<Node>();
        Builder(Mind mind) { this.mind = mind; }

        int visit(IRule rule) throws Exception {
            if (rule == null) return -1; // explicitly unresolved, never silently factual
            Integer existing = indexes.get(rule.getId());
            if (existing != null) return existing;
            int index = nodes.size();
            indexes.put(rule.getId(), index);
            nodes.add(null); // reserve before visiting causes: cycles stay finite
            List<Edge> edges = new ArrayList<Edge>();
            for (ICause cause : rule.getCauses()) {
                edges.add(new Edge(visit(cause.getRule(mind)), visit(cause.getDonor(mind))));
            }
            String origin = rule.getOrigin();
            nodes.set(index, new Node(origin == null ? "" : origin,
                    ((Rule) rule).getDomain().toString(mind), rule.isGenerated(), edges));
            return index;
        }
    }

    @Override public void close() {
        if (closed) return;
        if (owner != Thread.currentThread() || ACTIVE.get() != this) {
            throw new IllegalStateException("Collision capture must close on owner thread in stack order");
        }
        closed = true;
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
