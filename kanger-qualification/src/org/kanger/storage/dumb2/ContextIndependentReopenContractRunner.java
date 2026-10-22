/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.exception.StorageLifecycleException;

/** Desired durable-open contract: a certified, published consistent source must reopen. */
public final class ContextIndependentReopenContractRunner {
    static final int WORK=64000000;static int checks,failures;
    static final String RULE="!@x @y @z q(x,y,z)&&~p1(x)->out(x,y,z);";
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static void models(ContextUniversalFamilyInspection.Result r){
        require(r.status==ContextUniversalFamilyInspection.Status.CONSISTENT&&r.certificate!=null&&r.collision==null,"complete consistency before actual publication");
        ContextUniversalFamilyModel.Certificate c=r.certificate;require(c.cuts.size()==4,"minimal X plus three exact private cuts");
        List<ContextUniversalUnitRounds.Receipt> receipts=new ArrayList<>();List<ContextUniversalQuotientModel.Model> witnesses=new ArrayList<>();
        for(ContextUniversalFamilyModel.Cut cut:c.cuts){require(cut.receipt.verified&&cut.receipt.input==c.input&&cut.model.ledger==cut.receipt.closureStep.input,"exact verified private model binding");receipts.add(cut.receipt);witnesses.add(cut.model);}
        require(ContextUniversalFamilyModel.certify(c.input,receipts,witnesses,16,65536,WORK).certified,"independently recertify minimal family");
    }
    static void snapshot(Path path,long revision,Set<String> expected,List<RevisionRef> pins,ContextProductionNativeBinding.Snapshot expectedBinding)throws Exception{
        ContextSnapshotData data=new ContextSnapshotData(path,"snapshot",revision);User user=new User();data.init(user);Mind m=new Mind(user);user.setCurrentMind(m);m=(Mind)m.useStorage("snapshot");user.setCurrentMind(m);
        try{ContextProductionInventory inventory=ContextProductionInventory.inspect(m,data.getContextId(),data.getRevision(),true,Collections.emptyList(),1000);require(inventory.primary.size()==2&&ContextSevenPremiseNativeWriteRunner.origins(inventory).equals(expected),"exact durable original premise and whole candidate");require(ContextQualification.inspect(m,false).isValid(),"stored local theory itself remains valid");ContextProductionNativeBinding.Result bound=ContextProductionNativeBinding.bind(m,inventory,WORK,500000);require(bound.bound,"stored native primary bindings remain supported");require(bound.snapshot.primary.size()==expectedBinding.primary.size()&&bound.snapshot.primary.stream().allMatch(row->expectedBinding.primary.stream().anyMatch(original->original.source.rule==row.source.rule&&original.clauses.size()==row.clauses.size()&&original.clauses.size()==1&&original.clauses.get(0).equivalent(row.clauses.get(0)))),"stored native primary clauses exactly match semantically verified pre-close bindings");List<RevisionRef> actual=new ArrayList<>();for(ContextConnection connection:ConnectionStore.read(path,new RevisionRef(data.getContextId(),revision)).getConnections())actual.add(connection.getTarget());require(actual.equals(pins),"exact durable saved pin order");}finally{user.setCurrentMind(m.closeStorage());}
        try(ContextSnapshot frozen=ContextSnapshot.open(path)){require(frozen.getRevision()==revision,"failed ordinary open must not publish a revision");}
    }
    static void attempt(Path dir,String name,boolean nativePath)throws Exception{
        long revision;Set<String> origins;List<RevisionRef> pins;ContextProductionNativeBinding.Snapshot expectedBinding;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){
            require(f.root.compile("!~p1(1);",null,false),"one local premise");for(String target:new String[]{"A","Bridge","Deny"})f.db.connectContext(target);f.db.saveConnections(f.root);
            ContextUniversalCandidate.Evaluation diagnostic=f.db.qualifyUniversalProductionCandidate(f.root,RULE,1000,16,16,16,65536,1000000,WORK,500000,WORK);models(diagnostic.qualification);
            if(nativePath){ContextPendingNativeWrite scope=f.db.beginUniversalWrite(f.root,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK);try{require(Boolean.TRUE.equals(f.root.query(RULE,null,false)),"actual owning native scope accepts consistent candidate");}finally{f.db.endNativeWrite(scope);}}
            else{DB.UniversalProductionWriteResult result=f.db.writeUniversalProductionCandidate(f.root,RULE,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK,false);require(result.status==DB.ProductionWriteStatus.PUBLISHED&&result.audit!=null&&result.audit.verified&&result.trial!=diagnostic,"fresh audited actual public publication");models(result.trial.qualification);}
            PairQualification.CompositionQualification live=PairQualification.qualifyCompositionState(f.root,new ConnectionVector(f.db.dmzTopology()));require(live.isValid()&&live.getCollisions().isEmpty(),"legacy live composition also accepts before close");
            ContextProductionInventory inventory=ContextSevenPremiseNativeWriteRunner.inventory(f);origins=ContextSevenPremiseNativeWriteRunner.origins(inventory);require(origins.size()==2&&origins.contains(RULE)&&origins.contains("!~p1(1);"),"only source declarations committed");ContextProductionNativeBinding.Result bound=ContextProductionNativeBinding.bind(f.root,inventory,WORK,500000);require(bound.bound,"capture exact live native primary bindings");expectedBinding=bound.snapshot;revision=f.db.getRevision();pins=ContextSevenPremiseNativeWriteRunner.pins(f);
        }
        snapshot(dir.resolve(name),revision,origins,pins,expectedBinding);
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,name)){require(ContextSevenPremiseNativeWriteRunner.origins(ContextSevenPremiseNativeWriteRunner.inventory(f)).equals(origins)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"ordinary reopen preserves exact source and pins");}
        catch(StorageLifecycleException failure){++failures;System.out.println("REOPEN_CONTRACT_FAIL path="+name+" code="+failure.getCode());for(ContextQualification.CollisionWitness witness:failure.getCollisions())System.out.println("REOPEN_WITNESS "+witness.getLeft()+" <> "+witness.getRight());snapshot(dir.resolve(name),revision,origins,pins,expectedBinding);}
    }
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("independent-reopen-contract-");System.setProperty("user.home",dir.toString());ContextAuthorityChain.create(dir,"A","!@x @y @z q(x,y,z);");ContextAuthorityChain.create(dir,"Bridge","!@x @y @z out(x,y,z)->mid(x,y,z);");ContextAuthorityChain.create(dir,"Deny","!~mid(9,9,2);");
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"Flat")){require(f.root.compile("!~p1(1); "+RULE+" !@x @y @z q(x,y,z); !@x @y @z out(x,y,z)->mid(x,y,z); !~mid(9,9,2);",null,false),"same five primary declarations accept together");require(ContextQualification.inspect(f.root,false).isValid(),"same full union remains locally consistent");}
        attempt(dir,"Public",false);attempt(dir,"Native",true);System.out.println("INDEPENDENT_REOPEN_CONTRACT checks="+checks+" failures="+failures);if(failures>0)throw new AssertionError("Certified published consistent source failed ordinary reopen in "+failures+" paths");System.out.println("CONTEXT_INDEPENDENT_REOPEN_CONTRACT_PASS");System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
