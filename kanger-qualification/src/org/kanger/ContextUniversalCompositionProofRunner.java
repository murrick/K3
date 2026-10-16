/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextUniversalCompositionProof.*;

/** Checked universal resolution with independent finite-model counterexample search. */
public final class ContextUniversalCompositionProofRunner {
    private static int checks,ids;
    private static final int WORK = 16000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result b = ContextProductionNativeBinding.bind(m,inventory,WORK,500000);
        require(b.bound,"native binding"); return b.snapshot;
    }
    private static Receipt receipt(Snapshot x,Snapshot a) {
        Frontier f = ContextOrdinaryResidualRounds.start(x,Collections.singletonList(a),WORK);
        Receipt r = ContextOrdinaryResidualRounds.inspectWithProbes(f,a,16,WORK,500000,256);
        require(r.verified && r.collisions.isEmpty(),"verified actual ledger: "+r.reason); return r;
    }
    private static Clause clause(Snapshot s) { return s.primary.get(0).clauses.get(0); }
    private static int pivot(Clause c,String predicate) {
        for (int i=0;i<c.literals.size();++i) if (c.literals.get(i).predicate.equals(predicate)) return i;
        throw new AssertionError("pivot absent");
    }
    private static Evidence prove(Receipt r,Clause a,Clause b,String predicate) {
        Evidence e = ContextUniversalCompositionProof.prove(r,a,pivot(a,predicate),b,pivot(b,predicate),16,WORK);
        require(e.proved && !e.proofs.isEmpty(),"proof: "+e.reason);
        require(e.input == r.input && e.target == r.target && e.scope == Scope.ORDINARY_FLAT_UNIVERSAL_BINARY_RESOLUTION && e.reason.contains("admission unknown"),"exact scope and no transport/admission authority");
        for (Proof p : e.proofs) {
            Set<Source> sources = new HashSet<>(p.ownerSupport.sources); sources.addAll(p.partnerSupport.sources);
            require(p.support.sources.equals(sources) && p.support.depth == Math.max(p.ownerSupport.depth,p.partnerSupport.depth)+1,"whole original alternative pair and cumulative depth retained");
        }
        return e;
    }
    private static void fail(Evidence e,String why) { require(!e.proved && e.proofs.isEmpty(),why+": "+e.reason); }
    private static void instances(Clause clause,List<Integer> variables,int index,Map<Integer,SemanticTermSnapshot> assignment,List<SemanticTermSnapshot> domain,List<Clause> result) {
        if (index == variables.size()) { result.add(clause.instantiate(assignment)); return; }
        for (SemanticTermSnapshot v : domain) { assignment.put(variables.get(index),v); instances(clause,variables,index+1,assignment,domain,result); }
        assignment.remove(variables.get(index));
    }
    private static boolean truth(Clause c,List<Fact> atoms,int bits) {
        for (Atom a : c.literals) {
            Fact f = a.fact(),positive = f.positive ? f : f.opposite(); int at = -1;
            for (int i=0;i<atoms.size();++i) if (atoms.get(i).equivalent(positive)) { at = i; break; }
            if (at < 0) throw new AssertionError("oracle atom missing");
            if ((((bits>>at)&1) != 0) == f.positive) return true;
        }
        return false;
    }
    private static void oracle(Proof proof) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m);
        // Fresh values are test models, never input to proof generation or a domain restriction.
        List<SemanticTermSnapshot> domain = new ArrayList<>();
        for (Clause c : Arrays.asList(proof.owner,proof.partner,proof.conclusion)) for (Atom atom : c.literals) for (Slot slot : atom.arguments)
            if (slot.variable < 0 && domain.stream().noneMatch(v -> v.semanticallyEquals(slot.value))) domain.add(slot.value);
        boolean noConstants = domain.isEmpty();
        domain.add(SemanticTermSnapshot.capture(m.getTerms().add("FreshNeverMentionedOne")));
        if (noConstants) domain.add(SemanticTermSnapshot.capture(m.getTerms().add("FreshNeverMentionedTwo")));
        List<Clause> parents = new ArrayList<>(),conclusions = new ArrayList<>();
        for (Clause c : Arrays.asList(proof.owner,proof.partner)) instances(c,new ArrayList<>(c.variables),0,new HashMap<>(),domain,parents);
        instances(proof.conclusion,new ArrayList<>(proof.conclusion.variables),0,new HashMap<>(),domain,conclusions);
        List<Fact> atoms = new ArrayList<>();
        List<Clause> all = new ArrayList<>(parents); all.addAll(conclusions);
        for (Clause c : all) for (Atom a : c.literals) {
            Fact f = a.fact(),p = f.positive ? f : f.opposite(); if (atoms.stream().noneMatch(old -> old.equivalent(p))) atoms.add(p);
        }
        require(atoms.size() <= 14,"independent finite oracle remains bounded");
        int satisfyingParents = 0;
        for (int bits=0;bits<(1<<atoms.size());++bits) {
            boolean antecedent = true; for (Clause c : parents) antecedent &= truth(c,atoms,bits);
            if (!antecedent) continue; ++satisfyingParents;
            for (Clause c : conclusions) require(truth(c,atoms,bits),"all finite models of universal parents satisfy every result instance, including fresh values");
        }
        require(satisfyingParents > 0,"oracle implication has nonvacuous parent models");
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-composition-proof-").toString());
            Snapshot x = snapshot(true,"!@x @y p(x,1)||left(y);"),a = snapshot(false,"!@x ~p(2,x)||right(x);");
            Receipt r = receipt(x,a); Clause left = clause(x),right = clause(a);
            Evidence separated = prove(r,left,right,"p");
            Clause expected = clause(snapshot(false,"!@z left(z)||right(1);"));
            require(separated.proofs.get(0).conclusion.equivalent(expected),"separate variable namespaces avoid false constant conflict and preserve unbound left variable");
            oracle(separated.proofs.get(0));
            Snapshot rx = snapshot(true,"!@x p(x,1)||left(x);"),ra = snapshot(false,"!@x ~p(2,x)||right(x);");
            Evidence crossed = prove(receipt(rx,ra),clause(rx),clause(ra),"p");
            require(crossed.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!left(2)||right(1);"))),"independent same-named variables bind to different values coherently"); oracle(crossed.proofs.get(0));
            Snapshot ix = snapshot(true,"!@x @y p(x)||left(y);"),ia = snapshot(false,"!@x @y ~p(y)||right(x);");
            Evidence independent = prove(receipt(ix,ia),clause(ix),clause(ia),"p");
            require(independent.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!@u @v left(u)||right(v);"))) && independent.proofs.get(0).conclusion.variables.size() == 2,"unrelated retained variables never collapse just because local slot numbers match"); oracle(independent.proofs.get(0));
            Snapshot repeatX = snapshot(true,"!@x p(x,x)||left(x);"),bad = snapshot(false,"!~p(1,2)||right(1);");
            Receipt badReceipt = receipt(repeatX,bad);
            fail(ContextUniversalCompositionProof.prove(badReceipt,clause(repeatX),pivot(clause(repeatX),"p"),clause(bad),pivot(clause(bad),"p"),16,WORK),"repeated variable cannot be independently rebound to contradictory constants");
            Snapshot repeatedPartner = snapshot(false,"!@y @z ~p(y,z)||right(y,z);");
            Evidence repeated = prove(receipt(repeatX,repeatedPartner),clause(repeatX),clause(repeatedPartner),"p");
            require(repeated.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!@v left(v)||right(v,v);"))),"variable equivalence propagates through every remaining literal"); oracle(repeated.proofs.get(0));
            Snapshot factoredX = snapshot(true,"!@x p(x)||left(x);"),factoredA = snapshot(false,"!@y ~p(y)||left(y);");
            Evidence factored = prove(receipt(factoredX,factoredA),clause(factoredX),clause(factoredA),"p");
            require(factored.proofs.get(0).conclusion.literals.size() == 1 && factored.proofs.get(0).conclusion.variables.size() == 1,"identical substituted literals factor to universal unit proof, never a sampled ground fact"); oracle(factored.proofs.get(0));
            Snapshot originalX = snapshot(true,"!@x ~a(x)||~b(x)||~c(x);"),originalA = snapshot(false,"!@y d(y)->a(y);");
            Evidence original = prove(receipt(originalX,originalA),clause(originalX),clause(originalA),"a");
            require(original.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!@z ~d(z)||~b(z)||~c(z);"))) && original.proofs.get(0).support.depth == 1 && original.proofs.get(0).support.sources.size() == 2,"universal original X/A counterexample has exact uninstantiated resolvent and two original causes"); oracle(original.proofs.get(0));
            Snapshot derivedX = snapshot(true,"!@x @y anchor(1)&&rel(x,y)->out(x,y);"),derivedA = snapshot(false,"!anchor(1); !p(1); !p(1)->anchor(1); !@u @v out(u,v)->done(u,v);");
            Receipt derivedReceipt = receipt(derivedX,derivedA);
            Clause derivedParent = derivedReceipt.localProofs.stream().filter(p -> p.clause.variables.size() == 2 && p.clause.literals.size() == 2
                    && p.clause.literals.stream().anyMatch(at -> at.predicate.equals("rel")) && p.clause.literals.stream().anyMatch(at -> at.predicate.equals("out"))).findFirst().get().clause;
            Clause derivedPartner = derivedA.primary.stream().flatMap(entry -> entry.clauses.stream()).filter(c -> c.literals.stream().anyMatch(at -> at.predicate.equals("done"))).findFirst().get();
            Evidence derived = prove(derivedReceipt,derivedParent,derivedPartner,"out");
            Clause derivedExpected = clause(snapshot(false,"!@x @y rel(x,y)->done(x,y);"));
            require(derived.proofs.stream().allMatch(p -> p.conclusion.equivalent(derivedExpected)),"whole two-variable derived parent composes without losing bindings");
            require(derived.proofs.stream().anyMatch(p -> p.support.depth == 2 && p.support.sources.size() == 3)
                    && derived.proofs.stream().anyMatch(p -> p.support.depth == 3 && p.support.sources.size() == 4),"independent actual original cause groups stay separate and cumulative depth increases");
            for (Proof p : derived.proofs) oracle(p);
            fail(ContextUniversalCompositionProof.prove(derivedReceipt,derivedParent,pivot(derivedParent,"out"),derivedPartner,pivot(derivedPartner,"out"),2,WORK),"late deeper source alternative cannot expose earlier successful proof prefix");
            Snapshot sign = snapshot(false,"!@x p(x,1)||other(x);"); Receipt sameSign = receipt(x,sign);
            fail(ContextUniversalCompositionProof.prove(sameSign,left,pivot(left,"p"),clause(sign),pivot(clause(sign),"p"),16,WORK),"same sign is not resolution");
            fail(ContextUniversalCompositionProof.prove(r,left,-1,right,0,16,WORK),"pivot outside whole clause");
            fail(ContextUniversalCompositionProof.prove(r,left,pivot(left,"p"),right,pivot(right,"p"),0,WORK),"depth stop exposes no proof prefix");
            fail(ContextUniversalCompositionProof.prove(r,left,pivot(left,"p"),right,pivot(right,"p"),16,1),"work stop exposes no proof prefix");
            Receipt self = ContextOrdinaryResidualRounds.inspectWithProbes(r.input,x,16,WORK,500000,256);
            fail(ContextUniversalCompositionProof.prove(self,left,pivot(left,"p"),right,pivot(right,"p"),16,WORK),"foreign whole parent cannot authenticate in self ledger");
            Snapshot invented = snapshot(false,"!@x ~p(2,x)||invented(x);");
            fail(ContextUniversalCompositionProof.prove(r,left,pivot(left,"p"),clause(invented),pivot(clause(invented),"p"),16,WORK),"matching pivot cannot substitute an unauthenticated whole parent");
            Receipt unverified = ContextOrdinaryResidualRounds.inspectWithProbes(r.input,a,16,1,500000,256);
            fail(ContextUniversalCompositionProof.prove(unverified,left,pivot(left,"p"),right,pivot(right,"p"),16,WORK),"unverified receipt cannot grant schema proof");
            fail(ContextUniversalCompositionProof.prove(null,left,pivot(left,"p"),right,pivot(right,"p"),16,WORK),"no receipt cannot grant proof");
            require(r.input.products.isEmpty(),"detached schema proof creates no shared production");
            boolean immutable = false; try { separated.proofs.clear(); } catch (UnsupportedOperationException e) { immutable = true; } require(immutable,"proof list immutable");
            immutable = false; try { separated.proofs.get(0).conclusion.variables.clear(); } catch (UnsupportedOperationException e) { immutable = true; } require(immutable,"universal result variable scope immutable");
            System.out.println("CONTEXT_UNIVERSAL_COMPOSITION_PROOF_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
