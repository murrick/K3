/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Whole prepared signed X diagnostic; never append/rebase a candidate or authorize settlement. */
public final class ContextSignedBranchInspection {
 private ContextSignedBranchInspection(){}
 public enum Status { QUIESCENT_SUPPLIED, REFUTED, INCOMPLETE }
 public static final class View {
  public final UUID context;public final long revision;public final String fingerprint;public final List<String> initialization;
  private View(ContextProductionInventory i){context=i.context;revision=i.revision;fingerprint=i.fingerprint;initialization=Collections.unmodifiableList(new ArrayList<>(i.initialization));}
 }
 public static final class CutView {
  public final String trustedGroup;public final List<View> members;
  private CutView(Snapshot cut){trustedGroup=cut.trustedGroup;List<View> all=new ArrayList<>();for(Snapshot member:cut.physicalMembers())all.add(new View(member.inventory));members=Collections.unmodifiableList(all);}
 }
 public static final class Opposition {
  public final Set<UUID> targetContexts;public final String trustedGroup;public final boolean xRefuted,foreignRefuted,initial;public final int recordedTransitions,depth;public final Set<Source> sources;
  private Opposition(ContextWitnessResultTransport.Cut cut,ContextUniversalFrozenConstraints.Result result,ContextUniversalFrozenConstraints.Proof proof,ContextSignedWitnessNativeBranch.Receipt branch){Set<UUID> contexts=new LinkedHashSet<>();if(cut.trustedGroup==null)contexts.add(cut.context);else contexts.addAll(cut.members.keySet());targetContexts=Collections.unmodifiableSet(contexts);trustedGroup=cut.trustedGroup;xRefuted=result.xRefuted;foreignRefuted=result.foreignRefuted;initial=branch.initialRejection!=null;recordedTransitions=initial?0:branch.termination.result.history.size();depth=proof.support.depth;sources=Collections.unmodifiableSet(new LinkedHashSet<>(proof.support.sources));}
 }
 static List<Opposition> oppositions(ContextSignedWitnessNativeBranch.Receipt branch){List<Opposition> out=new ArrayList<>();for(Map.Entry<ContextWitnessResultTransport.Cut,ContextUniversalFrozenConstraints.Result> e:ContextSignedWitnessNativeCandidateAudit.checks(branch).entrySet())for(ContextUniversalFrozenConstraints.Proof proof:e.getValue().emptyProofs)out.add(new Opposition(e.getKey(),e.getValue(),proof,branch));return Collections.unmodifiableList(out);}
 public static final class Result {
  public final Status status;public final String phase,reason;public final View proposed;public final List<CutView> foreign;public final List<Opposition> oppositions;
  private Result(Status status,String phase,String reason,Snapshot x,List<Snapshot> cuts,List<Opposition> oppositions){this.status=status;this.phase=phase;this.reason=reason;proposed=x==null?null:new View(x.inventory);List<CutView> all=new ArrayList<>();if(cuts!=null)for(Snapshot cut:cuts)all.add(new CutView(cut));foreign=Collections.unmodifiableList(all);this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));}
 }
 /** Detached metadata is historical. Storage must guard the actual pending owner and views. */
 public static Result inspect(Snapshot x,List<Snapshot> foreign,ContextSignedCandidateInspection.Limits limits){String phase="signed-branch";
  try{if(x==null||foreign==null||limits==null)throw new IllegalArgumentException("Actual whole signed X request required");List<Snapshot> cuts=new ArrayList<>(foreign);ContextSignedPendingGuard.Result r=ContextSignedPendingGuard.inspect(x,cuts,limits);if(r.status==ContextSignedPendingGuard.Status.INCOMPLETE)return new Result(Status.INCOMPLETE,r.phase,r.reason,null,null,Collections.emptyList());return new Result(r.status==ContextSignedPendingGuard.Status.REFUTED?Status.REFUTED:Status.QUIESCENT_SUPPLIED,r.phase,r.reason,x,cuts,r.evidence==null?Collections.emptyList():r.evidence.getOppositions());}catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,null,Collections.emptyList());}
 }
}
