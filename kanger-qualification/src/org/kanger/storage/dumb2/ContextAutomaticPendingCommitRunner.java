/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;
import org.kanger.interfaces.internal.IData;

/** Whole-family selection at actual native ingress; ordinary default remains off. */
public final class ContextAutomaticPendingCommitRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    static class Fixture extends ContextPendingWitnessWriteRunner.Fixture {
        final Guard guard;
        Fixture(Path dir,String name) throws Exception {super(dir,name);guard=new Guard(this);}
        void flat(boolean reverse) throws Exception {require(root.compile("!anchor(1);",null,false),"anchor");db.connectContext(reverse?"B":"A");db.connectContext(reverse?"A":"B");}
        @Override public void close() throws Exception {guard.restore();super.close();}
    }
    static class Guard {
        final Fixture f;int calls,rounds=16,models=1000000,audit=WORK;
        Guard(Fixture f) {this.f=f;}
        void install() {f.user.setData((IData)java.lang.reflect.Proxy.newProxyInstance(DB.class.getClassLoader(),DB.class.getInterfaces(),(proxy,method,args) -> {
            if(method.getName().equals("validateCommit")) {
                ++calls;synchronized(f.db) {f.user.setData(f.db);try {
                    f.db.validatePendingProductionCommit((Mind)args[0],1000,rounds,16,16,20000,models,WORK,500000,WORK,audit,100000);
                    f.db.validateCommit((org.kanger.interfaces.IMind)args[0]);
                } finally {f.user.setData((IData)proxy);}}return null;
            }
            try {return method.invoke(f.db,args);} catch(java.lang.reflect.InvocationTargetException error) {throw error.getCause();}
        }));}
        void restore() {f.user.setData(f.db);}
    }
    static StorageLifecycleException refuse(Fixture f,String text) throws Exception {
        StorageLifecycleException seen=null;try {f.root.query(text,null,false);} catch(StorageLifecycleException expected) {seen=expected;}
        require(seen!=null,"actual automatic native refusal");return seen;
    }
    static void select(Fixture f,String text,ContextPendingProductionSnapshotSession.Engine expected) throws Exception {
        Mind child=new Mind(f.root);require(child.compile(text,null,false),"prepare selection child");
        try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
            ContextPendingProductionSnapshotSession.Decision decision=lease.inspectAutomatic(16,16,16,20000,1000000,WORK,500000,WORK,WORK,100000);
            require(decision.engine==expected,"engine selected from complete native family: "+decision.engine);lease.requireCurrent();
        } finally {f.root.release(child);}
    }
    static void command(Fixture f,String text) throws Exception {new CanonicalCommandProcessor().execute(new CommandParser().parse(text),f.user);}
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("automatic-pending-policy-");System.setProperty("user.home",dir.toString());
            try(Fixture a=new Fixture(dir,"A")) {require(a.root.compile("!left(1); !p(1);",null,false),"masking A");}
            try(Fixture b=new Fixture(dir,"B")) {require(b.root.compile("!right(1); !~p(1);",null,false),"opposite B");}
            try(Fixture a=new Fixture(dir,"WA")) {require(a.root.compile("!@w seed(w,1)->q(w,1);",null,false),"witness foreign producer");}
            try(Fixture a=new Fixture(dir,"FX")) {require(a.root.compile("!@x fun(x)->next(x+1);",null,false),"legal native foreign function rule");}
            try(Fixture a=new Fixture(dir,"FW")) {require(a.root.compile("!$w seed(w,1)&&flag(w,1);",null,false),"foreign witness bundle");}
            try(Fixture f=new Fixture(dir,"ForeignWitnessChoice")) {
                require(f.root.compile("!anchor(1);",null,false),"ordinary X with foreign witness only");f.db.connectContext("FW");select(f,"!ordinary(1);",ContextPendingProductionSnapshotSession.Engine.WITNESS);
            }
            for(boolean universal:new boolean[]{false,true}) for(boolean reverse:new boolean[]{false,true}) try(Fixture f=new Fixture(dir,"Flat"+universal+reverse)) {
                f.flat(reverse);String rule=universal?"!@x left(x)&&right(x)->p(x);":"!left(1)&&right(1)->p(1);";
                select(f,rule,universal?ContextPendingProductionSnapshotSession.Engine.UNIVERSAL:ContextPendingProductionSnapshotSession.Engine.GROUND);
                long revision=f.db.getRevision();int rows=f.root.getRules().size();f.guard.install();StorageLifecycleException failure=refuse(f,rule);
                require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"masked rule rejected in both orders by selected engine");
                if(universal) require(failure instanceof ContextProductionCommitException && ((ContextProductionCommitException)failure).getFamilyAudit().verified,"universal independent audit retained");
                else require(failure instanceof ContextGroundProductionCommitException && ((ContextGroundProductionCommitException)failure).getFamilyEvaluation().rounds==2,"ground collision evidence retained");
                require(f.db.getRevision()==revision && f.root.getRules().size()==rows && f.guard.calls==1,"one pre-settlement refusal, no leaked source/product/revision");
                require(Boolean.TRUE.equals(f.root.query("!unrelated(1);",null,false)),"foreign sibling disagreement alone does not prevent safe ordinary write");
            }
            try(Fixture f=new Fixture(dir,"ForeignVariables")) {
                require(f.root.compile("!anchor(1);",null,false),"ground X");f.db.connectContext("WA");select(f,"!ordinary(1);",ContextPendingProductionSnapshotSession.Engine.UNIVERSAL);
                f.guard.install();require(Boolean.TRUE.equals(f.root.query("!ordinary(1);",null,false)),"foreign universal clause controls choice despite ground candidate/X");
            }
            try(Fixture f=new Fixture(dir,"Witness")) {
                f.base();select(f,"!@w q(w,1)->out(w,1);",ContextPendingProductionSnapshotSession.Engine.WITNESS);long revision=f.db.getRevision();f.guard.install();
                StorageLifecycleException collision=refuse(f,"!@w q(w,1)->~flag(w,1);");require(collision instanceof ContextWitnessProductionCommitException && collision.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT && !((ContextWitnessProductionCommitException)collision).getFamilyEvaluation().refutations.isEmpty(),"ordinary witness collision retains full clause refutation");
                require(f.db.getRevision()==revision,"witness refusal preserves physical revision");
                f.guard.rounds=1;StorageLifecycleException bounded=refuse(f,"!@w q(w,1)->out(w,1);");require(bounded instanceof ContextWitnessProductionCommitException && bounded.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"witness incomplete does not fall through to another engine");f.guard.rounds=16;
                require(Boolean.TRUE.equals(f.root.query("!@w q(w,1)->out(w,1);",null,false)),"ordinary witness candidate accepted by selected witness engine");
            }
            try(Fixture f=new Fixture(dir,"GroundLimit")) {
                f.flat(false);f.guard.models=1;f.guard.install();StorageLifecycleException bounded=refuse(f,"!p(1)||q(1);");
                require(bounded instanceof ContextGroundProductionCommitException && bounded.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && ((ContextGroundProductionCommitException)bounded).getFamilyEvaluation().reason.toLowerCase().contains("model"),"ground model incomplete does not fall back to universal engine");
            }
            try(Fixture f=new Fixture(dir,"UniversalLimit")) {
                f.flat(false);f.guard.audit=1;f.guard.install();StorageLifecycleException bounded=refuse(f,"!@x left(x)->safe(x);");
                require(bounded instanceof ContextProductionCommitException && bounded.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && ((ContextProductionCommitException)bounded).getFamilyAudit()!=null,"universal audit incomplete does not fall back to witness engine");
            }
            try(Fixture f=new Fixture(dir,"WholeForeign")) {
                f.flat(false);f.db.connectContext("FX");long revision=f.db.getRevision();f.guard.install();StorageLifecycleException incomplete=refuse(f,"!@x left(x)&&right(x)->p(x);");
                require(incomplete.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && incomplete.getCause()!=null && incomplete.getCause().getMessage().contains("binding incomplete"),"unsupported foreign row prevents any positive collision prefix or engine guess");require(f.db.getRevision()==revision,"whole foreign binding refusal does not write");
            }
            try(Fixture f=new Fixture(dir,"WholeWitness")) {
                f.base();require(f.root.compile("!@x all(x,1);",null,false),"native declaration outside witness productive coverage");f.guard.install();StorageLifecycleException incomplete=refuse(f,"!ordinary(1);");
                require(incomplete instanceof ContextWitnessProductionCommitException && incomplete.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"existing uncovered primary cannot be hidden behind ground new assertion");
            }
            try(Fixture f=new Fixture(dir,"User")) {
                f.flat(false);f.guard.install();command(f,"transaction start");Mind retained=(Mind)f.user.getCurrentMind();require(retained.query("!@x left(x)&&right(x)->p(x);",null,false),"prepare explicit user level");
                StorageLifecycleException failure=null;try {command(f,"transaction commit");} catch(StorageLifecycleException expected) {failure=expected;}
                require(failure!=null && failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT && f.user.getCurrentMind()==retained,"automatic rejection retains exact user child");f.root.requirePendingPhysicalChild(retained);
                command(f,"transaction rollback");require(f.user.getCurrentMind()==f.root,"automatic ground parent refresh allows rollback of universal refused child");
            }
            try(Fixture f=new Fixture(dir,"ExplicitLimits")) {
                f.flat(false);ContextPendingNativeWrite scope=f.db.beginGroundWrite(f.root,1000,1,16,16,WORK,100000,100000,1000000);f.guard.install();
                try {StorageLifecycleException incomplete=refuse(f,"!p(1)||q(1);");require(incomplete instanceof ContextGroundProductionCommitException && incomplete.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"automatic hook preserves explicit scope and its round bound");}
                finally {f.guard.restore();f.db.endNativeWrite(scope);}
            }
            try(Fixture f=new Fixture(dir,"NullaryCompatibility")) {
                f.flat(false);require(Boolean.TRUE.equals(f.root.query("!nativezero;",null,false)),"legal native nullary assertion accepted by unchanged legacy default");f.guard.install();
                StorageLifecycleException incomplete=refuse(f,"!ordinary(1);");require(incomplete.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && incomplete.getCause()!=null && incomplete.getCause().getMessage().contains("arity"),"concrete compatibility blocker: existing nullary primary is outside current whole binding");
                long id=-1;for(org.kanger.interfaces.IRule item:f.root.getRules()) if(!item.isGenerated() && !item.isDeleted(f.root) && item.getOrigin().equals("!nativezero;")) {id=item.getId();break;}
                require(id>=0 && Boolean.TRUE.equals(f.root.query("-rule("+id+");",null,false)),"unsupported existing primary can be deleted through strict whole-family policy");
                require(Boolean.TRUE.equals(f.root.query("!ordinary(1);",null,false)),"ordinary authoring recovers after unsupported primary is removed");
            }
            System.out.println("CONTEXT_AUTOMATIC_PENDING_COMMIT_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
