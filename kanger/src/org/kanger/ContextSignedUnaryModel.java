/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.Cut;

/** Given unary interpretations of ORIGINAL supplied rows only; no search/operational/write admission. */
final class ContextSignedUnaryModel {
 enum Status { MODELLED_SUPPLIED_UNARY, INCOMPLETE }
 static final class Policy {
  final int rows,points,predicates,work;
  Policy(int rows,int points,int predicates,int work){if(rows<1||rows>64||points<1||points>16||predicates<1||predicates>64||work<1)throw new IllegalArgumentException("Bounded original unary model policy required");this.rows=rows;this.points=points;this.predicates=predicates;this.work=work;}
 }
 static final class Interpretation {
  final List<SemanticTermSnapshot> points;final Map<String,List<Boolean>> truth;final Map<Source,Integer> witnesses;
  /** Last truth position is ALL OTHER admissible values, not another named value. */
  Interpretation(List<SemanticTermSnapshot> points,Map<String,List<Boolean>> truth,Map<Source,Integer> witnesses){if(points==null||points.isEmpty()||points.size()>16||truth==null||truth.size()>64||witnesses==null||witnesses.size()>64)throw new IllegalArgumentException("Bounded explicit interpretation data required");this.points=Collections.unmodifiableList(new ArrayList<>(points));Map<String,List<Boolean>> copy=new LinkedHashMap<>();for(Map.Entry<String,List<Boolean>> e:truth.entrySet()){if(e.getKey()==null||e.getKey().isEmpty()||e.getKey().length()>256||e.getValue()==null||e.getValue().size()>17)throw new IllegalArgumentException("Bounded explicit predicate truth required");copy.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));}this.truth=Collections.unmodifiableMap(copy);this.witnesses=Collections.unmodifiableMap(new LinkedHashMap<>(witnesses));}
 }
 static final class Row {
  final Snapshot cut,member;final Entry entry;
  private Row(Snapshot cut,Snapshot member,Entry entry){this.cut=cut;this.member=member;this.entry=entry;}
 }
 static final class Ledger {
  final Snapshot x;final List<Snapshot> foreign;final Map<Cut,Interpretation> models;final Policy policy;final List<Row> rows;
  private Ledger(Snapshot x,List<Snapshot> foreign,Map<Cut,Interpretation> models,Policy policy,List<Row> rows){this.x=x;this.foreign=Collections.unmodifiableList(new ArrayList<>(foreign));this.models=Collections.unmodifiableMap(new LinkedHashMap<>(models));this.policy=policy;this.rows=Collections.unmodifiableList(new ArrayList<>(rows));}
 }
 static final class Result {
  final Status status;final String phase,reason;final ContextSignedUnaryModelAudit.Receipt output;
  private Result(Status status,String phase,String reason,ContextSignedUnaryModelAudit.Receipt output){this.status=status;this.phase=phase;this.reason=reason;this.output=output;}
 }
 static Result run(Snapshot x,List<Snapshot> foreign,Map<Cut,Interpretation> models,Policy p){String phase="original-model-request";
  try{if(x==null||foreign==null||foreign.size()>15||models==null||models.size()>16||p==null)throw new IllegalArgumentException("Exact complete original model request required");new Policy(p.rows,p.points,p.predicates,p.work);Runnable work=ContextGroundProductionRounds.budget(p.work);List<Snapshot> cuts=new ArrayList<>();cuts.add(x);cuts.addAll(foreign);List<Row> rows=new ArrayList<>();phase="whole-row-manifest";for(Snapshot cut:cuts){work.run();for(Snapshot member:cut.physicalMembers())for(Entry e:member.primary){work.run();if(rows.size()>=p.rows)throw new IllegalStateException("Whole model row limit");rows.add(new Row(cut,member,e));}}Ledger l=new Ledger(x,foreign,models,p,rows);phase="independent-original-model-audit";ContextSignedUnaryModelAudit.Receipt receipt=ContextSignedUnaryModelAudit.inspect(l,x,foreign,models,p,p.work);return new Result(Status.MODELLED_SUPPLIED_UNARY,"original-unary-modelled","Given all-admissible interpretations satisfy original supplied rows only; no operational/full-language/provider/write capability",receipt);
  }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null);}
 }
}
