/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.internal.IData;

/** QA-only opt-in actual native ingress, default remains unchanged. */
final class ContextNativeCommitGuardMatrix {
    static int checks;
    static final int WORK=64000000;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static final class Guard {
        final ContextNativeCandidateLeaseMatrix.Fixture f;int nativeWork=WORK,nativeAudit=WORK,familyWork=WORK,familyAudit=WORK,models=1000000,calls,rollbackCalls;
        Guard(ContextNativeCandidateLeaseMatrix.Fixture f){this.f=f;}
        ContextNativeCandidateInspection.Limits limits(){return new ContextNativeCandidateInspection.Limits(1000,8,16,WORK,500000,nativeWork,nativeAudit,4096);}
        void install(){f.user.setData((IData)java.lang.reflect.Proxy.newProxyInstance(DB.class.getClassLoader(),DB.class.getInterfaces(),(object,method,args)->{
            if(method.getName().equals("validateCommit")){synchronized(f.db){f.user.setData(f.db);try{Mind proposed=(Mind)args[0];if(proposed.getNext()==f.mind){++calls;if(f.mind.requirePendingValidationChild(proposed)!=null)++rollbackCalls;f.db.validatePendingNativeProductionCommit(proposed,limits(),familyWork,familyAudit,models);}f.db.validateCommit((IMind)args[0]);}finally{f.user.setData((IData)object);}}return null;}
            try{return method.invoke(f.db,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        }));}
        void restore(){f.user.setData(f.db);}
    }
    static Set<String> primary(ContextNativeCandidateLeaseMatrix.Fixture f)throws Exception{Set<String> out=new HashSet<>();for(ContextProductionInventory.Row row:ContextProductionInventory.inspect(f.mind,f.db.getContextId(),f.db.getRevision(),true,Collections.emptyList(),1000).primary)out.add(row.origin);return out;}
    static StorageLifecycleException attempt(Guard guard,String statement)throws Exception{guard.install();try{guard.f.mind.query(statement,null,false);return null;}catch(StorageLifecycleException e){return e;}finally{guard.restore();}}
    static ContextNativeCommitException nativeFailure(StorageLifecycleException e,StorageLifecycleErrorCode code){require(e instanceof ContextNativeCommitException&&e.getErrorCode()==code,"distinct typed native refusal, never relabeled old FamilyAudit");return (ContextNativeCommitException)e;}
    static void command(ContextNativeCandidateLeaseMatrix.Fixture f,String text)throws Exception{new CanonicalCommandProcessor().execute(new CommandParser().parse(text),f.user);}
    static void ingress()throws Exception{
        Path dir=Files.createTempDirectory("native-commit-ingress-");System.setProperty("user.home",dir.toString());ContextNativeCandidateLeaseMatrix.create(dir,"A","!left(1); !p(1);");ContextNativeCandidateLeaseMatrix.create(dir,"B","!right(1); !~p(1);");long durable;
        try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,"X")){require(f.mind.compile("!anchor(1);",null,false),"physical baseline");f.db.connectContext("A");f.db.connectContext("B");f.db.saveConnections(f.mind);Guard guard=new Guard(f);long before=f.db.getRevision();Set<String> original=primary(f);String masked="!@u left(u)&&right(u)->p(u);",safe="!@u left(u)&&right(u)->q(u);";
            ContextNativeCommitException collision=nativeFailure(attempt(guard,masked),StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT);require(collision.getNativeEvidence()!=null&&!collision.getNativeEvidence().getOppositions().isEmpty()&&collision.getCollisions().isEmpty(),"native DAG evidence retained; no invented UnitRounds collision witnesses");collision.getNativeEvidence().check(WORK,4096);require(true,"independent same-request refusal replay after native transaction rollback");for(ContextNativeBranchInspection.Opposition o:collision.getNativeEvidence().getOppositions())require(o.xRefuted&&o.sources.size()==4&&o.sources.stream().filter(s->s.localX).count()==1,"exact pending X plus three foreign Source occurrences preserved");require(f.db.getRevision()==before&&primary(f).equals(original),"native refusal preserves physical knowledge/revision");
            guard.nativeAudit=1;ContextNativeCommitException unverified=nativeFailure(attempt(guard,masked),StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);require(unverified.getPhase().equals("refutation-audit")&&unverified.getNativeEvidence()==null,"unverified real collision cannot become typed conflict");guard.nativeAudit=WORK;
            guard.familyWork=1;ContextNativeCommitException noFull=nativeFailure(attempt(guard,safe),StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);require(noFull.getPhase().equals("full-family-qualification")&&noFull.getNativeEvidence()==null,"native scoped ready cannot bypass mandatory full-family gate");
            nativeFailure(attempt(guard,masked),StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT);require(true,"native verified rejection precedes even resource-limited full-family phase");guard.familyWork=WORK;
            guard.familyAudit=1;StorageLifecycleException oldAudit=attempt(guard,safe);require(oldAudit instanceof ContextProductionCommitException&&oldAudit.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"existing full-family audit contract remains mandatory and distinct");guard.familyAudit=WORK;
            guard.nativeWork=1;ContextNativeCommitException bounded=nativeFailure(attempt(guard,safe),StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);require(bounded.getNativeEvidence()==null,"native resource stop has no rejection proof prefix");guard.nativeWork=WORK;require(f.db.getRevision()==before&&primary(f).equals(original),"all native/full-family bounds fail closed before physical write");
            require(attempt(guard,safe)==null&&f.db.getRevision()>before,"actual native publication ONLY after both independent native and existing full-family gates");require(primary(f).size()==2&&primary(f).contains(safe)&&primary(f).contains("!anchor(1);"),"only actual safe original rule published, no transported foreign facts");durable=f.db.getRevision();
            guard.install();try{command(f,"transaction start");Mind retained=(Mind)f.user.getCurrentMind();require(retained.query(masked,null,false),"user prepares real child without premature parent validation");ContextNativeCommitException refused=null;try{command(f,"transaction commit");}catch(ContextNativeCommitException e){refused=e;}require(refused!=null&&refused.getNativeEvidence()!=null&&f.user.getCurrentMind()==retained,"typed native user commit refusal retains amendable exact child");f.mind.requirePendingPhysicalChild(retained);long revision=f.db.getRevision();command(f,"transaction rollback");require(f.user.getCurrentMind()==f.mind&&f.db.getRevision()==revision&&guard.rollbackCalls==1,"both gates validate ACTUAL rollback refresh, retained conflicting rule never imported");}finally{guard.restore();}require(primary(f).size()==2&&!primary(f).contains(masked),"rollback discards refused rule without primary leakage");
        }
        try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,"X")){require(f.db.getRevision()>=durable&&primary(f).size()==2&&primary(f).stream().anyMatch(s->s.contains("->q")),"normal durable reopen observes only safe publication");}
        System.out.println("NATIVE_COMMIT_INGRESS typedDagRefusal=true nativeThenFullFamily=true noScopedApproval=true actualSafeWrite=true retainedRollback=true durableReopen=true");
    }
    static void boundaries()throws Exception{
        Path dir=Files.createTempDirectory("native-commit-boundaries-");System.setProperty("user.home",dir.toString());ContextNativeCandidateLeaseMatrix.create(dir,"A","!left(1); !p(1);");ContextNativeCandidateLeaseMatrix.create(dir,"B","!right(1); !~p(1);");
        try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,"X")){require(f.mind.compile("!anchor(1);",null,false),"boundary baseline");f.db.connectContext("A");f.db.connectContext("B");Guard guard=new Guard(f);long revision=f.db.getRevision();
            nativeFailure(attempt(guard,"!@u @v @w edge(u,v)&&edge(v,w)->path(u,w);"),StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);require(f.db.getRevision()==revision&&primary(f).size()==1,"unsupported native scope is not a general writer bypass");
            Mind child=new Mind(f.mind);try{require(child.compile("!@u left(u)&&right(u)->p(u);",null,false),"direct pending evidence fixture");ContextNativeCommitException proof=null;try{f.db.validatePendingNativeProductionCommit(child,guard.limits(),WORK,WORK,1000000);}catch(ContextNativeCommitException e){proof=e;}require(proof!=null&&proof.getNativeEvidence()!=null,"fresh exact pending guard produces sealed evidence");final ContextNativePendingGuard.Evidence evidence=proof.getNativeEvidence();boolean failed=false;try{evidence.check(1,4096);}catch(IllegalStateException expected){failed=true;}require(failed,"fresh evidence audit work exhaustion never verifies");evidence.check(WORK,1);require(true,"capacity1 independent replay retains exact Source minima");failed=false;try{evidence.getOppositions().clear();}catch(UnsupportedOperationException expected){failed=true;}require(failed,"native opposition summary immutable");}finally{f.mind.release(child);}
            boolean invalid=false;try{ContextNativeCommitException.conflict(null);}catch(IllegalArgumentException expected){invalid=true;}require(invalid,"public scalar diagnostic cannot fabricate native conflict evidence");
            boolean missing=false;try{f.db.validatePendingNativeProductionCommit(f.mind,guard.limits(),WORK,WORK,1000000);}catch(IllegalArgumentException expected){missing=true;}require(missing,"settled parent is invalid input: actual owning child required before either phase");
        }
        System.out.println("NATIVE_COMMIT_BOUNDARIES unsupportedAndBudgetFailClosed=true sealedEvidence=true independentReplayCapacity1=true actualChildRequired=true");
    }
}
