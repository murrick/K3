/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessClauseCollision.Evidence;
import org.kanger.enums.QueryPass;

/** Concrete rule-mediated refutations; absence of a refutation never admits a cut. */
public final class ContextWitnessClauseCollisionRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static Source s(int context,int id) { return new Source(new UUID(0,context),1,id,context == 0,"base"); }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Declaration d(Source source,String text) { return new Declaration(source,text); }
    private static final String RULE = "!@w q(w,1)&&r(w,1)->~flag(w,1);";
    private static Packet base(boolean independent) throws Exception {
        List<Bundle> bundles = new ArrayList<>();
        bundles.add(ContextWitnessBundle.capture(d(s(0,0),"!$w p(w,1)&&flag(w,1);"),10000));
        if (independent) bundles.add(ContextWitnessBundle.capture(d(s(0,20),"!$v seed(v,1);"),10000));
        try (Session r = ContextWitnessBundle.restoreObserved(bundles,10000,100000)) {
            r.capture.replay(r.mind,s(0,10),"!@w p(w,1)->warm(w,1);");
            r.mind.setQueryPass(QueryPass.ACCEPT); r.mind.link(null,false);
            Result result = ContextWitnessResultTransport.export(r,16,WORK);
            require(result.exported,"base packet: "+result.reason); return result.packet;
        }
    }
    private static Packet step(Packet input,Source source,String text) throws Exception {
        try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(input,10000,100000)) {
            round.rule(source,text); Result result = ContextWitnessResultTransport.exportNext(round,16,WORK);
            require(result.exported,"native step: "+result.reason); return result.packet;
        }
    }
    private static Packet merge(Packet input,Packet a,Packet b) {
        Map<Cut,Packet> outputs = new LinkedHashMap<>(); outputs.put(Cut.of(s(1,1)),a); outputs.put(Cut.of(s(2,2)),b);
        FamilyResult result = ContextWitnessResultTransport.family(input,new ArrayList<>(outputs.keySet()),WORK).finish(outputs,WORK);
        require(result.status == FamilyStatus.MERGED,"initial family has no direct literal opposition"); return result.packet;
    }
    private static ContextWitnessClauseCollision.Result inspect(Packet input,Declaration... declarations) {
        return ContextWitnessClauseCollision.inspect(input,Cut.of(s(3,3)),Arrays.asList(declarations),16,WORK,100000);
    }
    private static void incomplete(ContextWitnessClauseCollision.Result result,String why) {
        require(result.status == ContextWitnessClauseCollision.Status.INCOMPLETE && result.evidence.isEmpty(),why+": "+result.reason);
    }
    private static boolean proof(Evidence e,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return e.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-clause-collision-").toString());
            Packet base = base(false),a = step(base,s(1,1),"!@w p(w,1)->q(w,1);"),b = step(base,s(2,2),"!@w p(w,1)->r(w,1);");
            Declaration rule = d(s(3,3),RULE);
            incomplete(inspect(a,rule),"A alone cannot refute C"); incomplete(inspect(b,rule),"B alone cannot refute C");
            Packet merged = merge(base,a,b);
            ContextWitnessClauseCollision.Result conflict = inspect(merged,rule);
            require(conflict.status == ContextWitnessClauseCollision.Status.COLLISION && conflict.evidence.size() == 1,"merged products refute a concrete C rule instance");
            Evidence e = conflict.evidence.get(0);
            require(e.clause.literals.size() == 3 && e.opponents.size() == 3 && e.assignment.size() == 1,"one consistent substitution falsifies all three clause literals");
            require(proof(e,2,s(0,0),s(1,1),s(2,2),s(3,3)),"refutation retains complete joint source proof at max donor depth plus one");
            require(conflict.inputIdentity.equals(merged.identity) && conflict.cut.equals(Cut.of(s(3,99))),"certificate binds exact packet and source cut, independently of rule id");
            // Independent actual native inference is an oracle for this positive fixture.
            for (Packet input : Arrays.asList(a,b,merged)) {
                Packet next = step(input,s(3,3),RULE);
                FamilyResult nativeResult = ContextWitnessResultTransport.family(input,Collections.singletonList(Cut.of(s(3,3))),WORK)
                        .finish(Collections.singletonMap(Cut.of(s(3,3)),next),WORK);
                require(nativeResult.status == (input == merged ? FamilyStatus.CONFLICT : FamilyStatus.MERGED),"native cut settlement agrees on the concrete rule-mediated counterexample");
            }
            Packet qa;
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(base,10000,100000)) {
                round.rule(s(1,1),"!@w p(w,1)->q(w,1);"); round.rule(s(1,4),"!@w p(w,1)->q(w,1);");
                Result result = ContextWitnessResultTransport.exportNext(round,16,WORK); require(result.exported,"two q producers export"); qa = result.packet;
            }
            Packet alternatives = merge(base,qa,b); Evidence alt = inspect(alternatives,rule).evidence.get(0);
            require(alt.proofs.size() == 2 && proof(alt,2,s(0,0),s(1,1),s(2,2),s(3,3)) && proof(alt,2,s(0,0),s(1,4),s(2,2),s(3,3)),"refutation distributes over complete alternatives");
            require(!proof(alt,2,s(0,0),s(1,1),s(1,4),s(2,2),s(3,3)),"alternative authorities are not collapsed into one proof");
            Packet separate = base(true);
            Packet independent = merge(separate,step(separate,s(1,1),"!@w p(w,1)->q(w,1);"),step(separate,s(2,2),"!@w seed(w,1)->r(w,1);"));
            incomplete(inspect(independent,rule),"shared variable cannot match independent original witnesses");
            ContextWitnessClauseCollision.Result twoVariables = inspect(independent,d(s(3,3),"!@x @y q(x,1)&&r(y,1)->~flag(x,1);"));
            require(twoVariables.status == ContextWitnessClauseCollision.Status.COLLISION && twoVariables.evidence.get(0).assignment.size() == 2,"independent universal variables may legitimately bind independent witnesses");
            require(inspect(merged,d(s(3,3),"!@x @y q(x,1)&&r(y,1)->~flag(x,1);")).status == ContextWitnessClauseCollision.Status.COLLISION,"different variables may also bind the same witness");
            incomplete(inspect(merged,d(s(3,3),"!@w q(w,2)&&r(w,1)->~flag(w,1);")),"fixed argument mismatch");
            incomplete(inspect(merged,d(s(3,3),"!@w ~q(w,1)&&r(w,1)->~flag(w,1);")),"literal sign mismatch");
            require(inspect(merged,d(s(3,3),"!@w ~flag(w,1)||~r(w,1)||~q(w,1);")).status == ContextWitnessClauseCollision.Status.COLLISION,"clause literal order does not affect refutation");
            require(inspect(merged,d(s(3,3),"!@w ~q(w,1);")).status == ContextWitnessClauseCollision.Status.COLLISION,"universal unit clause can be refuted by a witness instance");
            incomplete(inspect(merged,d(s(3,3),"!$w ~q(w,1);")),"a fresh existential cannot be promoted to a universal instance");
            incomplete(inspect(merged,d(s(3,3),"!@w q(w,1)->~flag(w+1,1);")),"function argument outside proof scope");
            incomplete(inspect(merged,rule,d(s(3,4),"!@w q(w,1)&&r(w,1)&&p(w,1)->~flag(w,1);")),"unsupported later rule suppresses any earlier evidence prefix");
            incomplete(inspect(merged,rule,d(s(4,4),"!@w ~q(w,1);")),"raw sibling rule cannot enter the target cut");
            incomplete(inspect(merged,d(new Source(new UUID(0,3),2,3,false,"base"),RULE)),"target revision mismatch");
            incomplete(inspect(merged,d(new Source(new UUID(0,3),1,3,false,"changed"),RULE)),"target view mismatch");
            incomplete(inspect(merged,rule,rule),"source occurrence cannot be duplicated");
            incomplete(inspect(merged),"empty target declarations prove nothing");
            incomplete(ContextWitnessClauseCollision.inspect(merged,Cut.of(s(3,3)),Arrays.asList(rule),1,WORK,100000),"depth stop yields no evidence prefix");
            incomplete(ContextWitnessClauseCollision.inspect(merged,Cut.of(s(3,3)),Arrays.asList(rule),16,1,100000),"work stop yields no evidence prefix");
            incomplete(ContextWitnessClauseCollision.inspect(merged,Cut.of(s(3,3)),Arrays.asList(rule,d(s(3,4),"!@w ~q(w,1);")),16,WORK,1),"event stop yields no evidence prefix");
            ContextWitnessClauseCollision.Result self = ContextWitnessClauseCollision.inspect(merged,Cut.of(s(0,0)),Arrays.asList(d(s(0,30),RULE)),16,WORK,100000);
            require(self.status == ContextWitnessClauseCollision.Status.COLLISION && proof(self.evidence.get(0),2,s(0,0),s(1,1),s(2,2),s(0,30)),"X rule retains exact source declaration in its refutation");
            boolean immutable = false; try { e.assignment.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"certificate assignment is immutable");
            System.out.println("CONTEXT_WITNESS_CLAUSE_COLLISION_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
