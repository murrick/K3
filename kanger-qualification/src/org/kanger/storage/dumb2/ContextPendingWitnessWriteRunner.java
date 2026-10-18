/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.*;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;
import org.kanger.interfaces.internal.IData;

/** Native witness requalification and attempt lifetime, including strict-hook routing. */
public final class ContextPendingWitnessWriteRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    interface Action {void run() throws Exception;}
    static void reject(Action action,String why) throws Exception {boolean refused=false;try {action.run();} catch(IllegalStateException|StorageLifecycleException expected) {refused=true;}require(refused,why);}
    static class Fixture implements AutoCloseable {
        final User user;final DB db=new DB();final Mind root;
        Fixture(Path dir,String name) throws Exception {this(dir,name,new User());}
        Fixture(Path dir,String name,User u) throws Exception {user=u;user.setDatabaseDir(dir.toString()+File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);}
        void base() throws Exception {require(root.compile("!$w seed(w,1)&&flag(w,1); !@w seed(w,1)->warm(w,1);",null,false),"native witness base");db.connectContext("WA");}
        public void close() throws Exception {user.setData(db);if(user.getCurrentMind().isStorageUsed()) user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    static final class FailUser extends User {
        boolean fail;int failures;final IOException injected=new IOException("witness native committed finalization");
        FailUser() throws Exception {super();}
        @Override public void flush() throws Exception {super.flush();if(fail) {fail=false;++failures;throw injected;}}
    }
    static ContextWitnessProductionCommitException refuse(Fixture f,String statement) throws Exception {
        ContextWitnessProductionCommitException seen=null;try {f.root.query(statement,null,false);} catch(ContextWitnessProductionCommitException expected) {seen=expected;}
        require(seen!=null,"typed actual pending witness refusal");return seen;
    }
    static void strictProxy(Fixture f,int[] calls) {
        f.user.setData((IData)java.lang.reflect.Proxy.newProxyInstance(DB.class.getClassLoader(),DB.class.getInterfaces(),(proxy,method,args) -> {
            if(method.getName().equals("validateCommit")) {
                ++calls[0];synchronized(f.db) {f.user.setData(f.db);try {f.db.validatePendingUniversalCommit((Mind)args[0]);f.db.validateCommit((org.kanger.interfaces.IMind)args[0]);} finally {f.user.setData((IData)proxy);}}return null;
            }
            try {return method.invoke(f.db,args);} catch(java.lang.reflect.InvocationTargetException error) {throw error.getCause();}
        }));
    }
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("pending-witness-write-");System.setProperty("user.home",dir.toString());
            String out="!@w q(w,1)->out(w,1);",bad="!@w q(w,1)->~flag(w,1);";
            try(Fixture a=new Fixture(dir,"WA")) {require(a.root.compile("!@w seed(w,1)->q(w,1);",null,false),"foreign producer");}
            long published;
            try(Fixture f=new Fixture(dir,"Writer")) {
                f.base();Mind child=new Mind(f.root);require(child.compile(out,null,false),"prepare same native candidate outside scope");
                boolean flat=false;try {f.db.validatePendingUniversalCommit(child);} catch(ContextProductionCommitException expected) {flat=expected.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE;}
                require(flat,"ordinary flat adapter still refuses unsupported witness family");f.root.release(child);
                DB.ProductionWriteResult result=f.db.writeProductionCandidate(f.root,out,1000,12,16,WORK,100000,false);
                require(result.status==DB.ProductionWriteStatus.PUBLISHED && result.after.getRevision()>result.before.getRevision(),"real writer publishes after actual pending witness requalification");published=f.db.getRevision();
            }
            try(Fixture f=new Fixture(dir,"Writer");ContextProductionSnapshotSession view=ContextProductionSnapshotSession.open(f.db,f.root,1000)) {
                require(f.db.getRevision()>=published && view.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("->out")),"native source persists across reopen");
                require(view.inventory().x.primary.stream().noneMatch(r -> r.origin.startsWith("!out(")),"foreign-dependent products are not new primary assertions");
            }
            try(Fixture f=new Fixture(dir,"Strict")) {
                f.base();ContextPendingWitnessWrite attempt=f.db.beginWitnessWrite(f.root,1000,12,16,WORK,100000);int[] calls={0};strictProxy(f,calls);
                try {require(Boolean.TRUE.equals(f.root.query(out,null,false)),"strict universal hook routes only owned attempt through fresh witness engine");require(calls[0]==1,"one original native commit, no retry or recursive writer");}
                finally {f.user.setData(f.db);f.db.endWitnessWrite(attempt);}
                Mind fresh=new Mind(f.root);require(fresh.compile("!safe(1);",null,false),"prepare after scope closes");
                boolean flat=false;try {f.db.validatePendingUniversalCommit(fresh);} catch(ContextProductionCommitException expected) {flat=true;}
                require(flat,"closed attempt does not leave a general witness admission mode");f.root.release(fresh);
            }
            try(Fixture f=new Fixture(dir,"Collision")) {
                f.base();long revision=f.db.getRevision();int rows=f.root.getRules().size();ContextPendingWitnessWrite attempt=f.db.beginWitnessWrite(f.root,1000,12,16,WORK,100000);
                try {ContextWitnessProductionCommitException failure=refuse(f,bad);require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT && failure.getFamilyEvaluation().status==ContextProductionFamilyAssembly.Status.COLLISION,"actual pending collision classified separately");
                    require(failure.getFamilyEvaluation().barrier>0 && !failure.getFamilyEvaluation().refutations.isEmpty() && !failure.getFamilyEvaluation().refutations.get(0).proofs.isEmpty(),"full witness assignment/opponents/Source proof diagnostics preserved");}
                finally {f.db.endWitnessWrite(attempt);}
                require(f.db.getRevision()==revision && f.root.getRules().size()==rows,"refused pending candidate leaves parent/revision unchanged");
            }
            try(Fixture f=new Fixture(dir,"Incomplete")) {
                f.base();ContextPendingWitnessWrite attempt=f.db.beginWitnessWrite(f.root,1000,1,16,WORK,100000);Mind child=new Mind(f.root);require(child.compile(out,null,false),"bounded pending proposal");
                try {ContextWitnessProductionCommitException failure=null;try {f.db.validatePendingUniversalCommit(child);} catch(ContextWitnessProductionCommitException expected) {failure=expected;}
                    require(failure!=null && failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"bounded witness closure cannot authorize commit");reject(() -> f.db.validatePendingUniversalCommit(child),"same live child cannot reuse refused attempt");}
                finally {f.root.release(child);f.db.endWitnessWrite(attempt);}
            }
            try(Fixture f=new Fixture(dir,"Lifetime")) {
                f.base();ContextPendingWitnessWrite attempt=f.db.beginWitnessWrite(f.root,1000,12,16,WORK,100000);Mind child=new Mind(f.root);require(child.compile(out,null,false),"first owned pending child");
                try {reject(() -> f.db.beginWitnessWrite(f.root,1000,12,16,WORK,100000),"nested attempt cannot replace the owning scope");f.db.validatePendingUniversalCommit(child);f.db.validatePendingUniversalCommit(child);require(true,"same live immutable child can be rechecked without new native settlement");
                    final boolean[] refused={false};Thread other=new Thread(() -> {try {attempt.validate(child);} catch(Exception expected) {refused[0]=true;}});other.start();other.join();require(refused[0],"attempt cannot move to another thread");
                    f.root.release(child);Mind replacement=new Mind(f.root);try {require(replacement.compile(out,null,false),"replacement pending child");reject(() -> f.db.validatePendingUniversalCommit(replacement),"released child/replacement cannot reuse complete old qualification");} finally {f.root.release(replacement);}}
                finally {f.db.endWitnessWrite(attempt);}
                reject(() -> attempt.validate(child),"closed attempt cannot be reused");
            }
            try(Fixture f=new Fixture(dir,"ChangedPending")) {
                f.base();ContextPendingWitnessWrite attempt=f.db.beginWitnessWrite(f.root,1000,12,16,WORK,100000);Mind child=new Mind(f.root);
                try {require(child.compile(out,null,false),"pending proposal before requalification");f.db.validatePendingUniversalCommit(child);
                    require(child.compile("!changed(1);",null,false),"controlled edit after full pending witness qualification");
                    reject(() -> f.db.validatePendingUniversalCommit(child),"changed actual proposal cannot reuse cached qualification");}
                finally {f.root.release(child);f.db.endWitnessWrite(attempt);}
            }
            for(boolean reconnect:new boolean[]{false,true}) try(Fixture f=new Fixture(dir,"Stale"+reconnect)) {
                f.base();ContextPendingWitnessWrite attempt=f.db.beginWitnessWrite(f.root,1000,12,16,WORK,100000);
                try {if(reconnect) {f.db.disconnectContext("WA");f.db.connectContext("WA");} else f.db.applyConnectionCommand(f.root,"WA","!private(4);");
                    ContextWitnessProductionCommitException failure=refuse(f,out);require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && failure.getCause()!=null,"changed initialized view or same-metadata connection replacement refuses old handoff");}
                finally {f.db.endWitnessWrite(attempt);}
            }
            FailUser user=new FailUser();try(Fixture f=new Fixture(dir,"Failure",user)) {f.base();user.fail=true;TransactionSettlementException failure=null;
                try {f.db.writeProductionCandidate(f.root,out,1000,12,16,WORK,100000,false);} catch(TransactionSettlementException expected) {failure=expected;}
                require(failure!=null && failure.getOutcome()==TransactionSettlementException.Outcome.COMMITTED && failure.getCause()==user.injected && user.failures==1,"real writer preserves COMMITTED/original cause and never retries native commit");}
            try(Fixture f=new Fixture(dir,"Failure");ContextProductionSnapshotSession view=ContextProductionSnapshotSession.open(f.db,f.root,1000)) {require(view.inventory().x.primary.stream().anyMatch(r -> r.origin.contains("->out")),"post-durable failure retains exactly the committed source");}
            System.out.println("CONTEXT_PENDING_WITNESS_WRITE_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
