/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextUniversalFamilyInspection.*;

/** The second conjunctive result branch independently participates in qualification. */
public final class ContextAuthorityConjunctiveTailControlRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean yes,String why){++checks;if(!yes)throw new AssertionError(why);}
    static void one(boolean conflict,boolean negative,boolean reverse)throws Exception {
        Path dir=Files.createTempDirectory("conjunctive-tail-control-");System.setProperty("user.home",dir.toString());String sign=negative?"~":"";
        ContextAuthorityChain.create(dir,"Donor","!@x @y @z "+sign+"q(x,y,z);");
        ContextAuthorityChain.create(dir,"Bridge","!@x @y @z "+sign+"out(x,y,z)->"+sign+"mid(2)&&"+sign+"tail(x);");
        ContextAuthorityChain.create(dir,"Deny","!"+(negative?"":"~")+(conflict?"tail(1);":"tail(9);"));
        String text="!@x @y @z "+sign+"q(x,y,z)&&~p1(x)->"+sign+"out(x,y,z);";
        Set<String> origins;List<RevisionRef> pins;long settled;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile("!~p1(1);",null,false),"original premise");List<String> names=new ArrayList<>(Arrays.asList("Donor","Bridge","Deny"));if(reverse)Collections.reverse(names);for(String name:names)f.db.connectContext(name);f.db.saveConnections(f.root);ContextProductionInventory before=ContextSevenPremiseNativeWriteRunner.inventory(f);long revision=f.db.getRevision();pins=ContextSevenPremiseNativeWriteRunner.pins(f);
            ContextUniversalCandidate.Evaluation e=f.db.qualifyUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK);
            require(e.status==(conflict?Status.COLLISION:Status.CONSISTENT),"tail branch independently settles: "+e.reason);
            Entry bridge=e.artifact.foreign.stream().flatMap(c->c.primary.stream()).filter(row->row.clauses.size()==2).findFirst().get();require(bridge.clauses.size()==2,"both clauses have one whole Bridge Source");
            if(conflict){require(e.qualification.collision!=null&&e.qualification.certificate==null&&e.qualification.collision.oppositions.size()==2,"q and tail complete oppositions");Set<String> endpoints=new HashSet<>();for(ContextUniversalUnitProof.Proof p:e.qualification.collision.oppositions){require(p.dependsOnX&&p.contradiction&&p.support.depth==4&&p.support.sources.size()==5&&p.support.sources.contains(bridge.source)&&endpoints.add(p.left.literals.get(0).predicate),"tail refutation retains whole Bridge and cumulative depth4");}require(endpoints.equals(new HashSet<>(Arrays.asList("q","tail"))),"second head branch is refuted alongside donor");}
            else{
                ContextUniversalFamilyModel.Certificate cert=e.qualification.certificate;require(cert!=null&&e.qualification.collision==null&&e.qualification.completedBarriers==4,"nonmatching tail Deny has stable model after displaced premise round");
                Source donor=e.artifact.foreign.stream().flatMap(c->c.primary.stream()).filter(row->row.clauses.size()==1&&row.clauses.get(0).literals.get(0).predicate.equals("q")).findFirst().get().source;
                Set<Source> expected=new HashSet<>();for(Entry row:e.artifact.staged.primary)expected.add(row.source);expected.add(donor);expected.add(bridge.source);Map<String,Set<Source>> supports=new HashMap<>();for(ContextUniversalUnitLedger.Row row:cert.input.products)if(row.clause.literals.size()==1){String p=row.clause.literals.get(0).predicate;if(p.equals("mid")||p.equals("tail")){require(row.support.depth==3&&row.support.sources.equals(expected)&&supports.put(p,row.support.sources)==null,"both products share exact same whole4-source support");}}require(supports.size()==2,"both result branches actually exported");
                List<ContextUniversalUnitRounds.Receipt> receipts=new ArrayList<>();List<ContextUniversalQuotientModel.Model> models=new ArrayList<>();for(ContextUniversalFamilyModel.Cut cut:cert.cuts){receipts.add(cut.receipt);models.add(cut.model);}require(ContextUniversalFamilyModel.certify(cert.input,receipts,models,16,65536,WORK).certified,"independent tail-compatible model recheck");
            }
            require(f.db.getRevision()==revision&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(before.fingerprint),"diagnostic preserves physical theory");
            DB.UniversalProductionWriteResult w=f.db.writeUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK,false);require(w.status==(conflict?DB.ProductionWriteStatus.COLLISION:DB.ProductionWriteStatus.PUBLISHED)&&w.trial!=e&&w.audit!=null&&w.audit.verified,"fresh actual-child tail settlement audit");ContextProductionInventory after=ContextSevenPremiseNativeWriteRunner.inventory(f);origins=ContextSevenPremiseNativeWriteRunner.origins(after);settled=f.db.getRevision();require(after.primary.size()==(conflict?1:2)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"no product/foreign primary import and exact topology");if(conflict)require(settled==revision&&after.fingerprint.equals(before.fingerprint),"tail refusal retains exact physical state");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){ContextProductionInventory inv=ContextSevenPremiseNativeWriteRunner.inventory(f);require(inv.primary.size()==(conflict?1:2)&&ContextSevenPremiseNativeWriteRunner.origins(inv).equals(origins)&&f.db.getRevision()>=settled&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"tail settlement survives ordinary durable reopen");}
    }
    public static void main(String[] args){try{for(boolean c:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})one(c,n,r);System.out.println("CONTEXT_AUTHORITY_CONJUNCTIVE_TAIL_CONTROL_PASS checks="+(checks+ContextAuthorityChain.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
