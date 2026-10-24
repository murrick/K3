/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;

/** Real ordinary provider ingress: no proxy, explicit attempt, or diagnostic permission. */
public final class ContextOrdinaryProductionCommitRunner {
    static int checks; static final int WORK=64000000;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static class Fixture extends ContextSevenPremiseNativeWriteRunner.Fixture {
        Fixture(Path dir,String name)throws Exception{super(dir,name);}
        void connect(boolean reverse)throws Exception{db.connectContext(reverse?"B":"A");db.connectContext(reverse?"A":"B");db.saveConnections(root);}
    }
    static StorageLifecycleException refuse(Mind mind,String text)throws Exception{try{mind.query(text,null,false);}catch(StorageLifecycleException e){return e;}throw new AssertionError("ordinary write unexpectedly accepted: "+text);}
    static Set<String> origins(Fixture f)throws Exception{return ContextSevenPremiseNativeWriteRunner.origins(ContextProductionInventory.inspect(f.root,f.db.getContextId(),f.db.getRevision(),true,Collections.emptyList(),1000));}
    static void command(Fixture f,String text)throws Exception{new CanonicalCommandProcessor().execute(new CommandParser().parse(text),f.user);}
    static void flat(Path dir,boolean reverse,boolean universal)throws Exception{
        String name="Ordinary"+reverse+universal,rule=universal?"!@x left(x)&&right(x)->p(x);":"!left(1)&&right(1)->p(1);";Set<String> settled;List<RevisionRef> pins;long published;
        try(Fixture f=new Fixture(dir,name)){
            require(f.user.getData()==f.db,"actual DB owns ordinary provider hook");require(f.root.compile("!anchor(1);",null,false),"standalone anchor");f.connect(reverse);
            ContextProductionInventory before=ContextSevenPremiseNativeWriteRunner.inventory(f);long rev=f.db.getRevision();int count=f.root.getRules().size();pins=ContextSevenPremiseNativeWriteRunner.pins(f);List<String> foreign;
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)){foreign=ContextForeignAntecedentQuantifier.fingerprints(lease.inventory());}
            StorageLifecycleException error=refuse(f.root,rule);require(error.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"ordinary masked admission refuses with collision evidence");
            if(universal)require(error instanceof ContextProductionCommitException&&((ContextProductionCommitException)error).getFamilyAudit().verified&&!error.getCollisions().isEmpty(),"ordinary universal refusal independently audited");
            else require(error instanceof ContextGroundProductionCommitException&&((ContextGroundProductionCommitException)error).getFamilyEvaluation().rounds==2,"ordinary ground refusal reaches second common barrier");
            require(f.db.getRevision()==rev&&f.root.getRules().size()==count&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(before.fingerprint),"ordinary refusal exact physical purity");require(ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"ordinary refusal pins unchanged");
            require(Boolean.TRUE.equals(f.root.query("!unrelated(1);",null,false)),"independent conflicting siblings permit safe write");settled=origins(f);require(settled.equals(new HashSet<>(Arrays.asList("!anchor(1);","!unrelated(1);"))),"no foreign raw/product primary imported");published=f.db.getRevision();require(published>rev,"safe ordinary write advances revision");
            try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)){require(foreign.equals(ContextForeignAntecedentQuantifier.fingerprints(lease.inventory())),"ordinary write leaves initialized foreign stores unchanged");}
        }
        try(Fixture f=new Fixture(dir,name)){require(f.db.getRevision()>=published&&origins(f).equals(settled)&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"ordinary reopen exact settled origins and pins");require(Boolean.TRUE.equals(f.root.query("?unrelated(1);",null,false)),"safe ordinary result persists");refuse(f.root,rule);require(origins(f).equals(settled),"reopened ordinary refusal remains pure");}
    }
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("ordinary-production-commit-");System.setProperty("user.home",dir.toString());
        ContextSevenPremiseNativeWriteRunner.create(dir,"A","!left(1); !p(1);");ContextSevenPremiseNativeWriteRunner.create(dir,"B","!right(1); !~p(1);");ContextSevenPremiseNativeWriteRunner.create(dir,"WA","!@w seed(w,1)->q(w,1);");ContextSevenPremiseNativeWriteRunner.create(dir,"FX","!@x fun(x)->next(x+1);");
        for(boolean r:new boolean[]{false,true})for(boolean u:new boolean[]{false,true})flat(dir,r,u);
        System.out.println("ORDINARY_ADMISSION_CONTROLS_PASSED checks="+checks);
        try(Fixture f=new Fixture(dir,"User")){
            require(f.root.compile("!anchor(1);",null,false),"transaction anchor");f.connect(false);long rev=f.db.getRevision();Set<String> before=origins(f);command(f,"transaction start");Mind child=(Mind)f.user.getCurrentMind();require(child.query("!@x left(x)&&right(x)->p(x);",null,false),"retain proposed user rule");StorageLifecycleException rejection=null;try{command(f,"transaction commit");}catch(StorageLifecycleException e){rejection=e;}
            require(rejection instanceof ContextProductionCommitException&&rejection.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT&&f.user.getCurrentMind()==child,"ordinary rejected user commit retains exact editable child");f.root.requirePendingPhysicalChild(child);require(f.db.getRevision()==rev&&origins(f).equals(before),"ordinary rejected user commit leaves parent unchanged");command(f,"transaction rollback");require(f.user.getCurrentMind()==f.root&&origins(f).equals(before),"ordinary rollback restores parent without publishing child");
            command(f,"transaction start");Mind safe=(Mind)f.user.getCurrentMind();require(safe.query("!safe(1);",null,false),"safe explicit child");command(f,"transaction commit");require(f.user.getCurrentMind()==f.root&&origins(f).contains("!safe(1);"),"ordinary safe explicit commit settles");
        }
        System.out.println("ORDINARY_USER_TRANSACTION_CONTROLS_PASSED checks="+checks);
        try(ContextPendingWitnessWriteRunner.Fixture f=new ContextPendingWitnessWriteRunner.Fixture(dir,"Witness")){
            f.base();long rev=f.db.getRevision();StorageLifecycleException failure=refuse(f.root,"!@w q(w,1)->~flag(w,1);");require(failure instanceof ContextWitnessProductionCommitException&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT&&f.db.getRevision()==rev,"ordinary default selects witness collision engine");require(Boolean.TRUE.equals(f.root.query("!@w q(w,1)->out(w,1);",null,false)),"ordinary default publishes covered witness rule");
        }
        System.out.println("ORDINARY_WITNESS_CONTROLS_PASSED checks="+checks);
        try(Fixture f=new Fixture(dir,"Unsupported")){
            require(f.root.compile("!anchor(1);",null,false),"unsupported baseline");f.connect(false);f.db.connectContext("FX");long rev=f.db.getRevision();String fingerprint=ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint;StorageLifecycleException failure=refuse(f.root,"!@x left(x)&&right(x)->p(x);");require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&failure.getCollisions().isEmpty()&&failure.getCause()!=null,"unsupported foreign primary refuses whole request before collision prefix");require(f.db.getRevision()==rev&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(fingerprint),"unsupported ordinary write leaves exact source unchanged");
        }
        try(Fixture f=new Fixture(dir,"ExplicitLimit")){
            require(f.root.compile("!anchor(1);",null,false),"explicit limit anchor");f.connect(false);ContextPendingNativeWrite scope=f.db.beginGroundWrite(f.root,1000,1,16,16,WORK,100000,100000,1000000);try{StorageLifecycleException failure=refuse(f.root,"!p(1)||q(1);");require(failure instanceof ContextGroundProductionCommitException&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"ordinary hook preserves explicit attempt engine and smaller round limit");}finally{f.db.endNativeWrite(scope);}
        }
        System.out.println("CONTEXT_ORDINARY_PRODUCTION_COMMIT_PASS checks="+(checks+ContextSevenPremiseNativeWriteRunner.checks));System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
