/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextUniversalInputResiduals.*;
import org.kanger.ContextUniversalInputResiduals.Result;
import org.kanger.ContextUniversalInputResiduals.Residual;

/** Exhaustive input-pivot proofs preserve rigid origins and genuinely universal residual variables. */
public final class ContextUniversalInputResidualsRunner {
    static final int WORK=64000000;static int checks;
    static final String SEED=ContextUniversalDerivedReplayRunner.SEED;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static Source source(Snapshot x,String text){return ContextPrivateWitnessPoolRunner.source(x,text);}
    static Set<Source> set(Source... sources){return new HashSet<>(Arrays.asList(sources));}
    static ContextUniversalUnitClosure.Result theory(Snapshot x,Snapshot a){ContextUniversalUnitClosure.Result t=ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);require(t.unitClosed,"symbolic frame: "+t.reason);return t;}
    static Result scan(ContextUniversalUnitClosure.Result theory,Packet input){Result r=ContextUniversalInputResiduals.scan(theory,input,16,16384,WORK);require(r.enumerated,"input residual scan: "+r.reason);return r;}
    static void denied(Result r,String why){require(!r.enumerated&&r.steps.isEmpty()&&r.attempts==0,why+": "+r.reason);}
    static Literal head(Residual r){return r.literals.stream().filter(l->l.positive).findFirst().get();}
    static Step donated(Result r,String name){return r.steps.stream().filter(s->s.donor.atom.predicate.equals(name)).findFirst().get();}
    static void missingNative()throws Exception {
        String bundle="!$w seed(w,1)&&q(7,1);",p="!@u p(u,1);",rel="!@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot x=bind(true,bundle+"!@w seed(w,1)&&rel(w,7)->out(w,1);"),a=bind(false,p+rel);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalUnitClosure.Result t=theory(x,a);Result result=scan(t,input);
        require(result.basis.equals(input.identity)&&result.localBasis.equals(input.identity)&&!result.privateInput,"exact common input basis");
        Step universal=result.steps.stream().filter(s->s.residual.literals.size()==1&&head(s.residual).predicate.equals("rel")).findFirst().get();
        require(universal.schema.original==null&&universal.schema.support.depth==1&&universal.donor.atom.predicate.equals("q"),"input q consumes proved head-free schema, not a synthetic primary");
        require(universal.support.depth==2&&universal.support.sources.equals(set(source(x,bundle),source(a,p),source(a,rel))),"all three whole Sources and max(schema1,donor0)+1");
        Literal h=head(universal.residual);require(h.arguments.get(0).variable>=0&&h.arguments.get(1).variable<0&&h.arguments.get(1).origin==null,"forall u rel(u,7), no chosen sample for free u");
        require(h.arguments.get(1).value.semanticallyEquals(universal.donor.atom.arguments.get(0).value)&&!h.arguments.get(1).value.semanticallyEquals(universal.donor.atom.arguments.get(1).value),"q first argument7 is retained, not fixed argument1");
        require(universal.residual.variables.size()==1&&universal.residual.origins.isEmpty(),"ordinary ground donor produces a true universal residual");
        ContextUniversalDerivedReplay.Result replay=ContextUniversalDerivedReplay.replay(t,input,16,WORK,500000);
        ContextUniversalDerivedRoutes.Result nativeProof=ContextUniversalDerivedRoutes.reconstruct(replay,16,16384,WORK);
        require(replay.bound&&nativeProof.reconstructed&&nativeProof.conclusions.stream().noneMatch(r->r.physical.predicate.equals("rel")||r.physical.predicate.equals("out")),"current native observed path still misses the required residual; no coverage waiver");
        require(input.items.stream().noneMatch(i->i.atom.predicate.equals("rel")),"symbolic proof is not silently inserted into ordinary Packet");
        denied(ContextUniversalInputResiduals.scan(t,input,1,16384,WORK),"later required depth2 step clears earlier depth1 prefix");
        ContextUniversalUnitClosure.Result incomplete=ContextUniversalUnitClosure.close(x,a,1,16,512,256,WORK);
        denied(ContextUniversalInputResiduals.scan(incomplete,input,16,16384,WORK),"unfinished symbolic schema ledger grants no proof authority");
        Packet wrong=ContextPrivateWitnessPoolRunner.common(bind(true,bundle+"!@w seed(w,1)&&rel(w,7)->out(w,1);"));
        denied(ContextUniversalInputResiduals.scan(t,wrong,16,16384,WORK),"equal values in another X cut cannot reuse schema authority");
    }
    static void frozenAndAlpha()throws Exception {
        String rule="!@u @v seed(u,1)&&gate(v,1)&&guard(u,1)->rel(u,v);",other="!@y @z guard(y,1)&&gate(z,1)&&seed(y,1)->rel(y,z);";
        Snapshot x=bind(true,SEED),a=bind(false,rule),b=bind(false,other);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Step one=donated(scan(theory(x,a),input),"seed"),two=donated(scan(theory(x,b),input),"seed");
        require(one.residual.equivalent(two.residual,()->{}),"alpha renaming and literal order preserve the same frozen-origin conditional");
        Literal h=head(one.residual);Origin origin=input.items.stream().filter(i->i.atom.predicate.equals("seed")).findFirst().get().atom.arguments.get(0).origin;
        require(one.residual.variables.size()==1&&one.residual.origins.equals(Collections.singleton(origin)),"one frozen root and one genuinely universal variable");
        require(h.arguments.get(0).variable<0&&origin.equals(h.arguments.get(0).origin)&&h.arguments.get(1).variable>=0,"head cannot generalize the actual input witness");
        Literal gate=one.residual.literals.stream().filter(l->l.predicate.equals("gate")).findFirst().get();
        require(gate.arguments.get(0).variable==h.arguments.get(1).variable,"unbound variable sharing survives across body/head");
        require(one.support.depth==1&&one.support.sources.equals(set(source(x,SEED),source(a,rule))),"frozen residual retains exact original donor and rule Source");
        Snapshot gx=bind(true,"!seed(7,1);"),ga=bind(false,rule);Step ground=donated(scan(theory(gx,ga),ContextPrivateWitnessPoolRunner.common(gx)),"seed");
        require(!one.residual.equivalent(ground.residual,()->{})&&ground.residual.origins.isEmpty(),"an ordinary constant never aliases an existential origin");
        Snapshot dx=bind(true,SEED);Step distinct=donated(scan(theory(dx,a),ContextPrivateWitnessPoolRunner.common(dx)),"seed");
        require(!one.residual.equivalent(distinct.residual,()->{}),"same local binder number in another whole Source remains a different rigid origin");
        String universal="!@u seed(u,1);",withKnown="!@u @v seed(u,1)&&known(1)&&gate(v,1)&&guard(u,1)->rel(u,v);";
        Snapshot ux=bind(true,"!known(1);"),ua=bind(false,universal+withKnown);
        Result unrestricted=scan(theory(ux,ua),ContextPrivateWitnessPoolRunner.common(ux));
        Step free=unrestricted.steps.stream().filter(s->s.schema.original==null&&s.residual.variables.size()==2).findFirst().get();
        require(free.residual.origins.isEmpty()&&!one.residual.equivalent(free.residual,()->{}),"a universal variable cannot be alpha-renamed into a frozen witness");
    }
    static void diagonalsAndPivots()throws Exception {
        String diagonal="!@x pair(x,x)->same(x);",pair="!@x @y pair(x,y)->rel(x,y);";
        for(boolean repeated:new boolean[]{false,true}) {
            String bundle=repeated?"!$u pair(u,u);":"!$u $v pair(u,v);";
            Snapshot x=bind(true,bundle),a=bind(false,diagonal+pair);Packet input=ContextPrivateWitnessPoolRunner.common(x);Result r=scan(theory(x,a),input);
            require(r.steps.stream().anyMatch(s->head(s.residual).predicate.equals("same"))==repeated,"diagonal constraint matches exactly repeated rigid origin");
            Step general=r.steps.stream().filter(s->head(s.residual).predicate.equals("rel")).findFirst().get();
            require(general.residual.variables.isEmpty()&&general.residual.origins.size()==(repeated?1:2),"distinct frozen binders are retained independently");
            if(!repeated)require(!ContextUniversalDerivedReplay.replay(theory(x,a),input,16,WORK,500000).bound,"typed two-origin residual does not widen native single-origin authority");
        }
        String doubleBody="!@u @v seed(u,1)&&seed(v,1)->rel(u,v);";Snapshot x=bind(true,SEED),a=bind(false,doubleBody);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Result two=scan(theory(x,a),input);require(two.steps.size()==2&&two.steps.get(0).pivot!=two.steps.get(1).pivot,"each matching body pivot creates its own principal residual");
        require(!two.steps.get(0).residual.equivalent(two.steps.get(1).residual,()->{}),"rel(frozen,v) and rel(u,frozen) cannot be merged by alpha renaming");
        denied(ContextUniversalInputResiduals.scan(theory(x,a),input,16,1,WORK),"step count limit returns no selected pivot prefix");
        denied(ContextUniversalInputResiduals.scan(theory(x,a),input,0,16384,WORK),"depth0 cannot turn a unit/Horn application into primary knowledge");
        denied(ContextUniversalInputResiduals.scan(theory(x,a),input,16,16384,1),"work limit yields no proof prefix");
        String constants="!@u seed(7,u)->tag(u);";Result none=scan(theory(x,bind(false,constants)),input);
        require(none.steps.isEmpty()&&none.attempts>0,"ordinary fixed7 cannot match a frozen root; empty enumeration is not family coverage");
    }
    static void privateAndTrusted()throws Exception {
        String rule="!@u @v seed(u,1)&&gate(v,1)->rel(u,v);";
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,SEED),b=bind(false,rule);Packet common=ContextPrivateWitnessPoolRunner.common(x);
        for(boolean reverse:new boolean[]{false,true}) {
            Snapshot target=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);
            PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,target,WORK);
            ContextUniversalUnitClosure.Result t=theory(x,target);Result result=ContextUniversalInputResiduals.scanPrivate(t,pool,16,16384,WORK);
            require(result.enumerated&&result.privateInput&&result.basis.equals(common.identity)&&result.localBasis.equals(pool.local.identity),"exact trusted private/common input split");
            Step step=donated(result,"seed");require(step.support.sources.equals(set(source(a,SEED),source(b,rule)))&&step.support.depth==1,"raw trusted members can combine only in their declared private cut, without unrelated X anchor");
            require(step.residual.origins.iterator().next().source.equals(source(a,SEED)),"frozen origin keeps physical member owner, not synthetic trusted-group identity");
            require(scan(t,common).steps.isEmpty(),"private input facts are not borrowed by a common-only scan");
            PrivatePool wrong=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
            denied(ContextUniversalInputResiduals.scanPrivate(t,wrong,16,16384,WORK),"different owning cut cannot supply alleged trusted donors");
            require(common.items.size()==1&&pool.local.items.stream().noneMatch(i->i.atom.predicate.equals("rel")),"residual enumeration mutates neither private nor common Packet");
        }
        PrivatePool independent=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
        require(ContextUniversalInputResiduals.scanPrivate(theory(x,a),independent,16,16384,WORK).steps.isEmpty(),"independent A cannot acquire B's raw rule");
    }
    static void carriedDepth()throws Exception {
        String s1="!@w seed(w,1)->pone(w,1);",s2="!@w pone(w,1)->ptwo(w,1);",s3="!@w ptwo(w,1)->pthree(w,1);",rule="!@w pthree(w,1)&&gate(1)->out(w,1);";
        Snapshot x=bind(true,"!anchor(1);"+s1+s2+s3),a=bind(false,SEED),b=bind(false,rule);Packet common=ContextPrivateWitnessPoolRunner.common(x),carried;
        PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.openPrivate(pool,WORK,500000)) {
            for(String text:Arrays.asList(s1,s2,s3))pass.rule(source(x,text),text);
            ContextWitnessResultTransport.Result next=ContextWitnessResultTransport.exportNext(pass,16,WORK);require(next.exported,"real X-dependent private-root precursor: "+next.reason);carried=next.packet;
        }
        require(carried.items.stream().noneMatch(i->i.atom.predicate.equals("seed")||i.atom.predicate.equals("flag")),"raw private declaration absent from actual carried input");
        ContextUniversalUnitClosure.Result t=theory(x,b);Result result=scan(t,carried);Step step=donated(result,"pthree");
        require(step.donorProof.depth==3&&step.support.depth==4&&step.support.sources.equals(set(source(a,SEED),source(x,s1),source(x,s2),source(x,s3),source(b,rule))),"residual proof uses actual carried depth3 and all ancestry, not raw origin.Source shortcut");
        require(step.residual.origins.iterator().next().source.equals(source(a,SEED))&&step.residual.variables.isEmpty(),"carried foreign root stays rigid in the residual");
        denied(ContextUniversalInputResiduals.scan(t,carried,3,16384,WORK),"depth4 obligation refuses a shorter successful prefix");
    }
    static void alternatives()throws Exception {
        String q="!@w seed(w,1)->q(w,1);",rule="!@w q(w,1)&&gate(1)->out(w,1);";
        Snapshot x=bind(true,SEED),a=bind(false,q),b=bind(false,q),c=bind(false,rule);
        Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b),WORK);
        Packet input=ContextPrivateWitnessPoolRunner.common(x),carried;
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.open(input,Cut.of(group),WORK,500000)) {
            pass.rule(source(a,q),q);pass.rule(source(b,q),q);
            ContextWitnessResultTransport.Result result=ContextWitnessResultTransport.exportNext(pass,16,WORK);
            require(result.exported,"actual coalesced donor precursor: "+result.reason);carried=result.packet;
        }
        Result result=scan(theory(x,c),carried);require(result.steps.size()==2,"every authenticated donor Source alternative creates its own residual proof");
        require(result.steps.get(0).donor==result.steps.get(1).donor&&result.steps.get(0).residual.equivalent(result.steps.get(1).residual,()->{}),"one physical input fact and same residual retain distinct proof alternatives");
        Set<Support> actual=new HashSet<>();for(Step step:result.steps)actual.add(step.support);
        require(actual.equals(new HashSet<>(Arrays.asList(new Support(set(source(x,SEED),source(a,q),source(c,rule)),2),new Support(set(source(x,SEED),source(b,q),source(c,rule)),2)))),"no donor union flattening, omission or depth reset");
        ContextUniversalUnitClosure.Result invalid=ContextUniversalUnitClosure.close(x,bind(false,rule+"!~wrong(1);"),16,16,512,256,WORK);
        denied(ContextUniversalInputResiduals.scan(invalid,carried,16,16384,WORK),"unsupported later whole declaration cannot leave a usable earlier schema prefix");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("input-residuals-").toString());
        missingNative();frozenAndAlpha();diagonalsAndPivots();privateAndTrusted();carriedDepth();alternatives();
        denied(ContextUniversalInputResiduals.scan(null,null,16,16384,WORK),"missing sealed schema/input");
        System.out.println("CONTEXT_UNIVERSAL_INPUT_RESIDUALS_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
