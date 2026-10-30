/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextWitnessResultTransport.*;

/** Detached signed initial handoff. Deliberately not a legacy Entry or continuation permission. */
final class ContextSignedWitnessInitialEntry {
 enum Status { READY, REFUTED, INCOMPLETE }
 static final class Entry {
  final ContextSignedWitnessReceiver.Result receiver;
  final Packet common;
  final Map<Cut,PrivatePool> pools;
  final ContextUniversalSignedUnitExchange.Exchange units;
  final ContextUniversalCompoundExchange.Exchange compounds;
  private Entry(ContextSignedWitnessReceiver.Result receiver,Map<Cut,PrivatePool> pools,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds){this.receiver=receiver;common=receiver.frame.frame.common;this.pools=Collections.unmodifiableMap(new LinkedHashMap<>(pools));this.units=units;this.compounds=compounds;}
 }
 static final class Result {
  final Status status;final String phase,reason;final Entry entry;
  final ContextSignedWitnessReceiver.Result rejection;
  private Result(Status status,String phase,String reason,Entry entry,ContextSignedWitnessReceiver.Result rejection){this.status=status;this.phase=phase;this.reason=reason;this.entry=entry;this.rejection=rejection;}
 }
 static final class Receipt {
  final Entry entry;
  private Receipt(Entry entry){this.entry=entry;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result assemble(ContextSignedWitnessFrameAudit.Receipt frame,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){
  String phase="policy";
  try{require(capacity>=1&&capacity<=4096,"Bounded carrier validation capacity required");
   phase="receivers";ContextSignedWitnessReceiver.Result r=ContextSignedWitnessReceiver.run(frame,rows,limits,auditWork);require(r.status!=ContextSignedWitnessReceiver.Status.INCOMPLETE,r.phase+": "+r.reason);
   phase="receiver-replay";ContextSignedWitnessReceiver.check(r,frame,rows,limits,auditWork);
   if(r.status==ContextSignedWitnessReceiver.Status.REFUTED)return new Result(Status.REFUTED,phase,"Complete all-cut sourced rejection; no entry or carrier prefix",null,r);
   Map<Cut,PrivatePool> pools=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:r.checks.entrySet())if(e.getValue().pool!=null)pools.put(e.getKey(),e.getValue().pool);
   List<Cut> cuts=new ArrayList<>(r.checks.keySet());Packet common=frame.frame.common;
   phase="unit-collection";ContextUniversalSignedUnitExchange.Collection u=ContextUniversalSignedUnitExchange.collectWithValidationReuse(frame.frame.plan.x,common,cuts,r.checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;require(u.collected,u.reason);
   phase="compound-collection";ContextUniversalCompoundExchange.Collection c=ContextUniversalCompoundExchange.collectWithValidationReuse(frame.frame.plan.x,common,cuts,r.checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;require(c.collected,c.reason);
   Entry e=new Entry(r,pools,u.output,c.output);phase="entry-audit";inspect(e,frame,rows,limits,auditWork,capacity);
   return new Result(Status.READY,phase,"Exact initial receiver/carrier handoff only; no continuation or admission",e,null);
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,null);}
 }
 /** Fresh receiver replay and independent carrier reconstruction, never assembly replay. */
 static Receipt inspect(Entry e,ContextSignedWitnessFrameAudit.Receipt frame,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){
  require(capacity>=1&&capacity<=4096&&e!=null,"Actual bounded signed initial entry required");
  ContextSignedWitnessReceiver.check(e.receiver,frame,rows,limits,auditWork);require(e.receiver.status==ContextSignedWitnessReceiver.Status.CHECKED,"Refuted receiver cannot be an initial entry");
  require(e.common==frame.frame.common,"Different original common seed frame");Runnable work=ContextGroundProductionRounds.budget(auditWork);
  Set<Cut> expected=new LinkedHashSet<>();for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> c:e.receiver.checks.entrySet()){work.run();PrivatePool pool=c.getValue().pool;if(pool!=null){expected.add(c.getKey());require(e.pools.get(c.getKey())==pool&&pool.signedSeeds==frame&&pool.common==e.common,"Different actual signed receiver private pool");}}
  require(e.pools.keySet().equals(expected),"Different complete private inventory");
  require(e.units!=null&&e.compounds!=null&&e.units.x==frame.frame.plan.x&&e.compounds.x==frame.frame.plan.x&&e.units.common==e.common&&e.compounds.common==e.common&&e.units.checks.equals(e.receiver.checks)&&e.compounds.checks.equals(e.receiver.checks),"Different actual owning initial carriers");
  require(e.units.prior==null&&e.units.barrier==null&&e.compounds.prior==null&&e.compounds.barrier==null,"Continuation is not an original initial handoff");
  require(e.compounds.maximumClauses==limits.clauses&&e.compounds.maximumAlternatives==limits.alternatives&&e.compounds.maximumDepth==limits.factDepth,"Different initial carrier policy");
  ContextUniversalSignedUnitExchange.check(e.units,frame.frame.plan.x,e.common,limits.factDepth,work);ContextUniversalCompoundExchange.check(e.compounds,frame.frame.plan.x,e.common,limits.factDepth,work);
  List<Cut> cuts=new ArrayList<>(e.receiver.checks.keySet());
  ContextUniversalSignedUnitExchange.Collection u=ContextUniversalSignedUnitExchange.collectWithValidationReuse(frame.frame.plan.x,e.common,cuts,e.receiver.checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;
  ContextUniversalCompoundExchange.Collection c=ContextUniversalCompoundExchange.collectWithValidationReuse(frame.frame.plan.x,e.common,cuts,e.receiver.checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;
  require(u.collected&&c.collected&&ContextUniversalSignedUnitExchange.sameContents(u.output,e.units,limits.work)&&ContextUniversalCompoundExchange.sameContents(c.output,e.compounds,limits.work),"Different independently reconstructed carrier Source/depth inventory");return new Receipt(e);
 }
 static void check(Receipt receipt,ContextSignedWitnessFrameAudit.Receipt frame,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){require(receipt!=null,"Actual signed initial receipt required");inspect(receipt.entry,frame,rows,limits,auditWork,capacity);}
}
