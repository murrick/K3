/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessPrimaryStepVerifier.*;
import org.kanger.enums.QueryPass;

/** Whole primary donors, exact lineage, alternative source groups and depth controls. */
public final class ContextWitnessPrimaryStepRunner {
    private static int checks;
    private static Source source(int id) { return new Source(new UUID(0,id == 0 ? 0 : 1),1,id,id == 0,"base"); }
    private static Bundle bundle(int id,String text) throws Exception {
        return ContextWitnessBundle.capture(new Declaration(source(id),text),10000);
    }
    private static Session session(Bundle... bundles) throws Exception { return ContextWitnessBundle.restoreObserved(Arrays.asList(bundles),10000,50000); }
    private static void rule(Session s,int id,String text) throws Exception { s.capture.replay(s.mind,source(id),text); }
    private static Result prove(Session s) throws Exception {
        s.mind.setQueryPass(QueryPass.ACCEPT); s.mind.link(null,false);
        return ContextWitnessPrimaryStepVerifier.inspect(s,8,1000000);
    }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static boolean proof(Result r,String predicate,int depth,int... ids) {
        Set<Source> expected = new LinkedHashSet<>(); for (int id : ids) expected.add(source(id));
        return r.steps.stream().anyMatch(step -> step.observation.conclusion.predicate.equals(predicate)
                && step.supports.stream().anyMatch(s -> s.depth == depth && s.sources.equals(expected)));
    }
    private static Clause shape(String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        org.kanger.units.Rule r = (org.kanger.units.Rule)m.compileLine(text,false,new LinkedList<org.kanger.interfaces.ITerm>());
        return Clause.shapeOf(r.getTree().get(0),m);
    }
    private static void incomplete(Result r,String why) { require(!r.reconstructed && r.steps.isEmpty(),why+": "+r.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-primary-step-").toString());
            Bundle p = bundle(0,"!$x p(x,1);");
            try (Session s = session(p)) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,2,"!@x p(x,1) -> r(x,1);");
                rule(s,3,"!@x p(x,1) -> q(x,1);"); Result r = prove(s);
                require(r.reconstructed,"primary steps reconstructed: "+r.reason);
                ContextWitnessProjection q = r.steps.stream().filter(step -> step.observation.conclusion.predicate.equals("q")).findFirst().get().observation;
                require(!ContextWitnessPrimaryStepVerifier.producerInstance(shape("!@x p(x,2) -> q(x,1);"),q,() -> {}),
                        "independent producer check rejects a wrong fixed premise value");
                require(!ContextWitnessPrimaryStepVerifier.producerInstance(shape("!@x p(x,1) -> r(x,1);"),q,() -> {}),
                        "independent producer check rejects a wrong head predicate");
                require(proof(r,"q",1,0,1) && proof(r,"q",1,0,3),"producer aliases retain separate complete X/producer proofs");
                require(proof(r,"r",1,0,2),"other producer preserves its own source group");
                require(!proof(r,"q",1,1) && !proof(r,"q",1,0,1,3),"producer-only and merged-alternative groups cannot replace whole proofs");
                require(r.steps.stream().allMatch(step -> step.replay.equals(s.replay)),"every proof retains the owning replay namespace");
                incomplete(ContextWitnessPrimaryStepVerifier.inspect(s,0,1000000),"depth0 cannot admit an application step");
                incomplete(ContextWitnessPrimaryStepVerifier.inspect(s,8,1),"work stop cannot publish a verified prefix");
                require(!s.capture.snapshot().gaps.isEmpty(),"reconstructed steps do not clear native coverage gaps");
            }
            try (Session s = session(bundle(0,"!$x p(x,1) && q(x,1);"))) {
                rule(s,1,"!@x p(x,1) && q(x,1) -> r(x,1);"); Result r = prove(s);
                require(r.reconstructed && proof(r,"r",1,0,1),"joint primary bundle has one whole donor group at depth1: "+r.reason);
            }
            try (Session s = session(bundle(0,"!$x $y pair(x,y);"))) {
                rule(s,1,"!@x @y pair(x,y) -> out(y,x,1);"); Result r = prove(s);
                require(r.reconstructed && proof(r,"out",1,0,1),"independent witness positions retain their permutation: "+r.reason);
                ContextWitnessProjection out = r.steps.stream().filter(step -> step.observation.conclusion.predicate.equals("out")).findFirst().get().observation;
                require(ContextWitnessPrimaryStepVerifier.producerInstance(shape("!@a @b pair(a,b) -> out(b,a,1);"),out,() -> {}),
                        "alpha-renamed producer retains the correct variable correspondence");
                require(!ContextWitnessPrimaryStepVerifier.producerInstance(shape("!@x @y pair(x,y) -> out(x,y,1);"),out,() -> {}),
                        "independent checker rejects swapped witness identity");
                require(!ContextWitnessPrimaryStepVerifier.producerInstance(shape("!@x @y pair(x,x) -> out(y,x,1);"),out,() -> {}),
                        "independent checker cannot turn separate witnesses into a diagonal");
            }
            try (Session s = session(p,bundle(2,"!$x q(x,1);"))) {
                rule(s,1,"!@x p(x,1) && q(x,1) -> r(x,1);");
                incomplete(prove(s),"independent witness scopes cannot supply a shared-witness join");
            }
            try (Session s = session(p,bundle(2,"!$y q(y,2);"))) {
                rule(s,1,"!@x @y p(x,1) && q(y,2) -> r(x,y,1);"); Result r = prove(s);
                require(r.reconstructed && proof(r,"r",1,0,1,2),"independent binders can join through independent producer variables: "+r.reason);
            }
            try (Session s = session(p,bundle(2,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); Result r = prove(s);
                require(r.reconstructed && proof(r,"q",1,0,1) && proof(r,"q",1,1,2),"duplicate donors retain independent support alternatives: "+r.reason);
                require(!proof(r,"q",1,0,1,2),"donor alternatives are not a joint source group");
            }
            try (Session s = session(bundle(0,"!$x ~p(x,1);"))) {
                rule(s,1,"!@x ~p(x,1) -> q(x,1);"); Result r = prove(s);
                require(r.reconstructed && proof(r,"q",1,0,1),"signed primary premise is reconstructed: "+r.reason);
            }
            try (Session s = session(p)) {
                rule(s,1,"!@x p(x,2) -> q(x,1);"); incomplete(prove(s),"wrong ordinary constant cannot match the primary donor");
            }
            try (Session s = session(p)) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,2,"!@x q(x,1) -> r(x,1);");
                incomplete(prove(s),"generated witness chains remain outside single primary-step scope");
            }
            try (Session s = session(p)) {
                rule(s,0,"!@x p(x,1) -> q(x,1);");
                Result r = prove(s); incomplete(r,"conflicting source occurrence cannot authenticate both fact and rule");
                require(r.reason.equals("conflicting-source-occurrence"),"source conflict has an explicit guard");
            }
            try (Session s = session()) {
                rule(s,0,"!$x p(x,1);"); rule(s,1,"!@x p(x,1) -> q(x,1);");
                incomplete(prove(s),"raw witness metadata without bundle binding is insufficient");
            }
            try (Session s = session(p)) {
                rule(s,1,"!@x p(x,1) -> q(x,1);");
                s.capture.replay(s.mind,new Source(new UUID(0,99),1,0,false,"base"),"!@x p(x,1) -> r(x,1);");
                Result r = prove(s); incomplete(r,"independent sibling contexts cannot be combined into one proof cut");
                require(r.reason.equals("incompatible-source-cut"),"context cut guard is explicit");
            }
            System.out.println("CONTEXT_WITNESS_PRIMARY_STEP_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
