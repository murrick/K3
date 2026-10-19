/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextOrdinaryResidualRounds.LedgerLimits;
import org.kanger.ContextOrdinaryResidualRounds.Product;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;

/** Fixed shape per driver keeps each bounded native matrix below the per-driver gate timeout. */
final class ContextSevenSignedNativeMatrix {
    static final int WORK=64000000;static int checks;
    static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static String arg(boolean nullary){return nullary?"":"(1)";}
    static String rule(boolean negative,boolean nullary){return ContextWideGroundFamilyRunner.rule(7,negative,nullary);}
    static void create(Path dir,String name,String text)throws Exception{try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){require(f.root.compile(text,null,false),"private source "+name);}}
    static void prepare(ContextSevenPremiseNativeWriteRunner.Fixture f,boolean reverse,String other,boolean nullary)throws Exception{
        require(f.root.compile(ContextWideGroundFamilyRunner.base(7,nullary),null,false),"six source premises");f.db.connectContext(reverse?other:"A");f.db.connectContext(reverse?"A":other);f.db.saveConnections(f.root);
    }
    static DB.GroundProductionWriteResult write(ContextSevenPremiseNativeWriteRunner.Fixture f,String text,LedgerLimits limits)throws Exception{return f.db.writeGroundProductionCandidate(f.root,text,1000,16,16,16,WORK,500000,100000,1000000,false,limits);}
    static boolean hasUnit(Snapshot snapshot,String name){return snapshot.primary.stream().anyMatch(e->e.clauses.stream().anyMatch(c->c.literals.size()==1&&c.literals.get(0).positive&&c.literals.get(0).predicate.equals(name)));}
    static void bounded(DB.GroundProductionWriteResult result,int cap,long revision){
        require(result.status==DB.ProductionWriteStatus.INCOMPLETE&&result.before.getRevision()==revision&&result.after.getRevision()==revision,"capacity refusal cannot settle");
        ContextGroundClauseFamily.Evaluation e=result.trial.qualification;require(e!=null&&e.status==ContextGroundClauseFamily.Status.INCOMPLETE&&e.certificate==null&&e.collisions.isEmpty()&&e.reason.contains("Residual clause limit: clauses="+cap+", proofs="+cap),"exact capacity refusal exports neither certificate nor collision prefix: "+result.trial.reason);
    }
    static void closed(DB.GroundProductionWriteResult result,LedgerLimits limits,boolean negative,boolean nullary){
        ContextGroundClauseFamily.Evaluation e=result.trial.qualification;ContextProductionCandidate.Staged artifact=result.trial.artifact;
        require(e.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&e.rounds==2&&e.certificate.frontier.ledgerLimits==limits&&e.certificate.frontier.products.size()==127&&e.certificate.models.size()==3,"complete signed/arity family retains all recipes and models");
        Entry owner=artifact.staged.primary.stream().filter(t->t.source.equals(artifact.candidate)).findFirst().get();Clause original=owner.clauses.get(0);require(original.literals.size()==8,"whole eight-literal owner");
        List<Entry> donors=new ArrayList<>(artifact.staged.primary);donors.remove(owner);for(Snapshot s:artifact.foreign)if(hasUnit(s,"q"))donors.addAll(s.primary);
        for(Product p:e.certificate.frontier.products){
            require(p.clause.literals.stream().allMatch(l->l.arguments.size()==(nullary?0:1)&&original.literals.stream().anyMatch(o->o.fact().equivalent(l.fact()))),"exact original signed literals and native arity retained");
            require(p.clause.literals.stream().anyMatch(l->l.positive!=negative&&l.predicate.equals("out")),"every proper residual retains the requested conclusion sign");
            Set<Source> expected=new LinkedHashSet<>();expected.add(owner.source);for(Entry donor:donors){Fact removed=donor.clauses.get(0).literals.get(0).fact().opposite();if(p.clause.literals.stream().noneMatch(l->l.fact().equivalent(removed)))expected.add(donor.source);}
            require(p.proof.depth==1&&p.proof.sources.equals(expected)&&p.proof.sources.size()==9-p.clause.literals.size(),"exact producer/removed-donor recipe and shortest depth retained for every subset");
        }
        require(e.certificate.frontier.products.stream().filter(p->p.clause.literals.size()==1).count()==1,"only requested out literal is a shared unit, no private fact import");
        for(ContextGroundClauseFamily.Model model:e.certificate.models){
            boolean local=model.target==artifact.staged;require(model.positiveAtoms.size()==6+(negative?0:1)+(local?0:1),"separate selected model has expected positive atom count");
            require(model.positiveAtoms.stream().anyMatch(f->f.predicate.equals("out"))!=negative,"selected witness satisfies requested out sign; absence is not negative knowledge");
            require(model.positiveAtoms.stream().anyMatch(f->f.predicate.equals("q"))==hasUnit(model.target,"q")&&model.positiveAtoms.stream().anyMatch(f->f.predicate.equals("safe"))==hasUnit(model.target,"safe"),"private q/safe occur only in their own selected model");
        }
    }
    static void collision(ContextGroundClauseFamily.Evaluation e){
        require(e!=null&&e.status==ContextGroundClauseFamily.Status.COLLISION&&e.certificate==null&&e.rounds==2&&!e.collisions.isEmpty(),"complete round2 signed/arity collision: "+(e==null?"null":e.reason));
        require(e.collisions.stream().anyMatch(c->{Set<Source> all=new HashSet<>(c.leftProof.sources);all.addAll(c.rightProof.sources);return all.size()==9&&all.stream().filter(s->s.localX).count()==7&&all.stream().filter(s->!s.localX).map(s->s.context).distinct().count()==2&&Math.max(c.leftProof.depth,c.rightProof.depth)>=1;}),"opposition retains all seven X sources and both private cuts at nonzero depth");
    }
    static void accept(Path dir,boolean negative,boolean nullary)throws Exception{
        String text=rule(negative,nullary),a=arg(nullary);
        for(boolean reverse:new boolean[]{false,true}){
            String name="Accept"+reverse;Set<String> committed;List<RevisionRef> pinned;long after;
            try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){
                prepare(f,reverse,"Safe",nullary);long before=f.db.getRevision();ContextProductionInventory base=ContextSevenPremiseNativeWriteRunner.inventory(f);Set<String> initial=ContextSevenPremiseNativeWriteRunner.origins(base);pinned=ContextSevenPremiseNativeWriteRunner.pins(f);
                bounded(write(f,text,LedgerLimits.DEFAULT),64,before);bounded(write(f,text,new LedgerLimits(128,1024)),128,before);
                require(ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(base.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"both conservative refusals preserve complete parent and pins");
                LedgerLimits limits=new LedgerLimits(256,1024);DB.GroundProductionWriteResult result=write(f,text,limits);require(result.status==DB.ProductionWriteStatus.PUBLISHED,"actual signed/arity native publication: "+result.status+" / "+result.trial.reason);closed(result,limits,negative,nullary);
                after=f.db.getRevision();require(result.before.getRevision()==before&&result.after.getRevision()==after&&after>before&&result.before.getContextId().equals(result.after.getContextId()),"physical publication references and revision");
                ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);committed=ContextSevenPremiseNativeWriteRunner.origins(inv);require(inv.primary.size()==7&&committed.size()==7&&committed.containsAll(initial)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"only whole candidate source added with pins preserved");
                require(inv.primary.stream().noneMatch(r->r.origin.equals("!q"+a+";")||r.origin.equals("!safe"+a+";")||r.origin.equals("!"+(negative?"~":"")+"out"+a+";")),"no private or generated out primary fact");
            }
            try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(f.db.getRevision()>=after&&inv.primary.size()==7&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(committed)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"reopen preserves exact source theory and saved pins/order");}
            System.out.println("SEVEN_SHAPE_WRITE negative="+negative+" nullary="+nullary+" reverse="+reverse+" published=true");
        }
    }
    static void refuse(Path dir,boolean negative,boolean nullary)throws Exception{
        String text=rule(negative,nullary);
        for(boolean reverse:new boolean[]{false,true}){
            String name="Refuse"+reverse;Set<String> initial;List<RevisionRef> pinned;
            try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){
                prepare(f,reverse,"Opposite",nullary);long revision=f.db.getRevision();ContextProductionInventory baseline=ContextSevenPremiseNativeWriteRunner.inventory(f);initial=ContextSevenPremiseNativeWriteRunner.origins(baseline);pinned=ContextSevenPremiseNativeWriteRunner.pins(f);int rows=f.root.getRules().size();
                bounded(write(f,text,LedgerLimits.DEFAULT),64,revision);LedgerLimits limits=new LedgerLimits(256,1024);DB.GroundProductionWriteResult trial=write(f,text,limits);require(trial.status==DB.ProductionWriteStatus.COLLISION&&trial.before.getRevision()==revision&&trial.after.getRevision()==revision,"public signed/arity candidate refuses without settlement");collision(trial.trial.qualification);
                ContextPendingNativeWrite attempt=f.db.beginGroundWrite(f.root,1000,16,16,16,WORK,500000,100000,1000000,limits);
                try{ContextGroundProductionCommitException failure=null;try{f.root.query(text,null,false);}catch(ContextGroundProductionCommitException expected){failure=expected;}require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"actual fresh pending child refuses typed conflict under its own explicit policy");collision(failure.getFamilyEvaluation());}finally{f.db.endNativeWrite(attempt);}
                require(f.db.getRevision()==revision&&f.root.getRules().size()==rows&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(baseline.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"all refusals preserve physical revision, full parent inventory and pins");
            }
            try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(inv.primary.size()==6&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(initial)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"reopen has only original six premises and saved pins");}
            System.out.println("SEVEN_SHAPE_REFUSAL negative="+negative+" nullary="+nullary+" reverse="+reverse+" conflict=true");
        }
    }
    static void run(boolean negative,boolean nullary,boolean refusal,String marker){try{
        Path dir=Files.createTempDirectory("seven-signed-native-");System.setProperty("user.home",dir.toString());String a=arg(nullary);create(dir,"A","!q"+a+";");
        if(refusal){create(dir,"Opposite","!"+(negative?"":"~")+"out"+a+";");refuse(dir,negative,nullary);}else{create(dir,"Safe","!safe"+a+";");accept(dir,negative,nullary);}
        System.out.println(marker+" checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
