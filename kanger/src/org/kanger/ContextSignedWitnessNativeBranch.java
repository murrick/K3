/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Detached explicit signed-profile native ingress. No live-provider, model or write authority. */
final class ContextSignedWitnessNativeBranch {
 enum Status { QUIESCENT_SUPPLIED, REFUTED, INCOMPLETE }
 static final class Policy {
  final int rows,rank,rounds,work,capacity;
  final ContextUniversalFrozenBranch.Limits kernel;
  final ContextUniversalGroupedLocalRounds.Limits local;
  Policy(int rows,int rank,int rounds,int work,int capacity,ContextUniversalFrozenBranch.Limits kernel,ContextUniversalGroupedLocalRounds.Limits local){
   require(rows>=1&&rows<=64&&rank>=1&&rank<=8&&rounds>=1&&rounds<=63&&work>0&&capacity>=1&&capacity<=4096&&kernel!=null,"Exact bounded signed native policy required");ContextSignedWitnessNextReceiver.localPolicy(kernel,local);this.rows=rows;this.rank=rank;this.rounds=rounds;this.work=work;this.capacity=capacity;this.kernel=kernel;this.local=local;
  }
 }
 static final class Receipt {
  final Snapshot x;final List<Snapshot> foreign;final Policy policy;final Status status;
  final ContextSignedWitnessPlanAudit.Receipt plan;
  final ContextSignedWitnessFrameAudit.Receipt seeds;
  final ContextSignedWitnessReceiver.Result initialRejection;
  final ContextSignedWitnessContinuationScope.Receipt start;
  final ContextSignedWitnessUntilStop.Receipt termination;
  private Receipt(Snapshot x,List<Snapshot> foreign,Policy policy,Status status,ContextSignedWitnessPlanAudit.Receipt plan,ContextSignedWitnessFrameAudit.Receipt seeds,ContextSignedWitnessReceiver.Result initialRejection,ContextSignedWitnessContinuationScope.Receipt start,ContextSignedWitnessUntilStop.Receipt termination){this.x=x;this.foreign=Collections.unmodifiableList(new ArrayList<>(foreign));this.policy=policy;this.status=status;this.plan=plan;this.seeds=seeds;this.initialRejection=initialRejection;this.start=start;this.termination=termination;}
 }
 static final class Result {
  final Status status;final String phase,reason;final Receipt output,rejection;
  private Result(Status status,String phase,String reason,Receipt receipt){this.status=status;this.phase=phase;this.reason=reason;output=status==Status.QUIESCENT_SUPPLIED?receipt:null;rejection=status==Status.REFUTED?receipt:null;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result run(Snapshot x,List<Snapshot> foreign,Policy p){
  String phase="request";
  try{require(x!=null&&foreign!=null&&foreign.size()<=15&&p!=null,"Actual complete signed native request required");new Policy(p.rows,p.rank,p.rounds,p.work,p.capacity,p.kernel,p.local);List<Snapshot> cuts=new ArrayList<>(foreign);
   phase="whole-row-plan";ContextSignedWitnessPlan.Result planned=ContextSignedWitnessPlan.build(x,cuts,p.rows,p.work);require(planned.planned,planned.phase+": "+planned.reason);ContextSignedWitnessPlanAudit.Inspection plan=ContextSignedWitnessPlanAudit.inspect(planned.output,x,cuts,p.rows,p.work);require(plan.audited,plan.reason);
   phase="original-seeds";SignedSeedResult built=ContextWitnessResultTransport.signedWitnessFrame(plan.output,x,cuts,p.rows,p.rank,p.work,p.work);require(built.assembled,built.reason);ContextSignedWitnessFrameAudit.Inspection seeds=ContextSignedWitnessFrameAudit.inspect(built.output,plan.output,x,cuts,p.rows,p.rank,p.work,p.work);require(seeds.audited,seeds.reason);
   phase="initial-all-cut-qualification";ContextSignedWitnessInitialEntry.Result initial=ContextSignedWitnessInitialEntry.assemble(seeds.output,p.rows,p.kernel,p.work,p.capacity);require(initial.status!=ContextSignedWitnessInitialEntry.Status.INCOMPLETE,initial.phase+": "+initial.reason);Receipt receipt;
   if(initial.status==ContextSignedWitnessInitialEntry.Status.REFUTED)receipt=new Receipt(x,cuts,p,Status.REFUTED,plan.output,seeds.output,initial.rejection,null,null);
   else{phase="initial-barrier";ContextSignedWitnessInitialEntry.Receipt entry=ContextSignedWitnessInitialEntry.inspect(initial.entry,seeds.output,p.rows,p.kernel,p.work,p.capacity);ContextSignedWitnessInitialBarrier.Result barrier=ContextSignedWitnessInitialBarrier.run(entry,seeds.output,p.rows,p.kernel,p.work,p.capacity);require(barrier.merged,barrier.phase+": "+barrier.reason);ContextSignedWitnessInitialBarrierAudit.Receipt audited=ContextSignedWitnessInitialBarrierAudit.inspect(barrier,entry,seeds.output,p.rows,p.kernel,p.work,p.capacity);ContextSignedWitnessContinuationScope.Receipt start=ContextSignedWitnessContinuationScope.open(audited,p.rows,p.kernel,p.work,p.capacity);
    phase="bounded-termination";ContextSignedWitnessUntilStop.Result ended=ContextSignedWitnessUntilStop.run(start,p.rows,p.kernel,p.local,p.rounds,p.work,p.capacity);require(ended.status!=ContextSignedWitnessUntilStop.Status.INCOMPLETE,ended.phase+": "+ended.reason);ContextSignedWitnessUntilStop.Receipt termination=ContextSignedWitnessUntilStop.inspect(ended,start,p.rows,p.kernel,p.local,p.rounds,p.work,p.capacity);receipt=new Receipt(x,cuts,p,ended.status==ContextSignedWitnessUntilStop.Status.QUIESCENT?Status.QUIESCENT_SUPPLIED:Status.REFUTED,plan.output,seeds.output,null,start,termination);
   }
   phase="independent-ingress-audit";ContextSignedWitnessNativeBranchAudit.check(receipt,x,cuts,p,p.work,p.capacity);return new Result(receipt.status,"audited-supplied", "Exact supplied signed-profile evidence only; no model/full-family/provider/write admission",receipt);
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null);}
 }
}
