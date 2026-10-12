/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Native fixtures and detached omission controls for finite unary coverage. */
public final class DmzGroundCollisionRestrictionRunner {
    private static int checks;
    private static String shape;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-unit-coverage-").toString());
        run("ordinary"); run("permuted"); run("constants"); run("sets"); run("long-chain"); run("unseeded-cycle"); run("conjunction"); run("variables"); run("anonymous"); run("mixed-pins");
        sharedPremise(false); sharedPremise(true);
        System.out.println("DMZ_GROUND_COLLISION_RESTRICTION_PASS checks=" + checks); System.exit(0);
    }
    private static void run(String mode) throws Exception {
        shape=mode;
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision a = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision c = new IContextResults.Revision(UUID.randomUUID(), 2);
        IContextResults.Revision b = new IContextResults.Revision(UUID.randomUUID(), 3);
        IContextResults.Revision duplicate = new IContextResults.Revision(UUID.randomUUID(), 4);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            replay(q, a, 10, "!@x @y a(x,y) -> male(x,y);");
            replay(q, c, 10, "!@x @y c(x,y) -> " + (mode.equals("long-chain") ? "u" : "male") + "(x,y);");
            if (mode.equals("long-chain")) {
                replay(q, c, 12, "!@x @y u(x,y) -> v(x,y);");
                replay(q, c, 13, "!@x @y v(x,y) -> male(x,y);");
            }
            replay(q, b, 10, "!@x @y b(x,y) -> ~male(x,y);");
            if (mode.equals("conjunction")) replay(q, c, 20, "!@x @y (u(x,y) && v(x,y)) -> w(x,y);");
            if (mode.equals("variables")) replay(q, c, 20, "!@x @y u(x,y) -> v(y);");
            if (mode.equals("unseeded-cycle")) {
                replay(q, c, 20, "!@x @y u(x,y) -> v(x,y);");
                replay(q, c, 21, "!@x @y v(x,y) -> u(x,y);");
            }
            if (mode.equals("set")) replay(q, c, 20, "!family([]);");
            if (mode.equals("anonymous")) require(q.compileLine("!@x @y u(x,y) -> v(x,y);", false,
                    new java.util.LinkedList<org.kanger.interfaces.ITerm>()) != null, "anonymous rule compile");
            replay(q, mode.equals("mixed-pins") ? new IContextResults.Revision(a.getContextId(), 2) : a, 11, "!a(John,Tom);");
            replay(q, c, 11, "!c(John,Tom);"); replay(q, b, 11, "!b(John,Tom);");
            if ((mode.equals("ordinary") || mode.equals("permuted") || mode.equals("constants") || mode.equals("sets"))) {
                replay(q, duplicate, 11, "!a(John,Tom);");
                replay(q, duplicate, 13, "!@x @y a(x,y) -> male(x,y);");
            }
            try (TerminalSupportCapture applications = TerminalSupportCapture.begin(q);
                    CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                DmzGroundProofCoverage coverage = DmzGroundProofCoverage.beforeOperation(applications, q, 10000);
                DmzGroundCollisionRestriction restriction=DmzGroundCollisionRestriction.beforeOperation(applications,q,10000);
                DmzGroundProofCoverage bounded = DmzGroundProofCoverage.beforeOperation(applications, q, 1);
                require(!q.compile("!anchor(Trigger);", null, false), "native collision: " + mode);
                require(!collisions.snapshot().isEmpty(), "native event: " + mode);
                CollisionProofCapture.Conflict event = collisions.snapshot().get(0);
                DmzProvisionalCollisionProof proof = DmzProvisionalCollisionProof.build(event, 1000, 100);
                DmzGroundProofCoverage.Result result = coverage.audit(proof, q);
                require(result.current && !result.complete, "current bounded proof is never general completeness: " + mode);
                if ((mode.equals("ordinary") || mode.equals("permuted") || mode.equals("constants") || mode.equals("sets"))) {
                    require(result.covered && result.expectedSteps == 3, "complete finite unit steps and duplicate source choices: " + result.gaps + " steps=" + result.expectedSteps);
                    DmzProofWitnesses.Witness selected=null;
                    for(DmzWitnessConflicts.Combination pair:proof.combinations) {
                        if(pair.left.source.context.equals(a.getContextId()))selected=pair.left;
                        else if(pair.right.source.context.equals(a.getContextId()))selected=pair.right;
                        if(selected!=null)break;
                    }
                    require(selected!=null,"native original production source selectable");
                    String state=DmzObservationStateFingerprint.capture(q);
                    DmzGroundCollisionRestriction.Result projected=restriction.project(proof,selected,q,10000);
                    require(projected.eligible && !projected.truncated && !projected.complete && projected.removed==1,
                        "covered native typed route projects exactly one proof");
                    require(projected.retained.size()==4,"one of four source-alias combinations is cut while three aliases and independent route survive");
                    require(state.equals(DmzObservationStateFingerprint.capture(q)),"projection is read-only");
                    require(DmzProofWitnesses.enumerate(proof.graph,selected.node,10000).witnesses.size()==projected.retained.size()+1,
                        "original graph unchanged");
                    require(!restriction.project(proof,selected,q,1).eligible,"tiny projection budget refuses");
                    require(!restriction.project(proof,selected.premises.get(0),q,10000).eligible,"primary leaf cannot be selected");
                    DmzGroundProofCoverage.Result shortResult = bounded.audit(proof, q);
                    require(shortResult.truncated && !shortResult.covered,
                            "post-inventory audit exhaustion explicit");
                    List<TerminalSupportCapture.ProvisionalApplication> reduced = new ArrayList<TerminalSupportCapture.ProvisionalApplication>();
                    for (TerminalSupportCapture.ProvisionalApplication observed : event.observations.applications) {
                        boolean fromC = false;
                        for (DmzReplayProvenance.SourceObservation source : observed.ruleSources)
                            fromC |= source.binding.context.equals(c.getContextId());
                        if (!fromC) reduced.add(observed);
                    }
                    DmzGroundProofCoverage.Result missing = coverage.audit(rebuild(event, reduced), q);
                    refusal(restriction,rebuild(event,reduced),q);
                    require(missing.current && missing.supported && !missing.covered && has(missing, "missing-typed-native-application:"),
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
                    refusal(restriction,rebuild(event,reduced),q);
                    require(missing.current && !missing.covered && has(missing, "missing-typed-primary-alternative:"),
                            "missing canonical fact source choice detected independently of remaining witness");
                    for (int damage=0;damage<4;++damage) {
                        reduced.clear();
                        for (TerminalSupportCapture.ProvisionalApplication observed:event.observations.applications) {
                            TerminalSupportCapture.Application original=observed.application;
                            List<TerminalSupportCapture.Binding> bindings=new ArrayList<TerminalSupportCapture.Binding>();
                            for (TerminalSupportCapture.Binding binding:original.bindings) {
                                if (damage==0) continue;
                                bindings.add(new TerminalSupportCapture.Binding(binding.variable,
                                    damage==1 ? -1L : damage==2 ? binding.nativeVariable+100000L : binding.nativeVariable,
                                    binding.name,binding.rendering,damage==3 ?
                                        DmzGroundReplay.parseIdentity("!value(Wrong);").ground().arguments.get(0) : binding.value));
                            }
                            TerminalSupportCapture.Application changed=new TerminalSupportCapture.Application(original.mind,original.rule,
                                original.ruleOrigin,original.conclusion,original.ground,bindings,original.supports,original.sourceCandidates);
                            reduced.add(new TerminalSupportCapture.ProvisionalApplication(changed,observed.outcome,observed.ruleSources,observed.supportSources));
                        }
                        missing=coverage.audit(rebuild(event,reduced),q);
                        refusal(restriction,rebuild(event,reduced),q);
                        require(missing.current && missing.supported && !missing.covered && has(missing,"missing-typed-native-application:"),
                            "lost or forged native substitution is not covered: "+damage);
                    }
                    reduced=new ArrayList<TerminalSupportCapture.ProvisionalApplication>(event.observations.applications);
                    TerminalSupportCapture.Application selectedApp=selected.graph.observed.steps.get(selected.step).application;
                    for(TerminalSupportCapture.ProvisionalApplication observed:event.observations.applications) {
                        TerminalSupportCapture.Application app=observed.application;
                        if(app.rule!=selectedApp.rule || !app.ground.equivalent(selectedApp.ground))continue;
                        List<TerminalSupportCapture.Binding> forged=new ArrayList<TerminalSupportCapture.Binding>();
                        for(TerminalSupportCapture.Binding binding:app.bindings) forged.add(new TerminalSupportCapture.Binding(binding.variable,
                            binding.nativeVariable,binding.name,binding.rendering,DmzGroundReplay.parseIdentity("!value(Forged);").ground().arguments.get(0)));
                        TerminalSupportCapture.Application extra=new TerminalSupportCapture.Application(app.mind,app.rule,app.ruleOrigin,
                            app.conclusion,app.ground,forged,app.supports,app.sourceCandidates);
                        reduced.add(new TerminalSupportCapture.ProvisionalApplication(extra,observed.outcome,observed.ruleSources,observed.supportSources));break;
                    }
                    DmzProvisionalCollisionProof extraProof=rebuild(event,reduced);
                    require(coverage.audit(extraProof,q).covered,"all real routes remain covered despite an extra diagnostic forgery");
                    DmzProofWitnesses.Witness invented=null;
                    for(DmzWitnessConflicts.Combination pair:extraProof.combinations) for(DmzProofWitnesses.Witness side:java.util.Arrays.asList(pair.left,pair.right))
                        if(side.step>=0 && !side.graph.observed.steps.get(side.step).application.bindings.isEmpty()
                            && side.graph.observed.steps.get(side.step).application.bindings.get(0).value.semanticallyEquals(
                                DmzGroundReplay.parseIdentity("!value(Forged);").ground().arguments.get(0)))invented=side;
                    require(invented!=null,"forged selected alternative constructed");
                    require(!restriction.project(extraProof,invented,q,10000).eligible,"coverage of real routes cannot authorize an invented selected substitution");
                    replay(q, duplicate, 12, "!a(John,Tom);");
                    require(!restriction.project(proof,selected,q,10000).eligible,"new source occurrence invalidates typed projection");
                    require(!coverage.audit(proof, q).current, "new source occurrence invalidates audited baseline");
                } else if (mode.equals("long-chain")) {
                    require(result.covered && result.expectedSteps >= 4,
                            "native capture includes relevant unit steps in longer chain: steps=" + result.expectedSteps + " " + result.gaps);
                    DmzProofWitnesses.Witness recursive=null;
                    for(DmzWitnessConflicts.Combination pair:proof.combinations) for(DmzProofWitnesses.Witness side:java.util.Arrays.asList(pair.left,pair.right))
                        if(side.source.context.equals(c.getContextId()) && side.premises.get(0).step>=0)recursive=side;
                    require(recursive!=null,"recursive typed native witness available");
                    String state=DmzObservationStateFingerprint.capture(q);
                    DmzGroundCollisionRestriction.Result branch=restriction.project(proof,recursive,q,10000);
                    require(branch.eligible && branch.removed==1 && branch.retained.size()==DmzProofWitnesses.enumerate(proof.graph,recursive.node,10000).witnesses.size()-1,"recursive typed chain exclusion removes only one native witness: eligible="+branch.eligible+" removed="+branch.removed+" retained="+branch.retained.size());
                    require(state.equals(DmzObservationStateFingerprint.capture(q)),"recursive projection preserves Q");
                } else if (mode.equals("unseeded-cycle")) {
                    require(result.covered && result.expectedSteps == 3,
                            "unseeded cycle contributes no invented unit application");
                } else {
                    String reason = mode.equals("conjunction") ? "unsupported-live-clause"
                            : mode.equals("variables") ? "unsupported-unit-pattern"
                            : mode.equals("set") ? "unsupported-ground"
                            : mode.equals("anonymous") ? "missing-production-source" : "inconsistent-context-revisions";
                    require(!result.supported && !result.covered && has(result, reason),
                            "unsupported baseline fails closed for its actual boundary: " + mode + " " + result.gaps);
                }
            }
        }
    }
    /** Opposite consequences of two general rules share one unchanged primary fact. */
    private static void sharedPremise(boolean reverse) throws Exception {
        shape="ordinary";
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision a = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision b = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            replay(q, reverse ? b : a, 10, reverse ? "!@x @y a(x,y) -> ~b(x,y);" : "!@x @y a(x,y) -> b(x,y);");
            replay(q, reverse ? a : b, 10, reverse ? "!@x @y a(x,y) -> b(x,y);" : "!@x @y a(x,y) -> ~b(x,y);");
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
            replay(q, reverse ? b : a, 10, reverse ? "!@x @y a(x,y) -> ~b(x,y);" : "!@x @y a(x,y) -> b(x,y);");
            replay(q, reverse ? a : b, 10, reverse ? "!@x @y a(x,y) -> b(x,y);" : "!@x @y a(x,y) -> ~b(x,y);");
            replay(q, fact, 11, "!a(John,Tom);");
            try (TerminalSupportCapture applications = TerminalSupportCapture.begin(q);
                    CollisionProofCapture collisions = CollisionProofCapture.begin()) {
                DmzGroundProofCoverage coverage = DmzGroundProofCoverage.beforeOperation(applications, q, 10000);
                DmzGroundCollisionRestriction restriction=DmzGroundCollisionRestriction.beforeOperation(applications,q,10000);
                require(!q.compile("!anchor(Trigger);", null, false), "shared fact activates conflicting productions");
                require(!collisions.snapshot().isEmpty(), "shared-premise event captured");
                DmzProvisionalCollisionProof proof = DmzProvisionalCollisionProof.build(collisions.snapshot().get(0), 1000, 100);
                DmzGroundProofCoverage.Result result = coverage.audit(proof, q);
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
    private static void refusal(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,Mind q) throws Exception {
        DmzGroundCollisionRestriction.Result result=guard.project(proof,proof.combinations.isEmpty()?null:selection(proof),q,10000);
        require(!result.eligible && result.graph==null && result.retained.isEmpty(),"damaged native route never yields a branch graph");
    }
    private static DmzProofWitnesses.Witness selection(DmzProvisionalCollisionProof proof) {
        DmzWitnessConflicts.Combination pair=proof.combinations.get(0);
        return DmzProofWitnesses.enumerate(proof.graph,pair.left.node,10000).witnesses.size()>1 ? pair.left : pair.right;
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
        DmzReplayProvenance.replayRule(mind, pin, rule, DmzReplayProvenance.Authority.EXTERNAL,
            shape.equals("permuted") ? text.replace("male(x,y)","male(y,x)") :
            shape.equals("constants") ? text.replace("male(x,y)","male(x,y,Fixed)") :
            shape.equals("sets") ? text.replace("John,Tom","[John,Tom],7") : text);
    }
    private static boolean has(DmzGroundProofCoverage.Result result, String prefix) {
        for (String gap : result.gaps) if (gap.startsWith(prefix)) return true;
        return false;
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
