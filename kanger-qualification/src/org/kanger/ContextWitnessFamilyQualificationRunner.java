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
import org.kanger.ContextWitnessFamilyQualification.Status;
import org.kanger.enums.QueryPass;

/** Full-cut collision monitors must never turn a partial positive search into admission. */
public final class ContextWitnessFamilyQualificationRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static final Limits LIMITS = new Limits(12,16,WORK,100000);
    private static Source s(int c,int n) { return new Source(new UUID(0,c),1,n,c == 0,"base"); }
    private static Declaration d(Source source,String text) { return new Declaration(source,text); }
    private static final Declaration X = d(s(0,10),"!@w p(w,1)->warm(w,1);");
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Branch branch(int context,Declaration... rules) {
        List<Declaration> declarations = new ArrayList<>(); declarations.add(X); declarations.addAll(Arrays.asList(rules));
        return ContextWitnessFamilyFixedPoint.branch(Cut.of(s(context,0)),declarations);
    }
    private static Plan plan(Branch... branches) { return ContextWitnessFamilyFixedPoint.plan(Arrays.asList(branches)); }
    private static Packet base() throws Exception {
        Bundle bundle = ContextWitnessBundle.capture(d(s(0,0),"!$w p(w,1)&&flag(w,1);"),10000);
        try (Session r = ContextWitnessBundle.restoreObserved(Arrays.asList(bundle),10000,100000)) {
            r.capture.replay(r.mind,X.source,X.statement); r.mind.setQueryPass(QueryPass.ACCEPT); r.mind.link(null,false);
            Result result = ContextWitnessResultTransport.export(r,16,WORK); require(result.exported,"base exports"); return result.packet;
        }
    }
    private static Packet step(Packet input,Declaration declaration) throws Exception {
        try (ContextWitnessSeedReplay r = ContextWitnessSeedReplay.open(input,10000,100000)) {
            r.rule(declaration.source,declaration.statement); Result result = ContextWitnessResultTransport.exportNext(r,16,WORK);
            require(result.exported,"native preparation: "+result.reason); return result.packet;
        }
    }
    private static void incomplete(ContextWitnessFamilyQualification.Result result,String why) {
        require(result.status == Status.INCOMPLETE && result.fixedPoint == null && result.collisions.isEmpty() && result.barrier == -1,why+": "+result.reason);
    }
    private static boolean proof(ContextWitnessClauseCollision.Evidence e,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return e.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-family-qualification-").toString());
            Packet base = base(); Declaration aq = d(s(1,1),"!@w p(w,1)->q(w,1);"),br = d(s(2,2),"!@w p(w,1)->r(w,1);");
            Declaration guard = d(s(3,3),"!@w q(w,1)&&r(w,1)->~flag(w,1);");
            Plan supplied = plan(branch(0),branch(1,aq),branch(2,br),branch(3,guard));
            ContextWitnessFamilyQualification.Result collision = ContextWitnessFamilyQualification.run(supplied,base,LIMITS);
            require(collision.status == Status.COLLISION && collision.barrier == 1 && collision.collisions.size() == 1,"full C rule refutes products from the first complete common barrier: "+collision.reason);
            require(collision.supplied == supplied && collision.initialIdentity.equals(base.identity) && collision.fixedPoint == null,"refutation binds the full supplied plan and returns no accepted pool");
            ContextWitnessClauseCollision.Result evidence = collision.collisions.get(0);
            require(evidence.scanned && evidence.cut.equals(Cut.of(s(3,3))) && evidence.evidence.size() == 1,"evidence identifies the actual target cut and completed direct-instance scan");
            require(proof(evidence.evidence.get(0),2,s(0,0),s(1,1),s(2,2),s(3,3)),"refutation keeps complete X/A/B/C source proof at depth2");
            require(collision.uncovered.equals(Collections.singleton(s(3,3))),"unsupported productive coverage remains explicit even for a proved collision");
            ContextWitnessFamilyQualification.Result reverse = ContextWitnessFamilyQualification.run(plan(branch(3,guard),branch(2,br),branch(1,aq),branch(0)),base,LIMITS);
            require(reverse.status == Status.COLLISION && reverse.barrier == 1 && proof(reverse.collisions.get(0).evidence.get(0),2,s(0,0),s(1,1),s(2,2),s(3,3)),"reversing cuts preserves collision barrier and complete proof");
            Declaration bout = d(s(2,2),"!@w q(w,1)&&flag(w,1)->out(w,1);");
            Declaration delayedGuard = d(s(3,3),"!@w out(w,1)->~flag(w,1);");
            Plan delayed = plan(branch(0),branch(1,aq),branch(2,bout),branch(3,delayedGuard));
            ContextWitnessFamilyQualification.Result later = ContextWitnessFamilyQualification.run(delayed,base,LIMITS);
            require(later.status == Status.COLLISION && later.barrier == 2,"collision qualification repeats after every changed barrier");
            require(proof(later.collisions.get(0).evidence.get(0),3,s(0,0),s(1,1),s(2,2),s(3,3)),"later collision retains full two-round donor depth");
            Packet q = step(base,aq); Declaration denyQ = d(s(3,3),"!@w ~q(w,1);");
            ContextWitnessFamilyQualification.Result initial = ContextWitnessFamilyQualification.run(plan(branch(0),branch(3,denyQ)),q,LIMITS);
            require(initial.status == Status.COLLISION && initial.barrier == 0 && initial.collisions.get(0).inputIdentity.equals(q.identity),"already proved collision is found at initial barrier without native inference");
            Plan noCollision = plan(branch(0),branch(1,aq),branch(2,br),branch(3,d(s(3,3),"!@w q(w,1)&&r(w,1)->~other(w,1);")));
            ContextWitnessFamilyQualification.Result partial = ContextWitnessFamilyQualification.run(noCollision,base,LIMITS);
            incomplete(partial,"closed positive discovery does not complete omitted signed rules");
            require(partial.uncovered.contains(s(3,3)),"incomplete result records the exact uncovered declaration");
            ContextWitnessFamilyQualification.Result closed = ContextWitnessFamilyQualification.run(plan(branch(0),branch(1,aq),branch(2,bout)),base,LIMITS);
            require(closed.status == Status.FIXED_POINT && closed.uncovered.isEmpty() && closed.collisions.isEmpty() && closed.fixedPoint != null,"full supported positive snapshots retain their scoped fixed-point verdict");
            require(closed.barrier == 3 && closed.fixedPoint.rounds.size() == 3,"final unchanged barrier is collision-checked too");
            // Raw sibling knowledge is never collected as X-derived witness literals.
            incomplete(ContextWitnessFamilyQualification.run(plan(branch(0),branch(1,d(s(1,1),"!@w q(w,1);")),branch(2,d(s(2,2),"!@w ~q(w,1);"))),base,LIMITS),"opposite raw sibling units are not a fabricated X collision or completed proof pool");
            Plan malformed = plan(branch(0),branch(3,denyQ),branch(4,d(s(4,4),"!$w unknown(w,1);")));
            incomplete(ContextWitnessFamilyQualification.run(malformed,q,LIMITS),"unsupported later full snapshot suppresses an initial collision prefix");
            Declaration selfDenial = d(s(0,30),"!@w ~q(w,1);");
            Plan onlyConstraints = plan(ContextWitnessFamilyFixedPoint.branch(Cut.of(s(0,0)),Arrays.asList(selfDenial)));
            incomplete(ContextWitnessFamilyQualification.run(onlyConstraints,base,LIMITS),"no supported self-X producer snapshot cannot become fixed point");
            ContextWitnessFamilyQualification.Result selfCollision = ContextWitnessFamilyQualification.run(onlyConstraints,q,LIMITS);
            require(selfCollision.status == Status.COLLISION && selfCollision.barrier == 0 && proof(selfCollision.collisions.get(0).evidence.get(0),2,s(0,0),s(1,1),s(0,30)),"initial self-X refutation needs no supported positive discovery kernel");
            ContextWitnessClauseCollision.Result absent = ContextWitnessClauseCollision.inspect(base,Cut.of(s(3,3)),Arrays.asList(X,denyQ),16,WORK,100000);
            require(absent.scanned && absent.status == ContextWitnessClauseCollision.Status.INCOMPLETE && absent.evidence.isEmpty(),"completed direct scan without evidence is not consistency");
            ContextWitnessClauseCollision.Result stopped = ContextWitnessClauseCollision.inspect(q,Cut.of(s(3,3)),Arrays.asList(X,denyQ),16,WORK,1);
            require(!stopped.scanned && stopped.evidence.isEmpty(),"resource-stopped direct scan is explicitly distinguishable");
            incomplete(ContextWitnessFamilyQualification.run(delayed,base,new Limits(1,16,WORK,100000)),"round cap cannot publish discovery prefix");
            incomplete(ContextWitnessFamilyQualification.run(supplied,base,new Limits(12,1,WORK,100000)),"refutation-depth stop cannot publish evidence prefix");
            incomplete(ContextWitnessFamilyQualification.run(supplied,base,new Limits(12,16,1,100000)),"work stop yields no verdict prefix");
            incomplete(ContextWitnessFamilyQualification.run(supplied,base,new Limits(12,16,WORK,1)),"event stop yields no verdict prefix");
            Packet signed = step(base,d(s(1,9),"!@w p(w,1)->~n(w,1);"));
            incomplete(ContextWitnessFamilyQualification.run(supplied,signed,LIMITS),"signed discovery frontiers remain outside the positive kernel");
            boolean immutable = false; try { collision.collisions.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"collision list is immutable");
            immutable = false; try { partial.uncovered.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"uncovered declaration set is immutable");
            System.out.println("CONTEXT_WITNESS_FAMILY_QUALIFICATION_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
