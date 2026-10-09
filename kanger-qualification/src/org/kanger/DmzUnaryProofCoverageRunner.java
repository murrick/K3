/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Native fixtures and detached omission controls for finite unary coverage. */
public final class DmzUnaryProofCoverageRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-unit-coverage-").toString());
        run("ordinary"); run("long-chain"); run("unseeded-cycle"); run("conjunction"); run("variables"); run("set"); run("anonymous"); run("mixed-pins");
        sharedPremise(false); sharedPremise(true);
        System.out.println("DMZ_UNARY_PROOF_COVERAGE_PASS checks=" + checks);
    }
    private static void run(String mode) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision a = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision c = new IContextResults.Revision(UUID.randomUUID(), 2);
        IContextResults.Revision b = new IContextResults.Revision(UUID.randomUUID(), 3);
        IContextResults.Revision duplicate = new IContextResults.Revision(UUID.randomUUID(), 4);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            replay(q, a, 10, "!@x a(x) -> male(x);");
            replay(q, c, 10, "!@x c(x) -> " + (mode.equals("long-chain") ? "u" : "male") + "(x);");
            if (mode.equals("long-chain")) {
                replay(q, c, 12, "!@x u(x) -> v(x);");
                replay(q, c, 13, "!@x v(x) -> male(x);");
            }
            replay(q, b, 10, "!@x b(x) -> ~male(x);");
            if (mode.equals("conjunction")) replay(q, c, 20, "!@x (u(x) && v(x)) -> w(x);");
            if (mode.equals("variables")) replay(q, c, 20, "!@x @y u(x) -> v(y);");
            if (mode.equals("unseeded-cycle")) {
                replay(q, c, 20, "!@x u(x) -> v(x);");
                replay(q, c, 21, "!@x v(x) -> u(x);");
            }
            if (mode.equals("set")) replay(q, c, 20, "!family([]);");
            if (mode.equals("anonymous")) require(q.compileLine("!@x u(x) -> v(x);", false,
                    new java.util.LinkedList<org.kanger.interfaces.ITerm>()) != null, "anonymous rule compile");
            replay(q, mode.equals("mixed-pins") ? new IContextResults.Revision(a.getContextId(), 2) : a, 11, "!a(John);");
            replay(q, c, 11, "!c(John);"); replay(q, b, 11, "!b(John);");
            if (mode.equals("ordinary")) replay(q, duplicate, 11, "!a(John);");
            try (TerminalSupportCapture applications = TerminalSupportCapture.begin(q);
                    CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                DmzUnaryProofCoverage coverage = DmzUnaryProofCoverage.beforeOperation(applications, q, 10000);
                DmzUnaryProofCoverage bounded = DmzUnaryProofCoverage.beforeOperation(applications, q, 30);
                require(!q.compile("!anchor(Trigger);", null, false), "native collision: " + mode);
                require(!collisions.snapshot().isEmpty(), "native event: " + mode);
                CollisionProofCapture.Conflict event = collisions.snapshot().get(0);
                DmzProvisionalCollisionProof proof = DmzProvisionalCollisionProof.build(event, 1000, 100);
                DmzUnaryProofCoverage.Result result = coverage.audit(proof, q);
                require(result.current && !result.complete, "current bounded proof is never general completeness: " + mode);
                if (mode.equals("ordinary")) {
                    require(result.covered && result.expectedSteps == 3, "complete finite unit steps and duplicate source choices");
                    DmzUnaryProofCoverage.Result shortResult = bounded.audit(proof, q);
                    require(shortResult.supported && shortResult.truncated && !shortResult.covered,
                            "post-inventory audit exhaustion explicit");
                    List<TerminalSupportCapture.ProvisionalApplication> reduced = new ArrayList<TerminalSupportCapture.ProvisionalApplication>();
                    for (TerminalSupportCapture.ProvisionalApplication observed : event.observations.applications) {
                        boolean fromC = false;
                        for (DmzReplayProvenance.SourceObservation source : observed.ruleSources)
                            fromC |= source.binding.context.equals(c.getContextId());
                        if (!fromC) reduced.add(observed);
                    }
                    DmzUnaryProofCoverage.Result missing = coverage.audit(rebuild(event, reduced), q);
                    require(missing.current && missing.supported && !missing.covered && has(missing, "missing-unit-application:"),
                            "available roots and remaining pair do not hide missing independent production");
                    reduced.clear();
                    for (TerminalSupportCapture.ProvisionalApplication observed : event.observations.applications) {
                        List<List<DmzReplayProvenance.SourceObservation>> supports = new ArrayList<List<DmzReplayProvenance.SourceObservation>>();
                        for (List<DmzReplayProvenance.SourceObservation> alternatives : observed.supportSources) {
                            List<DmzReplayProvenance.SourceObservation> kept = new ArrayList<DmzReplayProvenance.SourceObservation>();
                            for (DmzReplayProvenance.SourceObservation source : alternatives)
                                if (!source.binding.context.equals(duplicate.getContextId())) kept.add(source);
                            supports.add(kept);
                        }
                        reduced.add(new TerminalSupportCapture.ProvisionalApplication(observed.application, observed.outcome,
                                observed.ruleSources, supports));
                    }
                    missing = coverage.audit(rebuild(event, reduced), q);
                    require(missing.current && !missing.covered && has(missing, "missing-primary-alternative:"),
                            "missing canonical fact source choice detected independently of remaining witness");
                    replay(q, duplicate, 12, "!a(John);");
                    require(!coverage.audit(proof, q).current, "new source occurrence invalidates audited baseline");
                } else if (mode.equals("long-chain")) {
                    require(result.covered && result.expectedSteps >= 4,
                            "native capture includes relevant unit steps in longer chain: steps=" + result.expectedSteps + " " + result.gaps);
                } else if (mode.equals("unseeded-cycle")) {
                    require(result.covered && result.expectedSteps == 3,
                            "unseeded cycle contributes no invented unit application");
                } else {
                    String reason = mode.equals("conjunction") ? "unsupported-clause-arity:"
                            : mode.equals("variables") ? "unsupported-variable-pattern:"
                            : mode.equals("set") ? "unsupported-primary-value:"
                            : mode.equals("anonymous") ? "missing-baseline-source:" : "inconsistent-context-revisions";
                    require(!result.supported && !result.covered && has(result, reason),
                            "unsupported baseline fails closed for its actual boundary: " + mode + " " + result.gaps);
                }
            }
        }
    }
    /** Opposite consequences of two general rules share one unchanged primary fact. */
    private static void sharedPremise(boolean reverse) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision a = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision b = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            replay(q, reverse ? b : a, 10, reverse ? "!@x a(x) -> ~b(x);" : "!@x a(x) -> b(x);");
            replay(q, reverse ? a : b, 10, reverse ? "!@x a(x) -> b(x);" : "!@x a(x) -> ~b(x);");
            try (TerminalSupportCapture applications = TerminalSupportCapture.begin(q);
                    CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                require(q.compile("!anchor(Trigger);", null, false), "general rules alone accepted");
                require(collisions.snapshot().isEmpty(), "no concrete collision without shared premise");
                for (org.kanger.interfaces.IRule rule : q.getRules())
                    require(!q.getRules().isGenerated(rule), "fixture does not materialize a general resolvent");
                // The unrelated anchor is outside replay; do not include it in the audited baseline.
            }
        }
        q = new Mind(user); user.setCurrentMind(q);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            replay(q, reverse ? b : a, 10, reverse ? "!@x a(x) -> ~b(x);" : "!@x a(x) -> b(x);");
            replay(q, reverse ? a : b, 10, reverse ? "!@x a(x) -> b(x);" : "!@x a(x) -> ~b(x);");
            replay(q, fact, 11, "!a(John);");
            try (TerminalSupportCapture applications = TerminalSupportCapture.begin(q);
                    CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                DmzUnaryProofCoverage coverage = DmzUnaryProofCoverage.beforeOperation(applications, q, 10000);
                require(!q.compile("!anchor(Trigger);", null, false), "shared fact activates conflicting productions");
                require(!collisions.snapshot().isEmpty(), "shared-premise event captured");
                DmzProvisionalCollisionProof proof = DmzProvisionalCollisionProof.build(collisions.snapshot().get(0), 1000, 100);
                DmzUnaryProofCoverage.Result result = coverage.audit(proof, q);
                require(result.current && result.covered && !result.complete && result.expectedSteps == 2,
                        "both concrete applications audited, without general completeness claim: " + result.gaps);
                require(!proof.combinations.isEmpty(), "shared-premise witness pair available");
                for (DmzWitnessConflicts.Combination pair : proof.combinations) {
                    require(pair.policy == DmzWitnessConflicts.Policy.SYMMETRIC_ALTERNATIVES,
                            "shared external fact does not prioritize either production");
                    require(pair.left.step >= 0 && pair.right.step >= 0 && pair.left.source != pair.right.source,
                            "opposite consequences retain distinct production occurrences");
                    require(pair.left.premises.size() == 1 && pair.right.premises.size() == 1
                            && pair.left.premises.get(0).source == pair.right.premises.get(0).source
                            && pair.left.premises.get(0).source.context.equals(fact.getContextId()),
                            "same primary occurrence retained on both sides");
                    require(pair.matches(pair.right, pair.left), "combination remains symmetric with shared premise");
                }
            }
        }
    }
    private static DmzProvisionalCollisionProof rebuild(CollisionProofCapture.Conflict event,
            List<TerminalSupportCapture.ProvisionalApplication> applications) {
        TerminalSupportCapture.CollisionObservations original = event.observations;
        TerminalSupportCapture.CollisionObservations reduced = new TerminalSupportCapture.CollisionObservations(
                new ArrayList<TerminalSupportCapture.ProvisionalApplication>(applications), original.gaps,
                original.captureActive, original.scope, original.operation);
        return DmzProvisionalCollisionProof.build(new CollisionProofCapture.Conflict(event.left, event.right, reduced), 1000, 100);
    }
    private static void replay(Mind mind, IContextResults.Revision pin, long rule, String text) throws Exception {
        DmzReplayProvenance.replayRule(mind, pin, rule, DmzReplayProvenance.Authority.EXTERNAL, text);
    }
    private static boolean has(DmzUnaryProofCoverage.Result result, String prefix) {
        for (String gap : result.gaps) if (gap.startsWith(prefix)) return true;
        return false;
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
