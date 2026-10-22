/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFamilyInspection.*;
import org.kanger.ContextUniversalUnitProof.Proof;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.ContextProductionCommitException;

/** Universal private Bridge instantiation with independent whole-Source alternatives. */
final class ContextUniversalAuthorityChain {
    static int checks;static final int WORK=64000000;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static DB.UniversalProductionWriteResult write(ContextSevenPremiseNativeWriteRunner.Fixture f,String text,int rounds,int depth)throws Exception{
        return f.db.writeUniversalProductionCandidate(f.root,text,1000,rounds,16,depth,20000,1000000,WORK,500000,WORK,WORK,false);
    }
    static void incomplete(Result r,String cause){require(r!=null&&r.status==Status.INCOMPLETE&&r.certificate==null&&r.collision==null&&r.reason.toLowerCase().contains(cause),"bounded universal refusal has no certificate/opposition prefix: "+(r==null?"null":r.reason));}
    static void collision(Result r,ContextAuthorityChain.Expected e,boolean exactLocal){
        require(r!=null&&r.status==Status.COLLISION&&r.completedBarriers==3&&r.certificate==null&&r.collision.oppositions.size()==12,"complete universal third-barrier conflict");
        Set<Set<Source>> expected=e.pairs();Map<Set<Source>,Set<String>> endpoints=new HashMap<>();
        for(Proof p:r.collision.oppositions){
            require(p.contradiction&&p.dependsOnX&&p.conclusion==null&&p.support.depth==5&&p.left.literals.size()==1&&p.right.literals.size()==1,"actual unit opposition has cumulative resolution depth5");
            Atom l=p.left.literals.get(0),rr=p.right.literals.get(0);
            require(l.arguments.size()==1&&rr.arguments.size()==1&&l.arguments.get(0).variable<0&&rr.arguments.get(0).variable<0&&l.fact().opposite().equivalent(rr.fact()),"ground complementary endpoints retain one exact argument");
            require(p.leftSupport.depth==0&&p.leftSupport.sources.size()==1&&p.rightSupport.depth==4&&p.rightSupport.sources.size()==5,"original private fact depth0 versus complete transported depth4 support");
            Set<Source> local=new HashSet<>(),foreign=new HashSet<>();for(Source x:p.support.sources)(x.localX?local:foreign).add(x);
            Set<Source> pair=new HashSet<>(foreign);pair.remove(e.deny);
            require(p.support.sources.size()==6&&local.size()==3&&foreign.size()==3&&foreign.contains(e.deny)&&expected.contains(pair),"exact whole X plus one independently enumerated donor/Bridge pair and deny");if(exactLocal)require(local.equals(e.locals),"exact staged local Source identities");
            require((l.predicate.equals("q")&&l.positive!=e.negativeQ&&p.leftSupport.sources.stream().anyMatch(e.authorities::contains))||(l.predicate.equals("mid")&&l.positive==e.negative&&p.leftSupport.sources.contains(e.deny)),"requested donor or deny endpoint polarity and original Source");
            require(endpoints.computeIfAbsent(pair,k->new HashSet<>()).add(l.predicate),"unique endpoint per exact donor/Bridge pair");
        }
        require(endpoints.keySet().equals(expected)&&endpoints.values().stream().allMatch(v->v.equals(new HashSet<>(Arrays.asList("q","mid")))),"every exact pair occurs at both endpoints, never duplicate substitutes");
    }
    static List<ContextUniversalUnitRounds.Receipt> pass(ContextUniversalUnitRounds.Frontier f,int depth){List<ContextUniversalUnitRounds.Receipt> rs=new ArrayList<>();for(Snapshot cut:f.cuts)rs.add(ContextUniversalUnitRounds.inspectFactored(f,cut,16,depth,WORK));return rs;}
    static void units(ContextUniversalUnitRounds.Frontier f,String predicate,boolean sign,int depth,List<Set<Source>> expected){Set<Set<Source>> actual=new HashSet<>();int count=0;for(ContextUniversalUnitLedger.Row row:f.products)if(row.clause.literals.size()==1){Atom a=row.clause.literals.get(0);if(a.predicate.equals(predicate)&&a.positive==sign){++count;require(row.clause.variables.isEmpty()&&a.arguments.size()==1&&row.support.depth==depth&&actual.add(row.support.sources),"exact unique ground carrier and cumulative resolution depth");}}require(count==expected.size()&&actual.equals(new HashSet<>(expected)),"all and only exact Source alternatives for "+predicate);}
    static void barriers(ContextProductionCandidate.Staged a,ContextAuthorityChain.Expected e){
        ContextUniversalUnitRounds.Frontier initial=ContextUniversalUnitRounds.start(a.staged,a.foreign,WORK);
        ContextUniversalUnitRounds.Barrier first=ContextUniversalUnitRounds.merge(initial,pass(initial,8),8,WORK);
        require(first.status==ContextUniversalUnitRounds.Status.MERGED&&first.changed&&initial.products.isEmpty()&&first.frontier.previous.equals(initial.identity),"first immutable universal common barrier");
        int saved=first.frontier.products.size();List<Set<Source>> out=new ArrayList<>(),mid=new ArrayList<>(),q=new ArrayList<>();for(Source donor:e.authorities){out.add(e.set(donor));for(Source b:e.bridges)mid.add(e.set(donor,b));}for(Source b:e.bridges)q.add(e.set(b,e.deny));
        units(first.frontier,"out",!e.negativeOut,3,out);require(first.frontier.products.stream().filter(row->row.clause.literals.size()==1).count()==3,"first round has only three out units, no private import");
        ContextUniversalUnitRounds.Barrier second=ContextUniversalUnitRounds.merge(first.frontier,pass(first.frontier,8),8,WORK);
        require(second.status==ContextUniversalUnitRounds.Status.MERGED&&second.changed&&first.frontier.products.size()==saved&&second.frontier.previous.equals(first.frontier.identity),"second immutable barrier preserves prior common input");
        units(second.frontier,"out",!e.negativeOut,3,out);units(second.frontier,"mid",!e.negative,4,mid);units(second.frontier,"q",e.negativeQ,4,q);
        require(second.frontier.products.stream().filter(row->row.clause.literals.size()==1).count()==11,"exact eleven derived units, never raw private rows");
        List<ContextUniversalUnitRounds.Receipt> low=pass(second.frontier,0);for(ContextUniversalUnitRounds.Receipt receipt:low)require(!receipt.verified&&receipt.products.isEmpty()&&receipt.oppositions.isEmpty(),"carried depth cannot reset at another cut");
        ContextUniversalUnitRounds.Barrier refused=ContextUniversalUnitRounds.merge(second.frontier,low,0,WORK);require(refused.status==ContextUniversalUnitRounds.Status.INCOMPLETE&&refused.frontier==null&&refused.oppositions.isEmpty(),"whole barrier rejects depth cutoff with no prefix");
    }
    static void nativeRefusal(ContextSevenPremiseNativeWriteRunner.Fixture f,String text,int rounds,int depth,ContextAuthorityChain.Expected e,String cause)throws Exception{
        ContextPendingNativeWrite attempt=f.db.beginUniversalWrite(f.root,1000,rounds,16,depth,20000,1000000,WORK,500000,WORK,WORK);
        try{ContextProductionCommitException failure=null;try{f.root.query(text,null,false);}catch(ContextProductionCommitException caught){failure=caught;}
            require(failure!=null&&failure.getErrorCode()==(cause==null?StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT:StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE),"actual universal native scope refuses precise typed status");
            ContextUniversalFamilyAudit.Audit audit=failure.getFamilyAudit();require(audit!=null,"actual pending-child family audit retained");
            if(cause==null){require(audit.verified,"actual native collision independently audited");collision(audit.verdict,e,false);}else{require(!audit.verified,"incomplete cannot have verified audit");incomplete(audit.verdict,cause);}
        }finally{f.db.endNativeWrite(attempt);}
    }
    static void run(boolean reverse,boolean negative,String marker){try{
        Path dir=Files.createTempDirectory("universal-authority-chain-");System.setProperty("user.home",dir.toString());String sign=negative?"~":"";
        for(String name:new String[]{"A1","A2","A3"})ContextAuthorityChain.create(dir,name,"!"+sign+"q(1);");
        for(String name:new String[]{"Bridge1","Bridge2"})ContextAuthorityChain.create(dir,name,"!@x "+sign+"out(x)->"+sign+"mid(x);");
        ContextAuthorityChain.create(dir,"Deny","!"+(negative?"":"~")+"mid(1);");
        String text="!"+sign+"q(1)&&~p1(1)&&~p2(1)->"+sign+"out(1);";
        Set<String> original;List<RevisionRef> pins;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile("!~p1(1); !~p2(1);",null,false),"two original signed local facts");
            List<String> names=new ArrayList<>(Arrays.asList("A1","A2","A3","Bridge1","Bridge2","Deny"));if(reverse)Collections.reverse(names);for(String name:names)f.db.connectContext(name);f.db.saveConnections(f.root);
            ContextProductionInventory before=ContextSevenPremiseNativeWriteRunner.inventory(f);original=ContextSevenPremiseNativeWriteRunner.origins(before);pins=ContextSevenPremiseNativeWriteRunner.pins(f);long revision=f.db.getRevision();int rows=f.root.getRules().size();
            require(original.size()==2&&pins.size()==6,"two physical facts and six durable private pins");
            DB.GroundProductionWriteResult ground=ContextAuthorityChain.write(f,text,3,8);
            require(ground.status==DB.ProductionWriteStatus.INCOMPLETE&&ground.trial.qualification.reason.equals("Ground family excludes universal clause slots")&&ground.trial.qualification.certificate==null&&ground.trial.qualification.collisions.isEmpty()&&ground.before.getRevision()==revision&&ground.after.getRevision()==revision,"older ground writer conservatively refuses universal Bridge without prefix");
            DB.UniversalProductionWriteResult full=write(f,text,3,8);
            require(full.status==DB.ProductionWriteStatus.COLLISION&&full.audit!=null&&full.audit.verified&&full.audit.evaluation==full.trial,"universal writer returns independently audited conflict");
            ContextAuthorityChain.Expected expected=new ContextAuthorityChain.Expected(full.trial.artifact,2,negative,negative,negative,false);
            for(String predicate:new String[]{"p1","p2"})require(full.trial.artifact.staged.primary.stream().anyMatch(e->e.clauses.size()==1&&e.clauses.get(0).literals.size()==1&&e.clauses.get(0).literals.get(0).predicate.equals(predicate)&&!e.clauses.get(0).literals.get(0).positive&&e.clauses.get(0).variables.isEmpty()),"exact original negative ground premise "+predicate);
            int bridgeCount=0;for(Snapshot cut:full.trial.artifact.foreign)for(Entry e:cut.primary)if(expected.bridges.contains(e.source)){++bridgeCount;Clause c=e.clauses.get(0);require(c.variables.size()==1&&c.literals.stream().allMatch(l->l.arguments.size()==1&&l.arguments.get(0).variable>=0)&&c.literals.get(0).arguments.get(0).variable==c.literals.get(1).arguments.get(0).variable,"whole private Bridge remains universal with one shared variable");}require(bridgeCount==2,"both exact universal Bridge declarations checked");
            collision(full.trial.qualification,expected,true);require(full.audit.oppositions.size()==12&&full.audit.oppositions.stream().allMatch(x->x.usesCandidate),"all twelve audited oppositions retain candidate attribution");barriers(full.trial.artifact,expected);
            DB.UniversalProductionWriteResult early=write(f,text,2,8);require(early.status==DB.ProductionWriteStatus.INCOMPLETE&&early.before.getRevision()==revision&&early.after.getRevision()==revision,"round cutoff cannot write");incomplete(early.trial.qualification,"common round limit");
            DB.UniversalProductionWriteResult shallow=write(f,text,3,0);require(shallow.status==DB.ProductionWriteStatus.INCOMPLETE&&shallow.before.getRevision()==revision&&shallow.after.getRevision()==revision,"depth cutoff cannot write");incomplete(shallow.trial.qualification,"depth");
            nativeRefusal(f,text,3,8,expected,null);nativeRefusal(f,text,2,8,expected,"common round limit");nativeRefusal(f,text,3,0,expected,"depth");
            require(full.before.getRevision()==revision&&full.after.getRevision()==revision&&f.db.getRevision()==revision&&f.root.getRules().size()==rows&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(before.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"universal refusal preserves exact physical parent and topology");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(inv.primary.size()==2&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(original)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"durable original facts and pins only");}
        System.out.println(marker+" checks="+(checks+ContextAuthorityChain.checks));System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
