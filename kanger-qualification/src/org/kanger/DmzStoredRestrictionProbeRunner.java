/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

public final class DmzStoredRestrictionProbeRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-restrict-probe-").toString());
        run(false, false, false); run(true, false, false); run(false, true, false); run(false, false, true);
        run(false, false, false, true); run(false, false, true, true);
        checkAliasRollback();
        System.out.println("DMZ_STORED_RESTRICTION_PROBE_PASS checks=" + checks);
    }
    private static void run(boolean alternative, boolean authoritative, boolean duplicate) throws Exception {
        run(alternative, authoritative, duplicate, false);
    }
    private static void run(boolean alternative, boolean authoritative, boolean duplicate, boolean primaryDuplicate) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, rules, 10,
                    authoritative ? DmzReplayProvenance.Authority.TARGET_Q : DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x source(x) -> derived(x);")), "native root production");
            accept(q, rules, 11, "!@x derived(x) -> tail(x);");
            if (alternative) {
                accept(q, rules, 12, "!@x source(x) -> middle(x);");
                accept(q, rules, 13, "!@x middle(x) -> derived(x);");
            }
            if (duplicate) DmzReplayProvenance.replayRule(q, rules, 14,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> derived(x);");
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            require(Boolean.TRUE.equals(guard.acceptAliases(fact, primaryDuplicate ? new long[] {20, 24} : new long[] {20},
                    DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "native input");
            TerminalSupportCapture.Materialization stored = null;
            for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot())
                if (observation.materialization.causes.nodes.get(observation.materialization.causes.root).ground.predicate.equals("derived")) stored = observation.materialization;
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored, 1000);
            DmzProofWitnesses.Witness blocked = null;
            for (DmzProofWitnesses.Witness witness : proof.witnesses) if (witness.source.sourceRule == 10 && witness.premises.get(0).source.sourceRule == 20) blocked = witness;
            String state = DmzObservationStateFingerprint.capture(q);
            DmzStoredRestrictionProbe projected = DmzStoredRestrictionProbe.project(guard, proof, blocked, q, 10000);
            if (authoritative) {
                require(!projected.eligible && projected.nodes.isEmpty(), "Q production cannot be selected for exclusion");
                require(state.equals(DmzObservationStateFingerprint.capture(q)), "rejected Q projection leaves native state unchanged");
                return;
            }
            require(projected.eligible && !projected.truncated && !projected.complete, "bounded projection eligible");
            boolean retained = alternative || duplicate || primaryDuplicate;
            int checked = 0;
            for (DmzStoredRestrictionProbe.Node node : projected.nodes) {
                String predicate = proof.graph.observed.nodes.get(node.index).ground.predicate;
                if (predicate.equals("derived") || predicate.equals("tail")) {
                    require(node.removed == 1 && node.retracted() == !retained,
                            "dependent route removed; independent route preserves " + predicate);
                    require(node.retained.size() == ((duplicate ? 2 : 1) * (primaryDuplicate ? 2 : 1) - 1 + (alternative ? 1 : 0)), "exact retained route count"); ++checked;
                }
                if (predicate.equals("source")) require(node.removed == 0 && !node.retained.isEmpty(), "primary survives");
            }
            require(checked == 2, "root and downstream continuation checked");
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "projection does not mutate native state");
            DmzStoredRetractionTransaction overlay = DmzStoredRetractionTransaction.begin(guard, proof, blocked, q, 10000);
            try {
                for (org.kanger.interfaces.IRule candidate : q.getRules()) {
                    org.kanger.units.Rule rule = (org.kanger.units.Rule) candidate;
                    if (candidate.isDeleted(q)) continue;
                    if (q.getRules().isGenerated(rule)) {
                        String predicate = rule.getDomain().getPredicate().getName(q);
                        if (predicate.equals("derived") || predicate.equals("tail"))
                            require(overlay.hasLiveRule(rule.getId()) == retained,
                                    "native overlay retracts last-dependent atom and preserves independent " + predicate);
                    } else require(overlay.hasLiveRule(rule.getId()), "native primary rule preserved");
                }
                require(state.equals(DmzObservationStateFingerprint.capture(q)), "native child tombstones leave parent unchanged");
                Boolean rootAnswer = overlay.query("?derived(1);");
                require(retained ? Boolean.TRUE.equals(rootAnswer) : rootAnswer == null,
                        "query cannot bypass excluded direct route; independent proof survives");
                Boolean tailAnswer = overlay.query("?tail(1);");
                require(retained ? Boolean.TRUE.equals(tailAnswer) : tailAnswer == null,
                        "dependent query respects selected derivation restriction");
                Boolean negative = overlay.query("?~derived(1);");
                require(retained ? Boolean.FALSE.equals(negative) : negative == null,
                        "blocked proof means unknown, never asserted opposite opinion");
                if (!retained) require(overlay.queryDeniedCount() > 0, "native query pair veto exercised");
                require(Boolean.TRUE.equals(overlay.accept(fact, 21, DmzReplayProvenance.Authority.EXTERNAL,
                        "!source(2);")), "native continuation accepts another substitution");
                require(overlay.hasLiveRule(stored.nativeRule) == retained,
                        "continued acceptance cannot restore blocked substitution");
                require((duplicate || primaryDuplicate) ? overlay.deniedCount() == 0 : overlay.deniedCount() > 0,
                        "native continuation retains a canonical alternative or exercises the veto");
                if (duplicate || primaryDuplicate) checkSources(capture, journal, stored.operation,
                        proof.graph.observed.nodes.get(proof.root).ground, true, duplicate, primaryDuplicate);
                int fresh = 0;
                for (TerminalSupportCapture.StoredObservation observation : capture.storedSnapshot()) {
                    TerminalSupportCapture.Materialization materialization = observation.materialization;
                    TerminalSupportCapture.Ground ground = materialization.causes.nodes.get(materialization.causes.root).ground;
                    if ((ground.predicate.equals("derived") || ground.predicate.equals("tail"))
                            && !ground.arguments.get(0).semanticallyEquals(proof.graph.observed.nodes.get(proof.root).ground.arguments.get(0))
                            && materialization.operation != stored.operation && overlay.hasLiveRule(materialization.nativeRule)) ++fresh;
                }
                require(fresh >= 2, "different substitution still produces result and downstream continuation");
                require(Boolean.TRUE.equals(overlay.query("?derived(2);"))
                        && Boolean.TRUE.equals(overlay.query("?tail(2);")), "other substitution queries unaffected");
                boolean unsupported = false;
                try { overlay.accept(fact, 22, DmzReplayProvenance.Authority.EXTERNAL, "?derived(1);"); }
                catch (IllegalArgumentException expected) { unsupported = true; }
                require(unsupported, "hypothesis/query path cannot bypass qualification boundary");
            } finally { overlay.close(); }
            if (duplicate || primaryDuplicate) checkSources(capture, journal, stored.operation,
                    proof.graph.observed.nodes.get(proof.root).ground, false, duplicate, primaryDuplicate);
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "rollback restores unchanged parent");
            if (duplicate && primaryDuplicate) checkAllPairs(q, rules, fact, proof, state);
            boolean closed = false;
            try { overlay.hasLiveRule(stored.nativeRule); } catch (IllegalStateException expected) { closed = true; }
            require(closed, "closed native overlay unreadable");
            require(Boolean.TRUE.equals(q.query("?derived(1);", null, false)), "closed restriction cannot affect parent queries");
            DmzStoredRestrictionProbe small = DmzStoredRestrictionProbe.project(guard, proof, blocked, q, 1);
            require(!small.eligible && small.truncated && small.nodes.isEmpty(), "budget exhaustion exposes no usable partial branch");
            DmzStoredProof other = DmzStoredProof.build(capture, journal, stored, 1000);
            require(!DmzStoredRestrictionProbe.project(guard, proof, other.witnesses.get(0), q, 10000).eligible, "foreign witness rejected");
            q.query("!anchor(2);", null, false);
            require(!DmzStoredRestrictionProbe.project(guard, proof, blocked, q, 10000).eligible, "stale boundary cannot project");
            if (duplicate) {
                int before = capture.applicationSnapshot().size();
                require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, fact, 23,
                        DmzReplayProvenance.Authority.EXTERNAL, "!source(3);")), "parent accepts after restriction closes");
                int restored = 0;
                java.util.List<TerminalSupportCapture.Application> applications = capture.applicationSnapshot();
                for (TerminalSupportCapture.ApplicationSources sources : capture.sourceSnapshot(journal)) {
                    if (applications.indexOf(sources.application) < before
                            || !sources.application.ground.predicate.equals("derived")) continue;
                    boolean first = false, second = false;
                    for (DmzReplayProvenance.Binding binding : sources.ruleSources) {
                        if (binding.sourceRule == 10) first = true;
                        if (binding.sourceRule == 14) second = true;
                    }
                    require(first && second, "closed child exclusion cannot filter new parent associations"); ++restored;
                }
                require(restored > 0, "unrestricted parent provenance exercised");
            }
        }
    }
    private static void checkSources(TerminalSupportCapture capture, DmzReplayProvenance journal,
            int originalOperation, TerminalSupportCapture.Ground selected, boolean open, boolean duplicate, boolean primaryDuplicate) {
        int filtered = 0, untouched = 0;
        for (TerminalSupportCapture.ProvisionalApplication sources : capture.provisionalSourceSnapshot()) {
            if (!sources.application.ground.predicate.equals("derived") || sources.application.supports.size() != 1
                    || !sources.application.supports.get(0).primary) continue;
            boolean one = sources.application.ground.arguments.get(0).semanticallyEquals(selected.arguments.get(0));
            boolean blocked = false, other = false;
            for (DmzReplayProvenance.SourceObservation observation : sources.ruleSources) {
                DmzReplayProvenance.Binding binding = observation.binding;
                if (binding.sourceRule == 10) blocked = true;
                if (binding.sourceRule == 14) other = true;
            }
            if (sources.application.mind == originalOperation) continue;
            boolean excluded = false;
            for (TerminalSupportCapture.SourcePair pair : sources.excludedPairs)
                if (pair.production.sourceRule == 10 && pair.primary.sourceRule == 20) excluded = true;
            if (one && blocked && other == duplicate && excluded) ++filtered;
            if (!one && blocked && other == duplicate && sources.excludedPairs.isEmpty()) ++untouched;
        }
        if (open) {
            require(filtered > 0, "continued same-ground proof records only the selected source-pair exclusion");
            require(untouched > 0, "other substitution retains production occurrences without a pair exclusion");
            checkPairWitnesses(capture, selected, duplicate, primaryDuplicate);
        } else require(filtered == 0 && untouched == 0, "rolled-back child applications cannot enter accepted provenance");
    }
    private static void checkPairWitnesses(TerminalSupportCapture capture, TerminalSupportCapture.Ground selected,
            boolean duplicate, boolean primaryDuplicate) {
        java.util.List<TerminalSupportCapture.ApplicationSources> associations =
                new java.util.ArrayList<TerminalSupportCapture.ApplicationSources>();
        for (TerminalSupportCapture.ProvisionalApplication observed : capture.provisionalSourceSnapshot()) {
            java.util.List<java.util.List<DmzReplayProvenance.Binding>> supports =
                    new java.util.ArrayList<java.util.List<DmzReplayProvenance.Binding>>();
            for (java.util.List<DmzReplayProvenance.SourceObservation> slot : observed.supportSources) supports.add(bindings(slot));
            associations.add(new TerminalSupportCapture.ApplicationSources(observed.application,
                    bindings(observed.ruleSources), supports, observed.excludedPairs));
        }
        DmzSourcedProofGraph graph = DmzSourcedProofGraph.build(associations);
        int checked = 0;
        for (int n = 0; n < graph.observed.nodes.size(); ++n) {
            if (!graph.observed.nodes.get(n).ground.equivalent(selected)) continue;
            boolean restricted = false;
            for (int step : graph.observed.nodes.get(n).alternatives)
                restricted |= !graph.steps.get(step).excludedPairs.isEmpty();
            if (!restricted) continue;
            DmzProofWitnesses.Result witnesses = DmzProofWitnesses.enumerate(graph, n, 1000);
            require(!witnesses.truncated && witnesses.witnesses.size()
                    == (duplicate ? 2 : 1) * (primaryDuplicate ? 2 : 1) - 1, "exact remaining source-pair witness count");
            for (DmzProofWitnesses.Witness witness : witnesses.witnesses)
                require(witness.source.sourceRule != 10 || witness.premises.get(0).source.sourceRule != 20,
                        "excluded production/primary pair cannot be reconstructed");
            ++checked;
        }
        require(checked > 0, "restricted branch witness enumeration exercised");
    }
    private static java.util.List<DmzReplayProvenance.Binding> bindings(
            java.util.List<DmzReplayProvenance.SourceObservation> observations) {
        java.util.List<DmzReplayProvenance.Binding> result = new java.util.ArrayList<DmzReplayProvenance.Binding>();
        for (DmzReplayProvenance.SourceObservation source : observations) result.add(source.binding);
        return result;
    }
    private static void checkAllPairs(Mind q, IContextResults.Revision rules, IContextResults.Revision fact,
            DmzStoredProof proof, String parentState) throws Exception {
        TechnicalMindTransaction child = TechnicalMindTransaction.beginIsolated(q);
        java.util.List<DmzTerminalRestriction> restrictions = new java.util.ArrayList<DmzTerminalRestriction>();
        try {
            for (org.kanger.interfaces.IRule candidate : q.getRules()) {
                if (candidate.isDeleted(q) || !q.getRules().isGenerated(candidate)) continue;
                org.kanger.units.Rule rule = (org.kanger.units.Rule) candidate;
                String predicate = rule.getDomain().getPredicate().getName(q);
                if (predicate.equals("derived") || predicate.equals("tail")) rule.setDeleted(true, child.mind());
            }
            for (DmzProofWitnesses.Witness witness : proof.witnesses) {
                DmzTerminalRestriction.validate(witness, q);
                restrictions.add(DmzTerminalRestriction.begin(child.mind(), witness));
            }
            require(restrictions.size() == 4, "all four independent direct pairs restricted");
            require(child.mind().query("?derived(1);", null, false) == null
                    && child.mind().query("?tail(1);", null, false) == null,
                    "exhausting source pairs makes native result and continuation unknown");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(child.mind(), fact, 25,
                    DmzReplayProvenance.Authority.EXTERNAL, "!source(2);")), "all-pair branch accepts another ground");
            int denied = 0;
            for (DmzTerminalRestriction restriction : restrictions) denied += restriction.deniedCount();
            require(denied > 0, "collective pair exhaustion reaches native terminal veto");
            require(Boolean.TRUE.equals(child.mind().query("?derived(2);", null, false)),
                    "all-pair restriction remains specific to its original ground");
        } finally {
            for (int i = restrictions.size() - 1; i >= 0; --i) restrictions.get(i).close();
            child.close();
        }
        require(parentState.equals(DmzObservationStateFingerprint.capture(q)), "all-pair qualification rolls back");
        boolean rejected = false;
        try { DmzReplayProvenance.acceptAliases(q, rules, new long[] {30, 30},
                DmzReplayProvenance.Authority.EXTERNAL, "!invalidAlias(9);"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && parentState.equals(DmzObservationStateFingerprint.capture(q)),
                "duplicate declared alias rejected before native mutation");
    }
    private static void checkAliasRollback() throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, fact, 40,
                    DmzReplayProvenance.Authority.EXTERNAL, "!~source(9);")), "alias rollback baseline");
            String state = DmzObservationStateFingerprint.capture(q);
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            require(!Boolean.TRUE.equals(guard.acceptAliases(fact, new long[] {41, 42},
                    DmzReplayProvenance.Authority.EXTERNAL, "!source(9);")), "contradicting multi-label input rejected");
            DmzReplayProvenance.Settlement settled = journal.settlementSnapshot();
            require(settled.accepted.size() == 1 && settled.discarded == 2 && settled.pending == 0,
                    "both aliases inherit the failed native input lifecycle");
            require(state.equals(DmzObservationStateFingerprint.capture(q)), "failed aliased input leaves Q unchanged");
        }
    }
    private static void accept(Mind q, IContextResults.Revision source, long id, String statement) throws Exception {
        require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, id, DmzReplayProvenance.Authority.EXTERNAL, statement)), "native production");
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
