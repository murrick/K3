/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitRounds.*;

/** Complete family barrier controls for sourced universal units, independent siblings and limits. */
public final class ContextUniversalUnitRoundsRunner {
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
    private static List<Receipt> pass(Frontier f) {
        List<Receipt> result=new ArrayList<>();
        for(Snapshot cut:f.cuts) { Receipt r=ContextUniversalUnitRounds.inspect(f,cut,12,16,WORK); require(r.verified,"verified complete private unit cut: "+r.reason); result.add(r); }
        return result;
    }
    private static Set<Source> causes(Snapshot... snapshots) {
        Set<Source> result=new HashSet<>();
        for(Snapshot s:snapshots) for(ContextProductionNativeBinding.Entry e:s.primary) result.add(e.source); return result;
    }
    private static int refute(Snapshot x,List<Snapshot> siblings,Set<Source> expected) {
        Frontier f=ContextUniversalUnitRounds.start(x,siblings,WORK);
        for(int round=1;round<=5;round++) {
            Barrier b=ContextUniversalUnitRounds.merge(f,pass(f),16,WORK);
            if(b.status==Status.COLLISION) {
                require(b.frontier==null && b.oppositions.stream().anyMatch(p -> p.contradiction && p.dependsOnX && p.support.sources.equals(expected)),"typed barrier opposition has exact original sources, no partial frontier"); return round;
            }
            require(b.status==Status.MERGED && b.changed,"pending counterexample cannot be marked stable: "+b.reason); f=b.frontier;
        }
        throw new AssertionError("missing family collision");
    }
    private static void incomplete(Barrier b,String why) {
        require(b.status==Status.INCOMPLETE && b.frontier==null && !b.changed && b.oppositions.isEmpty(),why+": "+b.reason);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-unit-rounds-").toString());
            Snapshot x=snapshot(true,"!@x p(x);"),a=snapshot(false,"!@x p(x)->q(x);"),b=snapshot(false,"!~q(9);");
            Frontier f=ContextUniversalUnitRounds.start(x,Arrays.asList(a,b),WORK);
            List<Receipt> outputs=pass(f);
            require(f.products.isEmpty(),"inspection cannot mutate family input");
            Receipt ar=outputs.stream().filter(r -> r.target==a).findFirst().get();
            require(ar.products.stream().anyMatch(row -> row.clause.variables.size()==1 && row.clause.literals.size()==1 && row.clause.literals.get(0).predicate.equals("q") && row.support.depth==1 && row.support.sources.equals(causes(x,a))),"generic q crosses with exact X/A sources, no known-value sampling");
            require(outputs.stream().filter(r -> r.target==b).allMatch(r -> r.products.isEmpty()),"B cannot see A's first-round q early");
            Barrier first=ContextUniversalUnitRounds.merge(f,outputs,16,WORK);
            require(first.status==Status.MERGED && first.changed && first.frontier.previous==f.identity,"new immutable complete family frontier");
            require(first.frontier.products.stream().allMatch(row -> row.support.depth>0 && row.support.sources.stream().anyMatch(s -> s.localX)),"only positive-depth X-dependent carriers share");
            int forward=refute(x,Arrays.asList(a,b),causes(x,a,b)),reverse=refute(x,Arrays.asList(b,a),causes(x,a,b));
            require(forward==2 && reverse==forward,"unmentioned value9 refutes transported generic q at second barrier in both sibling orders");
            Snapshot trigger=snapshot(true,"!@x trigger(x);"),positive=snapshot(false,"!@x trigger(x)->p(x);"),negative=snapshot(false,"!@x trigger(x)->~p(x);");
            require(refute(trigger,Arrays.asList(positive,negative),causes(trigger,positive,negative))==1,"opposite independently qualified unit schemas collide in the same complete barrier");
            Snapshot unrelated=snapshot(true,"!anchor(1);"),rawA=snapshot(false,"!@x p(x);"),rawB=snapshot(false,"!~p(9);");
            Frontier separate=ContextUniversalUnitRounds.start(unrelated,Arrays.asList(rawA,rawB),WORK);
            List<Receipt> separateOutputs=pass(separate); Barrier separateBarrier=ContextUniversalUnitRounds.merge(separate,separateOutputs,16,WORK);
            require(separateOutputs.stream().allMatch(r -> r.products.isEmpty()) && separateBarrier.status==Status.MERGED && !separateBarrier.changed,"raw sibling opposition stays separate, never communal X");
            Snapshot privateX=snapshot(true,"!@x q(x)->done(x);"),privateA=snapshot(false,"!@x p(x); !@x p(x)->q(x);"),privateB=snapshot(false,"!~done(9);");
            Frontier pf=ContextUniversalUnitRounds.start(privateX,Arrays.asList(privateA,privateB),WORK); List<Receipt> po=pass(pf);
            Receipt privateReceipt=po.stream().filter(r -> r.target==privateA).findFirst().get();
            require(privateReceipt.products.stream().anyMatch(row -> row.clause.literals.size()==1 && row.clause.literals.get(0).predicate.equals("done") && row.support.sources.equals(causes(privateX,privateA)) && row.support.depth==2),"private foreign-only q feeds X rule before export without exporting q itself");
            require(privateReceipt.products.stream().noneMatch(row -> row.clause.literals.get(0).predicate.equals("q")),"foreign-only intermediate q remains local");
            require(refute(privateX,Arrays.asList(privateA,privateB),causes(privateX,privateA,privateB))==2,"private unit closure is not lost when only X-dependent results export");
            incomplete(ContextUniversalUnitRounds.merge(f,Collections.singletonList(ar),16,WORK),"missing cuts block barrier");
            List<Receipt> duplicate=new ArrayList<>(outputs); duplicate.set(0,ar);
            incomplete(ContextUniversalUnitRounds.merge(f,duplicate,16,WORK),"duplicate cut blocks barrier");
            incomplete(ContextUniversalUnitRounds.merge(first.frontier,outputs,16,WORK),"stale exact-input receipts block successor");
            incomplete(ContextUniversalUnitRounds.merge(f,outputs,0,WORK),"lower merge depth cannot publish over-depth carrier");
            incomplete(ContextUniversalUnitRounds.merge(f,outputs,16,1),"merge work bound has no prefix");
            Receipt limited=ContextUniversalUnitRounds.inspect(f,a,1,16,WORK);
            require(!limited.verified && limited.products.isEmpty() && limited.oppositions.isEmpty(),"private closure needs stability pass; one local round returns no prefix");
            List<Receipt> failed=new ArrayList<>(outputs); failed.set(f.cuts.indexOf(a),limited);
            incomplete(ContextUniversalUnitRounds.merge(f,failed,16,WORK),"one incomplete cut blocks all successful carriers");
            Frontier cf=ContextUniversalUnitRounds.start(trigger,Arrays.asList(positive,negative),WORK); List<Receipt> co=pass(cf);
            Receipt unavailable=ContextUniversalUnitRounds.inspect(cf,positive,12,16,1); co.set(cf.cuts.indexOf(positive),unavailable);
            incomplete(ContextUniversalUnitRounds.merge(cf,co,16,WORK),"incomplete family blocks prospective shared opposition verdict");
            for(int type=0;type<2;type++) {
                Receipt stop=ContextUniversalUnitRounds.inspect(f,a,12,type==0?0:16,type==1?1:WORK);
                require(!stop.verified && stop.products.isEmpty() && stop.oppositions.isEmpty(),"depth/work limits preserve unknown with no receipt prefix");
            }
            Receipt wrong=ContextUniversalUnitRounds.inspect(f,privateA,12,16,WORK);
            require(!wrong.verified && wrong.products.isEmpty(),"foreign cut not in sealed family refuses");
            boolean immutable=false; try { first.frontier.products.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"shared product inventory immutable");
            immutable=false; try { ar.products.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"private receipt immutable");
            Snapshot splitX=snapshot(true,"!a(1)||b(1);"),splitA=snapshot(false,"!~a(1)||c(1);");
            Frontier split=ContextUniversalUnitRounds.start(splitX,Collections.singletonList(splitA),WORK);
            Barrier splitResult=ContextUniversalUnitRounds.merge(split,pass(split),16,WORK);
            require(splitResult.status==Status.MERGED && !splitResult.changed && splitResult.reason.contains("admission unknown"),"unchanged unit family does not claim missing compound closure/admission");
            System.out.println("CONTEXT_UNIVERSAL_UNIT_ROUNDS_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
