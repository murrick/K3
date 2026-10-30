/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import java.util.*;
import org.kanger.ContextWitnessResultTransport.*;

/** Exact audited barrier lineage, generation1..64. No arbitrary Packet rebinding/admission. */
final class ContextSignedWitnessContinuationScope {
 static final class Receipt {
  final ContextSignedWitnessInitialBarrierAudit.Receipt barrier;
  final ContextSignedWitnessSuccessorBarrierAudit.Receipt later;
  final Receipt prior;final int generation;
  final ContextSignedWitnessFrameAudit.Receipt seeds;
  final ContextUniversalFrozenBranch.Limits limits;
  final int rows,auditWork,capacity;
  private Receipt(ContextSignedWitnessInitialBarrierAudit.Receipt barrier,int auditWork,int capacity){this.barrier=barrier;later=null;prior=null;generation=1;ContextSignedWitnessReceiver.Result r=barrier.result.initial.entry.receiver;seeds=r.frame;limits=r.limits;rows=r.rows;this.auditWork=auditWork;this.capacity=capacity;}
  private Receipt(ContextSignedWitnessSuccessorBarrierAudit.Receipt later,int auditWork,int capacity){this.later=later;prior=later.result.next.scope;barrier=prior.barrier;generation=prior.generation+1;seeds=prior.seeds;limits=prior.limits;rows=prior.rows;this.auditWork=auditWork;this.capacity=capacity;}
  ConstraintBarrier current(){return later==null?barrier.result.barrier:later.result.barrier;}
  ContextUniversalSignedUnitExchange.Exchange units(){return later==null?barrier.result.units:later.result.units;}
  ContextUniversalCompoundExchange.Exchange compounds(){return later==null?barrier.result.compounds:later.result.compounds;}

 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Receipt open(ContextSignedWitnessInitialBarrierAudit.Receipt barrier,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){
  require(barrier!=null&&auditWork>0&&capacity>=1&&capacity<=4096,"Actual bounded first-barrier scope required");Receipt r=new Receipt(barrier,auditWork,capacity);require(r.rows==rows&&r.limits==limits,"Different original successor policy");check(r,auditWork,capacity);return r;
 }
 static Receipt openLater(ContextSignedWitnessSuccessorBarrierAudit.Receipt later,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){require(later!=null&&later.result.next!=null&&later.result.next.scope!=null&&later.result.next.scope.generation<64&&auditWork>0&&capacity>=1&&capacity<=4096,"Bounded actual later-barrier receipt required");Receipt r=new Receipt(later,auditWork,capacity);require(r.rows==rows&&r.limits==limits,"Different later-barrier policy");check(r,auditWork,capacity);return r;}
 static void check(Receipt r,int auditWork,int capacity){
  require(r!=null&&auditWork>0&&capacity>=1&&capacity<=4096,"Actual bounded successor receipt required");Set<Receipt> seen=Collections.newSetFromMap(new IdentityHashMap<Receipt,Boolean>());int expected=r.generation;require(expected>=1&&expected<=64,"Barrier lineage generation limit");
  for(Receipt cursor=r;cursor!=null;cursor=cursor.prior){require(seen.add(cursor)&&cursor.generation==expected--&&cursor.barrier==r.barrier&&cursor.seeds==r.seeds&&cursor.limits==r.limits&&cursor.rows==r.rows,"Cyclic/mixed/nonconsecutive barrier lineage");if(cursor.generation==1)require(cursor.prior==null&&cursor.later==null,"Different root barrier lineage");else require(cursor.prior!=null&&cursor.later!=null&&cursor.later.result.next.scope==cursor.prior,"Different actual later-barrier parent");}require(expected==0,"Incomplete barrier lineage");
  if(r.later==null)ContextSignedWitnessInitialBarrierAudit.check(r.barrier,r.barrier.result.initial,r.seeds,r.rows,r.limits,auditWork,capacity);else ContextSignedWitnessSuccessorBarrierAudit.check(r.later,r.later.result.next,r.prior,r.rows,r.limits,auditWork,capacity);
 }
 static void frame(Receipt r,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool){
  require(r!=null&&input.signedContinuation==r&&input.signedSeeds==r.seeds&&input.x==r.seeds.frame.plan.x,"Different signed successor row scope");Snapshot target=input.foreign;require(target==null||r.seeds.frame.plan.foreign.contains(target),"Different actual successor snapshot");Cut cut=target==null?Cut.of(input.x):Cut.of(target);require(input.target.equals(cut)&&common==r.current().packet&&pool==r.current().privatePools.get(cut),"Different exact successor common/private frame");
 }
 /** Consumer work is charged here; complete historical replay has its own explicit child budget. */
 static void authenticate(Receipt r,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds,Runnable work){
  work.run();frame(r,input,common,pool);require(units==r.units()&&compounds==r.compounds(),"Different actual successor carrier pair");check(r,r.auditWork,r.capacity);
 }
}
