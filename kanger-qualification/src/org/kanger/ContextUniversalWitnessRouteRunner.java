/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessUnaryChainVerifier.Route;

/** Observed universal proof chains are not family coverage or ordinary export authority. */
public final class ContextUniversalWitnessRouteRunner {
    static final int WORK=64000000,EVENTS=500000;
    static final String SEED=ContextUniversalUnitFamilyContractRunner.SEED,GATE=ContextUniversalUnitFamilyContractRunner.UNIVERSAL,
        MID=ContextUniversalUnitFamilyContractRunner.MID,DONE=ContextUniversalUnitFamilyContractRunner.DONE,
        OUT=ContextUniversalUnitFamilyContractRunner.OUT,WARM=ContextUniversalUnitFamilyContractRunner.WARM;
    static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot x,String s){return ContextPrivateWitnessPoolRunner.source(x,s);}
    static Set<Source> set(Source... s){return new HashSet<>(Arrays.asList(s));}
    static List<Entry> entries(Snapshot... snapshots){List<Entry> entries=new ArrayList<>();for(Snapshot s:snapshots)for(Entry e:s.primary)if(e.bundle==null)entries.add(e);return entries;}
    static void one(boolean local,boolean grouped,boolean reverse,boolean duplicate)throws Exception {
        Snapshot x=bind(true,SEED+WARM+OUT+(local?GATE:"")),a=bind(false,(local?"":GATE)+MID+(grouped?"":DONE)),
            b=grouped?bind(false,DONE+(duplicate?GATE:"")):null;
        List<Snapshot> members=grouped?(reverse?Arrays.asList(b,a):Arrays.asList(a,b)):Arrays.asList(a);
        Cut target=Cut.of(grouped?ContextProductionNativeBinding.trusted("own",members,WORK):a);
        List<Entry> originals=grouped?entries(x,a,b):entries(x,a);if(reverse)Collections.reverse(originals);
        Packet input=ContextPrivateWitnessPoolRunner.common(x);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.open(input,target,WORK,EVENTS)) {
            for(Entry e:originals)pass.rule(e.source,e.statement);
            ContextWitnessSeedReplay.ObservedUniversals observed=pass.inspectObservedUniversals(originals,8,WORK);
            System.out.println("UNIVERSAL_ROUTE_CASE local="+local+" grouped="+grouped+" reverse="+reverse+" duplicate="+duplicate+" reconstructed="+observed.reconstructed+" reason="+observed.reason);
            require(observed.reconstructed,"observed universal route chain: "+observed.reason);
            require(observed.schemaAudit!=null&&observed.schemaAudit.observedComplete&&!observed.schemaAudit.obligations.isEmpty(),"observed routes retain checked symbolic schema obligations, without waiving coverage gap");
            require(observed.basis.equals(input.identity)&&observed.replay.equals(pass.namespace),"exact immutable input/replay binding");
            require(observed.outstandingGaps.stream().anyMatch(g->g.startsWith("unbound-waiter-composition:")),"schema interaction remains an outstanding obligation");
            Source seed=source(x,SEED),gate=source(local?x:a,GATE),done=source(grouped?b:a,DONE);
            Set<Set<Source>> expected=new HashSet<>();expected.add(set(seed,gate,source(a,MID),done,source(x,OUT)));
            if(duplicate)expected.add(set(seed,source(b,GATE),source(a,MID),done,source(x,OUT)));
            Set<Set<Source>> actual=new HashSet<>();
            for(Route route:observed.routes)if(route.physical.predicate.equals("out")) {
                require(route.proof.depth==4,"unit elimination adds one level; three Horn applications keep cumulative depth");
                require(expected.contains(route.proof.sources),"exact original source alternative; no irrelevant warm source");actual.add(route.proof.sources);
                require(route.origin!=null&&route.origin.equals(pass.bindings.values().iterator().next()),"out keeps original carried root");
            }
            require(actual.equals(expected),"all original universal producer alternatives survive");
            for(Route route:observed.routes)if(route.physical.predicate.equals("gate"))require(route.proof.depth==1&&route.proof.sources.contains(seed),"instantiation uses original witness declaration, never an invented depth0 seed");
            require(!pass.inspect(8,WORK).reconstructed,"strict existing replay gate still rejects uncovered schema interaction");
            require(!ContextWitnessResultTransport.exportNext(pass,8,WORK).exported,"local observed proof cannot obtain ordinary export permission");
            require(!pass.inspectObservedUniversals(originals,3,WORK).reconstructed,"depth3 refuses out at cumulative depth4");
            require(!pass.inspectObservedUniversals(originals,0,WORK).reconstructed,"depth0 cannot create universal instance");
            List<Entry> missing=new ArrayList<>(originals);missing.remove(0);
            ContextWitnessSeedReplay.ObservedUniversals failed=pass.inspectObservedUniversals(missing,8,WORK);
            require(!failed.reconstructed&&failed.routes.isEmpty(),"missing whole owner yields no partial proof prefix");
            require(input.items.size()==2&&input.items.stream().noneMatch(i->i.atom.predicate.equals("gate")||i.atom.predicate.equals("out")),"common packet unchanged; private universal not imported");
        }
    }
    static void privateOrigin()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"+OUT),a=bind(false,SEED+GATE+MID+DONE);
        Packet common=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
        List<Entry> originals=entries(x,a);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.openPrivate(pool,WORK,EVENTS)) {
            for(Entry e:originals)pass.rule(e.source,e.statement);
            ContextWitnessSeedReplay.ObservedUniversals result=pass.inspectObservedUniversals(originals,8,WORK);
            require(result.reconstructed,"private-origin universal chain: "+result.reason);
            boolean gate=false,out=false;
            for(Route r:result.routes) {
                if(r.physical.predicate.equals("gate")){gate=true;require(r.proof.sources.equals(set(source(a,SEED),source(a,GATE)))&&r.proof.depth==1,"private universal instance cannot borrow unrelated X anchor");}
                if(r.physical.predicate.equals("out")){out=true;require(r.proof.depth==4&&r.proof.sources.equals(set(source(a,SEED),source(a,GATE),source(a,MID),source(a,DONE),source(x,OUT))),"only real X producer introduces X dependence");}
            }
            require(gate&&out,"both private instance and X-dependent application observed");
            require(common.items.size()==1&&common.items.get(0).atom.predicate.equals("anchor"),"no raw private root promoted into common input");
            ContextWitnessSeedReplay.ObservedUniversals bounded=pass.inspectObservedUniversals(originals,8,1);
            require(!bounded.reconstructed&&bounded.routes.isEmpty(),"shared work bound cannot leak partial routes");
        }
    }
    static void carriedPrivateOrigin()throws Exception {
        String s1="!@w seed(w,1)->pone(w,1);",s2="!@w pone(w,1)->ptwo(w,1);",s3="!@w ptwo(w,1)->pthree(w,1);",end="!@w pthree(w,1)&&gate(w,1)->finish(w,1);";
        Snapshot x=bind(true,"!anchor(1);"+s1+s2+s3+end),a=bind(false,SEED),b=bind(false,GATE);
        Packet common=ContextPrivateWitnessPoolRunner.common(x),carried;
        PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.openPrivate(pool,WORK,EVENTS)) {
            for(String rule:Arrays.asList(s1,s2,s3))pass.rule(source(x,rule),rule);
            ContextWitnessResultTransport.Result result=ContextWitnessResultTransport.exportNext(pass,8,WORK);
            require(result.exported,"private-origin carried precursor: "+result.reason);carried=result.packet;
        }
        require(carried.items.stream().noneMatch(i->i.atom.predicate.equals("seed")||i.atom.predicate.equals("flag")),"only X-dependent private-root products crossed barrier");
        List<Entry> selected=new ArrayList<>(b.primary);for(Entry e:x.primary)if(e.statement.equals(end))selected.add(e);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.open(carried,Cut.of(b),WORK,EVENTS)) {
            for(Entry e:selected)pass.rule(e.source,e.statement);
            ContextWitnessSeedReplay.ObservedUniversals result=pass.inspectObservedUniversals(selected,8,WORK);
            require(result.reconstructed,"foreign-root carried join: "+result.reason);boolean gate=false;
            for(Route route:result.routes)if(route.physical.predicate.equals("gate")) {
                gate=true;require(route.proof.depth==2&&route.proof.sources.equals(set(source(a,SEED),source(x,s1),source(b,GATE))),"universal instance must retain actual X-dependent carried-root support, not raw private origin alone");
            }
            require(gate,"actual instance on foreign carried root");
        }
    }
    static void carriedDepth()throws Exception {
        String s1="!@w seed(w,1)->sone(w,1);",s2="!@w sone(w,1)->stwo(w,1);",s3="!@w stwo(w,1)->sthree(w,1);",end="!@w sthree(w,1)&&gate(w,1)->finish(w,1);";
        Snapshot x=bind(true,SEED+s1+s2+s3+GATE+end);Packet input=ContextPrivateWitnessPoolRunner.common(x),carried;
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.open(input,WORK,EVENTS)) {
            for(String rule:Arrays.asList(s1,s2,s3))pass.rule(source(x,rule),rule);
            ContextWitnessResultTransport.Result result=ContextWitnessResultTransport.exportNext(pass,8,WORK);
            require(result.exported,"ordinary carried depth3 precursor: "+result.reason);carried=result.packet;
        }
        List<Entry> selected=new ArrayList<>();for(Entry e:x.primary)if(e.statement.equals(GATE)||e.statement.equals(end))selected.add(e);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.open(carried,WORK,EVENTS)) {
            for(Entry e:selected)pass.rule(e.source,e.statement);
            ContextWitnessSeedReplay.ObservedUniversals result=pass.inspectObservedUniversals(selected,8,WORK);
            require(result.reconstructed,"carried universal join: "+result.reason);
            Set<Source> expected=set(source(x,SEED),source(x,s1),source(x,s2),source(x,s3),source(x,GATE),source(x,end));
            List<Route> finish=new ArrayList<>();for(Route r:result.routes)if(r.physical.predicate.equals("finish"))finish.add(r);
            require(!finish.isEmpty(),"actual later finish route");for(Route r:finish)require(r.proof.depth==4&&r.proof.sources.equals(expected),"depth3 carried donor is not reset by universal elimination");
            require(!pass.inspectObservedUniversals(selected,3,WORK).reconstructed,"carried depth boundary enforced");
        }
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-routes-").toString());
        for(boolean l:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})one(l,g,r,false);
        for(boolean r:new boolean[]{false,true})one(false,true,r,true);
        privateOrigin();carriedDepth();carriedPrivateOrigin();System.out.println("CONTEXT_UNIVERSAL_WITNESS_ROUTE_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
