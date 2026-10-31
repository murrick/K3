/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionCapture.Atom;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextUniversalInputResiduals.Literal;
import org.kanger.units.Term;

/** Candidate bases ONLY: actual ordinary constants plus optional genuine numeric values, never Origin inequalities. */
final class ContextSignedModelBasis {
 private static void add(List<SemanticTermSnapshot> points,SemanticTermSnapshot value,int maximum,Runnable work){if(value==null)return;work.run();for(SemanticTermSnapshot p:points){work.run();if(p.semanticallyEquals(value))return;}if(points.size()>=maximum)throw new IllegalStateException("Complete actual constant basis exceeds point bound");points.add(value);}
 private static void entry(List<SemanticTermSnapshot> points,Entry e,int maximum,Runnable work){work.run();if(e.bundle!=null){for(ContextWitnessBundle.Atom a:e.bundle.atoms)for(ContextWitnessBundle.Slot s:a.arguments)add(points,s.value,maximum,work);}else for(Clause c:e.clauses)for(Atom a:c.literals)for(ContextProductionCapture.Slot s:a.arguments)add(points,s.value,maximum,work);}
 private static void item(List<SemanticTermSnapshot> points,Item item,int maximum,Runnable work){work.run();for(ContextWitnessResultTransport.Slot s:item.atom.arguments)add(points,s.value,maximum,work);}
 private static void clause(List<SemanticTermSnapshot> points,Residual clause,int maximum,Runnable work){work.run();for(Literal a:clause.literals)for(ContextUniversalInputResiduals.Term t:a.arguments)add(points,t.value,maximum,work);}
 private static List<SemanticTermSnapshot> copy(List<SemanticTermSnapshot> points){return Collections.unmodifiableList(new ArrayList<>(points));}
 static List<List<SemanticTermSnapshot>> plan(ContextSignedWitnessNativeBranch.Receipt branch,ContextSignedModelInspection.Automatic limits){Runnable work=ContextGroundProductionRounds.budget(limits.work);List<SemanticTermSnapshot> points=new ArrayList<>();
  for(Entry e:branch.x.primary)entry(points,e,limits.points,work);for(ContextProductionNativeBinding.Snapshot cut:branch.foreign)for(Entry e:cut.primary)entry(points,e,limits.points,work);
  ContextSignedWitnessContinuationScope.Receipt scope=branch.termination.result.output;ContextSignedWitnessStop.Receipt stop=branch.termination.result.stopping;
  for(Item i:scope.current().packet.items)item(points,i,limits.points,work);for(PrivatePool p:scope.current().privatePools.values())for(Item i:p.local.items)item(points,i,limits.points,work);for(ContextUniversalSignedUnitExchange.Unit u:stop.units.units)clause(points,u.clause,limits.points,work);for(ContextUniversalCompoundExchange.ResidualProof c:stop.compounds.clauses)clause(points,c.clause,limits.points,work);for(ContextUniversalFrozenConstraints.Result r:stop.terminal.checks.values())for(ContextUniversalFrozenConstraints.Proof p:r.proofs)clause(points,p.clause,limits.points,work);
  List<List<SemanticTermSnapshot>> bases=new ArrayList<>();if(!points.isEmpty())bases.add(copy(points));int extra=0;
  for(int probe=0;probe<64&&extra<limits.fresh&&points.size()<limits.points;probe++){work.run();SemanticTermSnapshot value;try{value=SemanticTermSnapshot.capture(new Term(Double.valueOf(probe),null));}catch(Exception e){throw new IllegalStateException("Ordinary numeric candidate unavailable",e);}boolean present=false;for(SemanticTermSnapshot p:points){work.run();present|=p.semanticallyEquals(value);}if(present)continue;add(points,value,limits.points,work);extra++;bases.add(copy(points));}
  if(bases.isEmpty())throw new IllegalStateException("No ordinary constant and no fresh candidate budget; not a conflict");return Collections.unmodifiableList(bases);
 }
}
