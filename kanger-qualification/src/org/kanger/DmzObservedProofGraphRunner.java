/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.udf.UDF;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Bounded detached recursive graph qualification; no DMZ split authorization. */
public final class DmzObservedProofGraphRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-graph-").toString());
        chain(false); chain(true); cycle(); incomplete();
        System.out.println("DMZ_OBSERVED_PROOF_GRAPH_PASS checks=" + checks);
    }
    private static Mind root() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind mind = new Mind(user); user.setCurrentMind(mind); return mind;
    }
    private static void chain(boolean reverse) throws Exception {
        Mind q = root();
        String rules = "!@x (a(x) && c(x)) -> male(x); !@x b(x) -> male(x); !@x male(x) -> good(x);";
        String facts = reverse ? "!b(John); !c(Mary); !a(Mary); !c(John); !a(John);"
                : "!a(John); !c(John); !a(Mary); !c(Mary); !b(John);";
        List<TerminalSupportCapture.Application> applications;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile(rules + facts, null, false), "chain program");
            applications = capture.applicationSnapshot();
        }
        DmzObservedProofGraph graph = DmzObservedProofGraph.build(applications);
        int male = find(graph, "!male(John);");
        int good = find(graph, "!good(John);");
        int mary = find(graph, "!male(Mary);");
        require(graph.nodes.get(male).alternatives.size() == 2, "two independent male alternatives");
        require(!graph.nodes.get(male).primary && graph.nodes.get(male).grounded, "generated male recursively grounded");
        require(graph.nodes.get(good).grounded && !graph.nodes.get(good).primary, "two-step good grounded");
        require(graph.nodes.get(mary).alternatives.size() == 1, "Mary has one AND alternative");
        boolean and = false, or = false;
        for (int step : graph.nodes.get(male).alternatives) {
            List<Integer> premises = graph.steps.get(step).premises;
            if (premises.size() == 2) {
                require(contains(graph, premises, "!a(John);") && contains(graph, premises, "!c(John);"), "exact AND donors");
                and = true;
            } else {
                require(premises.size() == 1 && contains(graph, premises, "!b(John);"), "separate OR donor"); or = true;
            }
        }
        require(and && or, "AND and OR topology preserved");
        require(graph.nodes.get(good).alternatives.size() == 1, "downstream event deduplicated");
        require(graph.steps.get(graph.nodes.get(good).alternatives.get(0)).premises.contains(male), "recursive connection to male");
        require(graph.steps.size() < applications.size(), "repeated visits deduplicated");
        List<TerminalSupportCapture.Application> missing = new ArrayList<TerminalSupportCapture.Application>();
        List<TerminalSupportCapture.Application> isolated = new ArrayList<TerminalSupportCapture.Application>();
        for (TerminalSupportCapture.Application application : applications) {
            List<TerminalSupportCapture.Support> supports = new ArrayList<TerminalSupportCapture.Support>();
            for (TerminalSupportCapture.Support support : application.supports)
                supports.add(new TerminalSupportCapture.Support(support.premise, support.evidence,
                        support.donor, support.ground, support.primary && !support.donor.equals("!c(Mary);")));
            missing.add(new TerminalSupportCapture.Application(application.mind, application.rule,
                    application.ruleOrigin, application.conclusion, application.ground, application.bindings, supports));
            int mind = application.conclusion.equals("!good(John);") ? application.mind : application.mind + 100000;
            isolated.add(new TerminalSupportCapture.Application(mind, application.rule,
                    application.ruleOrigin, application.conclusion, application.ground, application.bindings, application.supports));
        }
        DmzObservedProofGraph missingGraph = DmzObservedProofGraph.build(missing);
        require(!missingGraph.nodes.get(find(missingGraph, "!good(Mary);")).grounded, "missing AND donor blocks downstream graph proof");
        require(missingGraph.nodes.get(find(missingGraph, "!good(John);")).grounded, "independent tuple unaffected");
        DmzObservedProofGraph isolatedGraph = DmzObservedProofGraph.build(isolated);
        require(!isolatedGraph.nodes.get(find(isolatedGraph, "!good(John);")).grounded, "different Mind observations never connect");
        require(Boolean.TRUE.equals(q.query("?good(John);", null, false)), "native John truth");
        require(Boolean.TRUE.equals(q.query("?good(Mary);", null, false)), "native Mary truth");
        try { graph.nodes.clear(); throw new AssertionError("mutable nodes"); }
        catch (UnsupportedOperationException expected) { ++checks; }
        try { graph.nodes.get(male).alternatives.clear(); throw new AssertionError("mutable alternatives"); }
        catch (UnsupportedOperationException expected) { ++checks; }
        System.out.println("DMZ_RECURSIVE_GRAPH_PASS reverse=" + reverse + " nodes=" + graph.nodes.size() + " steps=" + graph.steps.size());
    }
    private static void cycle() throws Exception {
        Mind q = root();
        List<TerminalSupportCapture.Application> applications;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile("!@x a(x) -> b(x); !@x b(x) -> a(x); !a(John);", null, false), "seeded cycle");
            applications = capture.applicationSnapshot();
        }
        DmzObservedProofGraph graph = DmzObservedProofGraph.build(applications);
        require(graph.nodes.get(find(graph, "!a(John);")).primary, "cycle primary seed retained");
        require(graph.nodes.get(find(graph, "!b(John);")).grounded, "seed grounds cycle");
        // Algorithm test: remove primary observations from the captured cyclic topology.
        // This does not modify native rules, transactions or the inference engine.
        List<TerminalSupportCapture.Application> unseeded = new ArrayList<TerminalSupportCapture.Application>();
        for (TerminalSupportCapture.Application application : applications) {
            List<TerminalSupportCapture.Support> supports = new ArrayList<TerminalSupportCapture.Support>();
            for (TerminalSupportCapture.Support support : application.supports)
                supports.add(new TerminalSupportCapture.Support(support.premise, support.evidence,
                        support.donor, support.ground, false));
            unseeded.add(new TerminalSupportCapture.Application(application.mind, application.rule,
                    application.ruleOrigin, application.conclusion, application.ground, application.bindings, supports));
        }
        DmzObservedProofGraph cycle = DmzObservedProofGraph.build(unseeded);
        require(cycle.steps.size() >= 2, "cyclic topology retained");
        for (DmzObservedProofGraph.Node node : cycle.nodes)
            require(!node.grounded, "cycle alone cannot ground a proof");
        System.out.println("DMZ_GRAPH_CYCLE_PASS");
    }
    private static void incomplete() throws Exception {
        Mind q = root();
        DmzObservedProofGraph graph;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile("!@x a(x) -> male(x+1); !@x male(x) -> good(x); !a(1);", null, false), "unsupported upstream program");
            graph = DmzObservedProofGraph.build(capture.applicationSnapshot());
        }
        int male = find(graph, "!male(2.0);");
        int good = find(graph, "!good(2.0);");
        require(!graph.nodes.get(male).primary && graph.nodes.get(male).alternatives.isEmpty(), "unsupported generated donor unresolved");
        require(!graph.nodes.get(male).grounded && !graph.nodes.get(good).grounded, "unknown upstream blocks graph proof");
        require(Boolean.TRUE.equals(q.query("?good(2);", null, false)), "native proof still succeeds");
        System.out.println("DMZ_GRAPH_UNRESOLVED_UPSTREAM_PASS");
    }
    private static int find(DmzObservedProofGraph graph, String atom) {
        for (int i = 0; i < graph.nodes.size(); ++i) if (graph.nodes.get(i).atom.equals(atom)) return i;
        throw new AssertionError("missing node " + atom);
    }
    private static boolean contains(DmzObservedProofGraph graph, List<Integer> nodes, String atom) {
        for (int node : nodes) if (graph.nodes.get(node).atom.equals(atom)) return true;
        return false;
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
