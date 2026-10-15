/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessFamilyFixedPoint.Branch;
import org.kanger.ContextWitnessFamilyFixedPoint.Limits;
import org.kanger.ContextWitnessFamilyFixedPoint.Plan;
import org.kanger.ContextWitnessFamilyFixedPoint.Status;
import org.kanger.enums.QueryPass;

/** Complete common barriers, immutable plans, and proof-sensitive fixed points. */
public final class ContextWitnessFamilyFixedPointRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static final Limits LIMITS = new Limits(12,16,WORK,100000);
    private static Source s(int c,int n) { return new Source(new UUID(0,c),1,n,c == 0,"base"); }
    private static Declaration d(Source source,String text) { return new Declaration(source,text); }
    private static final Declaration X = d(s(0,10),"!@w p(w,1)->warm(w,1);");
    private static Declaration a(int id) { return d(s(1,id),"!@w p(w,1)->q(w,1);"); }
    private static Declaration b() { return d(s(2,2),"!@w q(w,1)&&flag(w,1)->out(w,1);"); }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Packet base() throws Exception {
        Bundle bundle = ContextWitnessBundle.capture(d(s(0,0),"!$w p(w,1)&&flag(w,1);"),10000);
        try (Session r = ContextWitnessBundle.restoreObserved(Arrays.asList(bundle),10000,100000)) {
            r.capture.replay(r.mind,X.source,X.statement); r.mind.setQueryPass(QueryPass.ACCEPT); r.mind.link(null,false);
            ContextWitnessResultTransport.Result result = ContextWitnessResultTransport.export(r,16,WORK);
            require(result.exported,"base packet exports"); return result.packet;
        }
    }
    private static Branch branch(int context,Declaration... rules) {
        List<Declaration> declarations = new ArrayList<>(); declarations.add(X); declarations.addAll(Arrays.asList(rules));
        return ContextWitnessFamilyFixedPoint.branch(Cut.of(s(context,0)),declarations);
    }
    private static Packet step(Packet input,Declaration rule) throws Exception {
        try (ContextWitnessSeedReplay r = ContextWitnessSeedReplay.open(input,10000,100000)) {
            r.rule(rule.source,rule.statement); ContextWitnessResultTransport.Result result = ContextWitnessResultTransport.exportNext(r,16,WORK);
            require(result.exported,"actual seed preparation: "+result.reason); return result.packet;
        }
    }
    private static Plan plan() { return ContextWitnessFamilyFixedPoint.plan(Arrays.asList(branch(0),branch(1,a(1)),branch(2,b()))); }
    private static void incomplete(ContextWitnessFamilyFixedPoint.Result result,String why) {
        require(result.status == Status.INCOMPLETE && result.packet == null && result.rounds.isEmpty(),why+": "+result.reason);
    }
    private static Item item(Packet p,String name) { return p.items.stream().filter(i -> i.atom.predicate.equals(name)).findFirst().orElseThrow(() -> new AssertionError(name)); }
    private static boolean proof(Item item,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return item.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    private static void invalid(List<Branch> branches,String why) {
        boolean refused = false; try { ContextWitnessFamilyFixedPoint.plan(branches); } catch (IllegalArgumentException expected) { refused = true; }
        require(refused,why);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-fixed-point-").toString());
            Packet base = base(); Plan plan = plan();
            ContextWitnessFamilyFixedPoint.Result result = plan.run(base,LIMITS);
            require(result.status == Status.FIXED_POINT,"complete positive Horn family reaches scoped fixed point: "+result.reason);
            require(result.plan == plan && result.initialIdentity.equals(base.identity),"fixed-point evidence binds exact immutable plan and starting packet");
            require(result.rounds.size() == 3,"A product, next-barrier B product, then one unchanged fully covered round");
            require(result.rounds.get(0).productive == 1 && result.rounds.get(0).unchanged == 2 && result.rounds.get(0).changed,"B cannot see same-round q");
            require(result.rounds.get(1).productive == 1 && result.rounds.get(1).changed,"B activates only in the second common-input round");
            require(!result.rounds.get(2).changed && result.rounds.get(2).productive == 0 && result.rounds.get(2).unchanged == 3,"fixed point requires every branch closed without new evidence");
            require(result.rounds.get(0).input.equals(base.identity) && result.rounds.get(0).output.equals(result.rounds.get(1).input)
                    && result.rounds.get(1).output.equals(result.rounds.get(2).input),"every barrier output is exactly the next common input");
            require(proof(item(result.packet,"out"),2,s(0,0),s(1,1),s(2,2)),"native cross-round result retains exact complete depth2 proof");
            require(result.packet.coveredCut == null && result.packet.emptyCut == null && !result.packet.observedStep,"family packet cannot masquerade as any branch certificate");
            ContextWitnessFamilyFixedPoint.Result reverse = ContextWitnessFamilyFixedPoint.plan(Arrays.asList(branch(2,b()),branch(1,a(1)),branch(0))).run(base,LIMITS);
            require(reverse.status == Status.FIXED_POINT && reverse.rounds.size() == 3 && ContextWitnessResultTransport.sameContents(result.packet,reverse.packet,WORK),"reversing branch order preserves proof pool and synchronous activation rounds");
            ContextWitnessFamilyFixedPoint.Result repeated = plan.run(result.packet,LIMITS);
            require(repeated.status == Status.FIXED_POINT && repeated.rounds.size() == 1 && repeated.rounds.get(0).productive == 0,"already closed enabled rules need no native observations");
            require(ContextWitnessResultTransport.sameContents(result.packet,repeated.packet,WORK),"fixed point preserves all whole proof groups");
            ContextWitnessResultTransport.Result unchanged = ContextWitnessResultTransport.exportHornUnchanged(result.packet,Cut.of(s(2,2)),Arrays.asList(X,b()),16,WORK,100000);
            require(unchanged.exported && unchanged.packet.emptyCut != null && unchanged.packet.coveredCut != null,"enabled but already covered Horn branch has an unchanged certificate");
            require(ContextWitnessResultTransport.sameContents(result.packet,unchanged.packet,WORK),"unchanged certificate never adds rule sources to old proofs");
            // All atoms exist initially; provenance still propagates over two distinct barriers.
            Packet old = step(step(base,a(1)),b());
            Plan alternative = ContextWitnessFamilyFixedPoint.plan(Arrays.asList(branch(0),branch(1,a(4)),branch(2,b())));
            ContextWitnessFamilyFixedPoint.Result alternatives = alternative.run(old,LIMITS);
            require(alternatives.status == Status.FIXED_POINT && alternatives.rounds.size() == 3,"new donor authority forces propagation then another unchanged barrier");
            require(old.items.size() == alternatives.packet.items.size() && alternatives.rounds.get(0).changed && alternatives.rounds.get(1).changed,"unchanged atoms cannot hide changing proof alternatives");
            require(item(alternatives.packet,"out").proofs.size() == 2 && proof(item(alternatives.packet,"out"),2,s(0,0),s(1,1),s(2,2))
                    && proof(item(alternatives.packet,"out"),2,s(0,0),s(1,4),s(2,2)),"both independent out alternatives survive the fixed point");
            require(!proof(item(alternatives.packet,"out"),2,s(0,0),s(1,1),s(1,4),s(2,2)),"alternative authorities are never flattened");
            Plan cycle = ContextWitnessFamilyFixedPoint.plan(Arrays.asList(branch(0),branch(1,a(1)),branch(2,d(s(2,2),"!@w q(w,1)->p(w,1);"))));
            ContextWitnessFamilyFixedPoint.Result cycled = cycle.run(base,LIMITS);
            require(cycled.status == Status.FIXED_POINT && cycled.rounds.size() == 4,"cycle propagates new source groups then closes without enumerating longer redundant paths: "+cycled.reason);
            require(proof(item(cycled.packet,"p"),2,s(0,0),s(1,1),s(2,2)) && proof(item(cycled.packet,"q"),3,s(0,0),s(1,1),s(2,2)),"cycle preserves minimum depth for each exact source group");
            require(proof(item(cycled.packet,"warm"),3,s(0,0),s(1,1),s(2,2),s(0,10)),"self-X pass also requalifies returning provenance");
            incomplete(plan.run(base,new Limits(2,16,WORK,100000)),"round cap cannot publish a changed prefix as fixed point");
            incomplete(plan.run(base,new Limits(12,1,WORK,100000)),"depth stop in a later round publishes no prefix");
            incomplete(plan.run(base,new Limits(12,16,1,100000)),"preflight work stop publishes no prefix");
            incomplete(plan.run(base,new Limits(12,16,WORK,1)),"event stop publishes no prefix");
            Plan unsupported = ContextWitnessFamilyFixedPoint.plan(Arrays.asList(branch(0),branch(1,a(1)),branch(2,d(s(2,2),"!@x @y q(x,1)->out(y,1);"))));
            incomplete(unsupported.run(base,LIMITS),"unsupported later branch is preflighted before any rounds");
            Plan negative = ContextWitnessFamilyFixedPoint.plan(Arrays.asList(branch(0),branch(1,d(s(1,1),"!@w p(w,1)->~q(w,1);"))));
            incomplete(negative.run(base,LIMITS),"signed rules cannot earn a positive-family fixed point");
            incomplete(plan.run(step(base,d(s(1,9),"!@w p(w,1)->~n(w,1);")),LIMITS),"signed input cannot earn positive-family coverage");
            invalid(Arrays.asList(branch(1,a(1)),branch(2,b())),"self-X branch cannot be omitted");
            invalid(Arrays.asList(branch(0),branch(1,a(1)),branch(1,a(4))),"context cannot appear twice");
            invalid(Arrays.asList(branch(0),ContextWitnessFamilyFixedPoint.branch(Cut.of(s(1,1)),Arrays.asList(a(1)))),"foreign branch cannot omit common X declarations");
            invalid(Arrays.asList(branch(0),ContextWitnessFamilyFixedPoint.branch(Cut.of(s(1,1)),Arrays.asList(d(X.source,"!@w p(w,1)->other(w,1);"),a(1)))),"same X label cannot conceal different branch declarations");
            invalid(Arrays.asList(ContextWitnessFamilyFixedPoint.branch(Cut.of(s(0,0)),Arrays.asList(X,a(1)))) ,"self-X snapshot cannot contain raw foreign declarations");
            List<Declaration> mutableRules = new ArrayList<>(Arrays.asList(X,a(1))); Branch sealedA = ContextWitnessFamilyFixedPoint.branch(Cut.of(s(1,1)),mutableRules);
            mutableRules.clear(); List<Branch> mutablePlan = new ArrayList<>(Arrays.asList(branch(0),sealedA,branch(2,b()))); Plan sealed = ContextWitnessFamilyFixedPoint.plan(mutablePlan); mutablePlan.clear();
            require(sealed.run(base,LIMITS).status == Status.FIXED_POINT,"caller list mutations cannot change the sealed execution plan");
            boolean immutable = false; try { result.rounds.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"successful audit trace is immutable");
            System.out.println("CONTEXT_WITNESS_FAMILY_FIXED_POINT_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
