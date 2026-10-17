/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalQuotientModel.*;
import org.kanger.ContextUniversalUnitRounds.*;

/** Exact family receipt/model binding and independent sibling interpretations. */
public final class ContextUniversalFamilyModelRunner {
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
    private static ContextUniversalUnitLedger open(Snapshot x,Snapshot a) {
        ContextUniversalUnitLedger.Opened o=ContextUniversalUnitLedger.openPrimary(x,a,WORK); require(o.opened,"sealed primary ledger"); return o.ledger;
    }
    private static Model model(ContextUniversalUnitLedger ledger) {
        Result r=ContextUniversalQuotientModel.find(ledger,20000,1000000,WORK);
        require(r.modeled && r.model!=null,"checked quotient witness: "+r.reason);
        require(r.model.ledger==ledger && r.model.scope==Scope.ORDINARY_FLAT_UNIVERSAL_QUOTIENT_MODEL,"exact historical model scope");
        require(ContextUniversalQuotientModel.check(ledger,r.model,20000,WORK).valid,"independent witness check"); return r.model;
    }
    private static SemanticTermSnapshot value(String text) throws Exception {
        return snapshot(false,"!value("+text+");").primary.get(0).clauses.get(0).literals.get(0).arguments.get(0).value;
    }
    private static List<Receipt> pass(Frontier f) {
        List<Receipt> result=new ArrayList<>();
        for(Snapshot cut:f.cuts) {
            Receipt r=ContextUniversalUnitRounds.inspectFactored(f,cut,16,16,WORK);
            require(r.verified,"closed receipt: "+r.reason); result.add(r);
        }
        return result;
    }
    private static List<Model> models(List<Receipt> receipts) {
        List<Model> result=new ArrayList<>(); for(Receipt r:receipts) result.add(model(r.closureStep.input)); return result;
    }
    private static ContextUniversalFamilyModel.Result certify(Frontier f,List<Receipt> r,List<Model> m) {
        return ContextUniversalFamilyModel.certify(f,r,m,16,20000,WORK);
    }
    private static void refused(ContextUniversalFamilyModel.Result r,String why) {
        require(!r.certified && r.certificate==null,why+": "+r.reason);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-family-model-").toString());
            Snapshot x=snapshot(true,"!anchor(1);"),a=snapshot(false,"!@x p(x);"),b=snapshot(false,"!~p(9);");
            Frontier f=ContextUniversalUnitRounds.start(x,Arrays.asList(a,b),WORK);
            List<Receipt> r=pass(f); List<Model> m=models(r);
            ContextUniversalFamilyModel.Result good=certify(f,r,m);
            require(good.certified && good.certificate.input==f && good.certificate.cuts.size()==3,"exact stable complete family certificate: "+good.reason);
            require(good.certificate.scope==ContextUniversalFamilyModel.Scope.FACTORED_STABLE_FLAT_UNIVERSAL_FAMILY && good.reason.contains("write admission unknown"),"explicit historical scope, no native authoring authority");
            SemanticTermSnapshot nine=value("9");
            require(m.get(1).holds("p",Arrays.asList(nine)) && !m.get(2).holds("p",Arrays.asList(nine)),"opposite sibling interpretations legitimately coexist");
            require(f.products.isEmpty() && r.stream().allMatch(v -> v.products.isEmpty()),"model evidence exports no raw foreign facts");
            List<Model> reversed=new ArrayList<>(m); Collections.reverse(reversed);
            List<Receipt> reversedR=new ArrayList<>(r); Collections.reverse(reversedR);
            require(certify(f,reversedR,reversed).certified,"receipt and model ordering irrelevant to exact ledger matching");
            refused(certify(f,r,m.subList(0,2)),"missing model including self cannot be ignored");
            List<Model> duplicate=new ArrayList<>(m); duplicate.set(2,m.get(1)); refused(certify(f,r,duplicate),"duplicate cannot replace missing private witness");
            List<Model> missing=new ArrayList<>(m); missing.set(2,null); refused(certify(f,r,missing),"null model");
            refused(certify(f,r.subList(0,2),m),"missing exact family receipt");
            List<Model> stale=models(pass(f)); refused(certify(f,r,stale),"same frontier and semantic rows but different historical closure ledgers refuse");
            Frontier other=ContextUniversalUnitRounds.start(x,Arrays.asList(b,a),WORK);
            refused(certify(other,r,m),"foreign frontier identity refuses");
            List<Receipt> otherR=pass(other); require(certify(other,otherR,models(otherR)).certified,"reverse sibling order independently certifies");
            List<Receipt> binary=new ArrayList<>(r); binary.set(0,ContextUniversalUnitRounds.inspectCombined(f,x,16,16,WORK));
            refused(certify(f,binary,m),"binary-only coverage cannot acquire factored model certificate");
            refused(ContextUniversalFamilyModel.certify(f,r,m,16,1,WORK),"instance allowance shared across whole family, no successful prefix");
            refused(ContextUniversalFamilyModel.certify(f,r,m,16,20000,1),"work allowance covers coverage and model checks");
            int[] coverageWork={0};
            require(ContextUniversalFamilyCoverage.audit(f,r,ContextUniversalFamilyCoverage.Mode.FACTORED_STABLE_SHARED,16,() -> coverageWork[0]++).covered,"count complete independent family audit work");
            int justCoverage=coverageWork[0]+1;
            require(ContextUniversalFamilyCoverage.audit(f,r,ContextUniversalFamilyCoverage.Mode.FACTORED_STABLE_SHARED,16,justCoverage).covered,"chosen work allowance really completes coverage alone");
            require(m.stream().allMatch(v -> ContextUniversalQuotientModel.check(v.ledger,v,20000,justCoverage).valid),"same allowance suffices for each model alone");
            refused(ContextUniversalFamilyModel.certify(f,r,m,16,20000,justCoverage),"coverage and model validation cannot reset their shared work allowance");
            int firstInstances=r.get(0).closureStep.input.rows.size();
            require(ContextUniversalQuotientModel.check(m.get(0).ledger,m.get(0),firstInstances,WORK).valid,"self model alone fits exact instance allowance");
            refused(ContextUniversalFamilyModel.certify(f,r,m,16,firstInstances,WORK),"later cut consumes same instance allowance after successful self check");
            Snapshot rule=snapshot(true,"!@x p(x)->q(x);"),fact=snapshot(false,"!@x p(x);");
            Frontier growing=ContextUniversalUnitRounds.start(rule,Collections.singletonList(fact),WORK);
            List<Receipt> growingR=pass(growing); List<Model> growingM=models(growingR);
            refused(certify(growing,growingR,growingM),"individually modeled cuts do not excuse a changed common barrier");
            Barrier barrier=ContextUniversalUnitRounds.merge(growing,growingR,16,WORK);
            require(barrier.status==Status.MERGED && barrier.changed,"q really crosses common barrier");
            List<Receipt> stableR=pass(barrier.frontier); List<Model> stableM=models(stableR);
            ContextUniversalFamilyModel.Result stable=certify(barrier.frontier,stableR,stableM);
            require(stable.certified,"requalified stable generic consequence has whole-family model evidence: "+stable.reason);
            SemanticTermSnapshot fresh=value("UnmentionedFamilyValue");
            require(stable.certificate.cuts.stream().allMatch(c -> c.model.holds("q",Arrays.asList(fresh))),"qualified universal consequence holds in every private interpretation for an unmentioned value");
            refused(certify(barrier.frontier,stableR,growingM),"previous common-round models cannot substitute for requalification");
            Snapshot oppose=snapshot(false,"!~q(9);"); Frontier clash=ContextUniversalUnitRounds.start(rule,Arrays.asList(fact,oppose),WORK);
            List<Receipt> clashR=pass(clash); Barrier crossed=ContextUniversalUnitRounds.merge(clash,clashR,16,WORK);
            require(crossed.status==Status.MERGED && crossed.changed,"first clash barrier carries q");
            List<Receipt> collisionR=pass(crossed.frontier);
            refused(certify(crossed.frontier,collisionR,Collections.emptyList()),"actual cross-cut opposition cannot be certified");
            require(ContextUniversalUnitRounds.merge(crossed.frontier,collisionR,16,WORK).status==Status.COLLISION,"actual complete barrier proves the counterexample");
            boolean immutable=false; try { good.certificate.cuts.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"certificate binding list immutable");
            require(good.certificate.cuts.stream().allMatch(c -> c.model.ledger==c.receipt.closureStep.input && c.receipt.input==f),"every binding exact and complete");
            refused(certify(null,r,m),"missing frontier");
            System.out.println("CONTEXT_UNIVERSAL_FAMILY_MODEL_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
