/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessFamilyFixedPoint.*;
import org.kanger.ContextWitnessFamilyFixedPoint.Result;

/** Owning synchronous rounds retain private progress and scan rebased local evidence. */
public final class ContextPrivateWitnessFamilyRunner {
    static int checks;static final int WORK=64000000,EVENTS=500000;
    static final Limits LIMITS=new Limits(12,16,WORK,EVENTS);
    static final String MID="!@w seed(w,1)->mid(w,1);",DONE="!@w mid(w,1)->done(w,1);",OUT="!@w done(w,1)->out(w,1);",NEG="!@w done(w,1)->~flag(w,1);";
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static Source source(Snapshot s,String text){return ContextPrivateWitnessPoolRunner.source(s,text);}
    static Declaration d(Snapshot s,String text){return new Declaration(source(s,text),text);}
    static Set<Source> set(Source...s){return new HashSet<>(Arrays.asList(s));}
    static boolean has(Packet p,String name){return p.items.stream().anyMatch(i->i.atom.predicate.equals(name));}
    static Item item(Packet p,String name){return ContextPrivateWitnessPoolRunner.item(p,name);}
    static void incomplete(Result r,String why){require(r.status==Status.INCOMPLETE&&r.packet==null&&r.rounds.isEmpty()&&r.privatePools.isEmpty(),why+": "+r.reason);}
    static List<Declaration> rules(Snapshot s){List<Declaration> ds=new ArrayList<>();for(ContextProductionNativeBinding.Entry e:s.primary)if(e.bundle==null&&!e.statement.equals("!anchor(1);"))ds.add(new Declaration(e.source,e.statement));return ds;}
    static Plan family(Snapshot x,Packet common,List<Snapshot> targets,boolean reverse){
        List<Branch> branches=new ArrayList<>();List<Declaration> shared=rules(x);branches.add(ContextWitnessFamilyFixedPoint.branch(Cut.of(x),shared));
        for(Snapshot target:targets){PrivatePool p=ContextWitnessResultTransport.privatePool(common,x,target,WORK);List<Declaration> ds=new ArrayList<>(shared);ds.addAll(rules(target));branches.add(ContextWitnessFamilyFixedPoint.privateBranch(p,ds));}
        if(reverse)Collections.reverse(branches);return ContextWitnessFamilyFixedPoint.plan(branches);
    }
    static void localOnly(boolean reverse)throws Exception{
        String warm="!@w never(w,1)->warm(w,1);",hidden="!@w seed(w,1)->hidden(w,1);";
        Snapshot x=bind(true,"!anchor(1); "+warm),a=bind(false,"!$w seed(w,1);"),b=bind(false,hidden),group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b),WORK);Packet initial=ContextPrivateWitnessPoolRunner.common(x);
        Plan p=family(x,initial,Arrays.asList(group),reverse);List<Map<Cut,PrivatePool>> observed=new ArrayList<>();
        Result r=p.run(initial,LIMITS,new BarrierCheck(){public void inspect(Packet packet){throw new AssertionError("private barrier callback must carry local states");}public void inspect(Packet packet,Map<Cut,PrivatePool> states){observed.add(states);require(states.size()==1&&states.get(Cut.of(group)).common==packet,"callback sees rebased local state at exact merged common barrier");}});
        require(r.status==Status.FIXED_POINT&&r.rounds.size()==2,"pure private progress requires changed and then stable rounds: "+r.reason);
        require(!r.rounds.get(0).commonChanged&&r.rounds.get(0).privateChanged&&r.rounds.get(0).changed,"unchanged X cannot terminate private progress");
        require(!r.rounds.get(1).changed&&r.rounds.get(1).unchanged==2,"all local/common pools must stop");
        require(ContextWitnessResultTransport.sameContents(initial,r.packet,WORK)&&!has(r.packet,"seed")&&!has(r.packet,"hidden"),"pure private inference never borrows unrelated X anchor");
        PrivatePool local=r.privatePools.get(Cut.of(group));require(has(local.local,"hidden")&&has(observed.get(0).get(Cut.of(group)).local,"hidden"),"private result persists in barrier and terminal evidence");
        require(item(local.local,"hidden").proofs.stream().anyMatch(s->s.sources.equals(set(a.primary.get(0).source,b.primary.get(0).source))&&s.depth==1),"private depth1 proof has exact original two sources");
        incomplete(p.run(initial,new Limits(1,16,WORK,EVENTS)),"common-stable/private-changing prefix cannot satisfy round cap");
        incomplete(p.run(initial,new Limits(12,0,WORK,EVENTS)),"private proof depth stop publishes no prefix");
        incomplete(p.run(initial,new Limits(12,16,1,EVENTS)),"work stop publishes no prefix");
        incomplete(p.run(initial,new Limits(12,16,WORK,1)),"event stop publishes no prefix");
        boolean immutable=false;try{r.privatePools.clear();}catch(UnsupportedOperationException e){immutable=true;}require(immutable,"terminal private state map is immutable");
        require(p.run(r.packet,LIMITS).rounds.size()==2,"rerunning original plan rebuilds its original private state, not hidden mutable cache");
        List<Branch> resumed=new ArrayList<>();for(Branch branch:p.branches)resumed.add(branch.privatePool==null?branch:ContextWitnessFamilyFixedPoint.privateBranch(local,branch.declarations));
        Result again=ContextWitnessFamilyFixedPoint.plan(resumed).run(r.packet,LIMITS);require(again.status==Status.FIXED_POINT&&again.rounds.size()==1,"explicit sealed continuation resumes already closed local pool");
    }
    static void pipeline(boolean reverse,boolean negative,boolean split)throws Exception{
        String end=negative?NEG:OUT;Snapshot x=bind(true,"!anchor(1); "+MID+" "+end),a=bind(false,split?"!$w seed(w,1); !$w flag(w,1);":"!$w seed(w,1)&&flag(w,1);"),b=bind(false,"!$w token(w,1); "+DONE);Packet initial=ContextPrivateWitnessPoolRunner.common(x);Plan p=family(x,initial,Arrays.asList(a,b),reverse);
        ContextWitnessFamilyQualification.Result q=ContextWitnessFamilyQualification.run(p,initial,LIMITS);
        if(negative){
            require(q.status==(split?ContextWitnessFamilyQualification.Status.INCOMPLETE:ContextWitnessFamilyQualification.Status.COLLISION),"local all-cut qualification uses original private witness identity: "+q.reason);
            if(split)require(q.collisions.isEmpty(),"split seed/flag witnesses never falsely refute");
            else {require(q.barrier==2&&q.collisions.size()==1,"independent B cannot use same-round A product; A refutes at barrier2");for(ContextWitnessClauseCollision.Result c:q.collisions){require(c.cut.equals(Cut.of(a)),"collision belongs to A with its private flag, not B's independent token");for(ContextWitnessClauseCollision.Evidence e:c.evidence){require(e.producer.equals(source(x,NEG)),"exact negative X producer");for(Support proof:e.proofs)require(proof.depth==3&&proof.sources.equals(set(a.primary.get(0).source,source(x,MID),source(b,DONE),source(x,NEG))),"four whole original sources at cumulative depth3");}}}
        }else{
            require(q.status==ContextWitnessFamilyQualification.Status.FIXED_POINT,"safe complete selected family closes: "+q.reason);Result r=q.fixedPoint;require(r.rounds.size()>=3&&q.barrier==r.rounds.size()&&!r.rounds.get(r.rounds.size()-1).changed,"mid, next-round B chain, then unchanged full closure");
            require(!has(r.packet,"seed")&&!has(r.packet,"flag")&&!has(r.packet,"token")&&has(r.packet,"mid")&&has(r.packet,"done")&&has(r.packet,"out"),"only X-dependent derived facts enter common X");
            require(item(r.packet,"out").proofs.stream().anyMatch(s->s.depth==3&&s.sources.equals(set(a.primary.get(0).source,source(x,MID),source(b,DONE),source(x,OUT)))),"cross-cut proof retains original foreign witness and exact depth3 support");
            for(PrivatePool local:r.privatePools.values())require(local.common==r.packet,"every terminal private pool rebased to exact terminal common packet");
            PrivatePool al=r.privatePools.get(Cut.of(a)),bl=r.privatePools.get(Cut.of(b));require(item(al.local,"flag").atom.arguments.get(0).origin.source.equals(a.primary.get(split?1:0).source),"private flag never relabelled to common X source");require(!has(bl.local,"flag")&&!has(bl.local,"seed"),"independent B receives common results, never A's raw private facts");
            int[] barrier={0};
            Result reverseResult=family(x,initial,Arrays.asList(a,b),!reverse).run(initial,LIMITS,packet->{
                ++barrier[0];if(barrier[0]==1)require(has(packet,"mid")&&!has(packet,"done"),"B cannot consume same-round A output");
                if(barrier[0]==2)require(has(packet,"done"),"B consumes A output only from the next common input");
            });require(reverseResult.status==Status.FIXED_POINT&&ContextWitnessResultTransport.sameContents(r.packet,reverseResult.packet,WORK)&&reverseResult.rounds.size()==r.rounds.size(),"branch order preserves common proof pool and activation rounds");
        }
        require(ContextProductionFamilyAssembly.assemble(x,Arrays.asList(ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b),WORK)),WORK).assembled,"complete private family assembly is available; live admission qualified separately");
    }
    static void alternatives()throws Exception{
        String warm="!@w never(w,1)->warm(w,1);",hidden="!@w seed(w,1)->hidden(w,1);";
        Snapshot x=bind(true,"!anchor(1); "+warm),a=bind(false,"!$w seed(w,1);"),b=bind(false,hidden),c=bind(false,hidden);
        Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b,c),WORK);Packet common=ContextPrivateWitnessPoolRunner.common(x);
        PrivatePool raw=ContextWitnessResultTransport.privatePool(common,x,group,WORK);
        PrivateAdvance previous=ContextWitnessResultTransport.advancePrivate(raw,set(b.primary.get(0).source),16,WORK,EVENTS);
        List<Declaration> shared=rules(x),selected=new ArrayList<>(shared);selected.addAll(rules(c));
        Plan plan=ContextWitnessFamilyFixedPoint.plan(Arrays.asList(ContextWitnessFamilyFixedPoint.branch(Cut.of(x),shared),ContextWitnessFamilyFixedPoint.privateBranch(previous.after,selected)));
        Result result=plan.run(common,LIMITS);require(result.status==Status.FIXED_POINT&&result.rounds.size()==2,"source-alternative-only private progress needs another barrier: "+result.reason);
        PrivatePool after=result.privatePools.get(Cut.of(group));require(after.local.items.size()==previous.after.local.items.size()&&item(after.local,"hidden").proofs.size()==2,"no new atom hides a new private proof alternative");
        require(!result.rounds.get(0).commonChanged&&result.rounds.get(0).privateChanged,"local source alternatives count toward family progress");
        ContextWitnessFamilyQualification.Result stopped=ContextWitnessFamilyQualification.run(plan,common,new Limits(12,0,WORK,EVENTS));
        require(stopped.status==ContextWitnessFamilyQualification.Status.INCOMPLETE&&stopped.collisions.isEmpty(),"carried private proof depth preflighted before initial collision scan");
    }
    static void preflight()throws Exception{
        Snapshot x=bind(true,"!anchor(1); "+MID),a=bind(false,"!$w seed(w,1);"),bad=bind(false,"!$w token(w,1); !@u @v @z tri(u,v,z)->bad(u);");Packet common=ContextPrivateWitnessPoolRunner.common(x);Plan p=family(x,common,Arrays.asList(a,bad),false);int[] callbacks={0};
        incomplete(p.run(common,LIMITS,packet->++callbacks[0]),"later unsupported whole private declaration preflighted");require(callbacks[0]==0,"no barrier/collision prefix published before all branches preflight");
        PrivatePool local=ContextWitnessResultTransport.privatePool(common,x,a,WORK);Declaration original=d(x,MID);Branch forged=ContextWitnessFamilyFixedPoint.privateBranch(local,Arrays.asList(new Declaration(original.source,"!@w seed(w,1)->fake(w,1);")));
        Plan altered=ContextWitnessFamilyFixedPoint.plan(Arrays.asList(ContextWitnessFamilyFixedPoint.branch(Cut.of(x),forged.declarations),forged));incomplete(altered.run(common,LIMITS),"same-source substituted caller statement cannot replace exact private owning declaration");
        ContextWitnessFamilyQualification.Result q=ContextWitnessFamilyQualification.run(p,common,LIMITS);require(q.status==ContextWitnessFamilyQualification.Status.INCOMPLETE&&q.collisions.isEmpty(),"full qualification rejects unsupported later private input before collision prefix");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("private-witness-family-").toString());for(boolean reverse:new boolean[]{false,true}){localOnly(reverse);for(boolean split:new boolean[]{false,true}){pipeline(reverse,false,split);pipeline(reverse,true,split);}}alternatives();preflight();System.out.println("CONTEXT_PRIVATE_WITNESS_FAMILY_PASS checks="+(checks+ContextPrivateWitnessPoolRunner.checks+ContextTrustedWitnessScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
