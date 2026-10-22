/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextUniversalFamilyInspection.*;
import org.kanger.ContextUniversalUnitProof.Proof;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.ContextProductionCommitException;

/** Shared candidate variables cannot join different ground premise values. */
final class ContextAuthoritySubstitution {
    static int checks;static final int WORK=64000000;
    static void require(boolean yes,String why){++checks;if(!yes)throw new AssertionError(why);}
    static ContextUniversalCandidate.Evaluation inspect(ContextSevenPremiseNativeWriteRunner.Fixture f,String text,int rounds)throws Exception{return f.db.qualifyUniversalProductionCandidate(f.root,text,1000,rounds,16,16,20000,1000000,WORK,500000,WORK);}
    static void consistent(Result r){
        require(r.status==Status.CONSISTENT&&r.collision==null&&r.certificate!=null&&r.completedBarriers>=2,"exact stable family consistency, not a first-round prefix");
        ContextUniversalFamilyModel.Certificate c=r.certificate;require(c.cuts.size()==7&&c.input.cuts.size()==7,"self and every private cut have models");
        List<ContextUniversalUnitRounds.Receipt> receipts=new ArrayList<>();List<ContextUniversalQuotientModel.Model> models=new ArrayList<>();
        for(ContextUniversalFamilyModel.Cut cut:c.cuts){receipts.add(cut.receipt);models.add(cut.model);require(cut.receipt.verified&&cut.receipt.input==c.input&&cut.model.ledger==cut.receipt.closureStep.input,"model belongs to exact closed private cut");}
        require(ContextUniversalFamilyModel.certify(c.input,receipts,models,16,20000,WORK).certified,"independently recheck all-value family certificate");
        require(c.input.products.stream().noneMatch(row->row.clause.literals.size()==1&&(row.clause.literals.get(0).predicate.equals("out")||row.clause.literals.get(0).predicate.equals("mid"))),"different ground premise values never produce out/mid units");
    }
    static SemanticTermSnapshot deny(ContextProductionCandidate.Staged a,ContextAuthorityChain.Expected e){return a.foreign.stream().flatMap(c->c.primary.stream()).filter(row->row.source.equals(e.deny)).findFirst().get().clauses.get(0).literals.get(0).arguments.get(0).value;}
    static void collision(Result r,ContextAuthorityChain.Expected e,SemanticTermSnapshot value,boolean exactLocal){
        require(r.status==Status.COLLISION&&r.certificate==null&&r.completedBarriers==3&&r.collision.oppositions.size()==12,"matched premises prove complete third-barrier collision");
        Map<Set<Source>,Set<String>> actual=new HashMap<>();Set<Set<Source>> expected=e.pairs();
        for(Proof p:r.collision.oppositions){require(p.contradiction&&p.dependsOnX&&p.conclusion==null&&p.support.depth==5&&p.left.literals.size()==1&&p.right.literals.size()==1,"whole unit contradiction and resolution depth5");Atom l=p.left.literals.get(0),rr=p.right.literals.get(0);
            require(l.predicate.equals(rr.predicate)&&l.positive!=rr.positive&&l.arguments.size()==1&&rr.arguments.size()==1&&rr.arguments.get(0).variable<0&&rr.arguments.get(0).value.semanticallyEquals(value),"derived opposition uses exact matched ground value");
            require((l.predicate.equals("q")&&l.arguments.get(0).variable>=0&&l.positive!=e.negativeQ&&p.leftSupport.sources.stream().anyMatch(e.authorities::contains))||(l.predicate.equals("mid")&&l.arguments.get(0).variable<0&&l.arguments.get(0).value.semanticallyEquals(value)&&l.positive==e.negative&&p.leftSupport.sources.contains(e.deny)),"generic donor or exact ground deny endpoint");
            require(p.leftSupport.depth==0&&p.leftSupport.sources.size()==1&&p.rightSupport.depth==4&&p.rightSupport.sources.size()==5,"original private support versus complete transported support");
            Set<Source> local=new HashSet<>(),foreign=new HashSet<>();for(Source source:p.support.sources)(source.localX?local:foreign).add(source);Set<Source> pair=new HashSet<>(foreign);pair.remove(e.deny);
            require(p.support.sources.size()==6&&local.size()==3&&foreign.size()==3&&foreign.contains(e.deny)&&expected.contains(pair),"exact whole X plus one donor/Bridge pair and deny");if(exactLocal)require(local.equals(e.locals),"exact fresh staged local Sources");
            require(actual.computeIfAbsent(pair,k->new HashSet<>()).add(l.predicate),"duplicate endpoint cannot replace another pair");}
        require(actual.keySet().equals(expected)&&actual.values().stream().allMatch(v->v.equals(new HashSet<>(Arrays.asList("q","mid")))),"all six independently enumerated pairs at both endpoints");
    }
    static void caseOf(Path dir,String name,boolean reverse,boolean negative,boolean matched,boolean nativePath,String text)throws Exception{
        Set<String> persisted;List<RevisionRef> pins;long settled;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){
            require(f.root.compile(matched?"!~p1(9); !~p2(9);":"!~p1(1); !~p2(2);",null,false),"two exact local premise assertions");List<String> names=new ArrayList<>(Arrays.asList("A1","A2","A3","Bridge1","Bridge2","Deny"));if(reverse)Collections.reverse(names);for(String target:names)f.db.connectContext(target);f.db.saveConnections(f.root);
            ContextProductionInventory baseline=ContextSevenPremiseNativeWriteRunner.inventory(f);Set<String> initial=ContextSevenPremiseNativeWriteRunner.origins(baseline);pins=ContextSevenPremiseNativeWriteRunner.pins(f);long revision=f.db.getRevision();int rows=f.root.getRules().size();require(initial.size()==2&&pins.size()==6,"only two local primary facts and six saved pins");
            ContextUniversalCandidate.Evaluation diagnostic=inspect(f,text,16);require(diagnostic.status==(matched?Status.COLLISION:Status.CONSISTENT),"exact shared-parameter verdict: "+diagnostic.reason);
            ContextAuthorityChain.Expected expected=new ContextAuthorityChain.Expected(diagnostic.artifact,2,negative,negative,negative,false);SemanticTermSnapshot value=deny(diagnostic.artifact,expected);Map<String,SemanticTermSnapshot> values=new HashMap<>();
            for(Entry entry:diagnostic.artifact.base.primary){Clause c=entry.clauses.get(0);require(c.variables.isEmpty()&&c.literals.size()==1&&!c.literals.get(0).positive&&c.literals.get(0).arguments.size()==1,"original ground negative local premise");Atom atom=c.literals.get(0);values.put(atom.predicate,atom.arguments.get(0).value);}
            require(values.size()==2&&values.containsKey("p1")&&values.containsKey("p2"),"two distinct premise predicates retained");require(values.get("p1").semanticallyEquals(values.get("p2"))==matched,"premise argument equality is semantic, not textual");require(matched?values.get("p1").semanticallyEquals(value):!values.get("p1").semanticallyEquals(value)&&!values.get("p2").semanticallyEquals(value),"matched deny value or two distinct unrelated values");
            Clause candidate=diagnostic.artifact.staged.primary.stream().filter(e->e.source.equals(diagnostic.artifact.candidate)).findFirst().get().clauses.get(0);int variable=candidate.literals.get(0).arguments.get(0).variable;require(candidate.variables.size()==1&&candidate.literals.size()==4&&variable>=0&&candidate.literals.stream().allMatch(atom->atom.arguments.size()==1&&atom.arguments.get(0).variable==variable),"one candidate parameter across all four predicate positions");
            if(matched)collision(diagnostic.qualification,expected,value,true);else consistent(diagnostic.qualification);
            ContextUniversalCandidate.Evaluation stopped=inspect(f,text,1);require(stopped.status==Status.INCOMPLETE&&stopped.qualification.certificate==null&&stopped.qualification.collision==null&&stopped.reason.contains("common round limit"),"one common round cannot certify or expose opposition prefix");
            require(f.db.getRevision()==revision&&f.root.getRules().size()==rows&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(baseline.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"diagnostics preserve exact physical parent and topology");
            if(nativePath){ContextPendingNativeWrite attempt=f.db.beginUniversalWrite(f.root,1000,16,16,16,20000,1000000,WORK,500000,WORK,WORK);try{ContextProductionCommitException failure=null;try{f.root.query(text,null,false);}catch(ContextProductionCommitException caught){failure=caught;}if(matched){require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT&&failure.getFamilyAudit()!=null&&failure.getFamilyAudit().verified,"actual native conflict independently audited");require(failure.getFamilyAudit().verdict!=diagnostic.qualification,"native guard uses a fresh actual-child verdict");collision(failure.getFamilyAudit().verdict,expected,value,false);}else require(failure==null,"actual native consistent candidate accepted");}finally{f.db.endNativeWrite(attempt);}}
            else{DB.UniversalProductionWriteResult result=f.db.writeUniversalProductionCandidate(f.root,text,1000,16,16,16,20000,1000000,WORK,500000,WORK,WORK,false);require(result.status==(matched?DB.ProductionWriteStatus.COLLISION:DB.ProductionWriteStatus.PUBLISHED),"fresh public writer reaches expected settlement: "+result.status+" / "+result.trial.reason);require(result.trial!=diagnostic&&result.audit!=null&&result.audit.verified&&result.audit.evaluation==result.trial,"historical diagnostic is not publication authority");require(result.before.getRevision()==revision&&result.after.getRevision()==f.db.getRevision(),"exact pretrial and physical settlement references");if(matched){ContextAuthorityChain.Expected fresh=new ContextAuthorityChain.Expected(result.trial.artifact,2,negative,negative,negative,false);collision(result.trial.qualification,fresh,deny(result.trial.artifact,fresh),true);}else{require(result.audit.oppositions.isEmpty(),"consistent publication has no collision attribution");consistent(result.trial.qualification);}}
            ContextProductionInventory current=ContextSevenPremiseNativeWriteRunner.inventory(f);persisted=ContextSevenPremiseNativeWriteRunner.origins(current);settled=f.db.getRevision();require(ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"publication/refusal retains exact pinned order");
            if(matched)require(settled==revision&&f.root.getRules().size()==rows&&current.fingerprint.equals(baseline.fingerprint)&&persisted.equals(initial),"matched refusal retains exact original physical theory");
            else{require(settled>revision&&current.primary.size()==3&&persisted.size()==3&&persisted.containsAll(initial),"only one new whole rule is durably published");ContextProductionNativeBinding.Result bound=ContextProductionNativeBinding.bind(f.root,current,WORK,500000);require(bound.bound&&bound.snapshot.primary.size()==3&&bound.snapshot.primary.stream().anyMatch(e->e.clauses.size()==1&&e.clauses.get(0).equivalent(candidate)),"actual published primary equals the requested universal candidate");require(bound.snapshot.primary.stream().allMatch(e->e.clauses.size()==1&&(e.clauses.get(0).equivalent(candidate)||(e.clauses.get(0).literals.size()==1&&!e.clauses.get(0).literals.get(0).positive&&(e.clauses.get(0).literals.get(0).predicate.equals("p1")||e.clauses.get(0).literals.get(0).predicate.equals("p2"))))),"only original premises and whole candidate, no private or derived primary import");}
            System.out.println("SUBSTITUTION matched="+matched+" negative="+negative+" reverse="+reverse+" native="+nativePath+" diagnosticBarriers="+diagnostic.qualification.completedBarriers+" primary="+current.primary.size());
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(f.db.getRevision()>=settled&&inv.primary.size()==(matched?2:3)&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(persisted)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"reopen retains exactly original premises plus optional candidate and all pins");}
    }
    static void run(boolean reverse,boolean negative,boolean matched,String marker){try{
        Path dir=Files.createTempDirectory("authority-substitution-");System.setProperty("user.home",dir.toString());String sign=negative?"~":"";
        for(String name:new String[]{"A1","A2","A3"})ContextAuthorityChain.create(dir,name,"!@x "+sign+"q(x);");for(String name:new String[]{"Bridge1","Bridge2"})ContextAuthorityChain.create(dir,name,"!@x "+sign+"out(x)->"+sign+"mid(x);");ContextAuthorityChain.create(dir,"Deny","!"+(negative?"":"~")+"mid(9);");String text="!@x "+sign+"q(x)&&~p1(x)&&~p2(x)->"+sign+"out(x);";
        caseOf(dir,"Public",reverse,negative,matched,false,text);caseOf(dir,"Native",reverse,negative,matched,true,text);
        System.out.println(marker+" checks="+(checks+ContextAuthorityChain.checks));System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
