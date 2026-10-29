/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;
final class ContextNativeSignedWitnessMatrix {
 static int checks;static final int WORK=64000000;
 static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
 static ContextNativeCandidateInspection.Limits limits(){return ContextNativeWriterMatrix.limits(WORK);}
 static void run()throws Exception{
  Path dir=Files.createTempDirectory("native-signed-witness-");System.setProperty("user.home",dir.toString());ContextNativeCandidateLeaseMatrix.create(dir,"A","!$u seed(u,1)&&~blocked(u,1);");ContextNativeCandidateLeaseMatrix.create(dir,"B","!foreignAnchor(1);");
  for(boolean reverse:new boolean[]{false,true})try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,"X"+reverse)){
   require(f.mind.compile("!anchor(1);",null,false),"signed private physical parent");f.db.connectContext(reverse?"B":"A","own");f.db.connectContext(reverse?"A":"B","own");f.db.saveConnections(f.mind);long revision=f.db.getRevision();String bad="!@u seed(u,1)->blocked(u,1);";
   ContextNativeCommitException conflict=null;try{f.db.writeNativeProductionCandidate(f.mind,bad,limits(),WORK,WORK,1000000,false);}catch(ContextNativeCommitException e){conflict=e;}
   require(conflict!=null&&conflict.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&conflict.toString().contains("Unsupported whole original initial row")&&conflict.getNativeEvidence()==null,"signed existential whole-row gate precedes apparent contradiction, no fabricated conflict evidence");
   String same="!@u seed(u,1)->~blocked(u,1);";try(ContextNativeWriteBaseline baseline=ContextNativeWriteBaseline.open(f.db,f.mind,1000)){Mind child=new Mind(f.mind);try{require(child.compile(same,null,false),"same-sign actual child");ContextNativeBranchInspection.Result r=baseline.bind(child).inspectNative(limits());require(r.status==ContextNativeBranchInspection.Status.INCOMPLETE&&r.proposed==null&&r.oppositions.isEmpty(),"unsupported signed bundle cannot expose provisional stable frame or collision prefix");}finally{f.mind.release(child);}}
   ContextNativeCommitException stopped=null;try{f.db.writeNativeProductionCandidate(f.mind,same,limits(),WORK,WORK,1000000,false);}catch(ContextNativeCommitException e){stopped=e;}require(stopped!=null&&stopped.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&stopped.getPhase().equals("native-branch")&&stopped.toString().contains("Unsupported whole original initial row")&&stopped.getNativeEvidence()==null,"actual signed-private request remains incomplete at whole-row classification, no permission");
   require(f.db.getRevision()==revision&&ContextNativeCommitGuardMatrix.primary(f).equals(Collections.singleton("!anchor(1);")),"signed conflict/stable-incomplete never publish");
  }
  ContextNativeCandidateLeaseMatrix.create(dir,"Positive","!$u seed(u,1);");
  try(ContextNativeCandidateLeaseMatrix.Fixture f=new ContextNativeCandidateLeaseMatrix.Fixture(dir,"SelfX")){require(f.mind.compile("!anchor(1); !$u local(u,1);",null,false),"self-X eligibility root");f.db.connectContext("Positive","own");f.db.connectContext("B","own");f.db.saveConnections(f.mind);long revision=f.db.getRevision();String unobserved="!@t absent(t)->~anchor(t);";
   try(ContextNativeWriteBaseline baseline=ContextNativeWriteBaseline.open(f.db,f.mind,1000)){Mind child=new Mind(f.mind);try{require(child.compile(unobserved,null,false),"prepared witness-free self-X producer");require(baseline.bind(child).inspectNative(limits()).status==ContextNativeBranchInspection.Status.STABLE_SUPPLIED,"native headless constraint scope is stable");}finally{f.mind.release(child);}}
   ContextWitnessProductionCommitException stopped=null;try{f.db.writeNativeProductionCandidate(f.mind,unobserved,limits(),WORK,WORK,1000000,false);}catch(ContextWitnessProductionCommitException e){stopped=e;}require(stopped!=null&&stopped.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE&&stopped.getFamilyEvaluation().reason.contains("No supported self-X producer snapshot"),"old witness Horn self-X coverage fails incomplete, never logical collision");require(f.db.getRevision()==revision&&ContextNativeCommitGuardMatrix.primary(f).size()==2,"self-X eligibility refusal is atomic");require(f.db.writeNativeProductionCandidate(f.mind,"!@u seed(u,1)->ready(u,1);",limits(),WORK,WORK,1000000,false),"supported positive-Horn producer still writes after self-X refusal");
  }
  System.out.println("NATIVE_SIGNED_PRIVATE orders=2 wholeRowStopsBeforeInference=true noPrefix=true actualWriterIncomplete=true signedCasesNoPublication=true selfXBoundary=true positiveRecovery=true");
 }
}
