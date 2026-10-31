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
  private final ContextSignedUnaryModelSearch.Policy search;
  /** Separate construction/child audit bounds; one explicit named basis is used in EVERY cut. */
  public Limits(ContextSignedCandidateInspection.Limits nativeLimits,int candidates,int cells,int witnesses,int constructionWork,int points,int predicates,int originalAudit,int extensionCandidates,int extensionCells,int extensionOrigins,int extensionWork,int obligations,int finalAudit){
   if(nativeLimits==null)throw new IllegalArgumentException("Explicit native/model diagnostic limits required");this.nativeLimits=nativeLimits;ContextSignedUnaryModel.Policy original=new ContextSignedUnaryModel.Policy(nativeLimits.rows,points,predicates,originalAudit);ContextSignedUnaryBranchModel.Policy finalPolicy=new ContextSignedUnaryBranchModel.Policy(obligations,finalAudit,nativeLimits.capacity);ContextSignedUnaryExtensionSearch.Policy extension=new ContextSignedUnaryExtensionSearch.Policy(extensionCandidates,extensionCells,extensionOrigins,extensionWork,finalPolicy);search=new ContextSignedUnaryModelSearch.Policy(candidates,cells,witnesses,constructionWork,original,extension);
  }
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
  public final int originalCandidates,validOriginals,extensionCandidates,originalRows,finalObligations,namedPoints;
  private Result(Status status,String phase,String reason,Snapshot x,List<Snapshot> cuts,List<ContextSignedBranchInspection.Opposition> oppositions,ContextSignedUnaryModelSearch.Result model,int namedPoints){this.status=status;this.phase=phase;this.reason=reason;proposed=x==null?null:new View(x.inventory);List<CutView> all=new ArrayList<>();if(cuts!=null)for(Snapshot cut:cuts)all.add(new CutView(cut));foreign=Collections.unmodifiableList(all);this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));originalCandidates=model==null?0:model.tried;validOriginals=model==null?0:model.originals;extensionCandidates=model==null?0:model.extensionCandidates;originalRows=model==null?0:model.output.ledger.original.ledger.rows.size();finalObligations=model==null?0:model.output.ledger.obligations.size();this.namedPoints=namedPoints;}
 }
 /** Detached invocation is historical only; NO receipt/model/result input grants live authority. */
 public static Result inspect(Snapshot x,List<Snapshot> foreign,List<SemanticTermSnapshot> namedBasis,Limits limits){String phase="model-request";
  try{if(x==null||foreign==null||foreign.size()>15||limits==null||namedBasis==null||namedBasis.isEmpty()||namedBasis.size()>limits.search.original.points)throw new IllegalArgumentException("Actual signed family and explicit bounded named basis required");List<Snapshot> cuts=new ArrayList<>(foreign);List<SemanticTermSnapshot> basis=Collections.unmodifiableList(new ArrayList<>(namedBasis));for(int i=0;i<basis.size();i++){if(basis.get(i)==null)throw new IllegalArgumentException("Concrete ordinary named value required");for(int j=0;j<i;j++)if(basis.get(i).semanticallyEquals(basis.get(j)))throw new IllegalArgumentException("Semantically distinct named values required, not Origin inequality");}
   phase="signed-native-branch";ContextSignedWitnessNativeBranch.Result nativeResult=ContextSignedWitnessNativeBranch.run(x,cuts,limits.nativeLimits.branchPolicy());if(nativeResult.status==ContextSignedWitnessNativeBranch.Status.INCOMPLETE)throw new IllegalStateException(nativeResult.phase+": "+nativeResult.reason);ContextSignedWitnessNativeBranch.Receipt branch=nativeResult.output!=null?nativeResult.output:nativeResult.rejection;
   phase="signed-native-audit";ContextSignedWitnessNativeBranchAudit.check(branch,x,cuts,limits.nativeLimits.branchPolicy(),limits.nativeLimits.audit,limits.nativeLimits.capacity);
   if(nativeResult.status==ContextSignedWitnessNativeBranch.Status.REFUTED)return new Result(Status.REFUTED,"signed-refutation-audited","Actual signed proof only, not failed model search or write authority",x,cuts,ContextSignedBranchInspection.oppositions(branch),null,0);
   phase="original-and-final-model-construction";Map<Cut,List<SemanticTermSnapshot>> bases=new LinkedHashMap<>();bases.put(Cut.of(x),basis);for(Snapshot cut:cuts)bases.put(Cut.of(cut),basis);ContextSignedUnaryModelSearch.Result model=ContextSignedUnaryModelSearch.run(branch,bases,limits.search);if(model.status!=ContextSignedUnaryModelSearch.Status.CONSTRUCTED_FINAL_UNARY)throw new IllegalStateException(model.reason);
   phase="final-model-audit";ContextSignedUnaryBranchModel.Ledger l=model.output.ledger;ContextSignedUnaryBranchModelAudit.check(model.output,l.original,branch,l.extensions,l.policy,limits.search.extension.audit.work);
   return new Result(Status.MODELLED_SUPPLIED,"historical-supplied-model","Constructed original and ALL actual final unary obligations audited; scoped diagnostic only, no settlement/eligibility capability",x,cuts,Collections.emptyList(),model,basis.size());
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,null,Collections.emptyList(),null,0);}
 }
}
