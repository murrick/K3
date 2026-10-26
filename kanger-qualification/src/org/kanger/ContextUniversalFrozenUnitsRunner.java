/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenUnits.Unit;
import org.kanger.ContextUniversalFrozenUnits.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** Checked fixed unit receipt controls; never treats a universal as an already supplied fact. */
public final class ContextUniversalFrozenUnitsRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static Source source(Snapshot x,String text){return ContextPrivateWitnessPoolRunner.source(x,text);}
    static ContextUniversalFrozenReplay.Result replay(Snapshot x,Snapshot a,Packet common,PrivatePool pool) {
        ContextUniversalUnitClosure.Result schema=ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);require(schema.unitClosed,"schema precursor "+schema.reason);
        ContextUniversalFrozenClosure.Result theory=pool==null?ContextUniversalFrozenClosure.close(schema,common,16,16,512,256,WORK)
            :ContextUniversalFrozenClosure.closePrivate(schema,pool,16,16,512,256,WORK);require(theory.closed,"typed precursor "+theory.reason);
        ContextUniversalFrozenReplay.Result r=ContextUniversalFrozenReplay.replay(theory,16,WORK,500000);require(r.bound,"native precursor "+r.reason);return r;
    }
    static Result project(ContextUniversalFrozenReplay.Result input) {
        Result r=ContextUniversalFrozenUnits.project(input,16,65536,WORK);require(r.projected,"fixed unit projection "+r.reason);
        require(r.input==input,"exact sealed replay retained");
        for(Unit unit:r.units) {
            require(unit.conclusion==unit.proof.clause&&unit.support==unit.proof.support,"exact proved clause and whole Source/depth token, no relabel");
            require(unit.conclusion.literals.size()==1&&unit.conclusion.variables.isEmpty(),"fixed single conclusion only");
            require(unit.proof.input==null?unit.nativeInput!=null&&unit.nativeInput.source==null:unit.nativeInput==null,"actual derived input or authentic imported fact, never fabricated source");
        }
        long expected=input.theory.proofs.stream().filter(p->p.clause.literals.size()==1&&p.clause.variables.isEmpty()).count();
        require(r.units.size()==expected,"all fixed minimum Source alternatives, no theorem subset");
        require(r.units.size()+r.universalUnits+r.conditionals==input.theory.proofs.size(),"every proof classified exactly once");return r;
    }
    static void denied(Result r,String why){require(!r.projected&&r.units.isEmpty()&&r.universalUnits==0&&r.conditionals==0,why+": "+r.reason);}
    static void fixedAndFree()throws Exception {
        String bundle="!$w seed(w,1)&&q(7,1);",consumer="!@w seed(w,1)&&rel(w,7)->out(w,1);",p="!@u p(u,1);",rel="!@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot x=bind(true,bundle+consumer),a=bind(false,p+rel);Packet common=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalFrozenReplay.Result input=replay(x,a,common,null);Result r=project(input);
        Unit out=r.units.stream().filter(u->u.conclusion.literals.get(0).predicate.equals("out")).findFirst().get();
        require(out.support.depth==3&&out.support.sources.equals(new HashSet<>(Arrays.asList(source(x,bundle),source(x,consumer),source(a,p),source(a,rel)))),"out remains its existing depth3 theorem with all four whole Sources");
        require(out.conclusion.origins.size()==1&&out.nativeInput.clause==null,"frozen conclusion represented despite null ordinary receipt");
        require(r.universalUnits>=2&&r.conditionals>0,"universal p/rel and conditionals explicitly withheld");
        require(r.units.stream().noneMatch(u->u.conclusion.literals.get(0).predicate.equals("p")||u.conclusion.literals.get(0).predicate.equals("rel")),"no finite sampling of universal units");
        require(common.items.stream().noneMatch(i->i.atom.predicate.equals("out")),"receipt grants no mutable output Packet");
        denied(ContextUniversalFrozenUnits.project(input,2,65536,WORK),"depth bound rechecks entire DAG, no reset/prefix");
        denied(ContextUniversalFrozenUnits.project(input,16,1,WORK),"unit limit no prefix");
        denied(ContextUniversalFrozenUnits.project(input,16,65536,1),"work limit no prefix");
        ContextUniversalFrozenReplay.Result failed=ContextUniversalFrozenReplay.replay(input.theory,16,WORK,1);
        denied(ContextUniversalFrozenUnits.project(failed,16,65536,WORK),"failed replay grants no unit prefix");
    }
    static void privateAndOrigins()throws Exception {
        String seed="!$u pair(u,u);",rel="!@u @v pair(u,v)->rel(u,v);",out="!@u @v rel(u,v)->out(u,v);",anchor="!anchor(1);";
        Snapshot x=bind(true,anchor),a=bind(false,seed+rel),b=bind(false,out);Packet common=ContextPrivateWitnessPoolRunner.common(x);
        for(boolean reverse:new boolean[]{false,true}) {
            Snapshot target=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);
            PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,target,WORK);Result r=project(replay(x,target,common,pool));
            Unit unit=r.units.stream().filter(u->u.conclusion.literals.get(0).predicate.equals("out")).findFirst().get();
            require(unit.conclusion.origins.size()==1,"same rigid original parameter retained at both coordinates");
            require(unit.conclusion.origins.stream().allMatch(o->o.source.equals(source(a,seed))),"actual physical source of the binder");
            require(!unit.support.sources.contains(source(x,anchor))&&unit.support.sources.equals(new HashSet<>(Arrays.asList(source(a,seed),source(a,rel),source(b,out)))),"private theorem cannot borrow unrelated X anchor");
            require(unit.support.depth==2,"private theorem needs no extra materialization step");
            require(common.items.size()==1&&pool.local.items.stream().noneMatch(i->i.atom.predicate.equals("out")),"private receipt mutates neither common nor local input");
        }
        Snapshot two=bind(true,"!$u $v pair(u,v);"),rule=bind(false,rel+out);
        Result pair=project(replay(two,rule,ContextPrivateWitnessPoolRunner.common(two),null));
        Unit twoOrigins=pair.units.stream().filter(u->u.conclusion.literals.get(0).predicate.equals("out")).findFirst().get();
        require(twoOrigins.conclusion.origins.size()==2,"two distinct original frozen parameters retained in common-input mode");
        require(!twoOrigins.conclusion.literals.get(0).arguments.get(0).origin.equals(twoOrigins.conclusion.literals.get(0).arguments.get(1).origin),"no unproved binder identification");
        PrivatePool onlyA=ContextWitnessResultTransport.privatePool(common,x,a,WORK);Result independent=project(replay(x,a,common,onlyA));
        require(independent.units.stream().noneMatch(u->u.conclusion.literals.get(0).predicate.equals("out")),"independent A cannot consume B's rule");
    }
    static void alternativesAndOrdinary()throws Exception {
        String seed="!$w seed(w,1);",one="!@w seed(w,1)->q(w,1);",two="!@w seed(w,1)->mid(w,1); !@w mid(w,1)->q(w,1);";
        Snapshot x=bind(true,seed),a=bind(false,one+two);Result r=project(replay(x,a,ContextPrivateWitnessPoolRunner.common(x),null));
        Set<Integer> depths=new HashSet<>();Set<Set<Source>> groups=new HashSet<>();Set<Long> ids=new HashSet<>();
        for(Unit u:r.units)if(u.conclusion.literals.get(0).predicate.equals("q")){depths.add(u.support.depth);groups.add(u.support.sources);ids.add(u.nativeInput.nativeRule);}
        require(depths.equals(new HashSet<>(Arrays.asList(1,2)))&&groups.size()==2&&ids.size()==1,"coalesced native q retains depth1/depth2 exact Source alternatives");
        Snapshot gx=bind(true,"!seed(7,1);"),ga=bind(false,"!@v p(v,1); !@v seed(v,1)&&p(v,1)->out(v,1);");
        Result ordinary=project(replay(gx,ga,ContextPrivateWitnessPoolRunner.common(gx),null));
        require(ordinary.units.stream().allMatch(u->u.conclusion.origins.isEmpty()),"ordinary path invents no witness origins");
        require(ordinary.units.stream().anyMatch(u->u.conclusion.literals.get(0).predicate.equals("out")),"ordinary fixed theorem extracted");
        boolean immutable=false;try{ordinary.units.clear();}catch(UnsupportedOperationException expected){immutable=true;}require(immutable,"immutable theorem receipt list");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-units-").toString());
        fixedAndFree();privateAndOrigins();alternativesAndOrdinary();denied(ContextUniversalFrozenUnits.project(null,16,65536,WORK),"no sealed replay");
        System.out.println("CONTEXT_UNIVERSAL_FROZEN_UNITS_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
