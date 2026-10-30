/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;
final class ContextSignedWitnessBarrierMatrix {
 static int checks;static final int WORK=64000000;
 static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
 static final class Fixture {
  final ContextSignedWitnessFrameAudit.Receipt frame;final ContextUniversalFrozenBranch.Limits limits;
  final ContextSignedWitnessInitialEntry.Receipt initial;
  Fixture(boolean trusted,boolean reverse)throws Exception{
   Snapshot x=ContextSignedWitnessReceiverMatrix.bind(true,"!anchor(1); !commonSeed(1); !@u commonSeed(u)->shared(u); !@u anchor(u)->shared(u); !@u seed(u,1)->~made(u,1); !@u ~global(u,1);");
   Snapshot a=ContextSignedWitnessReceiverMatrix.bind(false,"!$u seed(u,1)&&~blocked(u,1);"),b=ContextSignedWitnessReceiverMatrix.bind(false,"!$u other(u,1);");List<Snapshot> members=reverse?Arrays.asList(b,a):Arrays.asList(a,b);List<Snapshot> foreign=trusted?Collections.singletonList(ContextProductionNativeBinding.trusted("signed-barrier",members,WORK)):members;
   frame=ContextSignedWitnessReceiverMatrix.frame(x,foreign);limits=ContextSignedWitnessReceiverMatrix.limits(16,WORK);ContextSignedWitnessInitialEntry.Result e=ContextSignedWitnessInitialEntry.assemble(frame,64,limits,WORK,64);require(e.status==ContextSignedWitnessInitialEntry.Status.READY,e.reason);initial=ContextSignedWitnessInitialEntry.inspect(e.entry,frame,64,limits,WORK,64);
  }
  ContextSignedWitnessInitialBarrier.Result run(){ContextSignedWitnessInitialBarrier.Result r=ContextSignedWitnessInitialBarrier.run(initial,frame,64,limits,WORK,64);require(r.merged,r.phase+": "+r.reason);return r;}
  void check(ContextSignedWitnessInitialBarrierAudit.Receipt receipt,int work){ContextSignedWitnessInitialBarrierAudit.check(receipt,initial,frame,64,limits,work,1);}
 }
 static boolean foreignOnly(org.kanger.ContextProductionCapture.Support p){return p.sources.stream().noneMatch(s->s.localX);}
 static void positive()throws Exception{
  for(boolean trusted:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}){Fixture f=new Fixture(trusted,reverse);ContextSignedWitnessInitialBarrier.Result r=f.run();ConstraintBarrier b=r.barrier;
   require(b.changed&&b.branches.size()==f.frame.frame.plan.foreign.size()+1&&b.privatePools.size()==f.initial.entry.pools.size(),"real changed common only after all supplied cuts");
   require(b.packet.items.stream().flatMap(i->i.proofs.stream()).noneMatch(ContextSignedWitnessBarrierMatrix::foreignOnly),"no raw private signed seed exported to common");Item made=b.packet.items.stream().filter(i->i.atom.predicate.equals("made")).findFirst().orElseThrow(()->new AssertionError("missing derived signed witness fact"));require(!made.atom.positive&&made.atom.arguments.get(0).origin!=null&&made.proofs.stream().allMatch(p->p.sources.size()==2),"signed derived fact retains Source-owned Origin and exact bundle+rule support");
   Item shared=b.packet.items.stream().filter(i->i.atom.predicate.equals("shared")).findFirst().get();require(shared.proofs.size()==2,"ALL exact alternative Source proofs survive merged shared scalar");
   for(Map.Entry<Cut,PrivatePool> e:b.privatePools.entrySet()){PrivatePool p=e.getValue();require(p.common==b.packet&&p.signedSeeds==f.frame,"rebased pool retains original signed seed scope");require(ContextWitnessResultTransport.retains(f.initial.entry.pools.get(e.getKey()).local,p.local,ContextGroundProductionRounds.budget(WORK)),"all original private proofs retained");require(p.local.items.stream().anyMatch(i->i.atom.predicate.equals("made")),"new common signed fact reaches every private receiver frame");}
   require(r.units.prior==f.initial.entry.units&&r.compounds.prior==f.initial.entry.compounds&&r.units.barrier==b&&r.compounds.barrier==b,"both carriers rebase against same actual all-cut barrier");ContextSignedWitnessInitialBarrierAudit.Receipt receipt=ContextSignedWitnessInitialBarrierAudit.inspect(r,f.initial,f.frame,64,f.limits,WORK,64);f.check(receipt,WORK);require(true,"direct independent full union/minimum audit at capacity1");
   Snapshot target=f.frame.frame.plan.foreign.get(0);ContextUniversalNativeRows.Result rows=ContextUniversalNativeRows.inspectSigned(f.frame,target,64,WORK,WORK);ContextUniversalFrozenConstraints.Result next=ContextUniversalFrozenConstraints.closeSignedInstantiatedUnits(rows,b.packet,b.privatePools.get(Cut.of(target)),null,16,16,512,256,WORK);require(!next.closed,"original initial receipt cannot authorize changed-common receiver");
  }
  System.out.println("SIGNED_WITNESS_BARRIER orders=4 allCutsBeforeMerge=true signedOriginsAndAlternatives=true privateRetention=true carriersRebased=true directAudit=true nextReceiverStillGuarded=true");
 }
 interface Attempt{void run()throws Exception;}
 static void reject(Attempt a,String why)throws Exception{boolean failed=false;try{a.run();}catch(Exception expected){failed=true;}require(failed,why);}
 static Object replace(Object target,String name,Object value)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);Object old=f.get(target);f.set(target,value);return old;}
 static void incomplete(ContextSignedWitnessInitialBarrier.Result r,String why){require(!r.merged&&r.initial==null&&r.barrier==null&&r.units==null&&r.compounds==null,why+": no merge or carrier prefix");}
 static void boundaries()throws Exception{
  Fixture f=new Fixture(false,false);ContextSignedWitnessInitialBarrier.Result r=f.run();ContextSignedWitnessInitialBarrierAudit.Receipt receipt=ContextSignedWitnessInitialBarrierAudit.inspect(r,f.initial,f.frame,64,f.limits,WORK,64);
  incomplete(ContextSignedWitnessInitialBarrier.run(f.initial,f.frame,64,f.limits,1,64),"fresh initial audit budget");incomplete(ContextSignedWitnessInitialBarrier.run(f.initial,f.frame,63,f.limits,WORK,64),"different row policy");incomplete(ContextSignedWitnessInitialBarrier.run(f.initial,f.frame,64,f.limits,WORK,0),"invalid validation capacity");reject(()->f.check(receipt,1),"no historical receipt budget reuse");
  Object old=replace(r.barrier,"branches",Collections.emptyMap());reject(()->f.check(receipt,WORK),"missing actual donor cut invalidates old receipt");replace(r.barrier,"branches",old);
  old=replace(r.barrier,"privatePools",Collections.emptyMap());reject(()->f.check(receipt,WORK),"missing private receiver frame invalidates receipt");replace(r.barrier,"privatePools",old);
  List<Item> leaked=new ArrayList<>(r.barrier.packet.items);Item seed=f.initial.entry.pools.values().stream().flatMap(p->p.local.items.stream()).filter(i->i.proofs.stream().anyMatch(ContextSignedWitnessBarrierMatrix::foreignOnly)).findFirst().get();leaked.add(seed);old=replace(r.barrier.packet,"items",Collections.unmodifiableList(leaked));reject(()->f.check(receipt,WORK),"raw private seed cannot contaminate common");replace(r.barrier.packet,"items",old);
  Item shared=r.barrier.packet.items.stream().filter(i->i.atom.predicate.equals("shared")).findFirst().get();require(shared.proofs.size()==2,"alternative loss control");java.lang.reflect.Constructor<Item> ctor=Item.class.getDeclaredConstructor(Atom.class,List.class);ctor.setAccessible(true);List<Item> dropped=new ArrayList<>(r.barrier.packet.items);dropped.set(dropped.indexOf(shared),ctor.newInstance(shared.atom,Collections.singletonList(shared.proofs.get(0))));old=replace(r.barrier.packet,"items",Collections.unmodifiableList(dropped));reject(()->f.check(receipt,WORK),"lost exact Source alternative in isolated common packet rejected by direct union audit");replace(r.barrier.packet,"items",old);
  old=replace(r.barrier,"changed",false);reject(()->f.check(receipt,WORK),"wrong common change flag rejected");replace(r.barrier,"changed",old);
  old=replace(r,"units",f.initial.entry.units);reject(()->f.check(receipt,WORK),"unrebased carrier rejected");replace(r,"units",old);f.check(receipt,WORK);require(true,"restored entire actual graph passes fresh audit");
  Map<Cut,ConstraintTransfer> missing=new LinkedHashMap<>(r.barrier.branches);missing.remove(missing.keySet().iterator().next());ConstraintBarrier bad=ContextWitnessResultTransport.mergeSignedWitnessInitial(f.initial.entry,missing,WORK);require(!bad.merged&&bad.packet==null&&bad.privatePools.isEmpty()&&bad.branches.isEmpty(),"adapter refuses incomplete cut map before any merge");
  System.out.println("SIGNED_WITNESS_BARRIER_BOUNDARIES noPrefixes=true missingCutsPrivateLeaksAlternativesFlagsCarriersRejected=true freshAudit=true noAdmission=true");
 }
}
