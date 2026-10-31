/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextSignedUnaryModel.Interpretation;
import org.kanger.ContextSignedUnaryBranchModel.Extension;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextUniversalInputResiduals.*;

/** Bounded candidate extensions of FIXED original models; only independent final audit grants a receipt. */
final class ContextSignedUnaryExtensionSearch {
 enum Status { CONSTRUCTED_FINAL_UNARY, INCOMPLETE }
 static final class Policy {
  final int candidates,cells,origins,work;final ContextSignedUnaryBranchModel.Policy audit;
  Policy(int candidates,int cells,int origins,int work,ContextSignedUnaryBranchModel.Policy audit){if(candidates<1||candidates>64||cells<0||cells>16||origins<0||origins>8||work<1||audit==null)throw new IllegalArgumentException("Bounded fixed-model extension search required");this.candidates=candidates;this.cells=cells;this.origins=origins;this.work=work;this.audit=audit;}
 }
 static final class Result {
  final Status status;final int tried;final String reason;final ContextSignedUnaryBranchModelAudit.Receipt output;
  private Result(Status status,int tried,String reason,ContextSignedUnaryBranchModelAudit.Receipt output){this.status=status;this.tried=tried;this.reason=reason;this.output=output;}
 }
 private static final class CutData {
  final Cut cut;final Interpretation base;final Set<String> names=new LinkedHashSet<>();final Set<Origin> origins=new LinkedHashSet<>();
  CutData(Cut cut,Interpretation base){this.cut=cut;this.base=base;}
 }
 private static final class Choice {
  final CutData cut;final String predicate;final int point,radix;final Origin origin;
  Choice(CutData cut,String predicate,int point,Origin origin,int radix){this.cut=cut;this.predicate=predicate;this.point=point;this.origin=origin;this.radix=radix;}
 }
 private static void atom(CutData c,Item i,Runnable work){work.run();if(i.atom.arguments.size()!=1)throw new IllegalArgumentException("Unary final atom required");c.names.add(i.atom.predicate);Slot t=i.atom.arguments.get(0);if(t.origin!=null)c.origins.add(t.origin);}
 private static void clause(CutData c,Residual r,Runnable work){work.run();if(r==null||r.literals.isEmpty()||r.literals.size()>8||r.variables.size()>3)throw new IllegalArgumentException("Bounded unary final residual required");for(Literal a:r.literals){work.run();if(a.arguments.size()!=1)throw new IllegalArgumentException("Unary final literal required");c.names.add(a.predicate);for(Term t:a.arguments)if(t.origin!=null)c.origins.add(t.origin);}}
 private static void count(int[] n,int amount,Policy p,Runnable work){for(int i=0;i<amount;i++){work.run();if(++n[0]>p.audit.obligations)throw new IllegalStateException("Whole final search obligation limit");}}
 private static Map<Cut,Extension> candidate(List<CutData> cuts,List<Choice> choices,int[] values,Runnable work){Map<Cut,Extension> out=new LinkedHashMap<>();for(CutData c:cuts){Map<String,List<Boolean>> truth=new LinkedHashMap<>();Map<Origin,Integer> denotations=new LinkedHashMap<>();for(String name:c.names)if(!c.base.truth.containsKey(name))truth.put(name,new ArrayList<>(Collections.nCopies(c.base.points.size()+1,false)));for(Origin o:c.origins){work.run();Integer fixed=c.base.witnesses.get(o.source);denotations.put(o,fixed==null?0:fixed);}for(int i=0;i<choices.size();i++){work.run();Choice choice=choices.get(i);if(choice.cut==c){if(choice.origin==null)truth.get(choice.predicate).set(choice.point,values[i]!=0);else denotations.put(choice.origin,values[i]);}}out.put(c.cut,new Extension(truth,denotations));}return out;}
 private static boolean next(List<Choice> choices,int[] values,Runnable work){for(int i=0;i<values.length;i++){work.run();if(++values[i]<choices.get(i).radix)return true;values[i]=0;}return false;}
 static Result run(ContextSignedUnaryModelAudit.Receipt original,ContextSignedWitnessNativeBranch.Receipt branch,Policy p){int tried=0;
  try{if(original==null||branch==null||p==null)throw new IllegalArgumentException("Actual fixed original model and final branch required");new Policy(p.candidates,p.cells,p.origins,p.work,p.audit);ContextSignedUnaryModel.Ledger base=original.ledger;if(branch.x!=base.x||!branch.foreign.equals(base.foreign)||branch.status!=ContextSignedWitnessNativeBranch.Status.QUIESCENT_SUPPLIED)throw new IllegalArgumentException("Exact completed signed family required");
   ContextSignedUnaryModelAudit.check(original,base.x,base.foreign,base.models,base.policy,p.audit.work);ContextSignedWitnessNativeBranchAudit.check(branch,base.x,base.foreign,branch.policy,p.audit.work,p.audit.capacity);
   Runnable work=ContextGroundProductionRounds.budget(p.work);ContextSignedWitnessStop.Receipt stop=branch.termination.result.stopping;ContextSignedWitnessContinuationScope.Receipt scope=branch.termination.result.output;List<CutData> cuts=new ArrayList<>();int[] obligations={0};
   if(!base.models.keySet().equals(stop.terminal.checks.keySet()))throw new IllegalArgumentException("ALL exact final cuts required");
   for(Cut cut:stop.terminal.checks.keySet()){work.run();CutData c=new CutData(cut,base.models.get(cut));cuts.add(c);for(Item i:scope.current().packet.items){count(obligations,i.proofs.size(),p,work);atom(c,i,work);}PrivatePool pool=scope.current().privatePools.get(cut);if(pool!=null)for(Item i:pool.local.items){count(obligations,i.proofs.size(),p,work);atom(c,i,work);}for(ContextUniversalSignedUnitExchange.Unit u:stop.units.units){count(obligations,1,p,work);clause(c,u.clause,work);}for(ContextUniversalCompoundExchange.ResidualProof r:stop.compounds.clauses){count(obligations,1,p,work);clause(c,r.clause,work);}for(ContextUniversalFrozenConstraints.Proof proof:stop.terminal.checks.get(cut).proofs){count(obligations,1,p,work);clause(c,proof.clause,work);}}
   List<Choice> choices=new ArrayList<>();int cells=0,origins=0;for(CutData c:cuts){if(!c.names.containsAll(c.base.truth.keySet())||c.names.size()>base.policy.predicates)throw new IllegalStateException("Complete bounded final vocabulary required");for(String name:c.names)if(!c.base.truth.containsKey(name))for(int point=0;point<=c.base.points.size();point++){work.run();if(++cells>p.cells)throw new IllegalStateException("New truth cell bound");choices.add(new Choice(c,name,point,null,2));}for(Origin o:c.origins){work.run();if(!c.base.witnesses.containsKey(o.source)){if(++origins>p.origins)throw new IllegalStateException("Imported Origin choice bound");choices.add(new Choice(c,null,-1,o,c.base.points.size()));}}}
   int[] values=new int[choices.size()];while(tried<p.candidates){Map<Cut,Extension> extension=candidate(cuts,choices,values,work);++tried;ContextSignedUnaryBranchModel.Result checked=ContextSignedUnaryBranchModel.run(original,branch,extension,p.audit);if(checked.status==ContextSignedUnaryBranchModel.Status.SATISFIED_FINAL_UNARY)return new Result(Status.CONSTRUCTED_FINAL_UNARY,tried,"Constructed extension accepted by independent actual-final unary audit only; no write authority",checked.output);if(!next(choices,values,work))return new Result(Status.INCOMPLETE,tried,"Fixed-basis candidate space exhausted; not a no-model or conflict proof",null);}
   return new Result(Status.INCOMPLETE,tried,"Candidate limit reached; not a no-model proof",null);
  }catch(Exception failure){return new Result(Status.INCOMPLETE,tried,failure.toString(),null);}
 }
}
