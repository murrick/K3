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
  final Result base;final ContextUniversalGroupedLocalRounds.Limits localLimits;
  final Map<Cut,ContextUniversalGroupedLocalRounds.Certificate> certificates;
  private Result(Status status,String phase,String reason,ContextSignedWitnessContinuationScope.Receipt scope,Map<Cut,ContextUniversalFrozenConstraints.Result> checks){this(status,phase,reason,scope,checks,null,null,Collections.emptyMap());}
  private Result(Status status,String phase,String reason,ContextSignedWitnessContinuationScope.Receipt scope,Map<Cut,ContextUniversalFrozenConstraints.Result> checks,Result base,ContextUniversalGroupedLocalRounds.Limits localLimits,Map<Cut,ContextUniversalGroupedLocalRounds.Certificate> certificates){this.status=status;this.phase=phase;this.reason=reason;this.scope=scope;this.checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks));this.base=base;this.localLimits=localLimits;this.certificates=Collections.unmodifiableMap(new LinkedHashMap<>(certificates));}
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
 static void check(Result result,ContextSignedWitnessContinuationScope.Receipt scope,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork){if(result!=null&&result.localLimits!=null){checkGrouped(result,scope,rows,limits,result.localLimits,auditWork,1);return;}require(result!=null&&result.status!=Status.INCOMPLETE&&result.scope==scope&&scope.rows==rows&&scope.limits==limits,"Different actual successor request");Result fresh=run(scope,rows,limits,auditWork);require(fresh.status==result.status&&fresh.checks.keySet().equals(result.checks.keySet()),"Different/incomplete successor replay");for(Cut cut:result.checks.keySet()){ContextUniversalFrozenConstraints.Result a=result.checks.get(cut),b=fresh.checks.get(cut);ContextUniversalFrozenConstraints.check(a,limits.proofDepth,ContextGroundProductionRounds.budget(auditWork));require(a.xRefuted==b.xRefuted&&a.foreignRefuted==b.foreignRefuted&&ContextUniversalGroupedLocalRounds.sameContents(a,b,ContextGroundProductionRounds.budget(auditWork)),"Different exact successor Source/depth proof inventory");}}
 static void localPolicy(ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits local){require(limits!=null&&local!=null&&local.kernelRounds==limits.rounds&&local.depth==limits.proofDepth&&local.clauses==limits.clauses&&local.alternatives==limits.alternatives,"Different local/kernel receiver policy");}
 static Result runGrouped(ContextSignedWitnessContinuationScope.Receipt scope,int rows,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits local,int auditWork,int capacity){
  String phase="local-policy";
  try{localPolicy(limits,local);require(capacity>=1&&capacity<=4096,"Bounded local validation capacity required");phase="ordinary-all-cut-base";Result base=run(scope,rows,limits,auditWork);require(base.status!=Status.INCOMPLETE,base.phase+": "+base.reason);
   phase="all-local-completions";Map<Cut,ContextUniversalGroupedLocalRounds.Certificate> certificates=new LinkedHashMap<>();Map<Cut,ContextUniversalFrozenConstraints.Result> checks=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:base.checks.entrySet()){ContextUniversalGroupedLocalRounds.Closure c=ContextUniversalGroupedLocalRounds.runWithValidationReuse(e.getValue(),local,capacity);require(c.stable,c.reason);certificates.put(e.getKey(),c.output);checks.put(e.getKey(),c.output.output);}
   boolean refuted=false;for(ContextUniversalFrozenConstraints.Result c:checks.values())refuted|=c.xRefuted||c.foreignRefuted;Result result=new Result(refuted?Status.REFUTED:Status.CHECKED,"locally-complete","All fixed-incoming grouped local certificates checked; no global equilibrium",scope,checks,base,local,certificates);phase="all-local-certificate-audit";checkGrouped(result,scope,rows,limits,local,auditWork,capacity);return result;
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,Collections.emptyMap());}
 }
 static void checkGrouped(Result r,ContextSignedWitnessContinuationScope.Receipt scope,int rows,ContextUniversalFrozenBranch.Limits limits,ContextUniversalGroupedLocalRounds.Limits local,int auditWork,int capacity){
  localPolicy(limits,local);require(r!=null&&r.status!=Status.INCOMPLETE&&r.scope==scope&&r.localLimits==local&&r.base!=null&&r.base.localLimits==null&&r.certificates.keySet().equals(r.base.checks.keySet())&&r.checks.keySet().equals(r.base.checks.keySet()),"Different complete grouped receiver ledger/policy");check(r.base,scope,rows,limits,auditWork);boolean refuted=false;
  try(ContextUniversalValidationSession.Scope session=ContextUniversalValidationSession.open(auditWork,capacity)){for(Cut cut:r.checks.keySet()){session.work.run();ContextUniversalGroupedLocalRounds.Certificate c=r.certificates.get(cut);require(c!=null&&c.initial==r.base.checks.get(cut)&&c.output==r.checks.get(cut)&&c.limits==local,"Different actual complete local certificate owner");ContextUniversalGroupedLocalRounds.check(c,session.work);refuted|=c.output.xRefuted||c.output.foreignRefuted;}}require(r.status==(refuted?Status.REFUTED:Status.CHECKED),"Different full grouped receiver refutation status");
 }
 static void productOwner(Result next,Cut cut,ConstraintTransfer transfer){
  if(next.localLimits==null)require(transfer.certificate==null&&transfer.bindings==null,"Ordinary successor cannot carry alleged grouped authority");else require(next.certificates.get(cut)==transfer.certificate&&transfer.certificate!=null&&transfer.certificate.output==transfer.proof&&transfer.bindings!=null&&transfer.bindings.coverage==transfer.proof,"Different actual grouped product certificate/bindings");
 }

}
