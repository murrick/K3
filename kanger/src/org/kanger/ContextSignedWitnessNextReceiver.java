/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** All receivers at an audited barrier successor; no stability or admission. */
final class ContextSignedWitnessNextReceiver {
 enum Status { CHECKED, REFUTED, INCOMPLETE }
 static final class Result {
  final Status status;final String phase,reason;
  final ContextSignedWitnessContinuationScope.Receipt scope;
  final Map<Cut,ContextUniversalFrozenConstraints.Result> checks;
  private Result(Status status,String phase,String reason,ContextSignedWitnessContinuationScope.Receipt scope,Map<Cut,ContextUniversalFrozenConstraints.Result> checks){this.status=status;this.phase=phase;this.reason=reason;this.scope=scope;this.checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks));}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result run(ContextSignedWitnessContinuationScope.Receipt scope,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork){
  String phase="successor-scope";
  try{require(scope!=null&&scope.rows==rows&&scope.limits==limits,"Exact successor scope/policy required");ContextSignedWitnessContinuationScope.check(scope,auditWork,scope.capacity);
   Map<Cut,Snapshot> targets=new LinkedHashMap<>();targets.put(Cut.of(scope.seeds.frame.plan.x),null);for(Snapshot target:scope.seeds.frame.plan.foreign)require(targets.put(Cut.of(target),target)==null,"Repeated successor cut");
   phase="all-row-preflight";Map<Cut,ContextUniversalNativeRows.Result> plans=new LinkedHashMap<>();for(Map.Entry<Cut,Snapshot> e:targets.entrySet()){ContextUniversalNativeRows.Result r=ContextUniversalNativeRows.inspectSignedContinuation(scope,e.getValue(),rows,limits.work,auditWork);require(r.classified&&r.withinShape,r.reason);plans.put(e.getKey(),r);}
   phase="all-successor-checks";ConstraintBarrier b=scope.current();Map<Cut,ContextUniversalFrozenConstraints.Result> checks=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalNativeRows.Result> e:plans.entrySet()){ContextUniversalFrozenConstraints.Result r=ContextUniversalFrozenConstraints.closeSignedCompounds(e.getValue(),b.packet,b.privatePools.get(e.getKey()),scope.units(),scope.compounds(),limits.rounds,limits.proofDepth,limits.clauses,limits.alternatives,limits.work);require(r.closed,r.reason);checks.put(e.getKey(),r);}
   phase="all-proof-audit";Runnable work=ContextGroundProductionRounds.budget(auditWork);boolean refuted=false;for(ContextUniversalFrozenConstraints.Result r:checks.values()){ContextUniversalFrozenConstraints.check(r,limits.proofDepth,work);refuted|=r.xRefuted||r.foreignRefuted;}
   return new Result(refuted?Status.REFUTED:Status.CHECKED,"checked","Audited successor receivers checked; no saturation or admission",scope,checks);
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,Collections.emptyMap());}
 }
 static void check(Result result,ContextSignedWitnessContinuationScope.Receipt scope,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork){require(result!=null&&result.status!=Status.INCOMPLETE&&result.scope==scope&&scope.rows==rows&&scope.limits==limits,"Different actual successor request");Result fresh=run(scope,rows,limits,auditWork);require(fresh.status==result.status&&fresh.checks.keySet().equals(result.checks.keySet()),"Different/incomplete successor replay");for(Cut cut:result.checks.keySet()){ContextUniversalFrozenConstraints.Result a=result.checks.get(cut),b=fresh.checks.get(cut);ContextUniversalFrozenConstraints.check(a,limits.proofDepth,ContextGroundProductionRounds.budget(auditWork));require(a.xRefuted==b.xRefuted&&a.foreignRefuted==b.foreignRefuted&&ContextUniversalGroupedLocalRounds.sameContents(a,b,ContextGroundProductionRounds.budget(auditWork)),"Different exact successor Source/depth proof inventory");}}
}
