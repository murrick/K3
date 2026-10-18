/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;

/** Native nullary binding, signed transport and mixed ordinary/witness families. */
public final class ContextNullaryPendingCommitRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    static class Fixture extends ContextAutomaticPendingCommitRunner.Fixture {
        Fixture(Path dir,String name) throws Exception {super(dir,name);}
    }
    static void create(Path dir,String name,String text) throws Exception {try(Fixture f=new Fixture(dir,name)) {require(f.root.compile(text,null,false),"native fixture "+name);}}
    static ContextPendingProductionSnapshotSession.Decision inspect(Fixture f,String text) throws Exception {
        Mind child=new Mind(f.root);require(child.compile(text,null,false),"prepare complete native nullary/mixed child");
        try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
            ContextPendingProductionSnapshotSession.Decision result=lease.inspectAutomatic(16,16,16,20000,1000000,WORK,500000,WORK,WORK,100000);
            lease.requireCurrent();return result;
        } finally {f.root.release(child);}
    }
    static StorageLifecycleException refuse(Fixture f,String text) throws Exception {
        StorageLifecycleException seen=null;try {f.root.query(text,null,false);} catch(StorageLifecycleException expected) {seen=expected;}
        require(seen!=null && seen.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"actual native nullary/mixed collision refused");return seen;
    }
    static void erasureAccepted(Fixture f,String text) throws Exception {
        long revision=f.db.getRevision();f.guard.install();
        require(Boolean.TRUE.equals(f.root.query(text,null,false)),"observed witness-erasing materialization permits fully checked native source write");
        require(f.db.getRevision()>revision,"supported erasure source publishes through original native commit");
    }
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("nullary-pending-");System.setProperty("user.home",dir.toString());
            create(dir,"A","!left; !p;");create(dir,"B","!right; !~p;");
            create(dir,"UA","!left(1); !p(1);");create(dir,"UB","!right; !~p(1);");
            create(dir,"Plus","!ready;");create(dir,"Minus","!~ready;");create(dir,"UnaryMinus","!~ready(0);");
            create(dir,"OrB","!~p;");create(dir,"Seed","!seed(1);");
            create(dir,"WA","!@w gate && seed(w,1)->q(w,1);");
            create(dir,"WR","!@w seed(w,1)->ready;");
            long published;
            try(Fixture f=new Fixture(dir,"Origins")) {
                require(f.root.compile("!ready; !~off(); !ready(0);",null,false),"native nullary spellings and nonempty arity coexist");
                ContextPendingProductionSnapshotSession.Decision decision=inspect(f,"!marker;");
                require(decision.engine==ContextPendingProductionSnapshotSession.Engine.GROUND && decision.ground.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT,"whole nullary family selects ground and certifies");
                require(decision.ground.certificate.models.get(0).positiveAtoms.stream().anyMatch(a -> a.predicate.equals("ready") && a.arguments.isEmpty()),"model retains nullary positive atom");
                require(decision.ground.certificate.models.get(0).positiveAtoms.stream().anyMatch(a -> a.predicate.equals("ready") && a.arguments.size()==1),"model keeps unary zero-value atom distinct");
                f.guard.install();require(Boolean.TRUE.equals(f.root.query("!extra;",null,false)),"strict ordinary authoring with existing nullary primaries");
                require(Boolean.TRUE.equals(f.root.query("?ready();",null,false)) && Boolean.TRUE.equals(f.root.query("?~off;",null,false)),"native query truth preserves spellings and signs");published=f.db.getRevision();
            }
            try(Fixture f=new Fixture(dir,"Origins");ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)) {
                require(f.db.getRevision()>=published && lease.inventory().x.primary.size()==4,"durable native nullary sources reopen without extra materialized facts");
            }
            for(boolean negative:new boolean[]{false,true}) try(Fixture f=new Fixture(dir,"Direct"+negative)) {
                require(f.root.compile("!anchor(1);",null,false),"direct parent");f.db.connectContext(negative?"Plus":"Minus");long revision=f.db.getRevision();f.guard.install();
                StorageLifecycleException failure=refuse(f,negative?"!~ready;":"!ready();");ContextGroundClauseFamily.Evaluation evidence=((ContextGroundProductionCommitException)failure).getFamilyEvaluation();
                require(evidence.collisions.stream().anyMatch(c -> c.left.arguments.isEmpty() && c.right.arguments.isEmpty() && c.left.positive!=c.right.positive),"both nullary signs have exact opposite proof, no dummy argument");
                require(f.db.getRevision()==revision,"direct nullary refusal before publication");
            }
            try(Fixture f=new Fixture(dir,"Arity")) {
                require(f.root.compile("!ready;",null,false),"nullary source");f.db.connectContext("UnaryMinus");f.guard.install();
                require(Boolean.TRUE.equals(f.root.query("!extra;",null,false)),"nullary ready does not collide with negative ready(0)");
            }
            for(boolean reverse:new boolean[]{false,true}) {
                try(Fixture f=new Fixture(dir,"Ground"+reverse)) {
                    require(f.root.compile("!anchor(1);",null,false),"ground parent");f.db.connectContext(reverse?"B":"A");f.db.connectContext(reverse?"A":"B");
                    ContextPendingProductionSnapshotSession.Decision decision=inspect(f,"!left&&right->p;");
                    require(decision.engine==ContextPendingProductionSnapshotSession.Engine.GROUND && decision.ground.status==ContextGroundClauseFamily.Status.COLLISION && decision.ground.rounds==2,"all-nullary masked conflict requires complete barrier2 in both orders");
                    long revision=f.db.getRevision();f.guard.install();refuse(f,"!left&&right->p;");require(f.db.getRevision()==revision,"masked nullary conflict does not publish");
                }
                try(Fixture f=new Fixture(dir,"Ground"+reverse);ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)) {require(lease.inventory().x.primary.size()==1,"reopen excludes refused nullary rule/products");}
                try(Fixture f=new Fixture(dir,"Universal"+reverse)) {
                    require(f.root.compile("!anchor(1);",null,false),"universal parent");f.db.connectContext(reverse?"UB":"UA");f.db.connectContext(reverse?"UA":"UB");
                    String rule="!@x left(x)&&right->p(x);";ContextPendingProductionSnapshotSession.Decision decision=inspect(f,rule);
                    require(decision.engine==ContextPendingProductionSnapshotSession.Engine.UNIVERSAL && decision.universal.audit.verified && decision.universal.inspection.status==ContextUniversalFamilyInspection.Status.COLLISION && decision.universal.inspection.completedBarriers==2,"mixed nullary/universal rule retains independently audited barrier2 conflict");
                    f.guard.install();StorageLifecycleException failure=refuse(f,rule);require(failure instanceof ContextProductionCommitException && ((ContextProductionCommitException)failure).getFamilyAudit().verified,"actual mixed native refusal carries independent audit");
                }
            }
            try(Fixture f=new Fixture(dir,"GroundOr")) {
                require(f.root.compile("!anchor(1);",null,false),"disjunctive parent");f.db.connectContext("OrB");ContextPendingProductionSnapshotSession.Decision decision=inspect(f,"!p||q;");
                require(decision.ground.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT && decision.ground.certificate.models.size()==2,"nullary disjunction closes with separate checked models");
                f.guard.install();require(Boolean.TRUE.equals(f.root.query("!p||q;",null,false)),"actual nullary disjunction accepted");
            }
            try(Fixture f=new Fixture(dir,"UniversalSafe")) {
                require(f.root.compile("!gate;",null,false),"nullary universal premise");f.db.connectContext("Seed");String rule="!@x gate&&seed(x)->safe(x);";
                ContextPendingProductionSnapshotSession.Decision decision=inspect(f,rule);require(decision.universal.audit.verified && decision.universal.inspection.status==ContextUniversalFamilyInspection.Status.CONSISTENT,"nullary pivot/premise survives all-value model audit");
                f.guard.install();require(Boolean.TRUE.equals(f.root.query(rule,null,false)),"actual mixed universal rule accepted");
            }
            for(boolean first:new boolean[]{false,true}) try(Fixture f=new Fixture(dir,"Witness"+first)) {
                require(f.root.compile((first?"!$w gate&&seed(w,1);":"!$w seed(w,1)&&gate;")+" !@w seed(w,1)->warm(w,1);",null,false),"witness bundle with nullary atom at either position");f.db.connectContext("WA");
                String rule="!@w q(w,1)->out(w,1);";ContextPendingProductionSnapshotSession.Decision decision=inspect(f,rule);
                require(decision.engine==ContextPendingProductionSnapshotSession.Engine.WITNESS && decision.witness.status==ContextProductionFamilyAssembly.Status.FIXED_POINT,"whole shared witness bundle and nullary premise qualify");
                f.guard.install();require(Boolean.TRUE.equals(f.root.query(rule,null,false)),"native witness publication preserves nullary premise");
                StorageLifecycleException failure=refuse(f,"!@w q(w,1)->~gate;");ContextProductionFamilyAssembly.Evaluation evidence=((ContextWitnessProductionCommitException)failure).getFamilyEvaluation();
                require(evidence.refutations.stream().anyMatch(r -> r.clause.literals.stream().anyMatch(a -> a.arguments.isEmpty()) && !r.proofs.isEmpty()),"nullary witness projection retains full Source refutation proofs");
            }
            try(Fixture f=new Fixture(dir,"WitnessConclusion")) {
                require(f.root.compile("!$w seed(w,1)&&gate; !@w seed(w,1)->warm(w,1);",null,false),"witness source for nullary conclusion");f.db.connectContext("WR");
                ContextPendingProductionSnapshotSession.Decision decision=inspect(f,"!ready->out;");
                require(decision.witness.status==ContextProductionFamilyAssembly.Status.FIXED_POINT,"nullary witness-erasing conclusion now has full observed closure: "+decision.witness.reason);
                erasureAccepted(f,"!ready->out;");


            }
            create(dir,"WU","!@w seed(w,1)->ready(1);");
            try(Fixture f=new Fixture(dir,"UnaryConclusionControl")) {
                require(f.root.compile("!$w seed(w,1)&&flag(w,1); !@w seed(w,1)->warm(w,1);",null,false),"same witness source for unary conclusion");f.db.connectContext("WU");
                ContextPendingProductionSnapshotSession.Decision decision=inspect(f,"!ready(1)->out(1);");
                require(decision.witness.status==ContextProductionFamilyAssembly.Status.FIXED_POINT,"all-unary witness-erasing conclusion now has full observed closure: "+decision.witness.reason);
                erasureAccepted(f,"!ready(1)->out(1);");
            }
            create(dir,"NotOutZero","!~out;");create(dir,"NotOutOne","!~out(1);");
            for(boolean unary:new boolean[]{false,true}) try(Fixture f=new Fixture(dir,"ErasureConflict"+unary)) {
                require(f.root.compile("!$w seed(w,1)&&flag(w,1); !@w seed(w,1)->warm(w,1);",null,false),"witness-erasure cross-cut collision parent");
                f.db.connectContext(unary?"WU":"WR");f.db.connectContext(unary?"NotOutOne":"NotOutZero");long revision=f.db.getRevision();f.guard.install();
                StorageLifecycleException failure=refuse(f,unary?"!ready(1)->out(1);":"!ready->out;");ContextProductionFamilyAssembly.Evaluation evidence=((ContextWitnessProductionCommitException)failure).getFamilyEvaluation();
                require(evidence.refutations.stream().anyMatch(r -> r.proofs.stream().anyMatch(p -> p.depth>=2 && p.sources.stream().anyMatch(a -> a.localX)
                        && p.sources.stream().filter(a -> !a.localX).map(a -> a.context).distinct().count()>=2)),"erased result requalifies other cut with full X/A/B support and cumulative depth");
                require(f.db.getRevision()==revision,"erasure conflict never publishes candidate or production");
            }
            try(Fixture f=new Fixture(dir,"NativeNullaryConclusion")) {
                require(f.root.compile("!$w seed(w,1); !@w seed(w,1)->ready;",null,false),"native witness-elimination control setup");
                require(Boolean.TRUE.equals(f.root.query("?ready;",null,false)),"native inference independently confirms nullary witness-erasing conclusion");
            }
            System.out.println("CONTEXT_NULLARY_PENDING_COMMIT_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
