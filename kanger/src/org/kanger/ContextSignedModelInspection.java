/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.Cut;

/** Historical unary-model diagnostic. Storage must guard its actual owning operation before/after. */
public final class ContextSignedModelInspection {
 private ContextSignedModelInspection(){}
 public enum Status { MODELLED_SUPPLIED, REFUTED, INCOMPLETE }
 public static final class Limits {
  public final ContextSignedCandidateInspection.Limits nativeLimits;
  final ContextSignedUnaryModelSearch.Policy search;
  /** Separate construction/child audit bounds; one explicit named basis is used in EVERY cut. */
  public Limits(ContextSignedCandidateInspection.Limits nativeLimits,int candidates,int cells,int witnesses,int constructionWork,int points,int predicates,int originalAudit,int extensionCandidates,int extensionCells,int extensionOrigins,int extensionWork,int obligations,int finalAudit){
   if(nativeLimits==null)throw new IllegalArgumentException("Explicit native/model diagnostic limits required");this.nativeLimits=nativeLimits;ContextSignedUnaryModel.Policy original=new ContextSignedUnaryModel.Policy(nativeLimits.rows,points,predicates,originalAudit);ContextSignedUnaryBranchModel.Policy finalPolicy=new ContextSignedUnaryBranchModel.Policy(obligations,finalAudit,nativeLimits.capacity);ContextSignedUnaryExtensionSearch.Policy extension=new ContextSignedUnaryExtensionSearch.Policy(extensionCandidates,extensionCells,extensionOrigins,extensionWork,finalPolicy);search=new ContextSignedUnaryModelSearch.Policy(candidates,cells,witnesses,constructionWork,original,extension);
  }
 }
 public static final class Automatic {
  public final Limits model;public final int points,fresh,work;
  public Automatic(Limits model,int points,int fresh,int work){if(model==null||points<1||points>model.search.original.points||fresh<0||fresh>8||work<1)throw new IllegalArgumentException("Bounded automatic basis policy required");this.model=model;this.points=points;this.fresh=fresh;this.work=work;}
 }
 public static final class View {
  public final UUID context;public final long revision;public final String fingerprint;public final List<String> initialization;
  private View(ContextProductionInventory inventory){context=inventory.context;revision=inventory.revision;fingerprint=inventory.fingerprint;initialization=Collections.unmodifiableList(new ArrayList<>(inventory.initialization));}
 }
 public static final class CutView {
  public final String trustedGroup;public final List<View> members;
  private CutView(Snapshot cut){trustedGroup=cut.trustedGroup;List<View> all=new ArrayList<>();for(Snapshot member:cut.physicalMembers())all.add(new View(member.inventory));members=Collections.unmodifiableList(all);}
 }
 public static final class Result {
  public final Status status;public final String phase,reason;public final View proposed;public final List<CutView> foreign;
  public final List<ContextSignedBranchInspection.Opposition> oppositions;
  public final int originalCandidates,validOriginals,extensionCandidates,originalRows,finalObligations,namedPoints,basisAttempts;
  private Result(Status status,String phase,String reason,Snapshot x,List<Snapshot> cuts,List<ContextSignedBranchInspection.Opposition> oppositions,ContextSignedUnaryModelSearch.Result model,int namedPoints,int basisAttempts){this.status=status;this.phase=phase;this.reason=reason;proposed=x==null?null:new View(x.inventory);List<CutView> all=new ArrayList<>();if(cuts!=null)for(Snapshot cut:cuts)all.add(new CutView(cut));foreign=Collections.unmodifiableList(all);this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));originalCandidates=model==null?0:model.tried;validOriginals=model==null?0:model.originals;extensionCandidates=model==null?0:model.extensionCandidates;originalRows=model==null?0:model.output.ledger.original.ledger.rows.size();finalObligations=model==null?0:model.output.ledger.obligations.size();this.namedPoints=namedPoints;this.basisAttempts=basisAttempts;}
 }
 /** Detached invocation is historical only; NO receipt/model/result input grants live authority. */
 public static Result inspect(Snapshot x,List<Snapshot> foreign,List<SemanticTermSnapshot> namedBasis,Limits limits){return view(ContextSignedModelEvaluation.run(x,foreign,namedBasis,limits));}
 public static Result inspectAutomatic(Snapshot x,List<Snapshot> foreign,Automatic limits){return view(ContextSignedModelEvaluation.runAutomatic(x,foreign,limits));}
 private static Result view(ContextSignedModelEvaluation.Result r){
  if(r.status==Status.INCOMPLETE)return new Result(Status.INCOMPLETE,r.phase,r.reason,null,null,Collections.emptyList(),null,0,0);
  return new Result(r.status,r.status==Status.MODELLED_SUPPLIED?"historical-supplied-model":r.phase,"Historical supplied-profile diagnostic only; no settlement/eligibility capability",r.x,r.cuts,r.status==Status.REFUTED?ContextSignedBranchInspection.oppositions(r.branch):Collections.emptyList(),r.model,r.namedPoints,r.basisAttempts);
 }
}
