/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;

/** Ground/universal native handoff uses actual pending inventories and own bounds. */
public final class ContextPendingClauseWriteRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    interface Action {void run() throws Exception;}
    static void reject(Action action,String why) throws Exception {boolean refused=false;try {action.run();} catch(IllegalStateException|StorageLifecycleException expected) {refused=true;}require(refused,why);}
    static class Fixture extends ContextPendingWitnessWriteRunner.Fixture {
        Fixture(Path dir,String name) throws Exception {super(dir,name);}
        Fixture(Path dir,String name,User user) throws Exception {super(dir,name,user);}
        void flat(boolean reverse) throws Exception {require(root.compile("!anchor(1);",null,false),"physical anchor");db.connectContext(reverse?"GB":"GA");db.connectContext(reverse?"GA":"GB");}
    }
    static ContextPendingNativeWrite begin(Fixture f,boolean universal,int rounds,int audit) throws Exception {
        return universal?f.db.beginUniversalWrite(f.root,1000,rounds,16,16,20000,1000000,WORK,500000,WORK,audit)
                :f.db.beginGroundWrite(f.root,1000,rounds,16,16,WORK,100000,100000,1000000);
    }
    static StorageLifecycleException refuse(Fixture f,String statement) throws Exception {
        StorageLifecycleException seen=null;try {f.root.query(statement,null,false);} catch(StorageLifecycleException expected) {seen=expected;}
        require(seen!=null,"actual native handoff refused");return seen;
    }
    static boolean write(Fixture f,boolean universal,String statement) throws Exception {
        return universal?f.db.writeUniversalProductionCandidate(f.root,statement,1000,12,16,16,20000,1000000,WORK,500000,WORK,WORK,false).status==DB.ProductionWriteStatus.PUBLISHED
                :f.db.writeGroundProductionCandidate(f.root,statement,1000,12,16,16,WORK,100000,100000,1000000,false).status==DB.ProductionWriteStatus.PUBLISHED;
    }
    public static void main(String[] args) {
        try {
            Path dir=Files.createTempDirectory("pending-clause-write-");System.setProperty("user.home",dir.toString());
            try(Fixture a=new Fixture(dir,"GA")) {require(a.root.compile("!left(1); !p(1);",null,false),"masking A");}
            try(Fixture b=new Fixture(dir,"GB")) {require(b.root.compile("!right(1); !~p(1);",null,false),"opposite B");}
            for(boolean universal:new boolean[]{false,true}) {
                String safe=universal?"!@x left(x)->safe(x);":"!p(1)||q(1);";
                String bad=universal?"!@x left(x)&&right(x)->p(x);":"!left(1)&&right(1)->p(1);";
                long revision;
                try(Fixture f=new Fixture(dir,"Writer"+universal)) {f.flat(false);require(write(f,universal,safe),"real explicit writer publishes after fresh actual-native qualification");revision=f.db.getRevision();}
                try(Fixture f=new Fixture(dir,"Writer"+universal);ContextProductionSnapshotSession view=ContextProductionSnapshotSession.open(f.db,f.root,1000)) {
                    require(f.db.getRevision()>=revision && view.inventory().x.primary.size()==2,"reopen has exact anchor plus source rule");
                    require(view.inventory().x.primary.stream().noneMatch(r -> r.origin.equals("!q(1);") || r.origin.equals("!safe(1);")),"foreign-dependent products are not materialized primary facts");
                }
                try(Fixture f=new Fixture(dir,"Strict"+universal)) {
                    f.flat(true);ContextPendingNativeWrite attempt=begin(f,universal,12,WORK);int[] calls={0};ContextPendingWitnessWriteRunner.strictProxy(f,calls);
                    try {require(Boolean.TRUE.equals(f.root.query(safe,null,false)) && calls[0]==1,"strict pending adapter routes owning ground/universal scope once");}
                    finally {f.user.setData(f.db);f.db.endNativeWrite(attempt);}
                    reject(() -> attempt.validate(null),"completed native scope cannot be reused");
                }
                for(boolean reverse:new boolean[]{false,true}) try(Fixture f=new Fixture(dir,"Collision"+universal+reverse)) {
                    f.flat(reverse);long before=f.db.getRevision();int rows=f.root.getRules().size();ContextPendingNativeWrite attempt=begin(f,universal,12,WORK);
                    try {StorageLifecycleException failure=refuse(f,bad);require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"actual prepared masked collision rejected in both orders");
                        if(universal) {ContextProductionCommitException proof=(ContextProductionCommitException)failure;require(proof.getFamilyAudit().verified && proof.getFamilyAudit().verdict.completedBarriers==2 && !proof.getFamilyAudit().oppositions.isEmpty(),"fresh independent universal audit and complete barrier2 proof retained");}
                        else {ContextGroundProductionCommitException proof=(ContextGroundProductionCommitException)failure;require(proof.getFamilyEvaluation().rounds==2 && !proof.getFamilyEvaluation().collisions.isEmpty(),"fresh complete ground barrier2 proof retained");}}
                    finally {f.db.endNativeWrite(attempt);}
                    require(f.db.getRevision()==before && f.root.getRules().size()==rows,"pre-settlement collision leaves parent and revision intact");
                }
                try(Fixture f=new Fixture(dir,"Incomplete"+universal)) {
                    f.flat(false);ContextPendingNativeWrite attempt=begin(f,universal,1,WORK);Mind child=new Mind(f.root);require(child.compile(safe,null,false),"bounded actual pending proposal");
                    try {StorageLifecycleException failure=null;try {f.db.validatePendingUniversalCommit(child);} catch(StorageLifecycleException expected) {failure=expected;}
                        require(failure!=null && failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"own synchronous round bound cannot authorize write");reject(() -> f.db.validatePendingUniversalCommit(child),"refused handoff cannot be reused for same live child");}
                    finally {f.root.release(child);f.db.endNativeWrite(attempt);}
                }
                try(Fixture f=new Fixture(dir,"Lifetime"+universal)) {
                    f.flat(false);ContextPendingNativeWrite attempt=begin(f,universal,12,WORK);Mind child=new Mind(f.root);require(child.compile(safe,null,false),"pending exact source");
                    try {f.db.validatePendingUniversalCommit(child);f.db.validatePendingUniversalCommit(child);require(true,"same immutable child permits repeated lease checks");
                        reject(() -> f.db.beginWitnessWrite(f.root,1000,12,16,WORK,100000),"another writer kind cannot replace active scope");
                        final boolean[] denied={false};Thread other=new Thread(() -> {try {attempt.validate(child);} catch(Exception expected) {denied[0]=true;}});other.start();other.join();require(denied[0],"native attempt cannot move threads");
                        require(child.compile("!changed(1);",null,false),"controlled pending edit");reject(() -> f.db.validatePendingUniversalCommit(child),"edited actual child cannot reuse completed family check");}
                    finally {f.root.release(child);f.db.endNativeWrite(attempt);}
                }
                try(Fixture f=new Fixture(dir,"Replaced"+universal)) {
                    f.flat(false);ContextPendingNativeWrite attempt=begin(f,universal,12,WORK);Mind old=new Mind(f.root);require(old.compile(safe,null,false),"old pending child");f.db.validatePendingUniversalCommit(old);f.root.release(old);Mind fresh=new Mind(f.root);
                    try {require(fresh.compile(safe,null,false),"same statement new child");reject(() -> f.db.validatePendingUniversalCommit(fresh),"new child cannot reuse completed old child qualification");}
                    finally {f.root.release(fresh);f.db.endNativeWrite(attempt);}
                }
                try(Fixture f=new Fixture(dir,"Stale"+universal)) {
                    f.flat(false);ContextPendingNativeWrite attempt=begin(f,universal,12,WORK);
                    try {f.db.disconnectContext("GA");f.db.connectContext("GA");StorageLifecycleException failure=refuse(f,safe);require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && failure.getCause()!=null,"same-metadata connection replacement invalidates precompilation baseline");}
                    finally {f.db.endNativeWrite(attempt);}
                }
                ContextPendingWitnessWriteRunner.FailUser user=new ContextPendingWitnessWriteRunner.FailUser();
                try(Fixture f=new Fixture(dir,"Failure"+universal,user)) {f.flat(false);user.fail=true;TransactionSettlementException failure=null;
                    try {write(f,universal,safe);} catch(TransactionSettlementException expected) {failure=expected;}
                    require(failure!=null && failure.getOutcome()==TransactionSettlementException.Outcome.COMMITTED && failure.getCause()==user.injected && user.failures==1,"real writer preserves COMMITTED/original cause without retry");}
                try(Fixture f=new Fixture(dir,"Failure"+universal);ContextProductionSnapshotSession view=ContextProductionSnapshotSession.open(f.db,f.root,1000)) {require(view.inventory().x.primary.size()==2,"post-durable failure retains exact committed primary set");}
            }
            try(Fixture f=new Fixture(dir,"UniversalAudit")) {f.flat(false);ContextPendingNativeWrite attempt=begin(f,true,12,1);
                try {StorageLifecycleException failure=refuse(f,"!@x left(x)->safe(x);");require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && ((ContextProductionCommitException)failure).getFamilyAudit()!=null,"own universal independent audit bound enforced on actual pending X");}
                finally {f.db.endNativeWrite(attempt);}
            }
            try(Fixture f=new Fixture(dir,"GroundModelBound")) {f.flat(false);ContextPendingNativeWrite attempt=f.db.beginGroundWrite(f.root,1000,12,16,16,WORK,100000,100000,1);
                try {StorageLifecycleException failure=refuse(f,"!p(1)||q(1);");ContextGroundClauseFamily.Evaluation diagnostic=((ContextGroundProductionCommitException)failure).getFamilyEvaluation();
                    require(failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE && diagnostic.reason.toLowerCase().contains("model") && diagnostic.certificate==null,"ground model limit yields incomplete with no consistency certificate");}
                finally {f.db.endNativeWrite(attempt);}
            }
            try(Fixture f=new Fixture(dir,"GroundScope")) {f.flat(false);ContextPendingNativeWrite attempt=begin(f,false,12,WORK);
                try {StorageLifecycleException failure=refuse(f,"!@x left(x)->safe(x);");require(failure instanceof ContextGroundProductionCommitException && failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"ground mode cannot silently accept universal variables");}
                finally {f.db.endNativeWrite(attempt);}
            }
            System.out.println("CONTEXT_PENDING_CLAUSE_WRITE_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
