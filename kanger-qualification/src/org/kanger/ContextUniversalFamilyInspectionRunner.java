/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFamilyInspection.*;

/** Bounded end-to-end synchronous orchestration, including late refusal and sourced collision. */
public final class ContextUniversalFamilyInspectionRunner {
    private static int checks,ids;
    private static final int WORK=32000000;
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result b=ContextProductionNativeBinding.bind(m,inventory,WORK,500000);
        require(b.bound,"native binding: "+b.reason); return b.snapshot;
    }
    private static Result inspect(Snapshot x,List<Snapshot> foreign) {
        return ContextUniversalFamilyInspection.inspect(x,foreign,8,16,16,20000,1000000,WORK);
    }
    private static void incomplete(Result r,String why) {
        require(r.status==Status.INCOMPLETE && r.certificate==null && r.collision==null,why+": "+r.reason);
    }
    private static void consistent(Result r,int barriers,String why) {
        require(r.status==Status.CONSISTENT && r.certificate!=null && r.collision==null && r.completedBarriers==barriers,why+": "+r.reason);
        List<org.kanger.ContextUniversalUnitRounds.Receipt> receipts=new ArrayList<>();
        List<org.kanger.ContextUniversalQuotientModel.Model> models=new ArrayList<>();
        for(ContextUniversalFamilyModel.Cut c:r.certificate.cuts) { receipts.add(c.receipt); models.add(c.model); }
        require(ContextUniversalFamilyModel.certify(r.certificate.input,receipts,models,16,20000,WORK).certified,"independent recheck of orchestrated historical certificate");
    }
    private static Set<Source> sources(Snapshot... snapshots) {
        Set<Source> result=new HashSet<>(); for(Snapshot s:snapshots) for(ContextProductionNativeBinding.Entry e:s.primary) result.add(e.source); return result;
    }
    private static void collision(Result r,int barriers,Set<Source> expected,String why) {
        require(r.status==Status.COLLISION && r.certificate==null && r.collision!=null && r.completedBarriers==barriers,why+": "+r.reason);
        require(r.collision.receipts.size()==r.collision.input.cuts.size() && r.collision.receipts.stream().allMatch(v -> v.verified && v.input==r.collision.input),"collision retains exact COMPLETE immutable barrier receipts");
        require(!r.collision.oppositions.isEmpty() && r.collision.oppositions.stream().anyMatch(p -> p.contradiction && p.dependsOnX && p.support.sources.equals(expected)),"collision has actual sourced X-dependent opposition");
        org.kanger.ContextUniversalUnitRounds.Barrier replay=org.kanger.ContextUniversalUnitRounds.merge(r.collision.input,r.collision.receipts,16,WORK);
        require(replay.status==org.kanger.ContextUniversalUnitRounds.Status.COLLISION,"independent complete-barrier replay of collision evidence");
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-family-inspection-").toString());
            Snapshot anchor=snapshot(true,"!anchor(1);"),empty=snapshot(false,"");
            consistent(inspect(anchor,Collections.emptyList()),1,"self-only stable family");
            consistent(inspect(anchor,Collections.singletonList(empty)),1,"empty foreign remains an actual modeled cut");
            Snapshot a=snapshot(false,"!@x p(x);"),b=snapshot(false,"!~p(9);");
            Result independent=inspect(anchor,Arrays.asList(a,b)); consistent(independent,1,"opposite raw siblings coexist");
            consistent(inspect(anchor,Arrays.asList(b,a)),1,"opposite raw sibling order independence");
            require(independent.certificate.input.products.isEmpty(),"orchestration never pools raw foreign facts");
            Snapshot rule=snapshot(true,"!@x p(x)->q(x);"),fact=snapshot(false,"!@x p(x);"),against=snapshot(false,"!~q(9);");
            consistent(inspect(rule,Collections.singletonList(fact)),2,"generic q requires second synchronous qualification round");
            Result clash=inspect(rule,Arrays.asList(fact,against)); collision(clash,2,sources(rule,fact,against),"generic q conflicts at second complete barrier");
            collision(inspect(rule,Arrays.asList(against,fact)),2,sources(rule,fact,against),"reverse order has same collision barrier and causes");
            require(clash.collision.oppositions.stream().anyMatch(p -> p.support.depth==2),"cumulative depth crosses common barrier");
            Snapshot positive=snapshot(true,"!@x @y p(x)||p(y);"),negative=snapshot(false,"!@x @y ~p(x)||~p(y);");
            collision(inspect(positive,Collections.singletonList(negative)),1,sources(positive,negative),"actual diagonal factoring refutation");
            Snapshot slow=snapshot(false,"!@x chaina(x); !@x chaina(x)->chainb(x); !@x chainb(x)->chainc(x);");
            incomplete(ContextUniversalFamilyInspection.inspect(positive,Arrays.asList(negative,slow),8,2,16,20000,1000000,WORK),"proved earlier-cut collision cannot escape a later unfinished private closure");
            incomplete(ContextUniversalFamilyInspection.inspect(positive,Arrays.asList(slow,negative),8,2,16,20000,1000000,WORK),"incomplete whole barrier remains incomplete in reverse order");
            incomplete(ContextUniversalFamilyInspection.inspect(rule,Collections.singletonList(fact),1,16,16,20000,1000000,WORK),"changed last permitted barrier is not consistency");
            Result shortCommon=ContextUniversalFamilyInspection.inspect(rule,Arrays.asList(fact,against),1,16,16,20000,1000000,WORK);
            incomplete(shortCommon,"future collision cannot be predicted from incomplete common rounds"); require(shortCommon.completedBarriers==1,"incomplete exposes only completed count, not tentative evidence");
            incomplete(ContextUniversalFamilyInspection.inspect(rule,Collections.singletonList(fact),8,1,16,20000,1000000,WORK),"local closure limit");
            incomplete(ContextUniversalFamilyInspection.inspect(rule,Arrays.asList(fact,against),8,16,1,20000,1000000,WORK),"cumulative depth limit cannot publish partial opposition");
            incomplete(ContextUniversalFamilyInspection.inspect(anchor,Collections.emptyList(),8,16,16,20000,1000000,1),"one work allowance begins at inventory opening");
            incomplete(ContextUniversalFamilyInspection.inspect(anchor,Collections.emptyList(),8,16,16,2,2,WORK),"model construction/check pass but final certificate exceeds shared instance allowance");
            consistent(ContextUniversalFamilyInspection.inspect(anchor,Collections.emptyList(),8,16,16,3,2,WORK),1,"exact construction/check/certificate instance allowance");
            incomplete(ContextUniversalFamilyInspection.inspect(anchor,Collections.singletonList(empty),8,16,16,6,3,WORK),"model assignment allowance not reset for second cut");
            consistent(ContextUniversalFamilyInspection.inspect(anchor,Collections.singletonList(empty),8,16,16,6,4,WORK),1,"exact family model search assignment allowance");
            incomplete(ContextUniversalFamilyInspection.inspect(anchor,Collections.singletonList(empty),8,16,16,5,4,WORK),"later certificate model check discards entire successful prefix");
            incomplete(inspect(anchor,Arrays.asList(a,a)),"duplicate family context");
            incomplete(inspect(null,Collections.emptyList()),"missing actual X snapshot");
            incomplete(inspect(anchor,Collections.singletonList(null)),"missing actual foreign snapshot");
            boolean invalid=false; try { ContextUniversalFamilyInspection.inspect(anchor,Collections.emptyList(),0,16,16,3,2,WORK); } catch(IllegalArgumentException expected) { invalid=true; } require(invalid,"invalid bounds rejected");
            boolean immutable=false; try { clash.collision.receipts.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"collision receipt list immutable");
            immutable=false; try { clash.collision.oppositions.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"collision proof list immutable");
            System.out.println("CONTEXT_UNIVERSAL_FAMILY_INSPECTION_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
