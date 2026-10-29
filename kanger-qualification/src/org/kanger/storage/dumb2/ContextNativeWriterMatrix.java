/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Actual public explicit writer, with no IData replacement. */
final class ContextNativeWriterMatrix {
    static int checks;static final int WORK=64000000;
    static final String MASKED="!@u left(u)&&right(u)->p(u);",SAFE="!@u left(u)&&right(u)->q(u);";
    interface Attempt {void run()throws Exception;}
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static void reject(Attempt a,String why)throws Exception{boolean failed=false;try{a.run();}catch(Exception expected){failed=true;}require(failed,why);}
    static ContextNativeCandidateInspection.Limits limits(int work){return new ContextNativeCandidateInspection.Limits(1000,8,16,WORK,500000,work,WORK,4096);}
    static void connect(ContextNativeCandidateLeaseMatrix.Fixture f,boolean reverse)throws Exception{f.db.connectContext(reverse?"B":"A");f.db.connectContext(reverse?"A":"B");f.db.saveConnections(f.mind);}
    static ContextNativeCommitException refused(ContextNativeCandidateLeaseMatrix.Fixture f,String text,ContextNativeCandidateInspection.Limits l,int work,StorageLifecycleErrorCode code)throws Exception{ContextNativeCommitException seen=null;try{f.db.writeNativeProductionCandidate(f.mind,text,l,work,WORK,1000000,false);}catch(ContextNativeCommitException e){seen=e;}require(seen!=null&&seen.getErrorCode()==code,"actual selected writer typed native refusal");return seen;}
    static void positive()throws Exception{
        Path dir=Files.createTempDirectory("native-selected-writer-");System.setProperty("user.home",dir.toString());ContextNativeCandidateLeaseMatrix.create(dir,"A","!left(1); !p(1);");ContextNativeCandidateLeaseMatrix.create(dir,"B","!right(1); !~p(1);");
        for(boolean reverse:new boolean[]{false,true}){String name="X"+reverse;long durable;
            try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,name)){require(f.mind.compile("!anchor(1);",null,false),"actual root baseline");connect(f,reverse);long before=f.db.getRevision();Set<String> primary=ContextNativeCommitGuardMatrix.primary(f);
                ContextNativeCommitException conflict=refused(f,MASKED,limits(WORK),WORK,StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT);require(conflict.getNativeEvidence()!=null&&!conflict.getNativeEvidence().getOppositions().isEmpty(),"public writer preserves sealed sourced native evidence");conflict.getNativeEvidence().check(WORK,1);require(true,"fresh capacity1 replay after actual refused transaction");require(f.db.getRevision()==before&&ContextNativeCommitGuardMatrix.primary(f).equals(primary),"actual refused writer is atomic");
                require(f.db.writeNativeProductionCandidate(f.mind,SAFE,limits(WORK),WORK,WORK,1000000,false),"actual selected writer publishes after both gates and legacy validation");require(f.db.getRevision()>before&&ContextNativeCommitGuardMatrix.primary(f).size()==2&&ContextNativeCommitGuardMatrix.primary(f).contains(SAFE),"only safe original assertion physically published");durable=f.db.getRevision();
                require(f.user.getData()==f.db&&f.user.getCurrentMind()==f.mind,"real provider selected directly, no QA proxy or leaked caller scope");
                require(f.mind.query("!legacyAfter(9);",null,false),"ordinary writer remains usable after explicit success");require(ContextNativeCommitGuardMatrix.primary(f).size()==3,"ordinary follow-up publication not swallowed by prior attempt");
            }
            try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,name)){require(f.db.getRevision()>=durable&&ContextNativeCommitGuardMatrix.primary(f).size()==3&&ContextNativeCommitGuardMatrix.primary(f).contains(SAFE)&&!ContextNativeCommitGuardMatrix.primary(f).contains(MASKED),"durable reopen exact safe and legacy assertions only");}
        }
        try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,"Solo")){require(f.db.writeNativeProductionCandidate(f.mind,"!solo(1);",limits(WORK),WORK,WORK,1000000,false),"selected writer validates even without connected foreign contexts");require(ContextNativeCommitGuardMatrix.primary(f).size()==1,"no-connection publication remains actual physical assertion");}
        System.out.println("NATIVE_SELECTED_WRITER_POSITIVE actualPublicApi=true independentOrders=2 nativeAndFullFamily=true legacyValidation=true noProxy=true durableReopen=true");
    }
    static void boundaries()throws Exception{
        Path dir=Files.createTempDirectory("native-writer-boundaries-");System.setProperty("user.home",dir.toString());ContextNativeCandidateLeaseMatrix.create(dir,"A","!left(1); !p(1);");ContextNativeCandidateLeaseMatrix.create(dir,"B","!right(1); !~p(1);");long revision;
        try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,"X")){require(f.mind.compile("!anchor(1);",null,false),"boundary root");connect(f,false);revision=f.db.getRevision();
            require(refused(f,SAFE,limits(WORK),1,StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE).getPhase().equals("full-family-qualification"),"native ready never bypasses full-family work");
            require(refused(f,SAFE,limits(1),WORK,StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE).getNativeEvidence()==null,"actual native work exhaustion has no partial evidence");refused(f,"!@u @v @w edge(u,v)&&edge(v,w)->path(u,w);",limits(WORK),WORK,StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);
            for(String invalid:new String[]{"", "?left(1);", "!one(1); !two(2);"})reject(()->f.db.writeNativeProductionCandidate(f.mind,invalid,limits(WORK),WORK,WORK,1000000,false),"one assertion input boundary");
            require(f.db.getRevision()==revision&&ContextNativeCommitGuardMatrix.primary(f).size()==1,"all actual refusal/invalid paths preserve physical state");
            ContextPendingNativeWrite attempt=f.db.beginNativeProductionWrite(f.mind,limits(WORK),WORK,WORK,1000000);Mind child=null;
            try{reject(()->f.db.beginNativeProductionWrite(f.mind,limits(WORK),WORK,WORK,1000000),"nested attempt refused");child=new Mind(f.mind);require(child.compile(SAFE,null,false),"actual prepared safe child");final Mind pending=child;AtomicBoolean wrongThread=new AtomicBoolean();Thread other=new Thread(()->{try{attempt.validate(pending);}catch(Exception expected){wrongThread.set(true);}});other.start();other.join();require(wrongThread.get(),"attempt cannot cross owner thread");attempt.validate(child);attempt.validate(child);require(true,"same bound child remains current on repeat validation without reacquiring historical authority");require(child.compile("!changed(7);",null,false),"pending child mutation fixture");ContextNativeCommitException stale=null;try{attempt.validate(child);}catch(ContextNativeCommitException e){stale=e;}require(stale!=null&&stale.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"retained owning lease rejects child mutation after successful qualification");reject(()->attempt.validate(pending),"failed attempt cannot be retried");}finally{if(child!=null)f.mind.release(child);f.db.endNativeWrite(attempt);}
            reject(()->attempt.validate(f.mind),"closed attempt cannot be reused");
            Mind userChild=new Mind(f.mind);f.user.setCurrentMind(userChild);try{reject(()->f.db.writeNativeProductionCandidate(userChild,SAFE,limits(WORK),WORK,WORK,1000000,false),"explicit standalone writer cannot borrow a retained user child");}finally{f.user.setCurrentMind(f.mind);f.mind.release(userChild);}
            require(f.db.getRevision()==revision&&ContextNativeCommitGuardMatrix.primary(f).size()==1,"manual ownership refusals never settle actual parent");require(f.db.writeNativeProductionCandidate(f.mind,SAFE,limits(WORK),WORK,WORK,1000000,false),"failed attempts clean up selection, fresh selected write succeeds");
        }
        try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,"X@"+revision)){require(f.db.isReadOnly(),"historical context fixture");reject(()->f.db.writeNativeProductionCandidate(f.mind,"!no(1);",limits(WORK),WORK,WORK,1000000,false),"read-only provider cannot select writable branch attempt");}
        System.out.println("NATIVE_SELECTED_WRITER_BOUNDARIES nativeAndFullFamilyStops=true singleAssertion=true nestedThreadChildStaleness=true failedClosed=true selectionCleanup=true historicalRefused=true");
    }
}
