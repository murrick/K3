/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextOrdinaryResidualRounds.LedgerLimits;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundProductionRounds.Collision;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.ContextGroundProductionCommitException;

/** Independent authority refutations, complete barriers, and actual native rollback. */
final class ContextAuthorityRefutation {
    static int checks;static final int WORK=256000000;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static void create(Path dir,String name,String text)throws Exception{try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){require(f.root.compile(text,null,false),"private source "+name);}}
    static DB.GroundProductionWriteResult write(ContextSevenPremiseNativeWriteRunner.Fixture f,String text,int work,int proofs)throws Exception{
        return f.db.writeGroundProductionCandidate(f.root,text,1000,16,16,16,work,500000,100000,1000000,false,new LedgerLimits(256,proofs));
    }
    static void incomplete(ContextGroundClauseFamily.Evaluation e,String reason){
        require(e!=null&&e.status==ContextGroundClauseFamily.Status.INCOMPLETE&&e.rounds==2&&e.certificate==null&&e.collisions.isEmpty()&&e.reason.endsWith(reason),"complete resource refusal, no collision prefix: "+(e==null?"null":e.reason));
    }
    static void refutations(ContextGroundClauseFamily.Evaluation e,ContextProductionCandidate.Staged artifact,boolean exactLocal,boolean negative,boolean nullary){
        require(e!=null&&e.status==ContextGroundClauseFamily.Status.COLLISION&&e.rounds==2&&e.certificate==null&&e.collisions.size()==6,"two-round complete six-witness refutation: "+(e==null?"null":e.reason));
        Set<Source> locals=new HashSet<>();for(Entry entry:artifact.staged.primary)locals.add(entry.source);
        Set<Source> authorities=new HashSet<>();Source deny=null;
        for(Snapshot cut:artifact.foreign)for(Entry entry:cut.primary){Atom atom=entry.clauses.get(0).literals.get(0);if(atom.predicate.equals("q")&&atom.positive)authorities.add(entry.source);else if(atom.predicate.equals("out")&&atom.positive==negative)deny=entry.source;}
        require(locals.size()==7&&authorities.size()==3&&deny!=null,"whole candidate, six local donors, three private authorities and separate deny source");
        Map<Source,Set<String>> routes=new HashMap<>();Set<Set<Source>> unions=new HashSet<>();
        for(Collision c:e.collisions){
            require(c.left.opposite().equivalent(c.right)&&c.left.arguments.size()==(nullary?0:1)&&c.right.arguments.size()==(nullary?0:1),"actual opposite ground unit facts with requested native arity");
            require(c.leftProof.depth==1&&c.rightProof.depth==0&&c.leftProof.sources.size()==8&&c.rightProof.sources.size()==1,"one transported eight-source proof versus one original private source");
            Set<Source> union=new HashSet<>(c.leftProof.sources);union.addAll(c.rightProof.sources);
            Set<Source> local=new HashSet<>(),foreign=new HashSet<>();for(Source s:union)(s.localX?local:foreign).add(s);
            require(union.size()==9&&local.size()==7&&foreign.size()==2&&foreign.contains(deny),"each refutation uses seven whole X sources and exactly two private sources");
            if(exactLocal)require(local.equals(locals),"all and only precise candidate/local primary Sources retained");
            Set<Source> q=new HashSet<>(foreign);q.remove(deny);Source authority=q.iterator().next();require(authorities.contains(authority),"exact one independent q authority, never flattened private union");
            String route=c.left.predicate;
            require((route.equals("q")&&!c.left.positive&&c.right.positive&&c.rightProof.sources.contains(authority))||(route.equals("out")&&c.left.positive!=negative&&c.right.positive==negative&&c.rightProof.sources.contains(deny)),"refutation reaches q's owning cut or deny's owning cut with requested sign and provenance");
            require(routes.computeIfAbsent(authority,k->new HashSet<>()).add(route),"no duplicate route replacing an independent authority");unions.add(union);
        }
        require(routes.keySet().equals(authorities)&&routes.values().stream().allMatch(r->r.equals(new HashSet<>(Arrays.asList("q","out")) ))&&unions.size()==3,"all three independent nine-Source refutations, each witnessed at both private endpoints");
    }
    // mode0 collision; mode1 work refusal; mode2 proof refusal. Each process has its own bounded native fixture.
    static void run(boolean reverse,int mode,String marker){run(reverse,mode,false,false,marker);}
    static void run(boolean reverse,int mode,boolean negative,boolean nullary,String marker){try{
        Path dir=Files.createTempDirectory("authority-refutation-");System.setProperty("user.home",dir.toString());
        String arg=ContextSevenSignedNativeMatrix.arg(nullary),text=ContextSevenSignedNativeMatrix.rule(negative,nullary);
        for(String name:new String[]{"A1","A2","A3"})create(dir,name,"!q"+arg+";");create(dir,"Deny","!"+(negative?"":"~")+"out"+arg+";");
        Set<String> initial;List<RevisionRef> pins;long revision;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile(ContextWideGroundFamilyRunner.base(7,nullary),null,false),"six local facts");List<String> names=new ArrayList<>(Arrays.asList("A1","A2","A3","Deny"));if(reverse)Collections.reverse(names);for(String name:names)f.db.connectContext(name);f.db.saveConnections(f.root);
            ContextProductionInventory baseline=ContextSevenPremiseNativeWriteRunner.inventory(f);initial=ContextSevenPremiseNativeWriteRunner.origins(baseline);pins=ContextSevenPremiseNativeWriteRunner.pins(f);revision=f.db.getRevision();int rows=f.root.getRules().size();require(initial.size()==6&&pins.size()==4,"saved six-source/four-pin baseline");
            int work=mode==1?64000000:WORK,proofs=mode==2?326:327;
            String reason=mode==1?"Ground transport work limit":"Residual proof limit: proofs=326, clauses=198";
            DB.GroundProductionWriteResult trial=write(f,text,work,proofs);
            require(trial.before.getRevision()==revision&&trial.after.getRevision()==revision,"public qualification does not settle refused candidate");
            if(mode==0){require(trial.status==DB.ProductionWriteStatus.COLLISION,"public writer rejects sourced conflict");refutations(trial.trial.qualification,trial.trial.artifact,true,negative,nullary);}else{require(trial.status==DB.ProductionWriteStatus.INCOMPLETE,"resource stop remains unknown");incomplete(trial.trial.qualification,reason);}
            ContextPendingNativeWrite attempt=f.db.beginGroundWrite(f.root,1000,16,16,16,work,500000,100000,1000000,new LedgerLimits(256,proofs));
            try{ContextGroundProductionCommitException failure=null;try{f.root.query(text,null,false);}catch(ContextGroundProductionCommitException expected){failure=expected;}
                require(failure!=null&&failure.getErrorCode()==(mode==0?StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT:StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE),"actual owning native attempt rejects with precise typed status");
                if(mode==0)refutations(failure.getFamilyEvaluation(),trial.trial.artifact,false,negative,nullary);else incomplete(failure.getFamilyEvaluation(),reason);
            }finally{f.db.endNativeWrite(attempt);}
            require(f.db.getRevision()==revision&&f.root.getRules().size()==rows&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(baseline.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"public and actual native refusals preserve full parent fingerprint, rules, revision and ordered pins");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            ContextProductionInventory reopened=ContextSevenPremiseNativeWriteRunner.inventory(f);require(reopened.primary.size()==6&&ContextSevenPremiseNativeWriteRunner.origins(reopened).equals(initial)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"reopen preserves only six original sources and four saved pins");
        }
        System.out.println("AUTHORITY_REFUTATION reverse="+reverse+" mode="+mode+" negative="+negative+" nullary="+nullary+" settled=false");System.out.println(marker+" checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
