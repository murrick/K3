/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Bounded operational termination for supplied snapshots/profile, never native/model admission. */
final class ContextSignedWitnessUntilStop {
 enum Status { QUIESCENT, REFUTED, INCOMPLETE }
 static final class Result {
  final Status status;final String phase,reason;final int maximumRounds;
  final ContextSignedWitnessContinuationScope.Receipt start,output;
  final ContextUniversalGroupedLocalRounds.Limits local;
  final List<ContextSignedWitnessRounds.Receipt> history;
  final ContextSignedWitnessNextReceiver.Result terminal;
  final ContextSignedWitnessStop.Receipt stopping;
  private Result(Status status,String phase,String reason,int maximumRounds,ContextSignedWitnessContinuationScope.Receipt start,ContextSignedWitnessContinuationScope.Receipt output,ContextUniversalGroupedLocalRounds.Limits local,List<ContextSignedWitnessRounds.Receipt> history,ContextSignedWitnessNextReceiver.Result terminal,ContextSignedWitnessStop.Receipt stopping){this.status=status;this.phase=phase;this.reason=reason;this.maximumRounds=maximumRounds;this.start=start;this.output=output;this.local=local;this.history=Collections.unmodifiableList(new ArrayList<>(history));this.terminal=terminal;this.stopping=stopping;}
 }
 static final class Receipt {final Result result;private Receipt(Result result){this.result=result;}}
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result run(ContextSignedWitnessContinuationScope.Receipt start,int rows,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits local,int maximumRounds,int work,int capacity){
  try(ContextSignedWitnessAuditSession session=ContextSignedWitnessAuditSession.open(work,capacity)){return build(start,rows,limits,local,maximumRounds,work,capacity);}catch(Exception failure){return new Result(Status.INCOMPLETE,"invocation-audit",failure.toString(),maximumRounds,null,null,null,Collections.emptyList(),null,null);}
 }
 private static Result build(ContextSignedWitnessContinuationScope.Receipt start,int rows,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits local,int maximumRounds,int work,int capacity){
  String phase="termination-policy";
  try{require(start!=null&&start.rows==rows&&start.limits==limits&&maximumRounds>=1&&maximumRounds<=64-start.generation&&work>0&&capacity>=1&&capacity<=4096,"Bounded exact termination request required");ContextSignedWitnessNextReceiver.localPolicy(limits,local);ContextSignedWitnessContinuationScope.check(start,work,capacity);ContextSignedWitnessContinuationScope.Receipt current=start;List<ContextSignedWitnessRounds.Receipt> history=new ArrayList<>();
   for(int n=1;n<=maximumRounds;++n){phase="grouped-round-"+n;ContextSignedWitnessRounds.Result a=ContextSignedWitnessRounds.runGrouped(current,rows,limits,local,1,work,capacity);require(a.status!=ContextSignedWitnessRounds.Status.INCOMPLETE,a.phase+": "+a.reason);ContextSignedWitnessRounds.Receipt receipt=ContextSignedWitnessRounds.inspectGrouped(a,current,rows,limits,local,1,work,capacity);history.add(receipt);
    if(a.status==ContextSignedWitnessRounds.Status.REFUTED){Result r=new Result(Status.REFUTED,phase,"Complete all-cut grouped refutation; no stopping output",maximumRounds,start,null,local,history,a.refutation,null);audit(r,start,rows,limits,local,maximumRounds,work,capacity);return r;}
    ContextSignedWitnessContinuationScope.Receipt prior=current;current=a.output;if(a.history.get(0).changes.any())continue;
    // A successful Stop receipt already includes actual final requalification. On refusal,
    // only a fresh typed all-cut refutation may reject; error text never becomes evidence.
    phase="qualified-stop-"+n;ContextSignedWitnessStop.Result stop=ContextSignedWitnessStop.run(receipt,prior,rows,limits,local,1,work,capacity);
    if(!stop.quiescent){phase="final-requalification-"+n;ContextSignedWitnessNextReceiver.Result finalCheck=ContextSignedWitnessNextReceiver.runGrouped(current,rows,limits,local,work,capacity);require(finalCheck.status!=ContextSignedWitnessNextReceiver.Status.INCOMPLETE,finalCheck.phase+": "+finalCheck.reason);if(finalCheck.status==ContextSignedWitnessNextReceiver.Status.REFUTED){Result r=new Result(Status.REFUTED,phase,"Actual final-barrier all-cut grouped refutation",maximumRounds,start,null,local,history,finalCheck,null);audit(r,start,rows,limits,local,maximumRounds,work,capacity);return r;}throw new IllegalStateException(stop.phase+": "+stop.reason);}
    Result r=new Result(Status.QUIESCENT,phase,"Audited supplied-profile stop; no live-provider/model/native/write authority",maximumRounds,start,current,local,history,stop.output.terminal,stop.output);phase="termination-history-composition";audit(r,start,rows,limits,local,maximumRounds,work,capacity);return r;
   }phase="round-limit";throw new IllegalStateException("Signed stopping round limit; progress is not quiescence");
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),maximumRounds,null,null,null,Collections.emptyList(),null,null);}
 }
 /** Direct actual termination ledger audit. Does not run this controller or infer from an error string. */
 static Receipt inspect(Result r,ContextSignedWitnessContinuationScope.Receipt start,int rows,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits local,int maximumRounds,int work,int capacity){
  try(ContextSignedWitnessAuditSession session=ContextSignedWitnessAuditSession.open(work,capacity)){return audit(r,start,rows,limits,local,maximumRounds,work,capacity);}
 }
 /** Actual child receipts and history are rechecked in the owned invocation; no persistent reuse. */
 private static Receipt audit(Result r,ContextSignedWitnessContinuationScope.Receipt start,int rows,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits local,int maximumRounds,int work,int capacity){
  require(r!=null&&r.status!=Status.INCOMPLETE&&r.start==start&&r.local==local&&r.maximumRounds==maximumRounds&&start!=null&&start.rows==rows&&start.limits==limits&&maximumRounds>=1&&maximumRounds<=64-start.generation&&!r.history.isEmpty()&&r.history.size()<=maximumRounds,"Different exact bounded termination ledger");ContextSignedWitnessNextReceiver.localPolicy(limits,local);ContextSignedWitnessContinuationScope.check(start,work,capacity);ContextSignedWitnessContinuationScope.Receipt current=start;
  for(int i=0;i<r.history.size();++i){ContextSignedWitnessRounds.Receipt a=r.history.get(i);require(a!=null&&a.result.start==current&&a.result.localLimits==local&&a.result.requested==1,"Different actual one-round child request");ContextSignedWitnessRounds.checkGrouped(a,current,rows,limits,local,1,work,capacity);boolean last=i==r.history.size()-1;
   if(a.result.status==ContextSignedWitnessRounds.Status.REFUTED){require(last&&r.status==Status.REFUTED&&r.output==null&&r.stopping==null&&r.terminal==a.result.refutation,"Different owned terminal round refutation");return new Receipt(r);}
   require(a.result.status==ContextSignedWitnessRounds.Status.ADVANCED&&a.result.output!=null&&a.result.history.size()==1,"Complete one-transition receipt required");boolean changed=a.result.history.get(0).changes.any();ContextSignedWitnessContinuationScope.Receipt prior=current;current=a.result.output;
   if(!last){require(changed,"Ignored earlier unchanged transition; termination must not be guessed or skipped");continue;}
   require(!changed,"Progressing transition cannot certify termination");
   if(r.status==Status.QUIESCENT){require(r.output==current&&r.stopping!=null&&r.stopping.advance==a&&r.terminal==r.stopping.terminal&&r.terminal.scope==current,"Actual final stopping receipt/scope required");ContextSignedWitnessStop.check(r.stopping,a,prior,rows,limits,local,1,work,capacity);}else{require(r.status==Status.REFUTED&&r.output==null&&r.stopping==null&&r.terminal!=null&&r.terminal.scope==current&&r.terminal.status==ContextSignedWitnessNextReceiver.Status.REFUTED&&r.terminal.localLimits==local,"Different final requalification refutation");ContextSignedWitnessNextReceiver.checkGrouped(r.terminal,current,rows,limits,local,work,capacity);}
  }return new Receipt(r);
 }
 static void check(Receipt r,ContextSignedWitnessContinuationScope.Receipt start,int rows,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits local,int maximumRounds,int work,int capacity){require(r!=null,"Actual termination receipt required");inspect(r.result,start,rows,limits,local,maximumRounds,work,capacity);}
}
