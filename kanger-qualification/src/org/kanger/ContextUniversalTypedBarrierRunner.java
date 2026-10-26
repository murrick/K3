/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Typed products cross only their actual common/local barrier; no coverage or admission stamp. */
public final class ContextUniversalTypedBarrierRunner {
    static final int WORK=64000000;static int checks;
    static final String SEED="!$w seed(w,1)&&q(7,1);",OUT="!@w seed(w,1)&&rel(w,7)->out(w,1);",P="!@u p(u,1);",REL="!@u @v p(u,1)&&q(v,1)->rel(u,v);",FINISH="!@w out(w,1)->finish(w,1);";
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot x,String s){return ContextPrivateWitnessPoolRunner.source(x,s);}
    static boolean has(Packet p,String name){return p.items.stream().anyMatch(i->i.atom.predicate.equals(name));}
    static ContextUniversalFrozenEvents.Result proof(Snapshot x,Snapshot a,Packet common,PrivatePool pool){
        ContextUniversalUnitClosure.Result schema=ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);require(schema.unitClosed,"schema precursor "+schema.reason);
        ContextUniversalFrozenClosure.Result typed=pool==null?ContextUniversalFrozenClosure.close(schema,common,16,16,512,256,WORK):ContextUniversalFrozenClosure.closePrivate(schema,pool,16,16,512,256,WORK);require(typed.closed,"typed precursor "+typed.reason);
        ContextUniversalFrozenReplay.Result replay=ContextUniversalFrozenReplay.replay(typed,16,WORK,500000);require(replay.bound,"native precursor "+replay.reason);
        ContextUniversalFrozenUnits.Result units=ContextUniversalFrozenUnits.project(replay,16,65536,WORK);require(units.projected,"unit precursor "+units.reason);
        ContextUniversalFrozenEvents.Result proof=ContextUniversalFrozenEvents.reconstruct(units,16,65536,WORK);require(proof.reconstructed,"event precursor "+proof.reason);return proof;
    }
    static void unmarked(Packet p){require(p.basis==null&&p.producers.isEmpty()&&!p.observedStep&&p.emptyCut==null&&p.coveredCut==null&&p.roundCut==null&&p.privateTarget==null&&p.emptyTarget==null,"typed product has no branch/coverage/admission stamp");}
    static TypedTransfer transfer(ContextUniversalFrozenEvents.Result p,Packet before){TypedTransfer r=ContextWitnessResultTransport.transferTyped(p,before,16,WORK);require(r.transferred,"typed transfer "+r.reason);unmarked(r.output);return r;}
    static TypedBarrier merge(Family f,Map<Cut,TypedTransfer> outputs){TypedBarrier r=ContextWitnessResultTransport.mergeTyped(f,outputs,WORK);require(r.merged,"typed merge "+r.reason);unmarked(r.packet);return r;}
    static void denied(TypedTransfer r,String why){require(!r.transferred&&r.output==null&&r.local==null&&r.privateAfter==null,why+": "+r.reason);}
    static void denied(TypedBarrier r,String why){require(!r.merged&&r.packet==null&&r.branches.isEmpty()&&r.privatePools.isEmpty(),why+": "+r.reason);}
    static Map<Cut,TypedTransfer> map(TypedTransfer... branches){Map<Cut,TypedTransfer> r=new LinkedHashMap<>();for(TypedTransfer b:branches)r.put(b.target,b);return r;}
    static void common(boolean reverse)throws Exception {
        Snapshot x=bind(true,SEED+OUT),a=bind(false,P+REL),b=bind(false,FINISH);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalFrozenEvents.Result ap=proof(x,a,input,null);TypedTransfer at=transfer(ap,input),bt=transfer(proof(x,b,input,null),input),xt=transfer(proof(x,null,input,null),input);
        require(has(at.output,"out")&&!has(bt.output,"finish")&&!has(input,"out"),"B sees only immutable common input, never A's same-round out");
        TypedTransfer limited=ContextWitnessResultTransport.transferTyped(ap,input,3,WORK);require(limited.transferred,"fixed theorem covers longer event at owning depth3: "+limited.reason);
        require(ContextWitnessResultTransport.sameContents(limited.output,at.output,WORK),"all exact fact/Source minima agree at depth3 and16");
        for(Item item:limited.output.items)if(item.atom.predicate.equals("out"))for(Support s:item.proofs)require(s.depth==3&&s.sources.equals(new HashSet<>(Arrays.asList(source(x,SEED),source(x,OUT),source(a,P),source(a,REL)))),"out3 exact four-Source proof survives depth4 native recipes");
        denied(ContextWitnessResultTransport.transferTyped(ap,input,2,WORK),"genuinely uncovered depth3 fact no prefix");
        denied(ContextWitnessResultTransport.transferTyped(ap,input,16,1),"transfer work limit no prefix");
        denied(ContextWitnessResultTransport.transferTyped(ap,bt.output,16,WORK),"equal common facts at another actual identity cannot replace input");
        require(ContextWitnessResultTransport.sameContents(input,bt.output,WORK),"different identity fixture is semantically equal");
        List<Cut> cuts=reverse?Arrays.asList(bt.target,xt.target,at.target):Arrays.asList(at.target,xt.target,bt.target);Family f=ContextWitnessResultTransport.family(input,cuts,WORK);
        Map<Cut,TypedTransfer> outputs=reverse?map(bt,xt,at):map(at,xt,bt);TypedBarrier first=merge(f,outputs);
        require(first.changed&&first.localChanged&&has(first.packet,"out")&&!has(first.packet,"finish"),"first barrier retains products but cannot grant later B inference");
        List<Cut> reversed=new ArrayList<>(cuts);Collections.reverse(reversed);TypedBarrier reordered=merge(ContextWitnessResultTransport.family(input,reversed,WORK),reverse?map(at,xt,bt):map(bt,xt,at));
        require(ContextWitnessResultTransport.sameContents(first.packet,reordered.packet,WORK),"expected-cut/map order preserves every Source alternative and depth");
        Map<Cut,Packet> alleged=new LinkedHashMap<>();for(TypedTransfer branch:outputs.values())alleged.put(branch.target,branch.output);
        require(f.finish(alleged,WORK).status==FamilyStatus.INCOMPLETE,"typed proof products cannot masquerade as family STEP");
        TypedTransfer na=transfer(proof(x,a,first.packet,null),first.packet),nb=transfer(proof(x,b,first.packet,null),first.packet),nx=transfer(proof(x,null,first.packet,null),first.packet);
        TypedBarrier second=merge(ContextWitnessResultTransport.family(first.packet,cuts,WORK),map(na,nb,nx));require(second.changed&&has(second.packet,"finish"),"B consumes out only in next synchronous input");
        for(Item item:second.packet.items)if(item.atom.predicate.equals("finish"))for(Support s:item.proofs)require(s.depth==4&&s.sources.equals(new HashSet<>(Arrays.asList(source(x,SEED),source(x,OUT),source(a,P),source(a,REL),source(b,FINISH)))),"finish retains A/X/B ancestry at depth4");
        denied(ContextWitnessResultTransport.mergeTyped(f,map(at,nb,xt),WORK),"mixed input barriers no prefix");
        denied(ContextWitnessResultTransport.mergeTyped(f,map(at,bt),WORK),"missing expected X cut no prefix");
        Map<Cut,TypedTransfer> swapped=new LinkedHashMap<>();swapped.put(at.target,bt);swapped.put(bt.target,at);swapped.put(xt.target,xt);denied(ContextWitnessResultTransport.mergeTyped(f,swapped,WORK),"wrong owning cut no prefix");
        denied(ContextWitnessResultTransport.mergeTyped(f,outputs,1),"merge work limit no prefix");
        denied(ContextWitnessResultTransport.transferTyped(nb.proof,first.packet,2,WORK),"carried input/next fact depths not reset");
        Packet prior=second.packet;boolean stable=false;
        for(int round=3;round<=8;++round) {
            TypedTransfer aa=transfer(proof(x,a,prior,null),prior),bb=transfer(proof(x,b,prior,null),prior),xx=transfer(proof(x,null,prior,null),prior);
            TypedBarrier following=merge(ContextWitnessResultTransport.family(prior,cuts,WORK),reverse?map(bb,xx,aa):map(aa,xx,bb));
            System.out.println("TYPED_BARRIER round="+round+" changed="+following.changed+" localChanged="+following.localChanged);
            if(round==3)require(following.changed&&prior.items.size()==following.packet.items.size(),"whole Source alternatives can grow after literal set stabilizes");
            if(!following.changed&&!following.localChanged){stable=true;break;}prior=following.packet;
        }
        require(stable,"fixture proof alternatives stabilize without claiming general termination or family qualification");
    }
    static void privateInput(boolean reverse)throws Exception {
        String seed="!$w seed(w,1);",gate="!@v gate(v,1);",rel="!@u @v seed(u,1)&&gate(v,1)->rel(u,v);",out="!@w rel(w,7)->out(w,1);",anchor="!anchor(1);",raw="!privateValue(7);";
        Snapshot x=bind(true,anchor+out),a=bind(false,seed+gate+raw),member=bind(false,rel),b=bind(false,FINISH);
        Snapshot target=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(member,a):Arrays.asList(a,member),WORK);Packet input=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(input,x,target,WORK);
        ContextUniversalFrozenEvents.Result p=proof(x,target,input,pool);TypedTransfer at=ContextWitnessResultTransport.transferTypedPrivate(p,pool,16,WORK);require(at.transferred,"private typed transfer "+at.reason);unmarked(at.output);
        require(at.privateBefore==pool&&at.privateAfter.common==input&&at.privateAfter.cut.equals(pool.cut),"exact private owner/common input before barrier");
        require(has(at.local,"rel")&&has(at.output,"out")&&at.localChanged&&at.commonChanged,"private derived rel retained; genuine X-dependent out exported");
        for(String name:Arrays.asList("seed","gate","rel","privateValue"))require(!has(at.output,name),"unrelated X anchor cannot export private "+name);
        for(Item item:at.local.items)if(item.atom.predicate.equals("rel"))for(Support s:item.proofs)require(!s.sources.contains(source(x,anchor)),"private proof contains no unrelated X anchor");
        for(Item item:at.output.items)if(item.atom.predicate.equals("out"))for(Support s:item.proofs)require(s.depth==3&&s.sources.equals(new HashSet<>(Arrays.asList(source(a,seed),source(a,gate),source(member,rel),source(x,out)))),"shared out preserves actual private owner, X consumer and minimum depth3");
        TypedTransfer bt=transfer(proof(x,b,input,null),input);Family f=ContextWitnessResultTransport.family(input,reverse?Arrays.asList(bt.target,at.target):Arrays.asList(at.target,bt.target),WORK);TypedBarrier first=merge(f,map(at,bt));PrivatePool next=first.privatePools.get(pool.cut);
        require(next!=null&&next.common==first.packet&&has(next.local,"seed")&&has(next.local,"rel")&&has(next.local,"out"),"private continuation rebased after whole barrier");
        require(pool.common==input&&!has(pool.local,"out")&&at.privateAfter.common==input,"prior pools remain immutable");
        for(String name:Arrays.asList("seed","gate","rel","privateValue"))require(!has(first.packet,name),"barrier cannot communalize private "+name);
        TypedTransfer following=transfer(proof(x,b,first.packet,null),first.packet);require(has(following.output,"finish"),"independent B consumes only shared out");
        require(ContextWitnessResultTransport.transferTypedPrivate(proof(x,target,first.packet,next),next,16,WORK).transferred,"private continuation replays on rebased input");
        denied(ContextWitnessResultTransport.transferTyped(p,input,16,WORK),"private proof cannot use common-only path");
        denied(ContextWitnessResultTransport.transferTypedPrivate(p,next,16,WORK),"old proof cannot use rebased local input");
        PrivatePool independent=ContextWitnessResultTransport.privatePool(input,x,a,WORK);TypedTransfer alone=ContextWitnessResultTransport.transferTypedPrivate(proof(x,a,input,independent),independent,16,WORK);require(alone.transferred&&!has(alone.output,"out"),"independent A cannot borrow unrelated member's rel rule");
        denied(ContextWitnessResultTransport.transferTypedPrivate(p,independent,16,WORK),"trusted proof cannot be relabelled as an independent owning cut");
    }
    static void twoOriginsAndAlternatives()throws Exception {
        Snapshot x=bind(true,"!$u $v pair(u,v);"),a=bind(false,"!@u @v pair(u,v)->rel(u,v); !@u @v rel(u,v)->out(u,v);");Packet input=ContextPrivateWitnessPoolRunner.common(x);TypedTransfer t=transfer(proof(x,a,input,null),input);
        Item out=t.output.items.stream().filter(i->i.atom.predicate.equals("out")).findFirst().get();require(out.atom.arguments.get(0).origin!=null&&!out.atom.arguments.get(0).origin.equals(out.atom.arguments.get(1).origin),"two original binders remain distinct after Packet transport");
        Replay restored=ContextWitnessResultTransport.restore(t.output,WORK);require(restored.witnesses.size()==2&&Boolean.TRUE.equals(restored.mind.query("?$u $v pair(u,v)&&out(u,v);",null,false)),"fresh native restore preserves two-origin correlation");
        String seed="!$w seed(w,1);",q="!@w seed(w,1)->q(w,1);",consumer="!@w q(w,1)->out(w,1);";
        Snapshot cx=bind(true,seed),ca=bind(false,q),cb=bind(false,q),cc=bind(false,consumer),target=ContextProductionNativeBinding.trusted("own",Arrays.asList(ca,cb,cc),WORK);
        Packet initial=ContextPrivateWitnessPoolRunner.common(cx);TypedTransfer alternatives=transfer(proof(cx,target,initial,null),initial);
        Set<Support> groups=new HashSet<>();for(Item item:alternatives.output.items)if(item.atom.predicate.equals("out"))groups.addAll(item.proofs);
        require(groups.equals(new HashSet<>(Arrays.asList(new Support(new HashSet<>(Arrays.asList(source(cx,seed),source(ca,q),source(cc,consumer))),2),new Support(new HashSet<>(Arrays.asList(source(cx,seed),source(cb,q),source(cc,consumer))),2)))),"coalesced exact Source groups remain separate with minimum depth2");
    }
    static void privateTwoOrigins()throws Exception {
        String sx="!$u seed(u,1);",sy="!$v flag(v,1);",rel="!@u @v seed(u,1)&&flag(v,1)->rel(u,v);",out="!@u @v rel(u,v)->out(u,v);";
        Snapshot x=bind(true,"!anchor(1);"+out),a=bind(false,sx+sy+rel);Packet input=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(input,x,a,WORK);
        TypedTransfer t=ContextWitnessResultTransport.transferTypedPrivate(proof(x,a,input,pool),pool,16,WORK);require(t.transferred,"private derived two-origin product "+t.reason);
        require(has(t.local,"rel")&&!has(t.output,"rel")&&has(t.output,"out"),"private two-origin rel stays local, real X consumer permits out");
        for(Item item:t.output.items)if(item.atom.predicate.equals("out")) {
            require(!item.atom.arguments.get(0).origin.equals(item.atom.arguments.get(1).origin),"two different private original roots remain distinct");
            for(Support support:item.proofs)require(support.depth==3&&support.sources.equals(new HashSet<>(Arrays.asList(source(a,sx),source(a,sy),source(a,rel),source(x,out)))),"two-origin shared product exact whole Sources/depth3");
        }
        TypedBarrier b=merge(ContextWitnessResultTransport.family(input,Collections.singletonList(t.target),WORK),map(t));
        PrivatePool next=b.privatePools.get(t.target);require(next!=null&&has(next.local,"rel")&&has(next.local,"out"),"rebase preserves two-origin derived local facts without widening primary private declarations");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("typed-barriers-").toString());
        common(false);common(true);privateInput(false);privateInput(true);twoOriginsAndAlternatives();privateTwoOrigins();denied(ContextWitnessResultTransport.transferTyped(null,null,16,WORK),"no sealed proof");
        System.out.println("CONTEXT_UNIVERSAL_TYPED_BARRIER_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
