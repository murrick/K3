/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessUnaryChainVerifier.*;
import org.kanger.enums.QueryPass;

/** Proof route controls for single-premise chains and alpha-reused storage. */
public final class ContextWitnessUnaryChainRunner {
    private static int checks;
    private static Source source(int id) { return new Source(new UUID(0,id == 0 ? 0 : 1),1,id,id == 0,"base"); }
    private static Bundle bundle(int id,String text) throws Exception { return ContextWitnessBundle.capture(new Declaration(source(id),text),10000); }
    private static Session session(Bundle... bundles) throws Exception { return ContextWitnessBundle.restoreObserved(Arrays.asList(bundles),10000,100000); }
    private static void rule(Session s,int id,String text) throws Exception { s.capture.replay(s.mind,source(id),text); }
    private static Result prove(Session s) throws Exception {
        s.mind.setQueryPass(QueryPass.ACCEPT); s.mind.link(null,false);
        return ContextWitnessUnaryChainVerifier.inspect(s,16,2000000);
    }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static boolean stored(Result r,String predicate,long origin,int depth,int... ids) {
        Set<Source> sources = new LinkedHashSet<>(); for (int id : ids) sources.add(source(id));
        return r.routes.stream().anyMatch(p -> p.crossing != null && p.physical.predicate.equals(predicate)
                && p.origin.term == origin && p.proof.depth == depth && p.proof.sources.equals(sources));
    }
    private static void incomplete(Result r,String why) { require(!r.reconstructed && r.routes.isEmpty(),why+": "+r.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-unary-chain-").toString());
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,2,"!@x q(x,1) -> r(x,1);"); Result r = prove(s);
                require(r.reconstructed,"observed chain reconstructs: "+r.reason); long root = s.primary.get(0).witnessTerms.get(0);
                require(stored(r,"q",root,1,0,1),"stored q retains primary origin and whole depth1 proof");
                require(stored(r,"r",root,2,0,1,2),"stored r retains whole depth2 proof through q materialization");
                require(!stored(r,"r",root,1,0,1,2) && !stored(r,"r",root,2,0,2),"depth and donor sources cannot reset at storage");
                require(r.routes.stream().allMatch(p -> p.replay.equals(s.replay)),"all routes retain owning replay namespace");
                require(r.routes.stream().filter(p -> p.crossing != null).allMatch(p -> p.prior != null && p.proof == p.prior.proof),
                        "materialization changes physical carrier with zero proof-depth cost");
                incomplete(ContextWitnessUnaryChainVerifier.inspect(s,1,2000000),"depth1 cannot admit the two-step chain");
                incomplete(ContextWitnessUnaryChainVerifier.inspect(s,16,1),"work stop cannot publish a route prefix");
                require(!s.capture.snapshot().gaps.isEmpty(),"proof routes do not clear witness completeness gaps");
            }
            try (Session s = session(bundle(0,"!$x left(x,1);"),bundle(2,"!$x right(x,1);"))) {
                rule(s,1,"!@x left(x,1) -> q(x,1);"); rule(s,4,"!@x right(x,1) -> q(x,1);");
                rule(s,3,"!@x q(x,1) -> r(x,1);"); Result r = prove(s);
                require(r.reconstructed,"alpha-reused chain reconstructs separate routes: "+r.reason);
                long left = s.primary.get(0).witnessTerms.get(0), right = s.primary.get(1).witnessTerms.get(0);
                require(left != right && stored(r,"r",left,2,0,1,3) && stored(r,"r",right,2,2,3,4),"both origins survive one stored representative");
                require(!stored(r,"r",right,2,0,1,3) && !stored(r,"r",left,2,2,3,4),"shared physical carrier cannot exchange source-witness identities");
                require(!stored(r,"r",left,2,0,1,2,3,4),"alternative routes never become a merged joint source group");
                Set<Root> physical = new HashSet<>(), origins = new HashSet<>();
                for (Route p : r.routes) if (p.crossing != null && p.physical.predicate.equals("r")) { physical.add(p.physical.root); origins.add(p.origin); }
                require(physical.size() == 1 && origins.size() == 2,"one durable root keeps two separate proof-origin routes");
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,2,"!@x q(x,1) -> r(x,1);"); rule(s,3,"!@x p(x,1) -> r(x,1);");
                Result r = prove(s); long root = s.primary.get(0).witnessTerms.get(0);
                require(r.reconstructed && stored(r,"r",root,1,0,3) && stored(r,"r",root,2,0,1,2),"different whole source alternatives retain their own minimum depths: "+r.reason);
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x (p(x,1)->q(x,1)) && (q(x,1)->r(x,1)) && (p(x,1)->r(x,1));");
                Result r = prove(s); long root = s.primary.get(0).witnessTerms.get(0);
                require(r.reconstructed && stored(r,"r",root,1,0,1) && !stored(r,"r",root,2,0,1),"identical whole source group retains the minimum depth: "+r.reason);
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> ~q(x,1);"); rule(s,2,"!@x ~q(x,1) -> r(x,1);"); Result r = prove(s);
                require(r.reconstructed && stored(r,"r",s.primary.get(0).witnessTerms.get(0),2,0,1,2),"negative intermediate facts preserve chain proof: "+r.reason);
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,2);"); rule(s,2,"!@x q(x,1) -> r(x,1);"); Result r = prove(s);
                require(r.reconstructed && r.routes.stream().noneMatch(p -> p.physical.predicate.equals("r")),"fixed-value mismatch cannot manufacture the missing second application: "+r.reason);
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);"); rule(s,3,"!@x p(x,1) -> q(x,1);"); rule(s,2,"!@x q(x,1) -> r(x,1);");
                Result r = prove(s); long root = s.primary.get(0).witnessTerms.get(0);
                require(r.reconstructed && stored(r,"r",root,2,0,1,2) && stored(r,"r",root,2,0,2,3),"duplicate producers retain separate downstream proofs: "+r.reason);
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x (p(x,1)->q(x,1)) && (q(x,1)->p(x,1));"); Result r = prove(s);
                require(r.reconstructed && stored(r,"q",s.primary.get(0).witnessTerms.get(0),1,0,1),"observed cycle settles at minimum proof depths: "+r.reason);
            }
            try (Session s = session(bundle(0,"!$x p(x,1) && q(x,1);"))) {
                rule(s,1,"!@x p(x,1) && q(x,1) -> r(x,1);"); incomplete(prove(s),"multi-premise route coherence is not admitted");
            }
            try (Session s = session(bundle(0,"!$x $y pair(x,y,1);"))) {
                rule(s,1,"!@x @y pair(x,y,1) -> out(x,y,1);"); incomplete(prove(s),"multiple simultaneous witness roots remain outside scope");
            }
            try (Session s = session(bundle(0,"!$x p(x,1);"))) {
                rule(s,1,"!@x p(x,1) -> q(x,1);");
                s.capture.replay(s.mind,new Source(new UUID(0,99),1,0,false,"base"),"!@x q(x,1) -> r(x,1);");
                incomplete(prove(s),"independent sibling contexts cannot form a communal proof chain");
            }
            System.out.println("CONTEXT_WITNESS_UNARY_CHAIN_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
