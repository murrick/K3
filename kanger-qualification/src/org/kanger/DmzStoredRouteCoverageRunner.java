/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

public final class DmzStoredRouteCoverageRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-stored-routes-").toString());
        run(false); run(true);
        System.out.println("DMZ_STORED_ROUTE_COVERAGE_PASS checks=" + checks);
    }
    private static void run(boolean duplicate) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            DmzReplayProvenance.acceptRule(q, rules, 10, DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> derived(x);");
            DmzReplayProvenance.acceptRule(q, rules, 11, DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> middle(x);");
            DmzReplayProvenance.acceptRule(q, rules, 12, DmzReplayProvenance.Authority.EXTERNAL, "!@x middle(x) -> derived(x);");
            if (duplicate) DmzReplayProvenance.replayRule(q, rules, 13, DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> derived(x);");
            DmzUnaryStateDelta coverage = DmzUnaryStateDelta.beforeInput(q, 10000);
            DmzReplayProvenance.acceptRule(q, fact, 20, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
            DmzReplayProvenance.Binding incoming = journal.snapshot().get(journal.snapshot().size()-1);
            TerminalSupportCapture.Materialization stored = null;
            for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot())
                if (observation.materialization.causes.nodes.get(observation.materialization.causes.root).ground.predicate.equals("derived")) stored = observation.materialization;
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored, 1000);
            require(coverage.auditRoutes(q, incoming, proof.graph, proof.root).matched, "all routes and source occurrences covered");
            List<TerminalSupportCapture.ApplicationSources> associations = capture.storedSources(stored, journal);
            List<TerminalSupportCapture.ApplicationSources> omitted = new ArrayList<TerminalSupportCapture.ApplicationSources>();
            // Use typed conclusion/premise shape to omit the recursive final edge, independent of rendering.
            for (TerminalSupportCapture.ApplicationSources association : associations)
                if (!(association.application.ground.predicate.equals("derived")
                        && association.application.supports.size() == 1
                        && association.application.supports.get(0).ground.predicate.equals("middle"))) omitted.add(association);
            DmzSourcedProofGraph partial = DmzSourcedProofGraph.build(omitted);
            int root = root(partial);
            require(!DmzProofWitnesses.enumerate(partial, root, 1000).witnesses.isEmpty(), "direct witness survives omitted recursive route");
            require(!coverage.auditRoutes(q, incoming, partial, root).matched, "missing route detected despite surviving witness");
            List<TerminalSupportCapture.ApplicationSources> missingSource = new ArrayList<TerminalSupportCapture.ApplicationSources>();
            for (TerminalSupportCapture.ApplicationSources association : associations) {
                List<DmzReplayProvenance.Binding> labels = association.ruleSources;
                if (labels.size() > 1) labels = Collections.singletonList(labels.get(0));
                List<List<DmzReplayProvenance.Binding>> support = new ArrayList<List<DmzReplayProvenance.Binding>>(association.supportSources);
                if (!duplicate) for (int i = 0; i < support.size(); ++i)
                    if (!support.get(i).isEmpty()) support.set(i, Collections.<DmzReplayProvenance.Binding>emptyList());
                missingSource.add(new TerminalSupportCapture.ApplicationSources(association.application, labels, support));
            }
            DmzSourcedProofGraph unlabelled = DmzSourcedProofGraph.build(missingSource);
            require(!coverage.auditRoutes(q, incoming, unlabelled, root(unlabelled)).matched, "missing source alternative detected");
            require(coverage.audit(q, incoming.nativeRule).matched, "faulty graph leaves native state delta unchanged");
            require(!proof.complete, "fragment audit never certifies general completeness");
        }
    }
    private static int root(DmzSourcedProofGraph graph) {
        for (int i = 0; i < graph.observed.nodes.size(); ++i) if (graph.observed.nodes.get(i).ground.predicate.equals("derived")) return i;
        throw new AssertionError("missing derived root");
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
