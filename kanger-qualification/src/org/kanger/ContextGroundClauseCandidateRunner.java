/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundClauseFamily.Status;

/** Ground candidates need no legacy positive seed and retain exact staged provenance. */
public final class ContextGroundClauseCandidateRunner {
    private static int checks,ids;
    private static final int WORK = 16000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static final class Fixture {
        final Mind mind; final Snapshot snapshot;
        Fixture(boolean x,String text) throws Exception {
            User u = new User(); mind = new Mind(u); u.setCurrentMind(mind);
            if (!text.isEmpty()) require(mind.compile(text,null,false),"native fixture");
            ContextProductionInventory i = ContextProductionInventory.inspect(mind,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
            ContextProductionNativeBinding.Result b = ContextProductionNativeBinding.bind(mind,i,WORK,500000);
            require(b.bound,"native binding"); snapshot = b.snapshot;
        }
        void unchanged() throws Exception {
            ContextProductionInventory i = snapshot.inventory;
            require(i.fingerprint.equals(ContextProductionInventory.inspect(mind,i.context,i.revision,i.localX,i.initialization,1000).fingerprint),"caller native inventory unchanged");
        }
    }
    private static ContextGroundClauseCandidate.Evaluation qualify(Fixture x,List<Snapshot> foreign,String text) {
        return ContextGroundClauseCandidate.qualify(x.snapshot,foreign,text,1000,16,16,16,WORK,500000,256,4096);
    }
    private static void incomplete(ContextGroundClauseCandidate.Evaluation e,String why) { require(e.status == Status.INCOMPLETE && (e.qualification == null || e.qualification.certificate == null),why+": "+e.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ground-clause-candidate-").toString());
            Fixture empty = new Fixture(true,""),a = new Fixture(false,"!left(1);"),b = new Fixture(false,"!right(1);");
            String rule = "!left(1)&&right(1)->p(1);";
            List<Snapshot> foreign = new ArrayList<>(Arrays.asList(a.snapshot,b.snapshot));
            ContextGroundClauseCandidate.Evaluation e = qualify(empty,foreign,rule);
            require(e.status == Status.GROUND_FIXED_POINT && e.qualification.certificate != null,"rule-only staged candidate qualifies without a legacy seed anchor: "+e.reason);
            require(e.artifact.base == empty.snapshot && e.artifact.staged.primary.size() == 1 && e.artifact.rebased.isEmpty(),"empty real X plus exactly one private native primary");
            require(e.qualification.certificate.frontier.x == e.artifact.staged && e.qualification.rounds == 3,"family certificate binds exact staged snapshot, not unstaged base");
            require(e.artifact.candidate.localX && e.artifact.candidate.context.equals(empty.snapshot.inventory.context) && e.artifact.candidate.revision == empty.snapshot.inventory.revision,"actual parent identity and historical revision retained");
            require(e.artifact.candidate.view.equals("inventory:"+e.artifact.staged.inventory.fingerprint),"candidate source has authenticated private view");
            Set<Source> expected = new HashSet<>(Arrays.asList(e.artifact.candidate,a.snapshot.primary.get(0).source,b.snapshot.primary.get(0).source));
            require(e.qualification.certificate.frontier.products.stream().anyMatch(p -> p.clause.literals.size() == 1 && p.clause.literals.get(0).predicate.equals("p") && p.proof.depth == 2 && p.proof.sources.equals(expected)),"qualified consequence retains actual candidate and original foreign pins/depth2");
            foreign.clear(); require(e.artifact.foreign.size() == 2,"caller list mutation cannot change staged family");
            empty.unchanged(); a.unchanged(); b.unchanged();
            ContextGroundClauseCandidate.Evaluation repeat = qualify(empty,Arrays.asList(a.snapshot,b.snapshot),rule);
            require(repeat.status == Status.GROUND_FIXED_POINT && !repeat.artifact.identity.equals(e.artifact.identity) && !repeat.artifact.candidate.view.equals(e.artifact.candidate.view),"another trial has distinct staged source authority");
            Fixture maskedA = new Fixture(false,"!left(1); !p(1);"),maskedB = new Fixture(false,"!right(1); !~p(1);");
            ContextGroundClauseCandidate.Evaluation collision = qualify(empty,Arrays.asList(maskedA.snapshot,maskedB.snapshot),rule);
            require(collision.status == Status.COLLISION && collision.qualification.rounds == 2 && collision.qualification.certificate == null,"actual masked candidate rejects after shared qualification without touching X");
            require(collision.qualification.collisions.stream().anyMatch(c -> c.leftProof.sources.contains(collision.artifact.candidate) || c.rightProof.sources.contains(collision.artifact.candidate)),"typed collision identifies this candidate occurrence");
            Fixture base = new Fixture(true,"!base(1);"),ca = new Fixture(false,"!d(1)->a(1);"),cb = new Fixture(false,"!d(1)->b(1);"),cc = new Fixture(false,"!d(1); !c(1);");
            ContextGroundClauseCandidate.Evaluation composed = qualify(base,Arrays.asList(ca.snapshot,cb.snapshot,cc.snapshot),"!~a(1)||~b(1)||~c(1);");
            require(composed.status == Status.COLLISION && composed.qualification.rounds == 3,"composed actual candidate rejects at common barrier3");
            require(composed.artifact.rebased.size() == 1 && composed.artifact.rebased.containsKey(base.snapshot.primary.get(0).source),"whole original X occurrence mapped to exactly one staged source");
            require(!composed.artifact.rebased.containsValue(composed.artifact.candidate),"candidate cannot alias a rebased original");
            require(composed.artifact.foreign.get(0) == ca.snapshot,"foreign source/view identity not rebased");
            base.unchanged(); ca.unchanged(); cb.unchanged(); cc.unchanged();
            incomplete(qualify(base,Collections.emptyList(),"?p(1);"),"query is not an assertion candidate");
            incomplete(qualify(base,Collections.emptyList(),"!p(1); !q(1);"),"source script cannot masquerade as one candidate");
            incomplete(qualify(base,Collections.emptyList(),"!base(1);"),"duplicate/replacement cannot masquerade as appended occurrence");
            incomplete(qualify(base,Collections.emptyList(),"!@x p(x)->q(x);"),"universal candidate remains outside ground scope");
            incomplete(ContextGroundClauseCandidate.qualify(empty.snapshot,Arrays.asList(a.snapshot,b.snapshot),rule,1000,1,16,16,WORK,500000,256,4096),"common round bound grants no candidate approval");
            incomplete(ContextGroundClauseCandidate.qualify(base.snapshot,Collections.emptyList(),"!new(1);",1000,16,16,16,1,500000,256,4096),"staging work bound exposes no candidate prefix");
            boolean immutable = false; try { composed.artifact.rebased.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; } require(immutable,"staged provenance immutable");
            immutable = false; try { composed.artifact.foreign.clear(); } catch (UnsupportedOperationException expectedFailure) { immutable = true; } require(immutable,"staged cuts immutable");
            empty.unchanged(); base.unchanged(); maskedA.unchanged(); maskedB.unchanged();
            System.out.println("CONTEXT_GROUND_CLAUSE_CANDIDATE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
