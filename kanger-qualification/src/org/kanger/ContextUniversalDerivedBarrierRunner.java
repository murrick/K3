/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Observed proof transport and immutable synchronous barriers never imply family admission. */
public final class ContextUniversalDerivedBarrierRunner {
    static final int WORK=64000000,EVENTS=500000;static int checks;
    static final String SEED=ContextUniversalDerivedReplayRunner.SEED,GATE=ContextUniversalDerivedReplayRunner.GATE,
        MID=ContextUniversalDerivedReplayRunner.MID,DONE=ContextUniversalDerivedReplayRunner.DONE,OUT=ContextUniversalDerivedReplayRunner.OUT,
        FINISH="!@w out(w,1)->finish(w,1);";
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static Source source(Snapshot x,String text){return ContextPrivateWitnessPoolRunner.source(x,text);}
    static Set<Source> set(Source... sources){return new HashSet<>(Arrays.asList(sources));}
    static boolean has(Packet packet,String predicate){return packet.items.stream().anyMatch(i->i.atom.predicate.equals(predicate));}
    static ContextUniversalUnitClosure.Result theory(Snapshot x,Snapshot a){ContextUniversalUnitClosure.Result result=ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);require(result.unitClosed,"closed schema frame: "+result.reason);return result;}
    static ContextUniversalDerivedRoutes.Result proof(ContextUniversalUnitClosure.Result theory,Packet input){
        ContextUniversalDerivedReplay.Result replay=ContextUniversalDerivedReplay.replay(theory,input,16,WORK,EVENTS);
        require(replay.bound,"native producer bindings: "+replay.reason);
        ContextUniversalDerivedRoutes.Result proof=ContextUniversalDerivedRoutes.reconstruct(replay,16,16384,WORK);
        require(proof.reconstructed,"native proof routes: "+proof.reason);return proof;
    }
    static ContextUniversalDerivedRoutes.Result privateProof(ContextUniversalUnitClosure.Result theory,PrivatePool pool){
        ContextUniversalDerivedReplay.Result replay=ContextUniversalDerivedReplay.replayPrivate(theory,pool,16,WORK,EVENTS);
        require(replay.bound,"private producer bindings: "+replay.reason);
        ContextUniversalDerivedRoutes.Result proof=ContextUniversalDerivedRoutes.reconstruct(replay,16,16384,WORK);
        require(proof.reconstructed,"private native routes: "+proof.reason);return proof;
    }
    static DerivedTransfer transfer(ContextUniversalDerivedRoutes.Result proof,Packet input){
        DerivedTransfer result=ContextWitnessResultTransport.transferDerived(proof,input,16,WORK);
        require(result.transferred,"proof transfer: "+result.reason);return result;
    }
    static void denied(DerivedTransfer result,String why){require(!result.transferred&&result.output==null&&result.local==null&&result.privateAfter==null,why+": "+result.reason);}
    static void unmarked(Packet packet){require(packet.basis==null&&!packet.observedStep&&packet.emptyCut==null&&packet.coveredCut==null&&packet.roundCut==null&&packet.privateTarget==null,"observed proof packet has no coverage/round/admission authority");}
    static DerivedBarrier merge(Family family,Map<Cut,DerivedTransfer> outputs){DerivedBarrier result=ContextWitnessResultTransport.mergeDerived(family,outputs,WORK);require(result.merged,"observed barrier: "+result.reason);unmarked(result.packet);return result;}
    static void badBarrier(DerivedBarrier result,String why){require(!result.merged&&result.packet==null&&result.branches.isEmpty()&&result.privatePools.isEmpty(),why+": "+result.reason);}
    static Map<Cut,DerivedTransfer> outputs(DerivedTransfer a,DerivedTransfer b,boolean reverse){Map<Cut,DerivedTransfer> map=new LinkedHashMap<>();if(reverse){map.put(b.target,b);map.put(a.target,a);}else{map.put(a.target,a);map.put(b.target,b);}return map;}
    static void common(boolean reverse)throws Exception {
        Snapshot x=bind(true,SEED+OUT),a=bind(false,GATE+MID+DONE),b=bind(false,FINISH);
        Packet input=ContextPrivateWitnessPoolRunner.common(x);Origin origin=input.items.get(0).atom.arguments.get(0).origin;
        ContextUniversalUnitClosure.Result ta=theory(x,a),tb=theory(x,b),tx=theory(x,null);
        DerivedTransfer at=transfer(proof(ta,input),input),bt=transfer(proof(tb,input),input);
        require(has(at.output,"out")&&!has(bt.output,"finish")&&!has(input,"out"),"B does not see A's same-round product; input unchanged");
        unmarked(at.output);unmarked(bt.output);
        DerivedTransfer xt=transfer(proof(tx,input),input);
        Family family=ContextWitnessResultTransport.family(input,reverse?Arrays.asList(Cut.of(b),Cut.of(x),Cut.of(a)):Arrays.asList(Cut.of(a),Cut.of(x),Cut.of(b)),WORK);
        Map<Cut,DerivedTransfer> firstOutputs=outputs(at,bt,reverse);firstOutputs.put(xt.target,xt);
        DerivedBarrier first=merge(family,firstOutputs);
        require(first.changed&&!has(first.packet,"finish")&&has(first.packet,"out"),"round1 merges out but cannot include B's later finish");
        List<Cut> oppositeCuts=new ArrayList<>(family.expected);Collections.reverse(oppositeCuts);
        Family oppositeFamily=ContextWitnessResultTransport.family(input,oppositeCuts,WORK);
        Map<Cut,DerivedTransfer> reversedOutputs=outputs(at,bt,!reverse);reversedOutputs.put(xt.target,xt);
        DerivedBarrier reordered=merge(oppositeFamily,reversedOutputs);require(ContextWitnessResultTransport.sameContents(first.packet,reordered.packet,WORK),"expected-cut and map traversal order preserve every fact/source/depth");
        Map<Cut,Packet> alleged=new LinkedHashMap<>();alleged.put(at.target,at.output);alleged.put(bt.target,bt.output);alleged.put(xt.target,xt.output);
        require(family.finish(alleged,WORK).status==FamilyStatus.INCOMPLETE,"sound observed products cannot masquerade as an existing family STEP");
        DerivedTransfer nextA=transfer(proof(ta,first.packet),first.packet),nextB=transfer(proof(tb,first.packet),first.packet);
        DerivedTransfer nextX=transfer(proof(tx,first.packet),first.packet);
        Map<Cut,DerivedTransfer> secondOutputs=outputs(nextA,nextB,!reverse);secondOutputs.put(nextX.target,nextX);
        DerivedBarrier second=merge(ContextWitnessResultTransport.family(first.packet,family.expected,WORK),secondOutputs);
        boolean finish=false;for(Item item:second.packet.items)if(item.atom.predicate.equals("finish")) {
            finish=true;require(item.atom.arguments.get(0).origin.equals(origin),"fresh native namespaces retain exact original semantic witness");
            for(Support support:item.proofs)require(support.depth==5&&support.sources.equals(set(source(x,SEED),source(x,OUT),source(a,GATE),source(a,MID),source(a,DONE),source(b,FINISH))),"later B conclusion retains all A/X/B source ancestry and depth5");
        }
        require(finish&&second.changed,"next synchronous input enables finish");
        require(at.proof.outstandingGaps.equals(at.proof.input.outstandingGaps)&&!at.proof.outstandingGaps.isEmpty(),"schema gaps survive typed transfer/barrier");
        Map<Cut,DerivedTransfer> mixed=outputs(at,nextB,false);mixed.put(xt.target,xt);
        badBarrier(ContextWitnessResultTransport.mergeDerived(family,mixed,WORK),"mixed input barriers yield no prefix");
        badBarrier(ContextWitnessResultTransport.mergeDerived(family,outputs(at,bt,false),WORK),"missing expected X branch yields no prefix");
        Map<Cut,DerivedTransfer> wrong=new LinkedHashMap<>();wrong.put(at.target,bt);wrong.put(bt.target,at);wrong.put(xt.target,xt);
        badBarrier(ContextWitnessResultTransport.mergeDerived(family,wrong,WORK),"swapped owning cuts yield no prefix");
        badBarrier(ContextWitnessResultTransport.mergeDerived(family,firstOutputs,1),"merge work limit yields no prefix");
        Packet equivalent=merge(ContextWitnessResultTransport.family(input,Collections.singletonList(bt.target),WORK),Collections.singletonMap(bt.target,bt)).packet;
        require(ContextWitnessResultTransport.sameContents(input,equivalent,WORK)&&input.identity!=equivalent.identity,"equal proof contents at another actual input identity");
        denied(ContextWitnessResultTransport.transferDerived(at.proof,equivalent,16,WORK),"same facts cannot replace exact input identity");
        denied(ContextWitnessResultTransport.transferDerived(at.proof,input,1,WORK),"conclusion depth bound retained at transport");
        denied(ContextWitnessResultTransport.transferDerived(at.proof,input,16,1),"transfer work bound no output prefix");
        denied(ContextWitnessResultTransport.transferDerived(nextB.proof,first.packet,3,WORK),"carried input depth bound cannot be bypassed by later transport");
        Packet prior=second.packet;boolean stable=false;
        for(int round=3;round<=8;++round) {
            Map<Cut,DerivedTransfer> following=new LinkedHashMap<>();
            following.put(Cut.of(a),transfer(proof(ta,prior),prior));
            following.put(Cut.of(b),transfer(proof(tb,prior),prior));
            following.put(Cut.of(x),transfer(proof(tx,prior),prior));
            DerivedBarrier barrier=merge(ContextWitnessResultTransport.family(prior,family.expected,WORK),following);
            System.out.println("DERIVED_BARRIER round="+round+" changed="+barrier.changed+" localChanged="+barrier.localChanged);
            if(round==3) {
                Set<String> oldPredicates=new HashSet<>(),newPredicates=new HashSet<>();
                for(Item item:prior.items)oldPredicates.add(item.atom.predicate);
                for(Item item:barrier.packet.items)newPredicates.add(item.atom.predicate);
                require(barrier.changed&&prior.items.size()==barrier.packet.items.size()&&oldPredicates.equals(newPredicates),"proof alternatives grow after the literal set has stabilized");
            }
            if(!barrier.changed&&!barrier.localChanged){stable=true;break;}
            prior=barrier.packet;
        }
        require(stable,"observed proof alternatives reach a stable barrier without issuing a fixed-point/admission verdict");
    }
    static void privateInput(boolean grouped,boolean reverse)throws Exception {
        String raw="!privateValue(7);";Snapshot x=bind(true,"!anchor(1);"+OUT),a=bind(false,SEED+GATE+MID+raw+(grouped?"":DONE)),member=grouped?bind(false,DONE):null,b=bind(false,FINISH);
        Snapshot target=grouped?ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(member,a):Arrays.asList(a,member),WORK):a;
        Packet input=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(input,x,target,WORK);
        ContextUniversalDerivedRoutes.Result p=privateProof(theory(x,target),pool);
        DerivedTransfer at=ContextWitnessResultTransport.transferDerivedPrivate(p,pool,16,WORK);require(at.transferred,"private transport: "+at.reason);
        require(at.privateBefore==pool&&at.privateAfter.common==input&&at.privateAfter.cut.equals(pool.cut),"private owner and exact common input retained before barrier");
        require(at.localChanged&&at.commonChanged&&has(at.local,"mid")&&has(at.output,"out"),"local products and real X-dependent products both retained");
        for(String name:Arrays.asList("seed","flag","gate","mid","done","privateValue"))require(!has(at.output,name),"raw/private-only "+name+" cannot cross using unrelated X anchor");
        require(has(at.local,"privateValue")&&has(at.local,"seed")&&!has(input,"out"),"private primary ground/witness facts stay local, common input immutable");
        for(Item item:at.local.items)if(item.atom.predicate.equals("mid"))for(Support support:item.proofs)require(!support.sources.contains(source(x,"!anchor(1);")),"unrelated X anchor never enters private proof");
        for(Item item:at.output.items)if(item.atom.predicate.equals("out")) {
            require(item.atom.arguments.get(0).origin.source.equals(source(a,SEED)),"trusted/private product preserves physical declaration owner");
            for(Support support:item.proofs)require(support.depth==4&&support.sources.equals(set(source(a,SEED),source(a,GATE),source(a,MID),source(grouped?member:a,DONE),source(x,OUT))),"only actual whole private Sources and X consumer qualify out");
        }
        for(Item item:at.local.items)if(item.atom.predicate.equals("privateValue"))for(Support support:item.proofs)
            require(support.depth==0&&support.sources.equals(set(source(a,raw))),"private ground theorem stays original depth0, never acquires X by co-location");
        require(p.outstandingGaps==at.proof.outstandingGaps,"transport retains outstanding coverage obligations");unmarked(at.output);
        DerivedTransfer bt=transfer(proof(theory(x,b),input),input);Family family=ContextWitnessResultTransport.family(input,Arrays.asList(pool.cut,Cut.of(b)),WORK);
        DerivedBarrier barrier=merge(family,outputs(at,bt,reverse));PrivatePool rebased=barrier.privatePools.get(pool.cut);
        require(rebased!=null&&rebased.common==barrier.packet&&has(rebased.local,"seed")&&has(rebased.local,"out"),"private local continuation rebased only after whole immutable barrier");
        require(at.privateAfter.common==input&&pool.common==input&&!has(pool.local,"out"),"rebasing does not mutate either previous pool");
        for(String name:Arrays.asList("seed","flag","gate","mid","done","privateValue"))require(!has(barrier.packet,name),"barrier cannot communalize private "+name);
        DerivedTransfer nextB=transfer(proof(theory(x,b),barrier.packet),barrier.packet);
        require(has(nextB.output,"finish"),"B can consume the actual X-dependent out product with A witness ancestry");
        ContextUniversalDerivedRoutes.Result nextPrivate=privateProof(theory(x,target),rebased);
        require(ContextWitnessResultTransport.transferDerivedPrivate(nextPrivate,rebased,16,WORK).transferred,"private continuation can replay at next common barrier");
        denied(ContextWitnessResultTransport.transferDerived(p,input,16,WORK),"private receipt cannot use common-only transfer");
        denied(ContextWitnessResultTransport.transferDerivedPrivate(p,rebased,16,WORK),"private receipt cannot reuse another local/common barrier");
    }
    static void alternatives()throws Exception {
        String p="!@w p(w,1);",pq="!@w p(w,1)->q(w,1);",q="!@w q(w,1);",out="!@w seed(w,1)&&q(w,1)->out(w,1);";
        Snapshot x=bind(true,SEED+q+out),a=bind(false,p+pq);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        DerivedTransfer result=transfer(proof(theory(x,a),input),input);Set<Support> supports=new HashSet<>();
        for(Item item:result.output.items)if(item.atom.predicate.equals("q"))supports.addAll(item.proofs);
        require(supports.equals(new HashSet<>(Arrays.asList(new Support(set(source(x,SEED),source(x,q)),1),new Support(set(source(x,SEED),source(a,p),source(a,pq)),2)))),"coalesced native producer alternatives survive Packet transport without flattening/reset");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("derived-barriers-").toString());
        for(boolean reverse:new boolean[]{false,true})common(reverse);
        for(boolean grouped:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})privateInput(grouped,reverse);
        alternatives();denied(ContextWitnessResultTransport.transferDerived(null,null,16,WORK),"missing sealed proof receipt");
        System.out.println("CONTEXT_UNIVERSAL_DERIVED_BARRIER_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
