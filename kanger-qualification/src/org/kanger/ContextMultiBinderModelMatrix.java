package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextRelationalInterpretation.*;
import org.kanger.ContextSignedRelationalModelSearch.*;
import org.kanger.ContextWitnessResultTransport.*;
final class ContextMultiBinderModelMatrix {
 static int checks; static final int WORK=64000000;
 static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Snapshot bind(String s)throws Exception{return ContextRelationalModelMatrix.bind(true,s);}
 static ContextSignedModelEvaluation.Result run(Snapshot x,List<Snapshot> fs){return ContextSignedModelEvaluation.runAutomatic(x,fs,new ContextSignedModelRequest("!p(1);",Collections.emptyMap()).policy);}
 static ContextSignedModelEvaluation.Result model(Snapshot x,List<Snapshot> fs){ContextSignedModelEvaluation.Result r=run(x,fs);require(r.status==ContextSignedModelInspection.Status.MODELLED_SUPPLIED&&r.relational!=null,r.phase+": "+r.reason);ContextSignedRelationalModelAudit.check(r.relational.output,WORK);require(true,"fresh independent original and final binder audit");return r;}
 static ContextRelationalInterpretation m(ContextSignedModelEvaluation.Result r,Snapshot x){return r.relational.output.ledger.models.get(Cut.of(x));}
 static void positive()throws Exception{
  Snapshot independent=bind("!$u $v p(u)&&q(v);");ContextSignedModelEvaluation.Result r=model(independent,Collections.emptyList());ContextRelationalInterpretation m=m(r,independent);
  require(m.witnesses.size()==2&&m.witnesses.values().stream().allMatch(v->v==0),"different binders in ONE Source may coincide; no invented inequality");
  Snapshot opposite=bind("!$u $v p(u)&&~p(v);");r=model(opposite,Collections.emptyList());m=m(r,opposite);
  require(r.namedPoints==2&&m.witnesses.size()==2&&new HashSet<>(m.witnesses.values()).size()==2,"same predicate opposite existential demands choose different values only when truth requires it");
  Source source=opposite.primary.get(0).source;require(!m.witnesses.get(new Binder(source,0)).equals(m.witnesses.get(new Binder(source,1))),"Source plus ordinal identifies each choice");
  Snapshot repeated=bind("!$u $v rel(u,u)&&~rel(v,v);");r=model(repeated,Collections.emptyList());m=m(r,repeated);Table t=m.relations.get(new Signature("rel",2));List<Integer> values=new ArrayList<>(m.witnesses.values());require(t.holds(Arrays.asList(values.get(0),values.get(0)),()->{})&&!t.holds(Arrays.asList(values.get(1),values.get(1)),()->{}),"repeated argument positions preserve each binder independently");
  Snapshot four=bind("!$u $v $w $z p(u)&&q(v)&&r(w)&&s(z);");r=model(four,Collections.emptyList());require(m(r,four).witnesses.size()==4&&m(r,four).denotations.size()==4,"all four captured binders retained in actual final inventory");
  Snapshot mixed=bind("!$u $v rel(u,1)&&~rel(1,v);");r=model(mixed,Collections.emptyList());require(m(r,mixed).witnesses.size()==2,"mixed fixed and existential arguments preserve two Source-owned choices");
  Snapshot linked=bind("!$u $v p(u)&&q(v);!@x p(x)->r(x);!@x q(x)->s(x);");r=model(linked,Collections.emptyList());require(m(r,linked).relations.containsKey(new Signature("r",1))&&m(r,linked).relations.containsKey(new Signature("s",1)),"native derived predicate obligations audited with both binders");
  Snapshot unsupported=bind("!$u $v rel(u,v);");ContextSignedModelEvaluation.Result no=run(unsupported,Collections.emptyList());require(no.status==ContextSignedModelInspection.Status.MODELLED_SUPPLIED&&no.relational!=null,"multiple Origins in one native atom have original and final relational models");
 }
 static Ledger replace(Ledger good,Snapshot x,ContextRelationalInterpretation replacement){Map<Cut,ContextRelationalInterpretation> all=new LinkedHashMap<>(good.models);all.put(Cut.of(x),replacement);return new Ledger(good.branch,good.policy,all,good.rows,good.obligations);}
 static boolean finalAccepts(Ledger l){try{ContextSignedRelationalModelAudit.finalModel(l,ContextSignedRelationalModelAudit.original(l,WORK),WORK);return true;}catch(IllegalStateException ex){return false;}}
 static void integrity()throws Exception{
  Snapshot x=bind("!$u $v p(u)&&~p(v);");ContextSignedModelEvaluation.Result r=model(x,Collections.emptyList());Ledger good=r.relational.output.ledger;ContextRelationalInterpretation m=m(r,x);Binder second=new Binder(x.primary.get(0).source,1);
  Map<Binder,Integer> missing=new LinkedHashMap<>(m.witnesses);missing.remove(second);require(!ContextRelationalModelMatrix.originalAccepts(replace(good,x,new ContextRelationalInterpretation(m.points,m.relations,missing,m.denotations))),"cannot lose second binder behind one Source");
  Map<Binder,Integer> changed=new LinkedHashMap<>(m.witnesses);changed.put(second,m.witnesses.get(new Binder(second.source,0)));require(!ContextRelationalModelMatrix.originalAccepts(replace(good,x,new ContextRelationalInterpretation(m.points,m.relations,changed,m.denotations))),"independent original truth rejects collapsed opposite binders");
  Map<Binder,Integer> extra=new LinkedHashMap<>(m.witnesses);extra.put(new Binder(second.source,2),0);require(!ContextRelationalModelMatrix.originalAccepts(replace(good,x,new ContextRelationalInterpretation(m.points,m.relations,extra,m.denotations))),"extra nonexistent binder cannot be model authority");
  Map<Origin,Integer> badFinal=new LinkedHashMap<>(m.denotations);Origin actualSecond=badFinal.keySet().stream().filter(o->o.source.equals(second.source)&&o.binder==1).findFirst().get();badFinal.put(actualSecond,m.witnesses.get(new Binder(second.source,0)));Ledger drift=replace(good,x,new ContextRelationalInterpretation(m.points,m.relations,m.witnesses,badFinal));require(ContextRelationalModelMatrix.originalAccepts(drift),"genuine original still valid when ONLY final second-binder denotation drifts");require(!finalAccepts(drift),"final audit independently pins second ordinal choice, not merely first binder of Source");
  badFinal=new LinkedHashMap<>(m.denotations);badFinal.remove(actualSecond);require(!finalAccepts(replace(good,x,new ContextRelationalInterpretation(m.points,m.relations,m.witnesses,badFinal))),"missing actual final second Origin refused");
  ContextSignedUnaryModelSearch.Policy p=good.policy;ContextSignedUnaryModelSearch.Policy one=new ContextSignedUnaryModelSearch.Policy(p.candidates,p.cells,1,p.work,p.original,p.extension);require(!ContextRelationalModelMatrix.originalAccepts(new Ledger(good.branch,one,good.models,good.rows,good.obligations)),"independent audit counts TWO binder choices instead of one Source against budget");
  Map<Binder,Integer> mutable=new LinkedHashMap<>(m.witnesses);ContextRelationalInterpretation copied=new ContextRelationalInterpretation(m.points,m.relations,mutable,m.denotations);mutable.clear();require(copied.witnesses.size()==2&&finalAccepts(replace(good,x,copied)),"immutable per-binder choices survive caller mutation and both receipts");
  for(int u=0;u<2;u++)for(int v=0;v<2;v++){Map<Binder,Integer> choices=new LinkedHashMap<>();choices.put(new Binder(second.source,0),u);choices.put(second,v);ContextRelationalInterpretation candidate=new ContextRelationalInterpretation(m.points,m.relations,choices,m.denotations);Table pTable=m.relations.get(new Signature("p",1));boolean expected=pTable.holds(Collections.singletonList(u),()->{})&&!pTable.holds(Collections.singletonList(v),()->{});require(ContextRelationalModelMatrix.originalAccepts(replace(good,x,candidate))==expected,"independent full two-binder assignment oracle "+u+","+v);}
 }
 static void topology()throws Exception{
  Snapshot x=bind("!$u $v p(u)&&~p(v);");Snapshot a=ContextRelationalModelMatrix.bind(false,"!p(1);"),b=ContextRelationalModelMatrix.bind(false,"!~p(1);");
  for(boolean reverse:new boolean[]{false,true}){ContextSignedModelEvaluation.Result r=model(x,reverse?Arrays.asList(b,a):Arrays.asList(a,b));ContextRelationalInterpretation ma=m(r,a),mb=m(r,b);Binder first=new Binder(x.primary.get(0).source,0),second=new Binder(first.source,1);require(!ma.witnesses.get(first).equals(mb.witnesses.get(first))&&!ma.witnesses.get(second).equals(mb.witnesses.get(second)),"independent A-X/B-X may choose different values for the SAME Source/binder in either order");require(ma.witnesses.size()==2&&mb.witnesses.size()==2,"no Source-only projection of independent cut models");}
  Snapshot trust=ContextProductionNativeBinding.trusted("multi-binder-trust",Arrays.asList(a,b),WORK);require(run(x,Collections.singletonList(trust)).status==ContextSignedModelInspection.Status.REFUTED,"TRUST still refutes all physical contradictory rows before modelling");
  ContextSignedModelEvaluation.Result bound=ContextSignedModelEvaluation.runAutomatic(x,Collections.emptyList(),new ContextSignedModelRequest("!p(1);",Collections.singletonMap("witnesses",1)).policy);require(bound.status==ContextSignedModelInspection.Status.INCOMPLETE,"two choices cannot fit witnesses1; exhaustion is not a refutation");
 }
}
