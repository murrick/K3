/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.*;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.interfaces.internal.IData;

/** Strict refusal adapter on actual native ingress; default hook remains unchanged. */
public final class ContextPendingCommitPolicyRunner {
    private static int checks;
    private static final int WORK=64000000;
    private static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    private static final class Guard {
        final User user;final DB db;int calls,rounds=16,work=WORK,audit=WORK;
        Guard(User user,DB db) {this.user=user;this.db=db;}
        void install() {
            user.setData((IData)java.lang.reflect.Proxy.newProxyInstance(DB.class.getClassLoader(),DB.class.getInterfaces(),(proxy,method,args) -> {
                if(method.getName().equals("validateCommit")) {
                    ++calls;synchronized(db) {user.setData(db);try {
                        db.validatePendingUniversalCommit((Mind)args[0],1000,rounds,16,16,20000,1000000,WORK,500000,work,audit);
                        db.validateCommit((org.kanger.interfaces.IMind)args[0]);
                    } finally {user.setData((IData)proxy);}}return null;
                }
                try {return method.invoke(db,args);} catch(java.lang.reflect.InvocationTargetException failure) {throw failure.getCause();}
            }));
        }
        void restore() {user.setData(db);}
    }
    private static final class Fixture implements AutoCloseable {
        final User user;final DB db=new DB();final Mind root;final Guard guard;
        Fixture(Path dir,String name) throws Exception {this(dir,name,new User());}
        Fixture(Path dir,String name,User user) throws Exception {this.user=user;user.setDatabaseDir(dir.toString()+File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);guard=new Guard(user,db);}
        public void close() throws Exception {guard.restore();if(user.getCurrentMind().isStorageUsed()) user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static final class FailUser extends User {
        boolean fail;int failures;final IOException injected=new IOException("strict policy committed finalization error");
        FailUser() throws Exception {super();}
        @Override public void flush() throws Exception {super.flush();if(fail) {fail=false;++failures;throw injected;}}
    }
    private static void command(Fixture f,String text) throws Exception {new CanonicalCommandProcessor().execute(new CommandParser().parse(text),f.user);}
    private static ContextProductionCommitException refused(Fixture f,String text,StorageLifecycleErrorCode code) throws Exception {
        ContextProductionCommitException seen=null;try {f.root.query(text,null,false);} catch(ContextProductionCommitException expected) {seen=expected;}
        require(seen!=null && seen.getErrorCode()==code,"typed policy refusal: "+text);
        require((code==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT)==!seen.getCollisions().isEmpty(),"only actual collision has legacy statement witnesses");return seen;
    }
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("pending-commit-policy-");System.setProperty("user.home",dir.toString());
            try(Fixture a=new Fixture(dir,"A")) {require(a.root.compile("!left(1); !p(1);",null,false),"masking A");}
            try(Fixture b=new Fixture(dir,"B")) {require(b.root.compile("!right(1); !~p(1);",null,false),"opposite B");}
            String rule="!@x left(x)&&right(x)->p(x);";
            for(boolean reverse:new boolean[]{false,true}) {
                long rev;
                try(Fixture f=new Fixture(dir,"X"+reverse)) {
                    require(f.root.compile("!anchor(1);",null,false),"parent anchor");f.db.connectContext(reverse?"B":"A");f.db.connectContext(reverse?"A":"B");rev=f.db.getRevision();int rows=f.root.getRules().size();
                    f.guard.install();ContextProductionCommitException failure=refused(f,rule,StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT);
                    require(failure.getFamilyAudit().verified && failure.getFamilyAudit().verdict.completedBarriers==2 && !failure.getFamilyAudit().oppositions.isEmpty(),"complete independently audited masked opposition retained");
                    require(f.db.getRevision()==rev && f.root.getRules().size()==rows && f.guard.calls==1,"one refused attempt, no native publication/retry");
                    f.guard.rounds=1;require(refused(f,rule,StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE).getFamilyAudit()!=null,"bounded inspector retains typed incomplete audit");f.guard.rounds=16;
                    f.guard.audit=1;refused(f,"!safe(1);",StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);f.guard.audit=WORK;
                    f.guard.work=1;refused(f,"!safe(1);",StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);f.guard.work=WORK;
                    ContextProductionCommitException scope=refused(f,"!@x f(x)->g(x+1);",StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);
                    require(scope.getFamilyAudit()==null && scope.getCause() instanceof IllegalStateException,"whole binding failure preserves cause, no collision invented");
                    require(Boolean.TRUE.equals(f.root.query("!safe(1);",null,false)),"verified consistent proposal continues original commit");
                    require(f.db.getRevision()>rev,"one actual source publication");
                }
                try(Fixture f=new Fixture(dir,"X"+reverse);ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)) {
                    require(lease.inventory().x.primary.size()==2 && lease.inventory().x.primary.stream().noneMatch(r -> r.origin.contains("->p")),"reopen has only anchor/safe, no rejected rule or product");
                }
            }
            try(Fixture f=new Fixture(dir,"UserLevel")) {
                require(f.root.compile("!anchor(1);",null,false),"user-level parent");f.db.connectContext("A");f.db.connectContext("B");long rev=f.db.getRevision();f.guard.install();
                command(f,"transaction start");Mind child=(Mind)f.user.getCurrentMind();require(child.query(rule,null,false),"proposal prepared in explicit user transaction");
                ContextProductionCommitException seen=null;try {command(f,"transaction commit");} catch(ContextProductionCommitException expected) {seen=expected;}
                require(seen!=null && seen.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT && f.user.getCurrentMind()==child && child.getTransactionLevel()==1,"rejected user commit retains exact amendable child");
                f.root.requirePendingPhysicalChild(child);require(f.db.getRevision()==rev,"user rejection retains live reservation without publication");
                long id=-1;for(org.kanger.interfaces.IRule item:child.getRules()) if(!item.isGenerated() && !item.isDeleted(child) && item.getOrigin().contains("->p")) {id=item.getId();break;}
                require(id>=0 && Boolean.TRUE.equals(child.query("-rule("+id+");",null,false)),"user can remove offending pending rule by native rule selector");
                require(child.query("!amended(1);",null,false),"user can amend retained child");command(f,"transaction commit");
                require(f.user.getCurrentMind()==f.root && f.db.getRevision()>rev,"amended transaction commits normally once");
                command(f,"transaction start");Mind rollback=(Mind)f.user.getCurrentMind();require(rollback.query(rule,null,false),"rollback proposal prepared");
                long rollbackRevision=f.db.getRevision();ContextProductionCommitException rejected=null;try {command(f,"transaction commit");} catch(ContextProductionCommitException expected) {rejected=expected;}
                require(rejected!=null && rejected.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"rollback starts from an actually refused conflicting user commit");
                ContextProductionCommitException rollbackFailure=null;try {command(f,"transaction rollback");} catch(ContextProductionCommitException expected) {rollbackFailure=expected;}
                require(rollbackFailure!=null && rollbackFailure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && rollbackFailure.getCollisions().isEmpty() && f.db.getRevision()==rollbackRevision && f.user.getCurrentMind()==rollback,"blanket guard concretely blocks internal rollback qualification without discarding user child");
                f.root.requirePendingPhysicalChild(rollback);f.guard.restore();
                command(f,"transaction rollback");require(f.user.getCurrentMind()==f.root,"original native rollback releases rejected user transaction after restoring its routing");
            }
            FailUser user=new FailUser();
            try(Fixture f=new Fixture(dir,"Fail",user)) {
                f.db.connectContext("A");f.guard.install();user.fail=true;TransactionSettlementException seen=null;
                try {f.root.query("!durable(1);",null,false);} catch(TransactionSettlementException expected) {seen=expected;}
                require(seen!=null && seen.getOutcome()==TransactionSettlementException.Outcome.COMMITTED && seen.getCause()==user.injected && user.failures==1 && f.guard.calls==1,"successful guard preserves COMMITTED finalization failure and no retry");
            }
            try(Fixture f=new Fixture(dir,"Fail")) {require(Boolean.TRUE.equals(f.root.query("?durable(1);",null,false)),"guarded committed failure remains durable");}
            // Concrete compatibility blocker: existing complete witness writer versus flat-only guard.
            try(Fixture a=new Fixture(dir,"WA")) {require(a.root.compile("!@w seed(w,1)->q(w,1);",null,false),"witness foreign fixture");}
            try(Fixture f=new Fixture(dir,"WitnessCompatibility")) {
                require(f.root.compile("!$w seed(w,1)&&flag(w,1); !@w seed(w,1)->warm(w,1);",null,false),"existing supported witness base");f.db.connectContext("WA");
                String out="!@w q(w,1)->out(w,1);";Mind child=new Mind(f.root);require(child.compile(out,null,false),"same native proposal prepared");
                ContextProductionCommitException seen=null;try {f.db.validatePendingUniversalCommit(child);} catch(ContextProductionCommitException expected) {seen=expected;}
                require(seen!=null && seen.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"flat-only guard cannot certify this witness family");f.root.release(child);
                require(f.db.writeProductionCandidate(f.root,out,1000,12,16,WORK,100000,false).status==DB.ProductionWriteStatus.PUBLISHED,"existing owned witness writer publishes same source/statement after its complete qualification");
            }
            System.out.println("CONTEXT_PENDING_COMMIT_POLICY_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
