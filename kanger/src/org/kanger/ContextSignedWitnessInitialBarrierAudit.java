/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextWitnessResultTransport.*;

/** Direct all-cut union/minimum replay. Does not call the barrier builder or merge adapter. */
final class ContextSignedWitnessInitialBarrierAudit {
 static final class Receipt {
  final ContextSignedWitnessInitialBarrier.Result result;
  private Receipt(ContextSignedWitnessInitialBarrier.Result result){this.result=result;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 private static void unmarked(Packet p){require(p!=null&&p.basis==null&&p.producers.isEmpty()&&!p.observedStep&&p.emptyCut==null&&p.coveredCut==null&&p.roundCut==null&&p.privateTarget==null&&p.emptyTarget==null,"Unexpected barrier coverage/STEP stamp");}
 private static boolean same(Item a,Item b,Runnable work){return ContextUniversalInputResiduals.fact(a,work).equivalent(ContextUniversalInputResiduals.fact(b,work),work);}
 private static int minimum(List<Packet> inputs,Item atom,Support proof,Runnable work){int min=Integer.MAX_VALUE;for(Packet p:inputs)for(Item i:p.items){work.run();if(!same(atom,i,work))continue;for(Support s:i.proofs){work.run();if(s.sources.equals(proof.sources))min=Math.min(min,s.depth);}}return min;}
 static void union(Packet output,List<Packet> inputs,Runnable work,int depth){
  unmarked(output);require(output.items.size()<=64,"Barrier atom limit");Set<Origin> rank=new HashSet<>();for(Item item:output.items){work.run();require(item.atom.arguments.size()<=8&&item.proofs.size()<=256,"Barrier arity/alternative limit");for(Slot slot:item.atom.arguments){work.run();if(slot.origin!=null)rank.add(slot.origin);}for(Support support:item.proofs){work.run();require(support.depth<=depth,"Barrier fact depth limit");}}require(rank.size()<=8,"Barrier witness rank limit");
  for(int n=0;n<output.items.size();++n){Item i=output.items.get(n);require(!i.proofs.isEmpty(),"Empty barrier proof bucket");for(int k=0;k<n;++k){work.run();require(!same(i,output.items.get(k),work),"Duplicate barrier atom");}for(int j=0;j<i.proofs.size();++j){Support s=i.proofs.get(j);int min=minimum(inputs,i,s,work);require(min!=Integer.MAX_VALUE&&s.depth==min,"Unexpected or wrong-grade barrier proof");for(int k=0;k<j;++k){work.run();require(!s.sources.equals(i.proofs.get(k).sources),"Duplicate barrier Source alternative");}}}
  for(Packet p:inputs)for(Item i:p.items)for(Support s:i.proofs){work.run();int min=minimum(inputs,i,s,work);boolean found=false;for(Item actual:output.items){work.run();if(!same(i,actual,work))continue;for(Support a:actual.proofs){work.run();found|=a.sources.equals(s.sources)&&a.depth==min;}}require(found,"Missing exact barrier Source alternative");}
 }
 static Receipt inspect(ContextSignedWitnessInitialBarrier.Result result,ContextSignedWitnessInitialEntry.Receipt initial,ContextSignedWitnessFrameAudit.Receipt frame,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){
  require(result!=null&&result.merged&&result.initial==initial,"Actual successful signed initial barrier required");ContextSignedWitnessInitialEntry.check(initial,frame,rows,limits,auditWork,capacity);ContextSignedWitnessInitialEntry.Entry e=initial.entry;ConstraintBarrier b=result.barrier;Runnable work=ContextGroundProductionRounds.budget(auditWork);
  require(b!=null&&b.merged&&b.branches.keySet().equals(e.receiver.checks.keySet())&&b.privatePools.keySet().equals(e.pools.keySet()),"Different complete all-cut barrier ledger");List<Packet> shared=new ArrayList<>();shared.add(e.common);boolean localChanged=false;
  for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> c:e.receiver.checks.entrySet()){work.run();ConstraintTransfer t=b.branches.get(c.getKey());require(t!=null&&t.proof==c.getValue()&&t.before==e.common&&t.privateBefore==e.pools.get(c.getKey())&&t.bindings==null&&t.certificate==null,"Different actual initial product owner");ContextUniversalSignedProductAudit.Result a=ContextUniversalSignedProductAudit.bind(c.getValue(),t,65536,auditWork);require(a.bound,a.reason);shared.add(t.output);boolean changed=!ContextWitnessResultTransport.sameContents(c.getValue().local,t.local,auditWork);require(t.localChanged==changed&&t.commonChanged==!ContextWitnessResultTransport.sameContents(e.common,t.output,auditWork),"Different actual branch change grades");localChanged|=changed;}
  for(Packet packet:shared)for(Item item:packet.items)for(Support support:item.proofs){work.run();require(support.depth<=limits.factDepth,"Uncovered signed barrier fact depth");}
  union(b.packet,shared,work,limits.factDepth);ContextWitnessResultTransport.family(b.packet,new ArrayList<>(b.branches.keySet()),limits.work);
  require(b.changed==!ContextWitnessResultTransport.sameContents(e.common,b.packet,auditWork)&&b.localChanged==localChanged,"Different common/private barrier change flags");
  for(Map.Entry<Cut,PrivatePool> p:e.pools.entrySet()){work.run();PrivatePool old=p.getValue(),actual=b.privatePools.get(p.getKey());require(actual!=null&&actual.common==b.packet&&actual.signedSeeds==frame&&actual.x==old.x&&actual.snapshot==old.snapshot&&actual.cut.equals(old.cut)&&actual.sources.equals(old.sources)&&actual.declarations.size()==old.declarations.size(),"Different original signed private scope");for(int i=0;i<old.declarations.size();++i){work.run();require(actual.declarations.get(i)==old.declarations.get(i),"Different original private declaration identity");}union(actual.local,Arrays.asList(b.packet,b.branches.get(p.getKey()).local),work,limits.factDepth);}
  require(result.units!=null&&result.compounds!=null&&result.units.prior==e.units&&result.compounds.prior==e.compounds&&result.units.barrier==b&&result.compounds.barrier==b&&result.units.common==b.packet&&result.compounds.common==b.packet&&result.units.checks.equals(e.receiver.checks)&&result.compounds.checks.equals(e.receiver.checks),"Different actual initial carrier rebase");
  ContextUniversalSignedUnitExchange.check(result.units,frame.frame.plan.x,b.packet,limits.factDepth,work);ContextUniversalCompoundExchange.check(result.compounds,frame.frame.plan.x,b.packet,limits.factDepth,work);return new Receipt(result);
 }
 static void check(Receipt receipt,ContextSignedWitnessInitialEntry.Receipt initial,ContextSignedWitnessFrameAudit.Receipt frame,int rows,ContextUniversalFrozenBranch.Limits limits,int auditWork,int capacity){require(receipt!=null,"Actual signed barrier receipt required");inspect(receipt.result,initial,frame,rows,limits,auditWork,capacity);}
}
