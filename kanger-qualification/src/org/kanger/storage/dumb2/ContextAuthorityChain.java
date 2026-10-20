/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundProductionRounds.Collision;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.ContextGroundProductionCommitException;

/** Three independent routes through a private bridge and three immutable family barriers. */
final class ContextAuthorityChain {
    static int checks;static final int WORK=64000000;
    static final String RULE=ContextWideGroundFamilyRunner.rule(3,false,false);
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static void create(Path dir,String name,String text)throws Exception{try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){require(f.root.compile(text,null,false),"private source "+name);}}
    static final class Expected {
        final Set<Source> locals=new HashSet<>(),authorities=new HashSet<>();Source bridge,deny;
        Expected(ContextProductionCandidate.Staged a){
            for(Entry e:a.staged.primary)locals.add(e.source);
            for(Snapshot cut:a.foreign)for(Entry e:cut.primary){Clause c=e.clauses.get(0);if(c.literals.size()==2&&c.literals.stream().anyMatch(l->l.predicate.equals("mid")&&l.positive))bridge=e.source;
                else if(c.literals.size()==1){Atom atom=c.literals.get(0);if(atom.predicate.equals("q")&&atom.positive)authorities.add(e.source);else if(atom.predicate.equals("mid")&&!atom.positive)deny=e.source;}}
            require(locals.size()==3&&locals.contains(a.candidate)&&authorities.size()==3&&authorities.stream().map(s->s.context).distinct().count()==3&&bridge!=null&&deny!=null,"whole three-source X, three independent q authorities, private bridge and deny");
        }
        Set<Source> set(Source... extra){Set<Source> s=new HashSet<>(locals);s.addAll(Arrays.asList(extra));return s;}
    }
    static List<Receipt> pass(Frontier f,int depth){List<Receipt> rs=new ArrayList<>();for(Snapshot cut:f.cuts)rs.add(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,cut,depth,WORK,500000,100000,16));return rs;}
    static void units(Frontier f,String predicate,boolean positive,int depth,List<Set<Source>> expected){
        Set<Set<Source>> actual=new HashSet<>();int count=0;
        for(Product p:f.products)if(p.clause.literals.size()==1){Atom atom=p.clause.literals.get(0);if(atom.predicate.equals(predicate)&&atom.positive==positive){++count;require(atom.arguments.size()==1&&p.proof.depth==depth&&actual.add(p.proof.sources),"exact unique unit recipe with cumulative depth for "+predicate);}}
        require(count==expected.size()&&actual.equals(new HashSet<>(expected)),"all and only independent whole-Source alternatives for "+predicate);
    }
    static void collisions(List<Collision> cs,Expected e,boolean exactLocal){
        require(cs.size()==6,"six endpoint witnesses for three independent chain refutations");Map<Source,Set<String>> routes=new HashMap<>();Set<Set<Source>> unions=new HashSet<>();
        for(Collision c:cs){
            require(c.left.opposite().equivalent(c.right)&&c.left.arguments.size()==1&&c.right.arguments.size()==1&&c.leftProof.depth==2&&c.rightProof.depth==0&&c.leftProof.sources.size()==5&&c.rightProof.sources.size()==1,"transported five-source depth2 proof versus original private depth0 fact");
            Set<Source> union=new HashSet<>(c.leftProof.sources);union.addAll(c.rightProof.sources);Set<Source> local=new HashSet<>(),foreign=new HashSet<>();for(Source s:union)(s.localX?local:foreign).add(s);
            require(union.size()==6&&local.size()==3&&foreign.size()==3&&foreign.contains(e.bridge)&&foreign.contains(e.deny),"refutation keeps whole X, private bridge, deny and exactly one authority");if(exactLocal)require(local.equals(e.locals),"exact detached whole local primary Sources");
            Set<Source> q=new HashSet<>(foreign);q.remove(e.bridge);q.remove(e.deny);Source authority=q.iterator().next();require(e.authorities.contains(authority),"exact one independent pinned q authority");
            String route=c.left.predicate;require((route.equals("q")&&!c.left.positive&&c.rightProof.sources.contains(authority))||(route.equals("mid")&&c.left.positive&&c.rightProof.sources.contains(e.deny)),"opposition reaches the authority or deny endpoint");
            require(routes.computeIfAbsent(authority,k->new HashSet<>()).add(route),"no duplicate endpoint replaces an alternative");unions.add(union);
        }
        require(unions.size()==3&&routes.keySet().equals(e.authorities)&&routes.values().stream().allMatch(r->r.equals(new HashSet<>(Arrays.asList("q","mid")))),"all three independent six-Source chain refutations occur at both endpoints");
    }
    static void collision(ContextGroundClauseFamily.Evaluation result,Expected e,boolean exactLocal){require(result!=null&&result.status==ContextGroundClauseFamily.Status.COLLISION&&result.rounds==3&&result.certificate==null,"complete third-round sourced conflict");collisions(result.collisions,e,exactLocal);}
    static void incomplete(ContextGroundClauseFamily.Evaluation result,String reason){require(result!=null&&result.status==ContextGroundClauseFamily.Status.INCOMPLETE&&result.rounds==2&&result.certificate==null&&result.collisions.isEmpty()&&result.reason.endsWith(reason),"bounded refusal has no certificate/collision prefix: "+(result==null?"null":result.reason));}
    static void barriers(ContextProductionCandidate.Staged a,Expected e){
        Frontier initial=ContextOrdinaryResidualRounds.start(a.staged,a.foreign,WORK,LedgerLimits.DEFAULT);Barrier first=ContextOrdinaryResidualRounds.merge(initial,pass(initial,3),WORK);
        require(first.status==Status.MERGED&&first.changed&&first.frontier.products.size()==19&&first.frontier.ledgerLimits==LedgerLimits.DEFAULT&&initial.products.isEmpty(),"first immutable DEFAULT barrier,19 shared recipes");
        List<Set<Source>> out=new ArrayList<>(),mid=new ArrayList<>();for(Source q:e.authorities){out.add(e.set(q));mid.add(e.set(q,e.bridge));}
        units(first.frontier,"out",true,1,out);require(first.frontier.products.stream().filter(p->p.clause.literals.size()==1).count()==3,"first round shares only the three out units; private facts are not imported");
        List<Receipt> secondPass=pass(first.frontier,3);Barrier second=ContextOrdinaryResidualRounds.merge(first.frontier,secondPass,WORK);
        require(second.status==Status.MERGED&&second.changed&&second.frontier.products.size()==35&&second.frontier.products.stream().allMatch(p->p.proof.depth<=2)&&first.frontier.products.size()==19&&second.frontier.ledgerLimits==LedgerLimits.DEFAULT,"second immutable DEFAULT barrier,35 recipes at depth<=2, previous input unchanged");
        units(second.frontier,"out",true,1,out);units(second.frontier,"mid",true,2,mid);units(second.frontier,"q",false,2,Collections.singletonList(e.set(e.bridge,e.deny)));
        require(second.frontier.products.stream().filter(p->p.clause.literals.size()==1).count()==7,"exact seven shared units, no raw q/bridge/deny import");
        ContextGroundClauseFamily.Evaluation premature=ContextGroundClauseFamily.certify(first.frontier,secondPass,3,WORK,1000000);
        require(premature.status==ContextGroundClauseFamily.Status.INCOMPLETE&&premature.certificate==null&&premature.collisions.isEmpty()&&premature.reason.equals("Ground certificate requires unchanged shared recipes"),"changed second barrier is not saturation or admission");
        List<Receipt> low=pass(second.frontier,1);for(Receipt r:low)require(!r.verified&&r.products.isEmpty()&&r.collisions.isEmpty()&&r.reason.equals("Residual cumulative depth limit"),"carried depth2 recipes refuse depth1 before exposing any collision prefix");
        Barrier stopped=ContextOrdinaryResidualRounds.merge(second.frontier,low,WORK);require(stopped.status==Status.INCOMPLETE&&stopped.frontier==null&&stopped.collisions.isEmpty(),"complete barrier refuses carried-depth cutoff");
        Barrier third=ContextOrdinaryResidualRounds.merge(second.frontier,pass(second.frontier,3),WORK);require(third.status==Status.COLLISION&&third.frontier==null&&second.frontier.products.size()==35,"third barrier rejects without mutating second input");collisions(third.collisions,e,true);
    }
    static DB.GroundProductionWriteResult write(ContextSevenPremiseNativeWriteRunner.Fixture f,int rounds,int depth)throws Exception{return f.db.writeGroundProductionCandidate(f.root,RULE,1000,rounds,16,depth,WORK,500000,100000,1000000,false,LedgerLimits.DEFAULT);}
    static void nativeRefusal(ContextSevenPremiseNativeWriteRunner.Fixture f,int rounds,int depth,Expected e,String reason)throws Exception{
        ContextPendingNativeWrite attempt=f.db.beginGroundWrite(f.root,1000,rounds,16,depth,WORK,500000,100000,1000000,LedgerLimits.DEFAULT);
        try{ContextGroundProductionCommitException failure=null;try{f.root.query(RULE,null,false);}catch(ContextGroundProductionCommitException expected){failure=expected;}
            require(failure!=null&&failure.getErrorCode()==(reason==null?StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT:StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE),"actual owning native scope refuses precise typed status");
            if(reason==null)collision(failure.getFamilyEvaluation(),e,false);else incomplete(failure.getFamilyEvaluation(),reason);
        }finally{f.db.endNativeWrite(attempt);}
    }
    static void run(boolean reverse,String marker){try{
        Path dir=Files.createTempDirectory("authority-chain-");System.setProperty("user.home",dir.toString());for(String name:new String[]{"A1","A2","A3"})create(dir,name,"!q(1);");create(dir,"Bridge","!out(1)->mid(1);");create(dir,"Deny","!~mid(1);");
        Set<String> original;List<RevisionRef> pins;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile(ContextWideGroundFamilyRunner.base(3,false),null,false),"two local p assertions");List<String> names=new ArrayList<>(Arrays.asList("A1","A2","A3","Bridge","Deny"));if(reverse)Collections.reverse(names);for(String name:names)f.db.connectContext(name);f.db.saveConnections(f.root);
            ContextProductionInventory baseline=ContextSevenPremiseNativeWriteRunner.inventory(f);original=ContextSevenPremiseNativeWriteRunner.origins(baseline);pins=ContextSevenPremiseNativeWriteRunner.pins(f);long revision=f.db.getRevision();int rows=f.root.getRules().size();require(original.size()==2&&pins.size()==5,"durable two-source/five-pin baseline");
            DB.GroundProductionWriteResult early=write(f,2,3);require(early.status==DB.ProductionWriteStatus.INCOMPLETE&&early.before.getRevision()==revision&&early.after.getRevision()==revision,"two-round public request cannot settle");incomplete(early.trial.qualification,"Ground family common round limit");
            DB.GroundProductionWriteResult shallow=write(f,3,2);require(shallow.status==DB.ProductionWriteStatus.INCOMPLETE&&shallow.before.getRevision()==revision&&shallow.after.getRevision()==revision,"depth2 public request cannot settle");incomplete(shallow.trial.qualification,"Residual cumulative depth limit");
            DB.GroundProductionWriteResult full=write(f,3,3);require(full.status==DB.ProductionWriteStatus.COLLISION&&full.before.getRevision()==revision&&full.after.getRevision()==revision,"fresh complete public request rejects third-round conflict");Expected expected=new Expected(full.trial.artifact);collision(full.trial.qualification,expected,true);barriers(full.trial.artifact,expected);
            nativeRefusal(f,2,3,expected,"Ground family common round limit");nativeRefusal(f,3,2,expected,"Residual cumulative depth limit");nativeRefusal(f,3,3,expected,null);
            require(f.db.getRevision()==revision&&f.root.getRules().size()==rows&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(baseline.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"all public/native refusals preserve exact parent inventory, physical revision, rules and ordered pins");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(inv.primary.size()==2&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(original)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"reopen keeps exactly original two primary facts and five saved pins");}
        System.out.println("AUTHORITY_CHAIN reverse="+reverse+" rounds=3 alternatives=3 depth=2 settled=false");System.out.println(marker+" checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
