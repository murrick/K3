/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;

/** Three independent q authorities: exact recipe multiplicity and actual native proof-slot boundary. */
final class ContextAuthorityProofBoundary {
    static final int WORK=64000000;static int checks;
    static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static void create(Path dir,String name,String text)throws Exception{try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){require(f.root.compile(text,null,false),"private authority "+name);}}
    static DB.GroundProductionWriteResult write(ContextSevenPremiseNativeWriteRunner.Fixture f,LedgerLimits limits)throws Exception{return f.db.writeGroundProductionCandidate(f.root,ContextSevenPremiseNativeWriteRunner.rule(),1000,16,16,16,WORK,500000,100000,1000000,false,limits);}
    static void incomplete(ContextGroundClauseFamily.Evaluation e,String leaf){require(e!=null&&e.status==ContextGroundClauseFamily.Status.INCOMPLETE&&e.certificate==null&&e.collisions.isEmpty()&&e.reason.endsWith(leaf),"exact refusal without certificate or collision prefix: "+(e==null?"null":e.reason));}
    static void refusal(DB.GroundProductionWriteResult r,long revision,String leaf){require(r.status==DB.ProductionWriteStatus.INCOMPLETE&&r.before.getRevision()==revision&&r.after.getRevision()==revision,"bounded public trial cannot settle");incomplete(r.trial.qualification,leaf);}
    static boolean hasUnit(Snapshot s,String name){return s.primary.stream().anyMatch(e->e.clauses.stream().anyMatch(c->c.literals.size()==1&&c.literals.get(0).positive&&c.literals.get(0).predicate.equals(name)));}
    static void recipes(DB.GroundProductionWriteResult result,LedgerLimits limits){
        ContextGroundClauseFamily.Evaluation e=result.trial.qualification;ContextProductionCandidate.Staged artifact=result.trial.artifact;
        require(e.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&e.rounds==2&&e.certificate.frontier.ledgerLimits==limits&&e.certificate.frontier.products.size()==255&&e.certificate.models.size()==5,"complete255-recipe family with five separate models at exact263-proof limit");
        Entry owner=artifact.staged.primary.stream().filter(t->t.source.equals(artifact.candidate)).findFirst().get();Clause original=owner.clauses.get(0);List<Entry> donors=new ArrayList<>(artifact.staged.primary);donors.remove(owner);
        List<Source> authorities=new ArrayList<>();for(Snapshot s:artifact.foreign)if(hasUnit(s,"q"))for(Entry entry:s.primary)authorities.add(entry.source);
        require(original.literals.size()==8&&donors.size()==6&&authorities.size()==3&&authorities.stream().map(s->s.context).distinct().count()==3,"six local donors and three genuinely independent authorities for the same q");
        Map<Set<String>,Set<Set<Source>>> actual=new HashMap<>(),expected=new HashMap<>();
        for(Product p:e.certificate.frontier.products){
            require(p.clause.literals.stream().allMatch(l->l.arguments.size()==1&&original.literals.stream().anyMatch(o->o.fact().equivalent(l.fact())))&&p.clause.literals.stream().anyMatch(l->l.positive&&l.predicate.equals("out")),"each residual is a true original-literal subset retaining out");
            Set<String> key=new TreeSet<>();for(Atom atom:p.clause.literals)key.add((atom.positive?"+":"-")+atom.predicate);
            Set<Source> base=new LinkedHashSet<>();base.add(owner.source);for(Entry donor:donors){Fact removed=donor.clauses.get(0).literals.get(0).fact().opposite();if(p.clause.literals.stream().noneMatch(l->l.fact().equivalent(removed)))base.add(donor.source);}
            Set<Set<Source>> alternatives=new HashSet<>();if(key.contains("-q"))alternatives.add(base);else for(Source authority:authorities){Set<Source> branch=new LinkedHashSet<>(base);branch.add(authority);alternatives.add(branch);}
            require(p.proof.depth==1&&p.proof.sources.size()==9-p.clause.literals.size()&&alternatives.contains(p.proof.sources),"whole producer/removed-donor support retains exactly one applicable authority and shortest depth");
            require(actual.computeIfAbsent(key,k->new HashSet<>()).add(p.proof.sources),"no duplicate recipe may stand in for a missing authority alternative");expected.put(key,alternatives);
        }
        require(actual.size()==127&&actual.keySet().stream().filter(k->k.contains("-q")).count()==63,"127 distinct clauses:63 still require q,64 have discharged q");
        for(Set<String> key:actual.keySet())require(actual.get(key).equals(expected.get(key))&&actual.get(key).size()==(key.contains("-q")?1:3),"every clause retains its complete exact alternative Source sets, never a flattened union");
        require(e.certificate.frontier.products.stream().filter(p->p.clause.literals.size()==1).count()==3,"out has three independent proofs of eight Sources each");
        for(ContextGroundClauseFamily.Model model:e.certificate.models){require(model.positiveAtoms.size()==(model.target==artifact.staged?7:8),"separate selected model cardinality");require(model.positiveAtoms.stream().anyMatch(f->f.predicate.equals("q"))==hasUnit(model.target,"q")&&model.positiveAtoms.stream().anyMatch(f->f.predicate.equals("safe"))==hasUnit(model.target,"safe"),"q remains private to each owning authority's selected model, never communal knowledge");}
    }
    static void run(boolean reverse,String marker){try{
        Path dir=Files.createTempDirectory("authority-proof-boundary-");System.setProperty("user.home",dir.toString());for(String name:new String[]{"A1","A2","A3"})create(dir,name,"!q(1);");create(dir,"Safe","!safe(1);");
        Set<String> committed;List<RevisionRef> pinned;long after;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile(ContextNativeClauseWidthRunner.base(7),null,false),"six local X facts");List<String> names=new ArrayList<>(Arrays.asList("A1","A2","A3","Safe"));if(reverse)Collections.reverse(names);for(String name:names)f.db.connectContext(name);f.db.saveConnections(f.root);
            long revision=f.db.getRevision();ContextProductionInventory baseline=ContextSevenPremiseNativeWriteRunner.inventory(f);Set<String> initial=ContextSevenPremiseNativeWriteRunner.origins(baseline);int rows=f.root.getRules().size();pinned=ContextSevenPremiseNativeWriteRunner.pins(f);require(initial.size()==6&&pinned.size()==4,"durable six-source/four-pin baseline");
            refusal(write(f,LedgerLimits.DEFAULT),revision,"Residual clause limit: clauses=64, proofs=64");
            refusal(write(f,new LedgerLimits(256,256)),revision,"Residual proof limit: proofs=256, clauses=128");
            refusal(write(f,new LedgerLimits(256,262)),revision,"Residual proof limit: proofs=262, clauses=134");
            ContextPendingNativeWrite attempt=f.db.beginGroundWrite(f.root,1000,16,16,16,WORK,500000,100000,1000000,new LedgerLimits(256,262));
            try{ContextGroundProductionCommitException failure=null;try{f.root.query(ContextSevenPremiseNativeWriteRunner.rule(),null,false);}catch(ContextGroundProductionCommitException expected){failure=expected;}require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"actual native262-slot request refuses INCOMPLETE, never conflict or truncated acceptance");incomplete(failure.getFamilyEvaluation(),"Residual proof limit: proofs=262, clauses=134");}finally{f.db.endNativeWrite(attempt);}
            require(f.db.getRevision()==revision&&f.root.getRules().size()==rows&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(baseline.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"all capacity refusals preserve exact parent inventory, revision and pins");
            LedgerLimits exact=new LedgerLimits(256,263);DB.GroundProductionWriteResult result=write(f,exact);require(result.status==DB.ProductionWriteStatus.PUBLISHED,"fresh263-slot request publishes independently of prior refused scopes: "+result.status+" / "+result.trial.reason);recipes(result,exact);
            after=f.db.getRevision();require(after>revision&&result.before.getRevision()==revision&&result.after.getRevision()==after&&result.before.getContextId().equals(result.after.getContextId()),"physical publication references reflect actual settlement");
            ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);committed=ContextSevenPremiseNativeWriteRunner.origins(inv);require(inv.primary.size()==7&&committed.size()==7&&committed.containsAll(initial)&&committed.contains(result.trial.artifact.statement)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"only exact whole candidate source added; four saved pins unchanged");
            require(inv.primary.stream().noneMatch(r->r.origin.equals("!q(1);")||r.origin.equals("!safe(1);")||r.origin.equals("!out(1);")),"no private or derived fact becomes a primary X row");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(f.db.getRevision()>=after&&inv.primary.size()==7&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(committed)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pinned),"reopen preserves exact seven source rules and ordered saved pins");}
        System.out.println("AUTHORITY_PROOF_BOUNDARY reverse="+reverse+" clauses=127 products=255 capacity=263 published=true");System.out.println(marker+" checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
