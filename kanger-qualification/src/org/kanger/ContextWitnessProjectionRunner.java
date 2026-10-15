/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Producer labels and match-time lineage, without derived witness certification. */
public final class ContextWitnessProjectionRunner {
    private static int checks;
    private static Source source(int id) { return new Source(new UUID(0,id),1,0,id == 0,"base"); }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Bundle bundle(int id,String text) throws Exception {
        return ContextWitnessBundle.capture(new Declaration(source(id),text),10000);
    }
    private static ContextWitnessProjection projection(Snapshot snapshot,String predicate) {
        for (ContextWitnessProjection projection : snapshot.witnessProjections)
            if (projection.conclusion.predicate.equals(predicate)) return projection;
        throw new AssertionError("Missing native witness projection: "+predicate);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-projection-").toString());
            Bundle root = bundle(0,"!$x p(x,1);");
            try (Session session = ContextWitnessBundle.restoreObserved(Collections.singletonList(root),10000,10000)) {
                PrimaryEvidence evidence = session.primary.get(0);
                require(evidence.support.sources.equals(Collections.singleton(source(0))) && evidence.support.depth == 0,
                        "whole primary bundle has exactly its source at depth0");
                require(evidence.witnessTerms.size() == 1,"roundtrip binds the primary witness ordinal to its native term");
                session.capture.replay(session.mind,source(1),"!@x p(x,1) -> q(x,1);");
                session.capture.replay(session.mind,source(2),"!@x p(x,1) -> r(x,1);");
                session.capture.replay(session.mind,source(3),"!@x p(x,1) -> q(x,1);");
                session.mind.setQueryPass(QueryPass.ACCEPT); session.mind.link(null,false);
                Snapshot snapshot = session.capture.snapshot();
                ContextWitnessProjection q = projection(snapshot,"q"), r = projection(snapshot,"r");
                for (ContextWitnessProjection observed : Arrays.asList(q,r)) {
                    require(observed.premises.size() == 1 && observed.premises.get(0).predicate.equals("p")
                            && observed.premises.get(0).positive,"actual excluded premise is observed with its true sign");
                    List<ContextWitnessProjection.Node> lineage = observed.conclusion.arguments.get(0).lineage;
                    require(lineage.size() >= 2 && lineage.get(lineage.size()-1).term == evidence.witnessTerms.get(0),
                            "match-time conclusion retains lineage to the primary witness");
                    require(observed.premises.get(0).arguments.get(0).lineage.get(
                            observed.premises.get(0).arguments.get(0).lineage.size()-1).term == evidence.witnessTerms.get(0),
                            "premise and conclusion retain the same source root");
                    require(observed.conclusion.arguments.get(1).value.semanticallyEquals(
                            observed.premises.get(0).arguments.get(1).value),"fixed ordinary argument remains semantic evidence");
                }
                require(q.producerSupports.stream().anyMatch(s -> s.sources.equals(Collections.singleton(source(1))) && s.depth == 0)
                        && r.producerSupports.stream().anyMatch(s -> s.sources.equals(Collections.singleton(source(2))) && s.depth == 0),
                        "producer alternatives remain rule labels, not fabricated complete derived supports");
                require(q.producerSupports.stream().anyMatch(s -> s.sources.equals(Collections.singleton(source(1))))
                        && q.producerSupports.stream().anyMatch(s -> s.sources.equals(Collections.singleton(source(3)))),
                        "duplicate producer rules keep separate support alternatives");
                Map<String,Long> durable = new HashMap<>();
                for (IRule candidate : session.mind.getRules()) if (candidate.isGenerated() && candidate.isStored()) {
                    Domain domain = ((Rule)candidate).getDomain(); String name = domain.getPredicate(session.mind).getName(session.mind);
                    if (name.equals("q") || name.equals("r")) {
                        Term term = (Term)domain.get(0).getValue(session.mind);
                        require(term.getParent(session.mind) == null && term.getRuleId() == evidence.nativeRule,
                                "durable generated projection retains the primary root owner");
                        durable.put(name,term.getId());
                    }
                }
                require(durable.size() == 2 && durable.get("q").equals(durable.get("r")),
                        "materialized conclusions preserve their shared primary witness");
                require(!snapshot.gaps.isEmpty(),"new metadata cannot erase nonordinary proof gaps");
            }
            Bundle duplicate = bundle(1,"!$x p(x,1);");
            try (Session session = ContextWitnessBundle.restoreObserved(Arrays.asList(root,duplicate),10000,10000)) {
                require(session.primary.size() == 2 && session.primary.get(0).support.sources.equals(Collections.singleton(source(0)))
                        && session.primary.get(1).support.sources.equals(Collections.singleton(source(1))),"duplicate declarations retain separate whole source alternatives");
            }
            try (Session session = ContextWitnessBundle.restoreObserved(Collections.singletonList(bundle(0,"!$x p(x);")),10000,10000)) {
                session.capture.replay(session.mind,source(1),"!@x absent(x) -> q(x);");
                session.mind.setQueryPass(QueryPass.ACCEPT); session.mind.link(null,false);
                require(session.capture.snapshot().witnessProjections.stream().noneMatch(p -> p.conclusion.predicate.equals("q")),
                        "rule shape without an actual match cannot supply witness lineage");
            }
            try (Session session = ContextWitnessBundle.restoreObserved(Collections.singletonList(bundle(0,"!$x ~p(x,1);")),10000,10000)) {
                session.capture.replay(session.mind,source(1),"!@x ~p(x,1) -> q(x,1);");
                session.mind.setQueryPass(QueryPass.ACCEPT); session.mind.link(null,false);
                ContextWitnessProjection observed = projection(session.capture.snapshot(),"q");
                require(!observed.premises.get(0).positive,"excluded negative premise retains its actual sign");
            }
            Mind ordinary = ContextWitnessBundle.restore(Collections.singletonList(root),10000);
            try (ContextProductionCapture capture = ContextProductionCapture.begin(ordinary,10000)) {
                capture.replay(ordinary,source(1),"!@x p(x,1) -> q(x,1);");
                ordinary.setQueryPass(QueryPass.ACCEPT); ordinary.link(null,false);
                require(capture.snapshot().witnessProjections.isEmpty(),"default capture does not opt into witness observations");
            }
            boolean stopped = false;
            try (Session session = ContextWitnessBundle.restoreObserved(Collections.singletonList(root),10000,4)) {
                session.capture.replay(session.mind,source(1),"!@x p(x,1) -> q(x,1);");
                session.mind.setQueryPass(QueryPass.ACCEPT); session.mind.link(null,false);
            } catch (IllegalStateException expected) { stopped = expected.getMessage().contains("observation limit exceeded"); }
            require(stopped,"projection records consume the bounded capture event budget");
            System.out.println("CONTEXT_WITNESS_PROJECTION_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
