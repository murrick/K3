/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenBranch.*;
import org.kanger.ContextUniversalFrozenBranch.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** One source of owning branch evidence, atomic supplied barriers and no intermediate failure prefix. */
public final class ContextUniversalFrozenBranchRunner {
    static final int WORK=64000000;static int checks;
    static final String SEED="!$w seed(w,1)&&q(7,1);",OUT="!@w seed(w,1)&&rel(w,7)->out(w,1);",P="!@u p(u,1);",REL="!@u @v p(u,1)&&q(v,1)->rel(u,v);",FINISH="!@w out(w,1)->finish(w,1);";
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot x,String s){return ContextPrivateWitnessPoolRunner.source(x,s);}
    static Limits limits(int rounds,int proof,int fact,int work,int events,int receipts){return new Limits(rounds,proof,fact,512,256,work,events,receipts);}
    static Limits full(){return limits(16,16,16,WORK,500000,65536);}
    static boolean has(Packet p,String name){return p.items.stream().anyMatch(i->i.atom.predicate.equals(name));}
    static Result pass(Snapshot x,Snapshot a,Packet input,PrivatePool pool,Limits limits) {
        Result r=ContextUniversalFrozenBranch.run(x,a,input,pool,limits);require(r.produced,"owning pass "+r.phase+": "+r.reason);
        require(r.products!=null&&r.products.bound&&r.products.coverage.covered&&r.products.transfer.transferred,"complete audit/transfer/product chain only");
        require(r.x==x&&r.foreign==a&&r.common==input&&r.pool==pool&&r.limits==limits,"actual owning inputs/phase limits retained");
        require(r.products.coverage.input==r.products.transfer.proof.input.input.theory,"one exact ledger used through all phases");
        require(r.products.transfer.before==input&&r.products.transfer.privateBefore==pool,"one exact common/private frame");
        unmarked(r.products.transfer.output);return r;
    }
    static void unmarked(Packet p){require(p.basis==null&&p.producers.isEmpty()&&!p.observedStep&&p.emptyCut==null&&p.coveredCut==null&&p.roundCut==null&&p.privateTarget==null&&p.emptyTarget==null,"pipeline output has no family/STEP/qualification stamps");}
    static void denied(Result r,String phase,String why){require(!r.produced&&r.products==null&&(phase==null||phase.equals(r.phase)),why+": "+r.phase+" "+r.reason);}
    static Map<Cut,Result> map(Result... results){Map<Cut,Result> r=new LinkedHashMap<>();for(Result b:results)r.put(b.products.transfer.target,b);return r;}
    static Barrier merge(Family family,Map<Cut,Result> outputs){Barrier b=ContextUniversalFrozenBranch.merge(family,outputs,WORK);require(b.merged&&b.output!=null,"owning barrier "+b.reason);require(b.branches.size()==family.expected.size(),"all supplied checked pipelines retained");unmarked(b.output.packet);return b;}
    static void denied(Barrier b,String why){require(!b.merged&&b.output==null&&b.branches.isEmpty(),why+": "+b.reason);}
    static void phasesAndBounds()throws Exception {
        Snapshot x=bind(true,SEED+OUT),a=bind(false,P+REL);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Result r=pass(x,a,input,null,limits(16,16,3,WORK,500000,65536));
        require(r.products.products.stream().anyMatch(p->p.event!=null&&p.expected.depth==4&&p.localProof.depth==3),"independent exploration16 versus accepted fact3, no recipe depth reset");
        denied(ContextUniversalFrozenBranch.run(x,a,input,limits(16,16,2,WORK,500000,65536)),"transfer","late fact-bound failure exposes no earlier successful audit/replay/Packet");
        denied(ContextUniversalFrozenBranch.run(x,a,input,limits(16,3,3,WORK,500000,65536)),"events","recipe4 refusal is incomplete, not collision or theorem3 denial");
        denied(ContextUniversalFrozenBranch.run(x,a,input,limits(16,2,16,WORK,500000,65536)),"closure","uncovered typed theorem depth");
        denied(ContextUniversalFrozenBranch.run(x,a,input,limits(1,16,16,WORK,500000,65536)),"schemas","schema round cap no prefix");
        denied(ContextUniversalFrozenBranch.run(x,a,input,limits(16,16,16,WORK,1,65536)),"replay","native capture limit no prefix");
        denied(ContextUniversalFrozenBranch.run(x,a,input,limits(16,16,16,WORK,500000,1)),"coverage","later coverage receipt limit no prefix");
        denied(ContextUniversalFrozenBranch.run(x,a,input,limits(16,16,16,1,500000,65536)),"preflight","work limit no prefix");
        Snapshot anchor=bind(true,"!anchor(1);");Packet one=ContextPrivateWitnessPoolRunner.common(anchor);
        denied(ContextUniversalFrozenBranch.run(anchor,null,one,limits(16,16,16,WORK,500000,1)),"products","very late product obligation limit has no transfer prefix");
        Snapshot pair=bind(true,"!$w seed(w,1)&&flag(w,1);");denied(ContextUniversalFrozenBranch.run(pair,null,ContextPrivateWitnessPoolRunner.common(pair),limits(16,16,16,WORK,500000,1)),"units","fixed unit count limit after earlier audits");
        Snapshot bx=bind(true,"!anchor(1); !@w seed(w,1)->out(w,1);"),ba=bind(false,"!$w seed(w,1);");Packet common=ContextPrivateWitnessPoolRunner.common(bx);
        denied(ContextUniversalFrozenBranch.run(bx,ba,common,full()),"coverage","successful common-only closure cannot cover missing private row");
        PrivatePool actual=ContextWitnessResultTransport.privatePool(common,bx,ba,WORK);Result privateResult=ContextUniversalFrozenBranch.runPrivate(actual,full());require(privateResult.produced&&has(privateResult.products.transfer.output,"out"),"correct private input enables actual out");
        denied(ContextUniversalFrozenBranch.run(bx,ba,input,actual,full()),"preflight","private pool cannot change common barrier/X cut");
        Snapshot unsupported=bind(false,"!@w good(w,1); !~bad(1);");denied(ContextUniversalFrozenBranch.run(anchor,unsupported,one,full()),"schemas","later unsupported row cannot leave an earlier producer prefix");
    }
    static void commonRounds(boolean reverse)throws Exception {
        Snapshot x=bind(true,SEED+OUT),a=bind(false,P+REL),b=bind(false,FINISH);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Result aa=pass(x,a,input,null,full()),bb=pass(x,b,input,null,full()),xx=pass(x,null,input,null,full());Cut ac=aa.products.transfer.target,bc=bb.products.transfer.target,xc=xx.products.transfer.target;
        require(has(aa.products.transfer.output,"out")&&!has(bb.products.transfer.output,"finish")&&!has(input,"out"),"same-round B cannot see A product");
        List<Cut> cuts=reverse?Arrays.asList(bc,xc,ac):Arrays.asList(ac,xc,bc);Family family=ContextWitnessResultTransport.family(input,cuts,WORK);Map<Cut,Result> outputs=reverse?map(bb,xx,aa):map(aa,xx,bb);Barrier first=merge(family,outputs);
        require(first.output.changed&&has(first.output.packet,"out")&&!has(first.output.packet,"finish"),"owning round1 publishes only common-input-enabled branch products");
        List<Cut> other=new ArrayList<>(cuts);Collections.reverse(other);Barrier reordered=merge(ContextWitnessResultTransport.family(input,other,WORK),reverse?map(aa,xx,bb):map(bb,xx,aa));require(ContextWitnessResultTransport.sameContents(first.output.packet,reordered.output.packet,WORK),"supplied cut/map order preserves every Source/depth product");
        Map<Cut,Packet> alleged=new LinkedHashMap<>();for(Result branch:outputs.values())alleged.put(branch.products.transfer.target,branch.products.transfer.output);require(family.finish(alleged,WORK).status==FamilyStatus.INCOMPLETE,"complete positive pipeline is still not complete family STEP");
        Result na=pass(x,a,first.output.packet,null,full()),nb=pass(x,b,first.output.packet,null,full()),nx=pass(x,null,first.output.packet,null,full());Barrier second=merge(ContextWitnessResultTransport.family(first.output.packet,cuts,WORK),map(na,nb,nx));
        require(has(second.output.packet,"finish"),"next immutable common input enables B finish");
        for(Item i:second.output.packet.items)if(i.atom.predicate.equals("finish"))for(Support s:i.proofs)require(s.depth==4&&s.sources.equals(new HashSet<>(Arrays.asList(source(x,SEED),source(x,OUT),source(a,P),source(a,REL),source(b,FINISH)))),"full exact Source ancestry and minimum finish4");
        denied(ContextUniversalFrozenBranch.merge(family,map(aa,nb,xx),WORK),"mixed actual common inputs no barrier prefix");
        denied(ContextUniversalFrozenBranch.merge(family,map(aa,bb),WORK),"missing expected X output no prefix");
        denied(ContextUniversalFrozenBranch.merge(ContextWitnessResultTransport.family(input,Arrays.asList(ac,bc),WORK),map(aa,bb),WORK),"self-X cannot be omitted from supplied owning barrier");
        denied(ContextUniversalFrozenBranch.merge(family,outputs,1),"barrier work cap no prefix");
        Map<Cut,Result> swapped=new LinkedHashMap<>();swapped.put(ac,bb);swapped.put(bc,aa);swapped.put(xc,xx);denied(ContextUniversalFrozenBranch.merge(family,swapped,WORK),"wrong owning target no prefix");
        Snapshot bad=bind(false,"!~bad(1);");Result failed=ContextUniversalFrozenBranch.run(x,bad,input,full());require(!failed.produced,"actual failed later branch fixture");Map<Cut,Result> later=new LinkedHashMap<>();later.put(ac,aa);later.put(Cut.of(bad),failed);later.put(xc,xx);denied(ContextUniversalFrozenBranch.merge(ContextWitnessResultTransport.family(input,Arrays.asList(ac,Cut.of(bad),xc),WORK),later,WORK),"successful earlier branch cannot leak through failed later branch");
        Packet prior=second.output.packet;boolean stable=false;
        for(int number=3;number<=8;++number) {
            Result ar=pass(x,a,prior,null,full()),br=pass(x,b,prior,null,full()),xr=pass(x,null,prior,null,full());Barrier next=merge(ContextWitnessResultTransport.family(prior,cuts,WORK),reverse?map(br,xr,ar):map(ar,xr,br));
            System.out.println("OWNING_TYPED_ROUND number="+number+" common="+next.output.changed+" local="+next.output.localChanged);
            if(number==3)require(next.output.changed&&prior.items.size()==next.output.packet.items.size(),"Source alternatives grow after literal set stabilization");
            if(!next.output.changed&&!next.output.localChanged){stable=true;break;}prior=next.output.packet;
        }
        require(stable,"fixture stable at full product pools, no general termination or family verdict");
    }
    static void privateRounds(boolean reverse)throws Exception {
        String seed="!$w seed(w,1);",gate="!@v gate(v,1);",rel="!@u @v seed(u,1)&&gate(v,1)->rel(u,v);",out="!@w rel(w,7)->out(w,1);";
        Snapshot x=bind(true,"!anchor(1);"+out),a=bind(false,seed+gate+"!privateValue(7);"),member=bind(false,rel),b=bind(false,FINISH),target=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(member,a):Arrays.asList(a,member),WORK);
        Packet common=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,target,WORK);Result aa=pass(x,target,common,pool,full()),bb=pass(x,b,common,null,full()),xx=pass(x,null,common,null,full());
        for(String p:Arrays.asList("seed","rel","privateValue"))require(!has(aa.products.transfer.output,p),"unrelated X anchor cannot export private "+p);
        require(has(aa.products.transfer.local,"rel")&&has(aa.products.transfer.output,"out"),"single private pipeline retains local products and real X-dependent output");
        List<Cut> cuts=Arrays.asList(aa.products.transfer.target,bb.products.transfer.target,xx.products.transfer.target);Barrier first=merge(ContextWitnessResultTransport.family(common,cuts,WORK),reverse?map(bb,xx,aa):map(aa,xx,bb));PrivatePool next=first.output.privatePools.get(pool.cut);
        require(next!=null&&next.common==first.output.packet&&has(next.local,"seed")&&has(next.local,"rel"),"rebase follows complete supplied branch collection");
        require(pool.common==common&&!has(pool.local,"out")&&aa.products.transfer.privateAfter.common==common,"previous private states unchanged");
        Result ar=pass(x,target,first.output.packet,next,full()),br=pass(x,b,first.output.packet,null,full()),xr=pass(x,null,first.output.packet,null,full());Barrier second=merge(ContextWitnessResultTransport.family(first.output.packet,cuts,WORK),map(ar,br,xr));require(has(second.output.packet,"finish"),"independent B consumes only next shared out");
        PrivatePool independent=ContextWitnessResultTransport.privatePool(common,x,a,WORK);Result own=pass(x,a,common,independent,full());require(!has(own.products.transfer.output,"out"),"independent A cannot borrow member's rule");
        denied(ContextUniversalFrozenBranch.run(x,target,common,independent,full()),"preflight","independent private pool cannot relabel itself as trusted cut");
    }
    static void ordinaryOriginsAndAlternatives()throws Exception {
        Snapshot gx=bind(true,"!seed(7,1);"),ga=bind(false,"!@v p(v,1); !@v seed(v,1)&&p(v,1)->out(v,1);");
        Result ground=pass(gx,ga,ContextPrivateWitnessPoolRunner.common(gx),null,full());require(has(ground.products.transfer.output,"out")&&ground.products.transfer.proof.input.input.carried.isEmpty(),"ordinary pipeline invents no witness roots");
        Snapshot tx=bind(true,"!$u $v pair(u,v);"),ta=bind(false,"!@u @v pair(u,v)->rel(u,v); !@u @v rel(u,v)->out(u,v);");
        Result two=pass(tx,ta,ContextPrivateWitnessPoolRunner.common(tx),null,full());Item out=two.products.transfer.output.items.stream().filter(i->i.atom.predicate.equals("out")).findFirst().get();require(!out.atom.arguments.get(0).origin.equals(out.atom.arguments.get(1).origin),"unified pipeline preserves two original binder identities");
        String seed="!$w seed(w,1)&&flag(w,1);",simple="!@w seed(w,1)->q(w,1);",complex="!@w seed(w,1)&&flag(w,1)->q(w,1);";
        Snapshot x=bind(true,seed),a=bind(false,simple),b=bind(false,complex),target=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b),WORK);
        Result r=pass(x,target,ContextPrivateWitnessPoolRunner.common(x),null,limits(16,16,1,WORK,500000,65536));
        Set<Support> actual=new HashSet<>();for(Item i:r.products.transfer.output.items)if(i.atom.predicate.equals("q"))actual.addAll(i.proofs);
        require(actual.equals(new HashSet<>(Arrays.asList(new Support(new HashSet<>(Arrays.asList(source(x,seed),source(a,simple))),1),new Support(new HashSet<>(Arrays.asList(source(x,seed),source(b,complex))),1)))),"all coalesced Source alternatives at fact bound1, no premature symbolic2 refusal");
        require(r.products.products.stream().anyMatch(p->p.theorem!=null&&p.expected.depth==2&&p.localProof.depth==1),"actual native1 can cover a symbolic2 product through full pipeline");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-branch-").toString());phasesAndBounds();commonRounds(false);commonRounds(true);privateRounds(false);privateRounds(true);ordinaryOriginsAndAlternatives();
        denied(ContextUniversalFrozenBranch.run(null,null,null,(Limits)null),"preflight","no owning input");System.out.println("CONTEXT_UNIVERSAL_FROZEN_BRANCH_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
