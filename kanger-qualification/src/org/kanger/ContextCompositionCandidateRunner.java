/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.enums.QueryPass;

/** Native ground branch metadata must not be promoted to facts or composed proofs. */
public final class ContextCompositionCandidateRunner {
    private static int checks;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Source source(int id) { return new Source(new UUID(0,id),1,0,id == 0,"base"); }
    private static Snapshot observe(boolean enabled,int limit,String... texts) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); m.setQueryPass(QueryPass.ACCEPT);
        try (ContextProductionCapture c = ContextProductionCapture.begin(m,limit)) {
            if (enabled) c.observeCompositionCandidates();
            for (int i=0;i<texts.length;++i) c.replay(m,source(i),texts[i]);
            m.link(null,false); return c.snapshot();
        }
    }
    private static boolean contains(Clause clause,Fact fact) { return clause.literals.stream().anyMatch(a -> fact.equivalent(a.fact())); }
    private static void authenticate(Snapshot observed) {
        for (CompositionCandidate event : observed.compositionCandidates) {
            require(event.owner.variables.isEmpty() && event.partner.variables.isEmpty(),"ground whole branches only");
            require(contains(event.owner,event.pivot) && contains(event.partner,event.pivot.opposite()),"actual opposite ground pivots in whole clauses");
            for (Support proof : event.ownerSupports) require(observed.inputs.stream().anyMatch(i -> i.source != null && proof.sources.equals(Collections.singleton(i.source)) && i.clauses.stream().anyMatch(c -> c.equivalent(event.owner))),"owner authenticates to complete source branch");
            for (Support proof : event.partnerSupports) require(observed.inputs.stream().anyMatch(i -> i.source != null && proof.sources.equals(Collections.singleton(i.source)) && i.clauses.stream().anyMatch(c -> c.equivalent(event.partner))),"partner authenticates to complete source branch");
        }
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("composition-candidates-").toString());
            String x = "!~a(1)||~b(1)||~c(1);",a = "!d(1)->a(1);";
            Snapshot baseline = observe(false,10000,x,a),enabled = observe(true,10000,x,a);
            System.out.println("NATIVE_COMPOSITION_CANDIDATES count="+enabled.compositionCandidates.size()+" gaps="+enabled.gaps);
            require(baseline.compositionCandidates.isEmpty(),"capture remains opt-in");
            require(!enabled.compositionCandidates.isEmpty(),"original missing composition is visited by native branch traversal");
            authenticate(enabled);
            require(enabled.compositionCandidates.stream().anyMatch(e -> e.pivot.predicate.equals("a") && e.owner.literals.size()+e.partner.literals.size() == 5),"complete original three-literal and two-literal branches retained");
            require(enabled.applications.size() == baseline.applications.size() && enabled.residuals.size() == baseline.residuals.size(),"candidate metadata creates neither application nor residual proof");
            require(enabled.inputs.size() == baseline.inputs.size(),"metadata does not compile new rules");
            require(enabled.eventCount == baseline.eventCount+3,"two unique native branches and one candidate consume event budget");
            Snapshot aliases = observe(true,10000,x,a,x); authenticate(aliases);
            require(aliases.compositionCandidates.stream().anyMatch(e -> e.ownerSupports.size() == 2 || e.partnerSupports.size() == 2),"duplicate primary origins retain alternative source occurrences");
            User du = new User(); Mind dm = new Mind(du); du.setCurrentMind(dm); dm.setQueryPass(QueryPass.ACCEPT);
            List<Support> carried = Arrays.asList(new Support(new LinkedHashSet<>(Arrays.asList(source(0),source(2))),3),new Support(Collections.singleton(source(3)),1));
            try (ContextProductionCapture dc = ContextProductionCapture.begin(dm,10000)) {
                dc.observeCompositionCandidates(); dc.replayDerived(dm,enabled.inputs.get(0).clause,carried); dc.replay(dm,source(1),a); dm.link(null,false);
                Snapshot derived = dc.snapshot();
                require(derived.compositionCandidates.stream().anyMatch(e -> e.ownerSupports.equals(carried) || e.partnerSupports.equals(carried)),"carried whole-source alternatives and cumulative depths are copied without reset");
            }
            require(enabled.gaps.equals(baseline.gaps),"candidate metadata discharges no existing capture gaps");
            Snapshot reverse = observe(true,10000,a,x); authenticate(reverse);
            require(!reverse.compositionCandidates.isEmpty(),"reverse replay also observes interaction");
            require(observe(true,10000,x,"!d(2)->a(2);").compositionCandidates.isEmpty(),"different ground values cannot form candidate");
            require(observe(true,10000,x,"!~d(1)||~a(1);").compositionCandidates.isEmpty(),"same-sign literals cannot form candidate");
            boolean queryRejected = false;
            try { observe(true,10000,x,"?a(1);"); } catch (IllegalArgumentException expected) { queryRejected = true; }
            require(queryRejected,"query cannot be replayed as sourced candidate knowledge");
            require(observe(true,10000,"!@x ~a(x)||~b(x)||~c(x);","!@x d(x)->a(x);").compositionCandidates.isEmpty(),"unbound schemas remain outside ground observation scope");
            User su = new User(); Mind root = new Mind(su); su.setCurrentMind(root);
            Mind siblingA = new Mind(root),siblingB = new Mind(root);
            siblingA.setQueryPass(QueryPass.ACCEPT); siblingB.setQueryPass(QueryPass.ACCEPT);
            try (ContextProductionCapture sc = ContextProductionCapture.begin(root,10000)) {
                sc.observeCompositionCandidates(); sc.replay(siblingA,source(0),x); sc.replay(siblingB,source(1),a);
                siblingA.link(null,false); siblingB.link(null,false);
                require(sc.snapshot().compositionCandidates.isEmpty(),"one journal cannot combine independent native sibling scopes");
            }
            boolean immutable = false; try { enabled.compositionCandidates.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"snapshot candidate list immutable");
            immutable = false; try { enabled.compositionCandidates.get(0).ownerSupports.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"candidate alternatives immutable");
            boolean stopped = false; try { observe(true,enabled.eventCount-1,x,a); } catch (IllegalStateException expected) { stopped = true; }
            require(stopped,"candidate observations share bounded event allowance");
            System.out.println("CONTEXT_COMPOSITION_CANDIDATE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
