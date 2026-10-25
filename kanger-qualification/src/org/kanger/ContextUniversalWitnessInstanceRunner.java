/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Local universal-instance authentication does not waive the owning family's RED coverage gate. */
public final class ContextUniversalWitnessInstanceRunner {
    static final int WORK=64000000,EVENTS=500000;
    static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    interface Action{void run()throws Exception;}
    static void reject(Action action,String why)throws Exception {
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException|UnsupportedOperationException e){rejected=true;}
        require(rejected,why);
    }
    static final class Trial implements AutoCloseable {
        final Snapshot x,owner,consumer,impostor;
        final Packet input;
        final Replay replay;
        final ContextProductionCapture capture;
        final List<Entry> originals=new ArrayList<>();
        final ContextProductionCapture.Snapshot observed;
        Trial(boolean local,String original,String actual,String body,boolean duplicate)throws Exception {
            x=ContextPrivateWitnessPoolRunner.bind(true,ContextUniversalUnitFamilyContractRunner.SEED);
            owner=ContextPrivateWitnessPoolRunner.bind(local,original);
            impostor=ContextPrivateWitnessPoolRunner.bind(local,original);
            consumer=ContextPrivateWitnessPoolRunner.bind(false,"!@w seed(w,1)&&"+body+"->out(w,1);");
            Snapshot other=duplicate?ContextPrivateWitnessPoolRunner.bind(local,original):null;
            input=ContextPrivateWitnessPoolRunner.common(x);replay=ContextWitnessResultTransport.restore(input,WORK);
            replay.mind.setQueryPass(QueryPass.ACCEPT);capture=ContextProductionCapture.begin(replay.mind,EVENTS);
            capture.observeWitnessProjections();originals.addAll(owner.primary);
            capture.replay(replay.mind,owner.primary.get(0).source,actual);
            if(duplicate) {
                originals.addAll(other.primary);capture.replay(replay.mind,other.primary.get(0).source,actual);
            }
            for(Entry e:consumer.primary)capture.replay(replay.mind,e.source,e.statement);
            replay.mind.link(null,false);observed=capture.snapshot();
        }
        ContextWitnessProjection unit(){return observed.witnessProjections.stream().filter(p->p.premises.isEmpty()).findFirst().orElseThrow(()->new AssertionError("native universal instantiation required"));}
        ContextUniversalWitnessInstanceVerifier.Instance verify()throws Exception{return ContextUniversalWitnessInstanceVerifier.verify(replay,capture,unit(),originals,WORK);}
        public void close(){capture.close();}
    }
    static void supported(boolean local,String rule,String body,boolean duplicate)throws Exception {
        try(Trial t=new Trial(local,rule,rule,body,duplicate)) {
            List<String> gaps=new ArrayList<>(t.observed.gaps);
            ContextUniversalWitnessInstanceVerifier.Instance instance=t.verify();
            require(instance.replay.equals(t.replay.namespace)&&instance.observation==t.unit(),"exact replay and observed event");
            require(instance.origins.size()==1&&instance.origins.values().iterator().next().source.equals(t.x.primary.get(0).source),"canonical carried root maps to original X witness Source");
            require(instance.owners.size()==(duplicate?2:1)&&instance.owners.keySet().stream().allMatch(s->s.localX==local),"all original producer alternatives and roles retained");
            require(instance.owners.values().stream().allMatch(e->e.statement.equals(rule)),"whole owner text retained including other clauses");
            require(t.capture.snapshot().gaps.equals(gaps),"local receipt does not clear native capture gaps");
            require(t.input.items.size()==2&&t.input.items.stream().noneMatch(i->i.atom.predicate.equals("gate")||i.atom.predicate.equals("pair")),"no universal instance minted into common proof packet");
            reject(()->instance.owners.clear(),"immutable receipt owner bindings");
            reject(()->instance.origins.clear(),"immutable receipt canonical origins");
            reject(()->ContextUniversalWitnessInstanceVerifier.verify(t.replay,t.capture,t.unit(),t.originals,1),"bounded instance verification");
            reject(()->ContextUniversalWitnessInstanceVerifier.verify(t.replay,t.capture,t.unit(),t.impostor.primary,WORK),"same text under another original Source cannot replace owner");
            List<Entry> missing=new ArrayList<>(t.originals);missing.remove(0);
            reject(()->ContextUniversalWitnessInstanceVerifier.verify(t.replay,t.capture,t.unit(),missing,WORK),"no omitted producer alternative");
            ContextWitnessProjection ordinary=t.observed.witnessProjections.stream().filter(p->!p.premises.isEmpty()).findFirst().get();
            reject(()->ContextUniversalWitnessInstanceVerifier.verify(t.replay,t.capture,ordinary,t.originals,WORK),"ordinary inference is not universal elimination");
            Replay otherReplay=ContextWitnessResultTransport.restore(t.input,WORK);
            reject(()->ContextUniversalWitnessInstanceVerifier.verify(otherReplay,t.capture,t.unit(),t.originals,WORK),"equal native IDs in another replay cannot authenticate instance");
            t.originals.clear();require(instance.owners.size()==(duplicate?2:1),"receipt independent of mutable caller collection");
        }
    }
    static void altered(String original,String actual,String body)throws Exception {
        try(Trial t=new Trial(false,original,actual,body,false)){reject(t::verify,"changed whole owner cannot authenticate same-labelled native universal instance");}
    }
    static void independentOrigins()throws Exception {
        Snapshot x=ContextPrivateWitnessPoolRunner.bind(true,"!$u $v seed(u,1)&&flag(v,1);"),
            owner=ContextPrivateWitnessPoolRunner.bind(false,"!@u @v pair(u,v);"),
            consumer=ContextPrivateWitnessPoolRunner.bind(false,"!@u @v seed(u,1)&&flag(v,1)&&pair(u,v)->out(u,1);");
        Replay replay=ContextWitnessResultTransport.restore(ContextPrivateWitnessPoolRunner.common(x),WORK);
        replay.mind.setQueryPass(QueryPass.ACCEPT);
        try(ContextProductionCapture capture=ContextProductionCapture.begin(replay.mind,EVENTS)) {
            capture.observeWitnessProjections();
            for(Entry e:owner.primary)capture.replay(replay.mind,e.source,e.statement);
            for(Entry e:consumer.primary)capture.replay(replay.mind,e.source,e.statement);
            replay.mind.link(null,false);
            ContextWitnessProjection p=capture.snapshot().witnessProjections.stream().filter(v->v.premises.isEmpty()).findFirst().get();
            require(replay.witnesses.size()==2,"two independently carried origins are present");
            reject(()->ContextUniversalWitnessInstanceVerifier.verify(replay,capture,p,owner.primary,WORK),"multi-origin instance cannot silently enter single-origin transport scope");
        }
    }
    static void failures()throws Exception {
        altered("!@w gate(w,1);","!@w gate(w,2);","gate(w,2)");
        altered("!@w gate(1,w);","!@w gate(w,1);","gate(w,1)");
        altered("!@w pair(w,w);","!@u @v pair(u,v);","pair(w,w)");
        altered("!@w gate(w,1)&&spare(1);","!@w gate(w,1);","gate(w,1)");
        try(Trial t=new Trial(false,"!@w ~gate(w,1);","!@w ~gate(w,1);","~gate(w,1)",false)) {
            reject(t::verify,"signed universal outside positive instance scope");
        }
        Trial closed=new Trial(false,"!@w gate(w,1);","!@w gate(w,1);","gate(w,1)",false);
        ContextWitnessProjection observation=closed.unit();closed.close();
        reject(()->ContextUniversalWitnessInstanceVerifier.verify(closed.replay,closed.capture,observation,closed.originals,WORK),"closed native journal cannot authenticate new instance");
    }
    public static void main(String[] args){try{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-instance-").toString());
        for(boolean local:new boolean[]{false,true}) {
            supported(local,"!@w gate(w,1);","gate(w,1)",false);
            supported(local,"!@w gate(1,w);","gate(1,w)",false);
            supported(local,"!@w pair(w,w);","pair(w,w)",false);
            supported(local,"!@u @v pair(u,v);","pair(w,w)",false);
            supported(local,"!@w gate(w,1)&&spare(1);","gate(w,1)",false);
            supported(local,"!@w gate(w,1);","gate(w,1)",true);
        }
        independentOrigins();failures();System.out.println("CONTEXT_UNIVERSAL_WITNESS_INSTANCE_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
