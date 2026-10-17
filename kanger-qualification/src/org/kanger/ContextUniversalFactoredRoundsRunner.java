/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitRounds.*;
import org.kanger.ContextUniversalFamilyCoverage.Mode;

/** Actual factoring transport, triple independent audits and sourced diagonal refutation. */
public final class ContextUniversalFactoredRoundsRunner {
    private static int checks,ids;
    private static final int WORK=64000000;
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result b=ContextProductionNativeBinding.bind(m,inventory,WORK,500000);
        require(b.bound,"native binding: "+b.reason); return b.snapshot;
    }
    private static Clause clause(Snapshot s) { return s.primary.get(0).clauses.get(0); }
    private static Set<Source> causes(Snapshot... snapshots) {
        Set<Source> result=new HashSet<>(); for(Snapshot s:snapshots) for(ContextProductionNativeBinding.Entry e:s.primary) result.add(e.source); return result;
    }
    private static List<Receipt> pass(Frontier f) {
        List<Receipt> result=new ArrayList<>();
        for(Snapshot cut:f.cuts) {
            Receipt r=ContextUniversalUnitRounds.inspectFactored(f,cut,16,16,WORK);
            require(r.verified && r.includesCompounds && r.includesFactoring,"verified factored receipt: "+r.reason); result.add(r);
        }
        return result;
    }
    private static int refute(Snapshot x,List<Snapshot> foreign,Set<Source> expected) {
        Frontier f=ContextUniversalUnitRounds.start(x,foreign,WORK);
        for(int round=1;round<=5;round++) {
            Barrier b=ContextUniversalUnitRounds.merge(f,pass(f),16,WORK);
            if(b.status==Status.COLLISION) {
                require(b.frontier==null && b.oppositions.stream().anyMatch(p -> p.contradiction && p.dependsOnX && p.support.sources.equals(expected)),"factored family refutation has exact original sources"); return round;
            }
            require(b.status==Status.MERGED && b.changed,"pending factored consequence cannot be called stable: "+b.reason); f=b.frontier;
        }
        throw new AssertionError("missing factoring collision");
    }
    private static void incomplete(ContextUniversalUnitLedger.Step s,String why) {
        require(s.status==ContextUniversalUnitLedger.Status.INCOMPLETE && s.next==null && s.evidence.isEmpty() && s.compoundEvidence.isEmpty() && s.factoringEvidence.isEmpty() && s.oppositions.isEmpty(),why+": "+s.reason);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-factored-rounds-").toString());
            Snapshot positive=snapshot(true,"!@x @y p(x)||p(y);"),negative=snapshot(false,"!@x @y ~p(x)||~p(y);");
            ContextUniversalUnitLedger.Opened opened=ContextUniversalUnitLedger.openPrimary(positive,negative,WORK);
            require(opened.opened,"actual diagonal primary ledger"); ContextUniversalUnitLedger base=opened.ledger;
            ContextUniversalUnitLedger.Step first=ContextUniversalUnitLedger.stepFactored(base,16,WORK);
            require(first.status==ContextUniversalUnitLedger.Status.ADVANCED && first.changed && first.includesFactoring && first.factoringEvidence.size()==2 && first.oppositions.isEmpty(),"first immutable step proves both factors but cannot consume them yet");
            require(first.next.rows.stream().filter(row -> row.clause.literals.size()==1).count()==2,"both generic unit factors really enter sealed successor rows");
            require(base.rows.stream().allMatch(row -> row.clause.literals.size()==2),"factor step never mutates original ledger");
            require(ContextUniversalUnitCoverage.audit(base,first.evidence,16,WORK).covered && ContextUniversalCompositionCoverage.auditLedger(base,first.compoundEvidence,16,WORK).covered && ContextUniversalFactoringCoverage.audit(base,first.factoringEvidence,16,WORK).covered,"three independent audits bind same immutable input");
            ContextUniversalUnitLedger.Step second=ContextUniversalUnitLedger.stepFactored(first.next,16,WORK);
            require(second.status==ContextUniversalUnitLedger.Status.OPPOSITION && second.includesFactoring && second.next==null && second.oppositions.stream().anyMatch(p -> p.support.sources.equals(causes(positive,negative)) && p.support.depth==2),"second step gives actual typed diagonal refutation with two original Sources/depth2");
            incomplete(ContextUniversalUnitLedger.stepFactored(first.next,1,WORK),"later binary/alternative depth failure discards earlier successful factors too");
            incomplete(ContextUniversalUnitLedger.stepFactored(base,0,WORK),"factoring depth bound");
            incomplete(ContextUniversalUnitLedger.stepFactored(base,16,1),"shared triple-audit work budget");
            incomplete(ContextUniversalUnitLedger.stepFactored(null,16,WORK),"missing factored input");
            Frontier counter=ContextUniversalUnitRounds.start(positive,Collections.singletonList(negative),WORK);
            List<Receipt> counterReceipts=pass(counter); Barrier collision=ContextUniversalUnitRounds.merge(counter,counterReceipts,16,WORK);
            require(collision.status==Status.COLLISION && collision.oppositions.stream().anyMatch(p -> p.support.sources.equals(causes(positive,negative)) && p.support.depth==2),"diagonal counterexample is rejected at first COMPLETE family barrier");
            require(!ContextUniversalFamilyCoverage.audit(counter,counterReceipts,Mode.FACTORED_CUTS,16,WORK).covered,"proved collision cannot pass collision-free family coverage");
            List<Receipt> unavailable=new ArrayList<>(counterReceipts); unavailable.set(0,ContextUniversalUnitRounds.inspectFactored(counter,positive,16,16,1));
            Barrier blocked=ContextUniversalUnitRounds.merge(counter,unavailable,16,WORK);
            require(blocked.status==Status.INCOMPLETE && blocked.frontier==null && blocked.oppositions.isEmpty(),"missing complete self evidence blocks even already proved foreign-cut opposition");
            Snapshot empty=snapshot(false,"!unrelated(7);"); Frontier consistent=ContextUniversalUnitRounds.start(positive,Collections.singletonList(empty),WORK);
            List<Receipt> cr=pass(consistent);
            require(ContextUniversalFamilyCoverage.audit(consistent,cr,Mode.FACTORED_CUTS,16,WORK).covered,"factoring closure, result retention and export covered");
            require(!ContextUniversalFamilyCoverage.audit(consistent,cr,Mode.FACTORED_STABLE_SHARED,16,WORK).covered,"new common factors require another qualification round");
            Barrier next=ContextUniversalUnitRounds.merge(consistent,cr,16,WORK);
            require(next.status==Status.MERGED && next.changed,"actual generic unit factor crosses common barrier");
            List<Receipt> stable=pass(next.frontier);
            ContextUniversalFamilyCoverage.Audit stableAudit=ContextUniversalFamilyCoverage.audit(next.frontier,stable,Mode.FACTORED_STABLE_SHARED,16,WORK);
            require(stableAudit.covered && stableAudit.reason.contains("satisfiability and write admission unknown"),"handled same-sign factors pass explicit factored mode without becoming SAT/write certificate");
            List<Receipt> mixed=new ArrayList<>(stable); mixed.set(0,ContextUniversalUnitRounds.inspectCombined(next.frontier,positive,16,16,WORK));
            require(!ContextUniversalFamilyCoverage.audit(next.frontier,mixed,Mode.FACTORED_CUTS,16,WORK).covered,"binary-only receipt cannot certify factoring mode even with identical carriers");
            require(!ContextUniversalFamilyCoverage.audit(next.frontier,cr,Mode.FACTORED_CUTS,16,WORK).covered,"stale factoring family receipts refuse");
            require(!ContextUniversalFamilyCoverage.audit(next.frontier,stable,Mode.FACTORED_CUTS,16,1).covered,"factored family audit budget has no success prefix");
            Snapshot rule=snapshot(false,"!@x p(x)->q(x);"),opposing=snapshot(false,"!~q(9);");
            int forward=refute(positive,Arrays.asList(rule,opposing),causes(positive,rule,opposing));
            int reverse=refute(positive,Arrays.asList(opposing,rule),causes(positive,rule,opposing));
            require(forward==2 && reverse==forward,"factor-derived universal q requalifies at unmentioned9 next barrier in both sibling orders");
            Snapshot unrelated=snapshot(true,"!anchor(1);"),rawPositive=snapshot(false,"!@x @y p(x)||p(y);"),rawNegative=snapshot(false,"!@x @y ~p(x)||~p(y);");
            Frontier independent=ContextUniversalUnitRounds.start(unrelated,Arrays.asList(rawPositive,rawNegative),WORK); List<Receipt> independentReceipts=pass(independent);
            Barrier independentBarrier=ContextUniversalUnitRounds.merge(independent,independentReceipts,16,WORK);
            require(independentBarrier.status==Status.MERGED && !independentBarrier.changed && independentReceipts.stream().allMatch(r -> r.products.isEmpty()),"opposite sibling factors remain foreign-only, never pooled X");
            require(ContextUniversalFamilyCoverage.audit(independent,independentReceipts,Mode.FACTORED_STABLE_SHARED,16,WORK).covered,"independent foreign factor closures are separately covered");
            Snapshot dependent=snapshot(true,"!@x p(x)->done(x);"),against=snapshot(false,"!~done(9);");
            Frontier privateF=ContextUniversalUnitRounds.start(dependent,Arrays.asList(rawPositive,against),WORK); List<Receipt> privateR=pass(privateF);
            Clause pUnit=clause(snapshot(false,"!@x p(x);")),done=clause(snapshot(false,"!@x done(x);"));
            Receipt privateA=privateR.stream().filter(r -> r.target==rawPositive).findFirst().get();
            require(privateA.products.stream().noneMatch(row -> row.clause.equivalent(pUnit)) && privateA.products.stream().anyMatch(row -> row.clause.equivalent(done) && row.support.sources.equals(causes(dependent,rawPositive)) && row.support.depth==2),"private foreign factor feeds X rule without itself exporting");
            require(refute(dependent,Arrays.asList(rawPositive,against),causes(dependent,rawPositive,against))==2,"private foreign factoring chain survives cross-cut qualification");
            Receipt shortClosure=ContextUniversalUnitRounds.inspectFactored(consistent,positive,1,16,WORK);
            require(!shortClosure.verified && shortClosure.products.isEmpty() && shortClosure.oppositions.isEmpty(),"unfinished factoring closure returns no receipt prefix");
            boolean immutable=false; try { first.factoringEvidence.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"step factoring proof list immutable");
            System.out.println("CONTEXT_UNIVERSAL_FACTORED_ROUNDS_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
