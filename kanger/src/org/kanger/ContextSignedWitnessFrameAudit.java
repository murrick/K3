/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Direct original-atom/Source inventory audit; never invokes frame adapter or native inference. */
final class ContextSignedWitnessFrameAudit {
 private ContextSignedWitnessFrameAudit(){}
 static final class Receipt {
  final SignedSeedFrame frame;
  private Receipt(SignedSeedFrame frame){this.frame=frame;}
 }
 static final class Inspection {
  final boolean audited;final String phase,reason;final Receipt output;
  private Inspection(boolean audited,String phase,String reason,Receipt output){this.audited=audited;this.phase=phase;this.reason=reason;this.output=output;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 private static void plain(Packet p){require(p!=null&&p.basis==null&&p.producers.isEmpty()&&!p.observedStep&&p.emptyCut==null&&p.coveredCut==null&&p.roundCut==null&&p.emptyTarget==null&&p.privateTarget==null,"Seed packet has unexpected inference/coverage stamps");}
 private static boolean witness(Atom actual,ContextWitnessBundle.Atom expected,Source source,Runnable work){
  work.run();if(!actual.predicate.equals(expected.predicate)||actual.positive!=expected.positive||actual.arguments.size()!=expected.arguments.size())return false;
  for(int i=0;i<expected.arguments.size();i++){work.run();ContextWitnessBundle.Slot e=expected.arguments.get(i);Slot a=actual.arguments.get(i);if(e.witness>=0){if(a.origin==null||a.value!=null||!a.origin.source.equals(source)||a.origin.binder!=e.witness)return false;}else if(a.origin!=null||a.value==null||!a.value.semanticallyEquals(e.value))return false;}return true;
 }
 private static boolean scalar(Atom actual,ContextProductionCapture.Atom expected,Runnable work){
  work.run();if(!actual.predicate.equals(expected.predicate)||actual.positive!=expected.positive||actual.arguments.size()!=expected.arguments.size())return false;for(int i=0;i<expected.arguments.size();i++){work.run();ContextProductionCapture.Slot e=expected.arguments.get(i);Slot a=actual.arguments.get(i);if(e.variable>=0||e.value==null||a.origin!=null||a.value==null||!a.value.semanticallyEquals(e.value))return false;}return true;
 }
 private static void claim(Packet p,Map<Item,Set<Source>> expected,Entry entry,ContextWitnessBundle.Atom witness,ContextProductionCapture.Atom scalar,Runnable work){Item found=null;for(Item actual:p.items){work.run();if(witness!=null?witness(actual.atom,witness,entry.source,work):scalar(actual.atom,scalar,work)){require(found==null,"Duplicate native seed atom");found=actual;}}require(found!=null,"Missing original primary seed/sign/binder");expected.computeIfAbsent(found,k->new LinkedHashSet<>()).add(entry.source);}
 private static void packet(Packet packet,List<Snapshot> originals,int maximumRank,Runnable work){
  plain(packet);Map<Item,Set<Source>> expected=new IdentityHashMap<>();for(Snapshot snapshot:originals)for(Entry entry:snapshot.primary){work.run();if(entry.bundle!=null){for(ContextWitnessBundle.Atom atom:entry.bundle.atoms){work.run();claim(packet,expected,entry,atom,null,work);}}else for(ContextProductionCapture.Clause clause:entry.clauses){work.run();if(clause.variables.isEmpty()&&clause.literals.size()==1)claim(packet,expected,entry,null,clause.literals.get(0),work);}}
  require(expected.size()==packet.items.size(),"Extra/private-leaked native seed atom");Set<Origin> rank=new HashSet<>();for(Item item:packet.items){work.run();Set<Source> required=expected.get(item);require(required!=null,"Unaccounted native item");Set<Source> actual=new HashSet<>();require(!item.proofs.isEmpty()&&item.proofs.size()<=256,"Primary alternative bound");for(Support proof:item.proofs){work.run();require(proof.depth==0&&proof.sources.size()==1,"Nonprimary signed seed proof");Source s=proof.sources.iterator().next();require(required.contains(s)&&actual.add(s),"Wrong/duplicate singleton Source alternative");}require(actual.equals(required),"Missing original singleton Source alternative");require(item.atom.arguments.size()<=8,"Seed arity bound");Origin root=null;for(Slot slot:item.atom.arguments){work.run();if(slot.origin!=null){require(slot.value==null&&(root==null||root.equals(slot.origin)),"Multiple native Origins in one literal");root=slot.origin;rank.add(root);}else require(slot.value!=null,"Missing ordinary seed value");}require(rank.size()<=maximumRank,"Seed rank bound");}
 }
 static Inspection inspect(SignedSeedFrame frame,ContextSignedWitnessPlanAudit.Receipt plan,Snapshot x,List<Snapshot> foreign,int maximumRows,int maximumRank,int maximumWork,int auditWork){
  if(maximumRank<1||maximumRank>8||maximumWork<1||auditWork<1)throw new IllegalArgumentException("Bounded direct seed frame audit required");String phase="plan";
  try{require(frame!=null&&frame.plan==plan&&frame.maximumRank==maximumRank,"Different actual signed frame/plan/policy");ContextSignedWitnessPlanAudit.check(plan,x,foreign,maximumRows,auditWork);Runnable work=ContextGroundProductionRounds.budget(maximumWork);phase="common";packet(frame.common,Collections.singletonList(x),maximumRank,work);require(!frame.common.items.isEmpty(),"Missing authentic common primary seed");phase="private";Set<Cut> expected=new LinkedHashSet<>();for(Snapshot target:plan.foreign){work.run();Cut cut=Cut.of(target);require(expected.add(cut)&&frame.locals.containsKey(cut),"Missing/duplicate signed local cut");packet(frame.locals.get(cut),Arrays.asList(x,target),maximumRank,work);}require(frame.locals.keySet().equals(expected),"Extra signed local cut");ContextSignedWitnessPlanAudit.check(plan,x,foreign,maximumRows,auditWork);return new Inspection(true,"replayed","Original signs/Origins/all Source alternatives replayed; no admission authority",new Receipt(frame));}catch(Exception failure){return new Inspection(false,phase,failure.toString(),null);}
 }
 static void check(Receipt r,ContextSignedWitnessPlanAudit.Receipt plan,Snapshot x,List<Snapshot> foreign,int rows,int rank,int work,int auditWork){require(r!=null,"Actual signed frame receipt required");Inspection fresh=inspect(r.frame,plan,x,foreign,rows,rank,work,auditWork);require(fresh.audited,fresh.phase+": "+fresh.reason);}
}
