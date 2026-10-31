/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Sealed historical signed refutation only. Scoped quiescence never grants admission. */
public final class ContextSignedPendingGuard {
 private ContextSignedPendingGuard(){}
 public enum Status { SCOPED_QUIESCENT, REFUTED, INCOMPLETE }
 public static final class Evidence {
  private final Snapshot x;private final List<Snapshot> foreign;private final ContextSignedWitnessNativeBranch.Policy policy;
  /** Retain ACTUAL full original/continuation proof ledger, not scalar summaries alone. */
  private final ContextSignedWitnessNativeBranch.Receipt branch;
  private final List<ContextSignedBranchInspection.Opposition> oppositions;
  private Evidence(Snapshot x,List<Snapshot> foreign,ContextSignedWitnessNativeBranch.Policy policy,ContextSignedWitnessNativeBranch.Receipt branch){this.x=x;this.foreign=Collections.unmodifiableList(new ArrayList<>(foreign));this.policy=policy;this.branch=branch;oppositions=ContextSignedBranchInspection.oppositions(branch);}
  public List<ContextSignedBranchInspection.Opposition> getOppositions(){return oppositions;}
  /** Fresh direct exact-ledger audit; no controller replay or live-provider/write capability. */
  public void check(int maximumWork,int capacity){verify(this,maximumWork,capacity);}
 }
 public static final class Result {
  public final Status status;public final String phase,reason;public final Evidence evidence;
  private Result(Status status,String phase,String reason,Evidence evidence){this.status=status;this.phase=phase;this.reason=reason;this.evidence=evidence;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 private static void verify(Evidence e,int work,int capacity){
  require(e!=null&&work>0&&capacity>=1&&capacity<=4096&&e.branch!=null&&e.branch.status==ContextSignedWitnessNativeBranch.Status.REFUTED,"Actual bounded signed refutation ledger required");ContextSignedWitnessNativeBranchAudit.check(e.branch,e.x,e.foreign,e.policy,work,capacity);List<ContextSignedBranchInspection.Opposition> fresh=ContextSignedBranchInspection.oppositions(e.branch);require(!fresh.isEmpty()&&fresh.size()==e.oppositions.size(),"Complete owned signed opposition inventory required");Runnable budget=ContextGroundProductionRounds.budget(work);
  for(int n=0;n<fresh.size();++n){budget.run();ContextSignedBranchInspection.Opposition a=fresh.get(n),b=e.oppositions.get(n);require(b!=null&&a.targetContexts.equals(b.targetContexts)&&Objects.equals(a.trustedGroup,b.trustedGroup)&&a.xRefuted==b.xRefuted&&a.foreignRefuted==b.foreignRefuted&&a.initial==b.initial&&a.recordedTransitions==b.recordedTransitions&&a.depth==b.depth&&a.sources.equals(b.sources),"Different exact proof flags/phase/Source grade metadata");for(UUID context:a.targetContexts)budget.run();for(ContextProductionCapture.Source source:a.sources)budget.run();}
 }
 /** Package-only bridge for the shared fresh model computation; never trusts diagnostic metadata. */
 static Evidence sealRefutation(Snapshot x,List<Snapshot> foreign,ContextSignedWitnessNativeBranch.Policy policy,ContextSignedWitnessNativeBranch.Receipt branch,int work,int capacity){
  Evidence e=new Evidence(x,foreign,policy,branch);verify(e,work,capacity);return e;
 }
 /** Actual owning provider must check currentness around this operation and retain all family gates. */
 public static Result inspect(Snapshot x,List<Snapshot> foreign,ContextSignedCandidateInspection.Limits limits){String phase="signed-native-branch";
  try{require(x!=null&&foreign!=null&&limits!=null,"Actual complete signed pending request required");List<Snapshot> cuts=new ArrayList<>(foreign);ContextSignedWitnessNativeBranch.Policy p=limits.branchPolicy();ContextSignedWitnessNativeBranch.Result r=ContextSignedWitnessNativeBranch.run(x,cuts,p);require(r.status!=ContextSignedWitnessNativeBranch.Status.INCOMPLETE,r.phase+": "+r.reason);
   if(r.status==ContextSignedWitnessNativeBranch.Status.REFUTED){phase="signed-refutation-audit";Evidence e=new Evidence(x,cuts,p,r.rejection);verify(e,limits.audit,limits.capacity);return new Result(Status.REFUTED,phase,"Exact signed refusal ledger freshly audited; no settlement permission",e);}
   phase="signed-quiescence-audit";ContextSignedWitnessNativeBranchAudit.check(r.output,x,cuts,p,limits.audit,limits.capacity);return new Result(Status.SCOPED_QUIESCENT,phase,"Supplied profile quiescent; mandatory full-family/provider/language/model eligibility remains",null);
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null);}
 }
}
