/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionFamilyAssembly.*;
import org.kanger.ContextWitnessResultTransport.*;

/** Desired universal-unit coverage contract. Deliberately outside passing registries. */
public final class ContextUniversalUnitFamilyContractRunner {
    static final int WORK=64000000, EVENTS=500000;
    static final String SEED="!$w seed(w,1)&&flag(w,1);", UNIVERSAL="!@w gate(w,1);",
        GROUND="!gate(7,1);", MID="!@w seed(w,1)&&gate(w,1)->mid(w,1);",
        GROUND_MID="!@w seed(w,1)&&gate(7,1)->mid(w,1);",
        DONE="!@w mid(w,1)->done(w,1);", WARM="!@w seed(w,1)->warm(w,1);",
        OUT="!@w done(w,1)->out(w,1);", NEG="!@w done(w,1)->~flag(w,1);";
    static int checks, missing;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Source source(Snapshot s,String text){return ContextPrivateWitnessPoolRunner.source(s,text);}
    static Set<Source> sources(Source... s){return new HashSet<>(Arrays.asList(s));}
    static void nativeControl()throws Exception {
        User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);
        require(m.compile(SEED+UNIVERSAL+MID+DONE,null,false),"whole native positive theory accepted");
        require(Boolean.TRUE.equals(m.query("?$w done(w,1)&&flag(w,1);",null,false)),"native chain preserves shared seed/flag witness");
        require(Boolean.TRUE.equals(m.query("?gate(John,1);",null,false)),"universal covers a previously unmentioned named value");
        require(Boolean.TRUE.equals(m.query("?gate(73,1);",null,false)),"universal covers a previously unmentioned numeric value");
        require(m.query("?gate(John,2);",null,false)==null,"fixed universal argument remains fixed");
    }
    static void one(boolean universal,boolean local,boolean reverse,boolean negative)throws Exception {
        String gate=universal?UNIVERSAL:GROUND,mid=universal?MID:GROUND_MID,end=negative?NEG:OUT;
        Snapshot x=ContextPrivateWitnessPoolRunner.bind(true,SEED+WARM+end+(local?gate:"")),
            a=ContextPrivateWitnessPoolRunner.bind(false,(local?"":gate)+mid),
            b=ContextPrivateWitnessPoolRunner.bind(false,DONE);
        ContextProductionFamilyAssembly.Result assembled=ContextProductionFamilyAssembly.assemble(x,reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);
        require(assembled.assembled,"whole native family assembled: "+assembled.reason);
        Artifact f=assembled.artifact;Evaluation e=f.evaluate(12,16,WORK,EVENTS);
        Source owner=source(local?x:a,gate);
        require(f.rows.stream().filter(r->r.source.equals(owner)).count()==1,"one original universal/ground row, no synthetic source");
        require(f.rows.size()==x.primary.size()+a.primary.size()+b.primary.size(),"all whole native rows accounted once");
        Status desired=negative?Status.COLLISION:Status.FIXED_POINT;
        System.out.println("UNIVERSAL_UNIT_CASE universal="+universal+" local="+local+" reverse="+reverse+" negative="+negative+" desired="+desired+" actual="+e.status+" reason="+e.reason);
        if(universal&&e.status!=desired) {
            ++missing;
            require(e.status==Status.INCOMPLETE&&e.refutations.isEmpty(),"current gap must never fabricate a collision or consistency verdict");
            require(e.qualification!=null&&e.qualification.uncovered.contains(owner),"exact original universal Source remains uncovered");
            return;
        }
        require(e.status==desired,"ground control or implemented universal contract: "+e.reason);
        Set<Source> expected=sources(source(x,SEED),owner,source(a,mid),source(b,DONE),source(x,end));
        if(negative) {
            require(!e.refutations.isEmpty(),"complete sourced collision");
            for(Refutation r:e.refutations)for(Support p:r.proofs)require(p.sources.equals(expected),"exact five original Sources, no unrelated warm source");
        } else {
            Packet p=e.qualification.fixedPoint.packet;
            require(p.items.stream().filter(i->i.atom.predicate.equals("out")).anyMatch(i->i.proofs.stream().anyMatch(q->q.sources.equals(expected))),"safe closure has exact five-source out proof");
            if(!universal)require(p.items.stream().anyMatch(i->i.atom.predicate.equals("gate"))==local,"private ground control remains local");
        }
    }
    public static void main(String[] args){try{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-unit-contract-").toString());
        nativeControl();
        for(boolean u:new boolean[]{false,true})for(boolean l:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})one(u,l,r,n);
        System.out.println("UNIVERSAL_UNIT_CONTRACT_RESULT checks="+checks+" missing="+missing);
        require(missing==0,"universal-unit family coverage still missing in "+missing+" required cases");
        System.out.println("CONTEXT_UNIVERSAL_UNIT_FAMILY_CONTRACT_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
