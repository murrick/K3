/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitLedger;
import org.kanger.ContextUniversalUnitRounds.*;

/** Combined private binary resolution and synchronous family barriers. No general admission claim. */
public final class ContextUniversalCombinedRoundsRunner {
    private static int checks,ids;
    private static final int WORK=32000000;
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result b=ContextProductionNativeBinding.bind(m,inventory,WORK,500000);
        require(b.bound,"native binding"); return b.snapshot;
    }
    private static Clause clause(Snapshot s) { return s.primary.get(0).clauses.get(0); }
    private static ContextUniversalUnitLedger open(Snapshot x,Snapshot a) {
        ContextUniversalUnitLedger.Opened o=ContextUniversalUnitLedger.openPrimary(x,a,WORK);
        require(o.opened,"actual sealed primary ledger"); return o.ledger;
    }
    private static Set<Source> causes(Snapshot... snapshots) {
        Set<Source> result=new HashSet<>(); for(Snapshot s:snapshots) for(ContextProductionNativeBinding.Entry e:s.primary) result.add(e.source); return result;
    }
    private static List<Receipt> pass(Frontier f,boolean combined) {
        List<Receipt> result=new ArrayList<>();
        for(Snapshot cut:f.cuts) {
            Receipt r=combined?ContextUniversalUnitRounds.inspectCombined(f,cut,16,16,WORK):ContextUniversalUnitRounds.inspect(f,cut,16,16,WORK);
            require(r.verified,"verified cut: "+r.reason); require(r.includesCompounds==combined,"inference mode recorded explicitly"); result.add(r);
        }
        return result;
    }
    private static int refute(Snapshot x,List<Snapshot> foreign,Set<Source> expected) {
        Frontier f=ContextUniversalUnitRounds.start(x,foreign,WORK);
        for(int round=1;round<=6;round++) {
            Barrier b=ContextUniversalUnitRounds.merge(f,pass(f,true),16,WORK);
            if(b.status==Status.COLLISION) {
                require(b.frontier==null && b.oppositions.stream().anyMatch(p -> p.contradiction && p.dependsOnX && p.support.sources.equals(expected)),"combined opposition retains exactly all original causes"); return round;
            }
            require(b.status==Status.MERGED && b.changed,"cannot call missing combined consequence consistent: "+b.reason); f=b.frontier;
        }
        throw new AssertionError("missing combined collision");
    }
    private static void incomplete(ContextUniversalUnitLedger.Step s,String why) {
        require(s.status==ContextUniversalUnitLedger.Status.INCOMPLETE && s.next==null && s.evidence.isEmpty() && s.compoundEvidence.isEmpty() && s.oppositions.isEmpty(),why+": "+s.reason);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-combined-rounds-").toString());
            Snapshot x=snapshot(true,"!@x p(x)||left(x);"),a=snapshot(false,"!@x ~p(x)||left(x); !@x left(x)->done(x);");
            ContextUniversalUnitLedger l=open(x,a);
            ContextUniversalUnitLedger.Step first=ContextUniversalUnitLedger.stepCombined(l,16,WORK);
            require(first.status==ContextUniversalUnitLedger.Status.ADVANCED && first.changed && !first.compoundEvidence.isEmpty(),"actual compound ledger proof step");
            require(ContextUniversalUnitCoverage.audit(l,first.evidence,16,WORK).covered && ContextUniversalCompositionCoverage.auditLedger(l,first.compoundEvidence,16,WORK).covered,"both independent obligation audits cover exact immutable input");
            require(!ContextUniversalCompositionCoverage.auditLedger(l,Collections.emptyList(),16,WORK).covered,"unit coverage alone cannot hide missing compound result");
            Clause left=clause(snapshot(false,"!@x left(x);")),done=clause(snapshot(false,"!@x done(x);"));
            require(first.next.rows.stream().anyMatch(row -> row.clause.equivalent(left) && row.support.depth==1 && row.support.sources.size()==2),"compound parents produce real generic unit in sealed ledger");
            require(first.next.rows.stream().noneMatch(row -> row.clause.equivalent(done)),"fresh universal unit cannot feed a second inference in same immutable sweep");
            ContextUniversalUnitLedger.Step second=ContextUniversalUnitLedger.stepCombined(first.next,16,WORK);
            require(second.status==ContextUniversalUnitLedger.Status.ADVANCED && second.next.rows.stream().anyMatch(row -> row.clause.equivalent(done) && row.support.depth==2 && row.support.sources.equals(causes(x,a))),"next sweep derives generic done by unit and compound paths with same exact original causes");
            require(!ContextUniversalCompositionCoverage.auditLedger(first.next,first.compoundEvidence,16,WORK).covered,"old compound step evidence refuses on new ledger identity");
            incomplete(ContextUniversalUnitLedger.stepCombined(l,0,WORK),"combined depth stop has no unit/compound prefix");
            incomplete(ContextUniversalUnitLedger.stepCombined(l,16,1),"combined shared work stop");
            Frontier ff=ContextUniversalUnitRounds.start(x,Collections.singletonList(a),WORK);
            Receipt closed=ContextUniversalUnitRounds.inspectCombined(ff,a,16,16,WORK);
            require(closed.verified && closed.includesCompounds && closed.products.stream().anyMatch(row -> row.clause.equivalent(done) && row.support.sources.equals(causes(x,a))),"bounded combined private closure exports derived universal done");
            Receipt shortClosure=ContextUniversalUnitRounds.inspectCombined(ff,a,1,16,WORK);
            require(!shortClosure.verified && shortClosure.products.isEmpty() && shortClosure.oppositions.isEmpty(),"unfinished combined local closure exposes no prefix");
            Snapshot b=snapshot(false,"!~done(9);");
            require(refute(x,Arrays.asList(a,b),causes(x,a,b))==2,"composition-derived universal unit crosses barrier and refutes B at next round");
            Snapshot originalX=snapshot(true,"!@x ~a(x)||~b(x)||~c(x);"),originalA=snapshot(false,"!@x d(x)->a(x);"),originalB=snapshot(false,"!@x d(x)->b(x);"),originalC=snapshot(false,"!d(1); !c(1);");
            Frontier negative=ContextUniversalUnitRounds.start(originalX,Arrays.asList(originalA,originalB,originalC),WORK);
            boolean unchanged=false;
            for(int round=0;round<3;round++) {
                Barrier old=ContextUniversalUnitRounds.merge(negative,pass(negative,false),16,WORK);
                require(old.status==Status.MERGED,"unit-only negative control has not proved collision");
                if(!old.changed) { unchanged=true; break; } negative=old.frontier;
            }
            require(unchanged,"unit-only common stability really misses compound counterexample");
            int forward=refute(originalX,Arrays.asList(originalA,originalB,originalC),causes(originalX,originalA,originalB,originalC));
            int reverse=refute(originalX,Arrays.asList(originalC,originalB,originalA),causes(originalX,originalA,originalB,originalC));
            require(forward==3 && reverse==forward,"universal original composition counterexample refutes at barrier3 in both orders, exact five assertion Sources");
            Snapshot unrelated=snapshot(true,"!anchor(1);"),privateA=snapshot(false,"!@x a(x)||b(x); !@x ~a(x)||c(x); !@x ~c(x)||d(x);"),privateB=snapshot(false,"!~b(1); !~d(1);");
            Frontier pf=ContextUniversalUnitRounds.start(unrelated,Arrays.asList(privateA,privateB),WORK);
            List<Receipt> privateReceipts=pass(pf,true); Barrier pb=ContextUniversalUnitRounds.merge(pf,privateReceipts,16,WORK);
            require(pb.status==Status.MERGED && !pb.changed && privateReceipts.stream().allMatch(r -> r.products.isEmpty()),"private foreign compound closure never pools siblings with unrelated X");
            ContextUniversalUnitLedger privateLedger=open(unrelated,privateA); Clause bd=clause(snapshot(false,"!@x b(x)||d(x);"));
            ContextUniversalUnitLedger.Step ps1=ContextUniversalUnitLedger.stepCombined(privateLedger,16,WORK),ps2=ContextUniversalUnitLedger.stepCombined(ps1.next,16,WORK);
            require(ps1.status==ContextUniversalUnitLedger.Status.ADVANCED && ps1.next.rows.stream().noneMatch(row -> row.clause.equivalent(bd)) && ps2.next.rows.stream().anyMatch(row -> row.clause.equivalent(bd) && row.support.depth==2 && row.support.sources.size()==3),"concrete private second-step b-or-d survives in sealed local ledger");
            Snapshot related=snapshot(true,"!@x ~b(x)||out(x);"),opposing=snapshot(false,"!~d(1); !~out(1);");
            require(refute(related,Arrays.asList(privateA,opposing),causes(related,privateA,opposing))==2,"unexported private compound chain affects X-dependent result and is requalified, all six assertion Sources");
            Snapshot self=snapshot(true,"!@x p(x,1)||~p(2,x);"),empty=snapshot(false,"!unrelated(7);");
            ContextUniversalUnitLedger sl=open(self,empty); ContextUniversalUnitLedger.Step ss=ContextUniversalUnitLedger.stepCombined(sl,16,WORK);
            Clause selfResult=clause(snapshot(false,"!p(1,1)||~p(2,2);"));
            require(ss.status==ContextUniversalUnitLedger.Status.ADVANCED && ss.next.rows.stream().anyMatch(row -> row.clause.equivalent(selfResult) && row.support.sources.size()==1 && row.support.depth==1),"apart-renamed self composition remains in combined ledger mode");
            boolean immutable=false; try { first.compoundEvidence.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"combined compound proof list immutable");
            System.out.println("CONTEXT_UNIVERSAL_COMBINED_ROUNDS_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
