/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextWitnessResultTransport.*;

/** Given original models plus all actual FINAL signed obligations; not search/provider/write admission. */
final class ContextSignedUnaryBranchModel {
 enum Status { SATISFIED_FINAL_UNARY, INCOMPLETE }
 enum Kind { COMMON, PRIVATE, UNIT, COMPOUND, TERMINAL }
 static final class Policy {final int obligations,work,capacity;Policy(int obligations,int work,int capacity){if(obligations<1||obligations>262144||work<1||capacity<1||capacity>4096)throw new IllegalArgumentException("Bounded final unary obligations required");this.obligations=obligations;this.work=work;this.capacity=capacity;}}
 static final class Extension {
  final Map<String,List<Boolean>> truth;final Map<Origin,Integer> denotations;
  Extension(Map<String,List<Boolean>> truth,Map<Origin,Integer> denotations){if(truth==null||truth.size()>64||denotations==null||denotations.size()>64)throw new IllegalArgumentException("Bounded given final extension required");Map<String,List<Boolean>> copy=new LinkedHashMap<>();for(Map.Entry<String,List<Boolean>> e:truth.entrySet()){if(e.getKey()==null||e.getKey().isEmpty()||e.getKey().length()>256||e.getValue()==null||e.getValue().size()>17)throw new IllegalArgumentException("Bounded additional predicate truth required");copy.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));}this.truth=Collections.unmodifiableMap(copy);this.denotations=Collections.unmodifiableMap(new LinkedHashMap<>(denotations));}
 }
 static final class Obligation {final Kind kind;final Cut cut;final Object carrier;final Support support;private Obligation(Kind kind,Cut cut,Object carrier,Support support){this.kind=kind;this.cut=cut;this.carrier=carrier;this.support=support;}}
 static final class Ledger {
  final ContextSignedUnaryModelAudit.Receipt original;final ContextSignedWitnessNativeBranch.Receipt branch;final Map<Cut,Extension> extensions;final Policy policy;final List<Obligation> obligations;
  private Ledger(ContextSignedUnaryModelAudit.Receipt original,ContextSignedWitnessNativeBranch.Receipt branch,Map<Cut,Extension> extensions,Policy policy,List<Obligation> obligations){this.original=original;this.branch=branch;this.extensions=Collections.unmodifiableMap(new LinkedHashMap<>(extensions));this.policy=policy;this.obligations=Collections.unmodifiableList(new ArrayList<>(obligations));}
 }
 static final class Result {final Status status;final String phase,reason;final ContextSignedUnaryBranchModelAudit.Receipt output;private Result(Status status,String phase,String reason,ContextSignedUnaryBranchModelAudit.Receipt output){this.status=status;this.phase=phase;this.reason=reason;this.output=output;}}
 private static void add(List<Obligation> all,Kind kind,Cut cut,Object carrier,Support support,Policy p,Runnable work){work.run();if(all.size()>=p.obligations)throw new IllegalStateException("Final obligation bound");all.add(new Obligation(kind,cut,carrier,support));}
 static Result run(ContextSignedUnaryModelAudit.Receipt original,ContextSignedWitnessNativeBranch.Receipt branch,Map<Cut,Extension> extensions,Policy p){String phase="final-model-request";
  try{if(original==null||branch==null||extensions==null||extensions.size()>16||p==null||branch.status!=ContextSignedWitnessNativeBranch.Status.QUIESCENT_SUPPLIED||branch.termination==null||branch.termination.result.stopping==null)throw new IllegalArgumentException("Actual original model and completed signed stop required");new Policy(p.obligations,p.work,p.capacity);Runnable work=ContextGroundProductionRounds.budget(p.work);ContextSignedWitnessContinuationScope.Receipt scope=branch.termination.result.output;ContextSignedWitnessStop.Receipt stop=branch.termination.result.stopping;List<Obligation> all=new ArrayList<>();phase="actual-final-obligation-manifest";
   for(Cut cut:stop.terminal.checks.keySet()){for(Item i:scope.current().packet.items)for(Support s:i.proofs)add(all,Kind.COMMON,cut,i,s,p,work);PrivatePool pool=scope.current().privatePools.get(cut);if(pool!=null)for(Item i:pool.local.items)for(Support s:i.proofs)add(all,Kind.PRIVATE,cut,i,s,p,work);for(ContextUniversalSignedUnitExchange.Unit u:stop.units.units)add(all,Kind.UNIT,cut,u,u.support,p,work);for(ContextUniversalCompoundExchange.ResidualProof c:stop.compounds.clauses)add(all,Kind.COMPOUND,cut,c,c.support,p,work);for(ContextUniversalFrozenConstraints.Proof proof:stop.terminal.checks.get(cut).proofs)add(all,Kind.TERMINAL,cut,proof,proof.support,p,work);}
   Ledger ledger=new Ledger(original,branch,extensions,p,all);phase="independent-final-model-audit";return new Result(Status.SATISFIED_FINAL_UNARY,"final-unary-satisfied","Given original models and ALL actual final unary obligations verified; no provider/full-language/write authority",ContextSignedUnaryBranchModelAudit.inspect(ledger,original,branch,extensions,p,p.work));
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null);}
 }
}
