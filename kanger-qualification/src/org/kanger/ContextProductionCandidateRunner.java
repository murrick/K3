/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionCandidate.*;
import org.kanger.ContextWitnessResultTransport.Item;
import org.kanger.ContextWitnessResultTransport.Packet;

/** Detached candidate evidence, explicit source rebasing and no physical mutation. */
public final class ContextProductionCandidateRunner {
    private static final int WORK = 4000000;
    private static int checks;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static final class Fixture {
        final Mind mind; final Snapshot snapshot;
        Fixture(int id,boolean x,String text) throws Exception {
            User user = new User(); mind = new Mind(user); user.setCurrentMind(mind);
            if (!text.isEmpty()) require(mind.compile(text,null,false),"native fixture");
            ContextProductionInventory inventory = ContextProductionInventory.inspect(mind,new UUID(0,id),1,x,Collections.emptyList(),1000);
            ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(mind,inventory,WORK,100000);
            require(bound.bound,"whole native binding"); snapshot = bound.snapshot;
        }
        void unchanged() throws Exception {
            ContextProductionInventory i = snapshot.inventory;
            require(i.fingerprint.equals(ContextProductionInventory.inspect(mind,i.context,i.revision,i.localX,i.initialization,1000).fingerprint),"physical inventory unchanged by trial");
        }
    }
    private static Source source(Snapshot s,String fragment) { return s.primary.stream().filter(e -> e.statement.contains(fragment)).findFirst().get().source; }
    private static Item item(Packet packet,String name) { return packet.items.stream().filter(i -> i.atom.predicate.equals(name)).findFirst().orElseThrow(() -> new AssertionError(name)); }
    private static boolean proof(Item item,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return item.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    private static void refused(Result result,String why) { require(!result.prepared && result.artifact == null,why+": "+result.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("production-candidate-").toString());
            Fixture x = new Fixture(0,true,"!$w p(w,1)&&flag(w,1); !@w p(w,1)->warm(w,1);");
            Fixture a = new Fixture(1,false,"!@w p(w,1)->q(w,1);");
            List<Snapshot> foreign = new ArrayList<>(Arrays.asList(a.snapshot)); String text = "!@w q(w,1)->r(w,1);";
            Result result = ContextProductionCandidate.prepare(x.snapshot,foreign,text,1000,WORK,100000);
            require(result.prepared,"new rule staged without first publishing it in X: "+result.reason); Artifact trial = result.artifact;
            require(trial.base == x.snapshot && trial.statement.equals(text) && trial.staged.primary.size() == x.snapshot.primary.size()+1,"receipt retains exact base and one added native primary");
            require(trial.candidate.localX && trial.candidate.context.equals(x.snapshot.inventory.context) && trial.candidate.revision == x.snapshot.inventory.revision,"candidate keeps actual parent context and historical revision");
            require(!trial.candidate.view.equals(x.snapshot.primary.get(0).source.view) && trial.candidate.view.equals("inventory:"+trial.staged.inventory.fingerprint),"candidate has a distinct authenticated effective view");
            require(trial.rebased.keySet().equals(new HashSet<>(Arrays.asList(source(x.snapshot,"$w"),source(x.snapshot,"@w")))),"every original primary has one explicit staged occurrence");
            require(new HashSet<>(trial.rebased.values()).size() == trial.rebased.size() && !trial.rebased.containsValue(trial.candidate),"candidate cannot alias any rebased original source");
            require(trial.family.foreign.get(0) == a.snapshot && trial.family.foreign.get(0).primary.get(0).source.equals(a.snapshot.primary.get(0).source),"foreign pin and source identities remain exact");
            foreign.clear();
            ContextProductionFamilyAssembly.Evaluation closed = trial.evaluate(12,16,WORK,100000);
            require(closed.status == ContextProductionFamilyAssembly.Status.FIXED_POINT,"detached candidate family closes: "+closed.reason);
            require(proof(item(closed.qualification.fixedPoint.packet,"r"),2,trial.rebased.get(source(x.snapshot,"$w")),source(a.snapshot,"@w"),trial.candidate),"candidate result cites rebased X, original foreign and actual candidate source at depth2");
            require(trial.family.foreign.size() == 1,"caller list changes cannot change frozen trial"); x.unchanged(); a.unchanged();
            require(x.snapshot.primary.stream().noneMatch(e -> e.statement.equals(text)),"candidate was not inserted in original primary inventory");
            boolean immutable = false; try { trial.rebased.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"source receipt is immutable");
            Result repeat = ContextProductionCandidate.prepare(x.snapshot,Arrays.asList(a.snapshot),text,1000,WORK,100000);
            require(repeat.prepared && !repeat.artifact.identity.equals(trial.identity) && !repeat.artifact.candidate.view.equals(trial.candidate.view),"independent trial cannot reuse another candidate's source authority");
            Evaluation collision = ContextProductionCandidate.qualify(x.snapshot,Arrays.asList(a.snapshot),"!@w q(w,1)->~flag(w,1);",1000,12,16,WORK,100000);
            require(collision.status == ContextProductionFamilyAssembly.Status.COLLISION && collision.artifact != null,"delayed candidate collision is proved without publication: "+collision.reason);
            require(collision.qualification.qualification.collisions.stream().flatMap(c -> c.evidence.stream()).anyMatch(e -> e.proofs.stream().anyMatch(p -> p.sources.contains(collision.artifact.candidate))),"refutation identifies the actual candidate authority");
            x.unchanged(); a.unchanged();
            refused(ContextProductionCandidate.prepare(x.snapshot,Arrays.asList(a.snapshot),"?p(1);",1000,WORK,100000),"queries are outside candidate assertion boundary");
            refused(ContextProductionCandidate.prepare(x.snapshot,Arrays.asList(a.snapshot),"!a(1); !b(1);",1000,WORK,100000),"multiple statements are not one candidate occurrence");
            refused(ContextProductionCandidate.prepare(x.snapshot,Arrays.asList(a.snapshot),"",1000,WORK,100000),"empty candidate refuses");
            refused(ContextProductionCandidate.prepare(x.snapshot,Arrays.asList(a.snapshot),"!@w p(w,1)->warm(w,1);",1000,WORK,100000),"duplicate/replacement candidate cannot hide a missing new primary");
            refused(ContextProductionCandidate.prepare(x.snapshot,Arrays.asList(a.snapshot),text,1000,1,100000),"work stop returns no candidate prefix");
            refused(ContextProductionCandidate.prepare(x.snapshot,Arrays.asList(a.snapshot),text,1,WORK,100000),"whole family row bound returns no candidate prefix");
            require(ContextProductionCandidate.qualify(x.snapshot,Arrays.asList(a.snapshot),text,1000,12,16,WORK,1).status == ContextProductionFamilyAssembly.Status.INCOMPLETE,"event stop cannot qualify an unfinished trial");
            refused(ContextProductionCandidate.prepare(a.snapshot,Collections.emptyList(),text,1000,WORK,100000),"foreign snapshot cannot be used as X");
            refused(ContextProductionCandidate.prepare(x.snapshot,Arrays.asList(a.snapshot,a.snapshot),text,1000,WORK,100000),"duplicate foreign identity refuses");
            require(trial.evaluate(1,16,WORK,100000).status == ContextProductionFamilyAssembly.Status.INCOMPLETE,"bounded trial never publishes unfinished family closure");
            Evaluation outside = ContextProductionCandidate.qualify(x.snapshot,Arrays.asList(a.snapshot),"!@w universal(w,1);",1000,12,16,WORK,100000);
            require(outside.status == ContextProductionFamilyAssembly.Status.INCOMPLETE,"unsupported universal unit cannot earn admission from successful parsing");
            Fixture emptyX = new Fixture(2,true,"");
            Evaluation first = ContextProductionCandidate.qualify(emptyX.snapshot,Collections.emptyList(),"!scalar(1);",1000,12,16,WORK,100000);
            require(first.status == ContextProductionFamilyAssembly.Status.FIXED_POINT,"candidate may supply the first actual seed to an empty base"); emptyX.unchanged();
            Fixture local = new Fixture(3,true,"!scalar(1);");
            refused(ContextProductionCandidate.prepare(local.snapshot,Collections.emptyList(),"!~scalar(1);",1000,WORK,100000),"local native rejection returns no prepared artifact"); local.unchanged();
            x.unchanged(); a.unchanged();
            System.out.println("CONTEXT_PRODUCTION_CANDIDATE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
