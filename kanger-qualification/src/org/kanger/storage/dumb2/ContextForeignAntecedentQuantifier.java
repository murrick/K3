/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFamilyInspection.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.ContextProductionCommitException;

/** Whole unsupported foreign declarations block the family; supported neighbours remain usable. */
final class ContextForeignAntecedentQuantifier {
    static final int WORK=64000000;static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    interface Action {void run()throws Exception;}
    static void unsupported(Action action)throws Exception{IllegalStateException failure=null;try{action.run();}catch(IllegalStateException caught){failure=caught;}require(failure!=null&&failure.getMessage().contains("Foreign native binding incomplete")&&failure.getMessage().contains("conditional witness"),"foreign binding stops explicitly, never returns a partial verdict");}
    static List<String> fingerprints(ContextProductionSnapshotSession.Inventory inv){List<String> result=new ArrayList<>();for(ContextProductionInventory f:inv.foreign)result.add(f.fingerprint);return result;}
    static void verdict(ContextUniversalCandidate.Evaluation e,boolean safe,String bridge,String deny,boolean exactLocal){
        require(e.status==(safe?Status.CONSISTENT:Status.COLLISION),"supported control verdict: "+e.status+" / "+e.reason);
        if(safe){require(e.qualification.collision==null&&e.qualification.certificate!=null&&e.qualification.certificate.cuts.size()==4,"all four supported cuts have a complete family model");return;}
        collision(e.qualification,e.artifact.foreign,bridge,deny,exactLocal?e.artifact.staged:null);
    }
    static void collision(Result r,List<Snapshot> foreign,String bridge,String deny,Snapshot staged){
        require(r.status==Status.COLLISION&&r.certificate==null&&r.collision!=null&&!r.collision.oppositions.isEmpty(),"supported control has a complete contradiction");Set<Source> expected=new HashSet<>();for(Snapshot f:foreign)for(Entry entry:f.primary)if(entry.statement.equals(bridge)||entry.statement.equals(deny))expected.add(entry.source);require(expected.size()==2,"exact whole Bridge and Deny Sources");
        for(ContextUniversalUnitProof.Proof p:r.collision.oppositions){Set<Source> locals=new HashSet<>(),others=new HashSet<>();for(Source s:p.support.sources)(s.localX?locals:others).add(s);require(p.dependsOnX&&p.contradiction&&p.conclusion==null&&locals.size()==1&&others.equals(expected)&&p.support.sources.size()==3,"proof uses candidate and exact whole foreign Bridge/Deny only");if(staged!=null)require(staged.primary.stream().anyMatch(e->e.statement.contains("p(1)")&&locals.contains(e.source)),"exact staged candidate Source, no unrelated original p2");}
    }
    static void one(boolean negative,boolean reverse,boolean nativePath,boolean nested,boolean safe)throws Exception{
        Path dir=Files.createTempDirectory("foreign-antecedent-quantifier-");System.setProperty("user.home",dir.toString());String s=negative?"~":"",op=negative?"":"~";
        String bridge=nested?"!(@x "+s+"p(x))->"+s+"q();":"!@x "+s+"p(x)->"+s+"q();",deny="!"+op+"q();",text=safe?"!safe(1);":"!"+s+"p(1);";
        ContextSevenPremiseNativeWriteRunner.create(dir,"Bridge",bridge);ContextSevenPremiseNativeWriteRunner.create(dir,"Deny",deny);ContextSevenPremiseNativeWriteRunner.create(dir,"Other","!other(2);");
        Set<String> settled;List<RevisionRef> pins;long revision,afterRevision;boolean publish=!nested&&safe;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){
            require(f.root.compile("!"+op+"p(2);",null,false),"original local opposite-p2, no producer p1");
            for(String name:reverse?new String[]{"Other","Deny","Bridge"}:new String[]{"Bridge","Deny","Other"})f.db.connectContext(name);f.db.saveConnections(f.root);
            ContextProductionInventory before=ContextSevenPremiseNativeWriteRunner.inventory(f);Set<String> origins=ContextSevenPremiseNativeWriteRunner.origins(before);pins=ContextSevenPremiseNativeWriteRunner.pins(f);revision=f.db.getRevision();int rules=f.root.getRules().size();List<String> foreignPrints;
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)){
                ContextProductionSnapshotSession.Inventory inv=lease.inventory();foreignPrints=fingerprints(inv);require(inv.foreign.size()==3&&inv.foreign.stream().allMatch(i->i.primary.size()==1),"all three private whole primary Sources present");
                Set<String> all=new HashSet<>();for(ContextProductionInventory i:inv.foreign)all.addAll(ContextSevenPremiseNativeWriteRunner.origins(i));require(all.equals(new HashSet<>(Arrays.asList(bridge,deny,"!other(2);"))),"unsupported whole rule never omitted from inventory");
                if(nested)unsupported(()->lease.bind(WORK,500000));else require(lease.bind(WORK,500000).foreign.size()==3,"supported whole-family binding retains every private cut");lease.requireCurrent();
            }
            ContextUniversalCandidate.Evaluation diagnostic=null;
            if(nested)unsupported(()->f.db.qualifyUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK));
            else{diagnostic=f.db.qualifyUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK);verdict(diagnostic,safe,bridge,deny,true);}
            require(f.db.getRevision()==revision&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(before.fingerprint),"diagnostic leaves exact physical state unchanged");
            if(nativePath){ContextPendingNativeWrite attempt=f.db.beginUniversalWrite(f.root,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK);try{
                ContextProductionCommitException failure=null;try{f.root.query(text,null,false);}catch(ContextProductionCommitException caught){failure=caught;}
                if(publish)require(failure==null,"supported safe native publication succeeds");else{require(failure!=null&&failure.getErrorCode()==(nested?StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE:StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT),"native whole-family refusal has exact typed code");if(nested)require(failure.toString().contains("Pending foreign binding incomplete")&&failure.toString().contains("conditional witness")&&failure.getFamilyAudit()==null,"incomplete native foreign binding has no misleading conflict audit");else{require(failure.getFamilyAudit()!=null&&failure.getFamilyAudit().verified&&failure.getFamilyAudit().verdict!=diagnostic.qualification,"native control conflict freshly audited");collision(failure.getFamilyAudit().verdict,diagnostic.artifact.foreign,bridge,deny,null);}}
            }finally{f.db.endNativeWrite(attempt);}}
            else if(nested)unsupported(()->f.db.writeUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK,false));
            else{DB.UniversalProductionWriteResult result=f.db.writeUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK,false);require(result.status==(safe?DB.ProductionWriteStatus.PUBLISHED:DB.ProductionWriteStatus.COLLISION)&&result.trial!=diagnostic&&result.audit!=null&&result.audit.verified&&result.audit.evaluation==result.trial,"public supported control has fresh exact verified trial");verdict(result.trial,safe,bridge,deny,true);require(result.before.getRevision()==revision&&result.after.getRevision()==f.db.getRevision(),"actual public physical references");}
            ContextProductionInventory after=ContextSevenPremiseNativeWriteRunner.inventory(f);settled=ContextSevenPremiseNativeWriteRunner.origins(after);afterRevision=f.db.getRevision();Set<String> expected=new HashSet<>(origins);if(publish)expected.add(text);
            require(settled.equals(expected)&&after.primary.size()==expected.size()&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"only safe candidate may publish, foreign/raw/derived rules never imported");
            if(publish)require(afterRevision>revision,"supported safe physical publication advances revision");else require(afterRevision==revision&&f.root.getRules().size()==rules&&after.fingerprint.equals(before.fingerprint),"every refusal preserves exact revision/rules/fingerprint");
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)){require(fingerprints(lease.inventory()).equals(foreignPrints),"all initialized foreign inventories unchanged after settlement");}
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){require(f.db.getRevision()>=afterRevision&&ContextSevenPremiseNativeWriteRunner.origins(ContextSevenPremiseNativeWriteRunner.inventory(f)).equals(settled)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"ordinary durable reopen retains exact settled local origins and ordered pins");try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)){if(nested)unsupported(()->lease.bind(WORK,500000));else require(lease.bind(WORK,500000).foreign.size()==3,"supported foreign capture survives reopen");}}
    }
    static void run(boolean negative,String marker){try{for(boolean r:new boolean[]{false,true})for(boolean p:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})for(boolean s:new boolean[]{false,true})one(negative,r,p,n,s);System.out.println(marker+" checks="+(checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
