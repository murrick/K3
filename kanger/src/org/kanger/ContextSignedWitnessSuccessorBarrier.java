/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextWitnessResultTransport.*;

/** All first-successor products form a second barrier. No later receiver/stability authority. */
final class ContextSignedWitnessSuccessorBarrier {
 static final class Result {
  final boolean merged;final String phase,reason;
  final ContextSignedWitnessNextReceiver.Result next;
  final ConstraintBarrier barrier;
  final ContextUniversalSignedUnitExchange.Exchange units;
  final ContextUniversalCompoundExchange.Exchange compounds;
  private Result(boolean merged,String phase,String reason,ContextSignedWitnessNextReceiver.Result next,ConstraintBarrier barrier,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds){this.merged=merged;this.phase=phase;this.reason=reason;this.next=next;this.barrier=barrier;this.units=units;this.compounds=compounds;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result run(ContextSignedWitnessNextReceiver.Result next,ContextSignedWitnessContinuationScope.Receipt scope,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){
  String phase="successor-replay";
  try{require(capacity>=1&&capacity<=4096,"Bounded carrier capacity required");ContextSignedWitnessNextReceiver.check(next,scope,rows,limits,auditWork);require(next.status==ContextSignedWitnessNextReceiver.Status.CHECKED,"Refuted successor has no continuation barrier");Packet common=scope.barrier.result.barrier.packet;List<Cut> cuts=new ArrayList<>(next.checks.keySet());
   phase="all-products";Map<Cut,ConstraintTransfer> outputs=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> c:next.checks.entrySet()){ConstraintTransfer t=c.getValue().pool==null?ContextWitnessResultTransport.transferConstraints(c.getValue(),common,limits.factDepth,limits.work):ContextWitnessResultTransport.transferConstraintsPrivate(c.getValue(),c.getValue().pool,limits.factDepth,limits.work);require(t.transferred,t.reason);ContextUniversalSignedProductAudit.Result a=ContextUniversalSignedProductAudit.bind(c.getValue(),t,65536,auditWork);require(a.bound,a.reason);outputs.put(c.getKey(),t);}
   phase="unit-collection";ContextUniversalSignedUnitExchange.Collection u=ContextUniversalSignedUnitExchange.collectWithValidationReuse(scope.seeds.frame.plan.x,common,cuts,next.checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;require(u.collected,u.reason);
   phase="compound-collection";ContextUniversalCompoundExchange.Collection c=ContextUniversalCompoundExchange.collectWithValidationReuse(scope.seeds.frame.plan.x,common,cuts,next.checks,limits.clauses,limits.alternatives,limits.factDepth,limits.work,capacity).collection;require(c.collected,c.reason);
   phase="all-cut-merge";ConstraintBarrier b=ContextWitnessResultTransport.mergeSignedWitnessSuccessor(next,outputs,limits.work);require(b.merged,b.reason);
   phase="carrier-rebase";ContextUniversalSignedUnitExchange.Exchange units=ContextUniversalSignedUnitExchange.rebase(u.output,b,limits.work);ContextUniversalCompoundExchange.Exchange compounds=ContextUniversalCompoundExchange.rebase(c.output,b,limits.work);
   Result result=new Result(true,"barrier-built","Second barrier only; no stable/later-receiver claim",next,b,units,compounds);phase="direct-successor-barrier-audit";ContextSignedWitnessSuccessorBarrierAudit.inspect(result,next,scope,rows,limits,auditWork,capacity);return result;
  }catch(Exception failure){return new Result(false,phase,failure.toString(),null,null,null,null);}
 }
}
