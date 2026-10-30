/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextSignedWitnessPlan.*;
import org.kanger.ContextSignedWitnessPlan.Kind;

/** Independent direct replay from original bundle fields; no plan builder/native inference/memo. */
final class ContextSignedWitnessPlanAudit {
 private ContextSignedWitnessPlanAudit(){}
 static final class Receipt {
  final Plan plan;final Snapshot x;final List<Snapshot> foreign;final int maximumRows;
  private Receipt(Plan plan,Snapshot x,List<Snapshot> foreign,int maximumRows){this.plan=plan;this.x=x;this.foreign=Collections.unmodifiableList(new ArrayList<>(foreign));this.maximumRows=maximumRows;}
 }
 static final class Inspection {
  final boolean audited;final String phase,reason;final Receipt output;
  private Inspection(boolean audited,String phase,String reason,Receipt output){this.audited=audited;this.phase=phase;this.reason=reason;this.output=output;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Inspection inspect(Plan plan,Snapshot x,List<Snapshot> foreign,int maximumRows,int maximumWork){
  if(maximumRows<1||maximumRows>1024||maximumWork<1)throw new IllegalArgumentException("Bounded independent signed plan audit required");String phase="request";Runnable work=ContextGroundProductionRounds.budget(maximumWork);
  try{
   work.run();require(plan!=null&&x!=null&&foreign!=null&&foreign.size()<=15&&plan.x==x&&plan.foreign.equals(foreign)&&plan.maximumRows==maximumRows,"Different actual plan request/bounds");
   List<Snapshot> cuts=new ArrayList<>();cuts.add(x);cuts.addAll(foreign);Set<UUID> contexts=new HashSet<>();phase="topology";
   for(int n=0;n<cuts.size();n++){work.run();Snapshot cut=cuts.get(n);require(cut!=null,"Missing expected cut");ContextProductionFamilyAssembly.identity(cut,n==0,work);ContextProductionFamilyAssembly.claimContexts(cut,contexts,work);}
   phase="original-replay";int index=0,signedCount=0;Set<Source> sources=new HashSet<>();
   for(Snapshot cut:cuts)for(Snapshot member:cut.trustedGroup==null?Collections.singletonList(cut):cut.members)for(Entry original:member.primary){
    work.run();require(index<maximumRows&&index<plan.rows.size()&&sources.add(original.source),"Incomplete/duplicate original row inventory");Row row=plan.rows.get(index++);require(row.cut==cut&&row.member==member&&row.original==original&&row.clauses.size()==original.clauses.size(),"Different complete row/cut/physical member");for(int c=0;c<original.clauses.size();c++){work.run();require(row.clauses.get(c)==original.clauses.get(c),"Opaque original clause replaced or split");}
    if(original.bundle==null){require(row.kind==Kind.OPAQUE_CLAUSES&&row.witnesses==0&&row.seeds.isEmpty()&&!original.clauses.isEmpty()&&original.clauses.size()<=8,"Clauses are recorded only, never materialized as witness facts");continue;}
    ContextWitnessBundle.Bundle b=original.bundle;require(b.source.equals(original.source)&&b.witnesses>=1&&b.witnesses<=8&&!b.atoms.isEmpty()&&b.atoms.size()<=8&&row.witnesses==b.witnesses&&row.seeds.size()==b.atoms.size(),"Different bounded original witness inventory");boolean negative=false;
    for(int a=0;a<b.atoms.size();a++){work.run();ContextWitnessBundle.Atom atom=b.atoms.get(a);Seed seed=row.seeds.get(a);negative|=!atom.positive;require(atom.predicate!=null&&atom.arguments.size()<=8&&seed.predicate.equals(atom.predicate)&&seed.positive==atom.positive&&seed.arguments.size()==atom.arguments.size(),"Different original atom sign/predicate/arity");require(seed.proof.depth==0&&seed.proof.sources.equals(Collections.singleton(original.source)),"Witness seed is not exact original singleton depth0 proof");
     for(int t=0;t<atom.arguments.size();t++){work.run();ContextWitnessBundle.Slot expected=atom.arguments.get(t);Term actual=seed.arguments.get(t);require(expected.witness>=-1&&expected.witness<b.witnesses,"Invalid original binder");if(expected.witness>=0)require(expected.value==null&&actual.value==null&&actual.origin!=null&&actual.origin.source.equals(original.source)&&actual.origin.binder==expected.witness,"Different Source-owned witness binder");else require(expected.value!=null&&actual.origin==null&&actual.value!=null&&actual.value.semanticallyEquals(expected.value),"Ordinary value/origin mismatch");}
    }
    require(row.kind==(negative?Kind.SIGNED_WITNESS:Kind.POSITIVE_WITNESS),"Wrong whole witness row role");if(negative)++signedCount;
   }
   require(index==plan.rows.size()&&signedCount==plan.signedRows,"Extra/dropped row or wrong signed inventory");return new Inspection(true,"replayed","Exact direct source/binder/sign replay; no live-provider/admission authority",new Receipt(plan,x,foreign,maximumRows));
  }catch(Exception failure){return new Inspection(false,phase,failure.toString(),null);}
 }
 static void check(Receipt r,Snapshot x,List<Snapshot> foreign,int maximumRows,int maximumWork){require(r!=null&&r.x==x&&r.foreign.equals(foreign)&&r.maximumRows==maximumRows,"Different expected signed plan receipt");Inspection fresh=inspect(r.plan,x,foreign,maximumRows,maximumWork);require(fresh.audited,fresh.phase+": "+fresh.reason);}
}
