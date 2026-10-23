/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalFamilyInspection.*;

/** Matching Bridge input cannot conflict with a Deny carrying the wrong head literal. */
public final class ContextAuthorityDistinctHeadControlRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean yes,String why){++checks;if(!yes)throw new AssertionError(why);}
    static void one(boolean negative,boolean reverse)throws Exception {
        Path dir=Files.createTempDirectory("distinct-head-control-");System.setProperty("user.home",dir.toString());String sign=negative?"~":"";
        ContextAuthorityChain.create(dir,"Donor","!@x @y @z "+sign+"q(x,y,z);");
        ContextAuthorityChain.create(dir,"Bridge","!@x @z "+sign+"out(x,9,z)->"+sign+"mid(z,2,x);");
        ContextAuthorityChain.create(dir,"Deny","!"+(negative?"":"~")+"mid(9,9,1);");
        String text="!@x @y @z "+sign+"q(x,y,z)&&~p1(x)->"+sign+"out(x,y,z);";
        Set<String> origins;List<RevisionRef> pins;long settled;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")) {
            require(f.root.compile("!~p1(1);",null,false),"original premise");List<String> names=new ArrayList<>(Arrays.asList("Donor","Bridge","Deny"));if(reverse)Collections.reverse(names);for(String name:names)f.db.connectContext(name);f.db.saveConnections(f.root);
            ContextProductionInventory before=ContextSevenPremiseNativeWriteRunner.inventory(f);long revision=f.db.getRevision();pins=ContextSevenPremiseNativeWriteRunner.pins(f);
            ContextUniversalCandidate.Evaluation e=f.db.qualifyUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK);
            require(e.status==Status.CONSISTENT&&e.qualification.collision==null&&e.qualification.certificate!=null,"wrong head literal must be compatible: "+e.reason);
            Atom head=e.artifact.foreign.stream().flatMap(c->c.primary.stream()).flatMap(row->row.clauses.stream()).filter(c->c.literals.size()==2).flatMap(c->c.literals.stream()).filter(a->a.predicate.equals("mid")).findFirst().get();
            Atom deny=e.artifact.foreign.stream().flatMap(c->c.primary.stream()).flatMap(row->row.clauses.stream()).filter(c->c.literals.size()==1).flatMap(c->c.literals.stream()).filter(a->a.predicate.equals("mid")).findFirst().get();
            require(head.arguments.get(1).variable<0&&deny.arguments.get(1).variable<0&&!head.arguments.get(1).value.semanticallyEquals(deny.arguments.get(1).value),"actual native head and Deny middle literals differ");
            int mid=0;for(ContextUniversalUnitLedger.Row row:e.qualification.certificate.input.products)if(row.clause.literals.size()==1){Atom a=row.clause.literals.get(0);require(!a.predicate.equals("p1"),"wrong head Deny cannot derive displaced p1");if(a.predicate.equals("mid")){++mid;require(row.clause.variables.size()==1&&a.arguments.size()==3&&a.arguments.get(0).variable>=0&&a.arguments.get(1).variable<0&&a.arguments.get(1).value.semanticallyEquals(head.arguments.get(1).value)&&a.arguments.get(2).variable<0&&a.arguments.get(2).value.semanticallyEquals(deny.arguments.get(2).value)&&row.support.depth==3&&row.support.sources.size()==4,"actual exported mid keeps head literal, fixed last value and whole depth3 support");}}require(mid==1,"Bridge actually fires despite nonmatching Deny");
            ContextUniversalFamilyModel.Certificate cert=e.qualification.certificate;List<ContextUniversalUnitRounds.Receipt> receipts=new ArrayList<>();List<ContextUniversalQuotientModel.Model> models=new ArrayList<>();for(ContextUniversalFamilyModel.Cut cut:cert.cuts){receipts.add(cut.receipt);models.add(cut.model);}require(ContextUniversalFamilyModel.certify(cert.input,receipts,models,16,65536,WORK).certified,"independent all-values certificate recheck");
            require(f.db.getRevision()==revision&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(before.fingerprint),"diagnostic preserves physical state");
            DB.UniversalProductionWriteResult w=f.db.writeUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK,false);
            require(w.status==DB.ProductionWriteStatus.PUBLISHED&&w.trial!=e&&w.audit!=null&&w.audit.verified,"fresh writer publishes with independently audited trial");ContextProductionInventory after=ContextSevenPremiseNativeWriteRunner.inventory(f);origins=ContextSevenPremiseNativeWriteRunner.origins(after);settled=f.db.getRevision();require(after.primary.size()==2&&origins.containsAll(ContextSevenPremiseNativeWriteRunner.origins(before))&&settled>revision&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"only whole candidate added with exact pins");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(inv.primary.size()==2&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(origins)&&f.db.getRevision()>=settled&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"ordinary durable reopen retains exact origins and pins");}
    }
    public static void main(String[] args){try{for(boolean n:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})one(n,r);System.out.println("CONTEXT_AUTHORITY_DISTINCT_HEAD_CONTROL_PASS checks="+(checks+ContextAuthorityChain.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
