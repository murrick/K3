/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Explicit source identity exists before native acceptance inference, not retroactively. */
public final class DmzSourcedAcceptanceRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-sourced-accept-").toString());
        run(false, true); run(true, true); run(true, false);
        System.out.println("DMZ_SOURCED_ACCEPTANCE_PASS checks=" + checks);
    }
    private static void run(boolean nested, boolean commit) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 2);
        IContextResults.Revision rejected = new IContextResults.Revision(UUID.randomUUID(), 3);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, rules, 10, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x source(x) -> derived(x);")), "sourced direct production");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, rules, 11, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x source(x) -> middle(x);")), "sourced upstream production");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, rules, 12, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x middle(x) -> derived(x);")), "sourced recursive production");
            require(journal.snapshot().size() == 3 && journal.settlementSnapshot().accepted.size() == 3,
                    "one binding per accepted input, no extra promotion occurrence");
            TechnicalMindTransaction outer = nested ? TechnicalMindTransaction.begin(q) : null;
            try {
                Mind target = nested ? outer.mind() : q;
                require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(target, fact, 20,
                        DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "sourced native fact");
                TerminalSupportCapture.Materialization stored = null;
                boolean eventSource = false;
                for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot()) {
                    CollisionProofCapture.Graph graph = observation.materialization.causes;
                    if (graph.nodes.get(graph.root).ground.predicate.equals("derived")) stored = observation.materialization;
                    for (CollisionProofCapture.Node node : graph.nodes) {
                        if (node.generated) require(node.sources.isEmpty(), "generated consequence has no primary label");
                        for (DmzReplayProvenance.SourceObservation source : node.sources)
                            if (source.binding.context.equals(fact.getContextId())) {
                                eventSource = true;
                                require(source.outcome == DmzReplayProvenance.Outcome.PENDING && source.binding.sourceRule == 20,
                                        "exact input identity captured before acceptance settles");
                            }
                    }
                }
                require(eventSource && stored != null, "source exists in storage-time graph");
                long factId = -1;
                for (DmzReplayProvenance.Binding binding : journal.snapshot())
                    if (binding.context.equals(fact.getContextId())) factId = binding.nativeRule;
                require(factId >= 0 && journal.snapshot().size() == 4, "single actual native primary binding");
                if (nested) {
                    require(journal.sources(q, factId).isEmpty(), "pending child attribution not accepted in Q");
                    require(DmzStoredProof.build(capture, journal, stored, 1000).witnesses.isEmpty(), "pending result has no accepted witnesses");
                    if (commit) require(outer.commit(), "outer commit"); else outer.rollback();
                }
                if (commit) {
                    require(journal.sources(q, factId).size() == 1, "accepted source moves with native input to Q");
                    DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored, 1000);
                    require(proof.witnesses.size() == 2 && !proof.complete, "direct and recursive witnesses need no late labels");
                    for (DmzProofWitnesses.Witness witness : proof.witnesses)
                        require(DmzStoredProofGuard.atCurrentState(capture, q, proof, witness).isCurrent(q),
                                "all original input bindings visible from Q after commit");
                    try (CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                        require(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, rejected, 30,
                                DmzReplayProvenance.Authority.EXTERNAL, "!~source(1);")), "contradictory sourced input rejected");
                        require(!collisions.snapshot().isEmpty(), "native rejected event");
                        boolean pendingRejected = false;
                        CollisionProofCapture.Conflict event = collisions.snapshot().get(0);
                        for (CollisionProofCapture.Graph graph : new CollisionProofCapture.Graph[]{event.left, event.right})
                            for (CollisionProofCapture.Node node : graph.nodes)
                                for (DmzReplayProvenance.SourceObservation source : node.sources)
                                    if (source.binding.context.equals(rejected.getContextId())) {
                                        pendingRejected = true;
                                        require(source.outcome == DmzReplayProvenance.Outcome.PENDING, "rejected identity existed at collision time");
                                    }
                        require(pendingRejected && journal.settlementSnapshot().discarded == 1,
                                "rejected input stays discarded, never projected as accepted");
                    }
                } else {
                    require(journal.sources(q, factId).isEmpty() && journal.settlementSnapshot().discarded == 1,
                            "outer rollback does not promote attribution into Q");
                    require(DmzStoredProof.build(capture, journal, stored, 1000).witnesses.isEmpty(), "discarded result stays unaccepted");
                }
            } finally { if (outer != null) outer.close(); }
        }
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
