/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Read-only signed-profile candidate diagnostic. No result is a write/eligibility capability. */
public final class ContextSignedCandidateInspection {
 private ContextSignedCandidateInspection(){}
 public enum Status { QUIESCENT_SUPPLIED, REFUTED, INCOMPLETE }
 public static final class Limits {
  public final int rows,rank,rounds,depth,preparation,events,work,audit,capacity;
  private final ContextSignedWitnessNativeCandidate.Policy policy;
  public Limits(int rows,int rank,int rounds,int depth,int preparation,int events,int work,int audit,int capacity){
   if(audit<1)throw new IllegalArgumentException("Bounded independent signed diagnostic audit required");ContextUniversalFrozenBranch.Limits kernel=new ContextUniversalFrozenBranch.Limits(16,depth,depth,512,256,work,65536,65536);ContextUniversalGroupedLocalRounds.Limits local=new ContextUniversalGroupedLocalRounds.Limits(8,16,512,512,256,depth,65536,work);
   policy=new ContextSignedWitnessNativeCandidate.Policy(new ContextSignedWitnessNativeBranch.Policy(rows,rank,rounds,work,capacity,kernel,local),rows,preparation,events);this.rows=rows;this.rank=rank;this.rounds=rounds;this.depth=depth;this.preparation=preparation;this.events=events;this.work=work;this.audit=audit;this.capacity=capacity;
  }
 }
 public static final class Opposition {
  public final Set<UUID> targetContexts;public final String trustedGroup;
  public final boolean usesCandidate,xRefuted,foreignRefuted,initial;
  /** Number of recorded successor attempts in the actual termination ledger, not native proof depth. */
  public final int recordedTransitions,depth;public final Source candidate;public final Set<Source> originalSources;
  private Opposition(ContextWitnessResultTransport.Cut cut,ContextSignedWitnessNativeCandidate.Attribution a,ContextSignedWitnessNativeBranch.Receipt branch){Set<UUID> contexts=new LinkedHashSet<>();if(cut.trustedGroup==null)contexts.add(cut.context);else contexts.addAll(cut.members.keySet());targetContexts=Collections.unmodifiableSet(contexts);trustedGroup=cut.trustedGroup;usesCandidate=a.usesCandidate;ContextUniversalFrozenConstraints.Result result=ContextSignedWitnessNativeCandidateAudit.checks(branch).get(cut);xRefuted=result.xRefuted;foreignRefuted=result.foreignRefuted;initial=branch.initialRejection!=null;recordedTransitions=initial?0:branch.termination.result.history.size();depth=a.proof.support.depth;candidate=a.candidate;originalSources=Collections.unmodifiableSet(new LinkedHashSet<>(a.originalSources));}
 }
 public static final class Result {
  public final Status status;public final String phase,reason;
  /** Immutable historical metadata; no live snapshot, receipt or publication capability escapes. */
  public final UUID baseContext;public final long baseRevision;public final String baseFingerprint;
  public final List<String> foreignFingerprints;public final Source candidate;public final List<Opposition> oppositions;
  private Result(Status status,String phase,String reason,Snapshot base,List<Snapshot> foreign,Source candidate,List<Opposition> oppositions){this.status=status;this.phase=phase;this.reason=reason;this.candidate=candidate;baseContext=base==null?null:base.inventory.context;baseRevision=base==null?0:base.inventory.revision;baseFingerprint=base==null?null:base.inventory.fingerprint;List<String> prints=new ArrayList<>();if(foreign!=null)for(Snapshot s:foreign)prints.add(s.inventory.fingerprint);foreignFingerprints=Collections.unmodifiableList(prints);this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));}
 }
 /** Detached use remains historical. Owning storage callers must guard currentness around this call. */
 public static Result inspect(Snapshot base,List<Snapshot> foreign,String statement,Limits limits){String phase="request";
  try{if(base==null||foreign==null||limits==null)throw new IllegalArgumentException("Actual bounded signed diagnostic required");List<Snapshot> cuts=new ArrayList<>(foreign);phase="signed-candidate";ContextSignedWitnessNativeCandidate.Result trial=ContextSignedWitnessNativeCandidate.run(base,cuts,statement,limits.policy);if(trial.status==ContextSignedWitnessNativeBranch.Status.INCOMPLETE)throw new IllegalStateException(trial.phase+": "+trial.reason);ContextSignedWitnessNativeCandidate.Receipt receipt=trial.output!=null?trial.output:trial.rejection;
   phase="signed-candidate-audit";ContextSignedWitnessNativeCandidateAudit.check(receipt,base,cuts,statement,limits.policy,limits.audit,limits.capacity);List<Opposition> oppositions=new ArrayList<>();for(Map.Entry<ContextWitnessResultTransport.Cut,List<ContextSignedWitnessNativeCandidate.Attribution>> e:receipt.oppositions.entrySet())for(ContextSignedWitnessNativeCandidate.Attribution a:e.getValue())oppositions.add(new Opposition(e.getKey(),a,receipt.branch));
   return new Result(trial.status==ContextSignedWitnessNativeBranch.Status.QUIESCENT_SUPPLIED?Status.QUIESCENT_SUPPLIED:Status.REFUTED,"historical-supplied","Exact supplied signed-profile evidence only; full-family/provider/language/model/write eligibility remains",base,cuts,receipt.artifact.candidate,oppositions);
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,null,null,Collections.emptyList());}
 }
}
