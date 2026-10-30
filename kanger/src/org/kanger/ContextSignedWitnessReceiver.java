/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Explicit initial all-cut signed receiver checks. No exports, continuation, saturation or admission. */
final class ContextSignedWitnessReceiver {
 enum Status { CHECKED, REFUTED, INCOMPLETE }
 static final class Result {
  final Status status;final String phase,reason;final ContextSignedWitnessFrameAudit.Receipt frame;
  final ContextUniversalFrozenBranch.Limits limits;final int rows;
  final Map<Cut,ContextUniversalFrozenConstraints.Result> checks;
  private Result(Status status,String phase,String reason,ContextSignedWitnessFrameAudit.Receipt frame,ContextUniversalFrozenBranch.Limits limits,int rows,Map<Cut,ContextUniversalFrozenConstraints.Result> checks){this.status=status;this.phase=phase;this.reason=reason;this.frame=frame;this.limits=limits;this.rows=rows;this.checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks));}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result run(ContextSignedWitnessFrameAudit.Receipt receipt,int maximumRows,ContextUniversalFrozenBranch.Limits limits,int auditWork){
  if(maximumRows<1||maximumRows>64||limits==null||auditWork<1)throw new IllegalArgumentException("Bounded explicit signed receivers required");String phase="frame-audit";
  try{require(receipt!=null,"Audited signed frame required");SignedSeedFrame f=receipt.frame;ContextSignedWitnessFrameAudit.check(receipt,f.plan,f.plan.x,f.plan.foreign,f.plan.maximumRows,f.maximumRank,auditWork,auditWork);Map<Cut,Snapshot> targets=new LinkedHashMap<>();targets.put(Cut.of(f.plan.x),null);for(Snapshot target:f.plan.foreign)require(targets.put(Cut.of(target),target)==null,"Duplicate signed receiver");
   phase="all-row-preflight";Map<Cut,ContextUniversalNativeRows.Result> rows=new LinkedHashMap<>();for(Map.Entry<Cut,Snapshot> e:targets.entrySet()){ContextUniversalNativeRows.Result r=ContextUniversalNativeRows.inspectSigned(receipt,e.getValue(),maximumRows,limits.work,auditWork);require(r.classified&&r.withinShape,r.reason);rows.put(e.getKey(),r);}
   phase="all-receiver-checks";Map<Cut,ContextUniversalFrozenConstraints.Result> checks=new LinkedHashMap<>();for(Map.Entry<Cut,Snapshot> e:targets.entrySet()){PrivatePool pool=e.getValue()==null?null:ContextWitnessResultTransport.scopedSignedPool(receipt,e.getValue(),auditWork);ContextUniversalFrozenConstraints.Result r=ContextUniversalFrozenConstraints.closeSignedInstantiatedUnits(rows.get(e.getKey()),f.common,pool,null,limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work);require(r.closed,r.reason);checks.put(e.getKey(),r);}
   phase="all-proof-audit";Runnable work=ContextGroundProductionRounds.budget(auditWork);boolean refuted=false;for(ContextUniversalFrozenConstraints.Result r:checks.values()){ContextUniversalFrozenConstraints.check(r,limits.proofDepth,work);refuted|=r.xRefuted||r.foreignRefuted;}
   return new Result(refuted?Status.REFUTED:Status.CHECKED,"checked","All signed receivers closed and sourced proofs checked; no continuation or admission",receipt,limits,maximumRows,checks);
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,limits,maximumRows,Collections.emptyMap());}
 }
 static void check(Result result,ContextSignedWitnessFrameAudit.Receipt expected,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork){require(result!=null&&result.status!=Status.INCOMPLETE&&result.frame==expected&&result.limits==limits&&result.rows==rows,"Different actual signed receiver request");Result fresh=run(expected,rows,limits,auditWork);require(fresh.status==result.status&&fresh.checks.keySet().equals(result.checks.keySet()),"Incomplete/different independent signed receiver replay");for(Cut cut:result.checks.keySet()){ContextUniversalFrozenConstraints.Result a=result.checks.get(cut),b=fresh.checks.get(cut);ContextUniversalFrozenConstraints.check(a,limits.proofDepth,ContextGroundProductionRounds.budget(auditWork));require(a.xRefuted==b.xRefuted&&a.foreignRefuted==b.foreignRefuted&&ContextUniversalGroupedLocalRounds.sameContents(a,b,ContextGroundProductionRounds.budget(auditWork)),"Different full Source/depth signed proof inventory");}}
}
