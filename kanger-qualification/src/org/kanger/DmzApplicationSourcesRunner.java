/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.UUID;
import java.util.List;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Exact native IDs associate accepted replay alternatives with bounded applications. */
public final class DmzApplicationSourcesRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-app-sources-").toString());
        for (boolean reverse : new boolean[] {false, true}) run(reverse);
        generated();
        topology(true); topology(false);
        System.out.println("DMZ_APPLICATION_SOURCES_PASS checks=" + checks);
    }
    private static Mind root() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q); return q;
    }
    private static void run(boolean reverse) throws Exception {
        Mind q = root();
        IContextResults.Revision[] pins = {new IContextResults.Revision(UUID.randomUUID(), 1),
                new IContextResults.Revision(UUID.randomUUID(), 2), new IContextResults.Revision(UUID.randomUUID(), 3)};
        List<TerminalSupportCapture.ApplicationSources> detached;
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            for (int visit = 0; visit < 3; ++visit) {
                int i = reverse ? 2 - visit : visit;
                DmzReplayProvenance.replayRule(q, pins[i], 10 + i,
                        i == 0 ? DmzReplayProvenance.Authority.TARGET_Q : DmzReplayProvenance.Authority.EXTERNAL,
                        "!@x a(x) -> male(x);");
            }
            for (int visit = 0; visit < 3; ++visit) {
                int i = reverse ? 2 - visit : visit;
                DmzReplayProvenance.replayRule(q, pins[i], 20 + i,
                        i == 0 ? DmzReplayProvenance.Authority.TARGET_Q : DmzReplayProvenance.Authority.EXTERNAL,
                        "!a(John);");
            }
            require(q.compile("!anchor(Trigger);", null, false), "native inference operation");
            detached = capture.sourceSnapshot(journal);
            require(!detached.isEmpty(), "native application observed");
            DmzSourcedProofGraph graph = DmzSourcedProofGraph.build(detached);
            for (DmzSourcedProofGraph.Step step : graph.steps)
                require(step.available && step.ruleSources.size() == 3, "source alternatives reach graph step");
            for (TerminalSupportCapture.ApplicationSources sources : detached) {
                require(sources.ruleSources.size() == 3, "three canonical production occurrences");
                require(sources.supportSources.size() == 1 && sources.supportSources.get(0).size() == 3,
                        "three primary premise occurrences");
                for (DmzReplayProvenance.Binding binding : sources.ruleSources)
                    require(binding.sourceRule >= 10 && binding.sourceRule <= 12, "exact production IDs");
                for (DmzReplayProvenance.Binding binding : sources.supportSources.get(0))
                    require(binding.sourceRule >= 20 && binding.sourceRule <= 22, "exact premise IDs");
                int local = 0;
                for (DmzReplayProvenance.Binding binding : sources.ruleSources)
                    if (binding.authority == DmzReplayProvenance.Authority.TARGET_Q) ++local;
                require(local == 1, "Q alternative preserved");
            }
            Mind unrelated = root();
            require(unrelated.compile("!@x a(x) -> male(x); !a(John);", null, false), "unrelated inference");
            for (TerminalSupportCapture.ApplicationSources sources : capture.sourceSnapshot(journal))
                if (sources.application.mind != detached.get(0).application.mind)
                    require(sources.ruleSources.isEmpty(), "unrelated Mind cannot inherit source labels");
        }
        require(!detached.get(0).ruleSources.isEmpty(), "detached associations survive scope close");
        try { detached.get(0).supportSources.get(0).clear(); throw new AssertionError("mutable"); }
        catch (UnsupportedOperationException expected) { ++checks; }
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
    private static void generated() throws Exception {
        Mind q = root();
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 7);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            DmzReplayProvenance.replayRule(q, pin, 1, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x a(x) -> male(x);");
            DmzReplayProvenance.replayRule(q, pin, 2, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x male(x) -> adult(x);");
            DmzReplayProvenance.replayRule(q, pin, 3, DmzReplayProvenance.Authority.EXTERNAL, "!a(John);");
            require(q.compile("!anchor(Trigger);", null, false), "recursive inference operation");
            boolean found = false;
            for (TerminalSupportCapture.ApplicationSources sources : capture.sourceSnapshot(journal))
                if ("!adult(John);".equals(sources.application.conclusion)) {
                    found = true;
                    require(sources.ruleSources.size() == 1 && sources.ruleSources.get(0).sourceRule == 2,
                            "second-step production exact source");
                    require(!sources.application.supports.get(0).primary && sources.supportSources.get(0).isEmpty(),
                            "generated support requires upstream proof, never borrowed primary label");
                }
            require(found, "generated support application observed");
            DmzSourcedProofGraph graph = DmzSourcedProofGraph.build(capture.sourceSnapshot(journal));
            boolean adult = false;
            for (int i = 0; i < graph.observed.nodes.size(); ++i)
                if ("!adult(John);".equals(graph.observed.nodes.get(i).atom)) {
                    adult = true; require(graph.available.get(i), "source availability reaches recursive consequence");
                }
            require(adult, "recursive graph contains target");
            List<TerminalSupportCapture.ApplicationSources> incomplete = new java.util.ArrayList<>();
            for (TerminalSupportCapture.ApplicationSources sources : capture.sourceSnapshot(journal))
                incomplete.add(new TerminalSupportCapture.ApplicationSources(sources.application,
                        java.util.Collections.<DmzReplayProvenance.Binding>emptyList(), sources.supportSources));
            DmzSourcedProofGraph missing = DmzSourcedProofGraph.build(incomplete);
            for (DmzSourcedProofGraph.Step step : missing.steps)
                require(!step.available, "missing production source cannot ground a derivation");
        }
    }
    private static void topology(boolean cycle) throws Exception {
        Mind q = root();
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 9);
        String[] program = cycle ? new String[] {"!@x a(x) -> b(x);", "!@x b(x) -> a(x);", "!a(John);"}
                : new String[] {"!@x (a(x) && c(x)) -> b(x);", "!a(John);", "!c(John);"};
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            for (int i = 0; i < program.length; ++i)
                DmzReplayProvenance.replayRule(q, pin, i, DmzReplayProvenance.Authority.EXTERNAL, program[i]);
            require(q.compile("!anchor(Trigger);", null, false), "topology inference");
            List<TerminalSupportCapture.ApplicationSources> associations = capture.sourceSnapshot(journal);
            require(!associations.isEmpty(), "topology observed");
            DmzSourcedProofGraph graph = DmzSourcedProofGraph.build(associations);
            for (DmzSourcedProofGraph.Step step : graph.steps) require(step.available, "seeded topology available");
            List<TerminalSupportCapture.ApplicationSources> reduced = new java.util.ArrayList<>();
            for (TerminalSupportCapture.ApplicationSources sources : associations) {
                List<List<DmzReplayProvenance.Binding>> supports = new java.util.ArrayList<>(sources.supportSources);
                for (int i = 0; i < supports.size(); ++i)
                    if (cycle || i == supports.size() - 1)
                        supports.set(i, java.util.Collections.<DmzReplayProvenance.Binding>emptyList());
                reduced.add(new TerminalSupportCapture.ApplicationSources(sources.application, sources.ruleSources, supports));
            }
            DmzSourcedProofGraph missing = DmzSourcedProofGraph.build(reduced);
            for (DmzSourcedProofGraph.Step step : missing.steps)
                require(!step.available, cycle ? "unattributed cycle has no seed" : "AND needs every input source");
            try { missing.available.clear(); throw new AssertionError("mutable availability"); }
            catch (UnsupportedOperationException expected) { ++checks; }
        }
    }
}
