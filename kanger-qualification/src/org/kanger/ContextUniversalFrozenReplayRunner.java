/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextWitnessResultTransport.*;

/** Typed native input/producer receipts preserve free variables and exact original frozen parameters. */
public final class ContextUniversalFrozenReplayRunner {
    static final int WORK=64000000,EVENTS=500000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static Source source(Snapshot x,String text){return ContextPrivateWitnessPoolRunner.source(x,text);}
    static Set<Source> set(Source... sources){return new HashSet<>(Arrays.asList(sources));}
    static ContextUniversalUnitClosure.Result theory(Snapshot x,Snapshot a){ContextUniversalUnitClosure.Result t=ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);require(t.unitClosed,"schema frame: "+t.reason);return t;}
    static ContextUniversalFrozenClosure.Result close(ContextUniversalUnitClosure.Result theory,Packet input){ContextUniversalFrozenClosure.Result r=ContextUniversalFrozenClosure.close(theory,input,16,16,512,256,WORK);require(r.closed,"typed frame: "+r.reason);return r;}
    static ContextUniversalFrozenReplay.Result replay(ContextUniversalFrozenClosure.Result theory){ContextUniversalFrozenReplay.Result r=ContextUniversalFrozenReplay.replay(theory,16,WORK,EVENTS);System.out.println("FROZEN_REPLAY bound="+r.bound+" reason="+r.reason+" events="+r.events.size());require(r.bound,"native typed replay: "+r.reason);require(!r.events.isEmpty(),"actual native producer events authenticated, not a vacuous input-only result");return r;}
    static void denied(ContextUniversalFrozenReplay.Result r,String why){require(!r.bound&&r.bindings.isEmpty()&&r.carried.isEmpty()&&r.imported.isEmpty()&&r.events.isEmpty()&&r.observation==null,why+": "+r.reason);}
    static void missingNative()throws Exception {
        String bundle="!$w seed(w,1)&&q(7,1);",consumer="!@w seed(w,1)&&rel(w,7)->out(w,1);",p="!@u p(u,1);",rel="!@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot x=bind(true,bundle+consumer),a=bind(false,p+rel);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalFrozenClosure.Result closed=close(theory(x,a),input);ContextUniversalFrozenReplay.Result r=replay(closed);
        require(r.basis.equals(input.identity)&&r.localBasis.equals(input.identity)&&r.replay!=null,"exact input basis and fresh native namespace");
        Set<Proof> projected=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(List<Proof> group:r.bindings.values())projected.addAll(group);
        for(Proof proof:closed.proofs)require(proof.input!=null?r.imported.contains(proof):projected.contains(proof),"every ledger proof is either actual imported fact or bound native input");
        boolean frozen=false,universal=false;
        for(Input nativeInput:r.observation.inputs) {
            require(nativeInput.source==null&&nativeInput.typedClause!=null,"typed projection stays derived, never a synthetic primary Source");
            if(!nativeInput.typedClause.origins.isEmpty()){frozen=true;require(nativeInput.clause==null,"old ordinary Clause cannot pretend to represent a frozen parameter");}
            if(!nativeInput.typedClause.variables.isEmpty())universal=true;
        }
        require(frozen&&universal&&r.carried.size()==1,"both rigid inputs and true universal declarations survived native parse");
        boolean out=false;
        for(Map.Entry<Long,List<Proof>> group:r.bindings.entrySet())for(Proof proof:group.getValue())if(proof.clause.literals.size()==1&&proof.clause.literals.get(0).predicate.equals("out")) {
            out=true;require(proof.support.depth==3&&proof.support.sources.equals(set(source(x,bundle),source(x,consumer),source(a,p),source(a,rel))),"frozen native unit retains exact four Sources/schema depth3");
        }
        require(out&&input.items.stream().noneMatch(i->i.atom.predicate.equals("out")),"proved out replayed without mutating common knowledge");
        require(r.observation.gaps.stream().anyMatch(g->g.startsWith("nonordinary-primary-fact:")),"frozen-unit capture gap retained and locally authenticated, not globally cleared");
        denied(ContextUniversalFrozenReplay.replay(closed,2,WORK,EVENTS),"no producer depth reset");
        denied(ContextUniversalFrozenReplay.replay(closed,16,1,EVENTS),"DAG/transport work limit no prefix");
        denied(ContextUniversalFrozenReplay.replay(closed,16,WORK,1),"native capture budget no prefix");
        ContextUniversalFrozenClosure.Result incomplete=ContextUniversalFrozenClosure.close(theory(x,a),input,1,16,512,256,WORK);
        denied(ContextUniversalFrozenReplay.replay(incomplete,16,WORK,EVENTS),"incomplete typed closure grants no replay prefix");
    }
    static void privateAndMultiple()throws Exception {
        String seed="!$w seed(w,1);",gate="!@v gate(v,1);",rel="!@u @v seed(u,1)&&gate(v,1)->rel(u,v);",out="!@u rel(u,7)->out(u,1);";
        Snapshot x=bind(true,"!anchor(1);"+out),a=bind(false,seed+gate),b=bind(false,rel);Packet common=ContextPrivateWitnessPoolRunner.common(x);
        for(boolean reverse:new boolean[]{false,true}) {
            Snapshot target=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);
            PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,target,WORK);
            ContextUniversalFrozenClosure.Result closed=ContextUniversalFrozenClosure.closePrivate(theory(x,target),pool,16,16,512,256,WORK);require(closed.closed,"private typed precursor");
            ContextUniversalFrozenReplay.Result r=replay(closed);
            require(r.basis.equals(common.identity)&&r.localBasis.equals(pool.local.identity)&&r.theory.privateInput,"private/local input identity split retained");
            require(r.carried.values().stream().anyMatch(o->o.source.equals(source(a,seed))),"physical private witness owner retained, not trusted-group identity");
            for(List<Proof> group:r.bindings.values())for(Proof proof:group)if(proof.clause.literals.size()==1&&proof.clause.literals.get(0).predicate.equals("rel")&&!proof.clause.origins.isEmpty())
                require(!proof.support.sources.contains(source(x,"!anchor(1);")),"unrelated X anchor cannot alter producer Source proof");
            require(common.items.size()==1&&pool.local.items.stream().noneMatch(i->i.atom.predicate.equals("out")),"native private replay creates no shared/local output Packet");
        }
        Snapshot two=bind(true,"!$u $v pair(u,v);"),rule=bind(false,"!@u @v pair(u,v)->rel(u,v); !@u @v rel(u,v)->out(u,v);");
        Packet input=ContextPrivateWitnessPoolRunner.common(two);ContextUniversalFrozenReplay.Result r=replay(close(theory(two,rule),input));
        require(r.carried.size()==2,"two distinct original root identities remain separate");
        boolean pair=false;for(Input nativeInput:r.observation.inputs)if(nativeInput.typedClause.origins.size()==2)pair=true;
        require(pair,"native typed declaration can retain two frozen parameters without claiming family language coverage");
    }
    static void coalescenceAndGuard()throws Exception {
        String seed="!$w seed(w,1);",one="!@w seed(w,1)->q(w,1);",two="!@w seed(w,1)->mid(w,1); !@w mid(w,1)->q(w,1);";
        Snapshot x=bind(true,seed),a=bind(false,one+two);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalFrozenClosure.Result closed=close(theory(x,a),input);ContextUniversalFrozenReplay.Result r=replay(closed);
        boolean alternatives=false;for(List<Proof> group:r.bindings.values())if(group.size()>1&&group.get(0).clause.literals.size()==1&&group.get(0).clause.literals.get(0).predicate.equals("q")) {
            Set<Integer> depths=new HashSet<>();for(Proof proof:group)depths.add(proof.support.depth);require(depths.contains(1)&&depths.contains(2),"same native q keeps distinct proof depths/whole Source sets");alternatives=true;
        }
        require(alternatives,"actual frozen unit coalescence observed");
        Proof frozen=closed.proofs.stream().filter(p->p.clause.literals.size()==1&&!p.clause.origins.isEmpty()&&p.input==null).findFirst().get();
        Replay restored=ContextWitnessResultTransport.restore(input,WORK);Map<org.kanger.ContextWitnessUnaryChainVerifier.Root,Origin> map=new LinkedHashMap<>();
        for(Map.Entry<Origin,Long> e:restored.witnesses.entrySet()){org.kanger.units.Term term=(org.kanger.units.Term)restored.mind.getTerms().get(e.getValue());map.put(new org.kanger.ContextWitnessUnaryChainVerifier.Root(term.getId(),term.getRuleId()),e.getKey());}
        try(ContextProductionCapture capture=ContextProductionCapture.begin(restored.mind,EVENTS)) {
            Input actual=capture.replayTypedInput(restored.mind,frozen.clause,Collections.singletonList(frozen.support),map,()->{});
            require(actual.source==null,"raw typed projection is derived");
            boolean refused=false;try{ContextWitnessPrimaryStepVerifier.scope(Collections.emptyList(),capture.snapshot(),()->{},true,8,Cut.of(a));}
            catch(IllegalStateException expected){refused=expected.toString().contains("derived-input-outside-primary-step");}
            require(refused,"unchanged primary verifier rejects typed derived evidence");
        }
        String predicate=frozen.clause.literals.get(0).predicate;
        require(Boolean.TRUE.equals(restored.mind.query("?$w seed(w,1)&&"+predicate+"(w,1);",null,false)),"native query sees the same original witness after frozen projection");
        require(restored.mind.query("?"+predicate+"(John,1);",null,false)==null,"frozen input does not become universal over a fresh named value");
        require(restored.mind.query("?"+predicate+"(7331,1);",null,false)==null,"frozen input does not become universal over a fresh numeric value");
        Replay other=ContextWitnessResultTransport.restore(input,WORK);
        try(ContextProductionCapture capture=ContextProductionCapture.begin(other.mind,EVENTS)) {
            boolean refused=false;try{capture.replayTypedInput(other.mind,frozen.clause,Collections.singletonList(frozen.support),Collections.emptyMap(),()->{});}catch(IllegalStateException expected){refused=true;}
            require(refused&&capture.snapshot().inputs.isEmpty(),"missing frozen origin map cannot create a native input receipt");
        }
    }
    static void repeatedInstances()throws Exception {
        String bundle="!$w pair(w,w)&&p(w);",formal="!@u @v p(u)&&p(v)->rel(u,v);",
            normal="!@u @v pair(u,v)&&p(u)&&p(v)->rel(u,v);",extra="!@u @v pair(u,v)&&p(u)&&extra(v)->rel(u,v);";
        Snapshot x=bind(true,bundle),a=bind(false,formal+normal+extra);
        ContextUniversalFrozenClosure.Result closed=close(theory(x,a),ContextPrivateWitnessPoolRunner.common(x));
        Proof producer=closed.proofs.stream().filter(p->p.schema!=null&&p.schema.original!=null&&p.schema.original.source.equals(source(a,formal))).findFirst().get();
        Proof concrete=closed.proofs.stream().filter(p->p.clause.variables.isEmpty()&&p.clause.literals.size()==2&&p.clause.literals.stream().anyMatch(l->l.predicate.equals("p"))&&p.clause.literals.stream().anyMatch(l->l.predicate.equals("rel"))).findFirst().get();
        require(ContextUniversalInputResiduals.instance(producer.clause,concrete.clause,()->{}),"coincident premise instances may normalize to one literal with consistent repeated bindings");
        Proof extraPremise=closed.proofs.stream().filter(p->p.clause.variables.isEmpty()&&p.clause.literals.size()==3&&p.clause.literals.stream().anyMatch(l->l.predicate.equals("extra"))).findFirst().get();
        require(!ContextUniversalInputResiduals.instance(producer.clause,extraPremise.clause,()->{}),"surjective instance match cannot ignore an unrelated native premise");
        ContextUniversalFrozenReplay.Result replay=replay(closed);
        require(replay.observation.inputs.stream().allMatch(i->i.typedClause!=null),"normalized repeated-variable fixture retains every exact native input");
        Snapshot gx=bind(true,"!seed(7,1);"),ga=bind(false,"!@v p(v,1); !@v seed(v,1)&&p(v,1)->out(v,1);");
        ContextUniversalFrozenReplay.Result ground=replay(close(theory(gx,ga),ContextPrivateWitnessPoolRunner.common(gx)));
        require(ground.carried.isEmpty()&&ground.events.stream().allMatch(e->e.witness==null),"ordinary-only replay needs no invented witness binding");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-replay-").toString());
        missingNative();privateAndMultiple();coalescenceAndGuard();repeatedInstances();denied(ContextUniversalFrozenReplay.replay(null,16,WORK,EVENTS),"missing sealed DAG");
        System.out.println("CONTEXT_UNIVERSAL_FROZEN_REPLAY_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
