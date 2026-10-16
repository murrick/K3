/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitLedger.*;

/** Immutable private unit steps retain actual source alternatives and cumulative depth. */
public final class ContextUniversalUnitLedgerRunner {
    private static int checks,ids;
    private static final int WORK=16000000;
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
        Opened result=ContextUniversalUnitLedger.openPrimary(x,a,WORK);
        require(result.opened && result.ledger!=null,"sealed ledger: "+result.reason); return result.ledger;
    }
    private static Step step(ContextUniversalUnitLedger l,int depth) {
        Step s=ContextUniversalUnitLedger.step(l,depth,WORK);
        require(s.status!=Status.INCOMPLETE,"audited step: "+s.reason);
        require(ContextUniversalUnitCoverage.audit(l,s.evidence,depth,WORK).covered,"independent exact-ledger unit audit");
        require(s.evidence.stream().allMatch(e -> e.ledger==l),"every proof binds exact immutable ledger"); return s;
    }
    private static boolean has(ContextUniversalUnitLedger l,Clause c,int depth,int sources) {
        return l.rows.stream().anyMatch(r -> r.clause.equivalent(c) && r.support.depth==depth && r.support.sources.size()==sources);
    }
    private static void incomplete(Step s,String why) {
        require(s.status==Status.INCOMPLETE && s.next==null && !s.changed && s.evidence.isEmpty() && s.oppositions.isEmpty(),why+": "+s.reason);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-unit-ledger-").toString());
            Snapshot x=snapshot(true,"!@x p(x);"),a=snapshot(false,"!@x p(x)->q(x); !@x q(x)->r(x);");
            ContextUniversalUnitLedger l=open(x,a); int original=l.rows.size();
            Clause q=clause(snapshot(false,"!@x q(x);")),r=clause(snapshot(false,"!@x r(x);"));
            Step first=step(l,16);
            require(first.status==Status.ADVANCED && first.changed && first.next.previous==l.identity,"next historical ledger has separate identity");
            require(l.rows.size()==original && !has(l,q,1,2),"inspection cannot mutate input ledger");
            require(has(first.next,q,1,2) && first.next.rows.stream().noneMatch(row -> row.clause.equivalent(r)),"first immutable sweep learns q only, not r from freshly produced q");
            Step second=step(first.next,16);
            require(second.status==Status.ADVANCED && second.changed && has(second.next,r,2,3),"actual derived universal q becomes parent only on next step, all three sources/depth2 retained");
            Step third=step(second.next,16);
            require(third.status==Status.ADVANCED && !third.changed,"unchanged unit recipes after chain; no compound/family closure authority");
            require(!ContextUniversalUnitCoverage.audit(first.next,first.evidence,16,WORK).covered,"old ledger evidence refuses on successor even though snapshots match");
            require(!ContextUniversalUnitCoverage.auditPrimary(x,a,first.evidence,16,WORK).covered,"ledger proof cannot pass as old primary-only authority");
            ContextUniversalUnitLedger reopened=open(x,a);
            require(!ContextUniversalUnitCoverage.audit(reopened,first.evidence,16,WORK).covered,"equal inventories with a new ledger identity cannot reuse exact-input evidence");
            Snapshot opposing=snapshot(false,"!@x p(x)->q(x); !@x q(x)->r(x); !~r(9);");
            ContextUniversalUnitLedger ol=open(x,opposing); Step o1=step(ol,16),o2=step(o1.next,16);
            require(o2.status==Status.OPPOSITION && o2.next==null && !o2.oppositions.isEmpty(),"complete second step returns typed private opposition without ordinary empty clause or next ledger");
            require(o2.oppositions.stream().anyMatch(p -> p.dependsOnX && p.support.sources.size()==4 && p.support.depth==2),"opposition retains exact four original sources and cumulative depth2");
            Snapshot unrelated=snapshot(true,"!anchor(1);"),privateA=snapshot(false,"!@x p(x); !@x p(x)->q(x);");
            ContextUniversalUnitLedger local=open(unrelated,privateA); Step localStep=step(local,16);
            require(has(localStep.next,q,1,2) && localStep.evidence.stream().flatMap(e -> e.proofs.stream()).allMatch(p -> !p.dependsOnX),"foreign-only universal consequence remains private with foreign-only original causes");
            Snapshot alternative=snapshot(false,"!@x p(x)->q(x); !@x p(x)->r(x); !@x r(x)->q(x); !@x q(x)->done(x);");
            ContextUniversalUnitLedger al=open(x,alternative); Step al1=step(al,16),al2=step(al1.next,16);
            require(has(al2.next,q,1,2) && has(al2.next,q,2,3),"independent original cause groups for same generic q remain separate");
            Clause doneRule=alternative.primary.get(3).clauses.get(0); int qp=-1;
            for(int i=0;i<doneRule.literals.size();i++) if(doneRule.literals.get(i).predicate.equals("q")) qp=i;
            ContextUniversalUnitProof.Evidence alternatives=ContextUniversalUnitProof.prove(al2.next,q,0,doneRule,qp,3,WORK);
            require(alternatives.proved && alternatives.proofs.stream().anyMatch(p -> p.support.depth==2 && p.support.sources.size()==3)
                && alternatives.proofs.stream().anyMatch(p -> p.support.depth==3 && p.support.sources.size()==4),"all actual ledger support alternatives cross proof kernel coherently");
            ContextUniversalUnitProof.Evidence late=ContextUniversalUnitProof.prove(al2.next,q,0,doneRule,qp,2,WORK);
            require(!late.proved && late.proofs.isEmpty(),"late deeper alternative cannot expose earlier direct result prefix");
            incomplete(ContextUniversalUnitLedger.step(al2.next,2,WORK),"late alternative fails whole private step");
            incomplete(ContextUniversalUnitLedger.step(l,0,WORK),"depth stop");
            incomplete(ContextUniversalUnitLedger.step(l,16,1),"shared work stop");
            incomplete(ContextUniversalUnitLedger.step(null,16,WORK),"absent sealed input");
            Opened shortOpen=ContextUniversalUnitLedger.openPrimary(x,a,1);
            require(!shortOpen.opened && shortOpen.ledger==null,"bounded initial inventory exposes no row prefix");
            Snapshot sx=snapshot(true,"!@x p(x)||left(x);"),sa=snapshot(false,"!@x ~p(x)||left(x); !@x left(x)->done(x);");
            ContextOrdinaryResidualRounds.Frontier sf=ContextOrdinaryResidualRounds.start(sx,Collections.singletonList(sa),WORK);
            ContextOrdinaryResidualRounds.Receipt receipt=ContextOrdinaryResidualRounds.inspectWithProbes(sf,sa,16,WORK,500000,256);
            Clause parent=clause(sa); int px=-1,pa=-1;
            for(int i=0;i<clause(sx).literals.size();i++) if(clause(sx).literals.get(i).predicate.equals("p")) px=i;
            for(int i=0;i<parent.literals.size();i++) if(parent.literals.get(i).predicate.equals("p")) pa=i;
            ContextUniversalCompositionProof.Evidence composed=ContextUniversalCompositionProof.prove(receipt,clause(sx),px,parent,pa,16,WORK);
            require(composed.proved,"actual sealed compound-unit proof");
            Opened derived=ContextUniversalUnitLedger.open(sf,sa,Collections.singletonList(composed),16,WORK);
            require(derived.opened,"sealed compound result imports without primary Source invention");
            Step derivedStep=step(derived.ledger,16);
            Clause done=clause(snapshot(false,"!@x done(x);"));
            require(has(derivedStep.next,done,2,3),"imported actual composition unit derives generic done with all original causes");
            require(!ContextUniversalUnitLedger.open(sf,sx,Collections.singletonList(composed),16,WORK).opened,"wrong-cut compound proof cannot seed ledger");
            require(!ContextUniversalUnitLedger.open(sf,sa,Collections.singletonList(composed),0,WORK).opened,"import cannot reset derived depth");
            require(sf.products.isEmpty(),"private ledger step creates no shared Product");
            boolean immutable=false; try { l.rows.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"sealed row inventory immutable");
            immutable=false; try { first.evidence.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"step evidence immutable");
            boolean oldGate=false; try { ContextOrdinaryResidualRounds.start(x,Collections.singletonList(a),WORK); } catch(IllegalStateException expected) { oldGate=true; }
            require(oldGate,"old ground/universal-compound Frontier unit refusal unchanged");
            System.out.println("CONTEXT_UNIVERSAL_UNIT_LEDGER_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
