/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Native symbolic unit/Horn obligations preserve all values and cannot authorize family admission. */
public final class ContextUniversalSchemaInteractionRunner {
    static final int WORK=64000000,EVENTS=500000;
    static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static List<Entry> entries(Snapshot... all){List<Entry> out=new ArrayList<>();for(Snapshot s:all)out.addAll(s.primary);return out;}
    static final class Trial implements AutoCloseable {
        final Snapshot a,b,impostor;final Replay replay;final Packet input;final ContextProductionCapture capture;
        final List<Entry> originals;
        Trial(String unit,String horn,boolean reverse,boolean duplicate,boolean enabled)throws Exception {
            Snapshot x=bind(true,"!anchor(1);");a=bind(false,unit+horn);b=duplicate?bind(false,unit):null;impostor=bind(false,unit+horn);
            input=ContextPrivateWitnessPoolRunner.common(x);replay=ContextWitnessResultTransport.restore(input,WORK);
            originals=duplicate?entries(a,b):entries(a);List<Entry> order=new ArrayList<>(originals);if(reverse)Collections.reverse(order);
            replay.mind.setQueryPass(QueryPass.ACCEPT);capture=ContextProductionCapture.begin(replay.mind,EVENTS);
            if(enabled)capture.observeSchemaInteractions();
            try {for(Entry e:order)capture.replay(replay.mind,e.source,e.statement);replay.mind.link(null,false);}
            catch(Exception failure){capture.close();throw failure;}
        }
        ContextUniversalSchemaInteractionAudit.Result audit(){return ContextUniversalSchemaInteractionAudit.inspect(replay,capture,originals,WORK);}
        public void close(){capture.close();}
    }
    static Clause clause(String text)throws Exception{return bind(false,text).primary.get(0).clauses.get(0);}
    static void one(String unit,String horn,String expected,boolean reverse,boolean duplicate)throws Exception {
        Clause desired=clause(expected);
        try(Trial t=new Trial(unit,horn,reverse,duplicate,true)) {
            ContextProductionCapture.Snapshot before=t.capture.snapshot();ContextUniversalSchemaInteractionAudit.Result audit=t.audit();
            System.out.println("SCHEMA_CASE reverse="+reverse+" duplicate="+duplicate+" complete="+audit.observedComplete+" reason="+audit.reason+" encounters="+before.schemaInteractions.size());
            require(audit.observedComplete,"typed observed symbolic interaction: "+audit.reason);
            require(audit.replay.equals(t.replay.namespace),"symbolic obligations are bound to exact replay namespace");
            require(!audit.obligations.isEmpty(),"actual native encounters, not a vacuous audit");
            Set<Set<org.kanger.ContextProductionCapture.Source>> alternatives=new HashSet<>();
            for(ContextUniversalSchemaInteractionAudit.Obligation o:audit.obligations) {
                require(o.unifiable&&o.resolvent!=null&&o.resolvent.equivalent(desired),"principal symbolic resolvent preserves fixed values/sharing/free variables");
                require(before.schemaInteractions.contains(o.observation),"exact native encounter identity");alternatives.addAll(o.sourceAlternatives);
            }
            Set<org.kanger.ContextProductionCapture.Source> base=new HashSet<>();for(Entry e:t.a.primary)base.add(e.source);
            require(alternatives.contains(base),"whole original unit and Horn source support retained");
            if(duplicate){Set<org.kanger.ContextProductionCapture.Source> other=new HashSet<>();other.add(t.a.primary.get(1).source);other.add(t.b.primary.get(0).source);require(alternatives.contains(other),"duplicate original unit yields its separate support alternative");}
            require(t.capture.snapshot().eventCount==before.eventCount&&t.capture.snapshot().gaps.equals(before.gaps),"audit neither executes inference nor clears old native gaps");
            require(before.gaps.stream().anyMatch(g->g.startsWith("unbound-waiter-composition:")),"legacy refusal remains visible");
            require(t.input.items.size()==1&&t.input.items.get(0).atom.predicate.equals("anchor"),"no symbolic resolvent asserted into common X");
            require(!ContextUniversalSchemaInteractionAudit.inspect(t.replay,t.capture,t.impostor.primary,WORK).observedComplete,"same text under another Source cannot replace owners");
            List<Entry> omitted=new ArrayList<>(t.originals);omitted.remove(0);
            ContextUniversalSchemaInteractionAudit.Result missing=ContextUniversalSchemaInteractionAudit.inspect(t.replay,t.capture,omitted,WORK);
            require(!missing.observedComplete&&missing.obligations.isEmpty(),"missing owner yields no partial obligation prefix");
            ContextUniversalSchemaInteractionAudit.Result bounded=ContextUniversalSchemaInteractionAudit.inspect(t.replay,t.capture,t.originals,1);
            require(!bounded.observedComplete&&bounded.obligations.isEmpty(),"one shared work budget bounds the complete audit");
            Replay other=ContextWitnessResultTransport.restore(t.input,WORK);
            require(!ContextUniversalSchemaInteractionAudit.inspect(other,t.capture,t.originals,WORK).observedComplete,"equal local IDs cannot cross capture boundaries");
        }
    }
    static void incompatible()throws Exception {
        for(String[] pair:new String[][]{{"!@w gate(w,1);","!@w seed(w,1)&&gate(w,2)->mid(w,1);"},{"!@u @v tri(u,u,v);","!@w seed(w,1)&&tri(1,2,w)->mid(w,1);"}})
            try(Trial t=new Trial(pair[0],pair[1],false,false,true)) {
                ContextUniversalSchemaInteractionAudit.Result result=t.audit();require(result.observedComplete&&!result.obligations.isEmpty(),"nonunifiable native attempt is audited");
                for(ContextUniversalSchemaInteractionAudit.Obligation o:result.obligations)require(!o.unifiable&&o.resolvent==null,"MGU failure is incompatible attempt, never empty refutation");
            }
    }
    static void unsupported()throws Exception {
        String unit="!@w gate(w,1);",horn="!@w seed(w,1)&&gate(w,1)->mid(w,1);";
        try(Trial t=new Trial(unit,horn,false,false,false)) {
            require(!t.audit().observedComplete&&t.audit().obligations.isEmpty(),"absence of opt-in metadata cannot certify schema coverage");
            t.capture.observeSchemaInteractions();t.replay.mind.link(null,false);
            require(!t.capture.snapshot().schemaInteractions.isEmpty(),"later repeated native pass has typed encounter metadata");
            require(t.capture.snapshot().schemaInteractionsMissed,"earlier missed encounter is retained despite later observation");
            require(!t.audit().observedComplete&&t.audit().obligations.isEmpty(),"repeated native rule IDs cannot retrospectively certify omitted history");
        }
        try(Trial t=new Trial("!@w gate(w,1);","!@w gate(w,1)->(mid(w,1)||spare(w,1));",false,false,true)) {
            require(!t.capture.snapshot().schemaInteractions.isEmpty(),"non-Horn encounter actually observed");
            require(!t.audit().observedComplete&&t.audit().obligations.isEmpty(),"unit/Horn audit cannot waive a multi-head clause");
        }
    }
    static void budgetAndFreshValues()throws Exception {
        String unit="!@w gate(w,1);",horn="!@w gate(w,1)->mid(w,1);";
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,unit+horn);Replay replay=ContextWitnessResultTransport.restore(ContextPrivateWitnessPoolRunner.common(x),WORK);
        replay.mind.setQueryPass(QueryPass.ACCEPT);
        try(ContextProductionCapture capture=ContextProductionCapture.begin(replay.mind,2)) {
            capture.observeSchemaInteractions();for(Entry e:a.primary)capture.replay(replay.mind,e.source,e.statement);
            try{replay.mind.link(null,false);}catch(Exception expected){} // The journal must retain the exhausted-budget evidence even if native code catches it.
            require(capture.snapshot().gaps.contains("observation-budget-exhausted"),"typed encounters consume the capture event budget");
            require(capture.snapshot().schemaInteractionsMissed,"failed typed recording retains missed-history evidence");
            ContextUniversalSchemaInteractionAudit.Result result=ContextUniversalSchemaInteractionAudit.inspect(replay,capture,a.primary,WORK);
            require(!result.observedComplete&&result.obligations.isEmpty(),"exhausted journal cannot certify even an observed prefix");
        }
        User user=new User();Mind nativeMind=new Mind(user);user.setCurrentMind(nativeMind);
        require(nativeMind.compile(unit+horn,null,false),"native universal chain accepted");
        require(Boolean.TRUE.equals(nativeMind.query("?mid(John,1);",null,false)),"principal residual applies to previously unmentioned named value");
        require(Boolean.TRUE.equals(nativeMind.query("?mid(73,1);",null,false)),"principal residual applies to previously unmentioned numeric value");
        require(nativeMind.query("?mid(John,2);",null,false)==null,"fixed universal argument not generalized");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-schema-").toString());
        for(boolean reverse:new boolean[]{false,true}) {
            one("!@w gate(w,1);","!@w seed(w,1)&&gate(w,1)->mid(w,1);","!@w seed(w,1)->mid(w,1);",reverse,false);
            one("!@w gate(w,1);","!@w gate(w,1)->mid(w,1);","!@w mid(w,1);",reverse,false);
            one("!@w pair(w,w);","!@u @v seed(u,1)&&pair(u,v)->mid(u,v);","!@u seed(u,1)->mid(u,u);",reverse,false);
            one("!@w pair(w,7);","!@u @v seed(u,1)&&pair(u,v)->mid(u,v);","!@u seed(u,1)->mid(u,7);",reverse,false);
            one("!@w pair(7,w);","!@u @v seed(u,1)&&pair(u,v)->mid(u,v);","!@v seed(7,1)->mid(7,v);",reverse,false);
            one("!@w gate(w,1);","!@w seed(w,1)&&gate(w,1)->mid(w,1);","!@w seed(w,1)->mid(w,1);",reverse,true);
        }
        one("!@w gate(w,1)&&(seed(w,1)&&gate(w,1)->mid(w,1));","","!@w seed(w,1)->mid(w,1);",false,false);
        one("!@w gate(w,1);","!@w seed(w,1)&&gate(w,1)&&gate(w,1)->mid(w,1);","!@w seed(w,1)->mid(w,1);",false,false);
        incompatible();unsupported();budgetAndFreshValues();System.out.println("CONTEXT_UNIVERSAL_SCHEMA_INTERACTION_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
