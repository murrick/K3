/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionFamilyAssembly.*;
import org.kanger.ContextProductionFamilyAssembly.Result;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextProductionCapture.Source;

/** Every native-bound row must have an explicit local seed/producer or foreign-cut disposition. */
public final class ContextProductionFamilyAssemblyRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(int context,boolean x,String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); if (!text.isEmpty()) require(m.compile(text,null,false),"native snapshot fixture");
        ContextProductionInventory inventory = ContextProductionInventory.inspect(m,new UUID(0,context),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result result = ContextProductionNativeBinding.bind(m,inventory,WORK,10000);
        require(result.bound,"native snapshot binds: "+result.reason); return result.snapshot;
    }
    private static Artifact assemble(Snapshot x,Snapshot... foreign) {
        Result result = ContextProductionFamilyAssembly.assemble(x,Arrays.asList(foreign),WORK);
        require(result.assembled,"full family assembles: "+result.reason); return result.artifact;
    }
    private static Item item(Packet packet,String name) { return packet.items.stream().filter(i -> i.atom.predicate.equals(name)).findFirst().orElseThrow(() -> new AssertionError(name)); }
    private static boolean proof(Item item,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return item.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    private static Source source(Snapshot s,String fragment) { return s.primary.stream().filter(e -> e.statement.contains(fragment)).findFirst().get().source; }
    private static void refused(Result result,String why) { require(!result.assembled && result.artifact == null,why+": "+result.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("family-assembly-").toString());
            Snapshot x = snapshot(0,true,"!$w p(w,1)&&flag(w,1); !@w p(w,1)->warm(w,1); !scalar(John,2);");
            Snapshot a = snapshot(1,false,"!@w p(w,1)->q(w,1);"),b = snapshot(2,false,"!@w q(w,1)&&flag(w,1)->out(w,1);");
            Artifact family = assemble(x,a,b);
            require(family.seedAtoms == 3 && family.rows.size() == x.primary.size()+a.primary.size()+b.primary.size(),"every original primary row is accounted and only actual X literal seeds enter the packet");
            require(family.input.items.stream().allMatch(i -> i.proofs.stream().allMatch(p -> p.depth == 0 && p.sources.size() == 1)),"primary seed supports keep original singleton sources at depth0");
            Source witness = source(x,"$w"),scalar = source(x,"scalar"),warm = source(x,"@w"),aq = source(a,"@w"),bout = source(b,"@w");
            require(proof(item(family.input,"p"),0,witness) && proof(item(family.input,"flag"),0,witness) && proof(item(family.input,"scalar"),0,scalar),"whole witness and scalar facts retain exact distinct native source IDs");
            require(item(family.input,"p").atom.arguments.get(0).origin.equals(item(family.input,"flag").atom.arguments.get(0).origin),"global source/binder sharing survives seed assembly");
            require(family.input.items.stream().noneMatch(i -> i.atom.predicate.equals("warm") || i.atom.predicate.equals("q")),"generated X cache is not a primary seed and raw foreign rule result is absent");
            require(!family.input.observedStep && family.input.coveredCut == null && family.input.emptyCut == null,"primary seed packet does not masquerade as a productive or covered step");
            require(family.plan.branches.size() == 3 && family.plan.branches.get(0).declarations.size() == 1,"self branch contains actual X producer, not its seed declarations");
            require(family.rows.stream().filter(r -> r.use == Use.FOREIGN_CUT).count() == 2 && family.rows.stream().filter(r -> r.use == Use.X_SEEDS).count() == 2,"disposition ledger preserves all seed/foreign rows without fake source IDs");
            Evaluation result = family.evaluate(12,16,WORK,100000);
            require(result.status == Status.FIXED_POINT,"actual bound snapshots reach scoped fixed point: "+result.reason);
            Packet closed = result.qualification.fixedPoint.packet;
            require(proof(item(closed,"out"),2,witness,aq,bout) && proof(item(closed,"warm"),1,witness,warm),"native inference rebuilds cache results with exact original producer provenance");
            require(item(closed,"out").proofs.stream().noneMatch(p -> p.sources.contains(scalar)),"irrelevant scalar source is not fabricated into an output proof");
            require(assemble(x,b,a).evaluate(12,16,WORK,100000).status == Status.FIXED_POINT,"snapshot order cannot obstruct common-input qualification");
            Snapshot rawFact = snapshot(3,false,"!private(4); !@w p(w,1)->tail(w,1);"); Artifact privateCut = assemble(x,rawFact);
            require(privateCut.input.items.stream().noneMatch(i -> i.atom.predicate.equals("private")),"foreign ground fact stays outside X primary seed pool");
            require(privateCut.plan.branches.get(1).declarations.size() == 3,"foreign raw fact remains in the complete own-cut declarations");
            Evaluation incomplete = privateCut.evaluate(12,16,WORK,100000);
            require(incomplete.status == Status.INCOMPLETE && incomplete.qualification.uncovered.contains(source(rawFact,"private")),"unsupported scalar producer coverage cannot disappear into a positive verdict");
            Snapshot rawWitness = snapshot(4,false,"!$v foreignseed(v,1); !@w p(w,1)->tail(w,1);"); Artifact foreignBundle = assemble(x,rawWitness);
            require(foreignBundle.input.items.stream().noneMatch(i -> i.atom.predicate.equals("foreignseed")) && foreignBundle.plan.branches.get(1).declarations.stream().anyMatch(d -> d.statement.contains("$v")),"whole foreign witness remains an explicit raw own-cut declaration");
            require(foreignBundle.evaluate(12,16,WORK,100000).status == Status.INCOMPLETE,"unsupported foreign existential replay is not omitted");
            Snapshot mixed = snapshot(5,true,"!$w p(w,1); !@x (scalar(1)&&(p(x,1)->warm(x,1)));"); Artifact mixedFamily = assemble(mixed);
            require(mixedFamily.rows.stream().anyMatch(r -> r.use == Use.X_SEEDS_AND_PRODUCER && r.seedLiterals == 1),"mixed source row has both explicit seed and whole producer disposition");
            require(mixedFamily.plan.branches.get(0).declarations.get(0).statement.contains("scalar") && item(mixedFamily.input,"scalar").proofs.get(0).sources.contains(source(mixed,"scalar")),"mixed conjunction is never split into synthetic source identities");
            require(mixedFamily.evaluate(12,16,WORK,100000).status == Status.INCOMPLETE,"uncovered mixed-source unit remains explicit");
            Snapshot signed = snapshot(6,true,"!$w p(w,1); !@w p(w,1)->warm(w,1); !~scalar(1);"); Artifact signedFamily = assemble(signed);
            require(!item(signedFamily.input,"scalar").atom.positive && signedFamily.evaluate(12,16,WORK,100000).status == Status.INCOMPLETE,"negative scalar fact is retained rather than dropped to fit positive inference");
            Snapshot scalarPremise = snapshot(7,false,"!@w p(w,1)&&scalar(1)->q(w,1);");
            Snapshot scalarX = snapshot(8,true,"!$w p(w,1); !@w p(w,1)->warm(w,1); !scalar(1);");
            require(assemble(scalarX,scalarPremise).evaluate(12,16,WORK,100000).status == Status.INCOMPLETE,"current scalar/mixed native proof gap is surfaced, not silently excluded");
            refused(ContextProductionFamilyAssembly.assemble(snapshot(9,true,"!$w p(w,1);"),Collections.emptyList(),WORK),"no synthetic rule supplies a missing self-X producer cut");
            refused(ContextProductionFamilyAssembly.assemble(snapshot(10,true,"!@w p(w,1)->warm(w,1);"),Collections.emptyList(),WORK),"no synthetic seed supplies a missing X knowledge anchor");
            refused(ContextProductionFamilyAssembly.assemble(x,Arrays.asList(snapshot(11,false,"")),WORK),"empty foreign cut requires explicit future completion support");
            refused(ContextProductionFamilyAssembly.assemble(a,Arrays.asList(b),WORK),"foreign-role snapshot cannot become X");
            refused(ContextProductionFamilyAssembly.assemble(x,Arrays.asList(x),WORK),"X snapshot cannot be relabelled foreign");
            refused(ContextProductionFamilyAssembly.assemble(x,Arrays.asList(a,a),WORK),"duplicate foreign Context cannot be accepted twice");
            refused(ContextProductionFamilyAssembly.assemble(x,Arrays.asList(a),1),"assembly work stop returns no partial artifact");
            StringBuilder large = new StringBuilder(); for (int i=0;i<9;++i) large.append("!$w p").append(i).append("(w,1); "); large.append("!@w p0(w,1)->warm(w,1);");
            refused(ContextProductionFamilyAssembly.assemble(snapshot(12,true,large.toString()),Collections.emptyList(),WORK),"aggregate witness-rank stop returns no seed/plan prefix");
            boolean immutable = false; try { family.rows.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"row accounting ledger is immutable");
            Snapshot constraint = snapshot(13,false,"!@w out(w,1)->~flag(w,1);");
            Evaluation collision = assemble(x,a,b,constraint).evaluate(12,16,WORK,100000);
            require(collision.status == Status.COLLISION && collision.qualification.barrier == 2,"snapshot-assembled full cut finds delayed rule-mediated collision");
            System.out.println("CONTEXT_PRODUCTION_FAMILY_ASSEMBLY_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
