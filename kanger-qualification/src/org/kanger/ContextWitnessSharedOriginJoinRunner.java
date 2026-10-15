/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessUnaryChainVerifier.*;
import org.kanger.enums.QueryPass;

/** Shared-origin proof joins, including a foreign mask with independent primary witnesses. */
public final class ContextWitnessSharedOriginJoinRunner {
    private static int checks;
    private static Source source(int id) { return new Source(new UUID(0,id == 0 ? 0 : 1),1,id,id == 0,"base"); }
    private static Bundle bundle(int id,String text) throws Exception { return ContextWitnessBundle.capture(new Declaration(source(id),text),10000); }
    private static Session session(Bundle... bundles) throws Exception { return ContextWitnessBundle.restoreObserved(Arrays.asList(bundles),10000,100000); }
    private static void rule(Session s,int id,String text) throws Exception { s.capture.replay(s.mind,source(id),text); }
    private static Result prove(Session s) throws Exception {
        s.mind.setQueryPass(QueryPass.ACCEPT); s.mind.link(null,false);
        return ContextWitnessUnaryChainVerifier.inspectSharedOriginJoins(s,16,4000000);
    }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static boolean stored(Session session,Result r,String predicate,long origin,int depth,int... ids) {
        Set<Source> expected = new LinkedHashSet<>(); for (int id : ids) expected.add(source(id));
        return r.routes.stream().anyMatch(p -> physicalStored(session,p) && p.physical.predicate.equals(predicate)
                && p.origin.term == origin && p.proof.depth == depth && p.proof.sources.equals(expected));
    }
    private static boolean physicalStored(Session session,Route route) {
        for (org.kanger.ContextWitnessProjection.Materialization m : session.capture.snapshot().witnessMaterializations) {
            if (!m.stored.predicate.equals(route.physical.predicate) || m.stored.positive != route.physical.positive
                    || m.stored.arguments.size() != route.physical.arguments.size()) continue;
            boolean same = true;
            for (int i=0;i<m.stored.arguments.size();++i) {
                org.kanger.ContextWitnessProjection.Slot a = m.stored.arguments.get(i); Argument b = route.physical.arguments.get(i);
                if (a.lineage.isEmpty()) same &= b.root == null && a.value.semanticallyEquals(b.value);
                else {
                    org.kanger.ContextWitnessProjection.Node node = a.lineage.get(a.lineage.size()-1);
                    same &= b.root != null && b.root.term == node.term && b.root.owner == node.ownerRule;
                }
            }
            if (same) return true;
        }
        return false;
    }
    private static void incomplete(Result r,String why) { require(!r.reconstructed && r.routes.isEmpty(),why+": "+r.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-join-").toString());
            try (Session s = session(bundle(0,"!$x p(x,1) && q(x,1);"))) {
                rule(s,1,"!@x p(x,1) && q(x,1) -> out(x,1);"); Result r = prove(s); long origin = s.primary.get(0).witnessTerms.get(0);
                require(r.reconstructed && stored(s,r,"out",origin,1,0,1),"whole primary shared witness joins at depth1: "+r.reason);
                require(r.routes.stream().filter(p -> p.application != null).anyMatch(p -> p.donors.size() == 2
                        && p.donors.stream().allMatch(d -> d.origin.equals(p.origin))),"join recipe retains both coherent donors");
                incomplete(ContextWitnessUnaryChainVerifier.inspect(s,16,4000000),"unary entrypoint retains its previous scope");
                incomplete(ContextWitnessUnaryChainVerifier.inspectSharedOriginJoins(s,0,4000000),"depth0 cannot admit a join step");
                incomplete(ContextWitnessUnaryChainVerifier.inspectSharedOriginJoins(s,16,1),"work stop cannot expose a verified prefix");
            }
            try (Session s = session(bundle(0,"!$x p(x,1) && flag(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,3,"!@x q(x,1) && flag(x,1) -> out(x,1);"); Result r = prove(s);
                require(r.reconstructed && stored(s,r,"out",s.primary.get(0).witnessTerms.get(0),2,0,1,3),"derived and primary donor join with carried depth2: "+r.reason);
                require(s.capture.snapshot().gaps.stream().anyMatch(g -> g.startsWith("compound-residual:")),
                        "observed join proof does not erase unsupported partial witness residual coverage");
                incomplete(ContextWitnessUnaryChainVerifier.inspectSharedOriginJoins(s,1,4000000),"storage cannot reset a derived donor's depth");
            }
            for (boolean reverse : new boolean[]{false,true}) {
                try (Session s = session(bundle(0,"!$x left(x,1);"),bundle(2,"!$x q(x,1) && flag(x,1);"))) {
                    String join = reverse ? "!@x flag(x,1) && q(x,1) -> out(x,1);" : "!@x q(x,1) && flag(x,1) -> out(x,1);";
                    rule(s,1,"!@x left(x,1) -> q(x,1);"); rule(s,3,join); Result r = prove(s);
                    long local = s.primary.get(0).witnessTerms.get(0), foreign = s.primary.get(1).witnessTerms.get(0);
                    require(r.reconstructed,"masked carrier reconstructs coherent alternatives: "+r.reason);
                    require(stored(s,r,"out",foreign,1,2,3),"foreign whole bundle proves its own join");
                    require(r.routes.stream().filter(p -> p.physical.predicate.equals("out")).noneMatch(p -> p.proof.sources.stream().anyMatch(source -> source.localX)),
                            "an X-dependent q route cannot borrow another witness's flag through storage");
                    require(r.routes.stream().anyMatch(p -> p.application != null && p.physical.predicate.equals("q") && p.origin.term == local),
                            "the X-dependent q route is genuinely present with its own origin");
                    require(r.routes.stream().noneMatch(p -> p.physical.predicate.equals("out") && p.origin.term == local),
                            "reversing premise order cannot transfer the output to the wrong origin");
                }
            }
            try (Session s = session(bundle(0,"!$x p(x,1) && flag(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,4,"!@x p(x,1) -> q(x,1);");
                rule(s,3,"!@x q(x,1) && flag(x,1) -> out(x,1);");
                rule(s,5,"!@x q(x,1) && flag(x,1) -> out(x,1);");
                Result result = prove(s); long origin = s.primary.get(0).witnessTerms.get(0);
                require(result.reconstructed,"mixed donor alternatives join: "+result.reason);
                for (int q : new int[]{1,4}) for (int producer : new int[]{3,5})
                    require(stored(s,result,"out",origin,2,0,q,producer),"every donor/producer whole-source alternative survives independently");
                require(!stored(s,result,"out",origin,3,0,1,3),"join depth is max donor depth plus1, not sum");
                require(!stored(s,result,"out",origin,2,0,1,3,4,5),"support alternatives cannot merge into one joint proof");
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,2,"!@x p(x,1) -> r(x,1);");
                rule(s,3,"!@x q(x,1) && r(x,1) -> out(x,1);"); Result result = prove(s);
                require(s.capture.snapshot().witnessProjections.stream().anyMatch(p -> p.conclusion.predicate.equals("out")),
                        "native fork now records the out application");
                require(result.reconstructed && stored(s,result,"out",s.primary.get(0).witnessTerms.get(0),2,0,1,2,3),
                        "both derived donors now prove out from the same primary witness");
            }
            try (Session s = session(bundle(0,"!$x p(x,1) && ~q(x,1);"))) {
                rule(s,1,"!@x p(x,1) && ~q(x,1) -> out(x,1);"); Result r = prove(s);
                require(r.reconstructed && stored(s,r,"out",s.primary.get(0).witnessTerms.get(0),1,0,1),"signed coherent donors preserve their proof: "+r.reason);
            }
            try (Session s = session(bundle(0,"!$x p(x,1) && q(x,1) && r(x,1);"))) {
                rule(s,1,"!@x p(x,1) && q(x,1) && r(x,1) -> out(x,1);"); incomplete(prove(s),"three-premise witness joins remain outside scope");
            }
            System.out.println("CONTEXT_WITNESS_SHARED_ORIGIN_JOIN_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
