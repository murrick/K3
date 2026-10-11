/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.udf.UDF;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Qualification of detached collision causes before rejected compile rollback. */
public final class DmzCollisionProofCaptureRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-causes-").toString());
        for (boolean reverse : new boolean[] { false, true }) {
            derivedConflict(reverse);
        }
        characterizeAlternativeSupportGap();
        nestedCapture();
        System.out.println("DMZ_COLLISION_PROOF_CAPTURE_PASS checks=" + checks);
    }

    private static Mind root() throws Exception {
        User user = new User();
        new UDF().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return root;
    }

    private static void derivedConflict(boolean reverse) throws Exception {
        Mind q = root();
        require(q.compile("!@x a(x) -> middle(x); !@x middle(x) -> male(x); "
                + "!@x b(x) -> ~male(x);", null, false), "rules");
        Mind branch = Mind.ephemeralChild(q);
        List<CollisionProofCapture.Conflict> snapshot;
        try {
            require(branch.compile(reverse ? "!b(John);" : "!a(John);",
                    null, false), "first support");
            try (CollisionProofCapture capture = CollisionProofCapture.begin()) {
                require(!branch.compile(reverse ? "!a(John);" : "!b(John);",
                        null, false), "opposite supports rejected");
                snapshot = capture.snapshot();
                require(!snapshot.isEmpty(), "captured before rollback");
            }
            require(branch.compile("!b(Mary);", null, false), "another substitution still allowed");
            require(Boolean.FALSE.equals(branch.query("?male(Mary);", null, false)), "Mary unaffected");
        } finally {
            q.discardEphemeral(branch);
        }
        // All objects below remain readable after the entire branch is discarded.
        List<String> domains = new ArrayList<String>();
        List<String> origins = new ArrayList<String>();
        int edges = 0;
        for (CollisionProofCapture.Conflict conflict : snapshot) {
            for (CollisionProofCapture.Graph graph : new CollisionProofCapture.Graph[] {
                    conflict.left, conflict.right }) {
                require(graph.root >= 0 && graph.root < graph.nodes.size(), "valid root");
                require(graph.nodes.get(graph.root).domain.contains("John"), "concrete collision tuple");
                for (CollisionProofCapture.Node node : graph.nodes) {
                    require(node != null, "cycle slots finalized");
                    domains.add(node.domain);
                    origins.add(node.origin);
                    edges += node.causes.size();
                    for (CollisionProofCapture.Edge edge : node.causes) {
                        require(edge.rule >= -1 && edge.rule < graph.nodes.size(), "rule edge");
                        require(edge.donor >= -1 && edge.donor < graph.nodes.size(), "donor edge");
                    }
                }
                try {
                    graph.nodes.clear();
                    throw new AssertionError("mutable graph");
                } catch (UnsupportedOperationException expected) { ++checks; }
            }
        }
        require(origins.contains("!a(John);"), "positive ground input survived rollback");
        require(origins.contains("!b(John);"), "negative ground input survived rollback");
        require(domains.contains("!middle(John);") || domains.contains("?middle(John);"),
                "multi-step intermediate captured, including contraposition");
        require(origins.contains("!@x middle(x) -> male(x);"), "general rule captured separately");
        require(edges > 0, "cause edges retained");
        System.out.println("DETACHED_CONFLICT_PASS reverse=" + reverse
                + " collisions=" + snapshot.size() + " edges=" + edges);
    }

    /** This records an unmet DMZ requirement; it does not declare proof completeness. */
    private static void characterizeAlternativeSupportGap() throws Exception {
        Mind q = root();
        require(q.compile("!@x a(x) -> male(x); !@x c(x) -> male(x);",
                null, false), "alternative rules");
        require(q.compile("!a(John); !c(John);", null, false), "two positive supports");
        boolean a = false;
        boolean c = false;
        try (CollisionProofCapture capture = CollisionProofCapture.begin()) {
            require(!q.compile("!~male(John);", null, false), "alternative conflict");
            for (CollisionProofCapture.Conflict conflict : capture.snapshot()) {
                for (CollisionProofCapture.Graph graph : new CollisionProofCapture.Graph[] {
                        conflict.left, conflict.right }) {
                    for (CollisionProofCapture.Node node : graph.nodes) {
                        a |= "!a(John);".equals(node.origin);
                        c |= "!c(John);".equals(node.origin);
                    }
                }
            }
        }
        require(a || c, "at least one positive support exposed");
        require(!(a && c), "known alternative-support gap changed: reassess qualification");
        System.out.println("DMZ_OPEN_REQUIREMENT alternative_supports_complete=false"
                + " captured_a=" + a + " captured_c=" + c);
    }

    private static void nestedCapture() throws Exception {
        Mind q = root();
        require(q.compile("!male(John);", null, false), "nested fixture");
        try (CollisionProofCapture outer = CollisionProofCapture.begin()) {
            try (CollisionProofCapture inner = CollisionProofCapture.begin()) {
                require(!q.compile("!~male(John);", null, false), "inner collision");
                require(!inner.snapshot().isEmpty(), "inner owns event");
                require(outer.snapshot().isEmpty(), "outer suspended");
            }
            require(!q.compile("!~male(John);", null, false), "outer collision");
            require(!outer.snapshot().isEmpty(), "outer restored");
        }
        require(Boolean.TRUE.equals(q.query("?male(John);", null, false)), "default execution restored");
    }

    private static void require(boolean condition, String message) {
        ++checks;
        if (!condition) throw new AssertionError(message);
    }
}
