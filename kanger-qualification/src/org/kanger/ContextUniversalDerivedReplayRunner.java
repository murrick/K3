/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitClosure.Proof;
import org.kanger.ContextWitnessResultTransport.*;

/** Derived native producers retain checked schema identities and depths, without primary promotion. */
public final class ContextUniversalDerivedReplayRunner {
    static final int WORK=64000000,EVENTS=500000;static int checks;
    static final String SEED=ContextUniversalUnitFamilyContractRunner.SEED,GATE=ContextUniversalUnitFamilyContractRunner.UNIVERSAL,
        MID=ContextUniversalUnitFamilyContractRunner.MID,DONE=ContextUniversalUnitFamilyContractRunner.DONE,
        OUT=ContextUniversalUnitFamilyContractRunner.OUT,WARM=ContextUniversalUnitFamilyContractRunner.WARM;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static Source source(Snapshot x,String text){return ContextPrivateWitnessPoolRunner.source(x,text);}
    static Set<Source> set(Source... sources){return new HashSet<>(Arrays.asList(sources));}
    static ContextUniversalUnitClosure.Result theory(Snapshot x,Snapshot a){ContextUniversalUnitClosure.Result result=ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);require(result.unitClosed,"schema precursor: "+result.reason);return result;}
    static boolean predicate(ContextUniversalDerivedReplay.ProducerEvent e,String name){return e.witness!=null?e.witness.conclusion.predicate.equals(name):e.ground.conclusion.predicate.equals(name);}
    static void denied(ContextUniversalDerivedReplay.Result result,String why){require(!result.bound&&result.bindings.isEmpty()&&result.events.isEmpty(),why+": "+result.reason);}
    static void one(boolean local,boolean grouped,boolean reverse)throws Exception {
        Snapshot x=bind(true,SEED+WARM+OUT+(local?GATE:"")),a=bind(false,(local?"":GATE)+MID+(grouped?"":DONE)),b=grouped?bind(false,DONE):null;
        Snapshot foreign=grouped?ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK):a;
        ContextUniversalUnitClosure.Result closed=theory(x,foreign);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalDerivedReplay.Result result=ContextUniversalDerivedReplay.replay(closed,input,16,WORK,EVENTS);
        System.out.println("DERIVED_REPLAY_CASE local="+local+" grouped="+grouped+" reverse="+reverse+" bound="+result.bound+" reason="+result.reason);
        require(result.bound,"derived native producer bridge: "+result.reason);
        require(result.basis.equals(input.identity)&&result.localBasis.equals(input.identity)&&!result.privateInput&&result.replay!=null,"exact common input and fresh replay");
        require(result.parent.equals(closed.parent)&&result.target.equals(closed.target),"exact original frame cuts retained");
        Set<Proof> actual=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(List<Proof> group:result.bindings.values())actual.addAll(group);
        require(actual.size()==closed.proofs.size()&&actual.containsAll(closed.proofs),"every checked schema proof bound, without caller-selected omissions");
        Set<Source> expected=set(source(local?x:a,GATE),source(a,MID));boolean residualEvent=false;
        for(ContextUniversalDerivedReplay.ProducerEvent event:result.events)if(predicate(event,"mid"))for(Proof proof:event.producers)if(proof.original==null) {
            require(proof.support.depth==1&&proof.support.sources.equals(expected),"actual derived residual producer keeps both whole Sources/depth1");residualEvent=true;
            if(event.witness!=null)require(event.origins.size()==1&&event.origins.values().iterator().next().source.equals(source(x,SEED)),"native event root maps to exact original witness declaration");
        }
        require(residualEvent,"actual witness application of derived residual observed");
        require(result.outstandingGaps.stream().anyMatch(g->g.startsWith("unbound-waiter-composition:")),"native schema gap not converted into coverage permission");
        require(input.items.size()==2&&input.items.stream().noneMatch(i->i.atom.predicate.equals("mid")||i.atom.predicate.equals("gate")),"no native producer receipt mutates common knowledge");
        denied(ContextUniversalDerivedReplay.replay(closed,input,0,WORK,EVENTS),"producer depth cannot be reset to primary0");
        denied(ContextUniversalDerivedReplay.replay(closed,input,16,1,EVENTS),"proof checking work limit returns no prefix");
        denied(ContextUniversalDerivedReplay.replay(closed,input,16,WORK,1),"native event limit returns no prefix");
        Packet wrong=ContextPrivateWitnessPoolRunner.common(bind(true,SEED+WARM+OUT+(local?GATE:"")));
        denied(ContextUniversalDerivedReplay.replay(closed,wrong,16,WORK,EVENTS),"equal values from another X view cannot reuse closure");
    }
    static void coalescence()throws Exception {
        String p="!@w p(w,1);",pq="!@w p(w,1)->q(w,1);",q="!@w q(w,1);",consumer="!@w seed(w,1)&&q(w,1)->out(w,1);";
        Snapshot x=bind(true,SEED+q+consumer),a=bind(false,p+pq);ContextUniversalUnitClosure.Result closed=theory(x,a);
        ContextUniversalDerivedReplay.Result result=ContextUniversalDerivedReplay.replay(closed,ContextPrivateWitnessPoolRunner.common(x),16,WORK,EVENTS);
        require(result.bound,"coalesced primary-clause and derived-clause proof labels: "+result.reason);boolean mixed=false;
        for(ContextUniversalDerivedReplay.ProducerEvent event:result.events)if(predicate(event,"q")) {
            Set<Support> supports=new HashSet<>();for(Proof proof:event.producers)supports.add(proof.support);
            require(supports.equals(new HashSet<>(Arrays.asList(new Support(set(source(x,q)),0),new Support(set(source(a,p),source(a,pq)),1)))),"same native rule keeps separate singleton depth0 and two-source depth1 alternatives");mixed=true;
        }
        require(mixed,"native coalesced unit instantiated on carried witness");
    }
    static void privateInput()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"+OUT),a=bind(false,SEED+GATE+MID+DONE);Packet common=ContextPrivateWitnessPoolRunner.common(x);
        PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);ContextUniversalUnitClosure.Result closed=theory(x,a);
        ContextUniversalDerivedReplay.Result result=ContextUniversalDerivedReplay.replayPrivate(closed,pool,16,WORK,EVENTS);
        require(result.bound&&result.privateInput&&result.basis.equals(common.identity)&&result.localBasis.equals(pool.local.identity),"exact owning private/common input split: "+result.reason);
        boolean seen=false;for(ContextUniversalDerivedReplay.ProducerEvent event:result.events)if(predicate(event,"mid"))for(Proof proof:event.producers)if(proof.original==null){
            seen=true;require(proof.support.sources.equals(set(source(a,GATE),source(a,MID))),"private producer cannot borrow unrelated X anchor");
            require(event.origins.values().iterator().next().source.equals(source(a,SEED)),"private original witness identity survives native projection");
        }
        require(seen&&common.items.size()==1,"private events never become a shared output packet");
        Snapshot other=bind(false,SEED+GATE+MID+DONE);PrivatePool wrong=ContextWitnessResultTransport.privatePool(common,x,other,WORK);
        denied(ContextUniversalDerivedReplay.replayPrivate(closed,wrong,16,WORK,EVENTS),"another private native view cannot borrow schema cut authority");
    }
    static void headFreeAndMultipleRoots()throws Exception {
        String p="!@u p(u,1);",q="!q(7,1);",rel="!@u @v p(u,1)&&q(v,1)->rel(u,v);",consumer="!@w seed(w,1)&&rel(w,7)->out(w,1);";
        Snapshot x=bind(true,SEED+consumer),a=bind(false,p+q+rel);ContextUniversalUnitClosure.Result closed=theory(x,a);
        ContextUniversalDerivedReplay.Result result=ContextUniversalDerivedReplay.replay(closed,ContextPrivateWitnessPoolRunner.common(x),16,WORK,EVENTS);
        require(result.bound,"proof-backed head-free native projection: "+result.reason);boolean seen=false;
        for(ContextUniversalDerivedReplay.ProducerEvent event:result.events)if(predicate(event,"out"))for(Proof proof:event.producers)if(proof.original==null&&proof.clause.literals.size()==2) {
            seen=true;require(proof.support.depth==3&&proof.support.sources.equals(set(source(a,p),source(a,q),source(a,rel),source(x,consumer))),"head-free intermediate retains full binary schema depth3, not fresh primary status");
        }
        require(seen,"actual consumer of proved universal head-free residual");
        Snapshot two=bind(true,"!$u $v left(u,1)&&right(v,1);"),rule=bind(false,"!@u @v left(u,1)&&right(v,1)->pair(u,v);");
        ContextUniversalUnitClosure.Result multi=theory(two,rule);
        denied(ContextUniversalDerivedReplay.replay(multi,ContextPrivateWitnessPoolRunner.common(two),16,WORK,EVENTS),"two independently carried roots cannot silently enter single-origin bridge scope");
    }
    static void strictPrimaryGuard()throws Exception {
        Snapshot x=bind(true,SEED),a=bind(false,GATE+MID);ContextUniversalUnitClosure.Result closed=theory(x,a);
        Proof derived=closed.proofs.stream().filter(p->p.original==null).findFirst().get();
        Replay replay=ContextWitnessResultTransport.restore(ContextPrivateWitnessPoolRunner.common(x),WORK);
        replay.mind.setQueryPass(org.kanger.enums.QueryPass.ACCEPT);
        try(ContextProductionCapture capture=ContextProductionCapture.begin(replay.mind,EVENTS)) {
            capture.observeWitnessProjections();Input input=capture.replayDerivedInput(replay.mind,derived.clause,Collections.singletonList(derived.support));replay.mind.link(null,false);
            require(input.source==null,"derived projection has no synthetic primary Source");
            boolean rejected=false;try{ContextWitnessPrimaryStepVerifier.scope(Collections.emptyList(),capture.snapshot(),()->{},true,8,Cut.of(a));}
            catch(IllegalStateException expected){rejected=expected.getMessage().contains("derived-input-outside-primary-step");}
            require(rejected,"unchanged primary verifier still refuses a derived producer");
        }
    }
    static void ordinaryAndRefusal()throws Exception {
        String p="!@w p(w,1);",pq="!@w p(w,1)->q(w,1);";
        Snapshot x=bind(true,"!seed(7,1); !@w seed(w,1)&&q(w,1)->out(w,1);"),a=bind(false,p+pq);ContextUniversalUnitClosure.Result closed=theory(x,a);
        ContextUniversalDerivedReplay.Result result=ContextUniversalDerivedReplay.replay(closed,ContextPrivateWitnessPoolRunner.common(x),16,WORK,EVENTS);
        require(result.bound,"ordinary event bindings: "+result.reason);
        require(result.events.stream().anyMatch(e->e.ground!=null&&predicate(e,"q")&&e.producers.stream().anyMatch(v->v.support.depth==1&&v.support.sources.equals(set(source(a,p),source(a,pq))))),"ordinary unit instantiation retains derived schema proof depth");
        ContextUniversalUnitClosure.Result incomplete=ContextUniversalUnitClosure.close(x,a,1,16,512,256,WORK);
        denied(ContextUniversalDerivedReplay.replay(incomplete,ContextPrivateWitnessPoolRunner.common(x),16,WORK,EVENTS),"incomplete schema prefix is not replay authority");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("derived-schema-replay-").toString());
        for(boolean l:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})one(l,g,r);
        coalescence();privateInput();headFreeAndMultipleRoots();strictPrimaryGuard();ordinaryAndRefusal();
        System.out.println("CONTEXT_UNIVERSAL_DERIVED_REPLAY_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
