/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Explicit bounded advancement, not equilibrium. All Source grades/history remain authenticated. */
final class ContextSignedWitnessRounds {
 enum Status { ADVANCED, REFUTED, INCOMPLETE }
 static final class Step {
  final ContextSignedWitnessContinuationScope.Receipt input,output;
  final ContextSignedWitnessNextReceiver.Result checks;
  final ContextSignedWitnessSuccessorBarrierAudit.Receipt barrier;
  private Step(ContextSignedWitnessContinuationScope.Receipt input,ContextSignedWitnessNextReceiver.Result checks,ContextSignedWitnessSuccessorBarrierAudit.Receipt barrier,ContextSignedWitnessContinuationScope.Receipt output){this.input=input;this.checks=checks;this.barrier=barrier;this.output=output;}
 }
 static final class Result {
  final Status status;final String phase,reason;final int requested;
  final ContextSignedWitnessContinuationScope.Receipt start,output;
  final List<Step> history;final ContextSignedWitnessNextReceiver.Result refutation;
  private Result(Status status,String phase,String reason,int requested,ContextSignedWitnessContinuationScope.Receipt start,ContextSignedWitnessContinuationScope.Receipt output,List<Step> history,ContextSignedWitnessNextReceiver.Result refutation){this.status=status;this.phase=phase;this.reason=reason;this.requested=requested;this.start=start;this.output=output;this.history=Collections.unmodifiableList(new ArrayList<>(history));this.refutation=refutation;}
 }
 static final class Receipt {final Result result;private Receipt(Result result){this.result=result;}}
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result run(ContextSignedWitnessContinuationScope.Receipt start,int rows,ContextUniversalFrozenBranch.Limits limits,int rounds,int auditWork,int capacity){
  String phase="lineage-preflight";
  try{require(start!=null&&start.rows==rows&&start.limits==limits&&rounds>=1&&rounds<=64-start.generation&&auditWork>0&&capacity>=1&&capacity<=4096,"Bounded exact signed advancement request required");ContextSignedWitnessContinuationScope.check(start,auditWork,capacity);ContextSignedWitnessContinuationScope.Receipt current=start;List<Step> history=new ArrayList<>();
   for(int n=0;n<rounds;++n){phase="all-receivers-"+(n+1);ContextSignedWitnessNextReceiver.Result checked=ContextSignedWitnessNextReceiver.run(current,rows,limits,auditWork);require(checked.status!=ContextSignedWitnessNextReceiver.Status.INCOMPLETE,checked.phase+": "+checked.reason);
    if(checked.status==ContextSignedWitnessNextReceiver.Status.REFUTED){Result r=new Result(Status.REFUTED,phase,"Complete all-cut sourced refutation; no stable output",rounds,start,null,history,checked);inspect(r,start,rows,limits,rounds,auditWork,capacity);return r;}
    phase="all-products-"+(n+1);ContextSignedWitnessSuccessorBarrier.Result b=ContextSignedWitnessSuccessorBarrier.run(checked,current,rows,limits,auditWork,capacity);require(b.merged,b.phase+": "+b.reason);ContextSignedWitnessSuccessorBarrierAudit.Receipt audited=ContextSignedWitnessSuccessorBarrierAudit.inspect(b,checked,current,rows,limits,auditWork,capacity);ContextSignedWitnessContinuationScope.Receipt next=ContextSignedWitnessContinuationScope.openLater(audited,rows,limits,auditWork,capacity);history.add(new Step(current,checked,audited,next));current=next;
   }
   Result r=new Result(Status.ADVANCED,"bounded-advance","Requested transitions audited; no equilibrium/admission claim",rounds,start,current,history,null);phase="full-history-audit";inspect(r,start,rows,limits,rounds,auditWork,capacity);return r;
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),rounds,null,null,Collections.emptyList(),null);}
 }
 /** Direct actual-step replay, not controller replay. No stable status exists. */
 static Receipt inspect(Result r,ContextSignedWitnessContinuationScope.Receipt start,int rows,ContextUniversalFrozenBranch.Limits limits,int rounds,int auditWork,int capacity){
  require(r!=null&&r.status!=Status.INCOMPLETE&&r.start==start&&r.requested==rounds&&start!=null&&start.rows==rows&&start.limits==limits&&rounds>=1&&rounds<=64-start.generation,"Different exact signed advancement ledger");ContextSignedWitnessContinuationScope.check(start,auditWork,capacity);ContextSignedWitnessContinuationScope.Receipt current=start;require(r.history.size()<=rounds,"Too many signed transitions");
  for(Step s:r.history){require(s!=null&&s.input==current&&s.checks!=null&&s.checks.scope==current&&s.checks.status==ContextSignedWitnessNextReceiver.Status.CHECKED&&s.barrier!=null&&s.barrier.result.next==s.checks&&s.output!=null&&s.output.prior==current&&s.output.later==s.barrier&&s.output.generation==current.generation+1,"Different actual consecutive signed transition");ContextSignedWitnessNextReceiver.check(s.checks,current,rows,limits,auditWork);ContextSignedWitnessSuccessorBarrierAudit.check(s.barrier,s.checks,current,rows,limits,auditWork,capacity);ContextSignedWitnessContinuationScope.check(s.output,auditWork,capacity);current=s.output;}
  if(r.status==Status.ADVANCED)require(r.history.size()==rounds&&r.output==current&&r.refutation==null,"Incomplete advancement output");else{require(r.status==Status.REFUTED&&r.output==null&&r.history.size()<rounds&&r.refutation!=null&&r.refutation.scope==current&&r.refutation.status==ContextSignedWitnessNextReceiver.Status.REFUTED,"Different terminal signed refutation ledger");ContextSignedWitnessNextReceiver.check(r.refutation,current,rows,limits,auditWork);}return new Receipt(r);
 }
 static void check(Receipt r,ContextSignedWitnessContinuationScope.Receipt start,int rows,ContextUniversalFrozenBranch.Limits limits,int rounds,int auditWork,int capacity){require(r!=null,"Actual bounded advancement receipt required");inspect(r.result,start,rows,limits,rounds,auditWork,capacity);}
}
