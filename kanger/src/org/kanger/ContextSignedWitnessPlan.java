/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Detached whole-row signed witness DATA plan. Not native readiness, inference or admission. */
final class ContextSignedWitnessPlan {
 private ContextSignedWitnessPlan(){}
 enum Kind { POSITIVE_WITNESS, SIGNED_WITNESS, OPAQUE_CLAUSES }
 static final class Origin {
  final Source source;final int binder;
  private Origin(Source source,int binder){this.source=source;this.binder=binder;}
  @Override public boolean equals(Object o){return o instanceof Origin&&source.equals(((Origin)o).source)&&binder==((Origin)o).binder;}
  @Override public int hashCode(){return Objects.hash(source,binder);}
 }
 static final class Term {
  final Origin origin;final SemanticTermSnapshot value;
  private Term(Origin origin,SemanticTermSnapshot value){this.origin=origin;this.value=value;}
 }
 static final class Seed {
  final String predicate;final boolean positive;final List<Term> arguments;final Support proof;
  private Seed(String predicate,boolean positive,List<Term> arguments,Source source){this.predicate=predicate;this.positive=positive;this.arguments=Collections.unmodifiableList(new ArrayList<>(arguments));proof=new Support(Collections.singleton(source),0);}
 }
 static final class Row {
  final Snapshot cut,member;final Entry original;final Kind kind;final int witnesses;
  final List<Seed> seeds;final List<Clause> clauses;
  private Row(Snapshot cut,Snapshot member,Entry original,Kind kind,int witnesses,List<Seed> seeds){this.cut=cut;this.member=member;this.original=original;this.kind=kind;this.witnesses=witnesses;this.seeds=Collections.unmodifiableList(new ArrayList<>(seeds));clauses=Collections.unmodifiableList(new ArrayList<>(original.clauses));}
 }
 static final class Plan {
  final Snapshot x;final List<Snapshot> foreign;final int maximumRows;final List<Row> rows;final int signedRows;
  private Plan(Snapshot x,List<Snapshot> foreign,int maximumRows,List<Row> rows,int signedRows){this.x=x;this.foreign=Collections.unmodifiableList(new ArrayList<>(foreign));this.maximumRows=maximumRows;this.rows=Collections.unmodifiableList(new ArrayList<>(rows));this.signedRows=signedRows;}
 }
 static final class Result {
  final boolean planned;final String phase,reason;final Plan output;
  private Result(boolean planned,String phase,String reason,Plan output){this.planned=planned;this.phase=phase;this.reason=reason;this.output=output;}
 }
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static Result build(Snapshot x,List<Snapshot> foreign,int maximumRows,int maximumWork){
  if(maximumRows<1||maximumRows>1024||maximumWork<1)throw new IllegalArgumentException("Explicit bounded signed witness plan required");
  String phase="topology";Runnable work=ContextGroundProductionRounds.budget(maximumWork);
  try{
   require(x!=null&&foreign!=null&&foreign.size()<=15,"Complete detached snapshot request required");List<Snapshot> cuts=new ArrayList<>();cuts.add(x);cuts.addAll(new ArrayList<>(foreign));
   Set<UUID> contexts=new HashSet<>();for(int i=0;i<cuts.size();i++){work.run();Snapshot cut=cuts.get(i);require(cut!=null,"Missing original cut");ContextProductionFamilyAssembly.identity(cut,i==0,work);ContextProductionFamilyAssembly.claimContexts(cut,contexts,work);}
   phase="whole-rows";List<Row> rows=new ArrayList<>();Set<Source> sources=new HashSet<>();int signedRows=0;
   for(Snapshot cut:cuts)for(Snapshot member:cut.trustedGroup==null?Collections.singletonList(cut):cut.members)for(Entry entry:member.primary){
    work.run();require(rows.size()<maximumRows&&sources.add(entry.source),"Complete unique original row budget required");
    if(entry.bundle==null){require(!entry.clauses.isEmpty()&&entry.clauses.size()<=8,"Bounded opaque original clauses required");rows.add(new Row(cut,member,entry,Kind.OPAQUE_CLAUSES,0,Collections.emptyList()));continue;}
    ContextWitnessBundle.Bundle b=entry.bundle;require(b.source.equals(entry.source)&&b.witnesses>=1&&b.witnesses<=8&&!b.atoms.isEmpty()&&b.atoms.size()<=8,"Bounded authenticated whole witness bundle required");
    List<Seed> seeds=new ArrayList<>();boolean signed=false;
    for(ContextWitnessBundle.Atom atom:b.atoms){work.run();require(atom.predicate!=null&&atom.arguments.size()<=8,"Witness literal arity bound");signed|=!atom.positive;List<Term> terms=new ArrayList<>();
     for(ContextWitnessBundle.Slot slot:atom.arguments){work.run();require(slot.witness>=-1&&slot.witness<b.witnesses&&(slot.witness<0?slot.value!=null:slot.value==null),"Exact binder or ordinary value required");terms.add(new Term(slot.witness<0?null:new Origin(entry.source,slot.witness),slot.value));}
     seeds.add(new Seed(atom.predicate,atom.positive,terms,entry.source));
    }
    if(signed)++signedRows;rows.add(new Row(cut,member,entry,signed?Kind.SIGNED_WITNESS:Kind.POSITIVE_WITNESS,b.witnesses,seeds));
   }
   return new Result(true,"planned","Original signed witness rows/seed identities recorded; no eligibility, inference or publication",new Plan(x,foreign,maximumRows,rows,signedRows));
  }catch(Exception failure){return new Result(false,phase,failure.toString(),null);}
 }
}
