/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Fresh bounded unary qualification; owning provider must guard currentness and settlement. */
public final class ContextSignedModelGuard {
 private ContextSignedModelGuard(){}
 public enum Status { SCOPED_MODELLED_UNARY, REFUTED, INCOMPLETE }
 public static final class Result {
  public final Status status;public final String phase,reason;public final ContextSignedPendingGuard.Evidence evidence;
  private Result(Status status,String phase,String reason,ContextSignedPendingGuard.Evidence evidence){this.status=status;this.phase=phase;this.reason=reason;this.evidence=evidence;}
 }
 /** Inputs are fresh actual whole snapshots/basis only; no prior model, diagnostic or receipt input. */
 public static Result inspect(Snapshot x,List<Snapshot> foreign,List<SemanticTermSnapshot> namedBasis,ContextSignedModelInspection.Limits limits){
  ContextSignedModelEvaluation.Result r=ContextSignedModelEvaluation.run(x,foreign,namedBasis,limits);
  if(r.status==ContextSignedModelInspection.Status.INCOMPLETE)return new Result(Status.INCOMPLETE,r.phase,r.reason,null);
  if(r.status==ContextSignedModelInspection.Status.MODELLED_SUPPLIED)return new Result(Status.SCOPED_MODELLED_UNARY,r.phase,"Exact original and actual-final unary model checked; owning currentness/legacy gates still mandatory",null);
  try{return new Result(Status.REFUTED,"signed-refutation-audit","Actual signed refutation; never failed model search",ContextSignedPendingGuard.sealRefutation(r.x,r.cuts,limits.nativeLimits.branchPolicy(),r.branch,limits.nativeLimits.audit,limits.nativeLimits.capacity));}
  catch(Exception failure){return new Result(Status.INCOMPLETE,"signed-refutation-audit",failure.toString(),null);}
 }
}
