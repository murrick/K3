/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalFrozenRounds.*;
import org.kanger.ContextUniversalFrozenRounds.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** Stable positive pools include all Source alternatives and private state, never family authority. */
public final class ContextUniversalFrozenRoundsRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static ContextUniversalFrozenBranch.Limits full(){return ContextUniversalFrozenBranchRunner.full();}
    static Result run(Snapshot x,List<Snapshot> foreign,Packet input,Map<Cut,PrivatePool> pools,int rounds) {
        return ContextUniversalFrozenRounds.run(x,foreign,input,pools,full(),rounds,WORK);
    }
    static boolean has(Packet p,String name){return p.items.stream().anyMatch(i->i.atom.predicate.equals(name));}
    static void no(Result r,String phase,int completed,String why){require(!r.stabilized&&r.output==null&&r.history.isEmpty()&&(phase==null||r.phase.equals(phase))&&r.completedRounds==completed,why+": "+r.phase+" "+r.reason);}
    static Result good(Result r){require(r.stabilized&&r.output!=null&&r.output.merged,"positive pool stabilization "+r.phase+" "+r.reason);require(r.history.size()==r.completedRounds,"whole successful history only");return r;}
    static void audit(Result r,int cuts) {
        Packet prior=r.input;Map<Cut,PrivatePool> pools=null;
        for(Round round:r.history) {
            require(round.number>=1&&round.input==prior,"exact immutable input chain");
            if(pools!=null)for(Cut cut:pools.keySet())require(round.privateInput.get(cut)==pools.get(cut),"actual prior private continuation");
            require(round.barrier.branches.size()==cuts,"all supplied branches and self-X present");
            for(ContextUniversalFrozenBranch.Result b:round.barrier.branches.values()) {
                require(b.produced&&b.common==round.input&&b.premises!=null&&b.products.bound,"whole owning instance pipeline for every cut");
                require(b.products.transfer.before==round.input,"no branch reads another branch same-round output");
                require(b.pool==round.privateInput.get(b.products.transfer.target),"actual private input frame per branch");
                ContextUniversalFrozenBranchRunner.unmarked(b.products.transfer.output);
            }
            require(round.commonChanged==!ContextWitnessResultTransport.sameContents(prior,round.barrier.output.packet,WORK),"full common fact/Source/grade comparison");
            prior=round.barrier.output.packet;pools=round.barrier.output.privatePools;
        }
        Round last=r.history.get(r.history.size()-1);require(!last.commonChanged&&!last.privateChanged&&!last.barrier.output.localChanged,"stable means unchanged entire checked round");
        require(last.barrier==r.output,"only last complete stable barrier exposed");
        Map<Cut,Packet> packets=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalFrozenBranch.Result> e:r.output.branches.entrySet())packets.put(e.getKey(),e.getValue().products.transfer.output);
        require(ContextWitnessResultTransport.family(last.input,new ArrayList<>(packets.keySet()),WORK).finish(packets,WORK).status==FamilyStatus.INCOMPLETE,"positive stability issues no family STEP");
    }
    static void common()throws Exception {
        Snapshot x=bind(true,ContextUniversalFrozenBranchRunner.SEED+ContextUniversalFrozenBranchRunner.OUT),a=bind(false,ContextUniversalFrozenBranchRunner.P+ContextUniversalFrozenBranchRunner.REL),b=bind(false,ContextUniversalFrozenBranchRunner.FINISH);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Result forward=good(run(x,Arrays.asList(a,b),input,Collections.emptyMap(),8));audit(forward,3);require(forward.completedRounds==4,"full Source pools stabilize at4, not literal stability3");
        Round three=forward.history.get(2);require(three.input.items.size()==three.barrier.output.packet.items.size()&&three.commonChanged,"new exact Source alternatives despite stable atom count");
        Result reverse=good(run(x,Arrays.asList(b,a),input,Collections.emptyMap(),8));audit(reverse,3);require(reverse.completedRounds==forward.completedRounds&&ContextWitnessResultTransport.sameContents(reverse.output.output.packet,forward.output.output.packet,WORK),"supplied cut order preserves all final proofs and closure round");
        require(!has(forward.history.get(0).barrier.output.packet,"finish")&&has(forward.history.get(1).barrier.output.packet,"finish"),"B consumes prior barrier out only");
        Set<Source> expected=new HashSet<>(Arrays.asList(ContextPrivateWitnessPoolRunner.source(x,ContextUniversalFrozenBranchRunner.SEED),ContextPrivateWitnessPoolRunner.source(x,ContextUniversalFrozenBranchRunner.OUT),ContextPrivateWitnessPoolRunner.source(a,ContextUniversalFrozenBranchRunner.P),ContextPrivateWitnessPoolRunner.source(a,ContextUniversalFrozenBranchRunner.REL),ContextPrivateWitnessPoolRunner.source(b,ContextUniversalFrozenBranchRunner.FINISH)));
        require(forward.output.output.packet.items.stream().filter(i->i.atom.predicate.equals("finish")).flatMap(i->i.proofs.stream()).anyMatch(s->s.depth==4&&s.sources.equals(expected)),"full exact finish Source ancestry and minimum4");
        Result again=good(run(x,Arrays.asList(a,b),forward.output.output.packet,Collections.emptyMap(),1));audit(again,3);require(ContextWitnessResultTransport.sameContents(again.output.output.packet,forward.output.output.packet,WORK),"one checked unchanged round on stable pools is idempotent");
        no(run(x,Arrays.asList(a,b),input,Collections.emptyMap(),1),"round-limit",1,"round cap hides complete but nonstable prefix");
        no(run(x,Arrays.asList(a,b),input,Collections.emptyMap(),3),"round-limit",3,"literal stability alone cannot satisfy round cap");
        ContextUniversalFrozenBranch.Limits shallow=new ContextUniversalFrozenBranch.Limits(16,16,3,512,256,WORK,500000,65536);
        no(ContextUniversalFrozenRounds.run(x,Arrays.asList(a,b),input,Collections.emptyMap(),shallow,8,WORK),"branches",1,"later-round fact4 failure hides earlier valid barrier/history");
        require(!has(input,"out")&&!has(input,"finish"),"caller input unchanged after all success/failure runs");
        try{forward.history.clear();throw new AssertionError();}catch(UnsupportedOperationException expectedFailure){++checks;}
    }
    static void privateOnly()throws Exception {
        String seed="!$w seed(w,1);",mid="!@w seed(w,1)->mid(w,1);";
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,seed),member=bind(false,mid),target=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,member),WORK),b=bind(false,"!@w mid(w,1)->finish(w,1);");Packet input=ContextPrivateWitnessPoolRunner.common(x);PrivatePool original=ContextWitnessResultTransport.privatePool(input,x,target,WORK);Map<Cut,PrivatePool> pools=Collections.singletonMap(original.cut,original);
        Result r=good(run(x,Arrays.asList(target,b),input,pools,8));audit(r,3);
        require(!r.history.get(0).commonChanged&&r.history.get(0).privateChanged&&r.completedRounds>=2,"common stability cannot hide private-only growth");
        require(!has(r.output.output.packet,"mid")&&!has(r.output.output.packet,"seed")&&!has(r.output.output.packet,"finish"),"unrelated X anchor cannot communalize private proofs or feed independent B");
        PrivatePool after=r.output.output.privatePools.get(original.cut);require(has(after.local,"mid")&&after.common==r.output.output.packet,"derived private proof retained and rebased at stable barrier");
        require(!has(original.local,"mid")&&original.common==input,"original sealed private pool unchanged");
        Result again=good(run(x,Arrays.asList(b,target),after.common,r.output.output.privatePools,1));audit(again,3);require(ContextWitnessResultTransport.sameContents(again.output.output.privatePools.get(original.cut).local,after.local,WORK),"private stable pool idempotence under reversed order");
        no(run(x,Arrays.asList(target,b),input,pools,1),"round-limit",1,"private-only changed round cannot return common-stable success");
        no(run(x,Arrays.asList(target),input,Collections.emptyMap(),8),"branches",0,"omitted private original inventory cannot stabilize partial positive branch");
        no(run(x,Arrays.asList(target),ContextPrivateWitnessPoolRunner.common(x),pools,8),"preflight",0,"equal contents cannot replace exact private common frame");
        no(run(x,Arrays.asList(target,a),input,pools,8),"preflight",0,"overlapping trusted member cuts rejected by exact family inventory");
        try{r.history.get(0).privateInput.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}
    }
    static void invalidAndOpen()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!@v p(v,1);"),bad=bind(false,"!~bad(1);");Packet input=ContextPrivateWitnessPoolRunner.common(x);
        no(run(x,Arrays.asList(a,bad),input,Collections.emptyMap(),8),"branches",0,"later unsupported branch cannot expose earlier successful self-X/A prefix");
        no(run(x,Arrays.asList(a,a),input,Collections.emptyMap(),8),"preflight",0,"duplicate supplied cut");
        no(ContextUniversalFrozenRounds.run(x,Arrays.asList(a),input,Collections.emptyMap(),full(),8,1),"preflight",0,"scheduler preflight work limit");
        no(run(x,Collections.singletonList(null),input,Collections.emptyMap(),8),"preflight",0,"null foreign cut");
        Map<Cut,PrivatePool> wrong=new HashMap<>();wrong.put(Cut.of(x),null);no(run(x,Collections.singletonList(a),input,wrong,8),"preflight",0,"unexpected/null private frame");
        Result empty=good(run(x,Collections.emptyList(),input,Collections.emptyMap(),1));audit(empty,1);require(empty.completedRounds==1,"self-X is automatic even with no foreign cuts");
        Snapshot symbolic=bind(false,"!@v p(v,1)&&missing(v,1)->q(v,1);");Result open=good(run(x,Collections.singletonList(symbolic),input,Collections.emptyMap(),1));audit(open,2);
        require(open.output.branches.get(Cut.of(symbolic)).premises.symbolic>0,"symbolic obligations survive stable positive pools");
        require(!has(open.output.output.packet,"q"),"stability does not invent enabled premises");
        try{ContextUniversalFrozenRounds.run(x,Collections.emptyList(),input,Collections.emptyMap(),full(),0,WORK);throw new AssertionError();}catch(IllegalArgumentException expected){++checks;}
    }
    static void instancesAndTwoOrigins()throws Exception {
        Snapshot x=bind(true,"!anchor(1); !@u p(u,1);"),a=bind(false,"!p(7,1)&&missing(8,1)->done(1);");Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Result r=good(run(x,Collections.singletonList(a),input,Collections.emptyMap(),8));audit(r,2);
        require(has(r.output.output.packet,"p")&&!has(r.output.output.packet,"done"),"enabled generated instance participates in pool closure, disabled premise does not");
        require(r.history.get(0).barrier.branches.get(Cut.of(a)).products.products.stream().anyMatch(v->v.instance!=null),"whole generated instance product chain retained in history");
        require(r.output.branches.get(Cut.of(a)).premises.obligations.stream().anyMatch(o->!o.enabled),"disabled obligations remain explicit after pool stability");
        Snapshot tx=bind(true,"!$u $v pair(u,v);"),ta=bind(false,"!@u @v pair(u,v)->rel(u,v);"),tb=bind(false,"!@u @v rel(u,v)->out(u,v);");Packet initial=ContextPrivateWitnessPoolRunner.common(tx);
        Result two=good(run(tx,Arrays.asList(ta,tb),initial,Collections.emptyMap(),8));audit(two,3);
        Item out=two.output.output.packet.items.stream().filter(i->i.atom.predicate.equals("out")).findFirst().get();
        require(out.atom.arguments.get(0).origin!=null&&out.atom.arguments.get(1).origin!=null&&!out.atom.arguments.get(0).origin.equals(out.atom.arguments.get(1).origin),"two original binders remain distinct through all rounds");
        require(out.proofs.stream().anyMatch(p->p.depth==2&&p.sources.size()==3),"two-Origin conclusion keeps original whole Source history and minimum2");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-rounds-").toString());common();privateOnly();invalidAndOpen();instancesAndTwoOrigins();System.out.println("CONTEXT_UNIVERSAL_FROZEN_ROUNDS_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
