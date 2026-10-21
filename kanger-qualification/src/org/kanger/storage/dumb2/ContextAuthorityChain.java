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

/** Independent donor/bridge routes through three immutable family barriers. */
final class ContextAuthorityChain {
    static int checks;static final int WORK=64000000;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static void create(Path dir,String name,String text)throws Exception{try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){require(f.root.compile(text,null,false),"private source "+name);}}
    static final class Expected {
        final Set<Source> locals=new HashSet<>(),authorities=new HashSet<>(),bridges=new HashSet<>();final boolean negativeQ,negativeOut,negative,nullary;Source deny;
        Expected(ContextProductionCandidate.Staged a,int bridgeCount,boolean negativeQ,boolean negativeOut,boolean negative,boolean nullary){
            this.negativeQ=negativeQ;this.negativeOut=negativeOut;this.negative=negative;this.nullary=nullary;
            for(Entry e:a.staged.primary)locals.add(e.source);
            for(Snapshot cut:a.foreign)for(Entry e:cut.primary){Clause c=e.clauses.get(0);if(c.literals.size()==2&&c.literals.stream().anyMatch(l->l.predicate.equals("mid")&&l.positive!=negative)&&c.literals.stream().anyMatch(l->l.predicate.equals("out")&&l.positive==negativeOut))bridges.add(e.source);
                else if(c.literals.size()==1){Atom atom=c.literals.get(0);if(atom.predicate.equals("q")&&atom.positive!=negativeQ)authorities.add(e.source);else if(atom.predicate.equals("mid")&&atom.positive==negative)deny=e.source;}}
            require(locals.size()==3&&locals.contains(a.candidate)&&authorities.size()==3&&authorities.stream().map(s->s.context).distinct().count()==3&&bridges.size()==bridgeCount&&bridges.stream().map(s->s.context).distinct().count()==bridgeCount&&deny!=null,"whole three-source X, three independent q authorities, independent private bridges and deny");
        }
        Set<Source> set(Source... extra){Set<Source> s=new HashSet<>(locals);s.addAll(Arrays.asList(extra));return s;}
        Set<Set<Source>> pairs(){Set<Set<Source>> result=new HashSet<>();for(Source q:authorities)for(Source b:bridges)result.add(new HashSet<>(Arrays.asList(q,b)));return result;}
    }
    static List<Receipt> pass(Frontier f,int depth){List<Receipt> rs=new ArrayList<>();for(Snapshot cut:f.cuts)rs.add(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(f,cut,depth,WORK,500000,100000,16));return rs;}
    static void units(Frontier f,String predicate,boolean positive,int depth,List<Set<Source>> expected,boolean nullary){
        Set<Set<Source>> actual=new HashSet<>();int count=0;
        for(Product p:f.products)if(p.clause.literals.size()==1){Atom atom=p.clause.literals.get(0);if(atom.predicate.equals(predicate)&&atom.positive==positive){++count;require(atom.arguments.size()==(nullary?0:1)&&p.proof.depth==depth&&actual.add(p.proof.sources),"exact unique unit recipe with requested arity and cumulative depth for "+predicate);}}
        require(count==expected.size()&&actual.equals(new HashSet<>(expected)),"all and only independent whole-Source alternatives for "+predicate);
    }
    static void collisions(List<Collision> cs,Expected e,boolean exactLocal){
        Set<Set<Source>> pairs=e.pairs();require(cs.size()==2*pairs.size(),"two endpoint witnesses for every donor/bridge combination");Map<Set<Source>,Set<String>> routes=new HashMap<>();Set<Set<Source>> unions=new HashSet<>();
        for(Collision c:cs){
            require(c.left.opposite().equivalent(c.right)&&c.left.arguments.size()==(e.nullary?0:1)&&c.right.arguments.size()==(e.nullary?0:1)&&c.leftProof.depth==2&&c.rightProof.depth==0&&c.leftProof.sources.size()==5&&c.rightProof.sources.size()==1,"requested arity, transported five-source depth2 proof versus original private depth0 fact");
            Set<Source> union=new HashSet<>(c.leftProof.sources);union.addAll(c.rightProof.sources);Set<Source> local=new HashSet<>(),foreign=new HashSet<>();for(Source s:union)(s.localX?local:foreign).add(s);
            Set<Source> pair=new HashSet<>(foreign);pair.remove(e.deny);
            require(union.size()==6&&local.size()==3&&foreign.size()==3&&foreign.contains(e.deny)&&pairs.contains(pair),"refutation keeps whole X, deny, exactly one q authority and exactly one bridge");if(exactLocal)require(local.equals(e.locals),"exact detached whole local primary Sources");
            Source authority=pair.stream().filter(e.authorities::contains).findFirst().get();require(pair.stream().filter(e.bridges::contains).count()==1,"one independent pinned Bridge Source per refutation, never a flattened union");
            String route=c.left.predicate;require((route.equals("q")&&c.left.positive==e.negativeQ&&c.rightProof.sources.contains(authority))||(route.equals("mid")&&c.left.positive!=e.negative&&c.rightProof.sources.contains(e.deny)),"opposition reaches the signed authority or deny endpoint with requested conclusion sign");
            require(routes.computeIfAbsent(pair,k->new HashSet<>()).add(route),"no duplicate endpoint replaces a donor/bridge alternative");unions.add(union);
        }
        require(unions.size()==pairs.size()&&routes.keySet().equals(pairs)&&routes.values().stream().allMatch(r->r.equals(new HashSet<>(Arrays.asList("q","mid")))),"every independently enumerated donor/bridge pair occurs at both refutation endpoints");
    }
    static void collision(ContextGroundClauseFamily.Evaluation result,Expected e,boolean exactLocal){require(result!=null&&result.status==ContextGroundClauseFamily.Status.COLLISION&&result.rounds==3&&result.certificate==null,"complete third-round sourced conflict");collisions(result.collisions,e,exactLocal);}
    static void incomplete(ContextGroundClauseFamily.Evaluation result,String reason){require(result!=null&&result.status==ContextGroundClauseFamily.Status.INCOMPLETE&&result.rounds==2&&result.certificate==null&&result.collisions.isEmpty()&&result.reason.endsWith(reason),"bounded refusal has no certificate/collision prefix: "+(result==null?"null":result.reason));}
    static void barriers(ContextProductionCandidate.Staged a,Expected e){
        Frontier initial=ContextOrdinaryResidualRounds.start(a.staged,a.foreign,WORK,LedgerLimits.DEFAULT);Barrier first=ContextOrdinaryResidualRounds.merge(initial,pass(initial,3),WORK);
        int firstCount=15+4*e.bridges.size(),secondCount=15+20*e.bridges.size();
        require(first.status==Status.MERGED&&first.changed&&first.frontier.products.size()==firstCount&&first.frontier.ledgerLimits==LedgerLimits.DEFAULT&&initial.products.isEmpty(),"first immutable DEFAULT barrier with expected shared recipe count");
        List<Set<Source>> out=new ArrayList<>(),mid=new ArrayList<>(),oppositeQ=new ArrayList<>();for(Source q:e.authorities){out.add(e.set(q));for(Source b:e.bridges)mid.add(e.set(q,b));}for(Source b:e.bridges)oppositeQ.add(e.set(b,e.deny));
        units(first.frontier,"out",!e.negativeOut,1,out,e.nullary);require(first.frontier.products.stream().filter(p->p.clause.literals.size()==1).count()==3,"first round shares only the three signed out units; private facts are not imported");
        List<Receipt> secondPass=pass(first.frontier,3);Barrier second=ContextOrdinaryResidualRounds.merge(first.frontier,secondPass,WORK);
        require(second.status==Status.MERGED&&second.changed&&second.frontier.products.size()==secondCount&&second.frontier.products.stream().allMatch(p->p.proof.depth<=2)&&first.frontier.products.size()==firstCount&&second.frontier.ledgerLimits==LedgerLimits.DEFAULT,"second immutable DEFAULT barrier at depth<=2, previous input unchanged");
        units(second.frontier,"out",!e.negativeOut,1,out,e.nullary);units(second.frontier,"mid",!e.negative,2,mid,e.nullary);units(second.frontier,"q",e.negativeQ,2,oppositeQ,e.nullary);
        require(second.frontier.products.stream().filter(p->p.clause.literals.size()==1).count()==out.size()+mid.size()+oppositeQ.size(),"exact expected shared units, no raw q/bridge/deny import");
        ContextGroundClauseFamily.Evaluation premature=ContextGroundClauseFamily.certify(first.frontier,secondPass,3,WORK,1000000);
        require(premature.status==ContextGroundClauseFamily.Status.INCOMPLETE&&premature.certificate==null&&premature.collisions.isEmpty()&&premature.reason.equals("Ground certificate requires unchanged shared recipes"),"changed second barrier is not saturation or admission");
        List<Receipt> low=pass(second.frontier,1);for(Receipt r:low)require(!r.verified&&r.products.isEmpty()&&r.collisions.isEmpty()&&r.reason.equals("Residual cumulative depth limit"),"carried depth2 recipes refuse depth1 before exposing any collision prefix");
        Barrier stopped=ContextOrdinaryResidualRounds.merge(second.frontier,low,WORK);require(stopped.status==Status.INCOMPLETE&&stopped.frontier==null&&stopped.collisions.isEmpty(),"complete barrier refuses carried-depth cutoff");
        Barrier third=ContextOrdinaryResidualRounds.merge(second.frontier,pass(second.frontier,3),WORK);require(third.status==Status.COLLISION&&third.frontier==null&&second.frontier.products.size()==secondCount,"third barrier rejects without mutating second input");collisions(third.collisions,e,true);
    }
    static DB.GroundProductionWriteResult write(ContextSevenPremiseNativeWriteRunner.Fixture f,String text,int rounds,int depth)throws Exception{return f.db.writeGroundProductionCandidate(f.root,text,1000,rounds,16,depth,WORK,500000,100000,1000000,false,LedgerLimits.DEFAULT);}
    static void nativeRefusal(ContextSevenPremiseNativeWriteRunner.Fixture f,String text,int rounds,int depth,Expected e,String reason)throws Exception{
        ContextPendingNativeWrite attempt=f.db.beginGroundWrite(f.root,1000,rounds,16,depth,WORK,500000,100000,1000000,LedgerLimits.DEFAULT);
        try{ContextGroundProductionCommitException failure=null;try{f.root.query(text,null,false);}catch(ContextGroundProductionCommitException expected){failure=expected;}
            require(failure!=null&&failure.getErrorCode()==(reason==null?StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT:StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE),"actual owning native scope refuses precise typed status");
            if(reason==null)collision(failure.getFamilyEvaluation(),e,false);else incomplete(failure.getFamilyEvaluation(),reason);
        }finally{f.db.endNativeWrite(attempt);}
    }
    static void run(boolean reverse,String marker){run(reverse,1,marker);}
    static void run(boolean reverse,int bridgeCount,String marker){run(reverse,bridgeCount,false,false,marker);}
    static void run(boolean reverse,int bridgeCount,boolean negative,boolean nullary,String marker){run(reverse,bridgeCount,false,negative,nullary,marker);}
    static void run(boolean reverse,int bridgeCount,boolean negativeOut,boolean negative,boolean nullary,String marker){run(reverse,bridgeCount,false,negativeOut,negative,nullary,marker);}
    static void run(boolean reverse,int bridgeCount,boolean negativeQ,boolean negativeOut,boolean negative,boolean nullary,String marker){try{
        if(bridgeCount!=1&&bridgeCount!=2)throw new IllegalArgumentException("Qualified one/two-bridge fixtures only");
        String arg=nullary?"":"(1)",text=ContextWideGroundFamilyRunner.rule(3,negativeOut,nullary);
        if(negativeQ)text="!~"+text.substring(1);
        Path dir=Files.createTempDirectory("authority-chain-");System.setProperty("user.home",dir.toString());for(String name:new String[]{"A1","A2","A3"})create(dir,name,"!"+(negativeQ?"~":"")+"q"+arg+";");List<String> names=new ArrayList<>(Arrays.asList("A1","A2","A3"));for(int i=1;i<=bridgeCount;i++){String name=bridgeCount==1?"Bridge":"Bridge"+i;create(dir,name,"!"+(negativeOut?"~":"")+"out"+arg+"->"+(negative?"~":"")+"mid"+arg+";");names.add(name);}create(dir,"Deny","!"+(negative?"":"~")+"mid"+arg+";");names.add("Deny");
        Set<String> original;List<RevisionRef> pins;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile(ContextWideGroundFamilyRunner.base(3,nullary),null,false),"two local p assertions");if(reverse)Collections.reverse(names);for(String name:names)f.db.connectContext(name);f.db.saveConnections(f.root);
            ContextProductionInventory baseline=ContextSevenPremiseNativeWriteRunner.inventory(f);original=ContextSevenPremiseNativeWriteRunner.origins(baseline);pins=ContextSevenPremiseNativeWriteRunner.pins(f);long revision=f.db.getRevision();int rows=f.root.getRules().size();require(original.size()==2&&pins.size()==4+bridgeCount,"durable two-source baseline and all private pins");
            DB.GroundProductionWriteResult early=write(f,text,2,3);require(early.status==DB.ProductionWriteStatus.INCOMPLETE&&early.before.getRevision()==revision&&early.after.getRevision()==revision,"two-round public request cannot settle");incomplete(early.trial.qualification,"Ground family common round limit");
            DB.GroundProductionWriteResult shallow=write(f,text,3,2);require(shallow.status==DB.ProductionWriteStatus.INCOMPLETE&&shallow.before.getRevision()==revision&&shallow.after.getRevision()==revision,"depth2 public request cannot settle");incomplete(shallow.trial.qualification,"Residual cumulative depth limit");
            DB.GroundProductionWriteResult full=write(f,text,3,3);require(full.status==DB.ProductionWriteStatus.COLLISION&&full.before.getRevision()==revision&&full.after.getRevision()==revision,"fresh complete public request rejects third-round conflict");Expected expected=new Expected(full.trial.artifact,bridgeCount,negativeQ,negativeOut,negative,nullary);collision(full.trial.qualification,expected,true);barriers(full.trial.artifact,expected);
            nativeRefusal(f,text,2,3,expected,"Ground family common round limit");nativeRefusal(f,text,3,2,expected,"Residual cumulative depth limit");nativeRefusal(f,text,3,3,expected,null);
            require(f.db.getRevision()==revision&&f.root.getRules().size()==rows&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(baseline.fingerprint)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"all public/native refusals preserve exact parent inventory, physical revision, rules and ordered pins");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(inv.primary.size()==2&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(original)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"reopen keeps exactly original two primary facts and all saved pins");}
        System.out.println("AUTHORITY_CHAIN reverse="+reverse+" bridges="+bridgeCount+" negativeQ="+negativeQ+" negativeOut="+negativeOut+" negative="+negative+" nullary="+nullary+" rounds=3 alternatives="+(3*bridgeCount)+" depth=2 settled=false");System.out.println(marker+" checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
