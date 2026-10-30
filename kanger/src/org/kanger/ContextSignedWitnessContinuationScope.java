/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Exact FIRST-barrier successor scope. No generic Packet rebinding or later-round authority. */
final class ContextSignedWitnessContinuationScope {
 static final class Receipt {
  final ContextSignedWitnessInitialBarrierAudit.Receipt barrier;
  final ContextSignedWitnessFrameAudit.Receipt seeds;
  final ContextUniversalFrozenBranch.Limits limits;
  final int rows,auditWork,capacity;
  private Receipt(ContextSignedWitnessInitialBarrierAudit.Receipt barrier,int auditWork,int capacity){this.barrier=barrier;ContextSignedWitnessReceiver.Result r=barrier.result.initial.entry.receiver;seeds=r.frame;limits=r.limits;rows=r.rows;this.auditWork=auditWork;this.capacity=capacity;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Receipt open(ContextSignedWitnessInitialBarrierAudit.Receipt barrier,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){
  require(barrier!=null&&auditWork>0&&capacity>=1&&capacity<=4096,"Actual bounded first-barrier scope required");Receipt r=new Receipt(barrier,auditWork,capacity);require(r.rows==rows&&r.limits==limits,"Different original successor policy");check(r,auditWork,capacity);return r;
 }
 static void check(Receipt r,int auditWork,int capacity){require(r!=null&&auditWork>0&&capacity>=1&&capacity<=4096,"Actual bounded successor receipt required");ContextSignedWitnessInitialBarrierAudit.check(r.barrier,r.barrier.result.initial,r.seeds,r.rows,r.limits,auditWork,capacity);}
 static void frame(Receipt r,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool){
  require(r!=null&&input.signedContinuation==r&&input.signedSeeds==r.seeds&&input.x==r.seeds.frame.plan.x,"Different signed successor row scope");Snapshot target=input.foreign;require(target==null||r.seeds.frame.plan.foreign.contains(target),"Different actual successor snapshot");Cut cut=target==null?Cut.of(input.x):Cut.of(target);require(input.target.equals(cut)&&common==r.barrier.result.barrier.packet&&pool==r.barrier.result.barrier.privatePools.get(cut),"Different exact successor common/private frame");
 }
 /** Consumer work is charged here; complete historical replay has its own explicit child budget. */
 static void authenticate(Receipt r,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds,Runnable work){
  work.run();frame(r,input,common,pool);require(units==r.barrier.result.units&&compounds==r.barrier.result.compounds,"Different actual successor carrier pair");check(r,r.auditWork,r.capacity);
 }
}
