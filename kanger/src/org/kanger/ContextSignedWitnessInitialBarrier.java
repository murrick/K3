/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextWitnessResultTransport.*;

/** First synchronous signed barrier only. No next-receiver, stability or admission claim. */
final class ContextSignedWitnessInitialBarrier {
 static final class Result {
  final boolean merged;final String phase,reason;
  final ContextSignedWitnessInitialEntry.Receipt initial;
  final ConstraintBarrier barrier;
  final ContextUniversalSignedUnitExchange.Exchange units;
  final ContextUniversalCompoundExchange.Exchange compounds;
  private Result(boolean merged,String phase,String reason,ContextSignedWitnessInitialEntry.Receipt initial,ConstraintBarrier barrier,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds){this.merged=merged;this.phase=phase;this.reason=reason;this.initial=initial;this.barrier=barrier;this.units=units;this.compounds=compounds;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result run(ContextSignedWitnessInitialEntry.Receipt initial,ContextSignedWitnessFrameAudit.Receipt frame,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){
  String phase="initial-replay";
  try{ContextSignedWitnessInitialEntry.check(initial,frame,rows,limits,auditWork,capacity);ContextSignedWitnessInitialEntry.Entry e=initial.entry;
   phase="all-products";Map<Cut,ConstraintTransfer> outputs=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> c:e.receiver.checks.entrySet()){ConstraintTransfer t=c.getValue().pool==null?ContextWitnessResultTransport.transferConstraints(c.getValue(),e.common,limits.factDepth,limits.work):ContextWitnessResultTransport.transferConstraintsPrivate(c.getValue(),c.getValue().pool,limits.factDepth,limits.work);require(t.transferred,t.reason);ContextUniversalSignedProductAudit.Result a=ContextUniversalSignedProductAudit.bind(c.getValue(),t,65536,auditWork);require(a.bound,a.reason);outputs.put(c.getKey(),t);}
   phase="all-cut-merge";ConstraintBarrier b=ContextWitnessResultTransport.mergeSignedWitnessInitial(e,outputs,limits.work);require(b.merged,b.reason);
   phase="carrier-rebase";ContextUniversalSignedUnitExchange.Exchange u=ContextUniversalSignedUnitExchange.rebase(e.units,b,limits.work);ContextUniversalCompoundExchange.Exchange c=ContextUniversalCompoundExchange.rebase(e.compounds,b,limits.work);
   Result result=new Result(true,"barrier-built","First barrier only; next receiver remains unqualified",initial,b,u,c);phase="direct-barrier-audit";ContextSignedWitnessInitialBarrierAudit.inspect(result,initial,frame,rows,limits,auditWork,capacity);return result;
  }catch(Exception failure){return new Result(false,phase,failure.toString(),null,null,null,null);}
 }
}
