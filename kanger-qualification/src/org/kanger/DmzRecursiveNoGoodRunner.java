/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Qualification of detached recursive no-goods, deliberately not native recursive enforcement. */
public final class DmzRecursiveNoGoodRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-recursive-no-good-").toString());
        for (boolean alternative : new boolean[] {false, true})
            for (boolean duplicate : new boolean[] {false, true}) run(alternative, duplicate);
        System.out.println("DMZ_RECURSIVE_NO_GOOD_PASS checks=" + checks);
    }
    private static void run(boolean alternative, boolean duplicate) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, rules, 10, "!@x source(x) -> middle(x);");
            if (alternative) {
                accept(q, rules, 11, "!@x source(x) -> other(x);");
                accept(q, rules, 12, "!@x other(x) -> middle(x);");
            }
            accept(q, rules, 13, "!@x middle(x) -> derived(x);");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, rules, 14,
                    DmzReplayProvenance.Authority.TARGET_Q, "!@x derived(x) -> tail(x);")), "authoritative continuation");
            accept(q, rules, 16, "!@x independent(x) -> middle(x);");
            if (duplicate) DmzReplayProvenance.replayRule(q, rules, 15,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x middle(x) -> derived(x);");
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            require(Boolean.TRUE.equals(guard.accept(fact, 20, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "native input");
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored(capture, "derived"), 10000);
            int original = (alternative ? 2 : 1) * (duplicate ? 2 : 1);
            require(proof.witnesses.size() == original && !proof.truncated, "native root alternatives");
            DmzProofWitnesses.Witness blocked = null;
            for (DmzProofWitnesses.Witness witness : proof.witnesses)
                if (witness.source.sourceRule == 13 && witness.premises.get(0).source.sourceRule == 10) blocked = witness;
            require(blocked != null && blocked.premises.get(0).step >= 0, "selected chain has a generated premise");
            String state = DmzObservationStateFingerprint.capture(q);
            DmzStoredRestrictionProbe projection = DmzStoredRestrictionProbe.project(guard, proof, blocked, q, 10000);
            require(projection.eligible && !projection.truncated, "recursive exact projection audited");
            DmzProofWitnesses.NoGood noGood = DmzProofWitnesses.NoGood.from(blocked);
            DmzSourcedProofGraph branch = projection.graph;
            require(branch != null && branch.noGoods.size() == 1, "qualified projection exposes its detached branch graph");
            verify(branch, original - 1, alternative ? 2 : 1);
            require(DmzProofWitnesses.enumerate(proof.graph, proof.root, 10000).witnesses.size() == original,
                    "original accepted graph unchanged");
            for (DmzStoredRestrictionProbe.Node node : projection.nodes) {
                String predicate = proof.graph.observed.nodes.get(node.index).ground.predicate;
                if (predicate.equals("derived") || predicate.equals("tail"))
                    require(node.removed == 1 && node.retained.size() == original - 1,
                            "portable restriction agrees with audited dependent projection");
                if (predicate.equals("middle")) require(node.removed == 0, "blocking entire chain never removes its upstream fact");
            }
            List<TerminalSupportCapture.ApplicationSources> rebuiltInputs = new ArrayList<TerminalSupportCapture.ApplicationSources>();
            for (TerminalSupportCapture.ApplicationSources sources : capture.storedSources(proof.stored, journal)) {
                TerminalSupportCapture.Application a = sources.application;
                TerminalSupportCapture.Application copy = new TerminalSupportCapture.Application(a.mind + 10000, a.rule,
                        a.ruleOrigin, a.conclusion, a.ground, a.bindings, a.supports, a.sourceCandidates);
                rebuiltInputs.add(new TerminalSupportCapture.ApplicationSources(copy, sources.ruleSources,
                        sources.supportSources, sources.excludedPairs));
            }
            Collections.reverse(rebuiltInputs);
            DmzSourcedProofGraph rebuilt = DmzSourcedProofGraph.build(rebuiltInputs);
            verify(rebuilt.restrict(Collections.singletonList(noGood)), original - 1, alternative ? 2 : 1);
            require(rebuilt.observed.nodes.get(0).mind != proof.graph.observed.nodes.get(0).mind,
                    "restriction crosses rebuilt detached graph indices and Mind identifiers");
            List<DmzProofWitnesses.NoGood> all = new ArrayList<DmzProofWitnesses.NoGood>();
            for (DmzProofWitnesses.Witness witness : proof.witnesses) all.add(DmzProofWitnesses.NoGood.from(witness));
            verify(proof.graph.restrict(all), 0, alternative ? 2 : 1);
            require(DmzProofWitnesses.enumerate(branch, proof.root, 1).truncated, "small witness budget exposes truncation");
            List<DmzProofWitnesses.NoGood> many = Collections.nCopies(10000, noGood);
            require(DmzProofWitnesses.enumerate(proof.graph.restrict(many), proof.root, 10).truncated,
                    "no-good matching work participates in enumeration budget");
            boolean refused = false;
            try { DmzStoredRetractionTransaction.begin(guard, proof, blocked, q, 10000); }
            catch (IllegalStateException expected) { refused = true; }
            require(refused && state.equals(DmzObservationStateFingerprint.capture(q)),
                    "native recursive restriction remains refused without mutating Q");
            boolean primaryRefused = false;
            try { DmzProofWitnesses.NoGood.from(blocked.premises.get(0).premises.get(0)); }
            catch (IllegalArgumentException expected) { primaryRefused = true; }
            require(primaryRefused, "primary facts cannot be selected as derived no-goods");
            boolean qRefused = false;
            for (int n = 0; n < proof.graph.observed.nodes.size(); ++n)
                if (proof.graph.observed.nodes.get(n).ground.predicate.equals("tail")) {
                    DmzProofWitnesses.Witness qWitness = DmzProofWitnesses.enumerate(proof.graph, n, 10000).witnesses.get(0);
                    try { DmzProofWitnesses.NoGood.from(qWitness); }
                    catch (IllegalArgumentException expected) { qRefused = true; }
                }
            require(qRefused, "Q production cannot be the selected root of a no-good");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, fact, 21,
                    DmzReplayProvenance.Authority.EXTERNAL, "!source(2);")), "native different substitution");
            TerminalSupportCapture.Materialization fresh = stored(capture, "derived");
            DmzStoredProof other = DmzStoredProof.build(capture, journal, fresh, 10000);
            require(!other.graph.observed.nodes.get(other.root).ground.equivalent(proof.graph.observed.nodes.get(proof.root).ground),
                    "different native result selected");
            require(DmzProofWitnesses.enumerate(other.graph.restrict(Collections.singletonList(noGood)), other.root, 10000)
                    .witnesses.size() == other.witnesses.size(), "different substitution remains unrestricted");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, fact, 22,
                    DmzReplayProvenance.Authority.EXTERNAL, "!independent(1);")), "new independent primary support accepted");
            // Detached algorithm fixture merges accepted operations by their common target atom.
            // This is not a native operation-coverage certificate.
            List<TerminalSupportCapture.ApplicationSources> extended = new ArrayList<TerminalSupportCapture.ApplicationSources>();
            for (TerminalSupportCapture.ApplicationSources sources : capture.sourceSnapshot(journal)) {
                TerminalSupportCapture.Application a = sources.application;
                TerminalSupportCapture.Application copy = new TerminalSupportCapture.Application(0, a.rule,
                        a.ruleOrigin, a.conclusion, a.ground, a.bindings, a.supports, a.sourceCandidates);
                extended.add(new TerminalSupportCapture.ApplicationSources(copy, sources.ruleSources,
                        sources.supportSources, sources.excludedPairs));
            }
            DmzSourcedProofGraph renewed = DmzSourcedProofGraph.build(extended).restrict(Collections.singletonList(noGood));
            verifyAt(renewed, proof.graph.observed.nodes.get(proof.root).ground,
                    original - 1 + (duplicate ? 2 : 1), (alternative ? 2 : 1) + 1);
        }
    }
    private static void verify(DmzSourcedProofGraph graph, int roots, int middle) {
        verifyAt(graph, null, roots, middle);
    }
    private static void verifyAt(DmzSourcedProofGraph graph, TerminalSupportCapture.Ground selected, int roots, int middle) {
        int found = 0;
        for (int n = 0; n < graph.observed.nodes.size(); ++n) {
            String predicate = graph.observed.nodes.get(n).ground.predicate;
            if (selected != null && !graph.observed.nodes.get(n).ground.arguments.get(0).semanticallyEquals(selected.arguments.get(0))) continue;
            if (!predicate.equals("derived") && !predicate.equals("tail") && !predicate.equals("middle")) continue;
            DmzProofWitnesses.Result witnesses = DmzProofWitnesses.enumerate(graph, n, 10000);
            require(!witnesses.truncated && witnesses.witnesses.size() == (predicate.equals("middle") ? middle : roots),
                    "exact surviving proofs for " + predicate); ++found;
        }
        require(found == 3, "root, downstream and preserved upstream nodes checked");
    }
    private static TerminalSupportCapture.Materialization stored(TerminalSupportCapture capture, String predicate) {
        TerminalSupportCapture.Materialization result = null;
        for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot())
            if (observation.materialization.causes.nodes.get(observation.materialization.causes.root).ground.predicate.equals(predicate))
                result = observation.materialization;
        if (result == null) throw new AssertionError("Missing native materialization " + predicate);
        return result;
    }
    private static void accept(Mind q, IContextResults.Revision source, long id, String statement) throws Exception {
        require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, id,
                DmzReplayProvenance.Authority.EXTERNAL, statement)), "native production");
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
