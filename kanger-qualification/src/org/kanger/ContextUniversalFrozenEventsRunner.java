/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenEvents.*;
import org.kanger.ContextUniversalFrozenEvents.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** Every concrete native premise is a checked fact; Source labels alone never suffice. */
public final class ContextUniversalFrozenEventsRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot x,String s){return ContextPrivateWitnessPoolRunner.source(x,s);}
    static ContextUniversalFrozenUnits.Result units(Snapshot x,Snapshot a,Packet common,PrivatePool pool){return ContextUniversalFrozenUnitsRunner.project(ContextUniversalFrozenUnitsRunner.replay(x,a,common,pool));}
    static Result events(ContextUniversalFrozenUnits.Result units){
        Result r=ContextUniversalFrozenEvents.reconstruct(units,16,65536,WORK);System.out.println("FROZEN_EVENTS "+r.reconstructed+" "+r.reason+" conclusions="+r.conclusions.size()+" stored="+r.stored.size());
        require(r.reconstructed,"typed event proofs "+r.reason);require(r.input==units&&!r.conclusions.isEmpty(),"sealed input and nonvacuous native proofs");
        Set<ContextUniversalFrozenReplay.ProducerEvent> seen=Collections.newSetFromMap(new IdentityHashMap<ContextUniversalFrozenReplay.ProducerEvent,Boolean>());
        for(Conclusion c:r.conclusions) {
            seen.add(c.event);require(c.event.producers.contains(c.producer),"exact authenticated producer token");
            require(c.fact.variables.isEmpty()&&c.fact.literals.size()==1,"concrete canonical conclusion");
            int required=c.event.witness==null?c.event.ground.premises.size():c.event.witness.premises.size();require(c.donors.size()==required,"all actual native premises, no partial donor group");
            Set<Source> sources=new HashSet<>(c.producer.support.sources);int depth=c.producer.support.depth;
            for(Donor d:c.donors) {
                require(ContextUniversalInputResiduals.instance(d.theorem.clause,d.fact,()->{}),"actual premise is an instance of checked theorem");
                Set<Source> ds=new HashSet<>(d.theorem.support.sources);int dd=d.theorem.support.depth;
                for(Binding b:d.anchors){ds.addAll(b.support.sources);dd=Math.max(dd,b.support.depth);require(units.input.theory.frame.local.items.contains(b.input)&&b.input.proofs.contains(b.support),"binding is actual input proof, never Origin.source alone");}
                if(!d.theorem.clause.variables.isEmpty())++dd;
                require(d.support.depth==dd&&d.support.sources.equals(ds),"donor instantiation preserves max depth/whole Sources");sources.addAll(d.support.sources);depth=Math.max(depth,d.support.depth);
            }
            for(Binding b:c.anchors){sources.addAll(b.support.sources);depth=Math.max(depth,b.support.depth);require(units.input.theory.frame.local.items.contains(b.input),"free-head anchor belongs to actual local input");}
            if(!c.donors.isEmpty()||!c.producer.clause.variables.isEmpty())++depth;
            require(c.support.depth==depth&&c.support.sources.equals(sources),"conclusion recomputed from full producer/donor/anchor group");
        }
        require(seen.size()==units.input.events.size(),"every observed event has a proof");
        for(Stored s:r.stored){require(s.relation.replay.equals(units.input.replay)&&r.conclusions.contains(s.prior),"stored occurrence tied to this replay and checked event proof");require(s.relation.applications.contains(s.prior.event.witness),"no global stored alias");}
        return r;
    }
    static void denied(Result r,String why){require(!r.reconstructed&&r.conclusions.isEmpty()&&r.stored.isEmpty(),why+": "+r.reason);}
    static void missingDonors()throws Exception {
        String bundle="!$w seed(w,1)&&q(7,1);",consumer="!@w seed(w,1)&&rel(w,7)->out(w,1);",p="!@u p(u,1);",rel="!@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot x=bind(true,bundle+consumer),a=bind(false,p+rel);Packet common=ContextPrivateWitnessPoolRunner.common(x);ContextUniversalFrozenUnits.Result u=units(x,a,common,null);Result r=events(u);
        require(!r.stored.isEmpty(),"actual native materializations verified, not vacuous");boolean original=false;
        require(r.conclusions.stream().anyMatch(c->c.fact.literals.get(0).predicate.equals("rel")&&c.donors.isEmpty()&&!c.anchors.isEmpty()&&c.support.depth==3),"head-free universal unit instantiated only with actual root proof and one step");
        for(Conclusion c:r.conclusions)if(c.producer.schema!=null&&c.producer.schema.original!=null&&c.producer.schema.original.source.equals(source(x,consumer))) {
            original=true;require(c.producer.support.sources.size()==1&&c.donors.size()==2,"original consumer alone is not conclusion proof");
            require(c.support.sources.equals(new HashSet<>(Arrays.asList(source(x,bundle),source(x,consumer),source(a,p),source(a,rel))))&&c.support.depth==4,"actual native hyperapplication needs full four-Source group/depth4");
        }
        require(original,"original consumer native events actually reconstructed");
        require(u.units.stream().anyMatch(t->t.conclusion.literals.get(0).predicate.equals("out")&&t.support.depth==3),"symbolic fixed out theorem remains depth3, distinct from observed hyperapplication4");
        denied(ContextUniversalFrozenEvents.reconstruct(u,3,65536,WORK),"native event grade cannot reset to shorter symbolic theorem");
        denied(ContextUniversalFrozenEvents.reconstruct(u,16,1,WORK),"receipt limit no prefix");
        denied(ContextUniversalFrozenEvents.reconstruct(u,16,65536,1),"work limit no prefix");
    }
    static void privateAndFree()throws Exception {
        String seed="!$w seed(w,1);",p="!@w p(w,1);",rule="!@w seed(w,1)&&p(w,1)->out(w,1);",anchor="!anchor(1);";
        Snapshot x=bind(true,anchor),a=bind(false,seed+p),b=bind(false,rule);Packet common=ContextPrivateWitnessPoolRunner.common(x);
        for(boolean reverse:new boolean[]{false,true}) {
            Snapshot target=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,target,WORK);
            Result r=events(units(x,target,common,pool));boolean anchored=false;
            for(Conclusion c:r.conclusions)if(c.fact.literals.get(0).predicate.equals("out")) {
                require(!c.support.sources.contains(source(x,anchor)),"unrelated X anchor never communalizes private result");
                for(Donor donor:c.donors)if(!donor.theorem.clause.variables.isEmpty()&&!donor.fact.origins.isEmpty()){require(!donor.anchors.isEmpty(),"universal witness donor has real binding proof");anchored=true;}
            }
            require(anchored,"actual universal donor instantiation at private witness");
        }
        Snapshot two=bind(true,"!$u $v pair(u,v);"),rules=bind(false,"!@u @v pair(u,v)->rel(u,v); !@u @v rel(u,v)->out(u,v);");
        Result r=events(units(two,rules,ContextPrivateWitnessPoolRunner.common(two),null));require(r.conclusions.stream().anyMatch(c->c.fact.origins.size()==2),"two independent frozen parameters retained in event proofs");
        Snapshot gx=bind(true,"!seed(7,1);"),ga=bind(false,"!@v p(v,1); !@v seed(v,1)&&p(v,1)->out(v,1);");Result g=events(units(gx,ga,ContextPrivateWitnessPoolRunner.common(gx),null));
        require(g.conclusions.stream().allMatch(c->c.fact.origins.isEmpty()&&c.anchors.isEmpty()),"ordinary events invent no binding witnesses");
    }
    static void carriedBindingsAndAlternatives()throws Exception {
        String seed="!$w seed(w,1);",s1="!@w seed(w,1)->pone(w,1);",s2="!@w pone(w,1)->ptwo(w,1);",s3="!@w ptwo(w,1)->pthree(w,1);",p="!@u p(u,1);",out="!@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot x=bind(true,"!q(7,1); !@w pthree(w,1)&&rel(w,7)->done(w,1);"+s1+s2+s3),a=bind(false,seed),b=bind(false,p+out);Packet common=ContextPrivateWitnessPoolRunner.common(x),carried;
        PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.openPrivate(pool,WORK,500000)) {
            for(String text:Arrays.asList(s1,s2,s3))pass.rule(source(x,text),text);
            ContextWitnessResultTransport.Result next=ContextWitnessResultTransport.exportNext(pass,16,WORK);require(next.exported,"actual graded precursor "+next.reason);carried=next.packet;
        }
        require(carried.items.stream().noneMatch(i->i.atom.predicate.equals("seed")),"raw private origin declaration absent from carried input");
        Result r=events(units(x,b,carried,null));boolean real=false;
        for(Conclusion c:r.conclusions)if(c.fact.literals.get(0).predicate.equals("rel")&&c.donors.isEmpty()) {
            if(c.anchors.stream().noneMatch(t->t.support.depth==3&&t.support.sources.size()==4))continue;
            real=true;require(c.support.depth==4,"actual depth3 binding plus universal instance step, no Origin.Source reset");
            require(c.support.sources.containsAll(Arrays.asList(source(a,seed),source(x,s1),source(x,s2),source(x,s3),source(b,p),source(b,out))),"binding carries every actual ancestor Source");
        }
        require(real,"actual graded free-universal binding observed");
        String q="!@w seed(w,1)->q(w,1);",consumer="!@w q(w,1)->out(w,1);";
        Snapshot cx=bind(true,seed),ca=bind(false,q),cb=bind(false,q),cc=bind(false,consumer);
        Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(ca,cb,cc),WORK);
        Result alternatives=events(units(cx,group,ContextPrivateWitnessPoolRunner.common(cx),null));
        boolean both=false;
        for(Conclusion c:alternatives.conclusions)if(c.fact.literals.get(0).predicate.equals("out")) {
            Set<Set<Source>> groups=new HashSet<>();for(Conclusion other:alternatives.conclusions)if(other.event==c.event)groups.add(other.support.sources);
            if(groups.contains(new HashSet<>(Arrays.asList(source(cx,seed),source(ca,q),source(cc,consumer))))&&groups.contains(new HashSet<>(Arrays.asList(source(cx,seed),source(cb,q),source(cc,consumer)))))both=true;
        }
        require(both,"same actual native event keeps separate complete donor Source alternatives");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-events-").toString());missingDonors();privateAndFree();carriedBindingsAndAlternatives();denied(ContextUniversalFrozenEvents.reconstruct(null,16,65536,WORK),"no sealed input");
        System.out.println("CONTEXT_UNIVERSAL_FROZEN_EVENTS_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
