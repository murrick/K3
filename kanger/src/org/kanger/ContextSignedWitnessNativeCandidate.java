/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.Cut;

/** Exact detached signed candidate trial; operational evidence never publication permission. */
final class ContextSignedWitnessNativeCandidate {
 static final class Policy {
  final ContextSignedWitnessNativeBranch.Policy branch;final int rows,preparationWork,events;
  Policy(ContextSignedWitnessNativeBranch.Policy branch,int rows,int preparationWork,int events){if(branch==null||rows<1||rows>10000||preparationWork<1||events<1)throw new IllegalArgumentException("Exact bounded candidate preparation required");this.branch=branch;this.rows=rows;this.preparationWork=preparationWork;this.events=events;}
 }
 static final class Attribution {
  final ContextUniversalFrozenConstraints.Proof proof;final Source candidate;final boolean usesCandidate;final Set<Source> originalSources;
  private Attribution(ContextUniversalFrozenConstraints.Proof proof,Source candidate,Map<Source,Source> originals,Runnable work){this.proof=proof;this.candidate=candidate;usesCandidate=proof.support.sources.contains(candidate);originalSources=ContextSignedWitnessNativeCandidateAudit.translate(proof.support,originals,work);}
 }
 static final class Receipt {
  final ContextProductionCandidate.Staged artifact;final ContextSignedWitnessNativeBranch.Receipt branch;final Policy policy;final Map<Source,Source> originals;final Map<Cut,List<Attribution>> oppositions;
  private Receipt(ContextProductionCandidate.Staged artifact,ContextSignedWitnessNativeBranch.Receipt branch,Policy policy,Map<Source,Source> originals,Runnable work){this.artifact=artifact;this.branch=branch;this.policy=policy;this.originals=Collections.unmodifiableMap(new LinkedHashMap<>(originals));Map<Cut,List<Attribution>> all=new LinkedHashMap<>();if(branch.status==ContextSignedWitnessNativeBranch.Status.REFUTED)for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:ContextSignedWitnessNativeCandidateAudit.checks(branch).entrySet())if(!e.getValue().emptyProofs.isEmpty()){List<Attribution> values=new ArrayList<>();for(ContextUniversalFrozenConstraints.Proof p:e.getValue().emptyProofs){work.run();values.add(new Attribution(p,artifact.candidate,originals,work));}all.put(e.getKey(),Collections.unmodifiableList(values));}oppositions=Collections.unmodifiableMap(all);}
 }
 static final class Result {
  final ContextSignedWitnessNativeBranch.Status status;final String phase,reason;final Receipt output,rejection;
  private Result(ContextSignedWitnessNativeBranch.Status status,String phase,String reason,Receipt receipt){this.status=status;this.phase=phase;this.reason=reason;output=status==ContextSignedWitnessNativeBranch.Status.QUIESCENT_SUPPLIED?receipt:null;rejection=status==ContextSignedWitnessNativeBranch.Status.REFUTED?receipt:null;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result run(Snapshot base,List<Snapshot> foreign,String statement,Policy p){String phase="request";
  try{require(base!=null&&foreign!=null&&foreign.size()<=15&&p!=null,"Actual complete signed candidate request required");new Policy(p.branch,p.rows,p.preparationWork,p.events);List<Snapshot> cuts=new ArrayList<>(foreign);
   phase="native-staging";ContextProductionCandidate.StagingResult staged=ContextProductionCandidate.stage(base,cuts,statement,p.rows,p.preparationWork,p.events);require(staged.prepared,staged.reason);
   phase="source-binding";Runnable work=ContextGroundProductionRounds.budget(p.branch.work);Map<Source,Source> originals=ContextSignedWitnessNativeCandidateAudit.manifest(staged.artifact,base,cuts,statement,work);
   phase="signed-native-branch";ContextSignedWitnessNativeBranch.Result b=ContextSignedWitnessNativeBranch.run(staged.artifact.staged,cuts,p.branch);require(b.status!=ContextSignedWitnessNativeBranch.Status.INCOMPLETE,b.phase+": "+b.reason);Receipt receipt=new Receipt(staged.artifact,b.output!=null?b.output:b.rejection,p,originals,work);
   phase="independent-candidate-audit";ContextSignedWitnessNativeCandidateAudit.check(receipt,base,cuts,statement,p,p.branch.work,p.branch.capacity);return new Result(b.status,"audited-supplied-candidate","Exact signed staged candidate evidence; no live-provider/model/full-family/write admission",receipt);
  }catch(Exception failure){return new Result(ContextSignedWitnessNativeBranch.Status.INCOMPLETE,phase,failure.toString(),null);}
 }
}
