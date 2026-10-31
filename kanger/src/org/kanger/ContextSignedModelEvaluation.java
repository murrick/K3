/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.Cut;
import org.kanger.ContextSignedModelInspection.Status;
import org.kanger.ContextSignedModelInspection.Limits;
/** Shared actual proof/model computation; internal receipts never escape public diagnostics. */
final class ContextSignedModelEvaluation {
 static final class Result {
  final Status status;final String phase,reason;final Snapshot x;final List<Snapshot> cuts;
  final ContextSignedWitnessNativeBranch.Receipt branch;final ContextSignedUnaryModelSearch.Result model;final int namedPoints;
  private Result(Status status,String phase,String reason,Snapshot x,List<Snapshot> cuts,ContextSignedWitnessNativeBranch.Receipt branch,ContextSignedUnaryModelSearch.Result model,int namedPoints){this.status=status;this.phase=phase;this.reason=reason;this.x=x;this.cuts=cuts;this.branch=branch;this.model=model;this.namedPoints=namedPoints;}
 }
 static Result run(Snapshot x,List<Snapshot> foreign,List<SemanticTermSnapshot> namedBasis,Limits limits){String phase="model-request";
  try{if(x==null||foreign==null||foreign.size()>15||limits==null||namedBasis==null||namedBasis.isEmpty()||namedBasis.size()>limits.search.original.points)throw new IllegalArgumentException("Actual signed family and explicit bounded named basis required");List<Snapshot> cuts=new ArrayList<>(foreign);List<SemanticTermSnapshot> basis=Collections.unmodifiableList(new ArrayList<>(namedBasis));for(int i=0;i<basis.size();i++){if(basis.get(i)==null)throw new IllegalArgumentException("Concrete ordinary named value required");for(int j=0;j<i;j++)if(basis.get(i).semanticallyEquals(basis.get(j)))throw new IllegalArgumentException("Semantically distinct named values required, not Origin inequality");}
   phase="signed-native-branch";ContextSignedWitnessNativeBranch.Result nativeResult=ContextSignedWitnessNativeBranch.run(x,cuts,limits.nativeLimits.branchPolicy());if(nativeResult.status==ContextSignedWitnessNativeBranch.Status.INCOMPLETE)throw new IllegalStateException(nativeResult.phase+": "+nativeResult.reason);ContextSignedWitnessNativeBranch.Receipt branch=nativeResult.output!=null?nativeResult.output:nativeResult.rejection;
   phase="signed-native-audit";ContextSignedWitnessNativeBranchAudit.check(branch,x,cuts,limits.nativeLimits.branchPolicy(),limits.nativeLimits.audit,limits.nativeLimits.capacity);
   if(nativeResult.status==ContextSignedWitnessNativeBranch.Status.REFUTED)return new Result(Status.REFUTED,"signed-refutation-audited","Actual independently audited signed refutation",x,cuts,branch,null,0);
   phase="original-and-final-model-construction";Map<Cut,List<SemanticTermSnapshot>> bases=new LinkedHashMap<>();bases.put(Cut.of(x),basis);for(Snapshot cut:cuts)bases.put(Cut.of(cut),basis);ContextSignedUnaryModelSearch.Result model=ContextSignedUnaryModelSearch.run(branch,bases,limits.search);if(model.status!=ContextSignedUnaryModelSearch.Status.CONSTRUCTED_FINAL_UNARY)throw new IllegalStateException(model.reason);
   phase="final-model-audit";ContextSignedUnaryBranchModel.Ledger l=model.output.ledger;ContextSignedUnaryBranchModelAudit.check(model.output,l.original,branch,l.extensions,l.policy,limits.search.extension.audit.work);
   return new Result(Status.MODELLED_SUPPLIED,"original-and-final-model-audited","Original and ALL actual final obligations independently audited",x,cuts,branch,model,basis.size());
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,null,null,null,0);}
 }
}
