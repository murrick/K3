/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessProjection.Materialization;
import org.kanger.ContextWitnessMaterializationVerifier.*;
import org.kanger.enums.QueryPass;

/** Per-application stored correspondence, never global witness union. */
public final class ContextWitnessMaterializationRunner {
    private static int checks;
    private static Source source(int id) { return new Source(new UUID(0,id == 0 ? 0 : 1),1,id,id == 0,"base"); }
    private static Bundle bundle(int id,String text) throws Exception { return ContextWitnessBundle.capture(new Declaration(source(id),text),10000); }
    private static Session session(Bundle... bundles) throws Exception { return ContextWitnessBundle.restoreObserved(Arrays.asList(bundles),10000,50000); }
    private static void rule(Session s,int id,String text) throws Exception { s.capture.replay(s.mind,source(id),text); }
    private static Result link(Session s) throws Exception {
        s.mind.setQueryPass(QueryPass.ACCEPT); s.mind.link(null,false);
        return ContextWitnessMaterializationVerifier.inspect(s,1000000);
    }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Relation relation(Result r,String predicate) {
        return r.relations.stream().filter(x -> x.occurrence.stored.predicate.equals(predicate)).findFirst().orElseThrow(() -> new AssertionError("No relation for "+predicate));
    }
    private static long root(ContextWitnessProjection.Slot slot) { return slot.lineage.get(slot.lineage.size()-1).term; }
    private static void incomplete(Result r,String why) { require(!r.verified && r.relations.isEmpty(),why+": "+r.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-materialization-").toString());
            Materialization positive;
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,2,"!@x q(x,1) -> r(x,1);"); Result r = link(s);
                require(r.verified,"chain materializations have actual application correspondence: "+r.reason);
                Relation q = relation(r,"q"), out = relation(r,"r"); positive = q.occurrence;
                require(q.replay.equals(s.replay) && out.replay.equals(s.replay),"relations retain the owning replay namespace");
                require(root(q.occurrence.matched.arguments.get(0)) == s.primary.get(0).witnessTerms.get(0),"q matched the primary witness");
                require(root(q.occurrence.matched.arguments.get(0)) == root(q.occurrence.stored.arguments.get(0)),"q materialization preserves its primary witness");
                require(out.applications.stream().anyMatch(a -> root(a.premises.get(0).arguments.get(0)) == root(q.occurrence.stored.arguments.get(0))),
                        "r premise uses the observed stored q root");
                require(q.applications.stream().allMatch(a -> a.nativeRule == q.occurrence.producerRule),"producer identity is exact");
                require(!ContextWitnessMaterializationVerifier.alphaPreserving(q.occurrence.matched,out.occurrence.stored,() -> {}),"wrong stored predicate cannot discharge correspondence");
                incomplete(ContextWitnessMaterializationVerifier.inspect(s,1),"work stop cannot publish a correspondence prefix");
                require(!s.capture.snapshot().gaps.isEmpty(),"correspondence does not clear witness coverage gaps");
                require(!ContextWitnessPrimaryStepVerifier.inspect(s,8,1000000).reconstructed,"single-step verifier still refuses generated chains");
            }
            try (Session s = session(bundle(0,"!$x left(x,1);"),bundle(2,"!$y right(y,1);"))) {
                rule(s,1,"!@x left(x,1) -> q(x,1);"); rule(s,3,"!@x right(x,1) -> q(x,1);"); Result r = link(s);
                require(r.verified,"independent-origin storage has separately witnessed occurrences: "+r.reason);
                Set<Long> origins = new HashSet<>(), stored = new HashSet<>(); Set<Long> rules = new HashSet<>();
                for (Relation relation : r.relations) if (relation.occurrence.stored.predicate.equals("q")) {
                    origins.add(root(relation.occurrence.matched.arguments.get(0)));
                    stored.add(root(relation.occurrence.stored.arguments.get(0))); rules.add(relation.occurrence.storedRule);
                }
                require(origins.size() == 2 && stored.size() == 2 && rules.size() == 2,
                        "independent primary witnesses retain separate stored representatives");
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,2);"); Result r = link(s); require(r.verified,"fixed-value variant matches its own application");
                require(!ContextWitnessMaterializationVerifier.alphaPreserving(positive.matched,relation(r,"q").occurrence.stored,() -> {}),
                        "wrong stored fixed value is rejected");
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> ~q(x,1);"); Result r = link(s); require(r.verified,"negative stored result is observed");
                require(!ContextWitnessMaterializationVerifier.alphaPreserving(positive.matched,relation(r,"q").occurrence.stored,() -> {}),
                        "wrong stored sign is rejected");
            }
            try (Session s = session(bundle(0,"!$x $y seed(x,y,1);"))) {
                rule(s,1,"!@x @y seed(x,y,1) -> pair(x,x,1);"); rule(s,2,"!@x @y seed(x,y,1) -> pair(x,y,1);"); Result r = link(s);
                require(r.verified,"diagonal and independent materializations both retain correspondence: "+r.reason);
                Materialization diagonal = null, independent = null;
                for (Relation relation : r.relations) if (relation.occurrence.stored.predicate.equals("pair")) {
                    Materialization m = relation.occurrence;
                    if (root(m.matched.arguments.get(0)) == root(m.matched.arguments.get(1))) diagonal = m; else independent = m;
                }
                require(diagonal != null && independent != null,"both witness-sharing topologies are present");
                require(!ContextWitnessMaterializationVerifier.alphaPreserving(diagonal.matched,independent.stored,() -> {}),"one witness cannot split at materialization");
                require(!ContextWitnessMaterializationVerifier.alphaPreserving(independent.matched,diagonal.stored,() -> {}),"independent witnesses cannot merge within one occurrence");
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x absent(x,1) -> q(x,1);"); incomplete(link(s),"no actual materialization cannot create a relation");
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                org.kanger.units.Rule primary = (org.kanger.units.Rule)s.mind.getRules().get(s.primary.get(0).nativeRule);
                // Adversarial callback injection: no native application preceded it.
                ContextProductionCapture.stored(s.mind,primary.getDomain(),primary);
                incomplete(ContextWitnessMaterializationVerifier.inspect(s,1000000),"a stored callback without an observed application is insufficient");
            }
            try (Session s = ContextWitnessBundle.restoreObserved(Collections.singletonList(bundle(0,"!$x p(x,1);")),10000,4)) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); boolean stopped = false;
                try { link(s); } catch (IllegalStateException expected) { stopped = expected.getMessage().contains("observation limit exceeded"); }
                require(stopped,"materialization observations consume the event budget");
                incomplete(ContextWitnessMaterializationVerifier.inspect(s,1000000),"truncated capture cannot publish correspondence evidence");
            }
            System.out.println("CONTEXT_WITNESS_MATERIALIZATION_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
