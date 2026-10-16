/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextGroundCompositionProof.*;
import org.kanger.enums.QueryPass;

/** Independent truth-table oracle for receipt-bound ground resolution proofs. */
public final class ContextGroundCompositionProofRunner {
    private static int checks,ids;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native source fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(m,inventory,WORK,100000);
        require(bound.bound,"native source binding"); return bound.snapshot;
    }
    private static Receipt receipt(Snapshot x,Snapshot a) {
        Frontier f = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(a),WORK);
        Receipt r = ContextOrdinaryResidualRounds.inspectWithProbes(f,a,16,WORK,100000,64);
        require(r.verified && r.collisions.isEmpty(),"complete local receipt: "+r.reason); return r;
    }
    private static List<CompositionCandidate> candidates(Receipt r) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); m.setQueryPass(QueryPass.ACCEPT);
        try (ContextProductionCapture c = ContextProductionCapture.begin(m,100000)) {
            c.observeCompositionCandidates(); List<Clause> seen = new ArrayList<>();
            for (Product p : r.localProofs) {
                if (seen.stream().anyMatch(cl -> cl.equivalent(p.clause))) continue;
                List<Support> alternatives = new ArrayList<>();
                for (Product q : r.localProofs) if (q.clause.equivalent(p.clause)) alternatives.add(q.proof);
                c.replayDerived(m,p.clause,alternatives); seen.add(p.clause);
            }
            m.link(null,false); ContextProductionCapture.Snapshot s = c.snapshot();
            require(s.gaps.isEmpty(),"native pair capture has no hidden gaps: "+s.gaps);
            return s.compositionCandidates;
        }
    }
    private static CompositionCandidate relabel(CompositionCandidate old,List<Support> left) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); m.setQueryPass(QueryPass.ACCEPT);
        try (ContextProductionCapture c = ContextProductionCapture.begin(m,10000)) {
            c.observeCompositionCandidates(); c.replayDerived(m,old.owner,left); c.replayDerived(m,old.partner,old.partnerSupports);
            m.link(null,false); return c.snapshot().compositionCandidates.get(0);
        }
    }
    private static Evidence prove(Receipt r,CompositionCandidate c) {
        Evidence e = ContextGroundCompositionProof.prove(r,c,16,WORK);
        require(e.proved && !e.proofs.isEmpty(),"ground proof: "+e.reason);
        require(e.input == r.input && e.target == r.target && e.reason.contains("admission unknown"),"proof retains exact immutable input/cut and no admission claim"); return e;
    }
    private static void fail(Evidence e,String why) { require(!e.proved && e.proofs.isEmpty(),why+": "+e.reason); }
    private static boolean value(Clause clause,Map<String,Boolean> values) {
        for (Atom atom : clause.literals) {
            boolean v = values.get(atom.predicate); if (atom.positive == v) return true;
        }
        return false;
    }
    private static void truthTable(Proof proof) {
        List<String> names = new ArrayList<>();
        for (Clause c : Arrays.asList(proof.observation.owner,proof.observation.partner,proof.conclusion))
            for (Atom a : c.literals) if (!names.contains(a.predicate)) names.add(a.predicate);
        require(names.size() <= 5,"small independent oracle vocabulary");
        for (int bits=0;bits<(1<<names.size());++bits) {
            Map<String,Boolean> values = new HashMap<>();
            for (int i=0;i<names.size();++i) values.put(names.get(i),(bits&(1<<i))!=0);
            require(!(value(proof.observation.owner,values) && value(proof.observation.partner,values)) || value(proof.conclusion,values),"all propositional models of the two whole parents satisfy the conclusion");
        }
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ground-composition-proof-").toString());
            Snapshot x = snapshot(true,"!~a(1)||~b(1)||~c(1);"),a = snapshot(false,"!d(1)->a(1);");
            Receipt r = receipt(x,a); List<CompositionCandidate> observed = candidates(r);
            require(observed.size() == 1,"original missing pair observed exactly once");
            Evidence e = prove(r,observed.get(0));
            Clause expected = snapshot(false,"!~d(1)||~b(1)||~c(1);").primary.get(0).clauses.get(0);
            require(e.proofs.size() == 1 && e.proofs.get(0).conclusion.equivalent(expected),"first missing X-dependent resolvent retains all three other literals");
            Set<Source> origins = new HashSet<>(Arrays.asList(x.primary.get(0).source,a.primary.get(0).source));
            require(e.proofs.get(0).support.sources.equals(origins) && e.proofs.get(0).support.depth == 1,"exact two original sources and one composition step");
            truthTable(e.proofs.get(0));
            fail(ContextGroundCompositionProof.prove(r,observed.get(0),0,WORK),"depth limit publishes no proof prefix");
            fail(ContextGroundCompositionProof.prove(r,observed.get(0),16,1),"work limit publishes no proof prefix");
            Receipt self = ContextOrdinaryResidualRounds.inspectWithProbes(r.input,x,16,WORK,100000,64);
            fail(ContextGroundCompositionProof.prove(self,observed.get(0),16,WORK),"foreign branch cannot authenticate against self ledger");
            Receipt stale = receipt(snapshot(true,"!~a(1)||~b(1)||~c(1);"),a);
            fail(ContextGroundCompositionProof.prove(stale,observed.get(0),16,WORK),"same syntax with another original source cannot authenticate");
            Receipt incomplete = ContextOrdinaryResidualRounds.inspectWithProbes(r.input,a,16,1,100000,64);
            fail(ContextGroundCompositionProof.prove(incomplete,observed.get(0),16,WORK),"unverified receipt cannot certify");
            fail(ContextGroundCompositionProof.prove(r,null,16,WORK),"absent observation cannot certify");
            fail(ContextGroundCompositionProof.prove(null,observed.get(0),16,WORK),"absent receipt cannot certify");
            Snapshot cx = snapshot(true,"!a(1);"),ca = snapshot(false,"!~a(1);");
            Frontier cf = ContextOrdinaryResidualRounds.start(cx,Collections.singletonList(ca),WORK);
            Receipt collision = ContextOrdinaryResidualRounds.inspectWithProbes(cf,ca,16,WORK,100000,64);
            require(collision.verified && !collision.collisions.isEmpty(),"actual sourced collision receipt control");
            fail(ContextGroundCompositionProof.prove(collision,observed.get(0),16,WORK),"collision receipt cannot masquerade as a completed composition proof");
            require(r.input.products.isEmpty(),"proof checking does not mutate or publish to the shared frontier");
            CompositionCandidate original = observed.get(0);
            List<Support> mixed = new ArrayList<>(original.ownerSupports);
            Support first = mixed.get(0); mixed.add(new Support(first.sources,first.depth+1));
            fail(ContextGroundCompositionProof.prove(r,relabel(original,mixed),16,WORK),"one unauthenticated depth alternative rejects all proofs, including valid prefix");
            List<Support> fabricated = Collections.singletonList(new Support(Collections.singleton(new Source(new UUID(9,9),1,0,true,"fake")),0));
            fail(ContextGroundCompositionProof.prove(r,relabel(original,fabricated),16,WORK),"caller-labelled native replay cannot manufacture registered original provenance");
            boolean immutable = false; try { e.proofs.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; }
            require(immutable,"proof list immutable");
            // Alternative original clauses in one assertion keep their WHOLE source identity.
            Receipt conjunction = receipt(snapshot(true,"!(~a(1)||~b(1)||~c(1)) && marker(1);"),a);
            Evidence ce = prove(conjunction,candidates(conjunction).get(0));
            require(ce.proofs.get(0).support.sources.contains(conjunction.input.x.primary.get(0).source),"conjunctive original retains whole source occurrence");
            // Native-reduced compound plus another compound exercises cumulative depth.
            Receipt derived = receipt(snapshot(true,"!anchor(1)&&a(1)->r(1);"),snapshot(false,"!anchor(1); !d(1)->a(1);"));
            boolean cumulative = false;
            for (CompositionCandidate candidate : candidates(derived)) {
                Evidence de = prove(derived,candidate);
                for (Proof p : de.proofs) {
                    truthTable(p);
                    if (p.support.depth == 2 && p.support.sources.size() == 3) cumulative = true;
                }
            }
            require(cumulative,"derived owner retains its two causes plus partner and reaches depth2");
            Receipt alternatives = receipt(snapshot(true,"!a(1)&&b(1)->r(1); !p(1)->a(1);"),snapshot(false,"!a(1); !p(1); !d(1)->b(1);"));
            boolean alternativeGroups = false;
            for (CompositionCandidate candidate : candidates(alternatives)) {
                Evidence ae = prove(alternatives,candidate);
                if (ae.proofs.size() > 1) {
                    alternativeGroups = true;
                    for (Proof p : ae.proofs) {
                        truthTable(p);
                        require(p.support.sources.size() < 6,"alternative cause groups remain separate, not a union of all possible origins");
                    }
                    int deepest = ae.proofs.stream().mapToInt(p -> p.support.depth).max().getAsInt();
                    fail(ContextGroundCompositionProof.prove(alternatives,candidate,deepest-1,WORK),"late alternative depth overflow returns no earlier successful alternative");
                }
            }
            require(alternativeGroups,"separate actual unit proof recipes induce separate composition alternatives");
            Receipt duplicateRemainder = receipt(snapshot(true,"!a(1)||b(1);"),snapshot(false,"!~a(1)||b(1);"));
            Evidence unit = prove(duplicateRemainder,candidates(duplicateRemainder).get(0));
            require(unit.proofs.get(0).conclusion.literals.size() == 1 && unit.proofs.get(0).conclusion.literals.get(0).predicate.equals("b"),"equal retained literals factor to one checked ground unit");
            truthTable(unit.proofs.get(0));
            Receipt tautology = receipt(snapshot(true,"!a(1)||b(1);"),snapshot(false,"!~a(1)||~b(1);"));
            for (CompositionCandidate candidate : candidates(tautology)) for (Proof p : prove(tautology,candidate).proofs) truthTable(p);

            // Every sign orientation of a pivot and two independent retained literals.
            int cases = 0;
            for (int bits=0;bits<8;++bits) {
                String pivot = (bits&1)==0 ? "a(1)" : "~a(1)", opposite = (bits&1)==0 ? "~a(1)" : "a(1)";
                String left = (bits&2)==0 ? "b(1)" : "~b(1)",right = (bits&4)==0 ? "c(1)" : "~c(1)";
                Receipt sr = receipt(snapshot(true,"!"+pivot+"||"+left+";"),snapshot(false,"!"+opposite+"||"+right+";"));
                for (CompositionCandidate candidate : candidates(sr)) {
                    Evidence se = prove(sr,candidate); for (Proof p : se.proofs) truthTable(p); ++cases;
                }
            }
            require(cases == 8,"all signed binary orientations produce checked native pairs");
            System.out.println("GROUND_COMPOSITION_TRUTH_TABLE cases="+cases);
            System.out.println("CONTEXT_GROUND_COMPOSITION_PROOF_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
