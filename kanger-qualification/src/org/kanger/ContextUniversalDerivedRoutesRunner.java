/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Donor proof/depth and stored correspondence controls for checked derived replay. */
public final class ContextUniversalDerivedRoutesRunner {
    static final int WORK=64000000,EVENTS=500000;static int checks;
    static final String SEED=ContextUniversalDerivedReplayRunner.SEED,GATE=ContextUniversalDerivedReplayRunner.GATE,
        MID=ContextUniversalDerivedReplayRunner.MID,DONE=ContextUniversalDerivedReplayRunner.DONE,
        OUT=ContextUniversalDerivedReplayRunner.OUT,WARM=ContextUniversalDerivedReplayRunner.WARM;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static Source source(Snapshot x,String text){return ContextPrivateWitnessPoolRunner.source(x,text);}
    static Set<Source> set(Source... sources){return new HashSet<>(Arrays.asList(sources));}
    static ContextUniversalDerivedReplay.Result replay(Snapshot x,Snapshot a,Packet input){return ContextUniversalDerivedReplay.replay(ContextUniversalDerivedReplayRunner.theory(x,a),input,16,WORK,EVENTS);}
    static ContextUniversalDerivedRoutes.Result routes(ContextUniversalDerivedReplay.Result input){
        require(input.bound,"bound producer prerequisite: "+input.reason);
        ContextUniversalDerivedRoutes.Result result=ContextUniversalDerivedRoutes.reconstruct(input,16,16384,WORK);
        System.out.println("DERIVED_ROUTES reconstructed="+result.reconstructed+" reason="+result.reason+" routes="+result.routes.size());
        require(result.reconstructed,"conclusion proof: "+result.reason);return result;
    }
    static void denied(ContextUniversalDerivedRoutes.Result result,String why){require(!result.reconstructed&&result.routes.isEmpty()&&result.materializations.isEmpty()&&result.conclusions.isEmpty()&&result.stored.isEmpty()&&result.theorems.isEmpty(),why+": "+result.reason);}
    static void one(boolean local,boolean grouped,boolean reverse)throws Exception {
        Snapshot x=bind(true,SEED+WARM+OUT+(local?GATE:"")),a=bind(false,(local?"":GATE)+MID+(grouped?"":DONE)),b=grouped?bind(false,DONE):null;
        Snapshot foreign=grouped?ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK):a;
        Packet input=ContextPrivateWitnessPoolRunner.common(x);ContextUniversalDerivedReplay.Result replay=replay(x,foreign,input);
        ContextUniversalDerivedRoutes.Result result=routes(replay);
        require(result.basis.equals(input.identity)&&result.localBasis.equals(input.identity)&&result.replay.equals(replay.replay),"exact sealed input/replay identities");
        require(!result.materializations.isEmpty()&&result.outstandingGaps.equals(replay.outstandingGaps),"checked stored occurrences and unchanged outstanding schema gaps");
        boolean mid=false,gate=false,stored=false;
        for(ContextUniversalDerivedRoutes.Route route:result.conclusions) {
            if(route.producer!=null&&route.physical.predicate.equals("mid")&&route.producer.original==null) {
                mid=true;require(route.support.depth==2&&route.support.sources.equals(set(source(x,SEED),source(local?x:a,GATE),source(a,MID))),"derived residual conclusion includes actual seed donor and max(schema1,seed0)+1");
                require(route.donors.size()==1&&route.anchor==null&&route.donors.get(0).support.sources.equals(set(source(x,SEED))),"mid exact premise donor DAG");
            }
            if(route.producer!=null&&route.physical.predicate.equals("gate")) {
                gate=true;require(route.anchor!=null&&route.support.depth==1&&route.support.sources.equals(set(source(x,SEED),source(local?x:a,GATE))),"universal unit uses actual carried binding proof");
            }
            
        }
        for(ContextUniversalDerivedRoutes.Route route:result.stored)if(route.crossing!=null){stored=true;require(route.prior!=null&&route.support==route.prior.support,"materialization preserves exact proof and depth");}
        require(mid&&gate&&stored,"actual derived, unit and stored proof routes");
        denied(ContextUniversalDerivedRoutes.reconstruct(replay,1,16384,WORK),"schema depth1 does not authorize conclusion depth2");
        denied(ContextUniversalDerivedRoutes.reconstruct(replay,16,1,WORK),"route limit no successful prefix");
        denied(ContextUniversalDerivedRoutes.reconstruct(replay,16,16384,1),"shared work limit no successful prefix");
        require(input.items.size()==2,"no route receipt inserted into common knowledge");
    }
    static void ordinary()throws Exception {
        String p="!@w p(w,1);",pq="!@w p(w,1)->q(w,1);",consumer="!@w seed(w,1)&&q(w,1)->out(w,1);";
        Snapshot x=bind(true,"!seed(7,1);"+consumer),a=bind(false,p+pq);
        ContextUniversalDerivedRoutes.Result result=routes(replay(x,a,ContextPrivateWitnessPoolRunner.common(x)));
        boolean q=false,out=false,theorem=false;
        for(ContextUniversalDerivedRoutes.Route route:result.conclusions)if(route.producer!=null) {
            if(route.physical.predicate.equals("q")) {
                q=true;require(route.support.depth==2&&route.support.sources.equals(set(source(a,p),source(a,pq)))&&route.anchor==null,"ordinary universal instantiation adds step without arbitrary X anchor");
            }
            if(route.physical.predicate.equals("out")){out=true;require(route.support.sources.contains(source(x,"!seed(7,1);")),"ordinary conditional keeps seed donor");}
            if(route.event.ground.premises.isEmpty()&&route.producer.clause.variables.isEmpty()) {
                theorem=true;require(route.support.equals(route.producer.support),"ground theorem materialization keeps proved schema depth");
            }
        }
        for(ContextUniversalDerivedRoutes.Route route:result.theorems)if(route.producer.original==null) {
            theorem=true;require(route.support.equals(route.producer.support)&&route.event==null&&route.donors.isEmpty(),"ground theorem uses complete schema DAG without native-event invention or depth reset");
        }
        require(q&&out&&theorem,"ground instantiation, conditional and theorem materialization observed");
    }
    static void coalescence()throws Exception {
        String p="!@w p(w,1);",pq="!@w p(w,1)->q(w,1);",q="!@w q(w,1);";
        Snapshot x=bind(true,SEED+q+"!@w seed(w,1)&&q(w,1)->out(w,1);"),a=bind(false,p+pq);ContextUniversalDerivedRoutes.Result result=routes(replay(x,a,ContextPrivateWitnessPoolRunner.common(x)));
        Set<Support> supports=new HashSet<>();for(ContextUniversalDerivedRoutes.Route route:result.conclusions)if(route.producer!=null&&route.physical.predicate.equals("q"))supports.add(route.support);
        require(supports.equals(new HashSet<>(Arrays.asList(new Support(set(source(x,SEED),source(x,q)),1),new Support(set(source(x,SEED),source(a,p),source(a,pq)),2)))),"coalesced producer labels become distinct exact conclusion alternatives");
    }
    static void privateAndHeadFree()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"+OUT),a=bind(false,SEED+GATE+MID+DONE);Packet common=ContextPrivateWitnessPoolRunner.common(x);
        PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
        ContextUniversalDerivedRoutes.Result result=routes(ContextUniversalDerivedReplay.replayPrivate(ContextUniversalDerivedReplayRunner.theory(x,a),pool,16,WORK,EVENTS));
        require(result.localBasis.equals(pool.local.identity)&&result.basis.equals(common.identity),"private/local proof basis retained");
        boolean privateMid=false;for(ContextUniversalDerivedRoutes.Route route:result.conclusions)if(route.producer!=null&&route.physical.predicate.equals("mid")) {
            privateMid=true;require(!route.support.sources.contains(source(x,"!anchor(1);")),"unrelated X anchor cannot qualify private knowledge");
        }
        require(privateMid&&common.items.size()==1,"local results remain local receipts");
        String p="!@u p(u,1);",q="!q(7,1);",rel="!@u @v p(u,1)&&q(v,1)->rel(u,v);",consumer="!@w seed(w,1)&&rel(w,7)->out(w,1);";
        Snapshot hx=bind(true,SEED+consumer),ha=bind(false,p+q+rel);
        ContextUniversalDerivedRoutes.Result head=routes(replay(hx,ha,ContextPrivateWitnessPoolRunner.common(hx)));
        boolean free=false;
        for(ContextUniversalDerivedRoutes.Route route:head.conclusions)if(route.producer!=null&&route.physical.predicate.equals("rel")&&route.producer.clause.literals.size()==1) {
            free=true;require(route.anchor!=null&&route.donors.isEmpty(),"unit obtained through head-free schema intermediate binds actual input root");
            require(route.support.sources.equals(set(source(hx,SEED),source(ha,p),source(ha,q),source(ha,rel)))&&route.support.depth==3,"derived universal producer2 + binding0 ->3");
        }
        require(free,"actual unit through head-free intermediate reconstructed");
    }
    static void carriedPrivateOrigin()throws Exception {
        String s1="!@w seed(w,1)->pone(w,1);",s2="!@w pone(w,1)->ptwo(w,1);",s3="!@w ptwo(w,1)->pthree(w,1);",
            p="!@w p(w,1);",pg="!@w p(w,1)->gate(w,1);",end="!@w pthree(w,1)&&gate(w,1)->finish(w,1);";
        Snapshot x=bind(true,"!anchor(1);"+s1+s2+s3+end),a=bind(false,SEED),b=bind(false,p+pg);
        Packet common=ContextPrivateWitnessPoolRunner.common(x),carried;
        PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.openPrivate(pool,WORK,EVENTS)) {
            for(String rule:Arrays.asList(s1,s2,s3))pass.rule(source(x,rule),rule);
            ContextWitnessResultTransport.Result result=ContextWitnessResultTransport.exportNext(pass,8,WORK);
            require(result.exported,"private-origin input precursor: "+result.reason);carried=result.packet;
        }
        require(carried.items.stream().noneMatch(i->i.atom.predicate.equals("seed")||i.atom.predicate.equals("flag")),"raw private root declaration is absent from actual input");
        ContextUniversalDerivedRoutes.Result result=routes(replay(x,b,carried));
        Set<Support> expected=new HashSet<>(Arrays.asList(
            new Support(set(source(a,SEED),source(x,s1),source(b,p),source(b,pg)),2),
            new Support(set(source(a,SEED),source(x,s1),source(x,s2),source(b,p),source(b,pg)),3),
            new Support(set(source(a,SEED),source(x,s1),source(x,s2),source(x,s3),source(b,p),source(b,pg)),4)));
        Set<Support> actual=new HashSet<>();
        for(ContextUniversalDerivedRoutes.Route route:result.conclusions)if(route.physical.predicate.equals("gate")&&route.producer.original==null) {
            actual.add(route.support);require(route.anchor!=null&&route.support.depth==Math.max(route.producer.support.depth,route.anchor.support.depth)+1,"derived unit takes max(schema depth, actual carried depth)+1");
            require(route.support.sources.contains(source(x,s1))&&!route.support.sources.contains(source(x,"!anchor(1);")),"private origin name cannot replace actual X-dependent proof");
        }
        require(actual.equals(expected),"all real carried binding source/depth alternatives retained, including depth3 anchor");
        require(!actual.contains(new Support(set(source(a,SEED),source(b,p),source(b,pg)),2)),"no invented raw-origin shortcut");
        require(result.basis.equals(carried.identity)&&result.localBasis.equals(carried.identity),"proof basis is the actual later common packet");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("derived-routes-").toString());
        for(boolean l:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})one(l,g,r);
        ordinary();coalescence();privateAndHeadFree();carriedPrivateOrigin();denied(ContextUniversalDerivedRoutes.reconstruct(null,16,16384,WORK),"missing producer receipt");
        System.out.println("CONTEXT_UNIVERSAL_DERIVED_ROUTES_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
